package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jgrapht.generate.WattsStrogatzGraphGenerator;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import io.github.ai4ci.RObserver;
import io.github.ai4ci.RSimulation;
import io.github.ai4ci.RSimulationObserver;
import io.github.ai4ci.RSteppable;
import io.github.ai4ci.stats.Binomial;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.LockdownState;
import io.github.ai4ci.testModel.TestResult.Result;
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
	
	public enum Observations {INCIDENCE, CONTACT_RATES, TEST_POSITIVES, TESTS_PERFORMED, RT_EFFECTIVE};
	
	@Override
	protected boolean checkComplete() {
		return this.getNamedObservation(State.INFECTED, Long.class).stream()
				.limit(this.getParameterisation().getInfectivityProfile().size())
				.reduce((x,y) -> x+y)
				.map(l -> l == 0)
				.orElse(Boolean.FALSE);
	}

	private static RSimulationObserver<Outbreak, Long> inState(State state) {
		return RObserver.simulationHistory(state, Long.class,
				s -> Optional.of(
						s.streamAgents()
						.filter(a -> a.getStatus().getState().equals(state))
						.count()),
				null
			);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void setupStage1BeginConfiguration() {
		
		this.registerNamedObserver(inState(State.SUSCEPTIBLE));
		this.registerNamedObserver(inState(State.INFECTED));
		this.registerNamedObserver(inState(State.RECOVERED));
		this.registerNamedObserver(RObserver.simulationHistory(
				Observations.INCIDENCE, Long.class, 
				s -> s.streamAgents().map(a -> a.infectedToday() ? 1L : 0L).reduce((x,y) -> x+y ),
				null
		));
		this.registerNamedObserver(RObserver.simulationHistory(
				Observations.CONTACT_RATES, Double.class,
				s -> Optional.of(((Outbreak) s).contactRates()),
				null
		));
		this.registerNamedObserver(RObserver.simulationHistory(
				Observations.TEST_POSITIVES, Integer.class,
				s -> Optional.of(((Outbreak) s).testPositivity().getKey()),
				null
		));
		this.registerNamedObserver(RObserver.simulationHistory(
				Observations.TESTS_PERFORMED, Integer.class,
				s -> Optional.of(((Outbreak) s).testPositivity().getValue()),
				null
		));
		this.registerNamedObserver(RObserver.simulationHistory(
				Observations.RT_EFFECTIVE, Double.class,
				s -> Optional.of(((Outbreak) s).getRtEffective()),
				null
		));
		//		,
		
		this.contacts =  new SimpleWeightedGraph<>(
				(Serializable & Supplier<Person>) () -> new Person(this),
				(Serializable & Supplier<Person.Relationship>) () -> new Person.Relationship()
		);
		this.infections =  new DirectedAcyclicGraph<Person, Person.Infection>(
				(Serializable & Supplier<Person>) () -> new Person(this),
				(Serializable & Supplier<Person.Infection>) () -> new Person.Infection(this.getSimTime()),
				false
		);
		
	}

	@Override
	public void setupStage2CreateAgents() {
		WattsStrogatzGraphGenerator<Person, Person.Relationship> gen = 
				new WattsStrogatzGraphGenerator<Person, Person.Relationship>(
						this.getConfiguration().getPopulationSize(),
						this.getConfiguration().getConnectedness(),
						this.getConfiguration().getNetworkRandomness()
				);
		gen.generateGraph(contacts);
		log.debug("contact graph {} edges, {} average degree ", 
				contacts.iterables().edgeCount(),
				StreamSupport.stream(
							contacts.iterables().vertices().spliterator(),
							true
						).mapToInt(
							c -> contacts.degreeOf(c)
						).average().getAsDouble()
				);
		contacts.edgeSet().forEach(r -> contacts.setEdgeWeight(r, 
				this.sampler().uniform()
				));
	}
	
	public void setupStage4FinishConfiguration() {
		super.setupStage4FinishConfiguration();
		
		this.getSchedule().scheduleOnce(
				new RSteppable.UntilComplete<Outbreak>(10000) {
					@Override
					public void doStep(Outbreak s) {
						log.info("Simulation {} at step {}: {} Susceptible; {} Infected; {} Recovered; {} Incidence; {} Rt; {} positivity; {} mean contact rate",
									getUrn(),
									s.getSchedule().getSteps(),
									Outbreak.this.getLastNamedObservation(State.SUSCEPTIBLE, Long.class).orElse((long) s.getConfiguration().getPopulationSize()),
									Outbreak.this.getLastNamedObservation(State.INFECTED, Long.class).orElse(0L),
									Outbreak.this.getLastNamedObservation(State.RECOVERED, Long.class).orElse(0L),
									Outbreak.this.getLastNamedObservation(Observations.INCIDENCE, Long.class).orElse(0L),
									s.getRtEffective(),
									s.testPositivity(),
									s.contactRates()
									
							);
					}
				}
		);
	}
	
	

	@Override
	public void setupStage7FinishParameterisation() {
		super.setupStage7FinishParameterisation();
		
		for (int i=0; i<this.getConfiguration().getImportedInfectionCount(); i++) {
			int id = (int) (this.sampler().uniform()*this.getConfiguration().getPopulationSize());
			this.getAgentById(id).getStatus().setState(State.INFECTED);
			this.getAgentById(id).getStatus().setLastInfected(0);
		}
	}

	public Optional<Long> getSusceptibleCount() {
		return this.getLastNamedObservation(State.SUSCEPTIBLE.name(), Long.class);
	}

	public Optional<Long> getInfectedCount() {
		return this.getLastNamedObservation(State.INFECTED.name(), Long.class);
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
			.sum();
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
	
	public void updateParameterisation() {
		
		Binomial positivity = this.testPositivity();
		int high = this.getParameterisation().getHighCasesLockdownInitiatedTrigger();
		int low = this.getParameterisation().getLowCasesLockdownReleaseTrigger();
		
		//if (positivity.probability() > high && positivity.wilson(0.95).lower() > high*0.75) {
		
		if (positivity.getLeft() > high) {
			
			// everyone is locked down
			this.getParameterisation().setLockdownState(LockdownState.LOCKED_DOWN);
			
		} else if (positivity.getLeft() < low 
				//&& positivity.wilson(0.95).upper() < low/0.75
			) {
			
			this.getParameterisation().setLockdownState(LockdownState.RELEASE);
		};
		// Otherwise it will stay as it was
		
	}

	
	
	
	
}
