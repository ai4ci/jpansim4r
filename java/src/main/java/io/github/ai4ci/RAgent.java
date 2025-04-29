package io.github.ai4ci;

import java.io.Serializable;
import java.util.List;
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
 * @param <A> Agent class.
 * @param <S> Parent simulation class 
 * @param <B> A baseline object. Construted once immutable
 * @param <T> A status object. Mutable.
 */
public abstract class RAgent<
		A extends RAgent<A,S,B,T>, 
		S extends RSimulation<S,?,?,A>,
		B extends RAgentBaseline,
		T extends RAgentStatus
	> implements RSteppable<S>, Serializable, RObservable<A> {

	int id;
	S simulation;
	T status;
	T oldStatus;
	B baseline;
	ConcurrentMap<String, RObserver<A,?>> observers = new ConcurrentHashMap<>();
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
	
	public String getUrn() {
		return this.getSimulation().getUrn()+":agent:"+getId();
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
	
	public void setStatus(T status) {
		this.status = status;
	};

	public B getBaseline() {
		return baseline;
	}

	public void setBaseline(B baseline) {
		this.baseline = baseline;
	}


	
	public S getSimulation() {return simulation;}
	
	public Sampler sampler() {return getSimulation().sampler();}
	
	@Override
	@SuppressWarnings("unchecked")
	public Stream<RObserver<A,?>> getObservers() {
		return observers.values().stream();
	}
	
	@Override
	public final void registerNamedObserver(RObserver<A,?> observer) {
		RObserver<A,?> tmp = SerializationUtils.clone(observer);
		this.observers.put(tmp.getName(), tmp);
		tmp.setSubject(self());
	}
	


	@SuppressWarnings("unchecked")
	/**
	 * Cache 
	 * @param <X>
	 * @param name
	 * @param type
	 * @param mapper
	 * @return an optional. It is empty if and only if the name is not found in the cache.
	 */
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
	
//	// @Override
//	@SuppressWarnings("unchecked")
//	public <X> List<X> getNamedObservation(String name, Class<X> type) {
//		RObserver<A,?> obs = this.observers.get(name);
//		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
//		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
//		return (List<X>) obs.getObservation();
//	}
//	
//	@SuppressWarnings("unchecked")
//	public <X> Optional<X> getLastNamedObservation(String name, Class<X> type) {
//		RObserver<A,?> obs = this.observers.get(name);
//		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
//		if (!obs.getObservationType().equals(type)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+type.getName()+" requested; "+obs.getObservationType()+" found]");
//		return (Optional<X>) obs.getLastObservation();
//	}
//	
//	@SuppressWarnings("unchecked")
//	public <X> List<List<? extends X>> getNamedListObservation(String name, Class<X> subtype) {
//		RObserver<A,?> obs = this.observers.get(name);
//		if (obs == null) throw new RuntimeException("Observer name not defined in simulation: "+name);
//		if (!obs.getObservationType().equals(subtype)) throw new RuntimeException("Incorrect type specified for observer of name: "+name+" ["+subtype.getName()+" requested; "+obs.getObservationType()+" found]");
//		if (!(obs instanceof RObserver.OfLists)) {
//			throw new RuntimeException("Observer is not a list observer: "+name+" ["+subtype.getName()+"]");
//		}
//		return ((RObserver.OfLists<A,X>) obs).getObservationList();
//	}
	
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
	 * 
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
		return new Reference(this.self());
	};
	
	public static class Reference {
		
		public Reference(RAgent<?,?,?,?> agent) {
			this.id = agent.id;
			this.simulation = agent.simulation;
		}
		
		int id;
		RSimulation<?,?,?,?> simulation;
		
		@SuppressWarnings("unchecked")
		public <A1 extends RAgent<A1,?,?,?>> A1 resolve(Class<A1> type) {
			return (A1) simulation.getAgentById(id);
		}
		
	}

	public void copyStatus() {
		this.oldStatus = SerializationUtils.clone(status);
	}
	
}
