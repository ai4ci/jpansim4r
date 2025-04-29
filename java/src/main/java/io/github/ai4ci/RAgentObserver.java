//package io.github.ai4ci;
//
//import java.io.Serializable;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Optional;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//public abstract class RAgentObserver<
//	A extends RAgent<A,?,?,?>, 
//	X> implements RObserver<A,X>,Serializable {
//
////	public RAgentObserver(Class<A> agentType) {
////		this(agentType, agentType.getSimpleName()+" observer");
////	}
//	
//	public RAgentObserver(Class<A> agentType, Enum<?> name) {
//		this.agentType = agentType;
//		this.name = name.name();
//	}
//
//	Class<A> agentType;
//	String name;
//	A agent;
//	
//	public String getName() {
//		return this.name;
//	}
//	
//	public void setSubject(A agent2) {
//		agent = agent2;
//	}
//
//	public A getSubject() {
//		return agent;
//	}
//	
//	public Class<A> getAgentType() {
//		return agentType;
//	}
//	
//	@Override
//	public abstract void update(A subject);
//	
//	
//	
//	
//	
//}
