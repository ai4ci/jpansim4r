package io.github.ai4ci.flow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ThreadPoolExecutor;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationRunnable;
import io.github.ai4ci.data.RSimulationExporter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * Consumes a simulation by running it in parallel if there is enough memory 
 * to do so. Uses a pull mechanism to construct a simulation from a pipeline
 * 
 * @param <S>
 */
public class RSimulationConsumer<
	S extends RSimulation<S,?,?,A>, 
	A extends RAgent<A,S,?,?>
	> implements Flow.Subscriber<RObservedSimulation<S,A>> {

	ThreadPoolExecutor executor;
	List<RSimulationExporter<S,A>> writers = new ArrayList<>();
	RSimulationMonitor monitor;
	String directory;
	boolean paused = true;
	boolean upstreamComplete = false;
	boolean complete = false;
	int maxSteps;
	Thread shutdown = new Thread() {
		@Override
		public void run() {
			super.run();
			RSimulationConsumer.this.shutdown();
		}
	};

	public RSimulationConsumer(String directory, int maxThreads, int maxMemGb, int maxSteps, int totalSimulations) {
		// ThreadFactory threadFactory = Executors.defaultThreadFactory();
		this.directory = directory;
		this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreads);
        //start the monitoring thread
        log.info("[consumer] setting up simulation consumer with base directory: "+directory);
        monitor = new RSimulationMonitor(this, 3, maxMemGb, totalSimulations);
        this.maxSteps = maxSteps;
        Thread monitorThread = new Thread(monitor,"monitor");
        monitorThread.start();
        Runtime.getRuntime().addShutdownHook(shutdown);
	}
	
	public RSimulationConsumer<S, A> start() {
		this.paused = false;
		return this;
	}
	
	public void shutdown() {
		log.info("[consumer] shutting down simulation consumer.");
		executor.shutdown();
		writers.forEach(a-> a.close());
        try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			
		}
        monitor.shutdown();
        Runtime.getRuntime().removeShutdownHook(shutdown);
	}

	public boolean idle() {
		return 
				this.executor.getCompletedTaskCount() > 0 &&
				this.executor.getTaskCount() == this.executor.getCompletedTaskCount();
	}
	
//	public void collectCsv(String file, Enum<?>... names) throws IOException {
//		List<String> columns = Stream.of(names).map(e -> e.name()).collect(Collectors.toList());
//		Stream.of("id","exportTimestep","timestep").forEach(columns::add);
//		Appendable csvOut = new FileWriter(new File(directory,file));
//		try (CSVPrinter printer = new CSVPrinter(csvOut, CSVFormat.EXCEL)) {
//			printer.printRecord(columns);
//		}
//	}

//	public RSimulationConsumer<S,A> withResultWriter(String file, Enum<?>... names) throws IOException {
//		log.info("[results] configuring writer: "+Stream.of(names).map(n -> n.name()).collect(Collectors.joining(","))+" to file: "+file);
//		ResultWriter<S,A> rw = new ResultWriter<S,A>(this.directory, file, names);
//		this.writers.add(rw);
//		return this;
//	}
	
	public RSimulationConsumer<S,A> withExporter(RSimulationExporter<S,A> exp) throws IOException {
		this.writers.add(exp);
		return this;
	}
	
	private Runnable wrap(RSimulationRunnable<S,A> runnable, int maxSteps) {
		return new Runnable() {
			@Override
			public void run() {
				monitor.attach(runnable);
				runnable.setTarget(maxSteps).run();
				for (RSimulationExporter<S,A> rs: writers) {
					rs.export(runnable.getObsSim());
				}
				monitor.release(runnable);
			}
		};
	}
	
	Subscription subscription;

	@Override
	public void onSubscribe(Subscription subscription) {
		this.subscription = subscription;
		log.info("[consumer] subscribed to upstream");
		// subscription.request(1);
	}


	@Override
	public void onNext(RObservedSimulation<S,A> copy) {
		RSimulationRunnable<S,A> simRunner = new RSimulationRunnable<S,A>(copy, this.directory);
		log.info("[consumer] queued new simulation: "+simRunner.getObsSim().getSimulation().getUrn());
		executor.execute(
			this.wrap(simRunner, maxSteps)
		);
	}


	@Override
	public void onError(Throwable throwable) {
		throw new RuntimeException(throwable);
	}


	@Override
	public void onComplete() {
		upstreamComplete = true;
	}
}
