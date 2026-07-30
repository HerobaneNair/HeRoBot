package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.result.PathfinderResult;
import java.util.Optional;
import java.util.function.Consumer;

public interface PathfindingSearch {
  PathfindingSearch ifPresent(Consumer<PathfinderResult> callback);

  PathfindingSearch orElse(Consumer<PathfinderResult> callback);

  PathfindingSearch exceptionally(Consumer<Throwable> callback);

  PathfinderResult resultBlocking();

  Optional<PathfinderResult> result();

  boolean done();

  void abort();
}
