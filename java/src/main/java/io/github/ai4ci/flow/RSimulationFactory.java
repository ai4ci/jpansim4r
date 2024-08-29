package io.github.ai4ci.flow;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RAgentObserver;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RObservedSimulation.State;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationObserver;
import io.github.ai4ci.RSimulationParameterisation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSimulationFactory<
	S extends RSimulation<S,C,P,A>, 
	C extends RSimulationConfiguration, 
	P extends RSimulationParameterisation,
	A extends RAgent<A,S,?,?>> {

	Class<S> simulationType;
	String directory;
	boolean useCache;
	
	long seedBase = 0;
	LocalDate reproduceAt = LocalDate.now();
//	List<C> configurations = new ArrayList<C>();
//	List<P> parameterisations = new ArrayList<P>();
	List<RSimulationObserver<S,?>> simulationObservers = new ArrayList<RSimulationObserver<S,?>>();
	List<RAgentObserver<? extends A,?>> agentObservers = new ArrayList<RAgentObserver<? extends A,?>>();
	
	
	// Base builder constructors
	
	private RSimulationFactory(Class<S> simulationType, String directory, boolean useCache) {
		this.simulationType = simulationType;
		this.directory = directory;
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
		RSimulationFactory<S,C,P,A> ofType(Class<S> simulationType, String directory, boolean useCache) {
		return new RSimulationFactory<S,C,P,A>(simulationType, directory, useCache); 
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
	
	public RSimulationFactory<S,C,P,A> withLocalDate(LocalDate reproduceAt) {
		this.reproduceAt = reproduceAt;
		return this;
	}
	
	@SafeVarargs
	/**
	 * Setup simulation observers to be added to each simulation.
	 * @param observer
	 * @return
	 */
	public final RSimulationFactory<S,C,P,A> withObserver(RSimulationObserver<S,?>... observer) {
		return this.withObservers(Arrays.asList(observer));
	}
	
	public RSimulationFactory<S,C,P,A> withObservers(Collection<RSimulationObserver<S,?>> observers) {
		this.simulationObservers.addAll(observers);
		return this;
	}
	
	public final RSimulationFactory<S,C,P,A> withAgentObserver(RAgentObserver<? extends A,?> observer) {
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
			try {
				S simulation = simulationType.getDeclaredConstructor().newInstance();
				RObservedSimulation<S,A> obsSim = new RObservedSimulation<S,A>(simulation);
				return obsSim;
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(simulationType.getName()+" must provide a norgs public constructor",e);
			} catch (InvocationTargetException e) {
				throw new RuntimeException(e.getCause());
			}
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
		log.debug("[pipeline] initialised simulation configuration bootstrap {}", copy.getSimulation().getUrn());
		copy.getSimulation().setupStage1BeginConfiguration();
		log.debug("[pipeline] building agents {}", copy.getSimulation().getUrn());
		copy.getSimulation().setupStage2CreateAgents();
		log.debug("[pipeline] setting agents baselines {}", copy.getSimulation().getUrn());
		copy.getSimulation().streamAgents().forEach(a -> a.setupStage3SetAgentBaseline());
		copy.getSimulation().setupStage4FinishConfiguration();
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
		log.debug("[pipeline] finishing configuration {}", obsSim.getSimulation().getUrn());
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
		log.debug("[pipeline] starting parameterisation {}", copy.getSimulation().getUrn());
		copy.getSimulation().setupStage5StartParameterisation();
		copy.getSimulation().streamAgents().forEach(a -> a.setupStage6InitialiseAgentStatus());
		copy.getSimulation().setupStage7FinishParameterisation();
		log.debug("[pipeline] finishing parameterisation {}", copy.getSimulation().getUrn());
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
	public RObservedSimulation<S,A> bootstrapExecutions(RObservedSimulation<S,A> obsSim, int bootstrapId) {
		log.debug("[pipeline] execution simulation bootstrap {}", bootstrapId);
		RObservedSimulation<S,A> copy = SerializationUtils.clone(obsSim);
		copy.getSimulation().setExecutionBootstrapId(bootstrapId);
		copy.getSimulation().setSeed(seedBase);
		
		if (copy.hasNamedObservers()) {
			copy.initialiseObservatory();
			copy.getSimulation().getObservers().forEach(o ->
			copy.getObservatory().get().registerNamedObserver(o)
					);
			copy.getSimulation().streamAgents().forEach(a ->
			a.getObservers().forEach(o ->
			copy.getObservatory().get().registerNamedObserver(o)));
		}
		
		copy.getSimulation().start();
		copy.getSimulation().initialiseScheduler();
		copy.getObservatory().ifPresent(o -> o.initialiseScheduler());
		copy.setState(State.READY);
		return copy;
		
	}
	
	
	
}
