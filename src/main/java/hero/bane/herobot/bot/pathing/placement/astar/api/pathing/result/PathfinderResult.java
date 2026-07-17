package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.result;

public interface PathfinderResult {
  boolean successful();

  boolean hasFailed();

  boolean hasFallenBack();

  PathState getPathState();

  Path getPath();
}
