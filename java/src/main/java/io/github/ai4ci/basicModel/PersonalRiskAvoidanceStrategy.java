package io.github.ai4ci.basicModel;

import sim.engine.SimState;
import sim.engine.Steppable;

public class PersonalRiskAvoidanceStrategy implements Steppable {

	@Override
	public void step(SimState state) {
		SimpleOutbreak outbreak = (SimpleOutbreak) state;
		outbreak.getPeople().forEach(p -> {
			double local = p.getLocalPrevalence();
			if (local > 0.05) p.adjustCurrentBehaviour(0.90, 0.90, false, 0.5, 0.5);
			if (local < 0.005) p.adjustCurrentBehaviour(1/0.90, 1/0.90, false, 0.5, 0.5);
		});
		if (!outbreak.complete && outbreak.riskAvoidance) outbreak.schedule.scheduleOnce(this);
	}

}
