package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.Map;

public interface EvaluationContext {
  PathPosition getCurrentPathPosition();

  PathPosition getPreviousPathPosition();

  int getCurrentNodeDepth();

  double getCurrentNodeHeuristicValue();

  double getPathCostToPreviousPosition();

  double getBaseTransitionCost();

  SearchContext getSearchContext();

  default PathfinderConfiguration getPathfinderConfiguration() {
    return getSearchContext().getPathfinderConfiguration();
  }

  default NavigationPointProvider getNavigationPointProvider() {
    return getSearchContext().getNavigationPointProvider();
  }

  default Map<String, Object> getSharedData() {
    return getSearchContext().getSharedData();
  }

  default PathPosition getStartPathPosition() {
    return getSearchContext().getStartPathPosition();
  }

  default PathPosition getTargetPathPosition() {
    return getSearchContext().getTargetPathPosition();
  }

  default EnvironmentContext getEnvironmentContext() {
    return getSearchContext().getEnvironmentContext();
  }
}
