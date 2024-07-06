package io.github.ai4ci;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.TestAgent;
import io.github.ai4ci.testModel.TestOutbreak;
import io.github.ai4ci.testModel.TestOutbreak.Observations;
import io.github.ai4ci.testModel.TestSetup.OutbreakConfig;
import io.github.ai4ci.testModel.TestSetup.OutbreakConfig.OutbreakConfigBuilder;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters.Control;
import io.github.ai4ci.testModel.TestSetup.OutbreakParameters.OutbreakParametersBuilder;

public class TestTestTest {
	
	@Test
	void test() {
		
		Configurator.initialize(new DefaultConfiguration());
	    Configurator.setRootLevel(Level.DEBUG);
		
		OutbreakConfigBuilder defaultConfig = OutbreakConfig.builder()
				.configurationName("default-test")
				.populationSize(100000)
				.connectedness(40)
				.meanContactProbability(0.5)
				.networkRandomness(0.25)
				.R0(2.0)
				.importedInfectionCount(30)
				;
		
		OutbreakParametersBuilder defaultParam = OutbreakParameters.builder()
				.contactRecordedProbability(0.5)
				.meanTestDelay(7)
				.sdTestDelay(1)
				.infectivityProfile(DelayDistribution.fromCounts(1.0D, 0,0,1,2,2,1,1,1,1))
				.testTakenProbabilityProfile(DelayDistribution.fromProbabilities(0D,0D,0D,0D,0.5D,0.5D,0.5D,0.5D,0.25D,0.25D))
				.testSensitivity(0.8)
				.testSpecificity(0.99)
				.lockdownContactRate(3)
				.highCasesLockdownInitiatedTrigger(2000)
				.lowCasesLockdownReleaseTrigger(200)
				
			; 
		
		Stream<RSimulationRunnable<TestOutbreak, TestAgent>> simulations = RSimulationBuilder
			.ofType(TestOutbreak.class, Optional.empty(), false)
			.withConfiguration(
				defaultConfig.build()
			)
			.withParameterisation(
				//defaultParam.control(Control.NONE).parameterisationName("no-control").build()
				defaultParam.control(Control.LOCKDOWN).parameterisationName("lockdown").build()
				//defaultParam.control(Control.RISK_AVOIDANCE).parameterisationName("risk-avoid").build()
			)
			
			.initialiseSimulation(1)
			.flatMap(c -> c.parameteriseConfigured(1))
			.map(p -> p.buildThread());
		;
		
		RSimulationRunnable<TestOutbreak, TestAgent> s = simulations.findFirst().get();
		
		s.setTarget(1000);
		s.run();
		
//		s.setTarget(2);
//		s.run();
//		
//		s.setTarget(3);
//		s.run();
//		
//		s.setTarget(12);
//		s.run();
		
		s.obsSim.getSimulation().getNamedObservation(Observations.INCIDENCE, Long.class).forEach(System.out::println);
		
		System.exit(0);
		
	}
	
	public static void main(String[] args) {
		new TestTestTest().test();
	}
}
