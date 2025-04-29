package io.github.ai4ci;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.github.ai4ci.stats.DelayDistribution;

class TestSimulation {

	@Test
	void test() {
		DelayDistribution tmp = DelayDistribution.fromCounts(0.75, 0,0,0,50,50,100,100,100,50,50);
		for (int i=0; i<tmp.size(); i++) System.out.println(tmp.hazard(i));
	}

}
