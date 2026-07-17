package hero.bane.herobot.bot.pathing.placement.astar.pathfinder;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.INeighborStrategy;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.Pathfinder;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.PathfindingSearch;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.hook.PathfinderHook;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.hook.PathfindingContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.CostProcessor;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.Processor;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.ValidationProcessor;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.SearchContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.result.Path;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.result.PathState;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.result.PathfinderResult;
import hero.bane.herobot.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.Depth;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;
import hero.bane.herobot.bot.pathing.placement.astar.Node;
import hero.bane.herobot.bot.pathing.placement.astar.pathfinder.processing.EvaluationContextImpl;
import hero.bane.herobot.bot.pathing.placement.astar.pathfinder.processing.SearchContextImpl;
import hero.bane.herobot.bot.pathing.placement.astar.result.PathImpl;
import hero.bane.herobot.bot.pathing.placement.astar.result.PathfinderResultImpl;
import hero.bane.herobot.bot.pathing.placement.astar.util.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractPathfinder<S extends SearchState> implements Pathfinder {
  protected static final Set<PathPosition> EMPTY_PATH_POSITIONS =
      Collections.unmodifiableSet(new LinkedHashSet<>(0));
  private static final int MIN_INITIAL_HEAP_CAPACITY = 32;
  private static final double TIE_BREAKER_WEIGHT = 1e-6;

  protected final PathfinderConfiguration pathfinderConfiguration;
  protected final NavigationPointProvider navigationPointProvider;
  protected final List<ValidationProcessor> validationProcessors;
  protected final List<CostProcessor> costProcessors;
  protected final INeighborStrategy neighborStrategy;
  protected final ExecutorService executorService;

  private final List<Processor> processors;

  protected final boolean hasCustomProcessors;

  private final Set<PathfinderHook> pathfinderHooks = Collections.synchronizedSet(new HashSet<>());

  protected AbstractPathfinder(PathfinderConfiguration pathfinderConfiguration) {
    this.pathfinderConfiguration =
        Objects.requireNonNull(pathfinderConfiguration, "pathfinderConfiguration must not be null");

    this.navigationPointProvider =
        Objects.requireNonNull(
            pathfinderConfiguration.getProvider(),
            "NavigationPointProvider from configuration has to be set.");

    this.validationProcessors = pathfinderConfiguration.getNodeValidationProcessors();
    this.costProcessors = pathfinderConfiguration.getNodeCostProcessors();
    this.neighborStrategy = pathfinderConfiguration.getNeighborStrategy();
    this.pathfinderHooks.addAll(pathfinderConfiguration.pathfindingHooks());

    List<Processor> combinedProcessors =
        new ArrayList<>(this.validationProcessors.size() + this.costProcessors.size());
    combinedProcessors.addAll(this.validationProcessors);
    combinedProcessors.addAll(this.costProcessors);
    this.processors = Collections.unmodifiableList(combinedProcessors);
    this.hasCustomProcessors = !this.processors.isEmpty();
    if (pathfinderConfiguration.isAsync()) {
      this.executorService =
          Objects.requireNonNull(
              pathfinderConfiguration.executorService(),
              "Executor service from configuration has not been set");
    } else {
      this.executorService = pathfinderConfiguration.executorService();
    }
  }

  static int computeInitialHeapCapacity(
      PathPosition start, PathPosition target, int branching, int maxIterations) {
    int dx = Math.abs(start.getFlooredX() - target.getFlooredX());
    int dy = Math.abs(start.getFlooredY() - target.getFlooredY());
    int dz = Math.abs(start.getFlooredZ() - target.getFlooredZ());
    long manhattan = (long) dx + (long) dy + (long) dz;
    long estimated = manhattan * Math.max(1, branching);
    long bounded = Math.max(MIN_INITIAL_HEAP_CAPACITY, Math.min(estimated, maxIterations));
    return (int) bounded;
  }

  @Override
  public PathfindingSearch findPath(
      PathPosition start, PathPosition target, EnvironmentContext environmentContext) {
    Objects.requireNonNull(start, "start PathPosition must not be null");
    Objects.requireNonNull(target, "target PathPosition must not be null");
    return initiatePathing(start, target, environmentContext);
  }

  @Override
  public void registerPathfindingHook(PathfinderHook hook) {
    if (hook != null) {
      this.pathfinderHooks.add(hook);
    }
  }

  private PathfindingSearch initiatePathing(
      PathPosition start, PathPosition target, EnvironmentContext environmentContext) {
    final PathPosition effectiveStart = start.floor();
    final PathPosition effectiveTarget = target.floor();

    final AtomicBoolean abortFlag = new AtomicBoolean(false);

    CompletableFuture<PathfinderResult> future;
    if (pathfinderConfiguration.isAsync()) {
      future =
          CompletableFuture.supplyAsync(
              () ->
                  executePathingAlgorithm(
                      effectiveStart, effectiveTarget, environmentContext, abortFlag),
              executorService);
    } else {
      future =
          CompletableFuture.completedFuture(
              executePathingAlgorithm(
                  effectiveStart, effectiveTarget, environmentContext, abortFlag));
    }

    return new PathfindingSearchImpl(future, () -> abortFlag.set(true));
  }

  private PathfinderResult executePathingAlgorithm(
      PathPosition start,
      PathPosition target,
      EnvironmentContext environmentContext,
      AtomicBoolean abortFlag) {
    int expectedNodes = estimateInitialHeapCapacity(start, target);

    SearchContext searchContext =
        new SearchContextImpl(
            start,
            target,
            this.pathfinderConfiguration,
            this.navigationPointProvider,
            environmentContext);

    List<Processor> processors = this.processors;

    try {
      for (Processor processor : processors) {
        processor.initializeSearch(searchContext);
      }

      Node startNode = createStartNode(start, target);

      final EvaluationContext startNodeContext =
          new EvaluationContextImpl(
              searchContext, startNode, null, pathfinderConfiguration.getHeuristicStrategy());

      if (!this.validationProcessors.isEmpty()) {
        final boolean isStartNodeInvalid =
            this.validationProcessors.stream()
                .anyMatch(validator -> !validator.isValid(startNodeContext));

        if (isStartNodeInvalid) {
          return new PathfinderResultImpl(
              PathState.FAILED, new PathImpl(start, target, EMPTY_PATH_POSITIONS));
        }
      }

      S state = createSearchState(start, expectedNodes);

      double startKey = calculateHeapKey(startNode, startNode.getFCost());
      state.insert(startNode, startKey);

      final List<PathfinderHook> hookSnapshot;
      synchronized (pathfinderHooks) {
        hookSnapshot =
            pathfinderHooks.isEmpty() ? Collections.emptyList() : new ArrayList<>(pathfinderHooks);
      }

      if (!hookSnapshot.isEmpty()) {
        PathfindingContext startContext =
            new PathfindingContext(
                startNode.getPosition(), Depth.of(0), target, environmentContext);
        for (PathfinderHook hook : hookSnapshot) {
          hook.onPathfindingStart(startContext);
        }
      }

      int iteration = 0;
      Node bestFallbackNode = startNode;

      while (state.hasOpenNodes() && iteration < pathfinderConfiguration.getMaxIterations()) {
        if (abortFlag.get()) {
          return new PathfinderResultImpl(
              PathState.ABORTED, new PathImpl(start, target, EMPTY_PATH_POSITIONS));
        }

        iteration++;

        Node currentNode = state.extractBest();
        state.markExpanded(currentNode);

        if (!hookSnapshot.isEmpty()) {
          PathfindingContext hookContext =
              new PathfindingContext(
                  currentNode.getPosition(), Depth.of(iteration), target, environmentContext);
          for (PathfinderHook hook : hookSnapshot) {
            hook.onPathfindingStep(hookContext);
          }
        }

        if (currentNode.getHeuristic() < bestFallbackNode.getHeuristic()) {
          bestFallbackNode = currentNode;
        }

        if (hasReachedPathLengthLimit(currentNode)) {
          return new PathfinderResultImpl(
              PathState.LENGTH_LIMITED, reconstructPath(start, target, currentNode));
        }

        if (currentNode.isTarget(target)) {
          return new PathfinderResultImpl(
              PathState.FOUND, reconstructPath(start, target, currentNode));
        }

        processSuccessors(start, target, currentNode, state, searchContext);
      }

      return determinePostLoopResult(iteration, start, target, bestFallbackNode);

    } catch (RuntimeException e) {
      System.err.println("An exception occurred during pathfinding; returning FAILED result:");
      e.printStackTrace();
      return new PathfinderResultImpl(
          PathState.FAILED, new PathImpl(start, target, EMPTY_PATH_POSITIONS));
    } finally {
      for (Processor processor : processors) {
        try {
          processor.finalizeSearch(searchContext);
        } catch (Exception e) {
          System.err.println("An exception occurred during pathfinding finalization:");
          e.printStackTrace();
        }
      }
    }
  }

  double calculateHeapKey(Node neighbor, double fCost) {
    if (!Double.isFinite(fCost)) {
      throw new IllegalStateException(
          "Non-finite F-cost "
              + fCost
              + " for node at "
              + neighbor.getPosition()
              + "; a custom IHeuristicStrategy or CostProcessor likely returned NaN or Infinity");
    }

    double heuristic = neighbor.getHeuristic();
    double tieBreaker = TIE_BREAKER_WEIGHT * (heuristic / (Math.abs(fCost) + 1));
    return fCost - tieBreaker;
  }

  private int estimateInitialHeapCapacity(PathPosition start, PathPosition target) {
    int branching = Math.max(1, Iterables.size(neighborStrategy.getOffsets(start)));
    return computeInitialHeapCapacity(
        start, target, branching, pathfinderConfiguration.getMaxIterations());
  }

  protected Node createStartNode(PathPosition startPos, PathPosition targetPos) {
    return new Node(
        startPos,
        startPos,
        targetPos,
        pathfinderConfiguration.getHeuristicWeights(),
        pathfinderConfiguration.getHeuristicStrategy(),
        0);
  }

  private boolean hasReachedPathLengthLimit(Node currentNode) {
    int maxLength = pathfinderConfiguration.getMaxLength();
    return maxLength > 0 && currentNode.getDepth() >= maxLength;
  }

  private PathfinderResult determinePostLoopResult(
      int iterations, PathPosition start, PathPosition target, Node fallbackNode) {
    if (iterations >= pathfinderConfiguration.getMaxIterations()) {
      return new PathfinderResultImpl(
          PathState.MAX_ITERATIONS_REACHED, reconstructPath(start, target, fallbackNode));
    }

    if (pathfinderConfiguration.isFallback()) {
      return new PathfinderResultImpl(
          PathState.FALLBACK, reconstructPath(start, target, fallbackNode));
    }

    return new PathfinderResultImpl(
        PathState.FAILED, new PathImpl(start, target, EMPTY_PATH_POSITIONS));
  }

  protected Path reconstructPath(PathPosition start, PathPosition target, Node endNode) {
    if (endNode.getParent() == null && endNode.getDepth() == 0) {
      return new PathImpl(start, target, Collections.singletonList(endNode.getPosition()));
    }
    List<PathPosition> pathPositions = tracePathPositionsFromNode(endNode);
    return new PathImpl(start, target, pathPositions);
  }

  private List<PathPosition> tracePathPositionsFromNode(Node leafNode) {
    List<PathPosition> path = new ArrayList<>();
    Node currentNode = leafNode;
    while (currentNode != null) {
      path.add(currentNode.getPosition());
      currentNode = currentNode.getParent();
    }
    Collections.reverse(path);
    return path;
  }

  protected abstract S createSearchState(PathPosition start, int expectedNodes);

  protected abstract void processSuccessors(
      PathPosition requestStart,
      PathPosition requestTarget,
      Node currentNode,
      S state,
      SearchContext searchContext);
}
