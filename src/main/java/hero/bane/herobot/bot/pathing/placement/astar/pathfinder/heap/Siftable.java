package hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap;

public interface Siftable {
  void siftUp(int index);

  void siftDown(int index);
}
