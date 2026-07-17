package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.SearchContext;

public interface Processor {
  default void initializeSearch(SearchContext context) {
  }

  default void finalizeSearch(SearchContext context) {
  }
}
