package io.github.ai4ci.testModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.DefaultConfiguration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.github.ai4ci.RObservatory;
import io.github.ai4ci.RObserved;
import io.github.ai4ci.data.Dataframe;
import io.github.ai4ci.data.RSimulationExporter;
import io.github.ai4ci.flow.Bootstraps;
import io.github.ai4ci.flow.RSimulationConsumer;
import io.github.ai4ci.flow.RSimulationPipeline;
import io.github.ai4ci.testModel.Configuration.OutbreakConfig;
import io.github.ai4ci.testModel.Configuration.OutbreakParameters;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiTest {

	public static enum Observers {SUSCEPTIBLE, MOBILITY}
	public static enum AgentObservers {EXPOSED_DATE, INFECTOR}

	public static void main(String[] args) throws IOException {
		Configurator.initialize(new DefaultConfiguration());
		Configurator.setRootLevel(Level.DEBUG);
		
		Path home = SystemUtils.getUserHome().toPath().resolve("simulation");
		Files.createDirectories(home.resolve("config"));
		
		while (Files.list(home.resolve("config"))
			.filter(p -> p.getFileName().toString().endsWith(".yaml")).count() > 0) {
			
			Optional<Path> tmp = Files.list(home.resolve("config"))
			.filter(p -> p.getFileName().toString().endsWith(".yaml"))
			.findFirst();
			
			if (tmp.isPresent()) {
				Path file = tmp.get();
				Path temp = file.resolveSibling(file.getFileName()+".processing");
				if (!Files.exists(temp)) {
					// prevents race condition.
					Files.move(file, temp, StandardCopyOption.ATOMIC_MOVE);
					log.info("found simulation configuration: "+file);
					boolean success = doSim(home,temp);
					if (success) Files.move(temp, file.resolveSibling(file.getFileName()+".done"), StandardCopyOption.REPLACE_EXISTING);
				} 
			};
			
		}
		
		log.info("no more simulations found.");
		System.exit(0);
	}
	
	public static boolean doSim(Path home, Path file) {
		
		ObjectMapper om = new ObjectMapper(new YAMLFactory());
		om.enable(SerializationFeature.INDENT_OUTPUT);
		om.setSerializationInclusion(Include.NON_NULL);
		
		ExecutionConfiguration execution;
		String output = file.getFileName().toString().replace(".yaml.processing", "");
		try {
			execution = om.readerFor(ExecutionConfiguration.class).readValue(file.toFile());
			Files.createDirectories(home.resolve(output));
			om.writeValue(home.resolve(output).resolve(output+".yaml").toFile(), execution);
			System.out.println("Config file read from: " + file.toString());
		} catch (IOException e) {
			System.out.println("Failed to read from: " + file.toString());
			throw new RuntimeException(e);
		}
		
		
		if (execution.isDebug()) {
			Configurator.setRootLevel(Level.DEBUG);
		} else {
			Configurator.setRootLevel(Level.INFO);
		}
		
		String directory = home.resolve(output).toString();

		//Flowable.fromSingle(SingleFromUnsafeSource.fromSupplier(RObservedSimulation.uninitialised(Outbreak.class)));

		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(4);
		Builder factory = new Builder();
		RSimulationPipeline<Outbreak, OutbreakConfig, OutbreakParameters, Person> pipeline = 
				RSimulationPipeline.ofType(factory, directory, false);


		RSimulationConsumer<Outbreak, Person> pool;
		try {
			pool = pipeline.initialise(executor)
			.attach("configure", execution.bootstrapConfiguration(), pipeline::configure)
			.attach("parameterise", execution.bootstrapParamterisation(), pipeline::parameterise)
			.attach("bootstrap", Bootstraps.range(execution.getExecutionBootstraps()), pipeline::bootstrap)
			.process(directory, 
					execution.getMaxThreads(), 
					execution.getMaxMemoryGb(),
					execution.getMaxSimulationLength(),
					execution.totalSimulations()
					)
			.withExporter(RSimulationExporter.csvFromObserver(
					directory, "incidence.csv",
					Dataframe.map(
							RObservatory.class,
							Dataframe.pull("time", s -> s.getHistoricalSteps()),
							RObserved.data(Outbreak.RT_EFFECTIVE_HX),
							RObserved.data(Outbreak.SUSCEPTIBLE_COUNT),
							RObserved.data(Outbreak.INCIDENCE_COUNT),
							RObserved.data(Outbreak.RECOVERED_COUNT),
							RObserved.data(Outbreak.INFECTED_COUNT),
							RObserved.data(Outbreak.SYMPTOM_ONSET_COUNT),
							RObserved.data(Outbreak.TEST_POS_HX),
							RObserved.data(Outbreak.TEST_TAKEN_HX),
							RObserved.data(Outbreak.CONTACT_RATE_HX),
							RObserved.data(Outbreak.KNOWN_POSITIVE_COUNT)
							)
					)
			)
			.withExporter(RSimulationExporter.csvFromAgents(
					directory, "linelist.csv",
					Dataframe.map(
							Person.class,
							Dataframe.pull("id2", p -> p.getId()),
							RObserved.time(Person.PCR_POSITIVES),
							RObserved.data(Person.PCR_POSITIVES, "contact_rate_ratio", s -> s.getContactRateAdjustment()),
							RObserved.data(Person.PCR_POSITIVES, "last_infected_time", s -> s.getLastInfected()),
							RObserved.data(Person.PCR_POSITIVES, "susceptibility", s -> s.getProbabilityInfectionGivenInfectiousContact()) //,
							//Dataframe.pull("infectorId", p -> p.getLastInfector() )
							)
					)
			)
			.withExporter(RSimulationExporter.csvFromAgents(
					directory, "testagent.csv",
					Dataframe.map(
							Person.class,
							Dataframe.pull("id2", p -> p.getId()),
							RObserved.time(Person.TEST_AGENT),
							RObserved.data(Person.TEST_AGENT, "contact_rate_ratio", s -> s.getContactRateAdjustment()),
							RObserved.data(Person.TEST_AGENT, "last_infected_time", s -> s.getLastInfected()),
							RObserved.data(Person.TEST_AGENT, "susceptibility", s -> s.getProbabilityInfectionGivenInfectiousContact()),
							RObserved.data(Person.TEST_AGENT, "infection_risk", s -> s.getInfectionRisk()),
							RObserved.data(Person.TEST_AGENT, "observed_infection_risk", s -> s.getKnownInfectionRisk())
							//Dataframe.pull("infectorId", p -> p.getLastInfector() )
							)
					)
					)
			.start();
			
			while (!pool.idle()) Thread.sleep(10000);
			
			pool.shutdown();
			return true;
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			log.info("An error occurred in the simulation: "+output);
			return false;
		}

		
		
		
		
		
	}

}
