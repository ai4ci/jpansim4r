package io.github.ai4ci.testModel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github.ai4ci.testModel.TestResult.Result;
import io.github.ai4ci.testModel.TestResult.Type;

public enum TestingStrategy implements Function<Person, List<TestResult>>,Serializable {
	
	/**
	 * This strategy implements a combined LFT, plus PCR confirmation
	 * The PCR test is only performed if the LFT is positive. LFT results will
	 * typically be instant, with 1 day delay to getting a PCR. By default PCR 
	 * tests will be available after a delay.  
	 */
	SCREEN_LFT ((a) -> {
		ArrayList<TestResult> out = new ArrayList<>();
		if (a.knownRecovered() || a.awaitingTestOrKnownPositiveToday())
			// no tests performed on recovered people
			// no duplicates on 
			return out;
		if (a.screenToday() || a.symptomatic()) {
			// Do a LFT
			TestResult tr = TestResult.resultFrom(a,Type.LFT).get();
			out.add(tr);
			if (tr.confirmedPositive(a.getSimTime())) {
				// get a PCR scheduled for 1 day in future.
				TestResult tr2 = TestResult.resultFrom(a,Type.PCR).get();
				tr2.delay(1);
				out.add(tr2);
			}
		}
		return out;
	}),
	
	/**
	 * Only do PCR tests in response to either screening requirements or
	 * symptoms. Only do one test at a time. N.B. This strategy has the potential
	 * to generate multiple tests per person per infection.
	 */
	PCR_ONLY ((a) -> {
		// TODO Auto-generated method stub
		if (a.knownRecovered() || a.awaitingTestOrKnownPositiveToday())
			// no tests performed on recovered people
			// no duplicates on 
			return Collections.emptyList();
	
		if (a.screenToday() || a.symptomatic()) {
			// Do a PCR
			TestResult tr = TestResult.resultFrom(a,Type.PCR).get();
			return Collections.singletonList(tr);
		} else {
			return Collections.emptyList();
		}
	});
	
	
	// IMPLEMENTATION DETAILS
	
	private interface Updater extends Function<Person,List<TestResult>>,Serializable {}
	private Updater update;
	private TestingStrategy(Updater update) {
		this.update = update;
	}
	
	public List<TestResult> apply(Person person) {
		ArrayList<TestResult> out = new ArrayList<>();
		List<TestResult> tmp = update.apply(person);
		out.addAll(tmp);
		return out;
	}
	
//	/**
//	 * Update a list of tests for an individual based on current tests planned for
//	 * today and the person's state. This function does not return anything but
//	 * the test
//	 * @param t the person
//	 * @param tests the tests planned for today as a modifiable list
//	 */
//	// public abstract void update(Person t, ArrayList<TestResult> tests);
//	
//	public static interface Filter extends Predicate<Person> {};
	
//	public TestingStrategy or(TestingStrategy add) {
//		return new TestingStrategy() {
//			@Override
//			public void update(Person t, List<TestResult> out) {
//				add.update(t, out);
//			}
//		};
//	}
//	
//	public TestingStrategy excluding(Filter unless) {
//		return new TestingStrategy() {
//			@Override
//			public void update(Person t, List<TestResult> out) {
//				if (unless.test(t)) out.removeAll(out);
//			}
//		};
//	}
//	
//	public TestingStrategy withConfirmation(TestingStrategy test, int delay) {
//		return new TestingStrategy() {
//			@Override
//			public void update(Person t, List<TestResult> out) {
//				if (out.stream().anyMatch(r -> r.resultOnDay(t.getSimTime()).equals(Result.POSITIVE))) {
//					List<TestResult> followups = new ArrayList<>(test.apply(t));
//					followups.forEach(test -> test.delay(delay));
//					out.addAll(followups);
//				}
//			}
//		};
//	}
//	
//	public static TestingStrategy from(Function<Person, Optional<TestResult>> fn) {
//		return new TestingStrategy() {
//			@Override
//			public void update(Person t, ArrayList<TestResult> out) {
//				fn.apply(t).ifPresent(out::add);
//			}
//		};
//	}
//	
//	
//	
////	public static Filter RECOVERED = (a) -> a.knownRecovered();
////	public static Filter TEST_PENDING = (a) -> a.awaitingTestOrKnownPositiveToday();
//	
//	/**
//	 * This strategy implements a combined LFT, plus PCR confirmation
//	 * The PCR test is only performed if the LFT is positive. LFT results will
//	 * typically be instant, with 1 day delay to getting a PCR. By default PCR 
//	 * tests will be available after a delay.  
//	 */
//	public static TestingStrategy SCREEN_LFT = new TestingStrategy() {
//		@Override
//		public void update(Person a, ArrayList<TestResult> out) {
//			if (a.knownRecovered() || a.awaitingTestOrKnownPositiveToday())
//				// no tests performed on recovered people
//				// no duplicates on 
//				return;
//			if (a.screenToday() || a.symptomatic()) {
//				// Do a LFT
//				TestResult tr = TestResult.resultFrom(a,Type.LFT).get();
//				out.add(tr);
//				if (tr.confirmedPositive(a.getSimTime())) {
//					// get a PCR scheduled for 1 day in future.
//					TestResult tr2 = TestResult.resultFrom(a,Type.PCR).get();
//					tr2.delay(1);
//					out.add(tr2);
//				}
//			}
//		}
//	};
//	
//	
//	/**
//	 * Only do PCR tests in response to either screening requirements or
//	 * symptoms. Only do one test at a time. N.B. This strategy has the potential
//	 * to generate multiple tests per person per infection.
//	 */
//	public static TestingStrategy PCR_ONLY = new TestingStrategy() {
//		@Override
//		public void update(Person a, ArrayList<TestResult> tests) {
//			// TODO Auto-generated method stub
//			if (a.knownRecovered() || a.awaitingTestOrKnownPositiveToday())
//				// no tests performed on recovered people
//				// no duplicates on 
//				return;
//		
//			if (a.screenToday() || a.symptomatic()) {
//				// Do a LFT
//				TestResult tr = TestResult.resultFrom(a,Type.PCR).get();
//				tests.add(tr);
//			}
//		}
//		
//	};
	
			
	
	
//	public static TestingStrategy SYMPTOMATIC_LFT = from(
//			a -> {
//				if (a.symptomatic()) return TestResult.resultFrom(a,Type.LFT);
//			});
//	
//	public static TestingStrategy DEFAULT = SCREEN_LFT.excluding(RECOVERED).excluding(TEST_PENDING);
//			double pTmp;
//			if (a.knownRecovered()) return Optional.empty();
//			// TODO: this logic for deciding if a person is tested needs
//			// revision. This depends on all sorts of factors, and parameters
//			// around the use of a test. It'll need to be implemented as a
//			// strategy in the test class when we extend that.
//			if (!a.awaitingTestOrKnownPositiveToday()) {
//				// Do not re-test if recent test positive or waiting for test
//				// result unless randomly re-tested due to baseline test rate
//				// e.g. screening.
//				if (a.symptomatic()) {
//					
//					
//					
//				} else if (a.sampler().uniform() > a.getStatus().getBaseScreeningProbability()) {
//					
//					
//				} else {
//					
//				}
//				pTmp = a.getStatus().getBaseProbabilityOfTesting();
//			} else {
//				// Regardless of infection status the patient can be tested. 
//				// This depends on a probability distribution of days since last infection and
//				// a baseline probability
//				// Combined probability of screening test or reactive test.
//				pTmp = a.getSimulation().getParameterisation().getTestTakenProbability( 
//						a.getDaysSinceLastInfection(),
//						a.getStatus().getBaseProbabilityOfTesting()
//				);
//			}
//			
//			if (a.sampler().uniform() > pTmp) { 
//				return Optional.empty();
//			} else { 
//				return Optional.of(TestResult.resultFrom(a, Type.PCR));
//			}
//		});
//	
	
}