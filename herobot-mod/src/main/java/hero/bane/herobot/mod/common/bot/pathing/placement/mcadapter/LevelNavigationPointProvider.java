package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext;
import de.bsommerfeld.pathetic.api.provider.NavigationPoint;
import de.bsommerfeld.pathetic.api.provider.NavigationPointProvider;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

public final class LevelNavigationPointProvider implements NavigationPointProvider {
    public static final LevelNavigationPointProvider INSTANCE = new LevelNavigationPointProvider();

    private static final NavigationPoint TRAVERSABLE = () -> true;

    private LevelNavigationPointProvider() {}

    @Override
    public NavigationPoint getNavigationPoint(PathPosition position, EnvironmentContext environmentContext) {
        return TRAVERSABLE;
    }
}
