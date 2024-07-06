package io.github.ai4ci.testModel;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import io.github.ai4ci.RObserver;
import io.github.ai4ci.RSimulationBuilder;
import io.github.ai4ci.RSimulationRunnable;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.TestSetup.OutbreakConfig;
import io.github.ai4ci.testModel.TestSetup.OutbreakConfig.OutbreakConfigBuilder;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters.Control;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters.OutbreakParametersBuilder;

public class Test {

	public static enum Observers {SUSCEPTIBLE, MOBILITY}
	
	public static void main(String[] args) {
		
		
		
		Configurator.initialize(new DefaultConfiguration());
	    Configurator.setRootLevel(Level.DEBUG);
		
		OutbreakConfigBuilder defaultConfig = OutbreakConfig.builder()
				.configurationName("default-test")
				.populationSize(100)
				.connectedness(10)
				.meanContactProbability(0.5)
				.networkRandomness(0.2)
				.importedInfectionCount(2);
		
		OutbreakParametersBuilder defaultParam = OutbreakParameters.builder()
				.contactRecordedProbability(0.5)
				.meanTestDelay(7)
				.sdTestDelay(1)
				.infectivityProfile(DelayDistribution.fromCounts(1.0D, 0,0,1,2,2,1,1,1,1))
				.testTakenProbabilityProfile(DelayDistribution.fromCounts(0.5D, 0,0,0,0,50,50,50,50,25,25))
				.testSensitivity(0.8)
				.testSpecificity(0.99)
				.lockdownContactRate(3);
		
		Stream<RSimulationRunnable<TestOutbreak, TestAgent>> simulations = RSimulationBuilder
			.ofType(TestOutbreak.class, Optional.empty(), false)
			.withConfiguration(
				defaultConfig.build()
			)
			.withParameterisation(
				defaultParam.control(Control.NONE).parameterisationName("no-control").build(),
				defaultParam.control(Control.LOCKDOWN).parameterisationName("lockdown").build(),
				defaultParam.control(Control.RISK_AVOIDANCE).parameterisationName("risk-avoid").build()
			)
			.withObserver(
				RObserver.simulationHistory(Observers.SUSCEPTIBLE, Long.class, 
						s -> s.getSusceptibleCount(), null
				)
			)
			.withAgentObserver(
				RObserver.agentHistory(TestAgent.class, Observers.MOBILITY, Double.class,
						a -> Optional.ofNullable(a.getContactRate()), null)
			)
			.initialiseSimulation(5)
			.flatMap(c -> c.parameteriseConfigured(5))
			.map(p -> p.buildThread());
		;
		
		simulations.forEach(s -> s.run());
		
	}

}
