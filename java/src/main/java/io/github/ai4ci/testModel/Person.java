package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.SerializationUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import bsh.This;
import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObserver;
import io.github.ai4ci.stats.Binomial;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Configuration.AgentStatus.Symptoms;
import io.github.ai4ci.testModel.TestResult.Result;
import io.github.ai4ci.testModel.TestResult.Type;

public class Person extends RAgent<Person,Outbreak,Configuration.AgentBaseline,Configuration.AgentStatus> {

	// public enum Observers {  TESTS, DETECTED_CONTACTS, KNOWN_RECOVERD, SYMPTOMATIC };

	public static RObserver.OfLists<Person, TestResult> TESTS = RObserver.listHistory(Person.class, "test",  TestResult.class, 
			a -> a.testToday(), 14);
	
	public static RObserver.OfLists<Person, Person.Reference> DETECTED_CONTACTS = RObserver.listHistory(Person.class, "detected_contacts",  Person.Reference.class, 
			a -> a.getDetectedContacts().stream().map(a2 -> a2.weakReference()).collect(Collectors.toList()), 14);
	
	public static RObserver.Last<Person, Boolean> KNOWN_RECOVERED = RObserver.lastValue(Person.class, "known_recovered",  Boolean.class, 
			a -> Optional.of(a.knownRecovered()));
	
	public static RObserver.History<Person,Configuration.AgentStatus> PCR_POSITIVES = RObserver.history(Person.class,"pcr_positives",Configuration.AgentStatus.class,
			p -> Optional.ofNullable(p.knownTestPositiveToday(Type.PCR) ? SerializationUtils.clone(p.getStatus()) : null)
		);
	
	public static RObserver.History<Person,Configuration.AgentStatus> TEST_AGENT = RObserver.history(Person.class,"test_agent",Configuration.AgentStatus.class,
			p -> Optional.ofNullable(p.getId() == 1 ? SerializationUtils.clone(p.getStatus()) : null)
		);
	
	@Override
	public void setupHistory() {
		// keep last 4 test results
		this.registerNamedObserver(TESTS);
		this.registerNamedObserver(DETECTED_CONTACTS);
		this.registerNamedObserver(KNOWN_RECOVERED);
		this.registerNamedObserver(PCR_POSITIVES);
		this.registerNamedObserver(TEST_AGENT);
	}
	
	
	public Person(Outbreak simulation) {
		super(simulation);
	}
	
	public Stream<TestResult> testHistory() {
		return this.observations(TESTS).stream().flatMap(l -> l.stream());
	}
	
	public Stream<TestResult> testHistoryIncludingToday() {
		return Stream.concat(
				this.testHistory(),
				this.testToday().stream()
		);
	}
	
	@Override
	public boolean remainsActive() {
		// return this.getLastInfectedTime().orElse(Long.MAX_VALUE) + this.infectionDuration() > this.getSimTime();
		return !this.getSimulation().isComplete();
		// this.getOldStatus().map(o -> !o.getState().equals(State.RECOVERED)).orElse(Boolean.TRUE);
	}

	@Override
	public Person self() {return this;}

//	public State yesterdayState() {
//		return this.getOldStatus().map(s -> s.getState())
//			.orElse(State.SUSCEPTIBLE);
//	}
//	
//	public boolean infectedToday() {
//		return this.yesterdayState().equals(State.SUSCEPTIBLE) &&
//				this.getStatus().getState().equals(State.INFECTED);
//	}
	
	/** 
	 * Test results that have been performed today
	 * @return a list of test results which will probably be pending status
	 * unless there is an immediate result
	 */
	public List<TestResult> testToday() {
		return this.cachedList("testToday", TestResult.class, 
			a -> a.getStatus().getTestStrategy().apply(a)
		);
	}
	
	/**
	 * tests that have had a result today, but would have been taken in the past
	 * @return a list of test results none of which will be pending
	 */
	public List<TestResult> resultToday() {
		return 
			this.cachedList("results", TestResult.class, 
					a -> a
						.testHistoryIncludingToday()
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
					.filter(a -> this.sampler().uniform() < a.infectiousness() )
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
		// Calling this method makes sure that testing is done if not already
		// cached
		if (this.testToday().stream().findAny().isPresent()) {
			this.getStatus().setLastTested(this.getSimTime());
		}
		
		// Update symptomatic status
		if (this.symptomatic()) {
			this.getStatus().setSymptoms(Symptoms.PRESENT);
		} else {
			this.getStatus().setSymptoms(Symptoms.NONE);
		}
		
		// TODO: change probabilityOfInfectionGivenInfectiousContact 
		// to make a model with immunity in it. This would supplant susceptible
		// recovered status, and make that a continuous variable. We could do this
		// for another model.
		
		// Update infection risk / percievedInfectionRisk
		if (this.getStatus().getState().equals(State.RECOVERED)) {
			this.getStatus().setInfectionRisk(0);
		} else if (this.getStatus().getState().equals(State.INFECTED)) {
			this.getStatus().setInfectionRisk(1);
		} else {
			this.getStatus().setInfectionRisk(this.forceOfInfection());
		}
		
		if (this.knownRecovered()) {
			this.getStatus().setKnownInfectionRisk(0);
		} else if (this.knownTestPositiveToday(Type.values())) {
			this.getStatus().setKnownInfectionRisk(
					this.infectiousness()
			);
		} else {
			this.getStatus().setKnownInfectionRisk(this.knownContactRisk());
		}
	}

	@Override
	public void changeBehaviour() {
		
		double newAdj = this.getSimulation().getParameterisation().getControl().apply(this);
		this.getStatus().setContactRateAdjustment(newAdj);

		
	}
	
	/**
	 * Test positive if any tests are positive taken in the infectious period.
	 * @return
	 */
	public boolean knownTestPositiveToday(Type... types) {
		return 
				this.cached("test-pos-today", Boolean.TYPE, p -> p.testHistory()
					.filter(t -> t.ofType(types))
					.filter(t -> t.isResultCurrent(this.getSimTime(), this.infectionDuration() ))
					.map(t -> t.resultOnDay(this.getSimTime()))
					.filter(r -> !r.equals(Result.PENDING))
					//.findFirst()
					.map(r -> r.equals(Result.POSITIVE))
					.reduce(Boolean::logicalOr)
				).orElse(Boolean.FALSE);
	}
	
	/**
	 * A person is recovered if and only if a recent positive test is longer
	 * ago than the infection duration. This does not allow waning.
	 * @return
	 */
	public boolean knownRecovered() {
		Optional<Boolean> tmp = this.lastObservation(Person.KNOWN_RECOVERED);
		if (tmp.isPresent() && tmp.get().equals(Boolean.TRUE)) return true;
		return this.testHistory()
			// Only consider PCR tests
			//.filter(t -> t.isCanonical())
			.filter(t -> t.resultOnDay(this.getSimTime()).equals(Result.POSITIVE))
			.map(t -> t.isResultCurrent(this.getSimTime(), this.infectionDuration()))
			// a FALSE value here means that the positive test result is no longer current.
			// we only need to prove that one positive value is no longer current 
			.reduce((x,y) -> Boolean.logicalAnd(x, y))
			// a FALSE value here means that at least 1 positive test is no longer current
			.map(x -> !x.booleanValue())
			// a TRUE value here means that at least 1 positive test is no longer current
			// this is our definition.
			.orElse(Boolean.FALSE);
	}
	
	private long infectionDuration() {
		return this.getSimulation().getParameterisation().getInfectivityProfile().size();
	}
	
	private long symptomDuration() {
		return this.getSimulation().getParameterisation().getSymptomProbabilityProfile().size();
	}
	
	/**
	 * Select the most recent test result where the result is known
	 * and 
	 * @return
	 */
	public boolean awaitingTestOrKnownPositiveToday() {
		boolean tmp = this.cached("pending-tests-today", Boolean.TYPE, p -> p.testHistory()
				.filter(t -> t.isResultCurrent(this.getSimTime(), this.infectionDuration() ))
				.map(t -> t.resultOnDay(this.getSimTime()))
				.map(r -> r.equals(Result.NEGATIVE))
				// All currently are negative => TRUE; any tests positive or result is pending => FALSE; empty no tests been taken
				.reduce(Boolean::logicalAnd)
			).orElse(Boolean.TRUE)
			// All currently are negative or not tests been taken = TRUE; any tests positive or pending = FALSE
			;
		return !tmp;
	}
	
	/**
	 * The number of detected contacts that are known to have tested positive.
	 * This handles cases where a test result is pending. 
	 * @return a Binomial 
	 */
	public Binomial contactHistoryPositivity() {
		return this.cached("contactPrevalence",Binomial.class, a -> {
			Optional<Binomial> contactStatus = 
					a.observations(Person.DETECTED_CONTACTS).stream()
						.flatMap(st -> st.stream())
						.map(ar -> ar.resolve(Person.class))
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
	
	public boolean isInfected() {
		return this.getStatus().getState() == State.INFECTED;
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
					DelayDistribution tmp = this.getSimulation().getParameterisation().getInfectivityProfile()
							.conditionedOn(this.getStatus().getProbabilityInfectionGivenInfectiousContact());
					
					return a.getDaysSinceLastInfection()
						.map(i -> tmp.density(i.intValue()));
				}).orElse(0D);
	}
	
	public boolean symptomatic() {
		return this.cached(
				"symptomatic",
				Boolean.class,
				a -> {
					
					
					boolean recentlyInfected = 
							// Yesterdays status was infected
							// a.getOldStatus().map(s->s.getState()).map(s -> s.equals(State.INFECTED)).orElse(Boolean.FALSE) ||
							a.getDaysSinceLastInfection().map(t -> t < symptomDuration()).orElse(Boolean.FALSE);
					boolean currentlySymptomatic =
							a.getOldStatus().map(s->s.getSymptoms()).map(s -> s.equals(Symptoms.PRESENT)).orElse(Boolean.FALSE); 
					
					// Person was not recently infected hence symptoms are false
					// positives and determined by specificity. This condition
					// will on the whole clear symptomatic status
					if (!recentlyInfected) {
						// There is no recent infection 
						// The symptom rates depend on symptom specificity and these
						// are false positive symptoms
						return Optional.of(a.sampler().uniform() < 1-a.getSimulation().getParameterisation().getSymptomSpecificity());
					}
					
					// Person could still be exhibiting symptoms due to infection
					// or infectious and not yet exhibiting symptoms.
					if (!currentlySymptomatic) {
						// Person does have infection but does not already have symptoms
						// Onset of symptoms determined by delay distribution hazard
						DelayDistribution prob = a.getSimulation().getParameterisation().getSymptomProbabilityProfile();
						DelayDistribution probSymptoms = prob.conditionedOn(
								a.getSimulation().getParameterisation().getSymptomSensitivity()
						);
						// This test is only performed if symptoms have been absent so far. 
						// Therefore it is a survival type probability and we
						// are interested in the unconditional hazard of 
						// symptoms developing on this day. This takes
						// into account the probability that no symptoms appear
						long days = a.getDaysSinceLastInfection().get();
						double todayP = probSymptoms.hazard((int) days);
						return Optional.of(a.sampler().uniform() < todayP); 
					} else {
						// Infection is recent and Symptoms are already present. This is result of 
						// previous steps. Have symptoms resolved?
						// We say symptoms will have resolved if the infection
						// is long enough ago that there is no probability of
						// symptoms in which case the "not recently infected" rule
						// above applies and the condition is already dealt with
						// The only remaining case is always continues to be symptomatic
						return Optional.of(Boolean.TRUE);
					}
					
				}).get();
	}
	
	public boolean symptomOnset() {
		return this.getOldStatus().map(s -> s.getSymptoms().equals(Symptoms.NONE)).orElse(Boolean.FALSE) &&
				this.symptomatic();
	}
	
	public boolean screenToday() {
		return this.getStatus().isScreened() &&
				(this.getDaysSinceLastTestTaken()
					.orElse(0L) > this.getStatus().getScreeningPeriod());
	}

//	public double cumInfectiousness() {
//		return this.cached(
//				"cumInfectiousness",
//				Double.class,
//				a -> {
//					DelayDistribution tmp = this.getSimulation().getParameterisation().getInfectivityProfile();
//					return a.getDaysSinceLastInfection()
//						.map(i -> tmp.affected(i.intValue()));
//				}).orElse(0D);
//	}
	
	public double forceOfInfection() {
		return this.cached(
			"forceOfInfection", 
			Double.class,
			a -> a.getContacts().stream()
				.map(c -> c.infectiousness())
				.reduce((x,y) -> (1-(1-x)*(1-y)))
		).orElse(0D);
	}
	
	
	public double knownContactRisk() {
		return this.cached(
			"knownForceOfInfection", 
			Double.class,
			a -> a.getDetectedContacts().stream()
				.map(c -> c.getOldStatus().map(s -> s.getKnownInfectionRisk()).orElse(0D))
				.reduce((x,y) -> (1-(1-x)*(1-y)))
				//TODO: somehow we need to map this back to time since contact?
				.map(d -> d * a.getStatus().getProbabilityInfectionGivenInfectiousContact())
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
				.filter(e -> a.getContactRate() > 
					this.sampler().binom(connectedness, e.getConnectednessQuantile()))
				// .map(e -> Graphs.getOppositeVertex(network, e, a))
				.flatMap(e -> { 
					Person contact = Graphs.getOppositeVertex(network, e, a);
					if (
						// Check the 
						contact.getContactRate()  > this.sampler().binom(connectedness, e.getConnectednessQuantile())
					) {
						return Stream.of(contact);
					} else {
						return Stream.empty();
					}
					
				})
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
