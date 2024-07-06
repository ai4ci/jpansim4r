package io.github.ai4ci;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.RObservedSimulation.State;
import lombok.extern.slf4j.Slf4j;
/*
 *  * 
 * I really want to decompose this into two (or 3 steps).
 *  
 * 1) Configure and build model(s) using configuration
 * 1a) configure simulation; including all agents, and named observers. 
 * including registering named observers with the schedule.
 * 1b) configure observatory; including all model observers. 
 * 1c) checkpoint configured combination of simulation and observatory. (RObservedSimulation?)
 * 
 * 1 alt 1) reset existing model to configured model state (GUIstate.start()) 
 * this would be to stage 1a) only.
 * 1 alt 2) load existing configured model from disk.
 * 
 * 2) Parameterise configured model(s) using parameterisations
 * 2a) clone check-pointed combination of simulation and observatory. (if more than 1)
 * 2b) update parameters
 * 
 * 2 alt 1) load fully configured parameterisation model from disk.
 * 
 * 3) Bootstrap full parameterisations (GUI boots = 1)
 * 3a) clone parameterised model into runnable thread (if more than 1)
 * 3b) set boot id & random seed
 * 3c) call start on simulation, i.e. set to initial state.
 * 
 * For GUI we only need subset of steps (RSimulation)
 * 1a) configure simulation; including all agents, and named observers.
 * 2b) update parameters
 * 3b) set boot id & random seed
 * 3c) call start on GUI which calls start on simulation, i.e. reset to initial state.
 *  problem here is that it is difficult to revert to initial state of simulation
 *  from within the simulation class. Ideally we would reload but that is not
 *  possible without creating a new instance, and this would trigger a 
 *  reload of the GUI (and respawning all the windows I think) Maybe this is
 *  not the case and a GUIState.load(state) with the deserialised checkpoint
 *  within the GUIState.start().
 *  is all that is needed...?
 */
@Slf4j
public class RSimulationBuilder<
		S extends RSimulation<S,C,P,A>, 
		C extends RSimulationConfiguration, 
		P extends RSimulationParameterisation,
		A extends RAgent<A,S,?,?>> implements Serializable {

//	public static Kryo kryo = new Kryo();
//	{
//		kryo.register(RObservedSimulation.class);
//		kryo.register(RSimulationBuilder.class);
//		kryo.setRegistrationRequired(false);
//		kryo.setReferences(true);
//		kryo.register(SerializedLambda.class);
//	    kryo.register(Closure.class, new ClosureSerializer()); 
//	}
	
	Class<S> simulationType;
	long seedBase;
	LocalDate reproduceAt;
	List<C> configurations;
	List<P> parameterisations;
	List<RSimulationObserver<S,?>> simulationObservers;
	List<RAgentObserver<? extends A,?>> agentObservers;
	String directory;
	boolean useCache;
	
	// Base builder constructors
	
	private RSimulationBuilder(Class<S> simulationType, Optional<String> directory, boolean useCache) {
		this(simulationType, directory, new ArrayList<C>(), new ArrayList<P>(), 
				new ArrayList<RSimulationObserver<S,?>>(),
				new ArrayList<RAgentObserver<? extends A,?>>(),
				0,
				LocalDate.now(),
				useCache
				);
	}
	
	private RSimulationBuilder(Class<S> simulationType, Optional<String> directory, List<C> configurations,
			List<P> parameterisations, 
			List<RSimulationObserver<S,?>> observers,
			List<RAgentObserver<? extends A,?>> agentObservers,
			long seedBase,
			LocalDate reproduceAt,
			boolean useCache
			) {
		this.simulationType = simulationType;
		this.directory = directory.orElse(null);
		this.configurations = configurations;
		this.parameterisations = parameterisations;
		this.seedBase = seedBase;
		this.simulationObservers = new ArrayList<>();
		this.useCache = useCache;
		observers.forEach(o -> this.simulationObservers.add( 
				// RSimulationBuilder.kryo.copy(o) ));
				SerializationUtils.clone(o) ));
		this.agentObservers = new ArrayList<>();
		agentObservers.forEach(a -> this.agentObservers.add( 
				// RSimulationBuilder.kryo.copy(a) ));
				SerializationUtils.clone(a) ));
		this.reproduceAt = reproduceAt;
	}
	
	// Builder factory method
	
	/**
	 * A builder for new simulations
	 * 
	 * @param <S> The simulation class
	 * @param <C> The simulation configuration class
	 * @param <P> The simulation paramterisation class
	 * @param <A> The base of the agent classes for this simulation
	 * @param simulationType The simulation class
	 * @param directory A directory to store model snapshots and outputs.
	 * @return a simulation builder.
	 */
	public static <
			S extends RSimulation<S,C,P,A>, 
			C extends RSimulationConfiguration, 
			P extends RSimulationParameterisation,
			A extends RAgent<A,S,?,?>> 
		RSimulationBuilder<S,C,P,A> ofType(Class<S> simulationType, Optional<String> directory, boolean useCache) {
		return new RSimulationBuilder<S,C,P,A>(simulationType, directory, useCache); 
	}
	
	// Base builder fluent methods
	
	@SuppressWarnings("unchecked")
	public RSimulationBuilder<S,C,P,A> withConfiguration(C... configuration) {
		return withConfigurations(Arrays.asList(configuration));	
	}
	
	public RSimulationBuilder<S,C,P,A> withConfigurations(Collection<C> configurations) {
		this.configurations.addAll(configurations);
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public RSimulationBuilder<S,C,P,A> withParameterisation(P... parameterisation) {
		return withParameterisations(Arrays.asList(parameterisation));
	}
	
	public RSimulationBuilder<S,C,P,A> withParameterisations(Collection<P> parameterisation) {
		this.parameterisations.addAll(parameterisation);
		return this;
	}
	
	public RSimulationBuilder<S,C,P,A> withNewParameterisation(Collection<P> parameterisation) {
		this.parameterisations = new ArrayList<>();
		this.parameterisations.addAll(parameterisation);
		return this;
	}
	
	public RSimulationBuilder<S,C,P,A> withLocalDate(LocalDate reproduceAt) {
		this.reproduceAt = reproduceAt;
		return this;
	}
	
	@SafeVarargs
	/**
	 * Setup simulation observers to be added to each simulation.
	 * @param observer
	 * @return
	 */
	public final RSimulationBuilder<S,C,P,A> withObserver(RSimulationObserver<S,?>... observer) {
		return this.withObservers(Arrays.asList(observer));
	}
	
	public RSimulationBuilder<S,C,P,A> withObservers(Collection<RSimulationObserver<S,?>> observers) {
		this.simulationObservers.addAll(observers);
		return this;
	}
	
	public final RSimulationBuilder<S,C,P,A> withAgentObserver(RAgentObserver<? extends A,?> observer) {
		this.agentObservers.add(observer);
		return this;
	}
	
	// Stage 2 builder factory method, includes loaders
	
	/**
	 * Initialise a stream of factories
	 * @param bootstraps
	 * @param useCache
	 * @return
	 */
	public Stream<ConfigureOne> initialiseSimulation(int bootstraps) {
		log.debug("Initialising simulation with {} bootstraps", bootstraps);
		return this.configurations.stream().flatMap(
				conf -> IntStream.range(0, bootstraps).mapToObj(
						i -> this.constructSimulation(conf,i)));
	}
	
	/**
	 * initialises a single simulation builder or loads it from disk.
	 * @param config the configuration
	 * @param bootstrap which bootstrap replicate of the simulation setup to use?
	 * @param useCache cache the configured simulation.
	 * @return a configured simulation.
	 */
	private ConfigureOne constructSimulation(C config, int bootstrap) {
		log.debug("Configuring simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
		ConfigureOne tmp; //= new ConfigureOne(this, config, bootstrap);
		// Path ser = directory.resolve(tmp.getSimulation().getConfigurationId()+".ser");
		String ser = RSimulation.relPathFrom(this.directory, this.reproduceAt, config, bootstrap, null, null, null, "build.ser");
		try {
			if (useCache && Files.isRegularFile(RSimulation.fullPath(ser))) {
				log.debug("Loading configuration for simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
				return loadConfigured(ser);
				
			} else {
				log.debug("Building configuration for simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
				//TODO: wrap in a thread. submit to a thread pool
				tmp = new ConfigureOne(this, config, bootstrap);
				// ConfigureOne is a runnable. 
				tmp.run();
			}
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
		return tmp;
	}
	
	/**
	 * Configured but not yet parameterised model.
	 * This is one baseline simulation prior to bootstrapping. This is the 
	 * go to method if you need to re-run a simulation model with 
	 * different parameterisation, or a bootstrapped set of models.
	 * @param simulation
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	
	public ConfigureOne loadConfigured(S simulation) throws FileNotFoundException, IOException, ClassNotFoundException {
		String ser = simulation.getBaselineConfigFilePath(directory);
		return loadConfigured(ser);
	}
		
	@SuppressWarnings("unchecked")
	public ConfigureOne loadConfigured(String ser) throws FileNotFoundException, IOException, ClassNotFoundException {
		ConfigureOne tmp;
		try (FileInputStream fis = new FileInputStream(RSimulation.fullPath(ser).toFile())) {
//			Input input = new Input(fis);
//			tmp = kryo.readObject(input, ConfigureOne.class);
//			input.close();
			Object tmp2 = new ObjectInputStream(fis).readObject();
			tmp = (ConfigureOne) tmp2;
			if (!(tmp instanceof RSimulationBuilder.ConfigureOne)) throw new ClassCastException("Not the correct type");
		}
		return tmp;
	}
	
	
	
	/**
	 * STAGE 1-2 Configuration
	 * Constructing this results in a builder with status=initialised.
	 * 
	 */
	public class ConfigureOne extends RSimulationBuilder<S,C,P,A> implements Serializable, Runnable {
		
		C config;
		RObservedSimulation<S,A> obsSim;
		int configBootstrap;
		String ser;
		
		
		// CONSTRUCTORS
		
		/**
		 * Clone constructor is called by ParameteriseOne when subclassing
		 * @param builder
		 */
		public ConfigureOne(ConfigureOne builder) {
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
					Optional.ofNullable(builder.directory), 
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
			log.debug("Initialised simulation configuration bootstrap {}", this.obsSim.getSimulation().getId());
			obsSim.state = State.INITIALIZED;
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
			getSimulation().startConfiguration();
			log.debug("Building agents {}", getSimulation().getId());
			getSimulation().createAgents();
			log.debug("Setting agent baselive {}", getSimulation().getId());
			getSimulation().streamAgents().forEach(a -> a.setupBaseline());
			getSimulation().finishConfiguration();
			
			// TODO: could have a list of named agent environments in the simulation (e.g.
			// a simple generic wrapper that can wrap a field or network...)
			log.debug("Finishing configuration {}", getSimulation().getId());
			obsSim.state = State.CONFIGURED;
			
			if (this.simulationObservers.size() > 0) {
				this.obsSim.initialiseObservatory();
				this.simulationObservers.forEach(
						o -> this.obsSim.getObservatory().get().observeSimulation(o)
				);
			}
			
			if (this.agentObservers.size() > 0) {
				this.obsSim.initialiseObservatory();
				this.agentObservers.forEach(
						a -> this.obsSim.getObservatory().get()
							.observeAgents(a)
				);
			}
			
			log.debug("Saving configured simulation {}", getSimulation().getId());
			
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
		public Stream<ParameteriseOne> parameteriseConfigured(int bootstraps) {
			return this.parameterisations.stream().flatMap( p -> 
				IntStream.range(0, bootstraps).mapToObj( i -> 
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
		private ParameteriseOne parameteriseConfigured(P parameters, int bootstrap) {
			log.debug("Parameterising configured simulation {}; bootstrap {}", getSimulation().getId(), bootstrap);
			ParameteriseOne tmp;
			String ser = RSimulation.relPathFrom(directory, reproduceAt, this.config, this.configBootstrap, parameters, bootstrap, null, "param.ser");
			try {
				if (useCache && Files.isRegularFile(RSimulation.fullPath(ser))) {
					log.debug("Loading parameterised simulation {}; bootstrap {}", getSimulation().getId(), bootstrap);
					return loadParameterised(ser);
				} {
					tmp = new ParameteriseOne(this, parameters, bootstrap);
					//TODO: consider how to run in a thread pool.
					log.debug("Parameterising simulation {}; bootstrap {}", getSimulation().getId(), bootstrap);
					tmp.run();
				}
			} catch (ClassNotFoundException | IOException e) {
				throw new RuntimeException(e);
			}
			return tmp;
		}
	}
	
	/**
	 * Used to reload a fully parameterised simulation at the specific point 
	 * before it is executed. This is a single bootstrap of a simulation, and
	 * if the go-to method if you need to restart the exact same simulation at 
	 * step zero.
	 * @param simulation
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public ParameteriseOne loadParameterised(S simulation) throws FileNotFoundException, IOException, ClassNotFoundException {
		String ser = simulation.getFilePath(directory, null, "build2.ser");
		return loadParameterised(ser);
	}
	
	
	@SuppressWarnings("unchecked")
	public ParameteriseOne loadParameterised(String ser) throws FileNotFoundException, IOException, ClassNotFoundException {
		ParameteriseOne tmp;
		try (FileInputStream fis = new FileInputStream(RSimulation.fullPath(ser).toFile())) {
//			Input input = new Input(fis);
//			tmp = kryo.readObject(input, ParameteriseOne.class);
//			input.close();
			Object tmp2 = new ObjectInputStream(fis).readObject();
			tmp = (ParameteriseOne) tmp2; 
			if (!(tmp instanceof RSimulationBuilder.ParameteriseOne)) throw new ClassCastException("Not the correct type"); 
		}
		return tmp;
	}
	
	/**
	 * This is responsible for taking a ConfigureOne and finalising the setup
	 * and wrapping it in a thread ready for execution.
	 */
	//TODO make this runnable, so it can be done in multiple threads,
	public class ParameteriseOne  extends ConfigureOne implements Runnable,Serializable {
		
		P parameterisation;
		int parameterisationBootstrap;
		
		// CONSTRUCTOR
		
		public ParameteriseOne(ConfigureOne configuredBuilder, P parameterisation) {
			this(configuredBuilder,parameterisation,0);
		}
		
		public ParameteriseOne(ConfigureOne configuredBuilder, P parameterisation, int bootstrap) {
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
			log.debug("Starting parameterisation {}", getSimulation().getId());
			this.getSimulation().startParameterisation();
			this.getSimulation().streamAgents().forEach(a -> a.initialiseStatus());
			// Schedule all the agents.
			this.getSimulation().finishParameterisation();
			log.debug("Finished parameterisation {}", getSimulation().getId());
			obsSim.state = State.PARAMETERISED;
			if (useCache) {
				log.debug("Saving parameterised simulation {}", getSimulation().getId());
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
		public RSimulationRunnable<S,A> buildThread() {
			if (!obsSim.getState().equals(State.PARAMETERISED)) throw new RuntimeException("Simulation must be parameterised before this is called.");
			obsSim.getSimulation().start();
			obsSim.getSimulation().initialiseScheduler();
			obsSim.getObservatory().ifPresent(o -> o.initialiseScheduler());
			return new RSimulationRunnable<S,A>(this.obsSim, Optional.ofNullable(this.directory));
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
}
