package hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap;

public interface Siftable {
  void siftUp(int index);

  void siftDown(int index);
}
