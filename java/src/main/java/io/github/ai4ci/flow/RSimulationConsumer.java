package io.github.ai4ci.flow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationRunnable;
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
	List<ResultWriter> writers = new ArrayList<>();
	Monitor monitor;
	String directory;
	boolean paused = true;
	boolean upstreamComplete = false;
	boolean complete = false;
	

	public RSimulationConsumer(String directory, int maxThreads) {
		// ThreadFactory threadFactory = Executors.defaultThreadFactory();
		this.directory = directory;
		this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreads);
        //start the monitoring thread
        log.info("[consumer] setting up simulation consumer with base directory: "+directory);
        monitor = new Monitor(this, 3);
        
        Thread monitorThread = new Thread(monitor,"monitor");
        monitorThread.start();
        Runtime.getRuntime().addShutdownHook(
    		new Thread() {
				@Override
				public void run() {
					super.run();
					RSimulationConsumer.this.shutdown();
				}
    			
		});
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

	public static class Monitor implements Runnable {
		private RSimulationConsumer<?,?> pool;
		private int seconds;
		private boolean run = true;
		private int uncommitted;

		public Monitor(RSimulationConsumer<?,?> pool, int delay) {
			this.pool = pool;
			this.seconds=delay;
			uncommitted = pool.executor.getCorePoolSize();
		}

		public void shutdown(){
			log.info("[monitor] shutting down monitor.");
			this.run=false;
		}
		
		@Override
		public void run() {
			log.info("[monitor] Initialising monitor thread");
			while(run){
				Runtime runtime = Runtime.getRuntime();
				long allocatedMemory = runtime.totalMemory() - runtime.freeMemory();
				long presumableFreeMemory = runtime.maxMemory() - allocatedMemory;
				long mbMax = runtime.maxMemory() / (1024*1024);
				long mbFree = presumableFreeMemory / (1024*1024);
				log.info(
						String.format("[monitor] [%d/%d] Active: %d, Completed: %d, Task: %d, Free memory: %dMb/%dMb" ,
								this.pool.executor.getPoolSize(),
								this.pool.executor.getCorePoolSize(),
								this.pool.executor.getActiveCount(),
								this.pool.executor.getCompletedTaskCount(),
								this.pool.executor.getTaskCount(),
								mbFree , mbMax)
						);
				try {
					Thread.sleep(seconds*1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				int freeThreads = this.pool.executor.getCorePoolSize() - this.pool.executor.getActiveCount();
				// T
				if (mbFree > 2*1024 && freeThreads > 0) {
					// more than 2 Gb free and unused threads in pool.
					if (!pool.paused && !pool.upstreamComplete && uncommitted > 0) {
						log.info("[monitor] requesting a simulation to run: "+mbFree+" Mb free; "+freeThreads+" threads available; "+uncommitted+" uncommitted.");
						pool.subscription.request(1);
						uncommitted -= 1;
					}
				}
			}
			log.info("[monitor] monitor shut down complete.");
		}

		public void release() {
			this.uncommitted += 1;
			if (this.uncommitted > pool.executor.getCorePoolSize()) this.uncommitted = pool.executor.getCorePoolSize();
		}
	}
	
	
	
	public RSimulationConsumer<S,A> withResultWriter(String file, Enum<?>... names) throws IOException {
		log.info("[results] configuring writer: "+Stream.of(names).map(n -> n.name()).collect(Collectors.joining(","))+" to file: "+file);
		ResultWriter rw = new ResultWriter(file, names);
		this.writers.add(rw);
		return this;
	}
	
	private Runnable wrap(RSimulationRunnable<S,A> runnable) {
		return new Runnable() {
			@Override
			public void run() {
				runnable.run();
				for (ResultWriter rs: writers) {
					try {
						runnable.appendCsv(rs.fw, rs.columns);
						rs.fw.flush();
						log.info("[results] writing output for: "+runnable.getObsSim().getSimulation().getUrn()+"; "+rs.file);
					} catch (IOException e) {
						log.warn("[results] could not write results for: "+runnable.getObsSim().getSimulation().getUrn()+"; "+rs.file+"; "+e.getMessage()); 
					}
				}
				monitor.release();
			}
		};
	}
	
	/**
	 * This is a writer dedicated to a single file. It is a convenience for
	 * specifying what observations are to be written to a file. The CSV printer
	 * inside is passed 
	 *  
	 */
	private class ResultWriter {
		CSVPrinter fw;
		String file;
		List<String> columns;
		
		public ResultWriter(String file, Enum<?>... names) throws IOException {
			columns = Stream.of(names).map(e -> e.name()).collect(Collectors.toList());
			Stream.of("id","exportTimestep","timestep").forEach(columns::add);
			log.info("[results] writing CSV output to: "+new File(directory,file).getAbsolutePath());
			fw = new CSVPrinter(new FileWriter(new File(directory,file)),CSVFormat.RFC4180);
			this.file = file;
			// Write header.
			fw.printRecord(columns);
			fw.flush();
		}
		
		public void close() {
			try {
				fw.close();
			} catch (IOException e) {}
		}
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
			this.wrap(simRunner)
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
