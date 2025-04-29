package io.github.ai4ci;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Pair;



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
public interface RObserver<O extends RObservable<O>, X> extends Serializable {

	
	
	public Class<O> getSubjectType();
	public String getName();
	public Class<X> getObservationType();
	public List<Long> getObservationTimes();
	
	public O getSubject();
	public void setSubject(O subject);
	public void update(O subject);
	public List<X> getObservation();
	public default Optional<X> getLastObservation() {return getObservation().stream().findFirst();};
	public default void update() {
		update(getSubject());
	}
	
	
	public default boolean isCompatible(RObserver<?,?> filter) {
		return (
			filter.getObservationType().equals(this.getObservationType()) &&
			filter.getName().equals(this.getName()) &&
			filter.getSubjectType().equals(this.getSubjectType())
		);
	}
	
	public interface Tester<S extends RObservable<S>> extends Predicate<S>,Serializable {}
	public static interface Mapper<A extends RObservable<A>,X> extends Function<A,Optional<X>>,Serializable {}
	public static interface ListMapper<A extends RObservable<A>,X> extends Function<A,List<X>>,Serializable {}
	
	/**
	 * Records up to maxSize last values where the mapper return a non
	 * empty value.
	 * @param <A>
	 * @param <X>
	 * @param agentType
	 * @param name
	 * @param type
	 * @param mapper
	 * @param maxSize
	 * @return
	 */
	public static <A extends RObservable<A>,X> RObserver.History<A,X> 
		history(Class<A> agentType, String name, Class<X> type, RObserver.Mapper<A,X> mapper, Integer maxSize) {
			return new RObserver.History<A, X>(agentType, name, type, mapper, maxSize);
	}
	
	public static <A extends RObservable<A>,X> RObserver.History<A,X> 
		history(Class<A> agentType, String name, Class<X> type, RObserver.Mapper<A,X> mapper) {
		return new RObserver.History<A, X>(agentType, name, type, mapper, null);
}
	
	public static <A extends RObservable<A>,X> RObserver.OfLists<A,X> 
		listHistory(Class<A> agentType, String name, Class<X> type, RObserver.ListMapper<A,X> mapper, Integer maxSize) {
			return new RObserver.ListHistory<A, X>(agentType, name, type, mapper, maxSize);
}
	
	public static <A extends RObservable<A>,X> RObserver.Last<A,X> 
		lastValue(Class<A> agentType, String name, Class<X> type, RObserver.Mapper<A,X> mapper) {
			return new RObserver.Last<A, X>(agentType, name, type, mapper);
	}
	
	/**
	 * History counters will always return a value
	 * @param <S>
	 * @param <A>
	 * @param simType
	 * @param name
	 * @param tester
	 * @return
	 */
	public static <S extends RSimulation<S,?,?,A>, A extends RAgent<A,S,?,?>> RObserver.History<S, Long> 
		counter(Class<S> simType, String name, RObserver.Tester<A> tester) {
			return new RObserver.History<S,Long>(simType, name, Long.class,
				s -> Optional.of(
						s.streamAgents()
						.filter(a -> tester.test(a))
						.count()),
				null
			);
}

	public static interface OfLists<O extends RObservable<O>, X> extends RObserver<O,X> {
		public List<List<? extends X>> getObservationList();
	}
	
	public static abstract class Base<A extends RObservable<A>,X> implements RObserver<A,X> {
		
		Base(Class<A> agentType, String name) {
			this.agentType = agentType;
			this.name = name;
		}

		Class<A> agentType;
		String name;
		A subject;
		
		public String getName() {
			return this.name;
		}
		
		public void setSubject(A subject) {
			this.subject = subject;
		}

		public A getSubject() {
			return subject;
		}
		
		public Class<A> getSubjectType() {
			return agentType;
		}
		
		@Override
		public abstract void update(A subject);
	}
	
	/**
	 * These keep values that the mapper returns non empty optionals for
	 * I.e. it will not always be a complete history.
	 * @param <A>
	 * @param <X>
	 */
	public static class History<A extends RObservable<A>,X> extends Base<A,X> {

		List<Pair<Long,X>> x = new ArrayList<>();
		Mapper<A,X> mapper;
		Class<X> type;
		Integer maxElements;
		
		@Override
		public void update(A subject) {
			Optional<X> tmp = mapper.apply(subject);
			if (tmp.isPresent()) x.add(0, Pair.create(subject.getSimTime(), tmp.get()));
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public List<X> getObservation() {
			return x.stream().map(t -> t.getSecond()).collect(Collectors.toList());
		}

		public List<Long> getObservationTimes() {
			return x.stream().map(t -> t.getFirst()).collect(Collectors.toList());
		}
		
		@Override
		public Class<X> getObservationType() {
			return type;
		}
		
		History(Class<A> agentType, String name, Class<X> type, Mapper<A,X> mapper, Integer maxElements) {
			super(agentType, name);
			this.type = type;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}

	
	
	public static class Last<A extends RObservable<A>,X> extends Base<A,X> {

		X x = null;
		Long time;
		Mapper<A,X> mapper;
		Class<X> type;
		
		@Override
		public void update(A subject) {
			mapper.apply(subject).ifPresent(value -> x = value);
			time = subject.getSimTime();
		}

		public List<Long> getObservationTimes() {
			return Collections.singletonList(time);
		}
		
		@Override
		public List<X> getObservation() {
			return getLastObservation().map(e -> Collections.singletonList(e)).orElse(Collections.emptyList());
		}
		
		@Override 
		public Optional<X> getLastObservation() {
			return Optional.ofNullable(x);
		}

		@Override
		public Class<X> getObservationType() {
			return type;
		}
		
		Last(Class<A> agentType, String name, Class<X> type, Mapper<A,X> mapper) {
			super(agentType, name);
			this.type = type;
			this.mapper = mapper;
		}

		
		
	}

	public static class ListHistory<A extends RObservable<A>,X> extends Base<A,X> implements RObserver.OfLists<A, X> {

		List<Pair<Long,List<? extends X>>> x = new ArrayList<>();
		ListMapper<A,X> mapper;
		Class<X> subtype;
		Integer maxElements;
		
		@Override
		public void update(A subject) {
			List<? extends X> tmp = mapper.apply(subject);
			Long time = subject.getSimTime();
			x.add(0, Pair.create(time, tmp));
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public List<X> getObservation() {
			return x.stream().map(p -> p.getSecond()).flatMap(y -> y.stream()).collect(Collectors.toList());
		}

		public List<Long> getObservationTimes() {
			return x.stream().map(t -> t.getFirst()).collect(Collectors.toList());
		}
		
		public List<List<? extends X>> getObservationList() {
			return x.stream().map(p -> p.getSecond()).collect(Collectors.toList());
		}
		
		
		
		@Override
		public Class<X> getObservationType() {
			return subtype;
		}
		
		ListHistory(Class<A> agentType, String name, Class<X> subtype, ListMapper<A,X> mapper, Integer maxElements) {
			super(agentType, name);
			this.subtype = subtype;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}
}
