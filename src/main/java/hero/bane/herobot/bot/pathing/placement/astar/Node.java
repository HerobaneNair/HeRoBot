package hero.bane.herobot.bot.pathing.placement.astar;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.HeuristicContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.HeuristicWeights;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.IHeuristicStrategy;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.Objects;

public class Node {
  private final PathPosition position;
  private final int depth;
  private final double hCost;

  private double gCost;
  private Node parent;

  public Node(
      PathPosition position,
      PathPosition start,
      PathPosition target,
      HeuristicWeights heuristicWeights,
      IHeuristicStrategy heuristicStrategy,
      int depth) {
    this.position = position;
    this.depth = depth;

    this.hCost =
        heuristicStrategy.calculate(
            new HeuristicContext(position, start, target, heuristicWeights));
  }

  public PathPosition getPosition() {
    return position;
  }

  public double getHeuristic() {
    return hCost;
  }

  public Node getParent() {
    return parent;
  }

  public int getDepth() {
    return depth;
  }

  public void setGCost(double gCost) {
    this.gCost = gCost;
  }

  public void setParent(Node parent) {
    this.parent = parent;
  }

  public boolean isTarget(PathPosition target) {
    return this.position.equals(target);
  }

  public double getFCost() {
    return getGCost() + getHeuristic();
  }

  public double getGCost() {
    if (this.parent == null) return 0.0;
    return this.gCost;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Node node = (Node) o;
    return Objects.equals(position, node.position);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(position);
  }
}
