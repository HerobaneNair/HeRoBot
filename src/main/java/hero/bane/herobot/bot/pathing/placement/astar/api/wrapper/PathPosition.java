package hero.bane.herobot.bot.pathing.placement.astar.api.wrapper;

import hero.bane.herobot.bot.pathing.placement.astar.api.util.NumberUtils;

public class PathPosition {
  private final double x;
  private final double y;
  private final double z;
  public PathPosition(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public static PathPosition of(double x, double y, double z) {
    return new PathPosition(x, y, z);
  }

  @Deprecated
  public double manhattanDistance(PathPosition otherPosition) {
    return Math.abs(this.getFlooredX() - otherPosition.getFlooredX())
        + Math.abs(this.getFlooredY() - otherPosition.getFlooredY())
        + Math.abs(this.getFlooredZ() - otherPosition.getFlooredZ());
  }

  @Deprecated
  public double octileDistance(PathPosition otherPosition) {
    double dx = Math.abs(this.x - otherPosition.x);
    double dy = Math.abs(this.y - otherPosition.y);
    double dz = Math.abs(this.z - otherPosition.z);

    double smallest = Math.min(Math.min(dx, dz), dy);
    double highest = Math.max(Math.max(dx, dz), dy);
    double mid = Math.max(Math.min(dx, dz), Math.min(Math.max(dx, dz), dy));

    double D1 = 1;
    double D2 = 1.4142135623730951;
    double D3 = 1.7320508075688772;

    return (D3 - D2) * smallest + (D2 - D1) * mid + D1 * highest;
  }

  public double distanceSquared(PathPosition otherPosition) {
    return NumberUtils.square(this.x - otherPosition.x)
        + NumberUtils.square(this.y - otherPosition.y)
        + NumberUtils.square(this.z - otherPosition.z);
  }

  public double distance(PathPosition otherPosition) {
    return Math.sqrt(this.distanceSquared(otherPosition));
  }

  public PathPosition setX(double x) {
    return new PathPosition(x, this.y, this.z);
  }

  public PathPosition setY(double y) {
    return new PathPosition(this.x, y, this.z);
  }

  public PathPosition setZ(double z) {
    return new PathPosition(this.x, this.y, z);
  }

  public double getCenteredX() {
    return getFlooredX() + 0.5;
  }

  public double getCenteredY() {
    return getFlooredY() + 0.5;
  }

  public double getCenteredZ() {
    return getFlooredZ() + 0.5;
  }

  public int getFlooredX() {
    return (int) Math.floor(this.x);
  }

  public int getFlooredY() {
    return (int) Math.floor(this.y);
  }

  public int getFlooredZ() {
    return (int) Math.floor(this.z);
  }

  public PathPosition add(final double x, final double y, final double z) {
    return new PathPosition(this.x + x, this.y + y, this.z + z);
  }

  public PathPosition add(final PathVector vector) {
    return add(vector.getX(), vector.getY(), vector.getZ());
  }

  public PathPosition subtract(final double x, final double y, final double z) {
    return new PathPosition(this.x - x, this.y - y, this.z - z);
  }

  public PathPosition subtract(final PathVector vector) {
    return subtract(vector.getX(), vector.getY(), vector.getZ());
  }

  public PathVector toVector() {
    return new PathVector(this.x, this.y, this.z);
  }

  public PathPosition floor() {
    return new PathPosition(this.getFlooredX(), this.getFlooredY(), this.getFlooredZ());
  }

  public PathPosition mid() {
    return new PathPosition(
        this.getFlooredX() + 0.5, this.getFlooredY() + 0.5, this.getFlooredZ() + 0.5);
  }

  public PathPosition midPoint(PathPosition end) {
    return new PathPosition((this.x + end.x) / 2, (this.y + end.y) / 2, (this.z + end.z) / 2);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PathPosition that = (PathPosition) o;
    return getFlooredX() == that.getFlooredX()
        && getFlooredY() == that.getFlooredY()
        && getFlooredZ() == that.getFlooredZ();
  }

  @Override
  public int hashCode() {
    int x = getFlooredX();
    int y = getFlooredY();
    int z = getFlooredZ();
    int result = x;
    result = 31 * result + y;
    result = 31 * result + z;
    return result;
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

  public double getZ() {
    return this.z;
  }

  @Override
  public String toString() {
    return "PathPosition(x=" + this.getX() + ", y=" + this.getY() + ", z=" + this.getZ() + ")";
  }
}
