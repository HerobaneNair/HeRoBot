package hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathVector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NeighborStrategies {
  private static final List<PathVector> VERTICAL_AND_HORIZONTAL_OFFSETS =
      Collections.unmodifiableList(
          Arrays.asList(
              new PathVector(1, 0, 0), new PathVector(-1, 0, 0),
              new PathVector(0, 0, 1), new PathVector(0, 0, -1),
              new PathVector(0, 1, 0), new PathVector(0, -1, 0)));

  private static final List<PathVector> DIAGONAL_3D_OFFSETS = buildDiagonal3dOffsets();

  public static final INeighborStrategy VERTICAL_AND_HORIZONTAL = () -> VERTICAL_AND_HORIZONTAL_OFFSETS;

  public static final INeighborStrategy DIAGONAL_3D = () -> DIAGONAL_3D_OFFSETS;

  private NeighborStrategies() {}

  private static List<PathVector> buildDiagonal3dOffsets() {
    List<PathVector> offsets = new ArrayList<>(26);
    for (int x = -1; x <= 1; x++) {
      for (int y = -1; y <= 1; y++) {
        for (int z = -1; z <= 1; z++) {
          if (x == 0 && y == 0 && z == 0) continue;
          offsets.add(new PathVector(x, y, z));
        }
      }
    }
    return Collections.unmodifiableList(offsets);
  }
}
