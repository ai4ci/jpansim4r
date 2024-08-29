package io.github.ai4ci.builder;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RAgentObserver;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationObserver;
import io.github.ai4ci.RSimulationParameterisation;
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
	
	private RSimulationBuilder(Class<S> simulationType, String directory, boolean useCache) {
		this(simulationType, directory, new ArrayList<C>(), new ArrayList<P>(), 
				new ArrayList<RSimulationObserver<S,?>>(),
				new ArrayList<RAgentObserver<? extends A,?>>(),
				0,
				LocalDate.now(),
				useCache
				);
	}
	
	protected RSimulationBuilder(Class<S> simulationType, String directory, List<C> configurations,
			List<P> parameterisations, 
			List<RSimulationObserver<S,?>> observers,
			List<RAgentObserver<? extends A,?>> agentObservers,
			long seedBase,
			LocalDate reproduceAt,
			boolean useCache
			) {
		if (directory == null) throw new RuntimeException("Directory must be provided.");
		this.simulationType = simulationType;
		this.directory = directory;
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
		RSimulationBuilder<S,C,P,A> ofType(Class<S> simulationType, String directory, boolean useCache) {
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
	public Stream<ConfigureOne<S,C,P,A>> initialiseSimulation(int bootstraps) {
		log.debug("Initialising simulation with {} bootstraps", bootstraps);
		return this.configurations.stream().parallel().flatMap(
				conf -> IntStream.range(0, bootstraps).parallel().mapToObj(
						i -> this.constructSimulation(conf,i)));
	}
	
	/**
	 * initialises a single simulation builder or loads it from disk.
	 * @param config the configuration
	 * @param bootstrap which bootstrap replicate of the simulation setup to use?
	 * @param useCache cache the configured simulation.
	 * @return a configured simulation.
	 */
	private ConfigureOne<S,C,P,A> constructSimulation(C config, int bootstrap) {
		log.debug("Configuring simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
		ConfigureOne<S,C,P,A>  tmp; //= new ConfigureOne(this, config, bootstrap);
		// Path ser = directory.resolve(tmp.getSimulation().getConfigurationId()+".ser");
		String ser = RSimulation.relPathFrom(this.directory, this.reproduceAt, config, bootstrap, null, null, null, "build.ser");
		try {
			if (useCache && Files.isRegularFile(RSimulation.fullPath(ser))) {
				log.debug("Loading configuration for simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
				return loadConfigured(ser);
				
			} else {
				log.debug("Building configuration for simulation {} bootstrap {}", config.getConfigurationName(), bootstrap);
				//TODO: wrap in a thread. submit to a thread pool
				tmp = new ConfigureOne<S,C,P,A>(this, config, bootstrap);
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
	
	public ConfigureOne<S,C,P,A>  loadConfigured(S simulation) throws FileNotFoundException, IOException, ClassNotFoundException {
		String ser = simulation.getBaselineConfigFilePath(directory);
		return loadConfigured(ser);
	}
		
	@SuppressWarnings("unchecked")
	public ConfigureOne<S,C,P,A> loadConfigured(String ser) throws FileNotFoundException, IOException, ClassNotFoundException {
		ConfigureOne<S,C,P,A>  tmp;
		try (FileInputStream fis = new FileInputStream(RSimulation.fullPath(ser).toFile())) {
//			Input input = new Input(fis);
//			tmp = kryo.readObject(input, ConfigureOne.class);
//			input.close();
			Object tmp2 = new ObjectInputStream(fis).readObject();
			tmp = (ConfigureOne<S,C,P,A>) tmp2;
			if (!(tmp instanceof ConfigureOne)) throw new ClassCastException("Not the correct type");
		}
		return tmp;
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
	public ParameteriseOne<S,C,P,A>  loadParameterised(S simulation) throws FileNotFoundException, IOException, ClassNotFoundException {
		String ser = simulation.getFilePath(directory, null, "build2.ser");
		return loadParameterised(ser);
	}
	
	
	@SuppressWarnings("unchecked")
	public ParameteriseOne<S,C,P,A>  loadParameterised(String ser) throws FileNotFoundException, IOException, ClassNotFoundException {
		ParameteriseOne<S,C,P,A>  tmp;
		try (FileInputStream fis = new FileInputStream(RSimulation.fullPath(ser).toFile())) {
//			Input input = new Input(fis);
//			tmp = kryo.readObject(input, ParameteriseOne.class);
//			input.close();
			Object tmp2 = new ObjectInputStream(fis).readObject();
			tmp = (ParameteriseOne<S,C,P,A>) tmp2; 
			if (!(tmp instanceof ParameteriseOne)) throw new ClassCastException("Not the correct type"); 
		}
		return tmp;
	}
}
