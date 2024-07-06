package io.github.ai4ci;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class RSimulationObserver<S extends RSimulation<S,?,?,?>,
		X> implements RObserver<S,X>, Serializable {

//	public RSimulationObserver() {
//		this.name = this.getClass().getSimpleName();
//	}
	
	public RSimulationObserver(Enum<?> name) {
		this.name = name.name();
	}

	private String name;
	S simulation;
	
	public String getName() {
		return name;
	}
	
	public S getSubject() {
		return simulation;
	}

	public void setSubject(S simulation2) {
		simulation = simulation2;
	}

	public static interface Mapper<S extends RSimulation<S,?,?,?>,X> extends Function<S,Optional<X>>,Serializable {}
	public static interface ListMapper<S extends RSimulation<S,?,?,?>,X> extends Function<S,List<X>>,Serializable {}
	
	public static class History<S extends RSimulation<S,?,?,?>,X>  extends RSimulationObserver<S,X> {

		List<X> x = new ArrayList<>();
		Mapper<S,X> mapper;
		Class<X> type;
		Integer maxElements;
		
		@Override
		public synchronized void update(S subject) {
			Optional<X> tmp = mapper.apply(subject);
			if (tmp.isPresent()) x.add(0, tmp.get());
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public synchronized List<X> getObservation() {
			return x;
		}

		@Override
		public Class<X> getObservationType() {
			return type;
		}
		
		History(Enum<?> name, Class<X> type, Mapper<S,X> mapper, Integer maxElements) {
			super(name);
			this.type = type;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}

	public static class Last<S extends RSimulation<S,?,?,?>,X>  extends RSimulationObserver<S,X> {

		X x = null;
		Mapper<S,X> mapper;
		Class<X> type;
		
		@Override
		public synchronized void update(S subject) {
			mapper.apply(subject).ifPresent(value -> x = value);
		}

		@Override
		public synchronized List<X> getObservation() {
			return Optional.ofNullable(x).map(i -> Collections.singletonList(i)).orElse(Collections.emptyList());
		}
		
		@Override 
		public synchronized Optional<X> getLastObservation() {
			return Optional.ofNullable(x);
		}

		@Override
		public Class<X> getObservationType() {
			return type;
		}
		
		Last(Enum<?> name, Class<X> type, Mapper<S,X> mapper) {
			super(name);
			this.type = type;
			this.mapper = mapper;
		}
		
	}

	public static class ListHistory<S extends RSimulation<S,?,?,?>,X>  extends RSimulationObserver<S,X> implements RObserver.OfLists<S, X> {

		List<List<? extends X>> x = new ArrayList<>();
		ListMapper<S,X> mapper;
		Class<X> subtype;
		Integer maxElements;
		
		@Override
		public synchronized void update(S subject) {
			List<? extends X> tmp = mapper.apply(subject);
			x.add(0, tmp);
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public synchronized List<X> getObservation() {
			return x.stream().flatMap(y -> y.stream()).collect(Collectors.toList());
		}

		public synchronized List<List<? extends X>> getObservationList() {
			return x;
		}
		
		@Override
		public Class<X> getObservationType() {
			return subtype;
		}
		
		ListHistory(Enum<?> name, Class<X> subtype, ListMapper<S,X> mapper, Integer maxElements) {
			super(name);
			this.subtype = subtype;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}
}
