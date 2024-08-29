package io.github.ai4ci;

import static sim.engine.SimState.printlnSynchronized;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSimulationRunnable<
		S extends RSimulation<S,?,?,A>, 
		A extends RAgent<A,S,?,?>> implements Runnable {

	// by time state gets here the model will be ready to go, seeds will have
	// been set
	// or it will have been loaded from some sort of checkpoint.

	public RObservedSimulation<S, A> getObsSim() {
		return obsSim;
	}

	public String getDirectory() {
		return directory;
	}

	public RSimulationRunnable(RObservedSimulation<S,A> obsSim, String directory) {
		this(obsSim,directory,false);
	}
	
	public RSimulationRunnable(RObservedSimulation<S,A> obsSim, String directory, boolean saveFinalState) {
		this.obsSim = obsSim;
		this.directory = directory;
		this.save = saveFinalState;
	}

	RObservedSimulation<S,A> obsSim;
	boolean quiet = false;
	boolean paused = false;
	boolean save = false;
	
	String directory;
	
	long step = 0;
	long target = -1;
	long startedAt;
	long startStep;

	public String progress() {
		double rate = ((double) (step-startStep)) * 1000 / ((double) System.currentTimeMillis() - startedAt + 1);
		if (target > 0) return String.format("%d/%d [%.1f%%] (%.1f per sec)", step, target, ((double) step)/((double) target), rate);
		else return String.format("step %d (%.1f per sec)", step, rate);
	}

	public String toString() {
		return progress() + ": " +obsSim.getSimulation().getStepId();
	}

	public boolean isPaused() {
		return paused;
	}

	public void pause() {
		this.paused = true;
	}

	public void unpause() {
		this.paused = false;
	}

	public RSimulationRunnable<S,A> setTarget(long target) {
		this.target = target;
		return this;
	}
	
//	@SuppressWarnings("unchecked")
//	public RSimulationRunnable<S,A> cancelAndRestart() throws FileNotFoundException, ClassNotFoundException, IOException {
//		return RSimulationBuilder
//				.ofType(obsSim.getSimulation().getClass(), directory, true)
//				.loadParameterised(obsSim.getSimulation())
//				.buildThread();
//	}

	@Override
	public void run() {
		obsSim.state = RObservedSimulation.State.RUNNING;
		startedAt = System.currentTimeMillis();
		startStep = obsSim.getSimulation().getSchedule().getSteps();
		boolean simulationCompleted = false;
		if (!quiet) printlnSynchronized(obsSim.toString()+" started at step "+startStep);
		
		try {
			
			while (!simulationCompleted) {
				
				if (Thread.interrupted()) throw new InterruptedException("Simulation "+obsSim.getSimulation().getUrn()+" interrupted.");
				while (this.isPaused()) Thread.sleep(10);
				
				// step the simulation:
				obsSim.getSimulation().getSchedule().step(obsSim.getSimulation());
				simulationCompleted = obsSim.getSimulation().isComplete();
				step = obsSim.getSimulation().getSchedule().getSteps();
				
				
				
				if (simulationCompleted) {
					if (!quiet) printlnSynchronized(obsSim.toString()+" finished at step "+step);
				} else if (target > 0 && step >= target) {
					simulationCompleted = true; 
					if (!quiet) printlnSynchronized(obsSim.toString()+" ran to step "+step);
					
				}
				
			}
		} catch (InterruptedException e) {
			// deal with interrupt.
			if (!quiet) printlnSynchronized(obsSim.toString()+" interrupted at step "+step);
			
		}
		obsSim.state = RObservedSimulation.State.COMPLETE;
		if (this.save) obsSim.save(directory);
	}
	
	
	public void writeCsv(String file, Enum<?>... names) throws IOException {
		List<String> columns = Stream.of(names).map(e -> e.name()).collect(Collectors.toList());
		Stream.of("id","exportTimestep","timestep").forEach(columns::add);
		Appendable csvOut = new FileWriter(Paths.get(directory, file).toFile());
		log.info("Writing simulation observations to: "+Paths.get(directory, file).toString());
		try (CSVPrinter printer = new CSVPrinter(csvOut, CSVFormat.EXCEL)) {
			printer.printRecord(columns);
			appendCsv(printer, columns);
		}
	}
	
	public void appendCsv(CSVPrinter csvOut, List<String> columns) throws IOException {
		if (!obsSim.getObservatory().isPresent()) {
			log.debug("Csv output not pssible as no observatory is enabled");
			return;
		}
		Map<Integer,Map<String,Map<String,List<?>>>> tmp = obsSim.getObservatory().get().observations(columns);
		if (tmp.size() > 1) throw new IOException("Attempt to write non rectangular data to CSV");
		//OutputStream outFile = Files.newOutputStream(csvOut, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		
		for (int i: tmp.keySet()) {
				Map<String,Map<String,List<?>>> byName = tmp.get(i);
				List<String> ids = new ArrayList<>();
				ids.addAll(byName.get(columns.get(0)).keySet());
				int rows =  byName.get(columns.get(0)).get(ids.get(0)).size();
				for (int row = 0; row < rows; row++) {
					for (String id: ids) {
						List<Object> rowValues = new ArrayList<>();
						for (String col: columns) {
							Object value = null;
							if (byName.containsKey(col)) {
								value = byName.get(col).get(id).get(row);
							} else if (col.equals("id")) {
								value = id;
							} else if (col.equals("exportTimestep")) {
								value = this.obsSim.getSimulation().getSimTime().intValue();
							} else if (col.equals("timestep")) {
								value = this.obsSim.getSimulation().getSimTime() - (row+1);
							}
							rowValues.add(value);
						}
						csvOut.printRecord(rowValues);
					}
				}
			}; 
			
		
	}


}
