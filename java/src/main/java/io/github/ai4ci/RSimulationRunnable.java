package io.github.ai4ci;

import static sim.engine.SimState.printlnSynchronized;

import java.util.Optional;

public class RSimulationRunnable<
		S extends RSimulation<S,?,?,A>, 
		A extends RAgent<A,S,?,?>> implements Runnable {

	// by time state gets here the model will be ready to go, seeds will have
	// been set
	// or it will have been loaded from some sort of checkpoint.

	protected RSimulationRunnable(RObservedSimulation<S,A> obsSim, Optional<String> directory) {
		this.obsSim = obsSim;
		this.save = directory.isPresent();
		this.directory = directory.orElse(null);
	}

	RObservedSimulation<S,A> obsSim;
	boolean quiet = false;
	boolean paused = false;
	boolean save= false;
	
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

	public void setTarget(long target) {
		this.target = target;
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
				
				if (Thread.interrupted()) throw new InterruptedException("Simulation "+obsSim.getSimulation().getId()+" interrupted.");
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
		
	



}
