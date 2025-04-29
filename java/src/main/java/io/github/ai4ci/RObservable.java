package io.github.ai4ci;

public interface RObservable<O extends RObservable<O>> extends RObserved {
	
	public Long getSimTime();
	public String getUrn();
	public O self();
	
	public void registerNamedObserver(RObserver<O,?> observer);
	public void setupHistory();
	
	
	
	
	
	
//	@SuppressWarnings("unchecked")
//	public default <X> RObserver.History<O, X> keepHistory(
//			String name, Class<X> type, RObserver.Mapper<O,X> mapper, int length) {
//		RObserver.History<O, X> tmp = new RObserver.History<O, X>((Class<O>) this.getClass(), name, type, mapper, length);
//		this.registerNamedObserver(tmp);
//		return tmp;
//	}
//	
//	@SuppressWarnings("unchecked")
//	public default <X> History<O, X> keepFullHistory(
//			String name, Class<X> type, RObserver.Mapper<O,X> mapper) {
//			History<O, X> tmp = new RObserver.History<O, X>((Class<O>) this.getClass(), name, type, mapper, null);
//			this.registerNamedObserver(tmp);
//			return tmp;
//	}
//	
//	@SuppressWarnings("unchecked")
//	public default <X> Last<O, X> keepLastValue(
//			String name, Class<X> type, RObserver.Mapper<O,X> mapper) {
//			Last<O, X> tmp = new RObserver.Last<O, X>((Class<O>) this.getClass(), name, type, mapper);
//			this.registerNamedObserver(tmp);
//			return tmp;
//	}
//	
//	@SuppressWarnings("unchecked")
//	public default <X> ListHistory<O, X> keepHistoryList(
//			String name, Class<X> type, RObserver.ListMapper<O,X> mapper, int length) {
//			ListHistory<O, X> tmp = new RObserver.ListHistory<O, X>((Class<O>) this.getClass(), name, type, mapper, length);
//			this.registerNamedObserver(tmp);
//			return tmp;
//	}
}
