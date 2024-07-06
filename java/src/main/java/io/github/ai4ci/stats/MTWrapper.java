package io.github.ai4ci.stats;

import org.apache.commons.rng.UniformRandomProvider;

import ec.util.MersenneTwisterFast;

public class MTWrapper extends MersenneTwisterFast implements UniformRandomProvider {
	public MTWrapper(long seed) {
		super(seed);
	}
}