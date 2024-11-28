package org.homepoker.threading;

import org.homepoker.game.GameServerProperties;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.*;

public class VirtualThreadManager {

  private final ScheduledExecutorService scheduler;
  private final ExecutorService executor;

  public VirtualThreadManager(GameServerProperties properties) {

    if (properties.threadModel() == GameServerProperties.ThreadModel.SINGLE_THREAD) {
      this.scheduler = Executors.newSingleThreadScheduledExecutor();
      this.executor = new CurrentThreadExecutor();
    } else {
      this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
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


  /**
   * An executor that runs tasks on the current thread.
   */
  public static class CurrentThreadExecutor extends AbstractExecutorService {

    private volatile boolean terminated;

    @Override
    public void shutdown() {
      terminated = true;
    }

    @Override
    public boolean isShutdown() {
      return terminated;
    }

    @Override
    public boolean isTerminated() {
      return terminated;
    }

    @Override
    public boolean awaitTermination(long theTimeout, @NonNull TimeUnit theUnit) {
      shutdown();
      return terminated;
    }

    @Override
    @NonNull
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public void execute(Runnable theCommand) {
      theCommand.run();
    }
  }
}
