package hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap;

public interface Resizable {
  void ensureCapacity();

  int capacity();
}
