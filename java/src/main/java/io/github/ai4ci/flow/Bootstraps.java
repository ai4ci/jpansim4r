package io.github.ai4ci.flow;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;

public class Bootstraps<X extends Serializable> extends ArrayList<Pair<Integer,X>> {

	Bootstraps(int boots, X[] values) {
		super();
		for (int j = 0; j<boots; j++) {
			for (int i = 0; i<values.length; i++) {
				this.add(Pair.of(j, SerializationUtils.clone(values[i])));
			}
		}
	}
	
	Bootstraps(int boots, List<X> values) {
		super();
		for (int j = 0; j<boots; j++) {
			for (int i = 0; i<values.size(); i++) {
				this.add(Pair.of(j, SerializationUtils.clone(values.get(i))));
			}
		}
	}
	
	@SafeVarargs
	public static <Y extends Serializable> Bootstraps<Y> from(int boots, Y... values) {
		return new Bootstraps<Y>(boots,values);
	}
	
	public static <Y extends Serializable> Bootstraps<Y> from(int boots, List<Y> values) {
		return new Bootstraps<Y>(boots,values);
	}
	
	
	
	public static List<Integer> range(int max) {
		return IntStream.range(0, max).mapToObj(i -> (Integer) i).collect(Collectors.toList());
	}
}
