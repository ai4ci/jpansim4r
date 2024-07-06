package io.github.ai4ci.stats;

import java.io.Serializable;

import org.apache.commons.statistics.distribution.BinomialDistribution;
import org.apache.commons.statistics.distribution.PascalDistribution;
import org.apache.commons.statistics.distribution.PoissonDistribution;

import ec.util.MersenneTwisterFast;

/**
 * Extends the existing RNG to allow sampling from a normal, and log
 * normal distribution.
 */
public class Sampler implements Serializable {
	
	private MTWrapper random;
	
	public Sampler(MTWrapper random) {
		this.random = random;
	}
	
	public synchronized double uniform() {
		return random.nextDouble();
	}
	
	public synchronized double normal(double mean, double sd) {
		return random.nextGaussian()*sd+mean;
	}
	
	public synchronized double logNormal(double mean, double sd) {
//		double mu = Math.log(mean/(Math.sqrt(Math.pow(sd/mean,2)+1)));
//		double sigma = Math.sqrt(Math.log(Math.pow(sd/mean,2)+1));
//		return Math.exp(random.nextGaussian()*sigma+mu);
		return Commons.logNormalfromMeanAndSd(mean, sd).createSampler(random).sample();
		
	}
	
	public synchronized double logitNormal(double median, double scale) {
		double mu = logit(median);
		return invLogit(random.nextGaussian()*scale+mu);
	}
	
	private static double logit(double p) {
		return Math.log(p/(1-p));
	}
	
	private static double invLogit(double x) {
		return 1/(1+Math.exp(-x));
	}
	
	public synchronized int poisson(double mean) {
		return PoissonDistribution.of(mean).createSampler(random).sample();
	}
	
	public synchronized int negBinom(double mean, double sd) {
		int r = (int) Math.round((mean*mean) / (sd*sd - mean));
		double p = mean / (sd*sd);
		return PascalDistribution.of(r,p).createSampler(random).sample();
	}
	
	public synchronized int binom(int count, double probability) {
		return BinomialDistribution.of(count,probability).createSampler(random).sample();
	}
}