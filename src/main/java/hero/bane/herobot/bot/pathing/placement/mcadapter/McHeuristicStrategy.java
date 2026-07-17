package hero.bane.herobot.bot.pathing.placement.mcadapter;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.HeuristicContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic.IHeuristicStrategy;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;

public final class McHeuristicStrategy implements IHeuristicStrategy {
    public static final McHeuristicStrategy INSTANCE = new McHeuristicStrategy();

    @Override
    public double calculate(HeuristicContext context) {
        PathPosition current = context.position();
        PathPosition target = context.targetPosition();
        double dx = current.getCenteredX() - target.getCenteredX();
        double dy = current.getCenteredY() - target.getCenteredY();
        double dz = current.getCenteredZ() - target.getCenteredZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public double calculateTransitionCost(PathPosition from, PathPosition to) {
        return 0.0;
    }
}
