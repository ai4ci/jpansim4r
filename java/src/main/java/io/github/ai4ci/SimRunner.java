package io.github.ai4ci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ai4ci.basicModel.SimpleOutbreak;
import sim.engine.SimState;
import uk.co.terminological.rjava.RClass;

/**
 * This class is a very basic example of the features of the rJava maven plugin. <br>
 * The class is annotated with an @RClass to identify it as part of the R API. <br>
 */
@RClass
public class SimRunner {

	static SimRunner instance;
	static Logger log = LoggerFactory.getLogger(SimRunner.class);
	
	public SimRunner() {
		super();
	}
	
	static SimRunner get() {
		if (instance == null) instance = new SimRunner();
		return instance;
	}
	
	/**
	 * Description of a getMessage function
	 * @return this java method returns the message that the object was created with 
	 */
//	@RMethod
//	public RCharacter getMessage() {
//		return RConverter.convert(message);
//	}
	
	public static void runSimpleOutbreak() {
		SimState.doLoop(SimpleOutbreak.class, new String[] {"-for","10000"});
	}
	
	
	public static void main(String args[]) {
		SimRunner.runSimpleOutbreak();
	}
	
}
