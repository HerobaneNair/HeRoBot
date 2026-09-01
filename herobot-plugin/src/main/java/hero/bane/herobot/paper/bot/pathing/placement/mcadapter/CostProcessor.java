package hero.bane.herobot.paper.bot.pathing.placement.mcadapter;

import de.bsommerfeld.pathetic.api.pathing.processing.Cost;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

public final class CostProcessor implements de.bsommerfeld.pathetic.api.pathing.processing.CostProcessor {
    public static final CostProcessor INSTANCE = new CostProcessor();

    @Override
    public Cost calculateCostContribution(EvaluationContext context) {
        PathPosition prev = context.getPreviousPathPosition();
        if (prev == null) return Cost.ZERO;

        EnvironmentContext env = (EnvironmentContext) context.getEnvironmentContext();
        if (env == null) return Cost.ZERO;

        PathPosition cur = context.getCurrentPathPosition();

        double cost = MoveEvaluator.cachedTransitionCost(
                env.blocks(),
                prev.getFlooredX(), prev.getFlooredY(), prev.getFlooredZ(),
                cur.getFlooredX(), cur.getFlooredY(), cur.getFlooredZ(),
                env.maxJumpHeight(), env.maxFallDistance());

        if (!Double.isFinite(cost) || cost <= 0.0) return Cost.ZERO;
        return Cost.of(cost);
    }
}
