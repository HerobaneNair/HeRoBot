package hero.bane.herobot.mod.common.bot.pathing.placement.astar.factory;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.factory.PathfinderFactory;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.Pathfinder;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.configuration.PathfinderConfiguration;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.pathfinder.AStarPathfinder;

public class AStarPathfinderFactory implements PathfinderFactory {
    @Deprecated
    @Override
    public Pathfinder createPathfinder() {
        return new AStarPathfinder(
            PathfinderConfiguration.builder()
                .provider((position, environmentContext) -> () -> true)
                .build());
    }

    @Override
    public Pathfinder createPathfinder(PathfinderConfiguration configuration) {
        return new AStarPathfinder(configuration);
    }
}
