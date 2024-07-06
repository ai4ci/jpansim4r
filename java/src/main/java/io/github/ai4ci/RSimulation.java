package io.github.ai4ci;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.stats.MTWrapper;
import io.github.ai4ci.stats.Sampler;
import lombok.extern.slf4j.Slf4j;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.engine.Steppable;

/**
 * 
 * Setup functions:
 * Configuration:
 * configuration is set; and random number seed defined for each bootstrap
 * first setupEnvironment() is called to create up fields/networks
 * then createAgents() to create the agents
 * then for each agent the setupBaseline() function is called.
 * 
 * 
 * Main simulation loop:
 * 
 * At the beginning of every step the checkComplete() function is called.
 * any other agent processing is done.
 * then named observers are all updated
 * then unnamed observers (in the observatory)
 * @param <S>
 * @param <C>
 * @param <P>
 * @param <A>
 */
@Slf4j
public abstract class RSimulation<
		S extends RSimulation<S,C,P,A>,
		C extends RSimulationConfiguration,
		P extends RSimulationParameterisation,
		A extends RAgent<A,S,?,?>
	> extends SimState implements Serializable, RObservable {

	public RSimulation() {
		super(0L);
		this.random = new MTWrapper(0L);
		this.sampler = new Sampler((MTWrapper) random);
	}
	
	
	// JAVA BEAN SETTERS AND GETTERS
	
	/**
	 * Set seed takes a long seed and creates a deterministic seed depending on
	 * the configuration and parameterisation of the simulation plus any 
	 * bootstrap identifiers. This is called by the builder at various points
	 * but should not be needed to be called otherwise. Builders are responsible
	 * for maintaining seedBase during the scope of the build process.. 
	 */
	public void setSeed(long seedBase) {
		long tmp = RSimulation.seedFrom(seedBase, config, configBootstrapId, params, paramBootstrapId);
		super.setSeed(tmp);
	}
	
	private C config;
	private P params;
	private P oldParams;
	private List<A> agents = new ArrayList<A>();
	private LocalDate jobDate = LocalDate.now();
	private int configBootstrapId = 0;
	private int paramBootstrapId = 0;
	private ConcurrentMap<String, RSimulationObserver<S,?>> observers = new ConcurrentHashMap<>();
	transient private ConcurrentMap<String, Object> cache = new ConcurrentHashMap<>();
	private Sampler sampler;
	private boolean complete = false;

	public void setParameterisationBootstrapId(int bootstrapId) {
		this.paramBootstrapId = bootstrapId;
	}
	public void setConfigurationBootstrapId(int bootstrapId) {
		this.configBootstrapId = bootstrapId;
	}
		
	@SuppressWarnings("unchecked")
	public <X> Optional<X> cached(String name, Class<X> type, Function<S,Optional<X>> mapper) {
		if (this.cache == null) this.cache = new ConcurrentHashMap<>();
		if (!cache.containsKey(name)) {
			Optional<X> tmp = mapper.apply((S) RSimulation.this);
			cache.put(name, tmp);
		}
		return (Optional<X>) cache.get(name);
	}
	
	protected void clearCache() {
		if (this.cache == null) this.cache = new ConcurrentHashMap<>();
		this.cache.clear();
	}
	/**
	 * Gets a urn style id for a specific simulation configuration, parameterisation
	 * and replication.
	 * @return A string id unique for the simulation bootstrap.
	 */
	public String getId() {
		return RSimulation.idFrom(getJobDate(), config, configBootstrapId, params, paramBootstrapId, null);
	}
	
	/**
	 * Gets a urn style id for a specific simulation configuration, parameterisation
	 * and replicate, plus unique for a specific simulation step..
	 * @return A string id unique for the simulation step 
	 */
	public String getStepId() {
		return RSimulation.idFrom(getJobDate(), config, configBootstrapId, params, paramBootstrapId, (int) this.schedule.getSteps());
	}
	
	
	
	/**
	 * Utility method to return a stats distribution random number sampler.
	 * @return
	 */
	public Sampler sampler() {
		return sampler;
	}
	
	/**
	 * The role of this is to add the agent to the simulation, and generate a
	 * unique id for the agent in this simulation. This happens in the agent
	 * constructor.
	 * 
	 * It will not register the agent with the scheduler, which happens once
	 * agent configuration and parameterisation is complete as part of the 
	 * build process.. 
	 *  
	 * @param <A> the supertype of agents in this simulation
	 * @return nothing
	 */
	protected void addAgent(A agent) {
		agents.add(agent);
		agent.id = this.generateAgentId();
	}
	
	/**
	 * Override in extension as a `return this;` to allow correctly typed
	 * extensions.
	 */
	public abstract S self();
	
	/**
	 * Defines when a simulation is complete. The MASON default is when nothing
	 * else is scheduled but this is inconvenient when some scheduling is done 
	 * to update the model parameters or observe it. This flag is checked 
	 * before anything is added to the schedule.  
	 * @return
	 */
	public boolean isComplete() {
		return complete;
	};
	
	
	/** 
	 * In general this should only be called by checkComplete at the end of every 
	 * simulation. However if we need to force an exit this could be useful. 
	 * @param complete
	 */
	protected void setComplete(boolean complete) {
		this.complete = complete;
	}
	
	/**
	 * Scheduled to be run once at the end of every step to determine status of
	 * the simulation. The result is cached automatically using the `setComplete`
	 * method and available (at the next step) of simulation via the 
	 * `isComplete()` method.
	 * @return
	 */
	protected abstract boolean checkComplete();
	
	/**
	 * Aim to use this as an internal function when we want to set up a 
	 * specific historical variable for the model to use. e.g. a delayed 
	 * incidence count or similar. This will be called by the model setup 
	 * e.g.  
	 * setup() { addNamedObserver("incidence", new IncidenceObserver() {...})
	 * }  
	 * @param name
	 * @param observer
	 * @param observatory
	 */
	// @Override
	public void registerNamedObserver(RSimulationObserver<S,?> observer) {
		this.observers.put(observer.getName(), observer);
		observer.setSubject(self());
	}
	
	// @Override
	public Stream<RSimulationObserver<S,?>> getObservers() {
		return observers.values().stream();
	}
	
	/**
	 * Intended to be used in a specific simulation extension methods to allow the 
	 * history of the simulation determine control measures.
	 */
	// @Override
	@SuppressWarnings("unchecked")
	public <X> List<X> getNamedObservation(String name, Class<X> type) {
		RSimulationObserver<S,?> obs = this.observers.get(name);
		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
		return (List<X>) obs.getObservation();
	}
	
	public <X> List<X> getNamedObservation(Enum<?> enu, Class<X> type) {
		return getNamedObservation(enu.name(),type);
	}

	public <X> Optional<X> getLastNamedObservation(Enum<?> enu, Class<X> type) {
		return getLastNamedObservation(enu.name(),type);
	}
	
	@SuppressWarnings("unchecked")
	public <X> Optional<X> getLastNamedObservation(String name, Class<X> type) {
		RSimulationObserver<S,?> obs = this.observers.get(name);
		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
		return (Optional<X>) obs.getLastObservation();
	}
	
	/**
	 * The default implementation is an arraylist of agents
	 *  
	 * @param <A> the supertype of agents in this simulation
	 * @return a stream of the supertype of agents in this model.
	 */
	public Stream<A> streamAgents() {
		return agents.stream();
	};
	
	
	
	
	/**
	 * Instances of this method will define the specific type of RSimulationConfiguration
	 * that is valid for this simulation. This will always be populated.
	 * @param <X>
	 * @return
	 */
	public C getConfiguration() {
		return config;
	};
	
	/**
	 * This will only be populated after parameterisation
	 * @return
	 */
	public P getParameterisation() {
		return params;
	};
	
	public abstract void updateParameterisation();

	protected void setConfiguration(C config) {
		this.config = config;
	};
	public void setParameterisation(P params) {
		this.params = params;
	};

	protected void copyParameterisation() {
		this.oldParams = SerializationUtils.clone(params);
	}
	
	public Optional<P> getLastParameterisation() {
		return Optional.ofNullable(oldParams);
	};

	
	/**
	 * Get all of a specific subType of agents from the model.
	 * @param <A>
	 * @param <X>
	 * @param agentSubType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <X extends A> Stream<X> streamAgents(Class<X> agentSubType) {
		return this.streamAgents()
			.filter(a -> agentSubType.isAssignableFrom(a.getClass()))
			.map(a -> (X) a);
	};
	
	/**
	 * Start is called by GUIState
	 * Called immediately prior to starting the simulation, or in-between 
	 * simulation runs.
	 * 
	 * Or by SimState.doLoop: 
	 * Called once if the loop is not result of loading from a checkpoint
	 * 
	 * After this is called the main loop calling schedule.step is called.
	 * This is the last opportunity to do anything with regards to set up in
	 * both the SimState doLoop function and the GUI. 
	 * 
	 */
	public void start() {
		if (this.isComplete()) {
			super.start();
			initialiseScheduler();
		}
	}
	
	
	public void initialiseScheduler() {
		// Make sure all the named observers get stepped at the end of each simulation 
		// The fact that the things they are observing are not complete does 
		// not matter at this point as this code will only be run at the end of the first 
		// step.
		this.schedule.addAfter(new Steppable() {
			@SuppressWarnings("unchecked")
			@Override
			public void step(SimState s) {
				S simulation = ((S) s);
				simulation.streamAgents().forEach(a -> {
					a.getObservers().forEach(o -> o.update());
				});
				simulation.getObservers().forEach(o -> o.update());
			}
		});
		
		// Calculate the completeness flag at the beginning of each step
		// This will get rescheduled for the next round
		// Clears caches at the start of each step
		// and creates a clone of the parameterisation or status using a 
		// clone operation (?should be a copy constructor?)
		this.schedule.addBefore(new Steppable() {
				@SuppressWarnings("unchecked")
				@Override
				public void step(SimState s) {
					S simulation = ((S) s);
					complete = simulation.checkComplete();
					simulation.setComplete(complete);
					simulation.clearCache();
					simulation.copyParameterisation();
					simulation.streamAgents()
						.filter(a -> a.remainsActive(simulation))
						.forEach(a -> {
							a.clearCache();
							a.copyStatus();
						});
				}
		});
		
		this.streamAgents()
			.filter(a -> a.remainsActive())
			.forEach(a -> this.getSchedule().scheduleOnce(a));
		
		this.getSchedule().scheduleOnce(new RSteppable.UntilComplete<S>(10000) {
			@Override
			public void doStep(S simulation) {
				simulation.updateParameterisation();
			}
		});
	}
	
	
	/**
	 * The first phaze of the model configuration. At this stage the 
	 * simulation has been initialised, with a configuration and
	 * the configuration bootstrap id has been set, in case there are multiple 
	 * model replicants. At this stage the model hss configuration but does not have agents and 
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
	protected abstract void startConfiguration();

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
	protected abstract void createAgents();

	/**
	 * This hook is called at the end of the configuration stage, prior to the
	 * configured simulation being finalised and written to disk.
	 * The default registers all the named observers with the scheduler and makes
	 * sure they are triggered at the end of every round, and it makes sure that
	 * `checkComplete` is called at the beginning of each cycle. Any 
	 * extension to this method must call `super()`.
	 */
	protected void finishConfiguration() {
		
		
		
		
	};



	/**
	 * By default a no-op. This is called once the simulation has been 
	 * configured and agents created, and parameterisation initialised, but 
	 * before the agents initial status/behaviour is set, it might be used to 
	 * derive simulation wide values from the combination of configuration and 
	 * parameterisation.
	 */
	protected void startParameterisation() {}
	
	/**
	 * This is called when all the parameterisation of the 
	 * agents is complete. By this point all agents are in their start state.
	 * It is possible this might be used to conditionally wire agents into 
	 * their environments depending on their start conditions. 
	 */
	protected void finishParameterisation() {}; 

	protected int generateAgentId() {
		return agents.size()-1;
	}

	// UTILTY FUNCTIONS
	
	public Schedule getSchedule() {
		// N.B. to think about an RSchedule wrapper. Also will tg
		return this.schedule;
	}
	
	public Long getSimTime() {
		return this.getSchedule().getSteps();
	}
	
	/**
	 * Writes the current simulation state to a file which will be labelled
	 * with the current simulation step number, e.g. XX-010.sim.ser. Calling this 
	 * is generally to be avoided in favour of RObservedSimulation.save as this
	 * does not keep the state of non-named observers. However if we are running
	 * in a GUI this is what will be called.
	 * @param directory
	 */
	public void writeToCheckpoint(String directory) {
		try {
			super.writeToCheckpoint(
					Files.newOutputStream(
							RSimulation.fullPath(
								this.getFilePath(
										directory, 
										(int) this.schedule.getSteps(),
										"sim.ser"
									)
							)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public S resetToStart(String directory) throws FileNotFoundException, IOException {
		String tmp = getFilePath(directory, 0, "sim.ser");
		try (FileInputStream fis = new FileInputStream(RSimulation.fullPath(tmp).toFile())) {
			Object tmp2 = new ObjectInputStream(fis).readObject();
			return (S) tmp2;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * The path to the simulation
	 * @param basePath
	 * @return
	 */
	protected String getBaselineConfigFilePath(String basePath) {
		return RSimulation.relPathFrom(basePath, getJobDate(), this.config, this.configBootstrapId, 
				null, null, null, "build.ser");
	}
	
	protected String getFilePath(String basePath, Integer step, String fileType) {
		return RSimulation.relPathFrom(basePath, 
				this.getJobDate(), 
				this.config, this.configBootstrapId, 
				this.params, this.paramBootstrapId, 
				step, 
				fileType);
	}
	
	public static <
			C extends RSimulationConfiguration, 
			P extends RSimulationParameterisation
		> String idFrom(
			LocalDate jobDate,
			C config2,
			Integer configBootstrap,
			P params2,
			Integer paramBootstrap,
			Integer stepId
		) {
		String tmp = jobDate.format(DateTimeFormatter.BASIC_ISO_DATE); 
		if (config2 != null) {
			tmp += ":"+config2.getConfigurationName();
		}
		if (configBootstrap != null) tmp += ":"+configBootstrap.toString();
		if (params2 != null) {
			tmp += ":"+params2.getParameterisationName();
			if (paramBootstrap != null) tmp += ":"+paramBootstrap.toString();
		}
		if (stepId != null) {
			tmp += ":"+stepId.toString();
		}
		return tmp;
	}
	
	public static Path fullPath(String path) {
		Path home = Path.of(System.getProperty("user.home"));
		Path tmp = home.resolve(path);
		try {
			Files.createDirectories(tmp.getParent());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return tmp;
	}
	
	public static <
			C extends RSimulationConfiguration, 
			P extends RSimulationParameterisation
		> String relPathFrom(String directory, 
			LocalDate jobDate,
			C config2,
			Integer configBootstrap,
			P params2,
			Integer paramBootstrap,
			Integer stepId,
			String fileType) {
		Path home = Path.of(System.getProperty("user.home"));
		if (directory == null) directory = "";
		Path tmp = home.resolve(directory).resolve(jobDate.format(DateTimeFormatter.BASIC_ISO_DATE)); 
		if (config2 != null) {
			tmp = tmp.resolve(config2.getConfigurationName());
		}
		if (configBootstrap != null) tmp = tmp.resolve(configBootstrap.toString());
		if (params2 != null) {
			tmp = tmp.resolve(params2.getParameterisationName());
			if (paramBootstrap != null) tmp = tmp.resolve(paramBootstrap.toString());
		}
		if (stepId != null) {
			tmp = Path.of(tmp.toString()+"-"+String.format("%03d",stepId)+"."+fileType);
		} else {
			tmp = Path.of(tmp.toString()+"."+fileType);
		}
		try {
			Files.createDirectories(tmp.getParent());
		} catch (IOException e) {
			//ignore.
		}
		return home.relativize(tmp).toString();
	}

	/**
	 * generate a reproducible seed that is different from every configuration,
	 * bootstrap, parameterisation, bootstrap.
	 * @param <C> the type of configuration
	 * @param <P> the type of parameterisation
	 * @param seedBase a base value for a seed
	 * @param config2 a config object
	 * @param configBootstrap which replicates
	 * @param params2 a param object
	 * @param paramBootstrap which replicate
	 * @return
	 */
	public static <
		C extends RSimulationConfiguration, 
		P extends RSimulationParameterisation
	> long seedFrom(long seedBase, 
		C config2, 
		Integer configBootstrap,
		P params2,
		Integer paramBootstrap) {
		
		long tmp = (long) (
				seedBase*((int) Math.pow(31,4))+
				(config2 != null ? config2.getConfigurationName().hashCode()*((int) Math.pow(31,3)) : 0) +
				(configBootstrap != null ? configBootstrap*((int) Math.pow(31,2)) : 0) +
				(params2 != null ? params2.getParameterisationName().hashCode()*((int) Math.pow(31,1)) : 0) +
				(paramBootstrap != null ? paramBootstrap : 0));
		
		return tmp;
	}

	public LocalDate getJobDate() {
		return jobDate;
	}

	public A getAgentById(int id) {
		return this.agents.get(id);		
	}
}
