package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.stats.Binomial;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.Control;
import io.github.ai4ci.testModel.TestResult.Result;

public class Person extends RAgent<Person,Outbreak,Configuration.AgentBaseline,Configuration.AgentStatus> {

	public enum Observers {  TESTS, DETECTED_CONTACTS };

	public Person(Outbreak simulation) {
		super(simulation);
	}
	
	@Override
	public void setupStage3SetAgentBaseline() {
		
		Configuration.OutbreakConfig configuration = this.getSimulation().getConfiguration();
		
		this.setBaseline(Configuration.baselineFrom(configuration, this.sampler())); 
		
		keepHistory(
			Observers.TESTS, 
			TestResult.class,
			a -> a.testToday(),
			4
		);
		
		keepHistoryList(
			Observers.DETECTED_CONTACTS, 
			Person.Reference.class, 
			a -> a.getDetectedContacts().stream().map(a2 -> a2.weakReference()).collect(Collectors.toList()),
			14
		);
		
	}

	public Stream<TestResult> testHistory() {
		return this.getNamedObservation(Observers.TESTS, TestResult.class).stream();
	}
	
	@Override
	public boolean remainsActive() {
		// return this.getLastInfectedTime().orElse(Long.MAX_VALUE) + this.infectionDuration() > this.getSimTime();
		return !this.getSimulation().isComplete();
		// this.getOldStatus().map(o -> !o.getState().equals(State.RECOVERED)).orElse(Boolean.TRUE);
	}

	@Override
	public Person self() {return this;}

	@Override
	public void setupStage6InitialiseAgentStatus() {
		this.setStatus(
				Configuration.statusFrom(
						this.getSimulation().getConfiguration(), 
						this.getSimulation().getParameterisation(), 
						this.getBaseline(), 
						this.sampler()
				)
		);
	}

//	public State yesterdayState() {
//		return this.getOldStatus().map(s -> s.getState())
//			.orElse(State.SUSCEPTIBLE);
//	}
//	
//	public boolean infectedToday() {
//		return this.yesterdayState().equals(State.SUSCEPTIBLE) &&
//				this.getStatus().getState().equals(State.INFECTED);
//	}
	
	public Optional<TestResult> testToday() {
		return this.cached("testToday", TestResult.class, 
			a -> {
				double pTmp;
				// TODO: this logic for deciding if a person is tested needs
				// revision. This depends on all sorts of factors, and parameters
				// around the use of a test. It'll need to be implemented as a
				// strategy in the test class when we extend that.
				if (a.awaitingTestOrKnownPositiveToday()) {
					// Do not re-test if recent test positive or waiting for test
					// result unless randomly re-tested due to baseline test rate
					// e.g. screening.
					pTmp = this.getStatus().getBaseProbabilityOfTesting();
				} else {
					// Regardless of infection status the patient can be tested. 
					// This depends on a probability distribution of days since last infection and
					// a baseline probability
					// Combined probability of screening test or reactive test.
					pTmp = this.getSimulation().getParameterisation().getTestTakenProbability( 
							this.getDaysSinceLastInfection(),
							this.getStatus().getBaseProbabilityOfTesting()
					);
				}
				
				if (this.sampler().uniform() > pTmp) { 
					return Optional.empty();
				} else { 
					return Optional.of(new TestResult(
						this.infectiousness() > 0, // true test status as test is testing infectiousness 
						this.getSimTime().longValue(), // test date
						(long) Math.floor(this.sampler().logNormal(
							this.getSimulation().getParameterisation().getMeanTestDelay(),
							this.getSimulation().getParameterisation().getSdTestDelay()
						)), // per test test delay
						this.sampler(), // rng
						this.getSimulation().getParameterisation().getTestSensitivity(),
						this.getSimulation().getParameterisation().getTestSpecificity()
					));
				}
			}
		);
	}
	
	// TODO: This all needs restructuring for multiple tests with a single
	// test repository / factory.
	public List<TestResult> resultToday() {
		return 
				this.cachedList("results", TestResult.class, 
						a -> a.getNamedObservation(Observers.TESTS, TestResult.class)
							.stream()
							.flatMap(t -> t.publishedResult(this.getSimTime()).stream())
							.collect(Collectors.toList())
				);
	}
	
	@Override
	public void updateStatus() {
		if (this.getStatus().getState().equals(State.SUSCEPTIBLE)) {
			
			/* Bit unsure about this calculation
			 * The contacts can be regarded as infectors if a random number
			 * is less than their probability of infection given a specific
			 * day post infection.. 
			 * 
			 */
			Set<Person> infectors = this.getContacts().stream()
					.filter(a -> a.getStatus().getState().equals(State.INFECTED))
					.filter(a -> this.sampler().uniform() < a.infectiousness()*this.getStatus().getProbabilityInfectionGivenInfectiousContact() )
					.collect(Collectors.toSet());
			
				// Locally acquired infections
			if (infectors.size() > 0) {
				this.getStatus().setState(State.INFECTED);
				this.getStatus().setLastInfected(this.getSimTime());
				// TODO: figure out how to decide which is the infector in a fairer
				// way, or whether a multi infector model is possible.
				infectors.stream().findFirst().ifPresent(i -> {
					DirectedAcyclicGraph<Person, Infection> network = this.getSimulation()
						.getInfectionNetwork();
					synchronized(network) {
						network.addVertex(i);
						network.addVertex(this);
						network.addEdge(i, this, new Infection(this.getSimTime()) );
					}
				});	
			}
		
		} else if (this.getStatus().getState().equals(State.INFECTED)) {
			// Has the patient recovered and is no longer infectious?
			if (this.getSimulation().getParameterisation().isRecovered(this.getDaysSinceLastInfection().orElse(0L))) {
				this.getStatus().setState(State.RECOVERED);
				
			} 
		}
		
		// Regardless of infection status the patient can be tested
		// Calling this method makes sure the decision to test is made but we 
		// don't need the result.
		this.testToday().ifPresent(
				t -> this.getStatus().setLastTested(this.getSimTime())
		);
		
	}

	@Override
	public void changeBehaviour() {
		
		double lockdownContactRateAdjustment = this.getSimulation().getParameterisation().getLockdownContactRate() / (this.getSimulation().getConfiguration().getConnectedness() * this.getSimulation().getConfiguration().getMeanContactProbability());
		
		if (this.getSimulation().getParameterisation().getControl().equals(Control.LOCKDOWN)) {
			
			
			
			if (this.getSimulation().isInLockDown()) {
				
				this.getStatus().setContactRateAdjustment(lockdownContactRateAdjustment);
				
			} else {
				
				if (this.knownTestPositiveToday()) {
					this.getStatus().setContactRateAdjustment(lockdownContactRateAdjustment);
				} else {
					this.getStatus().setContactRateAdjustment(1.0D);
				}
			};
			
			
			
		} else if (this.getSimulation().getParameterisation().getControl().equals(Control.RISK_AVOIDANCE)) {
			
			double knownRisk = this.contactHistoryPositivity().probability();
			double tmpMob = this.getStatus().getContactRateAdjustment();
			
			if (this.knownTestPositiveToday()) {
				// Self isolation
				this.getStatus().setContactRateAdjustment(lockdownContactRateAdjustment);
				
			} else if (this.getStatus().getState().equals(State.RECOVERED)) {
				// If had covid then clear any mobility issues
				this.getStatus().setContactRateAdjustment(1.0D); 
			} else if (knownRisk > this.getBaseline().getHighRiskContactRateDecreaseTrigger()) {
				// decreate mobility in response to local infections
				tmpMob = Math.max(
						lockdownContactRateAdjustment,
						tmpMob*this.getBaseline().getContactRateRiskModifier()
					);
				this.getStatus().setContactRateAdjustment(tmpMob);
			} else if (knownRisk < this.getBaseline().getLowRiskContactRateIncreaseTrigger()) {
				// increase mobility in response to local infections
				tmpMob = Math.min(
						1,
						tmpMob/this.getBaseline().getContactRateRiskModifier()
					);
				this.getStatus().setContactRateAdjustment(tmpMob);
			} else {
				// no change 
			}
			
		} else {
			
			if (this.knownTestPositiveToday()) {
				this.getStatus().setContactRateAdjustment(lockdownContactRateAdjustment);
			} else {
				this.getStatus().setContactRateAdjustment(1.0D);
			}
			
		}

		
	}
	
	/**
	 * Select the most recent test result where the result is known
	 * and 
	 * @return
	 */
	public boolean knownTestPositiveToday() {
		return this.testHistory()
			.filter(t -> t.isResultCurrent(this.getSimTime(), this.getSimulation().getParameterisation().getInfectivityProfile().size() ))
			.map(t -> t.resultOnDay(this.getSimTime()))
			.filter(r -> !r.equals(Result.PENDING))
			.findFirst()
			.map(r -> r.equals(Result.POSITIVE))
			//.reduce(Boolean::logicalOr)
			.orElse(Boolean.FALSE);
	}
	
	private long infectionDuration() {
		return this.getSimulation().getParameterisation().getInfectivityProfile().size();
	}
	
	/**
	 * Select the most recent test result where the result is known
	 * and 
	 * @return
	 */
	public boolean awaitingTestOrKnownPositiveToday() {
		boolean tmp = this.testHistory()
			.filter(t -> t.isResultCurrent(this.getSimTime(), this.infectionDuration() ))
			.map(t -> t.resultOnDay(this.getSimTime()))
			.map(r -> r.equals(Result.NEGATIVE))
			// All currently are negative => TRUE; any tests positive or result is pending => FALSE; empty no tests been taken
			.reduce(Boolean::logicalAnd)
			.orElse(Boolean.TRUE)
			// All currently are negative or not tests been taken = TRUE; any tests positive or pending = FALSE
			;
		return !tmp;
	}
	
	public Binomial contactHistoryPositivity() {
		return this.cached("contactPrevalence",Binomial.class, a -> {
			Optional<Binomial> contactStatus = 
					a.getNamedListObservation(Observers.DETECTED_CONTACTS, Person.Reference.class).stream()
						.flatMap(st -> st.stream())
						.map(ar -> (Person) ar.resolve())
						.map(contact -> contact.knownTestPositiveToday() ? Binomial.of(1, 1) : Binomial.of(0,1))
						.reduce(Binomial::combine);
			return contactStatus;
		}).orElse(Binomial.of(0,0));
		
	}

	/** 
	 * Baseline contact rate (in people per day) modified by the current 
	 * rate adjustment.
	 * @return
	 */
	public double getContactRate() {
		return this.getBaseline().getContactRate() * this.getStatus().getContactRateAdjustment();
	}

	public Optional<Long> getLastInfectedTime() {
		return this.getOldStatus().map(s -> s.getLastInfected());
	}
	
	public Optional<Long> getDaysSinceLastInfection() {
		return this.getOldStatus()
				.filter(s -> s.getLastInfected() != Configuration.NA_LONG)
				.map(s -> this.getSimTime()-s.getLastInfected());
	}
	
	public Optional<Long> getDaysSinceLastTestTaken() {
		return this.getOldStatus()
				.filter(s -> s.getLastTested() != Configuration.NA_LONG)
				.map(s -> this.getSimTime()-s.getLastTested());
	}
	
	/**
	 * Describes the probability that this infectious individual transmits 
	 * to a susceptible contact within a specific time step. 
	 * @return
	 */
	public double infectiousness() {
		return this.cached(
				"infectiousness",
				Double.class,
				a -> {
					DelayDistribution tmp = this.getSimulation().getParameterisation().getInfectivityProfile();
					return a.getDaysSinceLastInfection()
						.map(i -> tmp.density(i.intValue()));
				}).orElse(0D);
	}

	/**
	 * Describes the probability that this infectious individual transmits 
	 * to a susceptible contact within a specific time step. 
	 * @return
	 */
//	public double hazardOfInfection() {
//		return this.cached(
//				"hazard",
//				Double.class,
//				a -> {
//					DelayDistribution tmp = this.getSimulation().getParameterisation().getInfectivityProfile();
//					return a.getDaysSinceLastInfection()
//						.map(i -> tmp.hazard(i.intValue()));
//				}).orElse(0D);
//	}
	
	public double cumInfectiousness() {
		return this.cached(
				"cumInfectiousness",
				Double.class,
				a -> {
					DelayDistribution tmp = this.getSimulation().getParameterisation().getInfectivityProfile();
					return a.getDaysSinceLastInfection()
						.map(i -> tmp.affected(i.intValue()));
				}).orElse(0D);
	}
	
	public double forceOfInfection() {
		return this.cached(
			"forceOfInfection", 
			Double.class,
			a -> a.getContacts().stream()
				.map(c -> c.infectiousness()*this.getStatus().getProbabilityInfectionGivenInfectiousContact())
				.reduce((x,y) -> (1-(1-x)*(1-y)))
		).orElse(0D);
	}
	
	
	
	public List<Person> getContacts() {
		
//		ClosestFirstIterator<TestAgent, TestAgent.Relationship> search = new ClosestFirstIterator<>(network, Collections.singletonList(subject),2.0);
//		Iterable<TestAgent> tmp = (() -> search); 
//		return StreamSupport.stream(tmp.spliterator(), false);
		
		// return Graphs.neighborSetOf(this.getSimulation().getContactNetwork(), this).stream();
		
		// Mobility adjusted contact network.
		
		return cachedList("contacts", Person.class, a -> {
			SimpleWeightedGraph<Person, Relationship> network = a.getSimulation().getContactNetwork();
			int connectedness = this.getSimulation().getConfiguration().getConnectedness();
			return StreamSupport.stream(network.edgesOf(a).spliterator(),false)
					// The network edge weight is the quantile of connection strength.
					// the contact rate is a people per day number.
				.filter(e -> a.getContactRate()/connectedness > e.getConnectednessQuantile())
				.map(e -> Graphs.getOppositeVertex(network, e, a))
				.collect(Collectors.toList());
		});
		
	}
	
	public List<Person> getDetectedContacts() {
		return cachedList("detectedContacts", Person.class, a -> { 
			return a.getContacts().stream()
				// Likelihood of a contact being detected is a function of the person contacted
				.filter(contact -> a.sampler().uniform() < a.getSimulation().getParameterisation().getContactRecordedProbability())
				.collect(Collectors.toList());
		});
	}
	
	
	
	public static class Relationship extends DefaultWeightedEdge implements Serializable {
		public double getConnectednessQuantile() {
			return this.getWeight();
		}
	}
	
	public static class Infection implements Serializable {
		
		private Long infectionTime;

		public Infection(Long infectionTime) {
			this.infectionTime = infectionTime;
		}
		
		public Long getInfectionTime() {
			return infectionTime;
		}
		
	}

	public boolean infectedToday() {
		return 
				this.getStatus().getState().equals(State.INFECTED) &&
				this.getDaysSinceLastInfection().orElse(0L) == 0;
	}
}
