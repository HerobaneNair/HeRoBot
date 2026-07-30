package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.Cost;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;

public final class CostProcessor implements hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.CostProcessor {
    public static final CostProcessor INSTANCE = new CostProcessor();

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        PathPosition prev = context.getPreviousPathPosition();
        if (prev == null) return Cost.ZERO;

        EnvironmentContext env = (EnvironmentContext) context.getEnvironmentContext();
        if (env == null) return Cost.ZERO;

        PathPosition cur = context.getCurrentPathPosition();

        double cost = MoveEvaluator.cachedTransitionCost(
                env.level(), env.settings(),
                prev.getFlooredX(), prev.getFlooredY(), prev.getFlooredZ(),
                cur.getFlooredX(), cur.getFlooredY(), cur.getFlooredZ(),
                env.maxJumpHeight(), env.maxFallDistance());

        if (!Double.isFinite(cost) || cost <= 0.0) return Cost.ZERO;
        return Cost.of(cost);
    }
}
