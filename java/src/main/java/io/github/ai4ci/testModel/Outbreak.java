package io.github.ai4ci.testModel;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import io.github.ai4ci.RObserver;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.stats.Binomial;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.LockdownState;
import io.github.ai4ci.testModel.TestResult.Result;
import io.github.ai4ci.testModel.TestResult.Type;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Outbreak extends RSimulation<Outbreak,
		Configuration.OutbreakConfig,
		Configuration.OutbreakParameters,
		Person> {
	
	@Override
	public Outbreak self() {return this;}
	
	private SimpleWeightedGraph<Person, Person.Relationship> contacts;
	private DirectedAcyclicGraph<Person, Person.Infection> infections;
	
	public static RObserver.History<Outbreak, Long> SUSCEPTIBLE_COUNT = RObserver.counter(Outbreak.class, "susceptible",  a -> a.getStatus().getState().equals(State.SUSCEPTIBLE));
	public static RObserver.History<Outbreak, Long> INFECTED_COUNT = RObserver.counter(Outbreak.class, "infected",  a -> a.getStatus().getState().equals(State.INFECTED));
	public static RObserver.History<Outbreak, Long> RECOVERED_COUNT = RObserver.counter(Outbreak.class, "recovered",  a -> a.getStatus().getState().equals(State.RECOVERED));
	public static RObserver.History<Outbreak, Long> INCIDENCE_COUNT = RObserver.counter(Outbreak.class, "incidence",  a -> a.infectedToday());
	public static RObserver.History<Outbreak, Long> SYMPTOM_ONSET_COUNT = RObserver.counter(Outbreak.class, "symptomatic",  a -> a.symptomOnset());
	public static RObserver.History<Outbreak, Long> KNOWN_POSITIVE_COUNT = RObserver.counter(Outbreak.class, "known_infected",  a -> a.knownTestPositiveToday(Type.PCR));
	
	public static RObserver.History<Outbreak, Double> CONTACT_RATE_HX = RObserver.history(Outbreak.class, "contact_rates", Double.class, s -> Optional.of(s.contactRates()));
	public static RObserver.History<Outbreak, Integer> TEST_POS_HX = RObserver.history(Outbreak.class, "test_positives", Integer.class, s -> Optional.of(s.testPositivity().getNumerator()));
	public static RObserver.History<Outbreak, Integer> TEST_TAKEN_HX = RObserver.history(Outbreak.class, "test_denom", Integer.class, s -> Optional.of(s.testPositivity().getDenominator()));
	public static RObserver.History<Outbreak, Double> RT_EFFECTIVE_HX = RObserver.history(Outbreak.class, "rt", Double.class, s -> Optional.of(s.getRtEffective()));
	
	
	public enum Observations {INCIDENCE, CONTACT_RATES, TEST_POSITIVES, TESTS_PERFORMED, RT_EFFECTIVE, SYMPTOM_ONSET};
	
	
	@Override
	public void setupHistory() {
		this.registerNamedObserver(INFECTED_COUNT);
		this.registerNamedObserver(SUSCEPTIBLE_COUNT);
		this.registerNamedObserver(RECOVERED_COUNT);
		this.registerNamedObserver(INCIDENCE_COUNT);
		this.registerNamedObserver(KNOWN_POSITIVE_COUNT);
		this.registerNamedObserver(CONTACT_RATE_HX);
		this.registerNamedObserver(TEST_POS_HX);
		this.registerNamedObserver(TEST_TAKEN_HX);
		this.registerNamedObserver(RT_EFFECTIVE_HX);
		this.registerNamedObserver(SYMPTOM_ONSET_COUNT);
	}
	
	@Override
	protected boolean checkComplete() {
		return this.observations(INFECTED_COUNT).stream()
				.limit(this.getParameterisation().getInfectivityProfile().size())
				.reduce((x,y) -> x+y)
				.map(l -> l == 0)
				.orElse(Boolean.FALSE);
	}

	public Optional<Long> getSusceptibleCount() {
		return this.lastObservation(SUSCEPTIBLE_COUNT);
	}

	public Optional<Long> getInfectedCount() {
		return this.lastObservation(INFECTED_COUNT);
	}

	protected SimpleWeightedGraph<Person, Person.Relationship> getContactNetwork() {
		return contacts;
	}
	
	protected DirectedAcyclicGraph<Person, Person.Infection> getInfectionNetwork() {
		return infections;
	}
	
	// This is a forward looking R number.
	// Can do effective if we look at the edges and get a unique number of
	// incoming nodes.
	public List<Double> getRTimeseries() {
		int size = this.getSimTime().intValue()+1;
		Binomial[] ts = new Binomial[size];
		for (int i=0;i<size;i++) ts[i] = new Binomial(0,0);
		infections.vertexSet().forEach(v -> {
			Optional<Long> tmp = v.getLastInfectedTime();
			if (tmp.isPresent()) {
				int infTime = tmp.get().intValue();
				ts[infTime].update(infections.outDegreeOf(v), 1);
			}
		});
		return Stream.of(ts).map(p -> p.probability()).collect(Collectors.toList());
	}
	
	public double getRtEffective() {
		// infected today
		long numerator = this.streamAgents()
			.filter(a -> a.infectedToday())
			.count();
		// people with capability to infect today. (n.b. those infected today will
		// have zero capability)
		double denominator = this.streamAgents()
			.filter(a -> a.getStatus().getState().equals(State.INFECTED))
			.mapToDouble(x -> x.infectiousness())
			.sum() / this.getConfiguration().getTransmissionProbabilityGivenInfectiousContact(this.getParameterisation().getInfectivityProfile());
		return ((double) numerator)/denominator;
	}
	
	public Binomial testPositivity() {
		Binomial positivity = this.cached("positivity", Binomial.class, s ->
			s.streamAgents()
			.flatMap(a -> a.resultToday().stream())
			.map(r -> r.resultOnDay(s.getSimTime()).equals(Result.POSITIVE) ? Binomial.of(1, 1) : Binomial.of(0, 1))
			.reduce(Binomial::combine)
		)
		.orElse(Binomial.of(0, 0));
		return positivity;
	}
	
	public double contactRates() {
		return this.streamAgents()
			.mapToInt(a -> a.getContacts().size())
			.average().orElse(0);
	}

	public boolean isInLockDown() {
		return this.getParameterisation().getLockdownState().equals(LockdownState.LOCKED_DOWN);
		//return (this.getSimTime() > 20 && this.getSimTime() < 30);
	}
	
	public double getLockdownContactRateAdjustment() {
		return this.getParameterisation().getLockdownContactRate() / (this.getConfiguration().getConnectedness() * this.getConfiguration().getMeanContactProbability());
	}
	
	private Long getKnownInfectedCount() {
		return this.lastObservation(KNOWN_POSITIVE_COUNT).orElse(0L);
	}
	
	public void updateParameterisation() {
		
		// Binomial positivity = this.testPositivity();
//		int high = this.getParameterisation().getHighCasesLockdownInitiatedTrigger();
//		int low = this.getParameterisation().getLowCasesLockdownReleaseTrigger();
		double high = this.getParameterisation().getHighPrevalenceLockdownInitiatedTrigger() * this.getConfiguration().getPopulationSize();
		double low = this.getParameterisation().getLowPrevalenceLockdownReleaseTrigger() * this.getConfiguration().getPopulationSize();
		
		//if (positivity.probability() > high && positivity.wilson(0.95).lower() > high*0.75) {
		
		if (getKnownInfectedCount() > high) {
			
			// everyone is locked down
			this.getParameterisation().setLockdownState(LockdownState.LOCKED_DOWN);
			
		} else if (getKnownInfectedCount() < low 
				//&& positivity.wilson(0.95).upper() < low/0.75
			) {
			
			this.getParameterisation().setLockdownState(LockdownState.RELEASE);
		};
		// Otherwise it will stay as it was
		
		// Ongoing IMPORTS
		// Randomly pick a number of cases, if they are susceptible then infect them.
		int importCases = this.sampler().poisson(this.getParameterisation().getImportRate());
		for (int i=0; i<importCases; i++) {
			int id = (int) (this.sampler().uniform()*this.getConfiguration().getPopulationSize());
			if (this.getAgentById(id).getStatus().getState().equals(State.SUSCEPTIBLE)) {
				this.getAgentById(id).getStatus().setState(State.INFECTED);
				this.getAgentById(id).getStatus().setLastInfected(this.getSimTime());
			}
		}
		
	}

	public SimpleWeightedGraph<Person, Person.Relationship> getContacts() {
		return contacts;
	}

	public void setContacts(SimpleWeightedGraph<Person, Person.Relationship> contacts) {
		this.contacts = contacts;
	}

	public DirectedAcyclicGraph<Person, Person.Infection> getInfections() {
		return infections;
	}

	
	
	public void setInfections(DirectedAcyclicGraph<Person, Person.Infection> infections) {
		this.infections = infections;
	}

}
