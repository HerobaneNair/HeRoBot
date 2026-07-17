package hero.bane.herobot.client.screen.ai.starfield;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Grid {
    interface PairVisitor {
        void visit(int i, int j);
    }

    private final List<Comet> comets;
    private final double cell;
    private final Map<Long, List<Integer>> buckets = new HashMap<>();

    Grid(List<Comet> comets, double cell) {
        this.comets = comets;
        this.cell = cell;
        for (int i = 0; i < comets.size(); i++) {
            Comet c = comets.get(i);
            buckets.computeIfAbsent(key(c.x, c.y), k -> new ArrayList<>()).add(i);
        }
    }

    void eachNeighborPair(PairVisitor visitor) {
        for (int i = 0; i < comets.size(); i++) {
            Comet a = comets.get(i);
            int cx = cellOf(a.x), cy = cellOf(a.y);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    List<Integer> bucket = buckets.get(pack(cx + dx, cy + dy));
                    if (bucket == null) continue;
                    for (int j : bucket) if (j > i) visitor.visit(i, j);
                }
            }
        }
    }

    void eachWithin(double x, double y, double radius, java.util.function.IntConsumer consumer) {
        int minCx = cellOf(x - radius), maxCx = cellOf(x + radius);
        int minCy = cellOf(y - radius), maxCy = cellOf(y + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                List<Integer> bucket = buckets.get(pack(cx, cy));
                if (bucket == null) continue;
                for (int idx : bucket) consumer.accept(idx);
            }
        }
    }

    private int cellOf(double v) {
        return (int) Math.floor(v / cell);
    }

    private long key(double x, double y) {
        return pack(cellOf(x), cellOf(y));
    }

    private static long pack(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xFFFFFFFFL);
    }
}
