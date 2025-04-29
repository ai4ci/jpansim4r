//package io.github.ai4ci.gui;
//
//import java.util.Collection;
//
//import javax.swing.SwingUtilities;
//
//import org.jfree.data.xy.XYSeries;
//
//import io.github.ai4ci.RObserver;
//import io.github.ai4ci.RSimulation;
//import io.github.ai4ci.RSteppable;
//import sim.display.GUIState;
//import sim.engine.SimState;
//import sim.engine.Steppable;
//import sim.util.media.chart.ChartGenerator;
//import sim.util.media.chart.SeriesAttributes;
//import sim.util.media.chart.TimeSeriesAttributes;
//import sim.util.media.chart.TimeSeriesChartGenerator;
//
//public class RChart<S extends RSimulation<S, ?, ?, ?>> {
//
//	S simulation;
//	GUIState state;
//	TimeSeriesChartGenerator gen;
//	Collection<Series> series;
//	
//	
//	
//	public void scheduleUpdates() {
//		series.forEach(s ->	state.scheduleRepeatingImmediatelyBefore(s));
//	}
//	
//	public void reset() {
//		gen.clearAllSeries();
//	}
//	
//	public class Series implements Steppable {
//		
//		TimeSeriesAttributes attr;
//		RObserver.History<S, ?> observer;
//		final XYSeries series = attr.getSeries();
//        double last = Schedule.BEFORE_SIMULATION;
//		
//		public void step(SimState state) {
//			  	final double x = simulation.lastObservationTime(observer).map(o->o.doubleValue()).orElse(Double.NaN);
//                if (x > last && x >= Schedule.EPOCH && x < Schedule.AFTER_SIMULATION) {
//                	
//                    final double value = simulation.lastObservation(observer).map(o -> {
//                    	if (o instanceof Number) {
//                            return ((Number) o).doubleValue();
//                        } else {
//                        	return Double.NaN;
//                        }
//                    }).orElse(Double.NaN);
//                                        
//                    // JFreeChart isn't synchronized.  So we have to update it from the Swing Event Thread
//                    SwingUtilities.invokeLater(new Runnable()
//                        {
//                        public void run()
//                            {
//                            attr.possiblyCull();
//                            series.add(x, value, true);
//                            }
//                        });
//                    // this will get pushed on the swing queue late
//                    attr.getGenerator().updateChartLater(state.schedule.getSteps());
//                    }
//                }
//	}
//	
//}



