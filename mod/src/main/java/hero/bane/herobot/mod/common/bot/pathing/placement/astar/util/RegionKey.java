package hero.bane.herobot.mod.common.bot.pathing.placement.astar.util;

public final class RegionKey {
  private static final long MASK_Y = 0xFFFFFL;
  private static final long MASK_XZ = 0x3FFFFFL;

  private static final int SHIFT_Z = 20;
  private static final int SHIFT_X = 42;

  private static final int MIN_XZ = -(1 << 21);
  private static final int MAX_XZ = (1 << 21) - 1;
  private static final int MIN_Y = -(1 << 19);
  private static final int MAX_Y = (1 << 19) - 1;

  private RegionKey() {}

  public static boolean isInRange(int x, int y, int z) {
    return x >= MIN_XZ && x <= MAX_XZ && z >= MIN_XZ && z <= MAX_XZ && y >= MIN_Y && y <= MAX_Y;
  }

  public static long pack(int x, int y, int z) {
    if (x < MIN_XZ || x > MAX_XZ) throw outOfRange("x", x, MIN_XZ, MAX_XZ);
    if (z < MIN_XZ || z > MAX_XZ) throw outOfRange("z", z, MIN_XZ, MAX_XZ);
    if (y < MIN_Y || y > MAX_Y) throw outOfRange("y", y, MIN_Y, MAX_Y);
    return ((long) x & MASK_XZ) << SHIFT_X | ((long) z & MASK_XZ) << SHIFT_Z | ((long) y & MASK_Y);
  }

  private static IllegalArgumentException outOfRange(String axis, int value, int min, int max) {
    return new IllegalArgumentException(
        "RegionKey " + axis + "=" + value + " out of range [" + min + ", " + max + "]");
  }
}
