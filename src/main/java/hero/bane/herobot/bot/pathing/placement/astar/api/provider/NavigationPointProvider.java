package hero.bane.herobot.bot.pathing.placement.astar.api.provider;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;

public interface NavigationPointProvider {
  default NavigationPoint getNavigationPoint(PathPosition position) {
    return getNavigationPoint(position, null);
  }

  NavigationPoint getNavigationPoint(PathPosition position, EnvironmentContext environmentContext);
}
