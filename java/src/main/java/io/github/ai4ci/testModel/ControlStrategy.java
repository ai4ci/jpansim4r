package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.function.Function;

import io.github.ai4ci.testModel.Configuration.AgentStatus.State;

public enum ControlStrategy implements Function<Person, Double>,Serializable {
	
	/**
	 * This strategy implements a combined LFT, plus PCR confirmation
	 * The PCR test is only performed if the LFT is positive. LFT results will
	 * typically be instant, with 1 day delay to getting a PCR. By default PCR 
	 * tests will be available after a delay.  
	 */
	NO_CONTROL ((a) -> {
		double lockdownContactRateAdjustment = a.getSimulation().getLockdownContactRateAdjustment();
		if (a.knownTestPositiveToday()) {
			return lockdownContactRateAdjustment;
		} else {
			return 1.0D;
		}
	}),
	
	/**
	 * Only do PCR tests in response to either screening requirements or
	 * symptoms. Only do one test at a time. N.B. This strategy has the potential
	 * to generate multiple tests per person per infection.
	 */
	LOCKDOWN ((a) -> {
		double lockdownContactRateAdjustment = a.getSimulation().getLockdownContactRateAdjustment();
		
		// TODO Auto-generated method stub
		if (a.getSimulation().isInLockDown()) {
			return lockdownContactRateAdjustment;
		} else {
			// Self isolation on positive test result
			if (a.knownTestPositiveToday()) {
				return lockdownContactRateAdjustment;
			} else {
				return 1.0D;
			}
		}
	}),
	
	RISK_AVOIDANCE ((a) -> {
		
		double lockdownContactRateAdjustment = a.getSimulation().getLockdownContactRateAdjustment();
		double knownRisk = a.knownContactRisk();
		double tmpMob = a.getStatus().getContactRateAdjustment();
		
		if (a.getStatus().getState().equals(State.RECOVERED)) {
			// If had covid then clear any mobility controls
			return 1.0D; 
		} else if (a.knownTestPositiveToday()) {
			// Self isolation
			return lockdownContactRateAdjustment;
			
		} else if (knownRisk > a.getBaseline().getHighRiskContactRateDecreaseTrigger()) {
			// decrease mobility in response to local infections
			return Math.max(
					lockdownContactRateAdjustment,
					tmpMob * a.getBaseline().getContactRateRiskModifier()
				);
			
		} else if (knownRisk < a.getBaseline().getLowRiskContactRateIncreaseTrigger()) {
			// increase mobility in response to local infections
			return Math.min(
					1,
					tmpMob / a.getBaseline().getContactRateRiskModifier()
				);
		} else {
			// no change 
			return tmpMob;
		}
		
	});
	
//	OLD_RISK_AVOIDANCE ((a) -> {
//		
//		double lockdownContactRateAdjustment = a.getSimulation().getLockdownContactRateAdjustment();
//		double knownRisk = a.contactHistoryPositivity().probability();
//		double tmpMob = a.getStatus().getContactRateAdjustment();
//		
//		if (a.getStatus().getState().equals(State.RECOVERED)) {
//			// If had covid then clear any mobility controls
//			return 1.0D; 
//		} else if (a.knownTestPositiveToday()) {
//			// Self isolation
//			return lockdownContactRateAdjustment;
//			
//		} else if (knownRisk > a.getBaseline().getHighRiskContactRateDecreaseTrigger()) {
//			// decrease mobility in response to local infections
//			return Math.max(
//					lockdownContactRateAdjustment,
//					tmpMob * a.getBaseline().getContactRateRiskModifier()
//				);
//			
//		} else if (knownRisk < a.getBaseline().getLowRiskContactRateIncreaseTrigger()) {
//			// increase mobility in response to local infections
//			return Math.min(
//					1,
//					tmpMob / a.getBaseline().getContactRateRiskModifier()
//				);
//		} else {
//			// no change 
//			return tmpMob;
//		}
//		
//	});
	
	
	// IMPLEMENTATION DETAILS
	
	private interface Updater extends Function<Person,Double>,Serializable {}
	private Updater update;
	private ControlStrategy(Updater update) {
		this.update = update;
	}
	
	public Double apply(Person person) {
		return update.apply(person);
	}
	

	
}