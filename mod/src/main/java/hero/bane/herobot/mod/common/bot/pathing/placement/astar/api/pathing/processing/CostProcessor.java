package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;

public interface CostProcessor extends Processor {
  Cost calculateCostContribution(EvaluationContext context);
}
