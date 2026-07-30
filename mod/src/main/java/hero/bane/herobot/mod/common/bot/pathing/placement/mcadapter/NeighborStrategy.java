package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.pathing.INeighborStrategy;
import hero.bane.herobot.mod.common.bot.pathing.placement.astar.api.wrapper.PathVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NeighborStrategy implements INeighborStrategy {
    public static final NeighborStrategy INSTANCE = new NeighborStrategy();

    private static final int FALL_CAP = 8;
    private static final int VERTICAL_CAP = 8;

    private static final List<PathVector> OFFSETS = buildOffsets();

    private NeighborStrategy() {}

    @Override
    public Iterable<PathVector> getOffsets() {
        return OFFSETS;
    }

    private static List<PathVector> buildOffsets() {
        List<PathVector> offsets = new ArrayList<>();

        int[][] cardinals = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        int[][] diagonals = { {1, 1}, {1, -1}, {-1, 1}, {-1, -1} };

        offsets.add(PathVector.of(0, 1, 0));
        offsets.add(PathVector.of(0, -1, 0));
        for (int k = 2; k <= VERTICAL_CAP; k++) {
            offsets.add(PathVector.of(0, k, 0));
            offsets.add(PathVector.of(0, -k, 0));
        }

        for (int[] c : cardinals) offsets.add(PathVector.of(c[0], 0, c[1]));
        for (int[] d : diagonals) offsets.add(PathVector.of(d[0], 0, d[1]));

        for (int[] c : cardinals) offsets.add(PathVector.of(c[0], 1, c[1]));

        for (int[] c : cardinals) {
            for (int k = 1; k <= FALL_CAP; k++) {
                offsets.add(PathVector.of(c[0], -k, c[1]));
            }
        }

        for (int[] c : cardinals) {
            offsets.add(PathVector.of(c[0] * 2, 0, c[1] * 2));
            offsets.add(PathVector.of(c[0] * 3, 0, c[1] * 3));
            offsets.add(PathVector.of(c[0] * 4, 0, c[1] * 4));
        }

        for (int[] c : cardinals) {
            offsets.add(PathVector.of(c[0] * 2, 1, c[1] * 2));
            offsets.add(PathVector.of(c[0] * 3, 1, c[1] * 3));
        }

        return Collections.unmodifiableList(offsets);
    }
}
