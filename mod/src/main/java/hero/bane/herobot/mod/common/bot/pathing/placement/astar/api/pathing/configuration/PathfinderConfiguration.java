package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.INeighborStrategy;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.NeighborStrategies;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic.HeuristicStrategies;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic.HeuristicWeights;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic.IHeuristicStrategy;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.hook.PathfinderHook;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.CostProcessor;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.ValidationProcessor;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PathfinderConfiguration {
  private final int maxIterations;

  private final int maxLength;

  private final boolean async;

  private final boolean fallback;

  private final NavigationPointProvider provider;

  private final HeuristicWeights heuristicWeights;

  private final List<ValidationProcessor> validationProcessors;

  private final List<CostProcessor> costProcessors;

  private final INeighborStrategy neighborStrategy;

  private final int gridCellSize;

  private final int bloomFilterSize;

  private final double bloomFilterFpp;

  private final IHeuristicStrategy heuristicStrategy;

  private final boolean reopenClosedNodes;

  private final List<PathfinderHook> pathfindingHooks;

  private final ExecutorService executorService;

  private PathfinderConfiguration(
      int maxIterations,
      int maxLength,
      boolean async,
      boolean fallback,
      NavigationPointProvider provider,
      HeuristicWeights heuristicWeights,
      List<ValidationProcessor> validationProcessors,
      List<CostProcessor> costProcessors,
      INeighborStrategy neighborStrategy,
      int gridCellSize,
      int bloomFilterSize,
      double bloomFilterFpp,
      IHeuristicStrategy heuristicStrategy,
      boolean reopenClosedNodes,
      List<PathfinderHook> pathfindingHooks,
      ExecutorService executorService) {
    this.maxIterations = maxIterations;
    this.maxLength = maxLength;
    this.async = async;
    this.fallback = fallback;
    this.provider = provider;
    this.heuristicWeights = heuristicWeights;
    this.validationProcessors = Collections.unmodifiableList(validationProcessors);
    this.costProcessors = Collections.unmodifiableList(costProcessors);
    this.neighborStrategy = neighborStrategy;
    this.gridCellSize = gridCellSize;
    this.bloomFilterSize = bloomFilterSize;
    this.bloomFilterFpp = bloomFilterFpp;
    this.heuristicStrategy = heuristicStrategy;
    this.reopenClosedNodes = reopenClosedNodes;
    this.pathfindingHooks = Collections.unmodifiableList(pathfindingHooks);
    this.executorService = executorService;
  }

  public static PathfinderConfiguration deepCopy(PathfinderConfiguration pathfinderConfiguration) {
    PathfinderConfigurationBuilder builder =
        builder()
            .maxIterations(pathfinderConfiguration.maxIterations)
            .maxLength(pathfinderConfiguration.maxLength)
            .async(pathfinderConfiguration.async)
            .fallback(pathfinderConfiguration.fallback)
            .provider(pathfinderConfiguration.provider)
            .heuristicWeights(pathfinderConfiguration.heuristicWeights)
            .validationProcessors(new ArrayList<>(pathfinderConfiguration.validationProcessors))
            .costProcessor(new ArrayList<>(pathfinderConfiguration.costProcessors))
            .neighborStrategy(pathfinderConfiguration.neighborStrategy)
            .gridCellSize(pathfinderConfiguration.gridCellSize)
            .bloomFilterSize(pathfinderConfiguration.bloomFilterSize)
            .bloomFilterFpp(pathfinderConfiguration.bloomFilterFpp)
            .heuristicStrategy(pathfinderConfiguration.heuristicStrategy)
            .reopenClosedNodes(pathfinderConfiguration.reopenClosedNodes)
            .pathfindingHooks(new ArrayList<>(pathfinderConfiguration.pathfindingHooks));
    if (pathfinderConfiguration.executorService != null) {
      builder.executorService(pathfinderConfiguration.executorService);
    }
    return builder.build();
  }

  public static PathfinderConfigurationBuilder builder() {
    return new PathfinderConfigurationBuilder();
  }

  public int getMaxIterations() {
    return this.maxIterations;
  }

  public int getMaxLength() {
    return this.maxLength;
  }

  public boolean isAsync() {
    return this.async;
  }

  public boolean isFallback() {
    return this.fallback;
  }

  public NavigationPointProvider getProvider() {
    return provider;
  }

  public HeuristicWeights getHeuristicWeights() {
    return this.heuristicWeights;
  }

  public List<CostProcessor> getNodeCostProcessors() {
    return costProcessors;
  }

  public List<ValidationProcessor> getNodeValidationProcessors() {
    return validationProcessors;
  }

  public INeighborStrategy getNeighborStrategy() {
    return neighborStrategy;
  }

  @Deprecated
  public int getGridCellSize() {
    return gridCellSize;
  }

  @Deprecated
  public int getBloomFilterSize() {
    return bloomFilterSize;
  }

  @Deprecated
  public double getBloomFilterFpp() {
    return bloomFilterFpp;
  }

  public IHeuristicStrategy getHeuristicStrategy() {
    return heuristicStrategy;
  }

  public boolean shouldReopenClosedNodes() {
    return reopenClosedNodes;
  }

  public List<PathfinderHook> pathfindingHooks() {
    return pathfindingHooks;
  }

  public ExecutorService executorService() {
    return executorService;
  }

  @Override
  public String toString() {
    return "PathfinderConfiguration{"
        + "maxIterations="
        + maxIterations
        + ", maxLength="
        + maxLength
        + ", async="
        + async
        + ", fallback="
        + fallback
        + ", provider="
        + provider
        + ", heuristicWeights="
        + heuristicWeights
        + ", nodeValidationProcessors="
        + validationProcessors
        + ", nodeCostProcessors="
        + costProcessors
        + ", neighborStrategy="
        + neighborStrategy
        + ", gridCellSize="
        + gridCellSize
        + ", bloomFilterSize="
        + bloomFilterSize
        + ", bloomFilterFpp="
        + bloomFilterFpp
        + ", heuristicMode="
        + heuristicStrategy
        + ", reopenClosedNodes="
        + reopenClosedNodes
        + ", pathfindingHooks="
        + pathfindingHooks
        + ", executorService="
        + executorService
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PathfinderConfiguration that = (PathfinderConfiguration) o;
    return maxIterations == that.maxIterations
        && maxLength == that.maxLength
        && async == that.async
        && fallback == that.fallback
        && gridCellSize == that.gridCellSize
        && bloomFilterSize == that.bloomFilterSize
        && Double.compare(that.bloomFilterFpp, bloomFilterFpp) == 0
        && reopenClosedNodes == that.reopenClosedNodes
        && Objects.equals(provider, that.provider)
        && Objects.equals(heuristicWeights, that.heuristicWeights)
        && Objects.equals(validationProcessors, that.validationProcessors)
        && Objects.equals(costProcessors, that.costProcessors)
        && Objects.equals(neighborStrategy, that.neighborStrategy)
        && heuristicStrategy == that.heuristicStrategy
        && Objects.equals(pathfindingHooks, that.pathfindingHooks)
        && Objects.equals(executorService, that.executorService);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        maxIterations,
        maxLength,
        async,
        fallback,
        provider,
        heuristicWeights,
        validationProcessors,
        costProcessors,
        neighborStrategy,
        gridCellSize,
        bloomFilterSize,
        bloomFilterFpp,
        heuristicStrategy,
        reopenClosedNodes,
        pathfindingHooks,
        executorService
      );
  }

  public static class PathfinderConfigurationBuilder {
    private int maxIterations = 5000;
    private int maxLength;
    private boolean async;
    private boolean fallback = true;
    private NavigationPointProvider provider;
    private HeuristicWeights heuristicWeights = HeuristicWeights.DEFAULT_WEIGHTS;
    private List<ValidationProcessor> validationProcessors = Collections.emptyList();
    private List<CostProcessor> costProcessors = Collections.emptyList();
    private INeighborStrategy neighborStrategy = NeighborStrategies.VERTICAL_AND_HORIZONTAL;
    private int gridCellSize = 12;
    private int bloomFilterSize = 1000;
    private double bloomFilterFpp = 0.01;
    private IHeuristicStrategy heuristicStrategy = HeuristicStrategies.LINEAR;
    private boolean reopenClosedNodes = false;
    private List<PathfinderHook> pathfindingHooks = Collections.emptyList();
    private ExecutorService executorService = null;

    PathfinderConfigurationBuilder() {}

    public PathfinderConfiguration.PathfinderConfigurationBuilder maxIterations(int maxIterations) {
      if (maxIterations <= 0) {
        throw new IllegalArgumentException(
            "maxIterations must be > 0, was " + maxIterations);
      }
      this.maxIterations = maxIterations;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder maxLength(int maxLength) {
      if (maxLength < 0) {
        throw new IllegalArgumentException(
            "maxLength must be >= 0 (0 = unlimited), was " + maxLength);
      }
      this.maxLength = maxLength;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder async(boolean async) {
      this.async = async;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder fallback(
        boolean allowingFallback) {
      this.fallback = allowingFallback;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder provider(
        NavigationPointProvider provider) {
      this.provider = Objects.requireNonNull(provider, "provider must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder heuristicWeights(
        HeuristicWeights heuristicWeights) {
      this.heuristicWeights =
          Objects.requireNonNull(heuristicWeights, "heuristicWeights must not be null");
      return this;
    }

    @Deprecated
    public PathfinderConfiguration.PathfinderConfigurationBuilder nodeValidationProcessors(
        List<ValidationProcessor> validationProcessors) {
      this.validationProcessors =
          Objects.requireNonNull(validationProcessors, "validationProcessors must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder validationProcessors(
        List<ValidationProcessor> validationProcessors) {
      this.validationProcessors =
          Objects.requireNonNull(validationProcessors, "validationProcessors must not be null");
      return this;
    }

    @Deprecated
    public PathfinderConfiguration.PathfinderConfigurationBuilder nodeCostProcessors(
        List<CostProcessor> costProcessors) {
      this.costProcessors =
          Objects.requireNonNull(costProcessors, "costProcessors must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder costProcessor(
        List<CostProcessor> costProcessors) {
      this.costProcessors =
          Objects.requireNonNull(costProcessors, "costProcessors must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder neighborStrategy(
        INeighborStrategy neighborStrategy) {
      this.neighborStrategy =
          Objects.requireNonNull(neighborStrategy, "neighborStrategy must not be null");
      return this;
    }

    @Deprecated
    public PathfinderConfiguration.PathfinderConfigurationBuilder gridCellSize(int gridCellSize) {
      if (gridCellSize <= 0) {
        throw new IllegalArgumentException(
            "gridCellSize must be > 0, was " + gridCellSize);
      }
      this.gridCellSize = gridCellSize;
      return this;
    }

    @Deprecated
    public PathfinderConfiguration.PathfinderConfigurationBuilder bloomFilterSize(
        int bloomFilterSize) {
      if (bloomFilterSize <= 0) {
        throw new IllegalArgumentException(
            "bloomFilterSize must be > 0, was " + bloomFilterSize);
      }
      this.bloomFilterSize = bloomFilterSize;
      return this;
    }

    @Deprecated
    public PathfinderConfiguration.PathfinderConfigurationBuilder bloomFilterFpp(
        double bloomFilterFpp) {
      if (!(bloomFilterFpp > 0.0 && bloomFilterFpp < 1.0)) {
        throw new IllegalArgumentException(
            "bloomFilterFpp must be in (0.0, 1.0), was " + bloomFilterFpp);
      }
      this.bloomFilterFpp = bloomFilterFpp;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder heuristicStrategy(
        IHeuristicStrategy heuristicStrategy) {
      this.heuristicStrategy =
          Objects.requireNonNull(heuristicStrategy, "heuristicStrategy must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder reopenClosedNodes(
        boolean reopenClosedNodes) {
      this.reopenClosedNodes = reopenClosedNodes;
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder pathfindingHooks(
        List<PathfinderHook> pathfindingHooks) {
      this.pathfindingHooks =
          Objects.requireNonNull(pathfindingHooks, "pathfindingHooks must not be null");
      return this;
    }

    public PathfinderConfiguration.PathfinderConfigurationBuilder executorService(
        ExecutorService executorService) {
      this.executorService =
          Objects.requireNonNull(executorService, "executorService must not be null");
      return this;
    }

    public PathfinderConfiguration build() {
      if (this.provider == null) {
        throw new IllegalStateException(
            "A NavigationPointProvider must be set via provider(...) before build()");
      }

      ExecutorService resolvedExecutor = this.executorService;
      if (resolvedExecutor == null && this.async) {
        resolvedExecutor = SharedAsyncPathfinderExecutorService.get();
      }
      return new PathfinderConfiguration(
          this.maxIterations,
          this.maxLength,
          this.async,
          this.fallback,
          this.provider,
          this.heuristicWeights,
          this.validationProcessors,
          this.costProcessors,
          this.neighborStrategy,
          this.gridCellSize,
          this.bloomFilterSize,
          this.bloomFilterFpp,
          this.heuristicStrategy,
          this.reopenClosedNodes,
          this.pathfindingHooks,
          resolvedExecutor);
    }
  }
}
