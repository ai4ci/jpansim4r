package io.github.ai4ci;

import java.io.Serializable;

import lombok.NonNull;

/**
 * A simulation configuration is a JavaBean that provides all the data needed
 * to setup and build one or more CONFIGURED simulations. These configurations 
 * simulations are potentially the result of random events during configuration
 * so multiple bootstraps may have the same configuration. 
 * 
 * Simulations that share the same configuration are testing the same basic 
 * scenario, but with details that use different random seeds. Each of these
 * will have a bootstrap id. 
 */
public interface RSimulationConfiguration extends RConfiguration, Serializable {
	
	@NonNull public String getConfigurationName();

	
	// parameters needed to build the simulation
	
	
}
