package hero.bane.herobot.bot.pathing.placement.astar.api.pathing;

import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathVector;

@FunctionalInterface
public interface INeighborStrategy {
  Iterable<PathVector> getOffsets();

  default Iterable<PathVector> getOffsets(PathPosition currentPosition) {
    return getOffsets();
  }
}
