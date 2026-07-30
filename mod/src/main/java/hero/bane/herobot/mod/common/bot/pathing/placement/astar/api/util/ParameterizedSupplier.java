package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.util;

@FunctionalInterface
public interface ParameterizedSupplier<T> {
  T accept(T value);
}
