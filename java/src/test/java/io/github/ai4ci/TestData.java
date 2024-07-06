package io.github.ai4ci;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import io.github.ai4ci.stats.DelayDistribution;
import uk.co.terminological.rjava.types.RObject;

/** 
 * Get previously serialised data for testing
 */
public class TestData {

	/**
	 * Gets a previously serialised resource. This is intended to be used from Java for testing so not
	 * exposed in the R api.
	 * 
	 *  @param resourceName - the name of the resource previously serialised
	 */
	public static <X extends RObject> X getResouce(Class<X> binding, String resourceName) {
		try {
			InputStream is = TestData.class.getResourceAsStream("/"+resourceName+".ser");
			if(is==null) throw new IOException("Could not locate "+resourceName);
			return RObject.readRDS(binding, is);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	
	@Test
	void testEmpirical() {
		System.out.println(DelayDistribution.fromProbabilities(0D,0D,1D,2D,2D,1D,1D,1D,1D));
		// System.out.println(DiscreteEmpirical.fromHazards(0D,0D,0.1D,0.2D,0.2D,0.1D,0.1D,0.1D,0.1D));
		// System.out.println(DiscreteEmpirical.fromHazards(0D,0D,0.1D,0.2D,0.2D,0.1D,0.1D,0.1D,0.1D).hazard(3));
		System.out.println(DelayDistribution.fromProbabilities(0D,0D,1D,2D,2D,1D,1D,1D,1D).hazard(3));
	}
}
