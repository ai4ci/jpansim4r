package io.github.ai4ci.flow;

import java.util.Collection;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;

/**
 * Supplies a new item of type T synchronously whenever one is requested.
 * This is a super simple
 * @param <T>
 */
public class RSimulationSupplier<
		S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>
		> implements Flow.Publisher<RObservedSimulation<S,A>> {

	Supplier<RObservedSimulation<S,A>> supplier;
	ThreadPoolExecutor executor;
	
	public RSimulationSupplier(Supplier<RObservedSimulation<S,A>> supplier, ThreadPoolExecutor executor) {
		super();
		this.supplier = supplier;
		this.executor = executor;
	}

	@Override
	public void subscribe(Subscriber<? super RObservedSimulation<S,A>> subscriber) {
		
		subscriber.onSubscribe(
				new RSimulationSubscription(subscriber)
		);
	}
	
	public class RSimulationSubscription implements Subscription {

		Subscriber<? super RObservedSimulation<S,A>> subscriber;
		boolean completed = false;
		
		public RSimulationSubscription(Subscriber<? super RObservedSimulation<S,A>> subscriber) {
			this.subscriber = subscriber;
		}
		
		@Override
		public void request(long n) {
			if (!completed) {
				RObservedSimulation<S,A> tmp = supplier.get();
				completed = true;
				subscriber.onNext(tmp);
			} else {
				subscriber.onComplete();
			}
		}

		@Override
		public void cancel() {
			completed = true;
		}
		
	}

	public static <S extends RSimulation<S,?,?,A>,
		A extends RAgent<A,S,?,?>> RSimulationSupplier<S,A> initialize(
				Supplier<RSimulation<S,?,?,A>> supplier, ThreadPoolExecutor executor) {
		return new RSimulationSupplier<S,A>(
				() -> {
					return new RObservedSimulation<S,A>(supplier.get().self());
				}, executor);
	}
	
	/**
	 * Attach a new processor to a flow that iterates through the provided data
	 * and executes the provided function with each element of the data and the
	 * flow data so far, requesting more if needed.
	 * 
	 * @param <I2> the type of the data to paramterise the input of this processor
	 * @param <R2> the output type of this processor
	 * @param data the parameter data items 
	 * @param function a function which takes input and parameterises it.
	 * @return a new simulation flow.
	 */
	public <I2> RSimulationFlow<S,A,I2> attach(String name, Collection<I2> data, BiFunction<RObservedSimulation<S,A>,I2,RObservedSimulation<S,A>> function) {
		RSimulationFlow<S,A,I2> tmp = new RSimulationFlow<S,A,I2>(name, data, function, this.executor);
		this.subscribe(tmp);
		return tmp;
	}
	
}
