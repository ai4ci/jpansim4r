package io.github.ai4ci.stats;

import org.apache.commons.statistics.distribution.LogNormalDistribution;

public class Commons {
	
	public static LogNormalDistribution logNormalfromMeanAndSd(double mean, double sd) {
		double mu = Math.log(mean/(Math.sqrt(Math.pow(sd/mean,2)+1)));
		double sigma = Math.sqrt(Math.log(Math.pow(sd/mean,2)+1));
		return LogNormalDistribution.of(mu, sigma);
	}
	
	public static LogNormalDistribution productLogNormals(LogNormalDistribution d1, LogNormalDistribution d2) {
		double mu = d1.getMu() + d2.getMu();
		double sigma = Math.sqrt( Math.pow(d1.getSigma(),2) + Math.pow(d2.getSigma(),2));
		return LogNormalDistribution.of(mu, sigma);
	}
	
}
