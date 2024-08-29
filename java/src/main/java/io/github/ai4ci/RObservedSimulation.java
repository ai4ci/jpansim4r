package io.github.ai4ci;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.Supplier;


public class RObservedSimulation<
		S extends RSimulation<S,?,?,A>, 
		A extends RAgent<A,S,?,?>> implements Serializable {

	S simulation;
	RObservatory<S,A> observatory;
	State state = State.UNCONFIGURED;
	
	public enum State {UNCONFIGURED, INITIALIZED, CONFIGURED, PARAMETERISED, READY, RUNNING, COMPLETE}
	
	public RObservedSimulation(S simulation2) {
		this.simulation = simulation2;
	}
	
	public boolean atOrBeyondStage(State state) {
		return this.state.compareTo(state) >= 0;
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
	public Optional<RObservatory<S,A>> getObservatory() {
		return Optional.ofNullable(observatory);
	}
	
	public String toString() {
		String tmp;
		if (state.equals(State.RUNNING)) tmp = simulation.getStepId();
		else tmp = simulation.getUrn();
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
	public void initialiseObservatory() {
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
		simulation.getObservers().forEach(o -> observatory.registerNamedObserver(o));
		simulation.streamAgents().forEach(a -> {
			a.getObservers().forEach(o -> observatory.registerNamedObserver(o));
		});
		// Add the observatory to the simulation schedule.
		simulation.getSchedule().scheduleOnce(observatory, observatory.getPriority());
	}

	public void setState(State initialized) {
		this.state = initialized;
	}

	public boolean hasNamedObservers() {
		return 
				this.getSimulation().getObservers().findAny().isPresent() ||
				this.getSimulation().streamAgents().flatMap(a -> a.getObservers()).findAny().isPresent();
	}
	
	
	public static <S extends RSimulation<S,?,?,A>, 
	A extends RAgent<A,S,?,?>> Supplier<? extends RObservedSimulation<S,A>> uninitialised(Class<S> type) {
		return new Supplier<RObservedSimulation<S,A>>() {
			@Override
			public RObservedSimulation<S, A> get() {
				try {
					S simulation = type.getDeclaredConstructor().newInstance();
					RObservedSimulation<S,A> obsSim = new RObservedSimulation<S,A>(simulation);
					return obsSim;
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| NoSuchMethodException | SecurityException e) {
					throw new RuntimeException(type.getName()+" must provide a norgs public constructor",e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e.getCause());
				}
			};
		};
	}
}
