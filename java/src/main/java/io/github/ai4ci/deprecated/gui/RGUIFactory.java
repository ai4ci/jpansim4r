//package io.github.ai4ci.gui;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.function.BiConsumer;
//
//import io.github.ai4ci.RAgent;
//import io.github.ai4ci.RSimulation;
//import io.github.ai4ci.gui.RGUI.ChartGeneratorHolder;
//import io.github.ai4ci.gui.RGUI.PortrayalHolder;
//import io.github.ai4ci.gui.RGUI.SeriesAttributeHolder;
//import sim.display.ChartUtilities;
//import sim.display.Display2D;
//import sim.portrayal.Portrayal;
//import sim.portrayal.continuous.ContinuousPortrayal2D;
//import sim.util.media.chart.ChartGenerator;
//import sim.util.media.chart.SeriesAttributes;
//import sim.util.media.chart.TimeSeriesChartGenerator;
//
//public class RGUIFactory<
//	S extends RSimulation<S,?,?,A>,
//	A extends RAgent<A,S,?,?>
//	> {
//
//	S simulation;
//	RGUI<S,A> gui;
//	RGUI<S,A>.PortrayalHolder<?> portrayal;
//	Collection<ChartFactory> charts = new ArrayList<>();
//	
//	
//	public RGUIFactory(S simulation) {
//		this.simulation = simulation;
//		this.gui = new RGUI<S,A>(simulation);
//	}
//	
//	public <X extends Portrayal> RGUIFactory<S,A> withPortrayal(Class<X> type, String title, double height, double width) {
//		
//		if (type.equals(ContinuousPortrayal2D.class)) {
//		
//			RGUI<S,A>.PortrayalHolder<ContinuousPortrayal2D> p2 = gui.new PortrayalHolder<ContinuousPortrayal2D>();
//			ContinuousPortrayal2D p = new ContinuousPortrayal2D();
//			p2.portrayal = p;
//			p2.display = new Display2D(width,height,gui);
//			p2.display.setClipping(true);
//			p2.displayFrame = p2.display.createFrame();
//			p2.displayFrame.setTitle(title);
//			gui.c.registerFrame(p2.displayFrame);
//			// so the frame appears in the "Display" list
//			p2.displayFrame.setVisible(false);
//			p2.display.attach(p, title);
//			
//			this.portrayal = p2;
//		
//		} else {
//			throw new RuntimeException("Not yet implemented: "+type);
//		}
//		
//		return this;
//	}
//	
//	private <Z extends SeriesAttributes> RGUI<S,A>.SeriesAttributeHolder<Z> newSeries(
//			Z att,
//			BiConsumer<S, Z> fn2) {
//		RGUI<S,A>.SeriesAttributeHolder<Z> tmp = gui.new SeriesAttributeHolder<Z>();
//		tmp.attribute = att;
//		tmp.fn = fn2;
//		tmp.scheduleUpdates();
//		return tmp;
//	}
//	
//	
//	@SuppressWarnings("unchecked")
//	public <X extends ChartGenerator, Y extends RGUI<S,A>.SeriesAttributeHolder<?>> ChartFactory withChart(Class<X> type, String title, String xLab, String yLab) {
//		RGUI<S,A>.ChartGeneratorHolder<X,Y> tmp = gui.new ChartGeneratorHolder<X,Y>();
//		if (type.equals(TimeSeriesChartGenerator.class)) {
//			TimeSeriesChartGenerator tmp2 = ChartUtilities.buildTimeSeriesChartGenerator(gui, title, xLab);
//			tmp2.setYAxisLabel(yLab);
//			tmp.cg = (X) tmp2;
//		} else {
//			throw new RuntimeException("Not yet implemented: "+type);
//		}
//		ChartFactory cf = new ChartFactory(tmp);
//		this.charts.add(cf);
//		return cf;
//	}
//	
//	public class ChartFactory {
//		
//		ChartFactory(RGUI<S,A>.ChartGeneratorHolder<?,?> cgh) {
//			this.cgh = cgh;
//		}
//		RGUI<S,A>.ChartGeneratorHolder<?,?> cgh;
//		
//		
//		public RGUIFactory<S,A> complete() {return RGUIFactory.this;}
//	}
//}



