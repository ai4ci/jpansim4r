package io.github.ai4ci;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.stats.Sampler;

/**
 * 
 * The agent is a immutable containing baseline configuration with a javabean
 * status field containing the current state. Important historics or previous
 * values can be recorded as a namedobserver, and accessed through 
 * getNamedObservation().
 * 
 * Firstly the agent is constructed by the simulation function (createAgents())
 * the setupBaseline() is called on every agent during model configuration stage, 
 * and this should deal with assigning any agent specific configuration variables
 *  
 * then initialise status
 * 
 * @param <A>
 * @param <S>
 * @param <C>
 * @param <T>
 */
public abstract class RAgent<
		A extends RAgent<A,S,B,T>, 
		S extends RSimulation<S,?,?,A>,
		B extends RAgentBaseline,
		T extends RAgentStatus
	> implements RSteppable<S>, Serializable, RObservable {

	int id;
	S simulation;
	T status;
	T oldStatus;
	B baseline;
	ConcurrentMap<String, RAgentObserver<A,?>> observers = new ConcurrentHashMap<>();
	transient ConcurrentMap<String,Object> cache = new ConcurrentHashMap<>();
	
	
	/**
	 * Overriding this constructor should set up the 
	 * @param simulation
	 */
	public RAgent(S simulation) {
		this.simulation = simulation;
		// simulation assigns id when attached. does not schedule.
		simulation.addAgent(self());
		
	}
	
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
	 * 
	 * @param <X>
	 * @param configuration
	 */
	public abstract void setupBaseline();
	
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
	public abstract void initialiseStatus();
	
	
	/**
	 * The step method should largely interact with the simulation parameters,
	 * the current agent status, the current agent named observers, and the
	 * current simulation. 
	 */
	public void doStep(S simulation) {
		updateStatus();
		changeBehaviour();
	}
	
	/**
	 * Is this agent still in the simulation? For some the 
	 * default simulation.isComplete()
	 * flag will be enough. Otherwise this should reference a flag set up by 
	 * updateStatus()
	 */
	public abstract boolean remainsActive();
	
	/**
	 * Is this agent still in the simulation? For some the 
	 * default simulation.isComplete()
	 * flag will be enough. Otherwise this should reference a flag set up by 
	 * updateStatus()
	 */
	public boolean remainsActive(S simulation) {
		return remainsActive();
	}
	
	/** 
	 * the agent id, unique per simulation.
	 * @return
	 */
	public int getId() {
		return id;
	}
	
	/** 
	 * This only needs a specific vanilla getter and setter implementation
	 * of the correct subtype.
	 * @param <X>
	 * @return
	 */
	public T getStatus() {
		return status;
	};
	
	public Optional<T> getOldStatus() {
		return Optional.ofNullable(oldStatus);
	};
	
	protected void setStatus(T status) {
		this.status = status;
	};

	public B getBaseline() {
		return baseline;
	}

	protected void setBaseline(B baseline) {
		this.baseline = baseline;
	}


	
	public S getSimulation() {return simulation;}
	
	public Sampler sampler() {return getSimulation().sampler();}
	
	// @Override
	public Stream<RAgentObserver<A,?>> getObservers() {
		return observers.values().stream();
	}
	
	// @Override
	@SafeVarargs
	public final void registerNamedObserver(RAgentObserver<A,?>... observers) {
		for (RAgentObserver<A,?> observer: observers) {
			this.observers.put(observer.getName(), observer);
			observer.setSubject(self());
		}
	}
	
	@SuppressWarnings("unchecked")
	public <X> void keepHistory(
			Enum<?> name, Class<X> type, RAgentObserver.Mapper<A,X> mapper, int length) {
		this.registerNamedObserver(
			new RAgentObserver.History<A, X>((Class<A>) self().getClass(), name, type, mapper, length)
		);
	}
	
	@SuppressWarnings("unchecked")
	public <X> void keepFullHistory(
			Enum<?> name, Class<X> type, RAgentObserver.Mapper<A,X> mapper) {
		this.registerNamedObserver(
			new RAgentObserver.History<A, X>((Class<A>) self().getClass(), name, type, mapper, null)
		);
	}
	
	@SuppressWarnings("unchecked")
	public <X> void keepLastValue(
			Enum<?> name, Class<X> type, RAgentObserver.Mapper<A,X> mapper) {
		this.registerNamedObserver(
			new RAgentObserver.Last<A, X>((Class<A>) self().getClass(), name, type, mapper)
		);
	}
	
	@SuppressWarnings("unchecked")
	public <X> void keepHistoryList(
			Enum<?> name, Class<X> type, RAgentObserver.ListMapper<A,X> mapper, int length) {
		this.registerNamedObserver(
			new RAgentObserver.ListHistory<A, X>((Class<A>) self().getClass(), name, type, mapper, length)
		);
	}

	@SuppressWarnings("unchecked")
	public <X> Optional<X> cached(String name, Class<X> type, Function<A,Optional<X>> mapper) {
		if (this.cache == null) this.cache = new ConcurrentHashMap<>();
		if (!cache.containsKey(name)) {
			Optional<X> tmp = mapper.apply((A) RAgent.this);
			cache.put(name, tmp);
		}
		return (Optional<X>) cache.get(name);
	}
	
	@SuppressWarnings("unchecked")
	public <X> List<X> cachedList(String name, Class<X> subtype, Function<A,List<X>> mapper) {
		if (this.cache == null) this.cache = new ConcurrentHashMap<>();
		if (!cache.containsKey(name)) {
			List<X> tmp = mapper.apply((A) RAgent.this);
			cache.put(name, tmp);
		}
		return (List<X>) cache.get(name);
	}
	
	protected void clearCache() {
		if (this.cache == null) this.cache = new ConcurrentHashMap<>();
		this.cache.clear();
	}
	
	// @Override
	@SuppressWarnings("unchecked")
	public <X> List<X> getNamedObservation(Enum<?> name, Class<X> type) {
		RAgentObserver<A,?> obs = this.observers.get(name.name());
		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
		return (List<X>) obs.getObservation();
	}
	
	@SuppressWarnings("unchecked")
	public <X> Optional<X> getLastNamedObservation(Enum<?> name, Class<X> type) {
		RAgentObserver<A,?> obs = this.observers.get(name.name());
		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
		return (Optional<X>) obs.getLastObservation();
	}
	
	@SuppressWarnings("unchecked")
	public <X> List<List<? extends X>> getNamedListObservation(Enum<?> name, Class<X> subtype) {
		RAgentObserver<A,?> obs = this.observers.get(name.name());
		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
		if (!obs.getObservationType().equals(subtype)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+subtype.getName()+" requested; "+obs.getObservationType()+" found]");
		if (!(obs instanceof RObserver.OfLists)) {
			throw new RuntimeException("Observer is not a list observer: "+name+" ["+subtype.getName()+"]");
		}
		return ((RObserver.OfLists<A,X>) obs).getObservationList();
	}
	
	/** 
	 * use in a flat map to get just specific sub-types of agent.
	 * @param <X> the class you want to select
	 * @param agentType the agent type you want to select
	 * @return a stream of size one of agents of this type.
	 */
	@SuppressWarnings("unchecked")
	public <X extends A> Optional<X> subtype(Class<X> agentType) {
		if (agentType.isAssignableFrom(this.getClass())) return Optional.of((X) this);
		return Optional.empty();
	}
	
	/**
	 * Just implement a return this; for the implementation, to make sure the 
	 * type is correct.
	 * @return
	 */
	public abstract A self();
	
	/**
	 * Simplify access to simulation time
	 * @return the number of steps.
	 */
	public Long getSimTime() {
		return this.getSimulation().getSchedule().getSteps();
	}

	
	/**
	 * This gets called on every simulation step;
	 * Tasks here are 
	 * 1) update the status JavaBean so that
	 * it is reflects changes in the simulation, including changes in the
	 * simulation parameters. This may be useful to call from initialiseStatus()
	 * as likely uses the same logic..
	 * 2) important to decide here whether this agent is still active in the 
	 * simulation.
	 */
	public abstract void updateStatus();
	
	/**
	 * adjust any behaviour changes as result of simulation and agent status
	 * this gets called immediately after a status update as part of each 
	 * simulation step so cannot assume other agents will have been updated.
	 * named observers will represent the state from previous steps which can 
	 * inform this. 
	 */
	
	public abstract void changeBehaviour();

	public Reference weakReference() {
		return new Reference(this.id);
	};
	
	public class Reference implements Serializable {
		
		public Reference(int id) {
			this.id = id;
			
		}
		
		int id;
		
		public A resolve() {
			return simulation.getAgentById(id);
		}
	}

	public void copyStatus() {
		this.oldStatus = SerializationUtils.clone(status);
	}
	
	
}
