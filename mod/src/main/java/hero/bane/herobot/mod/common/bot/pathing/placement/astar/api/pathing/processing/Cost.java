package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.processing;

public final class Cost {
  public static final Cost ZERO = new Cost(0.0);

  private final double value;

  private Cost(double value) {
    this.value = value;
  }

  public static Cost of(double value) {
    if (!Double.isFinite(value) || value < 0)
      throw new IllegalArgumentException("Cost must be a finite non-negative number, was " + value);

    return new Cost(value);
  }

  @Deprecated
  public double getValue() {
    return value;
  }

  public double value() {
    return this.value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Cost cost = (Cost) o;
    return Double.compare(cost.value, value) == 0;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(value);
  }

  @Override
  public String toString() {
    return "Cost{" + "value=" + value + '}';
  }
}
