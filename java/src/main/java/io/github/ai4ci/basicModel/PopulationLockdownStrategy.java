package io.github.ai4ci.basicModel;

import sim.engine.SimState;
import sim.engine.Steppable;

public class PopulationLockdownStrategy implements Steppable {

	@Override
	public void step(SimState state) {
		SimpleOutbreak outbreak = (SimpleOutbreak) state;
		if (outbreak.getPrevalence() > 0.05) outbreak.setCurrentBehaviour(0.5, 0.5);
		if (outbreak.getPrevalence() < 0.005) outbreak.returnToBaselineBehaviour();
		if (!outbreak.complete && outbreak.lockdowns) outbreak.schedule.scheduleOnceIn(7, this);
	}

}
