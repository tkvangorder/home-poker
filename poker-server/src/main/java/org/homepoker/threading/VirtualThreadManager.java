package org.homepoker.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class VirtualThreadManager {

  private final ScheduledExecutorService scheduler;
  private final ExecutorService executor;

  public VirtualThreadManager() {
   this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
   this.executor = Executors.newVirtualThreadPerTaskExecutor();
  }

  public void shutdown() {
    System.out.println("Shutting down virtual thread manager");
    scheduler.shutdown();
    executor.shutdown();
  }

  public ScheduledExecutorService getScheduler() {
    return scheduler;
  }

  public ExecutorService getExecutor() {
    return executor;
  }

}
