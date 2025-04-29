package io.github.ai4ci;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.ai4ci.data.Dataframe.MapRule;

public interface RObserved {

	public <X extends RObserver<?, ?>> Stream<X> getObservers();
	
	@SuppressWarnings("unchecked")
	public default <X> List<X> observations(RObserver.History<?, X> observer) {
		return getObservers()
				.filter(o -> o.isCompatible(observer))
				.map(o -> (RObserver.History<?,X>) o)
				.flatMap(o -> o.getObservation().stream())
				.collect(Collectors.toList());
	}
	
	@SuppressWarnings("unchecked")
	public default <X> List<Long> observationTimes(RObserver<?, X> observer) {
		return getObservers()
				.filter(o -> o.isCompatible(observer))
				.map(o -> (RObserver<?,X>) o)
				.flatMap(o -> o.getObservationTimes().stream())
				.collect(Collectors.toList());
	}
	
	@SuppressWarnings("unchecked")
	public default <X> Optional<X> lastObservation(RObserver<?, X> observer) {
		return getObservers()
				.filter(o -> o.isCompatible(observer))
				.map(o -> (RObserver<?,X>) o)
				.flatMap(o -> o.getLastObservation().stream())
				.findFirst();
	}
	
	@SuppressWarnings("unchecked")
	public default <X> Optional<Long> lastObservationTime(RObserver<?, X> observer) {
		return getObservers()
				.filter(o -> o.isCompatible(observer))
				.map(o -> (RObserver.History<?,X>) o)
				.flatMap(o -> o.getObservationTimes().stream())
				.findFirst();
	}
	
	
	@SuppressWarnings("unchecked")
	public default <X> List<List<X>> observations(RObserver.OfLists<?, X> observer) {
		return getObservers()
				.filter(o -> o.isCompatible(observer))
				.map(o -> (RObserver.OfLists<?,X>) o)
				.flatMap(o -> o.getObservationList().stream().map(l -> (List<X>) l))
				.collect(Collectors.toList());
	}
	
	
	/**
	 * Add a mapping to extract a column from a stream of 
	 * @param <X1> the input type
	 * @param <Y1> the output / column type
	 * @param name the name of the column
	 * @param fn a mapping lambda
	 * @return a mapping rule
	 */
	public static <X1 extends RObserved,Y1> MapRule<X1,List<Y1>> data(RObserver.History<?,Y1> observer) {
		String name = observer.getName();
		Function<X1,List<Y1>> fn = o -> o.observations(observer);
		return new MapRule<X1,List<Y1>>(name,fn);
	}
	
	/**
	 * Add a mapping to extract a column from a stream of 
	 * @param <X1> the input type
	 * @param <Y1> the output / column type
	 * @param name the name of the column
	 * @param fn a mapping lambda
	 * @return a mapping rule
	 */
	public static <X1 extends RObserved,Y1,Z1> MapRule<X1,List<Z1>> data(RObserver.History<?,Y1> observer, String name, Function<Y1,Z1> mapper) {
		String rename = observer.getName()+"."+name;
		Function<X1,List<Z1>> fn = o -> o.observations(observer).stream().map(mapper).collect(Collectors.toList());
		return new MapRule<X1,List<Z1>>(rename,fn);
	}
	
	/**
	 * Add a mapping to extract a column from a stream of 
	 * @param <X1> the input type
	 * @param <Y1> the output / column type
	 * @param name the name of the column
	 * @param fn a mapping lambda
	 * @return a mapping rule
	 */
	public static <X1 extends RObserved> MapRule<X1,List<Long>> time(RObserver.History<?,?> observer) {
		String name = observer.getName()+".time";
		Function<X1,List<Long>> fn = o -> o.observationTimes(observer);
		return new MapRule<X1,List<Long>>(name,fn);
	}
	
//	public default <X> List<X> getNamedObservation(RObserver<?,X> obs) {
//		return this.getObservers().filter(o -> o.isCompatible(obs))
//			.map(o -> (RObserver<?,?>) o)
//			.findFirst()
//			.map(o -> obs.getObservation())
//			.orElseThrow(() -> new RuntimeException("Observer not found: "+obs.toString()));
//	}
//	
//	public default <X> Optional<X> getLastNamedObservation(RObserver<?,X> obs) {
//		return this.getObservers().filter(o -> o.isCompatible(obs))
//			.map(o -> (RObserver<?,?>) o)
//			.findFirst()
//			.map(o -> obs.getObservation().stream().findFirst())
//			.orElseThrow(() -> new RuntimeException("Observer not found: "+obs.toString()));
//	}
}