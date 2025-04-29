package io.github.ai4ci.stats;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.commons.math3.analysis.solvers.BrentSolver;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * The delay distribution is a probability distribution in time
 * conditional on an event happening. It is useful to generate a per
 * day conditional hazard.
 * 
 * Relationships are discrete versions of https://grodri.github.io/glms/notes/c7s1
 */
public class DelayDistribution implements Serializable {

	/**
	 * The probability of being affected at time infinity (1-survival_Inf)  
	 */
	private double pAffected;
	
	/**
	 * the improper probability density - which will sum to pAffected
	 */
	private double[] density;
	
	/**
	 * the conditional probability density - which will sum to 1
	 */
	private double[] condDensity;
	
	
	/** 
	 * the unconditional survival function 
	 */
	private double[] survival;
	

	/** 
	 * the conditional survival function 
	 */
	//private double[] condSurvival;
	
	/** 
	 * the unconditional hazard function 
	 */
	private double[] hazard;
	
	/** 
	 * the conditional hazard function 
	 */
	//private double[] condHazard;
	
	/**
	 * 
	 * @param density
	 * @param pAffected
	 */
	
	@JsonCreator
	public DelayDistribution(@JsonProperty("density") double[] density, @JsonProperty("pAffected")  double pAffected) {
		
		double tmp = DoubleStream.of(density).sum();
		this.condDensity = DoubleStream.of(density).map(d -> d/tmp).toArray();
		
		if (tmp<1) {
			log.warn("delay distribution created with density function < 1; updating affected fraction");
			pAffected = pAffected*tmp;
		} else if (tmp>1 ){
			log.debug("delay distribution with density > 1 being normalised");
		}
		this.pAffected = pAffected;
		//double survInf = 1-pAffected;
		
		this.density = new double[density.length];
		this.hazard = new double[density.length];
		this.survival = new double[density.length];
		//this.condHazard = new double[density.length];
		//this.condSurvival = new double[density.length];
		
		for (int i = 0; i<density.length; i++) {
			this.density[i] = this.condDensity[i] * pAffected;
			this.survival[i] = (i == 0 ? 1 : this.survival[i-1]) - this.density[i];
			this.hazard[i] = this.density[i] / (i == 0 ? 1 : this.survival[i-1]);
			//this.condSurvival[i] = (this.survival[i] - survInf)/(1-survInf);
			//this.condHazard[i] = this.condDensity[i] / (i == 0 ? 1 : this.condSurvival[i-1]);
		}
	}
	
	public double getpAffected() {
		return pAffected;
	}

	public double[] getDensity() {
		return density;
	}

	public DelayDistribution conditionedOn(double pAffected) {
		return new DelayDistribution(condDensity, pAffected);
	}
	
	public double density(int x) {
		if (x<0) return 0;
		if (x>=density.length) return 0;
		return density[x];
	}
	
	public double cumulative(int x) {
		if (x<0) return 0;
		if (x>=survival.length) return 1;
		return 1-survival[x];
	}
	
	public double hazard(int x) {
		if (x<0) return 0;
		if (x>=hazard.length) return 0;
		return hazard[x];
	}
	
	public double expected() {
		return expected(1);
	}
	
	public double expected(double sampleSize) {
		double out = 0.0D;
		for (int i = 0; i<density.length; i++) {
			out += i*density[i];
		}
		return out*sampleSize;
	}
	
	public static DelayDistribution fromPDF(double... probabilities) {
		double conditionalProbability = DoubleStream.of(probabilities).sum();
		return new DelayDistribution(
				DoubleStream.of(probabilities).map(p -> p/conditionalProbability).toArray(), 
				conditionalProbability);
	}
	
	public static DelayDistribution fromCDF(double... cumulative) {
		double density[] = new double[cumulative.length];
		for (int i=0; i<cumulative.length; i++) {
			density[i] = cumulative[i] - (i>0 ? cumulative[i-1] : 0); 
		}
		return fromPDF(density);
	}
	
	
	
	public static DelayDistribution fromCounts(int... counts) {
		return fromCounts(1, counts);
	}
	
//	public static DelayDistribution fromCounts(int exposed, int... counts) {
//		double[] probabilities = IntStream.of(counts).asDoubleStream().map(d -> d/exposed).toArray();
//		return fromPDF(probabilities);
//	}
	
	public static DelayDistribution fromCounts(double pAffected, int... counts) {
		int exposed = IntStream.of(counts).sum();
		double[] probabilities = IntStream.of(counts).asDoubleStream().map(d -> d/exposed).toArray();
		return new DelayDistribution(probabilities,pAffected);
	}
	
	public long size() {
		return (long) density.length;
	}
	
	public String toString() {
		return "P("+Arrays.toString(density)+"|"+pAffected+")";
	}

	public double affected() {
		return pAffected;
	}

	/**
	 * What proportion of individuals expected to have been affected by
	 * day X. (i.e. 1-prob(survived to day X)) 
	 * @param intValue
	 * @return
	 */
	public double affected(int intValue) {
		return 1-this.survival[intValue];
	}
}
