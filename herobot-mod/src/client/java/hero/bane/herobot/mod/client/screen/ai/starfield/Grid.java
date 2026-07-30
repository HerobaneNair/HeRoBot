package hero.bane.herobot.mod.client.screen.ai.starfield;

import java.util.List;
import java.util.function.IntConsumer;

final class Grid {
    interface PairVisitor {
        void visit(int i, int j);
    }

    private final double cell;
    private final int n;
    private final int[] cellX, cellY;
    private final long[] keys;
    private final int[] start;
    private final int[] count;
    private final int[] items;
    private final int mask;

    Grid(List<Comet> comets, double cell) {
        this.cell = cell;
        this.n = comets.size();
        cellX = new int[n];
        cellY = new int[n];
        items = new int[n];
        int cap = 8;
        while (cap < n * 2) cap <<= 1;
        mask = cap - 1;
        keys = new long[cap];
        start = new int[cap];
        count = new int[cap];

        int[] slotOf = new int[n];
        for (int i = 0; i < n; i++) {
            Comet c = comets.get(i);
            int cx = cellOf(c.x), cy = cellOf(c.y);
            cellX[i] = cx;
            cellY[i] = cy;
            int s = intern(pack(cx, cy));
            slotOf[i] = s;
            count[s]++;
        }
        int run = 0;
        for (int s = 0; s < cap; s++) {
            start[s] = run;
            run += count[s];
        }
        int[] cursor = new int[cap];
        System.arraycopy(start, 0, cursor, 0, cap);
        for (int i = 0; i < n; i++) items[cursor[slotOf[i]]++] = i;
    }

    void eachNeighborPair(PairVisitor visitor) {
        for (int i = 0; i < n; i++) {
            int cx = cellX[i], cy = cellY[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int s = slot(pack(cx + dx, cy + dy));
                    if (s < 0) continue;
                    int end = start[s] + count[s];
                    for (int k = start[s]; k < end; k++) {
                        int j = items[k];
                        if (j > i) visitor.visit(i, j);
                    }
                }
            }
        }
    }

    void eachWithin(double x, double y, double radius, IntConsumer consumer) {
        int minCx = cellOf(x - radius), maxCx = cellOf(x + radius);
        int minCy = cellOf(y - radius), maxCy = cellOf(y + radius);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                int s = slot(pack(cx, cy));
                if (s < 0) continue;
                int end = start[s] + count[s];
                for (int k = start[s]; k < end; k++) consumer.accept(items[k]);
            }
        }
    }

    private int intern(long key) {
        int s = hash(key) & mask;
        while (count[s] != 0) {
            if (keys[s] == key) return s;
            s = (s + 1) & mask;
        }
        keys[s] = key;
        return s;
    }

    private int slot(long key) {
        int s = hash(key) & mask;
        while (count[s] != 0) {
            if (keys[s] == key) return s;
            s = (s + 1) & mask;
        }
        return -1;
    }

    private int cellOf(double v) {
        return (int) Math.floor(v / cell);
    }

    private static long pack(int cx, int cy) {
        return (((long) cx) << 32) ^ (cy & 0xFFFFFFFFL);
    }

    private static int hash(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> 32);
    }
}
