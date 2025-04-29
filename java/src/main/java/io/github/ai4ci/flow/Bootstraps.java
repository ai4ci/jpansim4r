package io.github.ai4ci;

import java.util.ArrayList;
import org.apache.commons.lang3.tuple.Pair;

public class Bootstraps<X> extends ArrayList<Pair<Integer,X>> {

	Bootstraps(int boots, X[] values) {
		super();
		for (int j = 0; j<boots; j++) {
			for (int i = 0; i<values.length; i++) {
				this.add(Pair.of(j, values[i]));
			}
		}
	}
	
	@SafeVarargs
	public static <Y> Bootstraps<Y> from(int boots, Y... values) {
		return new Bootstraps<Y>(boots,values);
	}
}
