package io.github.ai4ci.builder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RGUI;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationParameterisation;
import io.github.ai4ci.RSimulationRunnable;
import lombok.extern.slf4j.Slf4j;
import io.github.ai4ci.RObservedSimulation.State;

@Slf4j
/**
 * This is responsible for taking a ConfigureOne and finalising the setup
 * and wrapping it in a thread ready for execution.
 */
//TODO make this runnable, so it can be done in multiple threads,
public class ParameteriseOne<
		S extends RSimulation<S,C,P,A>, 
		C extends RSimulationConfiguration, 
		P extends RSimulationParameterisation,
		A extends RAgent<A,S,?,?>
	>  extends ConfigureOne<S,C,P,A> implements Runnable,Serializable {

	P parameterisation;
	int parameterisationBootstrap;

	// CONSTRUCTOR

	public ParameteriseOne(ConfigureOne<S,C,P,A>  configuredBuilder, P parameterisation) {
		this(configuredBuilder,parameterisation,0);
	}

	public ParameteriseOne(ConfigureOne<S,C,P,A>  configuredBuilder, P parameterisation, int bootstrap) {
		super(configuredBuilder);
		this.parameterisation = parameterisation;
		this.parameterisationBootstrap = bootstrap;
		this.getSimulation().setParameterisation(parameterisation);
		this.getSimulation().setParameterisationBootstrapId(bootstrap);
		this.getSimulation().setSeed(seedBase);
		this.ser = this.getSimulation().getFilePath(this.directory, null, "build2.ser");
	}

	// STAGE 2 BUILDER LOGIC

	/**
	 * The main configurable part of the parameterisation workflow. This
	 * iterates through the agents and calls initialiseStatus on them. It 
	 * schedules them for execution and calls "finishParameterisation"
	 */
	public void run() {
		log.debug("Starting parameterisation {}", getSimulation().getUrn());
		this.getSimulation().setupStage5StartParameterisation();
		this.getSimulation().streamAgents().forEach(a -> a.setupStage6InitialiseAgentStatus());
		// Schedule all the agents.
		this.getSimulation().setupStage7FinishParameterisation();
		log.debug("Finished parameterisation {}", getSimulation().getUrn());
		obsSim.setState(State.PARAMETERISED);
		if (useCache) {
			log.debug("Saving parameterised simulation {}", getSimulation().getUrn());
			try {
				this.saveParameterised();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * The ParameteriseOne save method saves the whole builder with the 
	 * observed simulation in a state ready to be run (using the buildThread())
	 * method. The file extension with be something like "YYY.build2.ser"  
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void saveParameterised() throws FileNotFoundException, IOException {
		if (!obsSim.getState().equals(State.PARAMETERISED)) throw new RuntimeException("Simulation must be parameterised before this is called.");
		try {
			FileOutputStream fos = new FileOutputStream(RSimulation.fullPath(ser).toFile());
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
			//				Output output = new Output(fos);
			//				RSimulationBuilder.kryo.writeObject(output, this);
			//				output.close();
		} catch (IOException e) {
			throw new RuntimeException("Could not save to: "+ser.toString());
		}
	}

	/**
	 * Complete the build process. The last stage is to wrap the simulation 
	 * whcih is fully configured into a 
	 * 
	 * @return
	 */
	public RSimulationRunnable<S,A> buildThread(boolean withObservatory, boolean saveFinalState) {
		if (!obsSim.getState().equals(State.PARAMETERISED)) throw new RuntimeException("Simulation must be parameterised before this is called.");

		// if the observatory is required register.
		if (withObservatory) {
			this.obsSim.initialiseObservatory();
			this.obsSim.getSimulation().getObservers().forEach(o ->
			this.obsSim.getObservatory().get().registerNamedObserver(o)
					);
			this.obsSim.getSimulation().streamAgents().forEach(a ->
			a.getObservers().forEach(o ->
			this.obsSim.getObservatory().get().registerNamedObserver(o)));
		}
		obsSim.getSimulation().start();
		obsSim.getSimulation().initialiseScheduler();
		obsSim.getObservatory().ifPresent(o -> o.initialiseScheduler());
		return new RSimulationRunnable<S,A>(this.obsSim, this.directory, saveFinalState);
	}

	/** Spawn a gui of a particular type
	 * 
	 * @param <X>
	 * @param guiType the type of GUI.
	 * @return
	 */
	public <X extends RGUI<S,A>> X spawnGui(Class<X> guiType) {
		if (!(
				obsSim.getState().equals(State.PARAMETERISED)
				)) throw new RuntimeException("Simulation must be parameterised but not currently running before this is called.");
		try {
			X gui = guiType.getDeclaredConstructor(getSimulation().getClass()).newInstance(getSimulation());
			return gui;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(guiType.getName()+" must provide a public constructor with one parameter of type "+getSimulation().getClass().getName(),e);
		}
	}
}