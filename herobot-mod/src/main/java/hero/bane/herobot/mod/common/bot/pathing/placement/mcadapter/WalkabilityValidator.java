package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import de.bsommerfeld.pathetic.api.pathing.processing.ValidationProcessor;
import de.bsommerfeld.pathetic.api.pathing.processing.context.EvaluationContext;
import de.bsommerfeld.pathetic.api.wrapper.PathPosition;

public final class WalkabilityValidator implements ValidationProcessor {
    public static final WalkabilityValidator INSTANCE = new WalkabilityValidator();

    @Override
    public boolean isValid(EvaluationContext context) {
        EnvironmentContext env = (EnvironmentContext) context.getEnvironmentContext();
        if (env == null) return true;

        PathPosition prev = context.getPreviousPathPosition();
        if (prev == null) return true;

        PathPosition cur = context.getCurrentPathPosition();

        double cost = MoveEvaluator.cachedTransitionCost(
                env.blocks(),
                prev.getFlooredX(), prev.getFlooredY(), prev.getFlooredZ(),
                cur.getFlooredX(), cur.getFlooredY(), cur.getFlooredZ(),
                env.maxJumpHeight(), env.maxFallDistance());

        return Double.isFinite(cost);
    }
}
