package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SharedAsyncPathfinderExecutorService {
  private SharedAsyncPathfinderExecutorService() {}

  public static ExecutorService get() {
    return Holder.INSTANCE;
  }

  private static final class Holder {
    static final ExecutorService INSTANCE =
        Executors.newWorkStealingPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    static {
      Runtime.getRuntime().addShutdownHook(new Thread(Holder::shutdownExecutor));
    }

    private static void shutdownExecutor() {
      INSTANCE.shutdown();
      try {
        if (!INSTANCE.awaitTermination(5, TimeUnit.SECONDS)) {
          INSTANCE.shutdownNow();
        }
      } catch (InterruptedException e) {
        INSTANCE.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }
}
