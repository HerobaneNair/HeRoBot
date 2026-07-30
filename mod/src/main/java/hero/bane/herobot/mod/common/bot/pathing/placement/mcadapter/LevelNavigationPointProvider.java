package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPoint;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.provider.NavigationPointProvider;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;

public final class LevelNavigationPointProvider implements NavigationPointProvider {
    public static final LevelNavigationPointProvider INSTANCE = new LevelNavigationPointProvider();

    private static final NavigationPoint TRAVERSABLE = () -> true;

    private LevelNavigationPointProvider() {}

    @Override
    public NavigationPoint getNavigationPoint(PathPosition position, EnvironmentContext environmentContext) {
        return TRAVERSABLE;
    }
}
