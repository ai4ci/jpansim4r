package io.github.ai4ci.basicModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.util.MathArrays;

import io.github.ai4ci.basicModel.Person.Status;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.continuous.Continuous2D;
import sim.util.Interval;

public class SimpleOutbreak  extends SimState {
	
	private static final long serialVersionUID = 1L;
	private ArrayList<Person> people = new ArrayList<>();

	protected ArrayList<Person> getPeople() {return people;}
	
	public SimpleOutbreak(long seed) {
		super(seed);
		serialInterval = MathArrays.normalizeArray(serialInterval,1.0);
		this.setR0(3.0);
		this.setPInfectedGivenContact(0.2);
		this.setLockdowns(false);
		this.setRiskAvoidance(false);
		this.setImports(10);
		
	}

	public Continuous2D field = new Continuous2D(1, 1000, 1000);
	public int population = 100000;
	public double[] serialInterval = {0D, 0.1D, 0.3D, 0.3D, 0.2D, 0.1D};
	public double R0 = 2.0;
	public double[] adjSerialInterval; 
	public double pInfectedGivenContact = 0.2; 
	//this is R0 per contact
	// Spread distance adjusts R0 and pInfectedGivenContact to the density of 
	// the population.
	// need a default because the inference of spreadDistance 
	public double spreadDistance;
	public int imports;
	public boolean riskAvoidance = false;
	public boolean lockdowns = false;
	
	/* GETTERS & SETTERS */
	
	public boolean isLockdowns() {
		return lockdowns;
	}

	public void setLockdowns(boolean lockdowns) {
		if (!this.lockdowns && lockdowns) this.schedule.scheduleOnce(new PopulationLockdownStrategy());
		this.lockdowns = lockdowns;
	}
	
	public boolean isRiskAvoidance() {
		return riskAvoidance;
	}

	public void setRiskAvoidance(boolean riskAvoidance) {
		if (!this.riskAvoidance && riskAvoidance) this.schedule.scheduleOnce(new PersonalRiskAvoidanceStrategy());
		this.riskAvoidance = riskAvoidance;
	}

	public int getImports() {
		return imports;
	}

	public void setImports(int imports) {
		this.imports = imports;
	}

	public double getSpreadDistance() {
		return spreadDistance;
	}
	
	public double getR0() {
		return R0;
	}
	public void setR0(double R0) {
		this.R0 = R0;
		inferSpreadDistance();
	}
	public Object domR0() { return new Interval(0.0 , 20); }

	
	public void inferSpreadDistance() {
		
		spreadDistance = 
			Math.sqrt(
				// total number of contact
				R0 / pInfectedGivenContact
				// divided by density (p/km2) to give area
				/ (population/(field.height*field.width))
				// and inverse PI.r^2 to give linear dimension
				/ Math.PI
			);
	}
	
	public double getPInfectedGivenContact() {
		return pInfectedGivenContact;
	}
	public void setPInfectedGivenContact(double value) {
		inferSpreadDistance();
		this.pInfectedGivenContact = value;
		BrentSolver solver = new BrentSolver();
		double factor = solver.solve(100, k -> {
			double tmp = 1-Arrays.stream(serialInterval)
				.map(d -> (1-Math.pow(d, k)))
				.reduce(1, (x,y) -> x*y);
			return tmp-pInfectedGivenContact;
		},0,1000);
		adjSerialInterval = Arrays
			.stream(serialInterval)
			.map(p -> Math.pow(p, factor))
			.toArray();
	}
	public Object domPInfectedGivenContact() {
		return new Interval(0, 1.0);
	}
	
	
	
	public void start() {
		super.start();
		this.people = new ArrayList<>();
		this.complete = false;
		this.field.clear();
		this.schedule.clear();
		for (int i=0; i<population; i++) {
			Person person = Person.init(field, random, this);
			people.add(person);
			if (i<imports) 
				person.infect(null);
			
			this.schedule.scheduleOnce(person);
		}
		this.schedule.addAfter(new Steppable() {
			@Override
			public void step(SimState state) {
				SimpleOutbreak outbreak = (SimpleOutbreak) state;
				if (outbreak.getInfectious() == 0) outbreak.complete = true;
			}
		});
		// reset personal exposure counters
		this.schedule.addBefore(new Steppable() {
			@Override
			public void step(SimState state) {
				SimpleOutbreak outbreak = (SimpleOutbreak) state;
				outbreak.people.forEach(p -> {
					p.exposuresTminus1 = p.exposures;
					p.exposures = 0;
				});
			}
		}); 
		if (lockdowns) this.schedule.scheduleOnce(new PopulationLockdownStrategy());
		if (riskAvoidance) this.schedule.scheduleOnce(new PersonalRiskAvoidanceStrategy());
	}
	
	boolean complete = false;
	
	public double mobilityBaseline = 1;
	
	public double getMobilityBaseline() {
		return mobilityBaseline;
	}

	public void setMobilityBaseline(double mobilityBaseline) {
		this.mobilityBaseline = mobilityBaseline;
		people.stream().forEach(p -> p.setMobilityBaseline(random));
	}
	
	public Object domMobilityBaseline() {
		return new Interval(0.0,2.0);
	}
	
	public double mobilitySd = 0.25;
	
	public double getMobilitySd() {
		return mobilitySd;
	}

	public void setMobilitySd(double mobilitySd) {
		this.mobilitySd = mobilitySd;
		people.stream().forEach(p -> p.setMobilityBaseline(random));
	}
	
	public Object domMobilitySd() {
		return new Interval(0.0,2.0);
	}

	// SOCIABILITY
	
	public double sociabilityBaseline = 1;
	
	public double getSociabilityBaseline() {
		return sociabilityBaseline;
	}

	public void setSociabilityBaseline(double sociabilityBaseline) {
		this.sociabilityBaseline = sociabilityBaseline;
		people.stream().forEach(p -> p.setSociabilityBaseline(random));
	}
	
	public Object domSociabilityBaseline() {
		return new Interval(0.0,2.0);
	}
	
	public double sociabilitySd = 0.25;
	public double density() {
		return ((double) population)/(field.height*field.width);
	};
	
	public double getSociabilitySd() {
		return sociabilitySd;
	}

	public void setSociabilitySd(double sociabilitySd) {
		this.sociabilitySd = sociabilitySd;
		people.stream().forEach(p -> p.setSociabilityBaseline(random));
	}
	
	public Object domSociabilitySd() {
		return new Interval(0.0,2.0);
	}
	
	public void returnToBaselineBehaviour() {
		this.people.forEach(p -> p.returnToBaselineBehaviour());
	}
	
	public void setCurrentBehaviour(double sociability, double mobility) {
		this.people.forEach(p -> p.setCurrentBehaviour(sociability, mobility));
	}
	
	public void adjustCurrentBehaviour(double sociability, double mobility, boolean allowAboveBaseline,  double minSociability, double minMobility) {
		this.people.forEach(p -> p.adjustCurrentBehaviour(sociability, mobility, allowAboveBaseline, minSociability, minMobility));
	}
	
	public double[] dataWhenInfected() {
		double[] tmp = people.stream()
				.filter(p -> !p.getStatus().equals(Status.SUSCEPTIBLE))
				.mapToDouble(p -> (double) p.exposed).toArray();
		if (tmp.length == 0) return new double[1];
		return tmp;
	}
	
	public long getIncidence() {
		return people.stream()
			.filter(p -> (p.exposed != null && p.exposed == this.schedule.getSteps()-1))
			.count();
	}
	
	public double[] getDispersion() {
		double[] tmp = people.stream()
				.filter(p -> p.getStatus().equals(Status.RECOVERED))
				.mapToDouble(p -> (double) p.infectees).toArray();
		if (tmp.length == 0) return new double[1];
		return tmp;
	}
	
	public double getMobilityMean() {
		return people.stream()
				.filter(p -> !p.getStatus().equals(Status.RECOVERED))
				.mapToDouble(p -> p.getCurrentMobility())
				.average().orElse(this.mobilityBaseline);
	}
	
	public double getSociabilityMean() {
		return people.stream()
				.filter(p -> !p.getStatus().equals(Status.RECOVERED))
				.mapToDouble(p -> p.getCurrentSociability())
				.average().orElse(this.sociabilityBaseline);
	}
	
	public long getSusceptible() {
		return people.stream().filter(p -> p.getStatus().equals(Status.SUSCEPTIBLE)).count();
	}
	
	public long getRecovered() {
		return people.stream().filter(p -> p.getStatus().equals(Status.RECOVERED)).count();
	}
	
	public long getInfectious() {
		return people.stream().filter(p -> p.getStatus().equals(Status.INFECTIOUS)).count();
	}
	
	public double getR() {
		List<Person> infectedAtStep = people.stream()
				.filter(p -> p.getExposed() == this.schedule.getSteps()-this.serialInterval.length-1)
				.collect(Collectors.toList());
		int infectees = infectedAtStep.stream()
				.mapToInt(p -> p.infectees)
				.sum();
		return ((double) infectees)/infectedAtStep.size();
	}
	
	public double getPrevalence() {
		return ((double) getInfectious())/population;
	}
}
