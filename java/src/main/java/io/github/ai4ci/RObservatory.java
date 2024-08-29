package io.github.ai4ci;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.SerializationUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RObservatory<
		S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>
	> implements RSteppable<S> {

	S simulation;
	Map<String,List<RObserver<?,?>>> observers = new HashMap<>();
	Map<String,List<RObserver<?,?>>> namedObservers = new HashMap<>();
	
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
			(u,o2) -> o2.forEach(o3 -> o3.update())
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
				observers.values().stream().flatMap(o -> o.stream()),
				namedObservers.values().stream().flatMap(o -> o.stream())
			)
			.filter(o -> observerType.isAssignableFrom(o.getClass()))
			.map(o -> (O) o)
			.flatMap(o -> o.getObservation().stream());
	}
	
	public <X> Stream<X> observationsByNameAndType(Enum<?> name, Class<X> type) {
		return observationsByNameAndType(name.name(), type);
	}
	
	@SuppressWarnings("unchecked")
	public <X> Stream<X> observationsByNameAndType(String name, Class<X> type) {
		return Stream.concat(
				observers.values().stream().flatMap(o -> o.stream()),
				namedObservers.values().stream().flatMap(o -> o.stream())
			)
			.filter(o -> o.getObservationType().equals(type) && o.getName().equals(name))
			.map(o -> (RObserver<S,X>) o)
			.flatMap(o -> o.getObservation().stream());
	}
	
	@SuppressWarnings("unchecked")
	public Map<String,List<?>> observationsByName(String name) {
		Map<String,List<?>> observationsById = new HashMap<>();
		Stream.concat(
				observers.values().stream().flatMap(o -> o.stream()),
				namedObservers.values().stream().flatMap(o -> o.stream())
			)
			.filter(o -> o.getName().equals(name))
			.map(o -> (RObserver<S,?>) o)
			.forEach(o -> observationsById.put(
					o.getSubject().getUrn(),
					o.getObservation()));
		return observationsById;
	}
	
	@SuppressWarnings("unchecked")
	public <X> Stream<X> observationsByType(Class<X> type) {
		return Stream.concat(
				observers.values().stream().flatMap(o -> o.stream()),
				namedObservers.values().stream().flatMap(o -> o.stream())
			)
			.filter(o -> o.getObservationType().equals(type))
			.map(o -> (RObserver<S,X>) o)
			.flatMap(o -> o.getObservation().stream());
	}
	
	public Map<Integer,Map<String,Map<String,List<?>>>> observations(List<String> columns) {
		// map is size, observation name, observed urn, and observations (maybe timeseries); 
		// size is total number of observations for a name (including all agents).
		Map<Integer,Map<String,Map<String,List<?>>>> out = new HashMap<>();
		for (String en: columns) {
			Map<String,List<?>> tmp = observationsByName(en);
			int size = tmp.values().stream().mapToInt(l -> l.size()).sum();
			if (size>0) {
				if (!out.containsKey(size)) out.put(size, new HashMap<>());
				if (!out.get(size).containsKey(en)) out.get(size).put(en, new HashMap<>());
				out.get(size).get(en).putAll(tmp);
			}
		}
		return out;
	}
	
	public void registerNamedObserver(RObserver<?,?> observer) {
		if (!this.namedObservers.containsKey(observer.getSubject().getUrn())) 
				this.namedObservers.put(observer.getSubject().getUrn(), new ArrayList<>());
		this.namedObservers.get(observer.getSubject().getUrn()).add(observer);
	}
	
	public void observeSimulation(RSimulationObserver<S,?> observer) {
		RSimulationObserver<S,?> o = // RSimulationBuilder.kryo.copy(observer); 
				(RSimulationObserver<S, ?>) SerializationUtils.clone(observer);
		o.setSubject(simulation);
		if (!this.observers.containsKey(observer.getSubject().getUrn())) 
				this.observers.put(observer.getSubject().getUrn(), new ArrayList<>());
		this.observers.get(observer.getSubject().getUrn()).add(o);
	}
	
	public <A2 extends RAgent<A2,?,?,?>> void observeAgent(A2 agent, RAgentObserver<A2,?> observer) {
		observer.setSubject(agent);
		if (!this.observers.containsKey(agent.getUrn())) 
			this.observers.put(agent.getUrn(), new ArrayList<>());
		this.observers.get(agent.getUrn()).add(observer);
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
				if (!this.observers.containsKey(a.getUrn())) 
					this.observers.put(a.getUrn(), new ArrayList<>());
				this.observers.get(a.getUrn()).add(observer);
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
