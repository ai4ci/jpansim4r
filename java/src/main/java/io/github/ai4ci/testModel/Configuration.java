package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import io.github.ai4ci.RAgentBaseline;
import io.github.ai4ci.RAgentStatus;
import io.github.ai4ci.RSimulationConfiguration;
import io.github.ai4ci.RSimulationParameterisation;
import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.stats.Sampler;
import io.github.ai4ci.testModel.TestResult.Type;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Configuration {

	public static OutbreakConfig.OutbreakConfigBuilder defaultConfig =
			OutbreakConfig
				.builder()
				.configurationName("default")
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
				.defaultTestStrategy(TestingStrategy.PCR_ONLY)
				.riskTriggerMedian(0.05)
				.riskTriggerScale(0.1)
				.riskTriggerRatio(1.2)
				.riskContactModifier(0.8);
	
	@Data
	@Builder(toBuilder = true)
	@Jacksonized
	public static class OutbreakConfig implements RSimulationConfiguration {

		/**
		 * id for this configuration
		 */
		private String configurationName;
		
		/**
		 * The proportion of a persons social network that they see each day
		 * in a fully mobile population. This plus the number of connections is 
		 * used to define the baseline number of contacts each person makes each day
		 * {@link AgentBaseline#contactRate}
		 */
		private Double meanContactProbability;
		
		
		
		
		
		private List<TestParameters> testTypes;
		private TestingStrategy defaultTestStrategy;
		
		/**
		 * network size
		 */
		private Integer populationSize;
		/**
		 * Number of connections each person makes with others in the social network
		 */
		private Integer connectedness;
		private Double R0;
		
		/**
		 * The initial size of the imported cases. 
		 */
		private Integer importedInfectionCount;
		
		
		
		/**
		 * A measure of the randomness of the small world social network. This is 
		 * a range from 0 to 1 where 0 is ordered and 1 is totally random.
		 */
		private Double networkRandomness;
		
		/**
		 * The risk trigger is the risk value at which an individuals behaviour 
		 * changes. It is assigned randomly to populations at configuration time
		 * and this is the median value of a logit. 
		 */
		private Double riskTriggerMedian;
		
		/**
		 * The risk trigger is the risk value at which an individuals behaviour 
		 * changes. It is assigned randomly to populations at configuration time
		 * and this is the scale of a logit. 
		 */
		private Double riskTriggerScale;
		
		/**
		 * The risk trigger ratio is the width from low risk relaxation and 
		 * hig risk increase. This is a so if the low value is 0.1 then a ratio of 
		 * 2 would make the high value 0.2. 
		 */
		private Double riskTriggerRatio;
		
		/**
		 * How much does being high risk modify the contact rate of the individual
		 * TODO: consider also transmission risk
		 */
		private Double riskContactModifier;
		
		/**
		 * A probability of infection given an infectious contact derived from the expected
		 * number of contacts each person makes in the network in the fully 
		 * mixing scenario and the baseline R0. This exists because R0 is a 
		 * odd concept which reflects transmission potential and initial mixing conditions
		 * @param infectivityProfile a delay distribution defining relative probability 
		 * per day.
		 * @return a probability of transmission given an infectious contact
		 */
		public double getTransmissionProbabilityGivenInfectiousContact(DelayDistribution infectivityProfile) {
			// The expected number of contacts per person per step over the whole population.
			double baselineContactsPerStep = meanContactProbability*connectedness;
			// Each contact is an opportunity for transmission but depends on 
			// day of infection, and the relative infectivity on that day. Assuming
			// this contact rate is the same per day over the whole infective
			// period then this value is weighted by the relative probability of 
			// transmission per day summed over all days which is 
			// equivalent to the total affected  = 1-(survival without transmission).
			// N.B. this is slightly due to the fact that the infectivityProfile
			// is defined as a probability distribution of day of infection given 
			// infection but it is necessarily applied as a daily probability of infection
			// given infectious contact on that day and these two things are not
			// equal.
			double transmissionOpportunities = baselineContactsPerStep * infectivityProfile.affected();
			// This is the total opportunitied for transmission. Probability is
			// R0 divided by total opportunities. If there are not enough transmission opportunities
			// then high R0 values are impossible. The network cannot sustain them
			// hence capping the transission probability at 1.
			double pAv = Math.min(1, this.getR0()/transmissionOpportunities);
			return pAv;
		}
		
		
	}
	
	public static OutbreakParameters.OutbreakParametersBuilder defaultParameters = OutbreakParameters
			.builder()
			.parameterisationName("default")
			.control(ControlStrategy.NO_CONTROL)
			.lockdownState(OutbreakParameters.LockdownState.RELEASE)
			.contactRecordedProbability(0.75)
			.infectivityProfile(DelayDistribution.fromCounts(0,0,1,2,2,1,1,1,1))
			.symptomSensitivity(0.75)
			.symptomSpecificity(0.99)
			.probabilityScreened(0.05)
			.meanScreeningPeriod(7.0)
			.importRate(0.2D)
			
			.symptomProbabilityProfile(DelayDistribution.fromCounts(0,0,0,50,50,100,100,100,50,50,0,0,0,0))
			.lockdownContactRate(3.0)
//			.highCasesLockdownInitiatedTrigger(200)
//			.lowCasesLockdownReleaseTrigger(50);
			.highPrevalenceLockdownInitiatedTrigger(0.02)
			.lowPrevalenceLockdownReleaseTrigger(0.005);
	
	@Data
	@Builder(toBuilder = true)
	@Jacksonized
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
		
		/** the symptom probability profile is the 
		 * likelihood that an infected person will experience symptoms on X days
		 * after infection given they experience symptoms.
		 */
		private DelayDistribution symptomProbabilityProfile;
		
		/**
		 * The symptom specifcity is the 1-average likelihood that an uninfected individual
		 * will experience symptoms.
		 */
		private Double symptomSpecificity;
		
		/**
		 * The symptom sensitivity is the average likelihood that an individual
		 * will experience symptoms given their infection status.
		 */
		private Double symptomSensitivity;
		
		private ControlStrategy control;
		private LockdownState lockdownState;
		
		/**
		 * How likely is a person to be part of the screening programme.
		 */
		private Double probabilityScreened;
		/**
		 * How often in days is a person tested if they are screened.
		 */
		private Double meanScreeningPeriod;
		/**
		 * The ongoing importation rate as a imported cases per step. 
		 */
		private Double importRate;
		
		
		private Double contactRecordedProbability;
//		private Integer highCasesLockdownInitiatedTrigger;
//		private Integer lowCasesLockdownReleaseTrigger;
		private Double highPrevalenceLockdownInitiatedTrigger;
		private Double lowPrevalenceLockdownReleaseTrigger;
		/**
		 * This is a relative mobility that is the minimum mobility that can be
		 * achieved through a lockdown.
		 */
		private Double lockdownContactRate;
		
		

		public boolean isRecovered(Long daysSinceInfection) {
			return infectivityProfile.size() <= daysSinceInfection;
		}
	}
	
	// AGENT CONFIGURATION
	
	@Value
	@Builder(toBuilder = true)
	@Jacksonized
	public static class AgentBaseline implements RAgentBaseline {

		/**
		 * A positive number that defines an individuals mobility compared
		 * to the population average.
		 */
		private Double contactRate;
		
		/**
		 * The value of local prevalence (i.e. prevalence in an individuals
		 * contact network) that will trigger a reduction in mobility
		 */
		private Double highRiskContactRateDecreaseTrigger;
		
		/**
		 * The value of local prevalence (i.e. prevalence in an individuals
		 * contact network) that will trigger an increase in mobility
		 */
		private Double lowRiskContactRateIncreaseTrigger;
		
		/**
		 * The increase or decrease of mobility risk as a ratio. This should be
		 * a number between 0 and 1. This is applied as a multiplier to an 
		 * individuals mobility adjustment at each step that the individuals 
		 * personal risk is above their personal threshold.  
		 */
		private Double contactRateRiskModifier;

	}
	
	public static long NA_LONG = -1L;
	
	@Data
	@Builder(toBuilder = true)
	@Jacksonized
	public static class AgentStatus implements RAgentStatus {

		public enum State {SUSCEPTIBLE, INFECTED, RECOVERED}
		public enum Symptoms {NONE, PRESENT}
		
		/**
		 * The day to day mobility relative to this individual's pre-outbreak baseline.
		 * At the moment this cannot go above 1, and cannot go below the lockdown
		 * minimum.
		 */
		@Builder.Default private Double contactRateAdjustment = 1.0;
		
		/**
		 * Given an infectious contact how likely is infection. This is set by
		 * the agent configuration such that the overall R0 is whatever it is
		 * set to in the context of the current network. Essentially though this
		 * is a susceptibility and could change over time.
		 */
		private double probabilityInfectionGivenInfectiousContact;
		
		@Builder.Default @NonNull private TestingStrategy testStrategy = TestingStrategy.SCREEN_LFT;
		@NonNull private Integer screeningPeriod;
		@Builder.Default private boolean isScreened = false;
		
		@NonNull @Builder.Default private State state = State.SUSCEPTIBLE;
		@NonNull @Builder.Default private Symptoms symptoms = Symptoms.NONE;
		@Builder.Default private long lastTested = NA_LONG;
		@Builder.Default private long lastInfected = NA_LONG;
		@Builder.Default private double infectionRisk = 0;
		@Builder.Default private double knownInfectionRisk = 0;
		
	}
	
	@Data
	@Builder(toBuilder = true)
	@Jacksonized
	public static class TestParameters implements Serializable {
		
		/**
		 * Identifier for the test type e.g. LFT, PCR, SYMPTOMS
		 */
		private String testName;
		private Double sensitivity;
		private Double specificity;
		/**
		 * The average delay in the result becoming available (days)
		 */
		private Double meanTestDelay;
		/**
		 * The SD of the delay in the result. This will be a log normal
		 */
		private Double sdTestDelay;
	}
	
	public static AgentBaseline baselineFrom(OutbreakConfig configuration, Sampler rng) {
		double trigger = rng.logitNormal(configuration.getRiskTriggerMedian(), configuration.getRiskTriggerScale());
		return AgentBaseline.builder()
				.contactRate(
					(double) rng.binom(configuration.getConnectedness(), configuration.getMeanContactProbability())
				)
				.lowRiskContactRateIncreaseTrigger(trigger * 1/Math.sqrt(configuration.getRiskTriggerRatio()))
				.highRiskContactRateDecreaseTrigger(trigger * Math.sqrt(configuration.getRiskTriggerRatio()))
				.contactRateRiskModifier(configuration.getRiskContactModifier())
				.build();
	}
	
	public static AgentStatus statusFrom(OutbreakConfig configuration, OutbreakParameters params, AgentBaseline baseline, Sampler rng) {
		
		return AgentStatus.builder()
				.testStrategy(configuration.getDefaultTestStrategy())
				.screeningPeriod(rng.poisson(params.getMeanScreeningPeriod()))
				.isScreened(rng.uniform() > params.getProbabilityScreened())
				.probabilityInfectionGivenInfectiousContact(
					// The R) divided by the expected number of contacts each day mulitplied by 1/(the probability of being infected at some point
					configuration.getTransmissionProbabilityGivenInfectiousContact(params.getInfectivityProfile())
				)
				.build();
	}
}
