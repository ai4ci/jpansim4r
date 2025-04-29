package io.github.ai4ci;

import java.io.Serializable;

import lombok.extern.slf4j.Slf4j;

/**
 * A simulation factory holds all the processes for building a simulation and
 * associated agents.
 *  
 * @param <S> The simulation type
 * @param <A> The agent supertype
 */
@Slf4j
public abstract class RSimulationFactory<S extends RSimulation<S,C,P,A>,
		C extends RSimulationConfiguration,
		P extends RSimulationParameterisation,
		A extends RAgent<A,S,?,?>> implements Serializable {

	/**
	 * Construct a simulation with any defaults needed.
	 * @return a simulation
	 */
	public abstract S setupStage0ConstructUnconfiguredSimulation();
	
	/**
	 * The first phase of the model configuration. At this stage the 
	 * simulation has been initialised, with a configuration and
	 * the configuration bootstrap id has been set, in case there are multiple 
	 * model replicants. At this stage the model has configuration but does not have agents and 
	 * does not have parameterisation.   
	 * 
	 * Tasks for this method are:
	 * 
	 * 1) Setup the environments that the agents operate in. This maybe a 2DField or
	 * a network or both, or something that we haven't thought of yet. In some
	 * way it defines how the agents interact with each other. It also does
	 * not need to exist. The simulation developer is responsible for 
	 * wiring it up and making it available to the agent, ?and GUI.
	 * 
	 * 2) Setup and named observers that may be needed to retrieve the state or
	 * history of the simulation, for the purposes of making decisions within the
	 * simulation. This will be done by `registerNamedObserver`
	 * 
	 * Following this method the `createAgents` method is called.
	 */
	public abstract S setupStage1BeginConfiguration(S simulation);

	/**
	 * This is a factory method and
	 * is expected construct new agents with new Agent(this, ...). Calling this 
	 * constructor adds an agent to the simulation so addAgent does nto need to 
	 * be called.
	 *  
	 * Following this the agents will have their 
	 * setupBaseline() method called so this is really just for creating the agents
	 * and does not need to define their baseline configuration or initial state 
	 * of the agent does not yet need to be defined.
	 * 
	 * Agents are expected to have a implementation 
	 * with immutable baseline parameters created using the setupBaseline(), 
	 * and mutable status object.
	 * 
	 * Once constructed we here manage any insertion of the agent 
	 * into their environment is also done here. Once the agent is constructed
	 * it will be wired into the schedule automatically, depending on their 
	 * remainsActive() flag, and this does not need to be handled.
	 * 
	 * After each of the agents has been constructed then their setupBaseline
	 * is called before the simulations `finishConfiguration` method is called.
	 * Which will schedule the agents. 
	 */
	public abstract S setupStage2CreateAgents(S simulation);

	
	// INITIALISATION FUNCTIONS
	
	/**
	 * Called during construction. Job is to construct the baseline configuration
	 * for this agent and set it using setBaseline(). This is called immediately
	 * after simulation configuration, and the method .getSimulation().getConfiguration()
	 * will work but before parameterisation.
	 * 
	 * It is also expected to at this point register named observers that are
	 * part of the agent, and which are used to drive behaviour of the agent.
	 * These should be added with `registerNamedObserver()`. or using
	 * the convenience methods keepHistory, keepFullHistory, keepLastValue, and 
	 * keepHistoryList in the agent. 
	 */
	public abstract A setupStage3SetAgentBaseline(A agent);
	
	
	
	
	/**
	 * This hook is called at the end of the configuration stage, prior to the
	 * configured simulation being finalised and written to disk.
	 * The default registers all the named observers with the scheduler and makes
	 * sure they are triggered at the end of every round, and it makes sure that
	 * `checkComplete` is called at the beginning of each cycle. Any 
	 * extension to this method must call `super()`.
	 */
	public S setupStage4FinishConfiguration(S simulation) {
		simulation.setupHistory();
		simulation.streamAgents().forEach(a -> a.setupHistory());
		log.debug(simulation.getUrn()+ " simulation configuration complete (stage 4).");
		return simulation;
	};

	/**
	 * By default a no-op. This is called once the simulation has been 
	 * configured and agents created, and parameterisation initialised, but 
	 * before the agents initial status/behaviour is set, it might be used to 
	 * derive simulation wide values from the combination of configuration and 
	 * parameterisation.
	 */
	public S setupStage5StartParameterisation(S simulation) {
		return simulation;
	}
	
	/**
	 * Called during parameterisation. This gets called before the simulation starts
	 * but both simulation configuration and parameterisation is complete and 
	 * the agent baseline will be defined.
	 * Tasks here are 
	 * 1) construct and initialise the status JavaBean with values so that
	 * it is ready to be updated during the stepping.
	 * this is being done on a once per parameterisation bootstrap.
	 * At this stage the model parameterisation is complete so the 
	 * status can reference the simulation parameters or config, and a 
	 * RNG generates log normal (e.g. sampler().logNormal()).
	 * By the time this is finished the remainsActive() function must work. 
	 */
	public abstract A setupStage6InitialiseAgentStatus(A agent);
	
	/**
	 * This is called when all the parameterisation of the 
	 * agents is complete. By this point all agents are in their start state.
	 * It is possible this might be used to conditionally wire agents into 
	 * their environments depending on their start conditions. 
	 */
	public S setupStage7FinishParameterisation(S simulation) {
		log.debug(simulation.getUrn()+ " simulation parameterisation complete (stage 7).");
		return simulation;
	}; 
	
	
	
	public S buildSingleSimulation(C config, P parameterisation, long seedBase) {
		
		S simulation = this.setupStage0ConstructUnconfiguredSimulation();
		simulation.setConfigurationBootstrapId(0);
		simulation.setSeed(seedBase);
		simulation.setConfiguration(config);
		this.setupStage1BeginConfiguration(simulation);
		this.setupStage2CreateAgents(simulation);
		simulation.streamAgents().forEach(a -> this.setupStage3SetAgentBaseline(a));
		this.setupStage4FinishConfiguration(simulation);
		simulation.setParameterisationBootstrapId(0);
		simulation.setSeed(seedBase);
		simulation.setParameterisation(parameterisation);
		this.setupStage5StartParameterisation(simulation);
		simulation.streamAgents().forEach(a -> this.setupStage6InitialiseAgentStatus(a));
		this.setupStage7FinishParameterisation(simulation);
		simulation.setExecutionBootstrapId(0);
		simulation.setSeed(seedBase);
		simulation.start();
		simulation.initialiseScheduler();
		return simulation;
			
		
	}
	
}
