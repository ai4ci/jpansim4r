package io.github.ai4ci;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

import sim.portrayal.DrawInfo2D;
import sim.portrayal.Portrayal;
import sim.portrayal.simple.OvalPortrayal2D;

public class RGUIUtilities {

	public static class AgentPortrayalEntry<A extends RAgent<A,?,?,?>> {
		Class<A> agentType;
		Portrayal p;
	}
	
	public static class AgentPortrayalMap extends ArrayList<AgentPortrayalEntry<?>> {
		
		public static AgentPortrayalMap create() {
			return new AgentPortrayalMap();
		}
		
		public <X extends RAgent<X,?,?,?>> AgentPortrayalMap withAgentAsPoint(
			Class<X> agentClass,
			Function<X,Paint> colourPicker
		) {
			
			AgentPortrayalEntry<X> tmp = new AgentPortrayalEntry<X>();
			tmp.agentType = agentClass;
			tmp.p = new OvalPortrayal2D(2.0) { //5.0) {
				public void draw(Object object, Graphics2D graphics, DrawInfo2D info) {
				@SuppressWarnings("unchecked")
					X person = (X) object;
					this.paint = colourPicker.apply(person);
					super.draw(object, graphics, info);
				}
			};
			this.add(tmp);
			return this;
		}
		
		public <X extends RAgent<X,?,?,?>> Optional<Portrayal> forClass(Class<X> agentType) {
			return this.stream().filter(e -> e.agentType.equals(agentType))
				.map(p -> p.p)
				.findFirst();
		}
	}	
	
	
	
}
