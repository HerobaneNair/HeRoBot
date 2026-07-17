package hero.bane.herobot.bot.pathing.placement.astar.api.factory;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.Pathfinder;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;

public interface PathfinderFactory {
    @Deprecated
    Pathfinder createPathfinder();

    Pathfinder createPathfinder(PathfinderConfiguration configuration);

    default Pathfinder createPathfinder(PathfinderConfiguration configuration, PathfinderInitializer initializer) {
        Pathfinder pathfinder = createPathfinder(configuration);
        initializer.initialize(pathfinder, configuration);
        return pathfinder;
    }
}
