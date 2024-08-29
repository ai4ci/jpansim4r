package io.github.ai4ci.testModel;

import java.util.Optional;

import org.apache.commons.statistics.distribution.LogNormalDistribution;

import io.github.ai4ci.RAgentBaseline;
import io.github.ai4ci.RAgentStatus;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationParameterisation;
import io.github.ai4ci.stats.Commons;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.stats.Sampler;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Configuration {

	@Value
	@Builder
	public static class OutbreakConfig implements RSimulationConfiguration {

		private String configurationName;
		private double meanContactProbability;
		private int populationSize;
		private int connectedness;
		private double R0;
		private int importedInfectionCount;
		
		/**
		 * A measure of the randomness of the small world network. This is 
		 * a range from 0 to 1 where 0 is ordered and 1 is random.
		 */
		private double networkRandomness;
		
		public double getR0PerContactPerStep() {
		// Based on the heurisitic for deciding whether on not a given contact is active per step this defines the 
		// number of contacts per step.
			double baselineContactsPerStep = meanContactProbability*connectedness;
			double pAv = Math.min(1, this.getR0()/baselineContactsPerStep);
			return pAv;
		}
		
		
	}
	
	@Data
	@Builder
	public static class OutbreakParameters implements RSimulationParameterisation {

		public static enum Control {NONE, LOCKDOWN, RISK_AVOIDANCE};
		public static enum LockdownState {LOCKED_DOWN, RELEASE};
		
		private String parameterisationName;
		/** The infectivity profile relates to the viral load of a individual
		 * and partly describes the probability that an individual will pass an 
		 * infection onto a susceptible contact on that specific day given that the 
		 * contact is infected, and assuming the contact rate is uniform. This 
		 * second assumption.
		 */
		private DelayDistribution infectivityProfile;
		private DelayDistribution testTakenProbabilityProfile;
		@Builder.Default private Control control = Control.NONE;
		@Builder.Default private LockdownState lockdownState = LockdownState.RELEASE;
		@Builder.Default private double testSensitivity = 1.0;
		@Builder.Default private double testSpecificity = 1.0;
		private double meanTestDelay;
		private double sdTestDelay;
		private double contactRecordedProbability;
		private int highCasesLockdownInitiatedTrigger;
		private int lowCasesLockdownReleaseTrigger;
		/**
		 * This is a relative mobility that is the minimum mobility that can be
		 * achieved through a lockdown.
		 */
		private double lockdownContactRate;
		
		public double getTestTakenProbability(Optional<Long> daysSinceInfection, double baseTestTakenProbability) {
			if (daysSinceInfection.isEmpty()) return baseTestTakenProbability;
			long days = daysSinceInfection.get();
			double tmp = testTakenProbabilityProfile.hazard((int) days);
			tmp = (1-(1-tmp)*(1-baseTestTakenProbability));
			return tmp;
		}

		public boolean isRecovered(Long daysSinceInfection) {
			return infectivityProfile.size() <= daysSinceInfection;
		}
	}
	
	// AGENT CONFIGURATION
	
	@Value
	@Builder
	public static class AgentBaseline implements RAgentBaseline {

		/**
		 * A positive number that defines an individuals mobility compared
		 * to the population average.
		 */
		private double contactRate;
		
		/**
		 * The value of local prevalence (i.e. prevalence in an individuals
		 * contact network) that will trigger a reduction in mobility
		 */
		private double highRiskContactRateDecreaseTrigger;
		
		/**
		 * The value of local prevalence (i.e. prevalence in an individuals
		 * contact network) that will trigger an increase in mobility
		 */
		private double lowRiskContactRateIncreaseTrigger;
		
		/**
		 * The increase or decrease of mobility risk as a ratio. This should be
		 * a number between 0 and 1. This is applied as a multiplier to an 
		 * individuals mobility adjustment at each step that the individuals 
		 * personal risk is above their personal threshold.  
		 */
		private double contactRateRiskModifier;

	}
	
	public static AgentBaseline baselineFrom(OutbreakConfig configuration, Sampler rng) {
		double trigger = rng.logitNormal(0.075, 0.1);
		return AgentBaseline.builder()
				.contactRate(
					(double) rng.binom(configuration.getConnectedness(), configuration.getMeanContactProbability())
				)
				.lowRiskContactRateIncreaseTrigger(trigger * 0.9)
				.highRiskContactRateDecreaseTrigger(trigger * 1/0.9)
				.contactRateRiskModifier(0.8)
				.build();
	}
	
	public static AgentStatus statusFrom(OutbreakConfig configuration, OutbreakParameters params, AgentBaseline baseline, Sampler rng) {
		
		return AgentStatus.builder()
				.baseProbabilityOfTesting(
					// TODO: paramaterise this?
					0.01	
					// rng.logitNormal(0.05, 0.1)
				)
				.probabilityInfectionGivenInfectiousContact(
						// The R) divided by the expected number of conacts each day mulitplied by 1/(the probability of being infected at some point
						configuration.getR0PerContactPerStep()/
						params.getInfectivityProfile().affected()
					// rng.logitNormal(pAv, 0.1)
				)
				.build();
	}
	
	@Data
	@Builder
	public static class AgentStatus implements RAgentStatus {

		public enum State {SUSCEPTIBLE, INFECTED, RECOVERED}
		
		/**
		 * The day to day mobility relative to this individual's pre-outbreak baseline.
		 * At the moment this cannot go above 1, and cannot go below the lockdown
		 * minimum.
		 */
		@Builder.Default private double contactRateAdjustment = 1.0;
		
		/**
		 * Given an infectious contact 
		 */
		double probabilityInfectionGivenInfectiousContact;
		double baseProbabilityOfTesting;
		@NonNull @Builder.Default State state = State.SUSCEPTIBLE;
		@Builder.Default long lastTested = NA_LONG;
		@Builder.Default long lastInfected = NA_LONG;
		
	}
	
	public static final long NA_LONG = Long.MIN_VALUE; 
}
