package hero.bane.herobot.bot.pathing.placement.astar.pathfinder;

import hero.bane.herobot.bot.pathing.placement.astar.Node;

interface SearchState {
  boolean hasOpenNodes();

  void insert(Node node, double heapKey);

  Node extractBest();

  void markExpanded(Node node);
}
