package hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.Cost;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.CostProcessor;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.ValidationProcessor;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.SearchContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathVector;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.Node;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.processing.EvaluationContextImpl;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.util.RegionKey;

public final class AStarPathfinder extends AbstractPathfinder<AStarSearchState> {
  public AStarPathfinder(PathfinderConfiguration configuration) {
    super(configuration);
  }

  @Override
  protected AStarSearchState createSearchState(PathPosition start, int expectedNodes) {
    return new AStarSearchState(pathfinderConfiguration, start, expectedNodes);
  }

  @Override
  protected void processSuccessors(
      PathPosition start,
      PathPosition target,
      Node currentNode,
      AStarSearchState state,
      SearchContext searchContext) {
    Iterable<PathVector> offsets = neighborStrategy.getOffsets(currentNode.getPosition());

    for (PathVector offset : offsets) {
      PathPosition neighborPos = currentNode.getPosition().add(offset);

      if (!state.isInRange(neighborPos)) continue;

      long packedPos = state.pack(neighborPos);

      int id = state.idOf(packedPos);

      if (id != AStarSearchState.NO_ID) {
        Node existing = state.openNode(id);
        if (existing != null) {
          updateExistingNode(existing, id, currentNode, searchContext, state);
          continue;
        }
      }

      Node neighbor = null;

      EvaluationContext context = null;

      double reopenGCost = 0.0;
      boolean reopening = false;

      if (id != AStarSearchState.NO_ID && state.isClosed(id)) {
        if (pathfinderConfiguration.shouldReopenClosedNodes()) {
          double oldCost = state.closedGCost(id);

          neighbor = createNeighborNode(neighborPos, start, target, currentNode);

          if (hasCustomProcessors) {
            context =
                new EvaluationContextImpl(
                    searchContext,
                    neighbor,
                    currentNode,
                    pathfinderConfiguration.getHeuristicStrategy());
            reopenGCost = calculateGCost(context);
          } else {
            reopenGCost = calculateGCostFast(currentNode, neighborPos);
          }

          if (Double.isNaN(oldCost) || reopenGCost + Math.ulp(reopenGCost) < oldCost) {
            reopening = true;
          }
        }

        if (!reopening) continue;

      }

      if (neighbor == null) {
        neighbor = createNeighborNode(neighborPos, start, target, currentNode);
      }
      neighbor.setParent(currentNode);

      double gCost;
      if (hasCustomProcessors) {
        if (context == null) {
          context =
              new EvaluationContextImpl(
                  searchContext, neighbor, currentNode, pathfinderConfiguration.getHeuristicStrategy());
        }
        if (!isValidByCustomProcessors(context)) {
          continue;
        }
        gCost = reopening ? reopenGCost : calculateGCost(context);
      } else {
        gCost = reopening ? reopenGCost : calculateGCostFast(currentNode, neighborPos);
      }

      if (reopening) {
        state.recordClosedGCost(id, gCost);
      }

      neighbor.setGCost(gCost);
      double fCost = neighbor.getFCost();
      double heapKey = calculateHeapKey(neighbor, fCost);

      if (id == AStarSearchState.NO_ID) {
        id = state.assignId(packedPos);
      }
      state.openInsert(id, heapKey);
      state.setOpenNode(id, neighbor);
    }
  }

  private void updateExistingNode(
      Node existing,
      int nodeId,
      Node currentNode,
      SearchContext searchContext,
      AStarSearchState state) {
    EvaluationContext context =
        hasCustomProcessors
            ? new EvaluationContextImpl(
                searchContext, existing, currentNode, pathfinderConfiguration.getHeuristicStrategy())
            : null;

    double newG =
        hasCustomProcessors
            ? calculateGCost(context)
            : calculateGCostFast(currentNode, existing.getPosition());
    double tol = Math.ulp(Math.max(Math.abs(newG), Math.abs(existing.getGCost())));
    if (newG + tol >= existing.getGCost()) return;

    if (hasCustomProcessors && !isValidByCustomProcessors(context)) {
      return;
    }

    existing.setParent(currentNode);
    existing.setGCost(newG);

    double newF = existing.getFCost();
    double newKey = calculateHeapKey(existing, newF);

    double oldKey = state.openKey(nodeId);

    if (newKey + Math.ulp(newKey) < oldKey) {
      state.openInsert(nodeId, newKey);
    }
    else if (Math.abs(newKey - oldKey) <= Math.ulp(newKey)) {
      state.openInsert(nodeId, oldKey - Math.ulp(oldKey));
    }
  }

  private Node createNeighborNode(
      PathPosition position, PathPosition start, PathPosition target, Node parent) {
    return new Node(
        position,
        start,
        target,
        pathfinderConfiguration.getHeuristicWeights(),
        pathfinderConfiguration.getHeuristicStrategy(),
        parent.getDepth() + 1);
  }

  private boolean isValidByCustomProcessors(EvaluationContext context) {
    for (ValidationProcessor validator : validationProcessors) {
      if (!validator.isValid(context)) {
        return false;
      }
    }
    return true;
  }

  private double calculateGCostFast(Node parent, PathPosition to) {
    double baseCost =
        pathfinderConfiguration.getHeuristicStrategy().calculateTransitionCost(parent.getPosition(), to);
    if (Double.isNaN(baseCost) || Double.isInfinite(baseCost)) {
      throw new IllegalStateException(
          "Heuristic transition cost produced an invalid numeric value: " + baseCost);
    }
    if (baseCost < 0) {
      baseCost = 0;
    }
    return parent.getGCost() + baseCost;
  }

  private double calculateGCost(EvaluationContext context) {
    double baseCost = context.getBaseTransitionCost();
    double additionalCost = 0.0;

    for (CostProcessor processor : costProcessors) {
      Cost contribution = processor.calculateCostContribution(context);
      if (contribution != null) {
        additionalCost += contribution.value();
      }
    }

    double transitionCost = baseCost + additionalCost;
    if (transitionCost < 0) {
      transitionCost = 0;
    }
    return context.getPathCostToPreviousPosition() + transitionCost;
  }
}
