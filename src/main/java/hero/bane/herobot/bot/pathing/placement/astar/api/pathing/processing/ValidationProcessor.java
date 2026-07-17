package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.processing.context.EvaluationContext;

public interface ValidationProcessor extends Processor {
  boolean isValid(EvaluationContext context);
}
