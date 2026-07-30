package hero.bane.herobot.mod.common.bot.pathing.placement;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pool that off-thread path searches run on.
 *
 * <p>Pathfinder configurations are always built with {@code async(false)}, so pathetic never
 * dispatches work of its own; every task here comes from {@link PathFinder#findPathAsync}. Sized to
 * half the available cores so searches cannot starve the server thread.
 */
final class PathingExecutor {

    private PathingExecutor() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static ExecutorService get() {
        return Holder.INSTANCE;
    }

    /** Allocated on first use via the initialization-on-demand holder idiom. */
    private static final class Holder {
        static final ExecutorService INSTANCE =
                Executors.newWorkStealingPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }
}
