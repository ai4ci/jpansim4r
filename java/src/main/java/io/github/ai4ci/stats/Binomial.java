package io.github.ai4ci.stats;

import java.util.stream.Collector;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.statistics.distribution.NormalDistribution;

public class Binomial extends MutablePair<Integer,Integer> {
	
	public static class Confidence extends ImmutablePair<Double, Double> {

		public Confidence(Double left, Double right) {
			super(left, right);
		}
		
		public String toString() {
			return String.format("[%.1f - %.1f]", left*100, right*100);
		}
		
		public double lower() {
			return left;
		}
		
		public double upper() {
			return right;
		}
		
	}
	
	private Binomial() {
		super(0, 0);
	}
	
	public Binomial(int num, int denom) {
		super(num,denom);
	}
	
	public static Binomial of(int num, int denom) {
		//TODO check denom bigger that num
		return new Binomial(num,denom);
	}
	
	public static Collector<Binomial, ?, Binomial> collect() {
		return Collector.of(Binomial::new, (p1,p2) -> p1.update(p2), Binomial::combine);
	}
	
	public static Collector<Boolean, ?, Binomial> collectBinary() {
		return Collector.of(Binomial::new, (p,b) -> p.update(b ? 1 : 0, 1), Binomial::combine);
	}
	
	public static Binomial combine(Binomial left, Binomial right) {
		return Binomial.of(
				left.getLeft()+right.getLeft(),
				left.getRight()+right.getRight()
			);
	}

	public double probability() {
		if (this.right == 0) return(0);
		return ((double) this.left)/((double) this.right);
	}
	
	public double ratio() {
		if ((this.right-this.left) == 0) return 0;
		return ((double) this.left)/(this.right-this.left);
	}
	
	public void update(Binomial toAdd) {
		update(toAdd.getLeft(), toAdd.getRight());
	}
	
	public void update(int num, int denom) {
		this.left += num;
		this.right += denom;
	}
	
	public Confidence wilson(double interval) {
		double z = NormalDistribution.of(0, 1).inverseCumulativeProbability(1-interval/2);
		double p = probability();
		int n = right;
		double tmp = z/(2*n)*Math.sqrt(4*n*p*(1-p)+z*z);
		double tmp2 = p+(z*z)/(2*n);
		double tmp3 = 1/(1+(z*z)/n);
		return new Confidence(
				tmp3*(tmp2-tmp),
				tmp3*(tmp2+tmp)
		);
	}
	
	public String toString() {
		return String.format("%.1f %s (%d/%d)", probability()*100, wilson(0.05), this.left, this.right);
	}
}