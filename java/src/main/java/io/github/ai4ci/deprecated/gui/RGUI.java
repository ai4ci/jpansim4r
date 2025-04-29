//package io.github.ai4ci.gui;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.function.BiConsumer;
//import java.util.function.Function;
//
//import javax.swing.JFrame;
//
//import io.github.ai4ci.RAgent;
//import io.github.ai4ci.RSimulation;
//import io.github.ai4ci.RSteppable;
//import io.github.ai4ci.gui.RGUIUtilities.AgentPortrayalMap;
//import sim.display.ChartUtilities;
//import sim.display.Console;
//import sim.display.Controller;
//import sim.display.Display2D;
//import sim.display.GUIState;
//import sim.engine.SimState;
//import sim.portrayal.FieldPortrayal;
//import sim.portrayal.Inspector;
//import sim.portrayal.Portrayal;
//import sim.portrayal.continuous.ContinuousPortrayal2D;
//import sim.util.media.chart.ChartGenerator;
//import sim.util.media.chart.SeriesAttributes;
//import sim.util.media.chart.TimeSeriesAttributes;
//import sim.util.media.chart.TimeSeriesChartGenerator;
//
//public class RGUI<
//		S extends RSimulation<S,?,?,A>,
//		A extends RAgent<A,S,?,?>
//	> extends GUIState {
//
//	Console c;
//	
//	public RGUI(S simulation) {
//		super(simulation);
//		c = new Console(this);
//		c.setVisible(true);
//		
//	}
//	
//	@SuppressWarnings("unchecked")
//	public S getSimulation() {
//		return (S) state;
//	}
//	
//	// Portrayal
//	
//	
//	
//	public class PortrayalHolder<X extends Portrayal> {
//		Display2D display;
//		JFrame displayFrame;
//		X portrayal;
//		
//		public void setup(
//				Function<S,Object> contextProvider,
//				AgentPortrayalMap portrayalSelector 
//			) {
//			Object context = contextProvider.apply(getSimulation());
//			if (portrayal instanceof FieldPortrayal) {
//				FieldPortrayal tmp = (FieldPortrayal) portrayal;
//				tmp.setField(context);
//				portrayalSelector.forEach(
//					map -> tmp.setPortrayalForClass(map.agentType,map.p)
//				);
//				
//			
//			} else {
//				throw new RuntimeException("Not yet implemented: "+portrayal.getClass());
//			}
//		}
//		
//		public void quit() {
//			if (this.displayFrame != null) this.displayFrame.dispose();
//			this.displayFrame = null;
//			this.display = null;
//		}
//	}
//	
//	// Chart
//	
//	
//	
//	public class ChartGeneratorHolder<X extends ChartGenerator, Y extends SeriesAttributeHolder<?>> {
//		
//		X cg;
//		Collection<Y> attributes = new ArrayList<>();
//		
//		/**
//		 * Add a series onto a graph. 
//		 * @param <Z> the series attributes e needs to be 
//		 * @param name
//		 * @param fn this should define how the series is updated by data from 
//		 * the simulation (simulation,series) -> series.doSomething(simulation.getData());
//		 * @return
//		 */
//		@SuppressWarnings("unchecked")
//		public <Z extends SeriesAttributes> ChartGeneratorHolder<X,Y> withSeries(String name, BiConsumer<S,Z> fn) {
//			
//			if (cg instanceof TimeSeriesChartGenerator) {
//				TimeSeriesChartGenerator tscg = (TimeSeriesChartGenerator) cg;
//				TimeSeriesAttributes att = ChartUtilities.addSeries(tscg, name);
//				attributes.add(
//					(Y) RGUI.this.newSeries(att, (BiConsumer<S, TimeSeriesAttributes>) fn)
//				);
//			} else {
//				throw new RuntimeException("Not yet implemented: "+cg.getClass());
//			}
//			
//			return this;
//		}
//		
//		public void reset() {
//			
//			if (cg instanceof TimeSeriesChartGenerator) {
//				TimeSeriesChartGenerator tscg = (TimeSeriesChartGenerator) cg;
//				tscg.clearAllSeries();
//			} else {
//				throw new RuntimeException("Not yet implemented: "+cg.getClass());
//			}
//			
//		}
//	}
//	
//	public class SeriesAttributeHolder<Y extends SeriesAttributes> {
//		
//		Y attribute;
//		BiConsumer<S,Y> fn;
//		
//		public void scheduleUpdates() {
//			RGUI.this.scheduleRepeatingImmediatelyBefore(new RSteppable<S>() {
//				@Override
//				public void doStep(S simulation) {
//					fn.accept(simulation, attribute);
//				}
//
//				@Override
//				public boolean remainsActive(S simulation) {
//					return !simulation.isComplete();
//				}
//
//			});
//		}
//		
//	}
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	
//	public void start() {
//		super.start();
//		this.withPortrayal(null, getName(), beforeSize, afterSize);
//		// portrayal.setup(null, null);
//		charts.forEach(c -> c.reset());
//		// updateGraphs();
//	}
//	
//	public void load(SimState state) {
//		super.load(state);
//		this.withPortrayal(null, getName(), beforeSize, afterSize);
//		// portrayal.setup(null, null);
//		// updateGraphs();
//		
//	}
//	
//	public void init(Controller c) {
//		super.init(c);
//		// this.withPortrayal(null, getName(), beforeSize, afterSize);
//		// this.withChart(null, getName(), getName(), getName())
//		
//		
//	}
//	
//	public void quit() {
//		super.quit();
//		this.portrayal.quit();
//	}
//	
//	public Object getSimulationInspectedObject() { 
//		return getSimulation().getParameterisation(); 
//	}
//	
//	public Inspector getInspector() {
//		Inspector i = super.getInspector();
//		i.setVolatile(true);
//		return i;
//	}
//}



