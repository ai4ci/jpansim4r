package io.github.ai4ci.basicModel;

import java.util.Optional;

import ec.util.MersenneTwisterFast;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.continuous.Continuous2D;
import sim.util.Bag;
import sim.util.Double2D;
import sim.util.MutableDouble2D;

public class Person implements Steppable {

	private static final long serialVersionUID = 1L;
	
	public static enum Status {
		SUSCEPTIBLE, INFECTIOUS, RECOVERED;
	}
	
	public Long exposed = null;
	public Person infector = null; 
	public SimpleOutbreak outbreak = null;
	public int exposuresTminus1 = 0;
	public int exposures = 0;
	public int infectees = 0;
	
	private double mobilityBaseline = 1;
	private double sociabilityBaseline = 1;
	
	private double mobilityFactor = 1;
	private double sociabilityFactor = 1;
	
	public Person(SimpleOutbreak outbreak) {
		this.outbreak = outbreak;
	}

	public static Person init(Continuous2D field, MersenneTwisterFast random, SimpleOutbreak outbreak) {
		Person p = new Person(outbreak);
		field.setObjectLocation(p,
			new Double2D(
				field.getWidth() * (0.5 + random.nextDouble() - 0.5),
				field.getHeight() * (0.5 + random.nextDouble() - 0.5))
		);
		p.setMobilityBaseline(random);
		p.setSociabilityBaseline(random);
		return p;
	}

	private static double mu(double mean, double sd) {
		return Math.log(mean/(
				Math.sqrt(
					Math.pow(sd/mean,2)+1
				)
				));
	}
	
	private static double sigma(double mean, double sd) {
		return Math.sqrt(Math.log(Math.pow(sd/mean,2)+1));
	}
	
	public void setMobilityBaseline(MersenneTwisterFast random) {
		double mu = mu(outbreak.mobilityBaseline, outbreak.mobilitySd);
		double sigma = sigma(outbreak.mobilityBaseline, outbreak.mobilitySd);
		mobilityBaseline = Math.exp(
				random.nextGaussian()*sigma+mu);
	}
	
	public void setSociabilityBaseline(MersenneTwisterFast random) {
		double mu = mu(outbreak.sociabilityBaseline, outbreak.sociabilitySd);
		double sigma = sigma(outbreak.sociabilityBaseline, outbreak.sociabilitySd);
		sociabilityBaseline = Math.exp(
				random.nextGaussian()*sigma+mu);
	}
	
	public long getExposed() {
		if (exposed == null) return -1L;
		return exposed;
	}
	
	public Status getStatus() {
		if (exposed == null) return Status.SUSCEPTIBLE;
		long step = outbreak.schedule.getSteps();
		if (step-exposed < outbreak.serialInterval.length) return Status.INFECTIOUS;
		return Status.RECOVERED;
	}
	
	@Override
	public void step(SimState state) {
		SimpleOutbreak outbreak = (SimpleOutbreak) state;
		this.outbreak = outbreak;
		Double2D me = outbreak.field.getObjectLocation(this);

		// Expose nearby neighours if infectious (toroidal)
		if (this.getStatus().equals(Status.INFECTIOUS)) {
			Bag bag = outbreak.field.getNeighborsExactlyWithinDistance(me, outbreak.spreadDistance * getCurrentSociability(), true);
			for (Object object: bag) {
				
				Person neighbour = (Person) object;
				this.expose(neighbour);
				
			}
		}
	
		// Update position (toroidal)
		MutableDouble2D meNew = new MutableDouble2D(me);
		meNew.addIn(
				(outbreak.random.nextDouble()-0.5)*2* outbreak.spreadDistance * getCurrentMobility(),
				(outbreak.random.nextDouble()-0.5)*2* outbreak.spreadDistance * getCurrentMobility()
		);
		meNew.x = (meNew.x+outbreak.field.width) % outbreak.field.width;
		meNew.y = (meNew.y+outbreak.field.height) % outbreak.field.height;
		
		outbreak.field.setObjectLocation(this, new Double2D(meNew));
		
		// Only stay in the sim if not recovered
		if (!this.getStatus().equals(Status.RECOVERED)) {
			if (!outbreak.complete) outbreak.schedule.scheduleOnce(this);
		}
	}
	
	/**
	 * Decide my infectiousness based on time since infection. This is a 
	 * adjusted serial interval lookup at the moment. (adjusted for the overall
	 * pInfectionGivenContact) 
	 * @return
	 */
	public double infectiousness() {
		long step = outbreak.schedule.getSteps();
		if (exposed == null) return 0D;
		if (step-exposed < outbreak.adjSerialInterval.length) return outbreak.adjSerialInterval[(int) (step-exposed)];
		return 0D;
	}
	
	
	public void expose(Person contact) {
		double tmp = infectiousness();
		if (tmp > 0) {
			contact.exposures += 1;
			if (outbreak.random.nextDouble() < tmp) {
				if (contact.infect(this)) {
					infectees +=1;
				};
			}
		}
	}
	
	public boolean infect(Person infector) {
		if (exposed != null) {
			return false;
		} else {
			exposed = outbreak.schedule.getSteps();
			this.infector = infector;
			return true;
		}
	}
	
	public Optional<Person> getInfector() {
		return Optional.ofNullable(infector);
	};
	
	public long getSocialCircleSize() {
		return (long) (
			Math.pow(outbreak.spreadDistance * sociabilityBaseline, 2) * Math.PI * outbreak.density()  
		);
	}
	
	public double getCurrentSociability() {
		return this.sociabilityBaseline * this.sociabilityFactor;
	}
	
	public double getCurrentMobility() {
		return this.mobilityBaseline * this.mobilityFactor;
	}
	
	public double getLocalPrevalence() {
		if (getSocialCircleSize() == 0) return this.exposuresTminus1==0 ? 0 : 1;  
		return ((double) this.exposuresTminus1) / getSocialCircleSize();
	}
	
	public void setCurrentBehaviour(double sociability, double mobility) {
		this.mobilityFactor = mobility;
		this.sociabilityFactor = sociability;
	}
	
	public void adjustCurrentBehaviour(double sociability, double mobility, boolean allowAboveBaseline, double minSociability, double minMobility) {
		this.mobilityFactor = Math.max(this.mobilityFactor*mobility, minMobility);
		this.sociabilityFactor = Math.max(this.sociabilityFactor*sociability, minSociability);
		if (!allowAboveBaseline) {
			if (this.sociabilityFactor > 1) this.sociabilityFactor = 1;
			if (this.mobilityFactor > 1) this.mobilityFactor = 1;
		}
	}
	
	public void returnToBaselineBehaviour() {
		this.sociabilityFactor = 1;
		this.mobilityFactor = 1;
	}
}
