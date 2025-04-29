package io.github.ai4ci.testModel;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.SerializationUtils;

import io.github.ai4ci.flow.Bootstraps;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder(toBuilder = true)
@Jacksonized
@Slf4j
public class ExecutionConfiguration {

	Configuration.OutbreakConfig defaultConfiguration;
	Configuration.OutbreakParameters defaultParameters;
	
	@Builder.Default Integer configurationBootstraps = 1;
	List<Configuration.OutbreakConfig> configurationModifiers;
	
	@Builder.Default Integer paramterisationBootstraps = 1;
	List<Configuration.OutbreakParameters> parameterModifiers;
	
	@Builder.Default Integer executionBootstraps = 1;
	@Builder.Default Integer maxSimulationLength = 100;
	@Builder.Default Integer maxThreads = 8;
	@Builder.Default Integer maxMemoryGb = 16;
	@Builder.Default boolean debug = false;
	@Builder.Default String packageVersion = ExecutionConfiguration.class.getPackage().getImplementationVersion();
	
	public Bootstraps<OutbreakConfig> bootstrapConfiguration() {
//		BeanUtilsBean notNull=new NullAwareBeanUtilsBean();
		List<OutbreakConfig> tmp = configurationModifiers.stream().map(mod -> {
			OutbreakConfig orig = SerializationUtils.clone(defaultConfiguration);
//			try {
				ConfigurationMapper.INSTANCE.updateOutbreakConfig(mod, orig);
				// notNull.copyProperties(orig,mod);
//			} catch (IllegalAccessException | InvocationTargetException e) {
//				log.warn("Couldnt copy property");
//			}
			return orig;
		}).collect(Collectors.toList());
		return Bootstraps.from(configurationBootstraps, tmp);
	}
	
	public Bootstraps<OutbreakParameters> bootstrapParamterisation() {
//		BeanUtilsBean notNull=new NullAwareBeanUtilsBean();
		List<OutbreakParameters> tmp = parameterModifiers.stream().map(mod -> {
			OutbreakParameters orig = SerializationUtils.clone(defaultParameters);
			// try {
				ConfigurationMapper.INSTANCE.updateOutbreakParameters(mod, orig);
				// notNull.copyProperties(orig,mod);
//			} catch (IllegalAccessException | InvocationTargetException e) {
//				log.warn("Couldnt copy property");
//			}
			return orig;
		}).collect(Collectors.toList());
		return Bootstraps.from(configurationBootstraps, tmp);
	}
	
	public int totalSimulations() {
		return 
				this.configurationBootstraps*
				this.configurationModifiers.size()*
				this.paramterisationBootstraps*
				this.parameterModifiers.size()*
				this.executionBootstraps;
	}
	
//	public static class NullAwareBeanUtilsBean extends BeanUtilsBean {
//
//		@Override
//		public void copyProperty(Object dest, String name, Object value)
//				throws IllegalAccessException, InvocationTargetException {
//			if (value == null)
//				return;
//			super.copyProperty(dest, name, value);
//		}
//
//	}
}
