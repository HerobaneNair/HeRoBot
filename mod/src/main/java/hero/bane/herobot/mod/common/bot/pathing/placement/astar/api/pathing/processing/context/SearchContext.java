package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.Processor;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.Map;

public interface SearchContext {
  PathPosition getStartPathPosition();

  PathPosition getTargetPathPosition();

  PathfinderConfiguration getPathfinderConfiguration();

  NavigationPointProvider getNavigationPointProvider();

  Map<String, Object> getSharedData();

  EnvironmentContext getEnvironmentContext();
}
