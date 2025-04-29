package io.github.ai4ci.flow;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RObservedSimulation.State;
import io.github.ai4ci.RObserver;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationFactory;
import io.github.ai4ci.RSimulationParameterisation;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds a configured and parameterised model.
 * @param <S>
 * @param <C>
 * @param <P>
 * @param <A>
 */
@Slf4j
public class RSimulationPipeline<
	S extends RSimulation<S,C,P,A>, 
	C extends RSimulationConfiguration, 
	P extends RSimulationParameterisation,
	A extends RAgent<A,S,?,?>> {

	// Class<S> simulationType;
	
	String directory;
	boolean useCache;
	RSimulationFactory<S,C,P,A> factory;
	long seedBase = 0;
	LocalDate reproduceAt = LocalDate.now();
//	List<C> configurations = new ArrayList<C>();
//	List<P> parameterisations = new ArrayList<P>();
	List<RObserver<S,?>> simulationObservers = new ArrayList<RObserver<S,?>>();
	List<RObserver<? extends A,?>> agentObservers = new ArrayList<RObserver<? extends A,?>>();
	
	
	// Base builder constructors
	
	private RSimulationPipeline(RSimulationFactory<S,C,P,A> factory, String directory, boolean useCache) {
		this.directory = directory;
		this.factory = factory;
		this.useCache = useCache;
		log.info("[pipeline] setting up simulation factory: "+directory);
	}
	
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
		RSimulationPipeline<S,C,P,A> ofType(RSimulationFactory<S,C,P,A> factory, String directory, boolean useCache) {
		return new RSimulationPipeline<S,C,P,A>(factory, directory, useCache); 
	}
	
//	// Base builder fluent methods
//	
//	@SuppressWarnings("unchecked")
//	public RSimulationFactory<S,C,P,A> withConfiguration(C... configuration) {
//		return withConfigurations(Arrays.asList(configuration));	
//	}
//	
//	public RSimulationFactory<S,C,P,A> withConfigurations(Collection<C> configurations) {
//		this.configurations.addAll(configurations);
//		return this;
//	}
//	
//	@SuppressWarnings("unchecked")
//	public RSimulationFactory<S,C,P,A> withParameterisation(P... parameterisation) {
//		return withParameterisations(Arrays.asList(parameterisation));
//	}
//	
//	public RSimulationFactory<S,C,P,A> withParameterisations(Collection<P> parameterisation) {
//		this.parameterisations.addAll(parameterisation);
//		return this;
//	}
//	
//	public RSimulationFactory<S,C,P,A> withNewParameterisation(Collection<P> parameterisation) {
//		this.parameterisations = new ArrayList<>();
//		this.parameterisations.addAll(parameterisation);
//		return this;
//	}
	
	public RSimulationPipeline<S,C,P,A> withLocalDate(LocalDate reproduceAt) {
		this.reproduceAt = reproduceAt;
		return this;
	}
	
	/**
	 * Setup simulation observers to be added to each simulation.
	 * @param observer
	 * @return
	 */
	public final RSimulationPipeline<S,C,P,A> withObserver(RObserver<S,?> observer) {
		this.simulationObservers.add(observer);
		return this;
	}
	
	public final RSimulationPipeline<S,C,P,A> withAgentObserver(RObserver<? extends A,?> observer) {
		this.agentObservers.add(observer);
		return this;
	}
	
	// Pipeline methods
	// ================
	
	/**
	 * Minimal observed simulation setup. This is the supplier method for 
	 * the flow publisher.
	 * @return an un-configured observed simulation of the correct type.
	 */
	public RSimulationSupplier<S,A> initialise(ThreadPoolExecutor executor) {
		return new RSimulationSupplier<S,A>(() -> {
			S simulation = factory.setupStage0ConstructUnconfiguredSimulation();
			RObservedSimulation<S,A> obsSim = new RObservedSimulation<S,A>(simulation);
			return obsSim;
		}, executor);
	}
	
	// TODO: load cached instead of configuring.
	
	/**
	 * Take an unconfigured bootstrapped observed simulation and generate the
	 * simulation configuration (e.g. environments and agents) 
	 * @param obsSim an unconfigured observed simulation
	 * @param config a configuration 
	 * @return the same observed simulation but configured 
	 */
	public RObservedSimulation<S,A> configure(RObservedSimulation<S,A> obsSim, Pair<Integer,C> config) {
		if (obsSim.atOrBeyondStage(State.CONFIGURED)) return obsSim;
		RObservedSimulation<S,A> copy = SerializationUtils.clone(obsSim);
		copy.getSimulation().setConfigurationBootstrapId(config.getKey());
		copy.getSimulation().setSeed(seedBase);
		copy.getSimulation().setConfiguration(config.getValue());
		log.info("[pipeline] initialised simulation configuration bootstrap {}", copy.getSimulation().getUrn());
		factory.setupStage1BeginConfiguration(copy.getSimulation());
		log.debug("[pipeline] building agents {}", copy.getSimulation().getUrn());
		factory.setupStage2CreateAgents(copy.getSimulation());
		log.debug("[pipeline] setting agents baselines {}", copy.getSimulation().getUrn());
		copy.getSimulation().streamAgents().forEach(a -> factory.setupStage3SetAgentBaseline(a));
		factory.setupStage4FinishConfiguration(copy.getSimulation());
		if (this.simulationObservers.size() + this.agentObservers.size() > 0) {
			log.debug("[pipeline] setting up simulation observatory {}", copy.getSimulation().getUrn());
			copy.initialiseObservatory();
			this.simulationObservers.forEach(
					o -> copy.getObservatory().get().observeSimulation(o)
					);
			this.agentObservers.forEach(
					a -> copy.getObservatory().get().observeAgents(a)
					);
		}
		log.info("[pipeline] finishing configuration {}", obsSim.getSimulation().getUrn());
		copy.setState(State.CONFIGURED);
		return copy;
	}
	
	
	
	//TODO: save configured
	
	//TODO: load parameterised instead of bootstrapping?
	// The order here is a bit difficult
	// because we are copying before paramterising we are doing two
	// steps where we want only one. 
	// at the moment it goes construct -> boot -> configure -> boot -> parameterise
	// when pipeline is pulled that means it will 
	
	
	
	// TODO: load cached instead of paramterising
	
	/**
	 * Parameterise an observed simulation clone
	 * @param obsSim
	 * @param parameterisation
	 * @return
	 */
	public RObservedSimulation<S,A> parameterise(RObservedSimulation<S,A> obsSim, Pair<Integer,P> parameterisation) {
		if (obsSim.atOrBeyondStage(State.PARAMETERISED)) return obsSim;
		RObservedSimulation<S,A> copy = SerializationUtils.clone(obsSim);
		copy.getSimulation().setParameterisationBootstrapId(parameterisation.getKey());
		copy.getSimulation().setSeed(seedBase);
		copy.getSimulation().setParameterisation(parameterisation.getValue());
		log.info("[pipeline] starting parameterisation {}", copy.getSimulation().getUrn());
		factory.setupStage5StartParameterisation(copy.getSimulation());
		copy.getSimulation().streamAgents().forEach(a -> factory.setupStage6InitialiseAgentStatus(a));
		factory.setupStage7FinishParameterisation(copy.getSimulation());
		log.info("[pipeline] finishing parameterisation {}", copy.getSimulation().getUrn());
		copy.setState(State.PARAMETERISED);
		return copy;
	}
	
	/** Clone an parameterised obsSim prior to execution, 
	 * assign a new parameter bootstrap and set a new
	 * random seed. This is an expensive operation as the simulation is fully 
	 * constructed at this point. After this operation no more cloning is 
	 * required.
	 * 
	 * @param obsSim the 
	 * @param bootstrapId
	 * @return a new obsSim clone with different bootstrap id and seed
	 */
	public RObservedSimulation<S,A> bootstrap(RObservedSimulation<S,A> obsSim, int bootstrapId) {
		RObservedSimulation<S,A> copy = SerializationUtils.clone(obsSim);
		copy.getSimulation().setExecutionBootstrapId(bootstrapId);
		copy.getSimulation().setSeed(seedBase);
		log.debug("[pipeline] execution simulation bootstrap: {}",  copy.getSimulation().getUrn());
		copy.initialiseObservatory();
		// if (copy.hasNamedObservers()) { copy.initialiseObservatory(); }
		copy.getSimulation().start();
		copy.getSimulation().initialiseScheduler();
		copy.getObservatory().ifPresent(o -> o.initialiseScheduler());
		copy.setState(State.READY);
		return copy;
		
	}
	
	
	
}
