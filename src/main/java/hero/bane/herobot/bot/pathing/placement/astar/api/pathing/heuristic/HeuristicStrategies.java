package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.heuristic;

public class HeuristicStrategies {
  public static final IHeuristicStrategy LINEAR = new LinearHeuristicStrategy();
  public static final IHeuristicStrategy SQUARED = new SquaredHeuristicStrategy();

  private HeuristicStrategies() {}
}
