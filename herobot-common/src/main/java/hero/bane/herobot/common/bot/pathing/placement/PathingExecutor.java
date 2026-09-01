package hero.bane.herobot.common.bot.pathing.placement;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PathingExecutor {

    private PathingExecutor() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static ExecutorService get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final ExecutorService INSTANCE =
                Executors.newWorkStealingPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }
}
