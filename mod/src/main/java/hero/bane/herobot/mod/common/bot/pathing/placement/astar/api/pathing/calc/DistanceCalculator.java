package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.calc;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.PathfindingProgress;

@FunctionalInterface
public interface DistanceCalculator<M> {
  M calculate(PathfindingProgress progress);
}
