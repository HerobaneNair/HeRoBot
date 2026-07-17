package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.hook;

public interface PathfinderHook {
  void onPathfindingStep(PathfindingContext pathfindingContext);

  default void onPathfindingStart(PathfindingContext pathfindingContext) {}
}
