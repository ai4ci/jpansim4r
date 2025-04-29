package io.github.ai4ci.data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExportCSV<X> {

	CSVPrinter fw;
	String file;
	Dataframe.MapSpecification<X> mapping;
	boolean closed = false;
	
	ExportCSV(String directory, String file) throws IOException {
		fw = new CSVPrinter(new FileWriter(new File(directory,file)),CSVFormat.RFC4180);
		this.file = file;
	}
	
	void setMapping(Dataframe.MapSpecification<X> mapping) throws IOException {
		this.mapping = mapping;
		fw.printRecord(mapping.getKeys());
	}
	
//	@Override
//	public abstract void export(RObservedSimulation<?,?> obsSim);
//	public abstract void export(RSimulation<?,?,?,?> sim);
//	public abstract void export(RObservatory obs);
//	
//	public <INPUT> void export(INPUT in, Function<INPUT,Stream<X>> supply ) {
//		supply.apply(in).forEach(x -> {
//			Dataframe tmp = this.mapping.extract(x);
//			try {
//				tmp.writeCsv(fw, tmp.keySet().toArray(new String[0]));
//			} catch (IOException e) {
//				log.warn("Error writing record");
//			}
//		});
//	}
	
	public void export(Stream<X> supply) {
		supply.forEach(x -> {
			exportSingle(x);
		});
	}
	
	public void exportSingle(X x) {
		Dataframe tmp = this.mapping.extract(x);
		try {
			tmp.writeCsv(fw, tmp.keySet().toArray(new String[0]));
		} catch (IOException e) {
			log.warn("Error writing record");
		}
	}
		
	public void close() {
		if (!closed) {
			try {
				fw.flush();
				fw.close();
				closed = true;
			} catch (IOException e) {
				log.error("Error shutting down exporter");
			}
		}
	}
	

	
}