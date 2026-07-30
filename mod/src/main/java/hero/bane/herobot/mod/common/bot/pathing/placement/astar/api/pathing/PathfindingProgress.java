package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.Objects;

public final class PathfindingProgress {
  private final PathPosition start;
  private final PathPosition current;
  private final PathPosition target;

  public PathfindingProgress(
      PathPosition startPosition, PathPosition currentPosition, PathPosition targetPosition) {
    this.start = startPosition;
    this.current = currentPosition;
    this.target = targetPosition;
  }

  public PathPosition startPosition() {
    return start;
  }

  public PathPosition currentPosition() {
    return current;
  }

  public PathPosition targetPosition() {
    return target;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    PathfindingProgress that = (PathfindingProgress) o;
    return Objects.equals(start, that.start)
        && Objects.equals(current, that.current)
        && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, current, target);
  }

  @Override
  public String toString() {
    return "PathfindingProgress{"
        + "start="
        + start
        + ", current="
        + current
        + ", target="
        + target
        + '}';
  }
}
