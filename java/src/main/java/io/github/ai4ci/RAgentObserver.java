package io.github.ai4ci;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class RAgentObserver<
	A extends RAgent<A,?,?,?>, 
	X> implements RObserver<A,X>,Serializable {

//	public RAgentObserver(Class<A> agentType) {
//		this(agentType, agentType.getSimpleName()+" observer");
//	}
	
	public RAgentObserver(Class<A> agentType, Enum<?> name) {
		this.agentType = agentType;
		this.name = name.name();
	}

	Class<A> agentType;
	String name;
	A agent;
	
	public String getName() {
		return this.name;
	}
	
	public void setSubject(A agent2) {
		agent = agent2;
	}

	public A getSubject() {
		return agent;
	}
	
	public Class<A> getAgentType() {
		return agentType;
	}
	
	@Override
	public abstract void update(A subject);
	
	public static interface Mapper<A extends RAgent<A,?,?,?>,X> extends Function<A,Optional<X>>,Serializable {}
	public static interface ListMapper<A extends RAgent<A,?,?,?>,X> extends Function<A,List<X>>,Serializable {}
	
	public static class History<A extends RAgent<A,?,?,?>,X>  extends RAgentObserver<A,X> {

		List<X> x = new ArrayList<>();
		Mapper<A,X> mapper;
		Class<X> type;
		Integer maxElements;
		
		@Override
		public void update(A subject) {
			Optional<X> tmp = mapper.apply(subject);
			if (tmp.isPresent()) x.add(0, tmp.get());
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public List<X> getObservation() {
			return x;
		}

		@Override
		public Class<X> getObservationType() {
			return type;
		}
		
		History(Class<A> agentType, Enum<?> name, Class<X> type, Mapper<A,X> mapper, Integer maxElements) {
			super(agentType, name);
			this.type = type;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}

	public static class Last<A extends RAgent<A,?,?,?>,X>  extends RAgentObserver<A,X> {

		X x = null;
		Mapper<A,X> mapper;
		Class<X> type;
		
		@Override
		public void update(A subject) {
			mapper.apply(subject).ifPresent(value -> x = value);
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
		
		Last(Class<A> agentType, Enum<?> name, Class<X> type, Mapper<A,X> mapper) {
			super(agentType, name);
			this.type = type;
			this.mapper = mapper;
		}
		
	}

	public static class ListHistory<A extends RAgent<A,?,?,?>,X>  extends RAgentObserver<A,X> implements RObserver.OfLists<A, X> {

		List<List<? extends X>> x = new ArrayList<>();
		ListMapper<A,X> mapper;
		Class<X> subtype;
		Integer maxElements;
		
		@Override
		public void update(A subject) {
			List<? extends X> tmp = mapper.apply(subject);
			x.add(0, tmp);
			while (maxElements != null && x.size() > maxElements) {
				x.remove((int) maxElements);
			}
		}

		@Override
		public List<X> getObservation() {
			return x.stream().flatMap(y -> y.stream()).collect(Collectors.toList());
		}

		public List<List<? extends X>> getObservationList() {
			return x;
		}
		
		@Override
		public Class<X> getObservationType() {
			return subtype;
		}
		
		ListHistory(Class<A> agentType, Enum<?> name, Class<X> subtype, ListMapper<A,X> mapper, Integer maxElements) {
			super(agentType, name);
			this.subtype = subtype;
			this.mapper = mapper;
			this.maxElements = maxElements;
		}
		
	}
	
}
