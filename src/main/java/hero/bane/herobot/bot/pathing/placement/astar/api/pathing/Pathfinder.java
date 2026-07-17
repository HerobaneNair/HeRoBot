package hero.bane.herobot.bot.pathing.placement.astar.api.pathing;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.hook.PathfinderHook;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;

public interface Pathfinder {
  default PathfindingSearch findPath(PathPosition start, PathPosition target) {
    return findPath(start, target, null);
  }

  PathfindingSearch findPath(PathPosition start, PathPosition target, EnvironmentContext context);

  @Deprecated
  void registerPathfindingHook(PathfinderHook hook);
}
