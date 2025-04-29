package io.github.ai4ci.testModel;

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import io.github.ai4ci.RObserved;
import io.github.ai4ci.data.Dataframe;
import io.github.ai4ci.data.RSimulationExporter;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig.OutbreakConfigBuilder;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.OutbreakParametersBuilder;
import io.github.ai4ci.testModel.TestResult.Type;

public class SingleTest {

	public static void main(String[] args) {
		
		String directory = SystemUtils.getUserHome().toPath().resolve("tmp").toString();
		
		Configurator.initialize(new DefaultConfiguration());
	    Configurator.setRootLevel(Level.DEBUG);
		
		OutbreakConfigBuilder defaultConfig = Configuration.defaultConfig
				.configurationName("default-test")
				
				// Social network configuration
				.populationSize(10000)
				.connectedness(40)
				.networkRandomness(0.25)
				
				.meanContactProbability(0.5)
				
				
				
				.R0(2.0)
				.importedInfectionCount(5)
				
				.testTypes(Arrays.asList(
						Type.PCR.params(),
						Type.LFT.params()
				))
				.defaultTestStrategy(TestingStrategy.SCREEN_LFT)
				;
		
		OutbreakParametersBuilder defaultParam = Configuration.defaultParameters
				.contactRecordedProbability(0.75)
				.infectivityProfile(DelayDistribution.fromCounts(0,0,1,2,2,1,1,1,1))
				.symptomSensitivity(0.75)
				.symptomSpecificity(0.99)
				.symptomProbabilityProfile(DelayDistribution.fromCounts(0,0,0,50,50,100,100,100,50,50,0,0,0,0))
				.lockdownContactRate(3.0)
				.probabilityScreened(0.05)
				.importRate(0.2D)
				.meanScreeningPeriod(7.0);
				
		Builder factory = new Builder();
		Outbreak outbreak = factory.buildSingleSimulation(
				defaultConfig.defaultTestStrategy(TestingStrategy.PCR_ONLY).configurationName("pcr-only").build(), 
				defaultParam.control(ControlStrategy.NO_CONTROL).parameterisationName("no-control").build(),
				0);
		
		RSimulationExporter<Outbreak,Person> exporter = null;
		try {
			 exporter = RSimulationExporter.csvFromSimulation(directory, "test.csv", 
					Dataframe.map(
							Outbreak.class,
							Dataframe.pull("time", s -> s.getHistoricalSteps()),
							RObserved.data(Outbreak.RT_EFFECTIVE_HX),
							RObserved.data(Outbreak.SUSCEPTIBLE_COUNT),
							RObserved.data(Outbreak.INCIDENCE_COUNT),
							RObserved.data(Outbreak.TEST_POS_HX),
							RObserved.data(Outbreak.TEST_TAKEN_HX)
					)	
			);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		boolean simulationCompleted = false;
		long target = 100;
		long step;
		while (!simulationCompleted) {
			
			// step the simulation:
			outbreak.getSchedule().step(outbreak);
			simulationCompleted = outbreak.isComplete();
			step = outbreak.getSchedule().getSteps();
			
			// This will export for each step
			exporter.export(outbreak);
			// TODO: could poll for input here?
			// Could wrap this as a function to run on a web service
			// event hook.
			
			if (!simulationCompleted) {
				if (target > 0 && step >= target) {
					simulationCompleted = true; 
				}
			}
			
		}
		
		exporter.close();
		
	}
	
}
