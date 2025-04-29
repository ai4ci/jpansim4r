package io.github.ai4ci;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JFrame;

import io.github.ai4ci.RGUIUtilities.AgentPortrayalMap;
import sim.display.ChartUtilities;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.FieldPortrayal;
import sim.portrayal.Inspector;
import sim.portrayal.Portrayal;
import sim.portrayal.continuous.ContinuousPortrayal2D;
import sim.util.media.chart.ChartGenerator;
import sim.util.media.chart.SeriesAttributes;
import sim.util.media.chart.TimeSeriesAttributes;
import sim.util.media.chart.TimeSeriesChartGenerator;

public abstract class RGUI<
		S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>
	> extends GUIState {

	Console c;
	PortrayalHolder<?> portrayal;
	Collection<ChartGeneratorHolder<?,?>> charts = new ArrayList<>();
	
	
	public RGUI(S simulation) {
		super(simulation);
		c = new Console(this);
		c.setVisible(true);
		
	}
	
	// Portrayal
	
	public <X extends Portrayal> RGUI<S,A> withPortrayal(Class<X> type, String title, double height, double width) {
		
		if (type.equals(ContinuousPortrayal2D.class)) {
		
			PortrayalHolder<ContinuousPortrayal2D> p2 = new PortrayalHolder<ContinuousPortrayal2D>();
			ContinuousPortrayal2D p = new ContinuousPortrayal2D();
			p2.portrayal = p;
			p2.display = new Display2D(width,height,RGUI.this);
			p2.display.setClipping(true);
			p2.displayFrame = p2.display.createFrame();
			p2.displayFrame.setTitle(title);
			c.registerFrame(p2.displayFrame);
			// so the frame appears in the "Display" list
			p2.displayFrame.setVisible(false);
			p2.display.attach(p, title);
			this.portrayal = p2;
		
		} else {
			throw new RuntimeException("Not yet implemented: "+type);
		}
		
		return this;
	}
	
	public class PortrayalHolder<X extends Portrayal> {
		Display2D display;
		JFrame displayFrame;
		X portrayal;
		
		public void setup(
				Function<S,Object> contextProvider,
				AgentPortrayalMap portrayalSelector 
			) {
			Object context = contextProvider.apply(getSimulation());
			if (portrayal instanceof FieldPortrayal) {
				FieldPortrayal tmp = (FieldPortrayal) portrayal;
				tmp.setField(context);
				portrayalSelector.forEach(
					map -> tmp.setPortrayalForClass(map.agentType,map.p)
				);
				
			
			} else {
				throw new RuntimeException("Not yet implemented: "+portrayal.getClass());
			}
		}
		
		public void quit() {
			if (this.displayFrame != null) this.displayFrame.dispose();
			this.displayFrame = null;
			this.display = null;
		}
	}
	
	// Chart
	
	@SuppressWarnings("unchecked")
	public <X extends ChartGenerator, Y extends SeriesAttributeHolder<?>> RGUI<S,A> withChart(Class<X> type, String title, String xLab, String yLab) {
		ChartGeneratorHolder<X,Y> tmp = new ChartGeneratorHolder<X,Y>();
		if (type.equals(TimeSeriesChartGenerator.class)) {
			TimeSeriesChartGenerator tmp2 = ChartUtilities.buildTimeSeriesChartGenerator(RGUI.this, title, xLab);
			tmp2.setYAxisLabel(yLab);
			tmp.cg = (X) tmp2;
		} else {
			throw new RuntimeException("Not yet implemented: "+type);
		}
		
		return this;
	}
	
	public class ChartGeneratorHolder<X extends ChartGenerator, Y extends SeriesAttributeHolder<?>> {
		
		X cg;
		Collection<Y> attributes = new ArrayList<>();
		
		/**
		 * Add a series onto a graph. 
		 * @param <Z> the series attributes e needs to be 
		 * @param name
		 * @param fn this should define how the series is updated by data from 
		 * the simulation (simulation,series) -> series.doSomething(simulation.getData());
		 * @return
		 */
		@SuppressWarnings("unchecked")
		public <Z extends SeriesAttributes> ChartGeneratorHolder<X,Y> withSeries(String name, BiConsumer<S,Z> fn) {
			
			if (cg instanceof TimeSeriesChartGenerator) {
				TimeSeriesChartGenerator tscg = (TimeSeriesChartGenerator) cg;
				TimeSeriesAttributes att = ChartUtilities.addSeries(tscg, name);
				attributes.add(
					(Y) RGUI.this.newSeries(att, (BiConsumer<S, TimeSeriesAttributes>) fn)
				);
			} else {
				throw new RuntimeException("Not yet implemented: "+cg.getClass());
			}
			
			return this;
		}
		
		public void reset() {
			
			if (cg instanceof TimeSeriesChartGenerator) {
				TimeSeriesChartGenerator tscg = (TimeSeriesChartGenerator) cg;
				tscg.clearAllSeries();
			} else {
				throw new RuntimeException("Not yet implemented: "+cg.getClass());
			}
			
		}
	}
	
	
	private <Z extends SeriesAttributes> SeriesAttributeHolder<Z> newSeries(
			Z att,
			BiConsumer<S, Z> fn2) {
		SeriesAttributeHolder<Z> tmp = new SeriesAttributeHolder<Z>();
		tmp.attribute = att;
		tmp.fn = fn2;
		tmp.scheduleUpdates();
		return tmp;
	}
	
	public class SeriesAttributeHolder<Y extends SeriesAttributes> {
		
		Y attribute;
		BiConsumer<S,Y> fn;
		
		public void scheduleUpdates() {
			RGUI.this.scheduleRepeatingImmediatelyBefore(new RSteppable<S>() {
				@Override
				public void doStep(S simulation) {
					fn.accept(simulation, attribute);
				}

				@Override
				public boolean remainsActive(S simulation) {
					return !simulation.isComplete();
				}

			});
		}
		
	}
	
	
	
	
	
	
	
	
	
	
	@SuppressWarnings("unchecked")
	public S getSimulation() {
		return (S) state;
	}
	
	public void start() {
		super.start();
		this.withPortrayal(null, getName(), beforeSize, afterSize);
		// portrayal.setup(null, null);
		charts.forEach(c -> c.reset());
		// updateGraphs();
	}
	
	public void load(SimState state) {
		super.load(state);
		this.withPortrayal(null, getName(), beforeSize, afterSize);
		// portrayal.setup(null, null);
		// updateGraphs();
		
	}
	
	public void init(Controller c) {
		super.init(c);
		// this.withPortrayal(null, getName(), beforeSize, afterSize);
		// this.withChart(null, getName(), getName(), getName())
		
		
	}
	
	public void quit() {
		super.quit();
		this.portrayal.quit();
	}
	
	public Object getSimulationInspectedObject() { 
		return getSimulation().getParameterisation(); 
	}
	
	public Inspector getInspector() {
		Inspector i = super.getInspector();
		i.setVolatile(true);
		return i;
	}
}
