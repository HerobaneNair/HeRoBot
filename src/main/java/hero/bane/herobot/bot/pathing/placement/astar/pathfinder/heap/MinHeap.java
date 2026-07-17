package hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap;

public interface MinHeap {
  boolean isEmpty();

  int size();

  void clear();

  boolean contains(long nodeId);

  double cost(long nodeId);

  void insertOrUpdate(long nodeId, double cost);

  static void requireOrderableCost(long nodeId, double cost) {
    if (Double.isNaN(cost)) {
      throw new IllegalArgumentException("Heap cost must not be NaN (node " + nodeId + ")");
    }
  }

  long extractMin();
}
