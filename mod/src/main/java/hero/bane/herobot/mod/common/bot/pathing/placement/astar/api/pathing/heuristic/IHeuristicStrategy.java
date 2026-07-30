package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.heuristic;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathPosition;

public interface IHeuristicStrategy {
    double calculate(HeuristicContext heuristicContext);

    double calculateTransitionCost(PathPosition from, PathPosition to);
}