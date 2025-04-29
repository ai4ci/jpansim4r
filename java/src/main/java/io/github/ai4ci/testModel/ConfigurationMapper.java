package io.github.ai4ci.testModel;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import io.github.ai4ci.testModel.Configuration.OutbreakConfig;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters;

@Mapper(
		nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
		nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ConfigurationMapper {

	ConfigurationMapper INSTANCE = Mappers.getMapper( ConfigurationMapper.class );
	
	OutbreakConfig updateOutbreakConfig(OutbreakConfig toMerge, @MappingTarget OutbreakConfig target);
	OutbreakParameters updateOutbreakParameters(OutbreakParameters toMerge, @MappingTarget OutbreakParameters target);
	
}
