package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.jgrapht.generate.WattsStrogatzGraphGenerator;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.SimpleWeightedGraph;

import io.github.ai4ci.RSimulationFactory;
import io.github.ai4ci.RSteppable;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Person.Relationship;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Builder extends RSimulationFactory<Outbreak, 
		Configuration.OutbreakConfig, Configuration.OutbreakParameters, Person> { //implements Serializable {

	@Override
	public Outbreak setupStage0ConstructUnconfiguredSimulation() {
		return new Outbreak();
	}
	
	@Override
	public Outbreak setupStage1BeginConfiguration(Outbreak simulation) {
		
		simulation.setContacts(new SimpleWeightedGraph<>(
				personGenerator(simulation),
				relationshipGenerator()
		));
		
		simulation.setInfections(new DirectedAcyclicGraph<Person, Person.Infection>(
				personGenerator(simulation),
				infectionGenerator(simulation),
				false
		));
		
		return simulation;
	}

	@Override
	public Outbreak setupStage2CreateAgents(Outbreak simulation) {
		WattsStrogatzGraphGenerator<Person, Person.Relationship> gen = 
				new WattsStrogatzGraphGenerator<Person, Person.Relationship>(
						simulation.getConfiguration().getPopulationSize(),
						simulation.getConfiguration().getConnectedness(),
						simulation.getConfiguration().getNetworkRandomness()
				);
		SimpleWeightedGraph<Person, Relationship> contacts = simulation.getContacts();
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
				simulation.sampler().uniform()
				));
		return simulation;
	}

	@Override
	public Person setupStage3SetAgentBaseline(Person agent) {
		Configuration.OutbreakConfig configuration = agent.getSimulation().getConfiguration();
		agent.setBaseline(Configuration.baselineFrom(configuration, agent.sampler())); 
		return agent;
	}

	public Outbreak setupStage4FinishConfiguration(Outbreak simulation) {
		super.setupStage4FinishConfiguration(simulation);
		
		simulation.getSchedule().scheduleOnce(
				new RSteppable.UntilComplete<Outbreak>(10000) {
					@Override
					public void doStep(Outbreak s) {
						log.debug("Simulation {} at step {}: {} Susceptible; {} Infected; {} Recovered; {} Incidence; {} Rt; {} positivity; {} mean contact rate",
									s.getUrn(),
									s.getSchedule().getSteps(),
									s.lastObservation(Outbreak.SUSCEPTIBLE_COUNT).orElse((long) s.getConfiguration().getPopulationSize()),
									s.lastObservation(Outbreak.INFECTED_COUNT).orElse(0L),
									s.lastObservation(Outbreak.RECOVERED_COUNT).orElse(0L),
									s.lastObservation(Outbreak.INCIDENCE_COUNT).orElse(0L),
									s.getRtEffective(),
									s.testPositivity(),
									s.contactRates()
									
							);
					}
				}
		);
		
		return simulation;
	}
	
	@Override
	public Person setupStage6InitialiseAgentStatus(Person agent) {
		agent.setStatus(
				Configuration.statusFrom(
						agent.getSimulation().getConfiguration(), 
						agent.getSimulation().getParameterisation(), 
						agent.getBaseline(), 
						agent.sampler()
				)
		);
		return agent;
	}

	public Outbreak setupStage7FinishParameterisation(Outbreak simulation) {
		super.setupStage7FinishParameterisation(simulation);
		
		// INITIAL IMPORTS
		for (int i=0; i<simulation.getConfiguration().getImportedInfectionCount(); i++) {
			int id = (int) (simulation.sampler().uniform()*simulation.getConfiguration().getPopulationSize());
			simulation.getAgentById(id).getStatus().setState(State.INFECTED);
			simulation.getAgentById(id).getStatus().setLastInfected(0L);
		}
		
		return simulation;
	}

	@SuppressWarnings("unchecked")
	public Supplier<Person> personGenerator(Outbreak outbreak) {
		return (Supplier<Person> & Serializable) () -> new Person(outbreak);
	}
	
	@SuppressWarnings("unchecked")
	public Supplier<Person.Relationship> relationshipGenerator() {
		return (Supplier<Person.Relationship> & Serializable) () -> new Person.Relationship();
	}
	
	@SuppressWarnings("unchecked")
	public Supplier<Person.Infection> infectionGenerator(Outbreak outbreak) {
		return (Supplier<Person.Infection> & Serializable) () -> new Person.Infection(outbreak.getSimTime());
	}
	
}
