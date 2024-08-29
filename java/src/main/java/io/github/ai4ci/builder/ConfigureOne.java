package io.github.ai4ci.builder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservatory;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationParameterisation;
import io.github.ai4ci.RObservedSimulation.State;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * STAGE 1-2 Configuration
 * Constructing this results in a builder with status=initialised.
 * 
 */
public class ConfigureOne<
		S extends RSimulation<S,C,P,A>, 
		C extends RSimulationConfiguration, 
		P extends RSimulationParameterisation,
		A extends RAgent<A,S,?,?>
	> extends RSimulationBuilder<S,C,P,A> implements Serializable, Runnable {

	C config;
	RObservedSimulation<S,A> obsSim;
	int configBootstrap;
	String ser;


	// CONSTRUCTORS

	/**
	 * Clone constructor is called by ParameteriseOne when subclassing
	 * @param builder
	 */
	public ConfigureOne(ConfigureOne<S,C,P,A> builder) {
		this(builder, builder.config, builder.configBootstrap);
		log.debug("Cloning configuration for simulation {} bootstrap {}", 
				builder.config.getConfigurationName(),
				builder.configBootstrap
				);
		this.obsSim = SerializationUtils.clone(builder.obsSim);
		//RSimulationBuilder.kryo.copy(builder.obsSim);
	}

	/**
	 * regular constructors are called by the RSimulationBuilder and not 
	 * expected to be called by anything else.
	 * @param builder
	 * @param config
	 */
	public ConfigureOne(RSimulationBuilder<S,C,P,A> builder, C config) {
		this(builder, config, 0);
	}

	public ConfigureOne(RSimulationBuilder<S,C,P,A> builder, C config, int bootstrap) {
		super(builder.simulationType, 
				builder.directory, 
				builder.configurations, 
				builder.parameterisations,
				builder.simulationObservers,
				builder.agentObservers,
				builder.seedBase,
				builder.reproduceAt,
				builder.useCache);
		this.config = config;
		this.configBootstrap = bootstrap;
		if (this.obsSim == null || this.obsSim.getState().equals(State.UNCONFIGURED)) {
			try {
				S simulation = simulationType.getDeclaredConstructor().newInstance();
				obsSim = new RObservedSimulation<S,A>(simulation);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(simulationType.getName()+" must provide a norgs public constructor",e);
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e.getCause());
			}
			getSimulation().setConfiguration(config);
			getSimulation().setConfigurationBootstrapId(bootstrap);
			getSimulation().setSeed(seedBase);
		}
		log.debug("Initialised simulation configuration bootstrap {}", this.obsSim.getSimulation().getUrn());
		obsSim.setState(State.INITIALIZED);
		ser = RSimulation.relPathFrom(directory, reproduceAt, config, bootstrap, null, null, null,"build.ser");
		// we are ready to build this simulation
	}

	// GETTERS

	public S getSimulation() {return obsSim.getSimulation();}

	public RObservatory<S,A> getObservatory() {
		Optional<RObservatory<S, A>> tmp = obsSim.getObservatory();
		if (!tmp.isPresent()) obsSim.initialiseObservatory();
		return obsSim.getObservatory().get();
	}


	// STAGE 1 BUILDER LOGIC

	/**
	 * This is the main configuration 
	 * Moves the state of the RObservedSimulation from initialised to configured. This 
	 * may be an expensive operation as builds the whole simulation environment
	 * and configures all the agents. If this is being done with bootstrapping
	 * then the resulting multiple simulations can be built in threads.
	 */
	@Override
	public void run() {

		if (!obsSim.getState().equals(State.INITIALIZED)) throw new RuntimeException("Simulation must be initialized before this is called.");

		// This is the main builder process:
		getSimulation().setupStage1BeginConfiguration();
		log.debug("Building agents {}", getSimulation().getUrn());
		getSimulation().setupStage2CreateAgents();
		log.debug("Setting agent baselive {}", getSimulation().getUrn());
		getSimulation().streamAgents().forEach(a -> a.setupStage3SetAgentBaseline());
		getSimulation().setupStage4FinishConfiguration();

		// TODO: could have a list of named agent environments in the simulation (e.g.
		// a simple generic wrapper that can wrap a field or network...)
		log.debug("Finishing configuration {}", getSimulation().getUrn());
		obsSim.setState(State.CONFIGURED);

		if (this.simulationObservers.size() > 0) {
			this.obsSim.initialiseObservatory();
			this.simulationObservers.forEach(
					o -> this.obsSim.getObservatory().get()
					.observeSimulation(o)
					);
		}

		if (this.agentObservers.size() > 0) {
			this.obsSim.initialiseObservatory();
			this.agentObservers.forEach(
					a -> this.obsSim.getObservatory().get()
					.observeAgents(a)
					);
		}

		log.debug("Saving configured simulation {}", getSimulation().getUrn());

		if (this.useCache) {
			try {
				this.saveConfigured();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Saving the configured builder allows us to generate more parameterised
	 * models with different assumptions (see withNewParameterisation(...))
	 * The serialisation is of the whole builder object and will be called
	 * XXX.build.ser
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void saveConfigured() throws FileNotFoundException, IOException {
		String ser = RSimulation.relPathFrom(directory, reproduceAt, this.config, this.configBootstrap, null, null, null, "build.ser");
		if (!obsSim.getState().equals(State.CONFIGURED)) throw new RuntimeException("Simulation must be configured before this is called.");
		try {
			FileOutputStream fos = new FileOutputStream(RSimulation.fullPath(ser).toFile());
			//				Output output = new Output(fos);
			//				RSimulationBuilder.kryo.writeObject(output, this);
			//				output.close();
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			throw new RuntimeException("Could not save to: "+ser.toString(),e);
		}
	}


	/**
	 * combine the configured simulation with a a set of parameterisations 
	 * and generate
	 * bootstrap replicates of the parameterised simulation. This may involve
	 * loading from the cached versions on disk.
	 * 
	 * This stage will generate a number of ParameteriseOne which are ready to 
	 * be executed. each of which
	 * is persisted to disk in the 
	 * 
	 * @param bootstraps
	 * @param useCache
	 * @return
	 */
	public Stream<ParameteriseOne<S,C,P,A>> parameteriseConfigured(int bootstraps) {
		return this.parameterisations.stream().parallel().flatMap( p -> 
		IntStream.range(0, bootstraps).parallel().mapToObj( i -> 
		this.parameteriseConfigured(p, i))
				);
	}

	/** 
	 * load or build a parameterisation
	 * Consider how these could be wrapped in a callable to return the 
	 * ParameteriseOne regardless of whether is is loaded or built.
	 * 
	 * @param parameters
	 * @param bootstrap
	 * @param useCache
	 * @return
	 */
	private ParameteriseOne<S,C,P,A>  parameteriseConfigured(P parameters, int bootstrap) {
		log.debug("Parameterising configured simulation {}; bootstrap {}", getSimulation().getUrn(), bootstrap);
		ParameteriseOne<S,C,P,A>  tmp;
		String ser = RSimulation.relPathFrom(directory, reproduceAt, this.config, this.configBootstrap, parameters, bootstrap, null, "param.ser");
		try {
			if (useCache && Files.isRegularFile(RSimulation.fullPath(ser))) {
				log.debug("Loading parameterised simulation {}; bootstrap {}", getSimulation().getUrn(), bootstrap);
				return loadParameterised(ser);
			} {
				tmp = new ParameteriseOne<S,C,P,A>(this, parameters, bootstrap);
				//TODO: consider how to run in a thread pool.
				log.debug("Parameterising simulation {}; bootstrap {}", getSimulation().getUrn(), bootstrap);
				tmp.run();
			}
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
		return tmp;
	}
}