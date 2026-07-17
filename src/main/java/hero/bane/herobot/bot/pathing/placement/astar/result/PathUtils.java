package hero.bane.herobot.bot.pathing.placement.astar.result;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.result.Path;
import hero.bane.herobot.bot.pathing.placement.astar.api.util.NumberUtils;
import hero.bane.herobot.bot.pathing.placement.astar.api.util.ParameterizedSupplier;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.ArrayDeque;
import java.util.Deque;

@Deprecated
public final class PathUtils {
  private PathUtils() {
    throw new AssertionError("Utility class - instantiation not allowed");
  }

  public static Path interpolate(Path path, double resolution) {
    if (resolution <= 0) {
      throw new IllegalArgumentException("Resolution must be > 0");
    }

    Deque<PathPosition> result = new ArrayDeque<>();
    PathPosition previous = null;

    for (PathPosition current : path) {
      if (previous != null) {
        interpolateSegment(previous, current, resolution, result);
      }
      result.addLast(current);
      previous = current;
    }

    return buildPath(result);
  }

  public static Path simplify(Path path, double epsilon) {
    validateEpsilon(epsilon);

    Deque<PathPosition> result = new ArrayDeque<>();
    int index = 0;
    int stride = Math.max(1, (int) Math.round(1.0 / epsilon));

    for (PathPosition pos : path) {
      if (index % stride == 0) {
        result.addLast(pos);
      }
      index++;
    }

    return buildPath(result);
  }

  public static Path join(Path first, Path second) {
    if (first.length() == 0) return second;
    if (second.length() == 0) return first;

    Deque<PathPosition> result = new ArrayDeque<>();
    for (PathPosition p : first) result.addLast(p);
    for (PathPosition p : second) result.addLast(p);

    return buildPath(result);
  }

  public static Path trim(Path path, int maxLength) {
    if (maxLength <= 0) {
      throw new IllegalArgumentException("maxLength must be > 0");
    }
    if (path.length() <= maxLength) {
      return path;
    }

    Deque<PathPosition> result = new ArrayDeque<>();
    int count = 0;
    for (PathPosition p : path) {
      result.addLast(p);
      if (++count >= maxLength) break;
    }

    return buildPath(result);
  }

  public static Path mutatePositions(Path path, ParameterizedSupplier<PathPosition> mutator) {
    Deque<PathPosition> result = new ArrayDeque<>(path.length());

    for (PathPosition pos : path) {
      result.addLast(mutator.accept(pos));
    }

    return buildPath(result);
  }

  private static void interpolateSegment(
      PathPosition start, PathPosition end, double resolution, Deque<PathPosition> result) {
    double distance = start.distance(end);
    int steps = (int) Math.ceil(distance / resolution);

    for (int i = 1; i < steps; i++) {
      double progress = (double) i / steps;
      result.addLast(interpolate(start, end, progress));
    }
  }

  private static PathPosition interpolate(PathPosition pos1, PathPosition pos2, double progress) {
    double x = NumberUtils.interpolate(pos1.getX(), pos2.getX(), progress);
    double y = NumberUtils.interpolate(pos1.getY(), pos2.getY(), progress);
    double z = NumberUtils.interpolate(pos1.getZ(), pos2.getZ(), progress);
    return new PathPosition(x, y, z);
  }

  private static Path buildPath(Deque<PathPosition> positions) {
    if (positions.isEmpty()) {
      throw new IllegalArgumentException("Cannot build path from empty position list");
    }
    PathImpl path = new PathImpl(positions.peekFirst(), positions.peekLast(), positions);
    return removeDuplicates(path);
  }

  private static Path removeDuplicates(Path path) {
    final double EPS = 1e-12;

    Deque<PathPosition> result = new ArrayDeque<>();
    PathPosition last = null;

    for (PathPosition pos : path) {
      if (last == null || !samePoint(last, pos, EPS)) {
        result.addLast(pos);
        last = pos;
      }
    }

    return new PathImpl(result.peekFirst(), result.peekLast(), result);
  }

  private static boolean samePoint(PathPosition a, PathPosition b, double eps) {
    return Math.abs(a.getX() - b.getX()) <= eps
        && Math.abs(a.getY() - b.getY()) <= eps
        && Math.abs(a.getZ() - b.getZ()) <= eps;
  }

  private static void validateEpsilon(double epsilon) {
    if (epsilon <= 0.0 || epsilon > 1.0) {
      throw new IllegalArgumentException("Epsilon must be in (0.0, 1.0]");
    }
  }
}
