package hero.bane.herobot.bot.pathing.placement.astar.api.factory;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.Pathfinder;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;

public interface PathfinderInitializer {
    void initialize(Pathfinder pathfinder, PathfinderConfiguration configuration);
}
