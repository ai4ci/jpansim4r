package io.github.ai4ci.flow;

import java.util.ArrayList;
import java.util.List;

import io.github.ai4ci.RSimulationRunnable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RSimulationMonitor implements Runnable {
	private RSimulationConsumer<?,?> pool;
	private int seconds;
	private long maxMem;
	private boolean run = true;
	private int uncommitted;
	private int totalSimulations;
	private List<RSimulationRunnable<?,?>> executing = new ArrayList<>();

	public RSimulationMonitor(RSimulationConsumer<?,?> pool, int delay, int maxMemGb, int totalSimulations) {
		this.pool = pool;
		this.seconds=delay;
		this.maxMem = maxMemGb * 1024 * 1024 * 1024;
		uncommitted = pool.executor.getCorePoolSize();
		this.totalSimulations = totalSimulations;
	}

	public void shutdown(){
		log.info("[monitor] shutting down monitor.");
		this.run=false;
	}
	
	private static long freeMem(long maxMem) {
		Runtime runtime = Runtime.getRuntime();
		long allocatedMemory = runtime.totalMemory() - runtime.freeMemory();
		// allocatedMemory = allocatedMemory > maxMem ? maxMem : allocatedMemory;  
		return runtime.maxMemory() - allocatedMemory;
	}
	
	@Override
	public void run() {
		log.info("[monitor] Initialising monitor thread");
		long last = System.currentTimeMillis();
		while(run){
			long presumableFreeMemory = freeMem(maxMem);
			long mbMax = Runtime.getRuntime().maxMemory() / (1024*1024);
			long mbFree = presumableFreeMemory / (1024*1024);
			if (System.currentTimeMillis() - last > 5000) {
				// only log every 5 secs
				log.info(
						String.format("[monitor] [%d/%d] Active: %d, Queued: %d, Completed: %d/%d [%d%%], Free memory: %dMb/%dMb" ,
								this.pool.executor.getPoolSize(),
								this.pool.executor.getCorePoolSize(),
								this.pool.executor.getActiveCount(),
								this.pool.executor.getQueue().size(),
								this.pool.executor.getCompletedTaskCount(),
								this.totalSimulations,
								this.pool.executor.getCompletedTaskCount()*100/this.totalSimulations,
								mbFree , mbMax)
						);
				this.executing.forEach(e -> {
					log.info(e.progress());
				});
				last = System.currentTimeMillis();
			}
			try {
				Thread.sleep(seconds*250);
			} catch (InterruptedException e) {
				run=false;
				e.printStackTrace();
			}
			int freeThreads = this.pool.executor.getCorePoolSize() - this.pool.executor.getActiveCount();
			
			if (Runtime.getRuntime().maxMemory() <= 2*1024*1024*1024 ) {
				log.error("Less than 2G memory available for simulations. Aborting.");
				run=false;
			}
			
			// T
			if (mbFree > 2*1024 && freeThreads > 0) {
				// more than 2 Gb free and unused threads in pool.
				if (!pool.paused && !pool.upstreamComplete && uncommitted > 0) {
					log.info("[monitor] requesting a simulation to run: "+mbFree+" Mb free; "+freeThreads+" threads available; "+uncommitted+" uncommitted.");
					pool.subscription.request(1);
					uncommitted -= 1;
				}
			}
		}
		log.info("[monitor] monitor shut down complete.");
	}

	public void attach(RSimulationRunnable<?,?> sim) {
		this.executing.add(sim);
	}
	
	public void release(RSimulationRunnable<?,?> sim) {
		this.executing.remove(sim);
		this.uncommitted += 1;
		if (this.uncommitted > pool.executor.getCorePoolSize()) this.uncommitted = pool.executor.getCorePoolSize();
	}
}