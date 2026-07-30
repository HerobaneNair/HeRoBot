package hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.impl;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.MinHeap;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.Resizable;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.heap.Siftable;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.NoSuchElementException;

public class PrimitiveMinHeap implements MinHeap, Siftable, Resizable {
  private final Long2IntOpenHashMap nodeToIndexMap;
  private long[] nodes;
  private double[] costs;
  private int size = 0;

  public PrimitiveMinHeap(int initialCapacity) {
    this.nodes = new long[initialCapacity + 1];
    this.costs = new double[initialCapacity + 1];

    this.nodeToIndexMap = new Long2IntOpenHashMap(initialCapacity);
    this.nodeToIndexMap.defaultReturnValue(-1);
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    size = 0;
    nodeToIndexMap.clear();
  }

  @Override
  public boolean contains(long packedNode) {
    return nodeToIndexMap.containsKey(packedNode);
  }

  @Override
  public double cost(long packedNode) {
    int index = nodeToIndexMap.get(packedNode);
    if (index == -1) return Double.MAX_VALUE;
    return costs[index];
  }

  @Override
  public void insertOrUpdate(long packedNode, double cost) {
    MinHeap.requireOrderableCost(packedNode, cost);

    int existingIndex = nodeToIndexMap.get(packedNode);

    if (existingIndex != -1) {
      if (cost < costs[existingIndex]) {
        costs[existingIndex] = cost;
        siftUp(existingIndex);
      }
    } else {
      ensureCapacity();

      size++;
      nodes[size] = packedNode;
      costs[size] = cost;
      nodeToIndexMap.put(packedNode, size);
      siftUp(size);
    }
  }

  @Override
  public long extractMin() {
    if (size == 0) throw new NoSuchElementException();

    long minNode = nodes[1];

    nodeToIndexMap.remove(minNode);

    long lastNode = nodes[size];
    double lastCost = costs[size];

    nodes[1] = lastNode;
    costs[1] = lastCost;

    size--;

    if (size > 0) {
      nodeToIndexMap.put(lastNode, 1);
      siftDown(1);
    }

    return minNode;
  }

  @Override
  public int capacity() {
    return nodes.length - 1;
  }

  @Override
  public void ensureCapacity() {
    if (size >= nodes.length - 1) {
      int newCap = nodes.length * 2;
      long[] newNodes = new long[newCap];
      double[] newCosts = new double[newCap];

      System.arraycopy(nodes, 0, newNodes, 0, nodes.length);
      System.arraycopy(costs, 0, newCosts, 0, costs.length);

      this.nodes = newNodes;
      this.costs = newCosts;
    }
  }

  @Override
  public void siftUp(int index) {
    int current = index;
    long nodeToMove = nodes[current];
    double costToMove = costs[current];

    while (current > 1) {
      int parentIndex = current >> 1;
      double parentCost = costs[parentIndex];

      if (costToMove < parentCost) {
        nodes[current] = nodes[parentIndex];
        costs[current] = parentCost;

        nodeToIndexMap.put(nodes[current], current);

        current = parentIndex;
      } else {
        break;
      }
    }

    nodes[current] = nodeToMove;
    costs[current] = costToMove;
    nodeToIndexMap.put(nodeToMove, current);
  }

  @Override
  public void siftDown(int index) {
    int current = index;
    long nodeToMove = nodes[current];
    double costToMove = costs[current];
    int half = size >> 1;

    while (current <= half) {
      int childIndex = current << 1;
      double childCost = costs[childIndex];

      int rightIndex = childIndex + 1;

      if (rightIndex <= size && costs[rightIndex] < childCost) {
        childIndex = rightIndex;
        childCost = costs[rightIndex];
      }

      if (costToMove > childCost) {
        nodes[current] = nodes[childIndex];
        costs[current] = childCost;

        nodeToIndexMap.put(nodes[current], current);

        current = childIndex;
      } else {
        break;
      }
    }

    nodes[current] = nodeToMove;
    costs[current] = costToMove;
    nodeToIndexMap.put(nodeToMove, current);
  }
}
