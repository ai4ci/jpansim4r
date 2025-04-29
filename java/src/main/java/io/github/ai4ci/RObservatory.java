package io.github.ai4ci;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.SerializationUtils;

public abstract class RObservatory implements RObserved {


	Map<String,List<RObserver<?,?>>> observers = new HashMap<>();
	//Map<String,List<RObserver<?,?>>> namedObservers = new HashMap<>();

	@SuppressWarnings("unchecked")
	public Stream<RObserver<?,?>> getObservers() {
		return Stream.concat(
				observers.values().stream().flatMap(o -> o.stream()),
				this.getSimulation().getAllObservers()
		);
	}
	
//	/**
//	 * Maybe less useful if the observers are anonymous classes.
//	 * @param <O>
//	 * @param <X>
//	 * @param observerType
//	 * @return
//	 */
//	@SuppressWarnings("unchecked")
//	public <O extends RObserver<?,X>,X> List<X> observationsByObserver(Class<O> observerType) {
//		return getObservers()
//				.filter(o -> observerType.isAssignableFrom(o.getClass()))
//				.map(o -> (O) o)
//				.flatMap(o -> o.getObservation().stream())
//				.collect(Collectors.toList());
//	}
//
//	
//	
//	@SuppressWarnings("unchecked")
//	public <X> List<X> observationsByType(Class<X> type) {
//		return getObservers()
//				.filter(o -> o.getObservationType().equals(type))
//				.map(o -> (RObserver<?,X>) o)
//				.flatMap(o -> o.getObservation().stream())
//				.collect(Collectors.toList());
//	}
//
//	public <X> List<X> observationsByNameAndType(Enum<?> name, Class<X> type) {
//		return observationsByNameAndType(name.name(), type);
//	}
//
//	@SuppressWarnings("unchecked")
//	public <X> List<X> observationsByNameAndType(String name, Class<X> type) {
//		return getObservers()
//				.filter(o -> o.getObservationType().equals(type) && o.getName().equals(name))
//				.map(o -> (RObserver<?,X>) o)
//				.flatMap(o -> o.getObservation().stream())
//				.collect(Collectors.toList());
//	}
//
//	//@SuppressWarnings("unchecked")
//	public Map<String,List<?>> observationsByName(String name) {
//		Map<String,List<?>> observationsById = new HashMap<>();
//		getObservers()
//			.filter(o -> o.getName().equals(name))
//			.map(o -> (RObserver<?,?>) o)
//			.forEach(o -> observationsById.put(
//					o.getSubject().getUrn(),
//					o.getObservation()));
//		return observationsById;
//	}
//
//	public Map<Integer,Map<String,Map<String,List<?>>>> observations(List<String> columns) {
//		// map is size, observation name, observed urn, and observations (maybe timeseries); 
//		// size is total number of observations for a name (including all agents).
//		Map<Integer,Map<String,Map<String,List<?>>>> out = new HashMap<>();
//		for (String en: columns) {
//			Map<String,List<?>> tmp = observationsByName(en);
//			int size = tmp.values().stream().mapToInt(l -> l.size()).sum();
//			if (size>0) {
//				if (!out.containsKey(size)) out.put(size, new HashMap<>());
//				if (!out.get(size).containsKey(en)) out.get(size).put(en, new HashMap<>());
//				out.get(size).get(en).putAll(tmp);
//			}
//		}
//		return out;
//	}

	public abstract <S extends RSimulation<?,?,?,?>> S getSimulation();

	public Long getObservationTime() {
		return this.getSimulation().getSimTime();
	}

	public String getUrn() {
		return this.getSimulation().getUrn();
	}

	public List<Long> getHistoricalSteps() {
		return this.getSimulation().getHistoricalSteps();
	}

	public static class Typed<
	S extends RSimulation<S,?,?,A>,
	A extends RAgent<A,S,?,?>
	> extends RObservatory implements RSteppable<S> {

		S simulation;

		public Typed(S simulation) {
			this.simulation = simulation;
		}

		public void initialiseScheduler() {
			simulation.getSchedule().addAfter(this);
		}

		public RObservatory untyped() {
			return this;
		}
		
		@SuppressWarnings("unchecked")
		public S getSimulation() {
			return simulation;
		}

		public int getPriority() {return 10000;}

		@Override
		public void doStep(S simulation) {
			observers.forEach(
					(u,o2) -> o2.forEach(o3 -> o3.update())
					);
		}





		public void observeSimulation(RObserver<S,?> observer) {
			RObserver<S,?> o = 
					(RObserver<S, ?>) SerializationUtils.clone(observer);
			o.setSubject(simulation);
			if (!this.observers.containsKey(observer.getSubject().getUrn())) 
				this.observers.put(observer.getSubject().getUrn(), new ArrayList<>());
			this.observers.get(observer.getSubject().getUrn()).add(o);
		}

		public <A2 extends RAgent<A2,?,?,?>> void observeAgent(A2 agent, RObserver<A2,?> observer) {
			observer.setSubject(agent);
			if (!this.observers.containsKey(agent.getUrn())) 
				this.observers.put(agent.getUrn(), new ArrayList<>());
			this.observers.get(agent.getUrn()).add(observer);
		}

		@SuppressWarnings("unchecked")
		public <A2 extends RAgent<A2,?,?,?>> void observeAgents(RObserver<?,?> observer) {
			simulation
			.streamAgents()
			.filter(a -> a.getClass().equals(observer.getSubjectType()))
			.forEach(a -> {
				RObserver<A2,?> observer2 = (RObserver<A2,?>)
						SerializationUtils.clone(observer);
				observer2.setSubject((A2) a);
				if (!this.observers.containsKey(a.getUrn())) 
					this.observers.put(a.getUrn(), new ArrayList<>());
				this.observers.get(a.getUrn()).add(observer2);
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
}
