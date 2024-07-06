package io.github.ai4ci;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

public class RObservatory<
		S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>
	> implements RSteppable<S> {

	S simulation;
	List<RObserver<?,?>> observers = new ArrayList<>();
	List<RObserver<?,?>> namedObservers = new ArrayList<>();
	
	public RObservatory(S simulation) {
		this.simulation = simulation;
	}
	
	public void initialiseScheduler() {
		simulation.getSchedule().addAfter(this);
	}
	
	public S getSimulation() {
		return simulation;
	}
	
	public int getPriority() {return 10000;}
	
	@Override
	public void doStep(S simulation) {
		observers.forEach(
			o2 -> o2.update()
		);
	}
	
	/**
	 * Maybe less useful if the observers are anonymous classes.
	 * @param <O>
	 * @param <X>
	 * @param observerType
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <O extends RObserver<S,X>,X> Stream<X> observationsByObserver(Class<O> observerType) {
		return Stream.concat(
				observers.stream(),
				namedObservers.stream()
			)
			.filter(o -> observerType.isAssignableFrom(o.getClass()))
			.map(o -> (O) o)
			.flatMap(o -> o.getObservation().stream());
	}
		
	@SuppressWarnings("unchecked")
	public <X> Stream<X> observationsByNameAndType(String name, Class<X> type) {
		return Stream.concat(
				observers.stream(),
				namedObservers.stream()
			)
			.filter(o -> o.getObservationType().equals(type) && o.getName().equals(name))
			.map(o -> (RObserver<S,X>) o)
			.flatMap(o -> o.getObservation().stream());
	}
	
	@SuppressWarnings("unchecked")
	public <X> Stream<X> observationsByType(Class<X> type) {
		return Stream.concat(
				observers.stream(),
				namedObservers.stream()
			)
			.filter(o -> o.getObservationType().equals(type))
			.map(o -> (RObserver<S,X>) o)
			.flatMap(o -> o.getObservation().stream());
	}
	
	public void observeSimulation(RSimulationObserver<S,?> observer) {
		RSimulationObserver<S,?> o = // RSimulationBuilder.kryo.copy(observer); 
				(RSimulationObserver<S, ?>) SerializationUtils.clone(observer);
		o.setSubject(simulation);
		this.observers.add(o);
	}
	
	public <A2 extends RAgent<A2,?,?,?>> void observeAgent(A2 agent, RAgentObserver<A2,?> observer) {
		observer.setSubject(agent);
		this.observers.add(observer);
	}
	
	@SuppressWarnings("unchecked")
	public <A2 extends RAgent<A2,?,?,?>> void observeAgents(RAgentObserver<?,?> observer) {
		simulation
			.streamAgents()
			.filter(a -> a.getClass().equals(observer.getAgentType()))
			.forEach(a -> {
				RAgentObserver<A2,?> observer2 = (RAgentObserver<A2,?>)
						// RSimulationBuilder.kryo.copy(observer);
						SerializationUtils.clone(observer);
				observer2.setSubject((A2) a);
				this.observers.add(observer2);
			});
	}
	
	public boolean remainsActive() {
		return !this.getSimulation().isComplete();
	}

	@Override
	public boolean remainsActive(S simulation) {
		return !simulation.isComplete();
	}
	
}
