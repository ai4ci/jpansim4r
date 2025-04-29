package io.github.ai4ci;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.lang3.SystemUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.testModel.Configuration;
import io.github.ai4ci.testModel.ControlStrategy;
import io.github.ai4ci.testModel.ExecutionConfiguration;
import io.github.ai4ci.testModel.TestingStrategy;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig.OutbreakConfigBuilder;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters.OutbreakParametersBuilder;
import io.github.ai4ci.testModel.ExecutionConfiguration.ExecutionConfigurationBuilder;
import io.github.ai4ci.testModel.TestResult.Type;

public class TestConfig {

	public static void main(String[] args) throws IOException {
		
		Path output = SystemUtils.getUserHome().toPath()
				.resolve("simulation")
				.resolve("config");
		Files.createDirectories(output);
		
		OutbreakConfigBuilder defaultConfig = Configuration.defaultConfig
				
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
				.riskTriggerMedian(0.01)
				.riskTriggerScale(0.0)
				.riskTriggerRatio(1.1)
				.riskContactModifier(0.8);
				;

		OutbreakParametersBuilder defaultParam = Configuration.defaultParameters
				.contactRecordedProbability(0.75)
				.infectivityProfile(DelayDistribution.fromCounts(0,0,1,2,2,1,1,1,1,1))
				.symptomSensitivity(0.75)
				.symptomSpecificity(0.99)
				.symptomProbabilityProfile(DelayDistribution.fromCounts(0,0,0,50,50,100,100,100,50,50,0,0,0,0))
				.lockdownContactRate(5.0)
				.probabilityScreened(0.05)
				.highPrevalenceLockdownInitiatedTrigger(0.05)
				.lowPrevalenceLockdownReleaseTrigger(0.025)
				.importRate(0.2D)
				.meanScreeningPeriod(7.0);
				 
		
		ExecutionConfigurationBuilder cfg = ExecutionConfiguration.builder();
		cfg
			.defaultConfiguration(defaultConfig.build())
			.configurationBootstraps(1)
			.configurationModifiers(Arrays.asList(
					OutbreakConfig.builder().defaultTestStrategy(TestingStrategy.PCR_ONLY).configurationName("pcr-only").build(),
					OutbreakConfig.builder().defaultTestStrategy(TestingStrategy.SCREEN_LFT).configurationName("lft-plus-pcr").build()
			))
			.defaultParameters(defaultParam.build())
			.paramterisationBootstraps(1)
			.parameterModifiers(Arrays.asList(
					OutbreakParameters.builder().control(ControlStrategy.NO_CONTROL).parameterisationName("no-control").build(),
					OutbreakParameters.builder().control(ControlStrategy.LOCKDOWN).parameterisationName("lockdown").build(),
					OutbreakParameters.builder().control(ControlStrategy.RISK_AVOIDANCE).parameterisationName("risk-avoid").build()
			))
			.executionBootstraps(1)
			.maxSimulationLength(200)
			.maxMemoryGb(24)
			.maxThreads(16)
			;
		
		ObjectMapper om = new ObjectMapper(new YAMLFactory());
		om.enable(SerializationFeature.INDENT_OUTPUT);
		om.setSerializationInclusion(Include.NON_NULL);
		Path file = output.resolve("test2.yaml");
		// ExecutionConfiguration execution;
		try {
			
			om.writeValue(file.toFile(), cfg.build());
			// execution = om.readerFor(ExecutionConfiguration.class).readValue(file.toFile());
			System.out.println("Config file written to: " + file.toString());
		} catch (IOException e) {
			System.out.println("Failed to write config: " + file.toString());
			throw new RuntimeException(e);
		}
	}
	
}
