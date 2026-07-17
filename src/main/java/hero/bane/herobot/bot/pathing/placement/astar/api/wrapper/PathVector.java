package hero.bane.herobot.bot.pathing.placement.astar.api.wrapper;

import hero.bane.herobot.bot.pathing.placement.astar.api.util.NumberUtils;
import java.util.Objects;

public class PathVector {
  private final double x;
  private final double y;
  private final double z;

  public PathVector(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  public static PathVector of(double x, double y, double z) {
    return new PathVector(x, y, z);
  }

  public static double computeDistance(PathVector A, PathVector B, PathVector C) {
    Objects.requireNonNull(A, "A must not be null");
    Objects.requireNonNull(B, "B must not be null");
    Objects.requireNonNull(C, "C must not be null");

    double lineLength = C.distance(B);
    if (lineLength == 0.0 || !Double.isFinite(lineLength)) {
      return A.distance(B);
    }

    PathVector d = C.subtract(B).divide(lineLength);
    PathVector v = A.subtract(B);

    double t = v.dot(d);
    PathVector P = B.add(d.multiply(t));

    return P.distance(A);
  }

  public double dot(PathVector otherVector) {
    return this.x * otherVector.x + this.y * otherVector.y + this.z * otherVector.z;
  }

  public double length() {
    return Math.sqrt(
        NumberUtils.square(this.x) + NumberUtils.square(this.y) + NumberUtils.square(this.z));
  }

  public double distance(PathVector otherVector) {
    return Math.sqrt(
        NumberUtils.square(this.x - otherVector.x)
            + NumberUtils.square(this.y - otherVector.y)
            + NumberUtils.square(this.z - otherVector.z));
  }

  public PathVector setX(double x) {
    return new PathVector(x, this.y, this.z);
  }

  public PathVector setY(double y) {
    return new PathVector(this.x, y, this.z);
  }

  public PathVector setZ(double z) {
    return new PathVector(this.x, this.y, z);
  }

  public PathVector subtract(PathVector otherVector) {
    return new PathVector(this.x - otherVector.x, this.y - otherVector.y, this.z - otherVector.z);
  }

  public PathVector multiply(double value) {
    return new PathVector(this.x * value, this.y * value, this.z * value);
  }

  public PathVector normalize() {
    double magnitude = this.length();
    return new PathVector(this.x / magnitude, this.y / magnitude, this.z / magnitude);
  }

  public PathVector divide(double value) {
    return new PathVector(this.x / value, this.y / value, this.z / value);
  }

  public PathVector add(PathVector otherVector) {
    return new PathVector(this.x + otherVector.x, this.y + otherVector.y, this.z + otherVector.z);
  }

  public PathVector getCrossProduct(PathVector o) {
    double x = this.y * o.getZ() - o.getY() * this.z;
    double y = this.z * o.getX() - o.getZ() * this.x;
    double z = this.x * o.getY() - o.getX() * this.y;
    return new PathVector(x, y, z);
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

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof PathVector)) return false;
    final PathVector other = (PathVector) o;
    if (!other.canEqual(this)) return false;
    if (Double.compare(this.getX(), other.getX()) != 0) return false;
    if (Double.compare(this.getY(), other.getY()) != 0) return false;
    if (Double.compare(this.getZ(), other.getZ()) != 0) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PathVector;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $x = Double.doubleToLongBits(this.getX());
    result = result * PRIME + (int) ($x >>> 32 ^ $x);
    final long $y = Double.doubleToLongBits(this.getY());
    result = result * PRIME + (int) ($y >>> 32 ^ $y);
    final long $z = Double.doubleToLongBits(this.getZ());
    result = result * PRIME + (int) ($z >>> 32 ^ $z);
    return result;
  }
}
