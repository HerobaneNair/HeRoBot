package hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap;

public interface Resizable {
  void ensureCapacity();

  int capacity();
}
