package io.github.ai4ci.stats;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class DelayDistribution implements Serializable {

	private double[] density;
	private double[] cumulative;
	private double[] hazard;
	private double sampleSize;
	
	public DelayDistribution(double[] probabilities) {
		this(probabilities, DoubleStream.of(probabilities).sum());
	}
	
	public DelayDistribution(double[] density, double sampleSize) {
		
		double tmp = DoubleStream.of(density).sum();
		this.density = DoubleStream.of(density).map(d -> d/tmp).toArray();
		this.sampleSize = sampleSize;
		
		this.cumulative = new double[density.length];
		this.hazard = new double[density.length];
		for (int i = 0; i<density.length; i++) {
			this.cumulative[i] = this.density[i] + (i == 0 ? 0 : this.cumulative[i-1]); 
			this.hazard[i] = this.density[i]/(i == 0 ? 1 : 1-this.cumulative[i-1]);
		}
	}
	
	public double density(int x) {
		if (x<0) return 0;
		if (x>=density.length) return 0;
		return density[x]*sampleSize;
	}
	
	public double cumulative(int x) {
		if (x<0) return 0;
		if (x>=cumulative.length) return 1;
		return cumulative[x]*sampleSize;
	}
	
	public double hazard(int x) {
		if (x<0) return 0;
		if (x>=hazard.length) return 0;
		return hazard[x]*sampleSize;
	}
	
	public double expected() {
		double out = 0.0D;
		for (int i = 0; i<density.length; i++) {
			out += i*density[i];
		}
		return out*sampleSize;
	}
	
	public static DelayDistribution fromProbabilities(double... probabilities) {
		return new DelayDistribution(probabilities);
	}
	
	public static DelayDistribution fromCounts(double probability, int... counts) {
		double[] probabilities = IntStream.of(counts).asDoubleStream().toArray();
		return new DelayDistribution(probabilities, probability);
	}
	
	public static DelayDistribution fromHazards(double... hazards) {
		double[] probabilities = new double[hazards.length];
		double cum = 0;
		for (int i = 0; i<hazards.length; i++) {
			if (hazards[i] > 1 || hazards[i] < 0) throw new RuntimeException("Hazards cannot be outside of the range 0 to 1"); 
			probabilities[i] = (1-cum)*hazards[i];
			cum += probabilities[i];
		}
		return new DelayDistribution(probabilities);
	}
	
	

	public long size() {
		return (long) density.length;
	}
	
	public String toString() {
		return Arrays.toString(density)+"("+this.sampleSize+")";
	}

	public double affected() {
		return affected(density.length);
	}

	public double affected(int intValue) {
		double out = 0.0D;
		for (int i = 0; i<intValue; i++) {
			out = 1-(1-out)*(1-density[i]);
		}
		return out*sampleSize;
	}
}
