package io.github.ai4ci.basicModel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Arrays;

import javax.swing.JFrame;

import sim.display.ChartUtilities;
import sim.display.ChartUtilities.ProvidesDoubles;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.portrayal.DrawInfo2D;
import sim.portrayal.Inspector;
import sim.portrayal.continuous.ContinuousPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.Valuable;
import sim.util.media.chart.HistogramGenerator;
import sim.util.media.chart.HistogramSeriesAttributes;
import sim.util.media.chart.TimeSeriesAttributes;
import sim.util.media.chart.TimeSeriesChartGenerator;

public class SimpleOutbreakWithUI extends GUIState {

	public Display2D display;
	public JFrame displayFrame;
	ContinuousPortrayal2D yardPortrayal = new ContinuousPortrayal2D();

	
	public SimpleOutbreakWithUI(SimState state) {
		super(state);
		// TODO Auto-generated constructor stub
	}

	public SimpleOutbreakWithUI() {
		super(new SimpleOutbreak(System.currentTimeMillis()));
	}

	public static void main(String[] args) {
		SimpleOutbreakWithUI vid = new SimpleOutbreakWithUI();
		Console c = new Console(vid);
		c.setVisible(true);
		
	}
	
	public static String getName() {return "Simple SIR outbreak";}

	
	public void start() {
		super.start();
//		SimpleOutbreak outbreak = (SimpleOutbreak) state;
//		outbreak.schedule.reset();
		setupPortrayals();
		ts.clearAllSeries();
		ts2.clearAllSeries();
		ts3.clearAllSeries();
		ts4.clearAllSeries();
		updateGraphs();
	}
	
	public void load(SimState state) {
		super.load(state);
		setupPortrayals();
		updateGraphs();
		if (((SimpleOutbreak) state).complete ) {
			this.finish();
		}
	}
	public void setupPortrayals() {
		SimpleOutbreak outbreak = (SimpleOutbreak) state;
		// tell the portrayals what to portray and how to portray them
		yardPortrayal.setField( outbreak.field );
		yardPortrayal.setPortrayalForAll(new OvalPortrayal2D(2.0) { //5.0) {
			public void draw(Object object, Graphics2D graphics, DrawInfo2D info) {
				Person person = (Person) object;
				paint = person.getStatus().equals(Person.Status.SUSCEPTIBLE) ?
						Color.gray :
							(person.getStatus().equals(Person.Status.INFECTIOUS) ? 
									Color.red : Color.blue);
				super.draw(object, graphics, info);
			}});
			
		// reschedule the displayer
		display.reset();
		display.setBackdrop(Color.white);
		// redraw the display
		display.repaint();
	}
	
	public void updateGraphs() {
		SimpleOutbreak outbreak = (SimpleOutbreak) state;
		ChartUtilities.scheduleSeries(this, infTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getInfectious();
			}
		});
		ChartUtilities.scheduleSeries(this, recTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getRecovered();
			}
		});
		ChartUtilities.scheduleSeries(this, susTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getSusceptible();
			}
		});
		ChartUtilities.scheduleSeries(this, rTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getR();
			}
		});
		this.scheduleRepeatingImmediatelyBefore(new Steppable() {
			@Override
			public void step(SimState state) {
				double bins = Arrays.stream(outbreak.getDispersion()).max().orElse(1);
				dispHist.setNumBins((int) bins);
			}
		});
		ChartUtilities.scheduleSeries(this, dispHist, new ProvidesDoubles() {
			@Override
			public double[] provide() {
				return outbreak.getDispersion();
			}
		});
		ChartUtilities.scheduleSeries(this, socTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getSociabilityMean();
			}
		});
		ChartUtilities.scheduleSeries(this, mobTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getMobilityMean();
			}
		});
//		ChartUtilities.scheduleSeries(this, mobHist2, new ProvidesDoubles() {
//			@Override
//			public double[] provide() {
//				return outbreak.dataMobilityFactor();
//			}
//		});
//		ChartUtilities.scheduleSeries(this, socHist3, new ProvidesDoubles() {
//			@Override
//			public double[] provide() {
//				return outbreak.dataSociabilityFactor();
//			}
//		});
		ChartUtilities.scheduleSeries(this, incidTs, new Valuable() {
			@Override
			public double doubleValue() {
				return outbreak.getIncidence();
			}
		});
	}
	
	TimeSeriesChartGenerator ts;
	TimeSeriesAttributes infTs;
	TimeSeriesAttributes recTs;
	TimeSeriesAttributes susTs;
	TimeSeriesAttributes rTs;
	TimeSeriesChartGenerator ts2;
	HistogramSeriesAttributes dispHist;
	HistogramGenerator hist;
	
	TimeSeriesAttributes mobTs;
	TimeSeriesAttributes socTs;
	TimeSeriesChartGenerator ts3;
	
//	HistogramSeriesAttributes mobHist2;
//	HistogramGenerator hist2;
//	
//	HistogramSeriesAttributes socHist3;
//	HistogramGenerator hist3;
	
	TimeSeriesAttributes incidTs;
	TimeSeriesChartGenerator ts4;
	
	public void init(Controller c) {
		super.init(c);
		display = new Display2D(1000,1000,this);
		display.setClipping(true);
		displayFrame = display.createFrame();
		displayFrame.setTitle("Outbreak Display");
		c.registerFrame(displayFrame);
		// so the frame appears in the "Display" list
		displayFrame.setVisible(false);
		display.attach( yardPortrayal, "Field" );
		
		ts = ChartUtilities.buildTimeSeriesChartGenerator(this, "Overview", "time");
		ts.setYAxisLabel("counts");
		infTs = ChartUtilities.addSeries(ts, "infectious");
		recTs = ChartUtilities.addSeries(ts, "recovered");
		susTs = ChartUtilities.addSeries(ts, "susceptible");
		
		ts2 = ChartUtilities.buildTimeSeriesChartGenerator(this, "Reproduction number", "time");
		ts2.setYAxisLabel("R");
		rTs = ChartUtilities.addSeries(ts2, "R number");
		
		hist = ChartUtilities.buildHistogramGenerator(this, "Dispersion", "count");
		hist.setXAxisLabel("infectees");
		hist.setYAxisLogScaled(true);
		dispHist = ChartUtilities.addSeries(hist, "dispersion", 1);
		// bin width?
		
		ts3 = ChartUtilities.buildTimeSeriesChartGenerator(this, "Mobility and Sociability", "time");
		ts3.setYAxisLabel("factor");
		mobTs = ChartUtilities.addSeries(ts3, "mobility");
		socTs = ChartUtilities.addSeries(ts3, "sociability");
		
		ts4 = ChartUtilities.buildTimeSeriesChartGenerator(this, "Incidence", "time");
		ts4.setYAxisLabel("cases");
		ts4.setYAxisLogScaled(false);
		incidTs = ChartUtilities.addSeries(ts4, "cases");
		
//		hist2 = ChartUtilities.buildHistogramGenerator(this, "Mobility", "count");
//		hist2.setYAxisLabel("factor");
//		mobHist2 = ChartUtilities.addSeries(hist2, "Relative mobility", 30);
//		
//		hist3 = ChartUtilities.buildHistogramGenerator(this, "Sociability", "count");
//		hist3.setYAxisLabel("factor");
//		socHist3 = ChartUtilities.addSeries(hist3, "Relative sociability", 30);
	}
	
	public void quit() {
		super.quit();
		if (displayFrame!=null) displayFrame.dispose();
		displayFrame = null;
		display = null;
	}
	
	public Object getSimulationInspectedObject() { return state; }
	
	
	public Inspector getInspector() {
		Inspector i = super.getInspector();
		i.setVolatile(true);
		return i;
	}

	
}
