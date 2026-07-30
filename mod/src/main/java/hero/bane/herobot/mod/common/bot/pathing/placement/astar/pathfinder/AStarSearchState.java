package hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.Node;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.MinHeap;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.impl.QuaternaryPrimitiveMinHeap;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.util.RegionKey;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;

class AStarSearchState implements SearchState {
  static final int NO_ID = -1;

  private static final int MIN_ID_CAPACITY = 16;
  private static final int MAX_INITIAL_ID_CAPACITY = 16384;

  private final MinHeap openSet;

  private final Long2IntOpenHashMap keyToId;
  private int nextId = 0;

  private int lastExtractedId = NO_ID;

  private Node[] openNodes;

  private boolean[] closed;

  private double[] closedGCosts;

  private final boolean reopenEnabled;

  private final int originX;
  private final int originY;
  private final int originZ;

  AStarSearchState(
      PathfinderConfiguration pathfinderConfiguration, PathPosition start, int expectedNodes) {
    this.originX = start.getFlooredX();
    this.originY = start.getFlooredY();
    this.originZ = start.getFlooredZ();
    this.reopenEnabled = pathfinderConfiguration.shouldReopenClosedNodes();

    this.openSet = new QuaternaryPrimitiveMinHeap(expectedNodes);

    int capacity = Math.max(MIN_ID_CAPACITY, Math.min(expectedNodes, MAX_INITIAL_ID_CAPACITY));
    this.keyToId = new Long2IntOpenHashMap(capacity);
    this.keyToId.defaultReturnValue(NO_ID);
    this.openNodes = new Node[capacity];
    this.closed = new boolean[capacity];
    if (reopenEnabled) {
      this.closedGCosts = newGCostArray(capacity);
    }
  }

  @Override
  public boolean hasOpenNodes() {
    return !openSet.isEmpty();
  }

  @Override
  public void insert(Node node, double heapKey) {
    long packed = pack(node.getPosition());
    int id = idOf(packed);
    if (id == NO_ID) {
      id = assignId(packed);
    }
    openSet.insertOrUpdate(id, heapKey);
    openNodes[id] = node;
  }

  @Override
  public Node extractBest() {
    int id = (int) openSet.extractMin();
    Node node = openNodes[id];
    openNodes[id] = null;
    lastExtractedId = id;
    return node;
  }

  @Override
  public void markExpanded(Node node) {
    int id = lastExtractedId;
    closed[id] = true;
    if (reopenEnabled) {
      closedGCosts[id] = node.getGCost();
    }
  }

  long pack(PathPosition position) {
    return RegionKey.pack(
        position.getFlooredX() - originX,
        position.getFlooredY() - originY,
        position.getFlooredZ() - originZ);
  }

  boolean isInRange(PathPosition position) {
    return RegionKey.isInRange(
        position.getFlooredX() - originX,
        position.getFlooredY() - originY,
        position.getFlooredZ() - originZ);
  }

  int idOf(long packedKey) {
    return keyToId.get(packedKey);
  }

  int assignId(long packedKey) {
    int id = nextId++;
    keyToId.put(packedKey, id);
    ensureIdCapacity(id);
    return id;
  }

  Node openNode(int id) {
    return openNodes[id];
  }

  void setOpenNode(int id, Node node) {
    openNodes[id] = node;
  }

  void clearOpenNode(int id) {
    openNodes[id] = null;
  }

  void openInsert(int id, double heapKey) {
    openSet.insertOrUpdate(id, heapKey);
  }

  double openKey(int id) {
    return openSet.cost(id);
  }

  boolean isClosed(int id) {
    return closed[id];
  }

  void markClosed(int id) {
    closed[id] = true;
  }

  double closedGCost(int id) {
    return closedGCosts[id];
  }

  void recordClosedGCost(int id, double gCost) {
    closedGCosts[id] = gCost;
  }

  private void ensureIdCapacity(int id) {
    if (id < openNodes.length) return;

    int newCapacity = Math.max(id + 1, openNodes.length * 2);
    openNodes = Arrays.copyOf(openNodes, newCapacity);
    closed = Arrays.copyOf(closed, newCapacity);
    if (closedGCosts != null) {
      double[] grown = newGCostArray(newCapacity);
      System.arraycopy(closedGCosts, 0, grown, 0, closedGCosts.length);
      closedGCosts = grown;
    }
  }

  private static double[] newGCostArray(int capacity) {
    double[] array = new double[capacity];
    Arrays.fill(array, Double.NaN);
    return array;
  }
}
