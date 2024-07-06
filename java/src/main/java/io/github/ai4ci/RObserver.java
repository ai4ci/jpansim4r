package io.github.ai4ci;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * A non-typical observer pattern. These observers are actually updated by the
 * simulation schedule, as the last operation in a simulation execution. They
 * are either registered by the Simulation or Agent (Observable) that is being 
 * observed and can be queried by the Observable itself. 
 * 
 * This is used to generate history-aware behaviour in the Agents. These 
 * observers are registered during simulation build time.  
 * 
 * Alternatively the observer is registered by an Observatory. The observatory 
 * manages the updating of the observers . 
 *  
 * @param <O>
 * @param <X>
 */
public interface RObserver<O extends RObservable, X> extends Serializable {

	public O getSubject();
	public String getName();
	public void update(O subject);
	public List<X> getObservation();
	public default Optional<X> getLastObservation() {return getObservation().stream().findFirst();};
	public Class<X> getObservationType();
	
	public default void update() {
		update(getSubject());
	}
	
	public static <A extends RAgent<A,?,?,?>,X> RAgentObserver<A,X> 
		agentHistory(Class<A> agentType, Enum<?> name, Class<X> type, RAgentObserver.Mapper<A,X> mapper, Integer maxSize) {
			return new RAgentObserver.History<A, X>(agentType, name, type, mapper, maxSize);
	}
	
	public static <A extends RAgent<A,?,?,?>,X> RAgentObserver<A,X> 
	agentLastValue(Class<A> agentType, Enum<?> name, Class<X> type, RAgentObserver.Mapper<A,X> mapper) {
		return new RAgentObserver.Last<A, X>(agentType, name, type, mapper);
	}

	public static <S extends RSimulation<S,?,?,?>,X> RSimulationObserver<S,X> 
		simulationLastValue(Enum<?> name, Class<X> type, RSimulationObserver.Mapper<S,X> mapper) {
	return new RSimulationObserver.Last<S, X>(name, type, mapper);
	}
	
	public static <S extends RSimulation<S,?,?,?>,X> RSimulationObserver<S,X> 
	simulationHistory(Enum<?> name, Class<X> type, RSimulationObserver.Mapper<S,X> mapper, Integer maxSize) {
		return new RSimulationObserver.History<S, X>(name, type, mapper, maxSize);
	}
	
	public static interface OfLists<O extends RObservable, X> extends RObserver<O,X> {
		public List<List<? extends X>> getObservationList();
	}
}
