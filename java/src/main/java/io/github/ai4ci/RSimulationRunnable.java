package io.github.ai4ci;

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
		String tmp;
		if (obsSim.state.compareTo(RObservedSimulation.State.RUNNING)<0) tmp = "not started.";
		else if (obsSim.state.equals(RObservedSimulation.State.COMPLETE)) tmp = "completed.";
		else if (this.isPaused()) tmp="paused.";
		else {
			double rate = ((double) (step-startStep)) * 1000 / ((double) System.currentTimeMillis() - startedAt + 1);
			if (target > 0) 
				tmp=String.format("%d/%d [%.1f%%] (%.1f per sec)", step, target, ((double) step)/((double) target)*100, rate);
			else 
				tmp=String.format("step %d (%.1f per sec)", step, rate);
		}
		return obsSim.getSimulation().getUrn()+" ["+tmp+"]";
	}

	public boolean isComplete() {
		return obsSim.state.equals(RObservedSimulation.State.COMPLETE);
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
		if (!quiet) 
			// printlnSynchronized(obsSim.toString()+" started step "+step);
			log.info(obsSim.toString()+" started at step "+startStep);
		
		try {
			
			while (!simulationCompleted) {
				
				if (Thread.interrupted()) throw new InterruptedException("Simulation "+obsSim.getSimulation().getUrn()+" interrupted.");
				while (this.isPaused()) Thread.sleep(10);
				
				// step the simulation:
				obsSim.getSimulation().getSchedule().step(obsSim.getSimulation());
				simulationCompleted = obsSim.getSimulation().isComplete();
				step = obsSim.getSimulation().getSchedule().getSteps();
				
				
				
				if (simulationCompleted) {
					if (!quiet) 
						log.info(obsSim.toString()+" finished at step "+step);
						// printlnSynchronized(obsSim.toString()+" finished at step "+step);
				} else if (target > 0 && step >= target) {
					simulationCompleted = true; 
					if (!quiet) 
						log.info(obsSim.toString()+" ran to step "+step);
						// printlnSynchronized(obsSim.toString()+" ran to step "+step);
					
				}
				
			}
		} catch (InterruptedException e) {
			// deal with interrupt.
			if (!quiet) 
				log.info(obsSim.toString()+" interrupted at step "+step);
				// printlnSynchronized(obsSim.toString()+" interrupted at step "+step);
			
		}
		obsSim.state = RObservedSimulation.State.COMPLETE;
		if (this.save) obsSim.save(directory);
	}
	
	
	


}
