package io.github.ai4ci;

import sim.engine.SimState;
import sim.engine.Steppable;

public interface RSteppable<S extends RSimulation<S,?,?,?>> extends Steppable {

	public default int getPriority() {return 0;}
	
	@SuppressWarnings("unchecked")
	public default void step(SimState state) {
		this.doStep((S) state);
		if (this.remainsActive((S) state)) {
			state.schedule.scheduleOnce(this, getPriority());
		}
	}
	
	/**
	 * override if you want behaviour to not terminate when the simulation
	 * thinks it is complete. If you need custom behaviour you can override this
	 * to determine whether the scheduler should continue with this agent or 
	 * set this to false; and schedule manually in the step() method.
	 * 
	 * This is evaluated by default after the step method is called.
	 *  
	 * @return
	 */
	public boolean remainsActive(S simulation);
	
	/**
	 * This performs the behaviour of the agent for one time step. By default
	 * everything is rescheduled for every step if the simulation is not 
	 * complete. 
	 */
	public void doStep(S simulation);
	
	/** 
	 * This implementation of RSteppable updates the scheduler until the simulation
	 * is complete.
	 * @param <S>
	 */
	public static abstract class UntilComplete<S extends RSimulation<S,?,?,?>> implements RSteppable<S> {
		
		int priority;
		
		public UntilComplete(int priority) {
			this.priority = priority;
		}
		
		public int getPriority() {return priority;}
		public boolean remainsActive(S simulation) {return !simulation.isComplete();}
	}
	
	

}
