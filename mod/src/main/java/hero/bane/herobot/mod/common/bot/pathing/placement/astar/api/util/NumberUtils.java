package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.util;

public final class NumberUtils {
  private NumberUtils() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static double interpolate(double a, double b, double progress) {
    return a + (b - a) * progress;
  }

  public static double square(double value) {
    return value * value;
  }

  @Deprecated
  public static double sqrt(double input) {
    return Math.sqrt(input);
  }
}
