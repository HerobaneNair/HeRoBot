package hero.bane.herobot.bot.pathing.placement.astar.pathfinder.processing;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.IHeuristicStrategy;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.SearchContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;
import hero.bane.herobot.bot.pathing.placement.astar.Node;
import java.util.Objects;

public class EvaluationContextImpl implements EvaluationContext {
  private final SearchContext searchContext;
  private final Node engineNode;
  private final Node parentEngineNode;
  private final IHeuristicStrategy heuristicStrategy;

  public EvaluationContextImpl(
      SearchContext searchContext,
      Node engineNode,
      Node parentEngineNode,
      IHeuristicStrategy heuristicStrategy) {
    this.searchContext = Objects.requireNonNull(searchContext, "searchContext must not be null");
    this.engineNode = Objects.requireNonNull(engineNode, "engineNode must not be null");
    this.parentEngineNode = parentEngineNode;
    this.heuristicStrategy = heuristicStrategy;
  }

  @Override
  public PathPosition getCurrentPathPosition() {
    return this.engineNode.getPosition();
  }

  @Override
  public PathPosition getPreviousPathPosition() {
    return this.parentEngineNode != null ? this.parentEngineNode.getPosition() : null;
  }

  @Override
  public int getCurrentNodeDepth() {
    return this.engineNode.getDepth();
  }

  @Override
  public double getCurrentNodeHeuristicValue() {
    return this.engineNode.getHeuristic();
  }

  @Override
  public double getPathCostToPreviousPosition() {
    if (this.parentEngineNode == null) {
      return 0.0;
    }
    return this.parentEngineNode.getGCost();
  }

  @Override
  public double getBaseTransitionCost() {
    if (this.parentEngineNode == null) {
      return 0.0;
    }

    PathPosition from = this.parentEngineNode.getPosition();
    PathPosition to = this.engineNode.getPosition();

    double baseCost = this.heuristicStrategy.calculateTransitionCost(from, to);

    if (Double.isNaN(baseCost) || Double.isInfinite(baseCost)) {
      throw new IllegalStateException(
          "Heuristic transition cost produced an invalid numeric value: " + baseCost);
    }

    return Math.max(baseCost, 0.0);
  }

  @Override
  public SearchContext getSearchContext() {
    return this.searchContext;
  }
}
