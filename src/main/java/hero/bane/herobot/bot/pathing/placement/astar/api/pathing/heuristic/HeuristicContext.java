package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.PathfindingProgress;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;

public class HeuristicContext {
  private final PathfindingProgress pathfindingProgress;
  private final HeuristicWeights heuristicWeights;

  public HeuristicContext(
      PathPosition position,
      PathPosition startPosition,
      PathPosition targetPosition,
      HeuristicWeights heuristicWeights) {
    this.pathfindingProgress = new PathfindingProgress(startPosition, position, targetPosition);
    this.heuristicWeights = heuristicWeights;
  }

  public HeuristicContext(
      PathfindingProgress pathfindingProgress, HeuristicWeights heuristicWeights) {
    this.pathfindingProgress = pathfindingProgress;
    this.heuristicWeights = heuristicWeights;
  }

  public PathfindingProgress getPathfindingProgress() {
    return pathfindingProgress;
  }

  public PathPosition position() {
    return pathfindingProgress.currentPosition();
  }

  public PathPosition startPosition() {
    return pathfindingProgress.startPosition();
  }

  public PathPosition targetPosition() {
    return pathfindingProgress.targetPosition();
  }

  public HeuristicWeights heuristicWeights() {
    return heuristicWeights;
  }
}
