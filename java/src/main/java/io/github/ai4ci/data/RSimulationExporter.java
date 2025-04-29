package io.github.ai4ci.data;

import java.io.IOException;
import java.util.stream.Stream;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservatory;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;

/**
 * Extract data from a completed simulation and collect it to a sink. 
 */
public interface RSimulationExporter<
		S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>
	> {
	
	public default void  export(Stream<A> agents) {};
	public default void export(S sim) {
		export(sim.streamAgents());
	};
	public default void export(RObservedSimulation<S,A> obsSim) {
		export(obsSim.getSimulation());
	};
	public void close();
	
	public static <S2 extends RSimulation<S2,?,?,A2>, A2 extends RAgent<A2,S2,?,?>> RSimulationExporter<S2,A2> csvFromSimulation(
			String directory,
			String file, 
			Dataframe.MapSpecification<S2> mapping) throws IOException {
		
		mapping.add(Dataframe.pull("id", s -> s.getUrn()));
		mapping.add(Dataframe.pull("exportTimestamp", s -> s.getSimTime()));
		
		ExportCSV<S2> out = new ExportCSV<S2>(directory, file);
		
		out.setMapping(mapping);
		return new RSimulationExporter<S2,A2>() {

			@Override
			public void close() {
				out.close();
			}
			
			public void export(S2 sim) {
				out.export(Stream.of(sim));
			}
			
		};
	}
	
	public static <S2 extends RSimulation<S2,?,?,A2>, A2 extends RAgent<A2,S2,?,?>> RSimulationExporter<S2,A2> csvFromAgents(
			String directory,
			String file, 
			Dataframe.MapSpecification<A2> mapping) throws IOException {
		
		mapping.add(Dataframe.pull("id", a -> a.getUrn()));
		mapping.add(Dataframe.pull("exportTimestamp", a -> a.getSimTime()));
		
		ExportCSV<A2> out = new ExportCSV<A2>(directory, file);
		
		out.setMapping(mapping);
		return new RSimulationExporter<S2,A2>() {

			@Override
			public void close() {
				out.close();
			}
			
			public void export(Stream<A2> sim) {
				out.export(sim);
			}
			
		};
	}
	
	public static <O extends RObservatory,S2 extends RSimulation<S2,?,?,A2>, A2 extends RAgent<A2,S2,?,?>> RSimulationExporter<S2,A2> csvFromObserver(
			String directory,
			String file, 
			Dataframe.MapSpecification<O> mapping) throws IOException {
		
		mapping.add(Dataframe.pull("id", a -> a.getUrn()));
		mapping.add(Dataframe.pull("exportTimestamp", a -> a.getObservationTime()));
		
		ExportCSV<O> out = new ExportCSV<O>(directory, file) ;
		
		out.setMapping(mapping);
		return new RSimulationExporter<S2,A2>() {

			@Override
			public void close() {
				out.close();
			}
			
			@SuppressWarnings("unchecked")
			public void export(RObservedSimulation<S2,A2> obsSim) {
				out.exportSingle((O) obsSim.getObservatory().get());
			}
			
		};
	}
	
}
