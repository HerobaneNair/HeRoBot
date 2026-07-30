package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.result;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;
import java.util.Collection;

public interface Path extends Iterable<PathPosition> {
  int length();

  PathPosition getStart();

  PathPosition getEnd();

  Collection<PathPosition> collect();
}
