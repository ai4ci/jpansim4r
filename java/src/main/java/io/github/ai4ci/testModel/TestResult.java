package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.Optional;

import io.github.ai4ci.stats.Sampler;
import io.github.ai4ci.testModel.Configuration.TestParameters;

/**
 * Models a testing process
 */
public class TestResult implements Serializable {
	
	public static enum Type { 
		LFT (
			TestParameters.builder()
				.sensitivity(0.9)
				.specificity(0.98)
				.meanTestDelay(0.0)
				.sdTestDelay(0.0)
				.build()), 
		
		PCR (TestParameters.builder()
				.sensitivity(0.8)
				.specificity(0.995)
				.meanTestDelay(3.0)
				.sdTestDelay(1.0)
				.build()
				)
		;
		private TestParameters params;
		public TestParameters params() {return params;}
		public TestParameters.TestParametersBuilder modify() {return params.toBuilder();} 
		private Type(TestParameters params) {
			this.params = params.toBuilder().testName(this.name()).build();
		}
	}
	
	public static Optional<TestResult> resultFrom(Person testee, Type type) {
		return resultFrom(testee, type.name());
	}
	
	public static Optional<TestResult> resultFrom(Person testee, String type) {
		return testee.getSimulation().getConfiguration().getTestTypes()
				.stream().filter(tp -> tp.getTestName().equalsIgnoreCase(type)).findFirst()
				.map(params -> {
					return new TestResult(
						testee.infectiousness() > 0, // true test status as test is testing infectiousness 
						testee.getSimTime().longValue(), // test date
						(long) Math.floor(testee.sampler().logNormal(
							params.getMeanTestDelay(),
							params.getSdTestDelay()
						)), // per test test delay
						testee.sampler(), // rng
						params.getSensitivity(),
						params.getSpecificity(),
						params.getTestName()
					);
				});
	}
	
	public enum Result {PENDING,POSITIVE,NEGATIVE};
	
	private boolean infected;
	private boolean detected;
	private long time;
	private long delay;
	private String type;
	
	private TestResult(boolean infected, long time, long delay, Sampler rng, double sensitivity, double specificity, String type) {
		super();
		this.infected = infected;
		this.time = time;
		this.delay = delay;
		if (infected) {
			this.detected = rng.uniform() < sensitivity;
		} else {
			this.detected = rng.uniform() > specificity;
		}
		this.type = type;
	}
	
	public void delay(int days) {
		this.time += days;
	}
	
	public Result resultOnDay(long day) {
		if (day < time+delay) return Result.PENDING;
		if (detected) return Result.POSITIVE;
		return Result.NEGATIVE;
	}
	
	public boolean confirmedPositive(long day) {
		return (resultOnDay(day) == Result.POSITIVE);
	}
	
	public Result trueResult() {
		if (infected) return Result.POSITIVE;
		return Result.NEGATIVE;
	}
	
	public boolean isResultCurrent(long day, long recoveryTime) {
		return day >= time && day < time + recoveryTime;
	}
	
	public Optional<Result> resultAvailable(long day) {
		if (day != time+delay) return Optional.empty();
		return Optional.of(detected ? Result.POSITIVE : Result.NEGATIVE);
	}
	
	public Optional<TestResult> publishedResult(long day) {
		if (day != time+delay) return Optional.empty();
		return Optional.of(this);
	}
	
	public boolean isCanonical() {
		return !type.equals("LFT");
	}

	public boolean ofType(Type[] types) {
		for (Type type: types) {
			if (this.type.equals(type.name())) return true;
		}
		return false;
	}
}
