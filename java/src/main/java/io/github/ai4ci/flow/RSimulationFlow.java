package io.github.ai4ci.flow;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiFunction;

import io.github.ai4ci.RAgent;
import io.github.ai4ci.RObservedSimulation;
import io.github.ai4ci.RSimulation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * A simulation initialisation flow processor. This expects an input and
 * produces an output. This caches inputs in a blocking queue, and for each 
 * input applies one of a set of extra parameters and outputs a flow of the 
 * result of combining the inputs and parameters. Typically this will result in 
 * more outputs than inputs.
 * @param <B> the input type of the flow
 * @param <I> the parameter type
 * @param <R> the output type
 */
public class RSimulationFlow<
		S extends RSimulation<S,?,?,A>, 
		A extends RAgent<A,S,?,?>,
		I
		> implements Flow.Processor<RObservedSimulation<S,A>, RObservedSimulation<S,A>> {

	Subscription upstream;
	
	BlockingQueue<RObservedSimulation<S,A>> prototype = new ArrayBlockingQueue<>(4);
	Collection<I> flowInput;
	Iterator<I> flowIterator;
	ThreadPoolExecutor executor;
	private boolean upstreamCompleted = false;
	private boolean complete = false;
	private BiFunction<RObservedSimulation<S,A>, I, RObservedSimulation<S,A>> function;
	private String name;

	/**
	 * The flow is defined by the parameters it will use to augment the inputs
	 * and the function that one input
	 * @param name the name of the processor for debugging 
	 * @param input the data to parameterise the input with
	 * @param function a function which takes input and parameterises it.
	 * @param executor the thread pool.
	 */
	RSimulationFlow(String name, Collection<I> input, 
			BiFunction<RObservedSimulation<S,A>,I,RObservedSimulation<S,A>> function, ThreadPoolExecutor  executor) {
		this.flowInput = input;
		this.flowIterator = input.iterator();
		this.executor = executor;
		this.function = function;
		this.name = name;
	}
	
//	public static <X> RSimulationFlow<?,?,X> initialise(Supplier<X> function, ExecutorService executor) {
//		return new RSimulationFlow<Integer,Integer,X>(
//				Collections.singleton(1), new BiFunction<Integer,Integer,X>() {
//					@Override
//					public X apply(Integer t, Integer u) {
//						return function.get();
//					}}, executor );
//	}

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
	public <I2,R2> RSimulationFlow<S,A,I2> attach(String name, Collection<I2> data, BiFunction<RObservedSimulation<S,A>,I2,RObservedSimulation<S,A>> function) {
		RSimulationFlow<S,A,I2> tmp = new RSimulationFlow<S,A,I2>(name, data, function, this.executor);
		this.subscribe(tmp);
		return tmp;
	}

	public RSimulationConsumer<S,A> process(String directory, int maxThreads) {
		RSimulationConsumer<S,A> tmp = new RSimulationConsumer<S,A>(directory, maxThreads);
		this.subscribe(tmp);
		return tmp;
	}
	
	@Override
	public void onSubscribe(Subscription upstreamSubscription) {
		this.upstream = upstreamSubscription;
		log.debug("[pipeline] {"+name+"} subscribed to upstream");
		upstream.request(1);
	}

	@Override
	public void onNext(RObservedSimulation<S,A> upstreamItem) {
		log.debug("[pipeline] {"+name+"} queued with new item");
		complete = upstreamCompleted;
		prototype.add(upstreamItem);
	}

	@Override
	public void onError(Throwable throwable) {
		if (throwable instanceof InterruptedException) {
			
		} else {
			log.warn("[pipeline] {"+name+"} an error occurred upstream: "+throwable.getMessage());
			upstream.request(1);
		}
	}

	@Override
	public void onComplete() {
		this.upstreamCompleted = true;
	}

	@Override
	public void subscribe(Subscriber<? super RObservedSimulation<S,A>> subscriber) {
		subscriber.onSubscribe(new RSimulationSubscription(subscriber));
	}

	public class RSimulationSubscription implements Flow.Subscription {

		Subscriber<? super RObservedSimulation<S,A>> downstream;
		private Future<RObservedSimulation<S,A>> future; // to allow cancellation
		
		
		RSimulationSubscription(Subscriber<? super RObservedSimulation<S,A>> downstream) {
			this.downstream = downstream;
		}

		/**
		 * This is called when downstream is ready for a new item(s)
		 * It pulls cached input from prototype, or iterates through the 
		 * flowInput and asynchonoously executes the processing step function.
		 * the processing step function will be called multiple times for 
		 * the same input so must check to make sure it clones it f   
		 */
		public void request(long n) {
			// This is the request from the downstream subscriber.
			for (int i = 0; i<n; i++) {
				
				while (prototype.isEmpty()) {
					try {
						if (!upstreamCompleted) {
							log.info("[pipeline] {"+name+"} waiting for input");
							Thread.sleep(1000);
						} else {
							log.info("[pipeline] {"+name+"} completed");
							downstream.onComplete();
							return;
						}
					} catch (InterruptedException e) {
						this.cancel();
						downstream.onError(e);
					}
				}
				// A flowiterator with items exists
				// the queued item exists
				
				final I data = flowIterator.next();
				final RObservedSimulation<S,A> proto = prototype.element();
				future = executor.submit(() -> {
					
					
					RObservedSimulation<S,A> tmp = null;
					try {
						// N.B. it is function's responsibility to make sure that it operates
						// on a clone
						log.debug("[pipeline] {"+name+"} executing with new parameters: "+data);
						tmp = function.apply(proto, data);
						// here the control is passed to the downstream with the new data.
						// this will often be simply placing the value in a queue until it
						// is needed in the downstream-downstream processor or consumer.
						log.debug("[pipeline] {"+name+"} sending downstream "+tmp.getSimulation().getUrn());
						downstream.onNext(tmp);
					} catch (Exception e) {
						log.warn("[pipeline] {"+name+"} exception thrown processing: "+prototype.toString()+" with data:"+ data.toString());
						e.printStackTrace();
						this.cancel();
						throw new RuntimeException(e);
						// downstream.onError(e);
					}
					return tmp;
				});
				
				// Deal with out of iterator condition 
				if (!flowIterator.hasNext()) {
					// The flowIterator has finished. Lets get another
					// input and reset it for the next round.
					try {
						if (!prototype.isEmpty()) {
							// The queue is empty because it has been removed
							// add one to queue, remove one from queue, reset the iterator
							complete = false;
							prototype.take();
							flowIterator = flowInput.iterator();
							if (!upstreamCompleted) upstream.request(1);
							log.info("[pipeline] {"+name+"} resetting iterator");
						} else {
							// empty queue, upstream complete.
							log.info("[pipeline] {"+name+"} no further items to process, nothing upstream");
							complete = true;
							downstream.onComplete();
						}
					} catch (InterruptedException e) {
						this.cancel();
					}
				} else {
					complete = false;
				}
				
				
				
				

			}

		}

		@Override
		public void cancel() {
			upstreamCompleted = true;
			complete = true;
			if (future != null) future.cancel(false);
			upstream.cancel();
		}
	}

}
