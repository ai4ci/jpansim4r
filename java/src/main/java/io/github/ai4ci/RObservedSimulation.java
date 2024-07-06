package io.github.ai4ci;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Optional;


public class RObservedSimulation<
		S extends RSimulation<S,?,?,A>, 
		A extends RAgent<A,S,?,?>> implements Serializable {

	S simulation;
	RObservatory<S,A> observatory;
	State state = State.UNCONFIGURED;
	
	public enum State {UNCONFIGURED, INITIALIZED, CONFIGURED, PARAMETERISED, RUNNING, COMPLETE}
	
	public RObservedSimulation(S simulation2) {
		this.simulation = simulation2;
	}
	
	public void save(String directory) {
		if (state.equals(State.UNCONFIGURED)) throw new RuntimeException("Simulation not yet configured");
		String ser;
		if (state.equals(State.RUNNING) || state.equals(State.COMPLETE)) {
			ser = simulation.getFilePath(directory, (int) simulation.getSchedule().getSteps(), "obsSim.ser");
		} else {
			ser = simulation.getFilePath(directory, 0,"obsSim.ser");
		}
		
		try {
			FileOutputStream fos = new FileOutputStream(RSimulation.fullPath(ser).toFile());
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
//			Output output = new Output(fos);
//			RSimulationBuilder.kryo.writeObject(output, this);
//			output.close();
		} catch (IOException e) {
			throw new RuntimeException("Could not save to: "+ser.toString(), e);
		}
	}
	
	public S getSimulation() {return simulation;}
	public Optional<RObservatory<S,A>> getObservatory() {return Optional.ofNullable(observatory);}
	
	public String toString() {
		String tmp;
		if (state.equals(State.RUNNING)) tmp = simulation.getStepId();
		else tmp = simulation.getId();
		return tmp;
	}

	public State getState() {
		return state;
	}
	
	/**
	 * Builds an observatory and registers it with the scheduler
	 * Assumes simulation is setup and agents created, and all 
	 * named observers have been setup in simulation and agents. 
	 * After this has been done we
	 * can add observers whenever we like to this, but not to simulation or agents.
	 * 
	 * @param <X> the observatory type
	 * @param cfg
	 * @param type  the observatory type
	 * @return the observatory
	 */
	protected void initialiseObservatory() {
		// TODO: better as a checked exception?
		if (observatory == null) {
			if (getState().equals(State.UNCONFIGURED)) throw new RuntimeException("Simulation must be configured before this is called.");
			observatory = new RObservatory<S, A>(getSimulation());
			registerNamedObservers();
		}
	}	
	
	private void registerNamedObservers() {
		// Any named observers in the simulation or agents we register with the observatory 
		// so we can query them for export later
		simulation.getObservers().forEach(o -> observatory.namedObservers.add(o));
		simulation.streamAgents().forEach(a -> {
			a.getObservers().forEach(o -> observatory.namedObservers.add(o));
		});
		// Add the observatory to the simulation schedule.
		simulation.getSchedule().scheduleOnce(observatory, observatory.getPriority());
	}
	
}
