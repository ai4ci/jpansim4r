package io.github.ai4ci.testModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import io.github.ai4ci.Bootstraps;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.flow.RSimulationConsumer;
import io.github.ai4ci.flow.RSimulationFactory;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.Configuration.AgentStatus.State;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig.OutbreakConfigBuilder;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.Control;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.OutbreakParametersBuilder;
import io.github.ai4ci.testModel.Outbreak.Observations;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.internal.operators.single.SingleFromUnsafeSource;

public class Test {

	public static enum Observers {SUSCEPTIBLE, MOBILITY}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		String directory = SystemUtils.getUserHome().toPath().resolve("tmp").toString();
		
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
				.infectivityProfile(DelayDistribution.fromCounts(1.0D, 0,0,1,2,2,1,1,1,1))
				.meanTestDelay(7)
				.sdTestDelay(1)
				.testTakenProbabilityProfile(DelayDistribution.fromProbabilities(0D,0D,0D,0D,0.5D,0.5D,0.5D,0.5D,0.25D,0.25D))
				.testSensitivity(0.8)
				.testSpecificity(0.99)
				.lockdownContactRate(3)
				.highCasesLockdownInitiatedTrigger(2000)
				.lowCasesLockdownReleaseTrigger(200)
				
			; 
		
		//Flowable.fromSingle(SingleFromUnsafeSource.fromSupplier(RObservedSimulation.uninitialised(Outbreak.class)));
		
		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
		RSimulationFactory<Outbreak, OutbreakConfig, OutbreakParameters, Person> factory = 
				RSimulationFactory
					.ofType(Outbreak.class, directory, false);
					
		
		RSimulationConsumer<Outbreak, Person> pool = 
			factory.initialise(executor)
				.attach("configure", Bootstraps.from(2 /*3*/, defaultConfig.build()), factory::configure)
				.attach("parameterise", Bootstraps.from(1 /*3*/, 
						defaultParam.control(Control.NONE).parameterisationName("no-control").build(),
						defaultParam.control(Control.LOCKDOWN).parameterisationName("lockdown").build(),
						defaultParam.control(Control.RISK_AVOIDANCE).parameterisationName("risk-avoid").build()
					), factory::parameterise)
				.attach("bootstrap 2", Arrays.asList(0 /*,1,2*/), factory::bootstrapExecutions)
				.process(directory,4)
				.withResultWriter(
					"incidence.csv", 
					Observations.INCIDENCE, Observations.CONTACT_RATES, Observations.TEST_POSITIVES, Observations.TESTS_PERFORMED, Observations.RT_EFFECTIVE,
					State.SUSCEPTIBLE, State.INFECTED, State.RECOVERED
				)
				.start();
		
		while (!pool.idle()) Thread.sleep(10000);
		System.exit(0);
		
	}

}
