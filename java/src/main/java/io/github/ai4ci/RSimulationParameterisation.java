package io.github.ai4ci;

import java.io.Serializable;

import lombok.NonNull;

/**
 * A Java bean defining global simulation initial parameterisation 
 */
public interface RSimulationParameterisation extends RConfiguration,Serializable {
	
	@NonNull public String getParameterisationName();
	
		
	
}
