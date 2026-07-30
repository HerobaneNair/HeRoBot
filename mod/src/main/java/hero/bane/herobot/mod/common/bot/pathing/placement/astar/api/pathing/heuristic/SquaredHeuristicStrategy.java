package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.PathfindingProgress;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.calc.DistanceCalculator;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;

public class SquaredHeuristicStrategy implements IHeuristicStrategy {
  private static final double EPSILON = 1e-9;
  private static final double D1 = 1.0;
  private static final double D2 = Math.sqrt(2);
  private static final double D3 = Math.sqrt(3);

  private final DistanceCalculator<Double> perpendicularCalc =
      progress -> {
        PathPosition s = progress.startPosition();
        PathPosition c = progress.currentPosition();
        PathPosition t = progress.targetPosition();

        double sx = s.getCenteredX(), sy = s.getCenteredY(), sz = s.getCenteredZ();
        double cx = c.getCenteredX(), cy = c.getCenteredY(), cz = c.getCenteredZ();
        double tx = t.getCenteredX(), ty = t.getCenteredY(), tz = t.getCenteredZ();

        double lineX = tx - sx;
        double lineY = ty - sy;
        double lineZ = tz - sz;
        double lineSq = lineX * lineX + lineY * lineY + lineZ * lineZ;

        if (lineSq < EPSILON) {
          double dx = cx - sx, dy = cy - sy, dz = cz - sz;
          return dx * dx + dy * dy + dz * dz;
        }

        double toX = cx - sx;
        double toY = cy - sy;
        double toZ = cz - sz;

        double crossX = toY * lineZ - toZ * lineY;
        double crossY = toZ * lineX - toX * lineZ;
        double crossZ = toX * lineY - toY * lineX;
        double crossSq = crossX * crossX + crossY * crossY + crossZ * crossZ;

        return crossSq / lineSq;
      };

  private final DistanceCalculator<Double> octileCalc =
      progress -> {
        int dx =
            Math.abs(
                progress.currentPosition().getFlooredX() - progress.targetPosition().getFlooredX());
        int dy =
            Math.abs(
                progress.currentPosition().getFlooredY() - progress.targetPosition().getFlooredY());
        int dz =
            Math.abs(
                progress.currentPosition().getFlooredZ() - progress.targetPosition().getFlooredZ());

        int min = Math.min(Math.min(dx, dy), dz);
        int max = Math.max(Math.max(dx, dy), dz);
        int mid = dx + dy + dz - min - max;

        double octile = (D3 - D2) * min + (D2 - D1) * mid + D1 * max;
        return octile * octile;
      };

  private final DistanceCalculator<Double> manhattanCalc =
      progress -> {
        PathPosition c = progress.currentPosition();
        PathPosition t = progress.targetPosition();

        double manhattan =
            Math.abs(c.getFlooredX() - t.getFlooredX())
                + Math.abs(c.getFlooredY() - t.getFlooredY())
                + Math.abs(c.getFlooredZ() - t.getFlooredZ());
        return manhattan * manhattan;
      };

  private final DistanceCalculator<Double> heightCalc =
      progress -> {
        double dy =
            progress.currentPosition().getFlooredY() - progress.targetPosition().getFlooredY();
        return dy * dy;
      };

  @Override
  public double calculate(HeuristicContext context) {
    PathfindingProgress p = context.getPathfindingProgress();
    HeuristicWeights w = context.heuristicWeights();

    return manhattanCalc.calculate(p) * w.getManhattanWeight()
        + octileCalc.calculate(p) * w.getOctileWeight()
        + perpendicularCalc.calculate(p) * w.getPerpendicularWeight()
        + heightCalc.calculate(p) * w.getHeightWeight();
  }

  @Override
  public double calculateTransitionCost(PathPosition from, PathPosition to) {
    double dx = to.getCenteredX() - from.getCenteredX();
    double dy = to.getCenteredY() - from.getCenteredY();
    double dz = to.getCenteredZ() - from.getCenteredZ();
    return dx * dx + dy * dy + dz * dz;
  }
}
