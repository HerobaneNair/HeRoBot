package hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap.impl;

import hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap.MinHeap;
import hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap.Resizable;
import hero.bane.herobot.bot.pathing.placement.astar.pathfinder.heap.Siftable;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class QuaternaryPrimitiveMinHeap implements MinHeap, Siftable, Resizable {
  private static final int INITIAL_CAPACITY = 1024;

  private int[] heap;

  private double[] costs;

  private int[] idToPos;

  private int size = 0;

  public QuaternaryPrimitiveMinHeap() {
    this(INITIAL_CAPACITY);
  }

  public QuaternaryPrimitiveMinHeap(int initialCapacity) {
    this.heap = new int[initialCapacity];
    this.costs = new double[initialCapacity];
    this.idToPos = new int[initialCapacity];
    Arrays.fill(idToPos, -1);
  }

  @Override
  public void insertOrUpdate(long nodeId, double cost) {
    MinHeap.requireOrderableCost(nodeId, cost);
    requireDenseId(nodeId);

    int nodeIdInt = (int) nodeId;
    ensureNodeIdCapacity(nodeIdInt);
    int pos = idToPos[nodeIdInt];
    if (pos != -1) {
      if (cost < costs[pos]) {
        costs[pos] = cost;
        siftUp(pos);
      }
    } else {
      ensureHeapCapacity();
      costs[size] = cost;
      heap[size] = nodeIdInt;
      idToPos[nodeIdInt] = size;
      siftUp(size++);
    }
  }

  private static void requireDenseId(long nodeId) {
    if ((nodeId & ~0x7FFFFFFFL) != 0) {
      throw new IllegalArgumentException(
          "Node id must be a non-negative int (dense id), was " + nodeId);
    }
  }

  private void ensureNodeIdCapacity(int nodeId) {
    if (nodeId >= idToPos.length) {
      int newCapacity = Math.max(nodeId + 1, idToPos.length * 2);
      int[] newIdToPos = new int[newCapacity];
      Arrays.fill(newIdToPos, -1);
      System.arraycopy(idToPos, 0, newIdToPos, 0, idToPos.length);
      idToPos = newIdToPos;
    }
  }

  @Override
  public int capacity() {
    return heap.length;
  }

  @Override
  public void ensureCapacity() {
    ensureHeapCapacity();
  }

  private void ensureHeapCapacity() {
    if (size >= heap.length) {
      int newCapacity = Math.max(heap.length * 2, 16);
      heap = Arrays.copyOf(heap, newCapacity);
      costs = Arrays.copyOf(costs, newCapacity);
    }
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    size = 0;
    Arrays.fill(idToPos, -1);
  }

  @Override
  public boolean contains(long nodeId) {
    if (nodeId < 0 || nodeId >= idToPos.length) return false;
    return idToPos[(int) nodeId] != -1;
  }

  @Override
  public double cost(long nodeId) {
    if (nodeId < 0 || nodeId >= idToPos.length) return Double.MAX_VALUE;
    int pos = idToPos[(int) nodeId];
    return pos == -1 ? Double.MAX_VALUE : costs[pos];
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public long extractMin() {
    if (size == 0) throw new NoSuchElementException();

    int minId = heap[0];
    idToPos[minId] = -1;
    size--;
    if (size > 0) {
      heap[0] = heap[size];
      costs[0] = costs[size];
      idToPos[heap[0]] = 0;
      siftDown(0);
    }
    return minId;
  }

  @Override
  public void siftUp(int index) {
    int id = heap[index];
    double cost = costs[index];
    while (index > 0) {
      int parent = (index - 1) >>> 2;
      if (cost >= costs[parent]) break;

      heap[index] = heap[parent];
      costs[index] = costs[parent];
      idToPos[heap[index]] = index;
      index = parent;
    }
    heap[index] = id;
    costs[index] = cost;
    idToPos[id] = index;
  }

  @Override
  public void siftDown(int index) {
    int id = heap[index];
    double cost = costs[index];
    while (true) {
      int firstChild = (index << 2) + 1;
      if (firstChild >= size) break;

      int minChild = firstChild;
      double minCost = costs[firstChild];
      int lastChild = Math.min(firstChild + 4, size);

      for (int i = firstChild + 1; i < lastChild; i++) {
        if (costs[i] < minCost) {
          minCost = costs[i];
          minChild = i;
        }
      }

      if (cost <= minCost) break;

      heap[index] = heap[minChild];
      costs[index] = minCost;
      idToPos[heap[index]] = index;
      index = minChild;
    }
    heap[index] = id;
    costs[index] = cost;
    idToPos[id] = index;
  }
}
