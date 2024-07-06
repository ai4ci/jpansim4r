package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.Optional;

import io.github.ai4ci.stats.DelayDistribution;
import io.github.ai4ci.stats.Sampler;

/**
 * Models a testing process
 */
public class TestTest implements Serializable {
	
	public enum Result {PENDING,POSITIVE,NEGATIVE};
	
	private boolean infected;
	private boolean detected;
	private long time;
	private long delay;
	
	public TestTest(boolean infected, long time, long delay, Sampler rng, double sensitivity, double specificity) {
		super();
		this.infected = infected;
		this.time = time;
		this.delay = delay;
		if (infected) {
			this.detected = rng.uniform() < sensitivity;
		} else {
			this.detected = rng.uniform() > specificity;
		}
	}
	
	public Result resultOnDay(long day) {
		if (day > time+delay) return Result.PENDING;
		if (detected) return Result.POSITIVE;
		return Result.NEGATIVE;
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
	
	public Optional<TestTest> publishedResult(long day) {
		if (day != time+delay) return Optional.empty();
		return Optional.of(this);
	}
}
