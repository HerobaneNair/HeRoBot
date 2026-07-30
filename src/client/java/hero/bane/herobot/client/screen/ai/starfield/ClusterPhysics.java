package hero.bane.herobot.client.screen.ai.starfield;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ClusterPhysics {
    private static final double LINK_R = 95.0;
    private static final double PAIR_PULL = 1100.0;
    private static final double CENTER_PULL = 11.0;
    private static final double PAIR_DAMP = 1.3;
    private static final double ORBIT_RATE = 2.5;
    private static final double CONVERGE = 0.9;
    private static final double CONVERGE_MIN = 12.0;
    private static final double COHERENCE_BOOST = 0.6;
    private static final double COLLAPSE_GROWTH = 0.8;
    private static final double COLLAPSE_MAX = 9.0;
    private static final double GRAVITY_GROWTH = 1.05;

    private static final double COLLIDE_R = 5.0;
    private static final double BLAST_R = 140.0;
    private static final double BLAST_PUSH = 360.0;

    record Blast(double x, double y, int size) {}

    private ClusterPhysics() {}

    private static double[] omgBuf = new double[0];
    private static double[] spdBuf = new double[0];
    private static boolean[] orbitBuf = new boolean[0];
    private static boolean[] clusterBuf = new boolean[0];
    private static boolean[] claimBuf = new boolean[0];
    private static boolean[] removeBuf = new boolean[0];
    private static int[] blastBuf = new int[0];
    private static int[] stampBuf = new int[0];

    static void apply(List<Comet> comets, double dt) {
        int n = comets.size();
        if (n < 2) return;

        Grid grid = new Grid(comets, LINK_R);
        UnionFind clusters = connect(comets, grid);
        clusters.buildGroups();

        double blend = Math.min(1.0, ORBIT_RATE * dt);
        boolean[] orbiting = orbitBuf = flags(orbitBuf, n);
        boolean[] clustered = clusterBuf = flags(clusterBuf, n);
        int[] items = clusters.items();
        double[] center = new double[4];

        for (int g = 0, gc = clusters.groupCount(); g < gc; g++) {
            int from = clusters.groupStart(g), to = clusters.groupEnd(g);
            int cnt = to - from;
            if (cnt < 2) continue;
            for (int k = from; k < to; k++) clustered[items[k]] = true;
            mean(comets, items, from, to, center);
            pullToCenter(comets, items, from, to, center, dt);
            if (cnt == 2) spring(comets, items[from], items[from + 1], dt);
            else orbit(comets, items, from, to, center, blend, dt, orbiting);
        }

        for (int i = 0; i < n; i++) {
            Comet c = comets.get(i);
            if (!orbiting[i]) c.groupAge = 0;
            c.gravityMul = clustered[i] ? c.gravityMul * GRAVITY_GROWTH : 1.0;
        }
    }

    private static void spring(List<Comet> comets, int ia, int ib, double dt) {
        Comet a = comets.get(ia), b = comets.get(ib);
        double dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy);
        if (d <= 0.01) return;

        double dirx = dx / d, diry = dy / d;
        double force = PAIR_PULL * (1.0 - d / LINK_R);
        a.vx += dirx * force * a.gravityMul * dt; a.vy += diry * force * a.gravityMul * dt;
        b.vx -= dirx * force * b.gravityMul * dt; b.vy -= diry * force * b.gravityMul * dt;

        double damp = PAIR_DAMP * (1.0 - d / LINK_R) * dt;
        double rvx = a.vx - b.vx, rvy = a.vy - b.vy;
        a.vx -= rvx * damp; a.vy -= rvy * damp;
        b.vx += rvx * damp; b.vy += rvy * damp;
    }

    private static void orbit(List<Comet> comets, int[] items, int from, int to, double[] center,
                              double blend, double dt, boolean[] orbiting) {
        int cnt = to - from;
        double cx = center[0], cy = center[1], cvx = center[2], cvy = center[3];

        if (omgBuf.length < cnt) {
            omgBuf = new double[Math.max(64, cnt)];
            spdBuf = new double[Math.max(64, cnt)];
        }
        double[] omg = omgBuf, spd = spdBuf;

        double omega = 0, omegaDen = 0, maxR = 0, sumOmg = 0, sumSpeed = 0;
        for (int m = 0; m < cnt; m++) {
            Comet s = comets.get(items[from + m]);
            double rx = s.x - cx, ry = s.y - cy, rvx = s.vx - cvx, rvy = s.vy - cvy;
            double r2 = rx * rx + ry * ry;
            maxR = Math.max(maxR, Math.sqrt(r2));
            omg[m] = r2 > 1e-6 ? (rx * rvy - ry * rvx) / r2 : 0;
            spd[m] = Math.hypot(rvx, rvy);
            omega += rx * rvy - ry * rvx;
            omegaDen += r2;
            sumOmg += omg[m];
            sumSpeed += spd[m];
        }
        omega = omegaDen > 1e-6 ? omega / omegaDen : 0;
        double meanOmg = sumOmg / cnt, meanSpeed = sumSpeed / cnt;

        double varOmg = 0, varSpeed = 0;
        for (int m = 0; m < cnt; m++) {
            varOmg += sq(omg[m] - meanOmg);
            varSpeed += sq(spd[m] - meanSpeed);
        }
        double cvOmg = Math.sqrt(varOmg / cnt) / (Math.abs(meanOmg) + 1e-3);
        double cvSpeed = Math.sqrt(varSpeed / cnt) / (meanSpeed + 1e-3);
        double coherence = clamp01(1 - cvOmg) * clamp01(1 - cvSpeed);

        for (int m = 0; m < cnt; m++) {
            int idx = items[from + m];
            Comet s = comets.get(idx);
            double rx = s.x - cx, ry = s.y - cy, r = Math.hypot(rx, ry);

            double boost = COHERENCE_BOOST * coherence * (maxR > 1e-3 ? r / maxR : 0);
            s.vx += (cx - s.x) * CENTER_PULL * boost * s.gravityMul * dt;
            s.vy += (cy - s.y) * CENTER_PULL * boost * s.gravityMul * dt;

            double conv = r * CONVERGE + CONVERGE_MIN;
            double inx = r > 0.01 ? -rx / r : 0, iny = r > 0.01 ? -ry / r : 0;
            double tvx = cvx - omega * ry + inx * conv;
            double tvy = cvy + omega * rx + iny * conv;
            s.vx += (tvx - s.vx) * blend;
            s.vy += (tvy - s.vy) * blend;

            orbiting[idx] = true;
            s.groupAge += dt;
            double collapse = Math.min(COLLAPSE_MAX, COLLAPSE_GROWTH * s.groupAge);
            s.vx += (cx - s.x) * CENTER_PULL * collapse * s.gravityMul * dt;
            s.vy += (cy - s.y) * CENTER_PULL * collapse * s.gravityMul * dt;
        }
    }

    private static void pullToCenter(List<Comet> comets, int[] items, int from, int to, double[] center, double dt) {
        for (int k = from; k < to; k++) {
            Comet s = comets.get(items[k]);
            s.vx += (center[0] - s.x) * CENTER_PULL * s.gravityMul * dt;
            s.vy += (center[1] - s.y) * CENTER_PULL * s.gravityMul * dt;
        }
    }

    static List<Blast> collide(List<Comet> comets) {
        int n = comets.size();
        if (n < 2) return List.of();

        Grid grid = new Grid(comets, LINK_R);
        UnionFind clusters = connect(comets, grid);
        clusters.buildGroups();

        UnionFind merged = new UnionFind(n);
        grid.eachNeighborPair((i, j) -> {
            if (clusters.find(i) != clusters.find(j)) return;
            double thresh = COLLIDE_R * Math.max(1, clusters.sizeOf(i) - 1);
            if (dist2(comets, i, j) <= thresh * thresh) merged.union(i, j);
        });
        merged.buildGroups();

        boolean[] claimed = claimBuf = flags(claimBuf, n);
        boolean[] remove = removeBuf = flags(removeBuf, n);
        if (blastBuf.length < n) blastBuf = new int[Math.max(64, n)];
        if (stampBuf.length < n) stampBuf = new int[Math.max(64, n)];
        int[] blast = blastBuf;
        int[] stamp = stampBuf;
        Arrays.fill(stamp, 0, n, 0);

        int[] items = merged.items();
        double absorbR = COLLIDE_R * 3;
        double[] center = new double[4];
        double[] at = new double[4];
        int[] cursor = new int[1];
        List<Blast> blasts = null;
        int tag = 0;

        for (int g = 0, gc = merged.groupCount(); g < gc; g++) {
            int from = merged.groupStart(g), to = merged.groupEnd(g);
            if (to - from < 2) continue;

            mean(comets, items, from, to, center);
            int bc = 0;
            for (int k = from; k < to; k++) {
                int idx = items[k];
                blast[bc++] = idx;
                claimed[idx] = true;
            }
            cursor[0] = bc;
            double ccx = center[0], ccy = center[1];
            grid.eachWithin(ccx, ccy, absorbR, i -> {
                if (claimed[i] || merged.sizeOf(i) >= 2) return;
                Comet s = comets.get(i);
                if (Math.hypot(s.x - ccx, s.y - ccy) <= absorbR) {
                    blast[cursor[0]++] = i;
                    claimed[i] = true;
                }
            });
            bc = cursor[0];

            mean(comets, blast, 0, bc, at);
            if (blasts == null) blasts = new ArrayList<>();
            blasts.add(new Blast(at[0], at[1], bc));

            tag++;
            for (int k = 0; k < bc; k++) {
                stamp[blast[k]] = tag;
                remove[blast[k]] = true;
            }
            repel(comets, grid, at[0], at[1], stamp, tag);
        }

        if (blasts == null) return List.of();
        compact(comets, remove);
        return blasts;
    }

    private static void repel(List<Comet> comets, Grid grid, double x, double y, int[] stamp, int tag) {
        grid.eachWithin(x, y, BLAST_R, i -> {
            if (stamp[i] == tag) return;
            Comet s = comets.get(i);
            double dx = s.x - x, dy = s.y - y, d = Math.hypot(dx, dy);
            if (d < 0.01 || d > BLAST_R) return;
            double push = BLAST_PUSH * (1.0 - d / BLAST_R);
            s.vx += dx / d * push;
            s.vy += dy / d * push;
        });
    }

    private static void compact(List<Comet> comets, boolean[] remove) {
        int n = comets.size(), w = 0;
        for (int i = 0; i < n; i++) {
            if (remove[i]) continue;
            if (w != i) comets.set(w, comets.get(i));
            w++;
        }
        for (int i = n - 1; i >= w; i--) comets.remove(i);
    }

    private static UnionFind connect(List<Comet> comets, Grid grid) {
        UnionFind uf = new UnionFind(comets.size());
        double r2 = LINK_R * LINK_R;
        grid.eachNeighborPair((i, j) -> {
            if (dist2(comets, i, j) < r2) uf.union(i, j);
        });
        return uf;
    }

    private static void mean(List<Comet> comets, int[] items, int from, int to, double[] out) {
        double cx = 0, cy = 0, cvx = 0, cvy = 0;
        for (int k = from; k < to; k++) {
            Comet s = comets.get(items[k]);
            cx += s.x; cy += s.y; cvx += s.vx; cvy += s.vy;
        }
        int n = to - from;
        out[0] = cx / n;
        out[1] = cy / n;
        out[2] = cvx / n;
        out[3] = cvy / n;
    }

    private static boolean[] flags(boolean[] buf, int n) {
        if (buf.length < n) return new boolean[Math.max(64, n)];
        Arrays.fill(buf, 0, n, false);
        return buf;
    }

    private static double dist2(List<Comet> comets, int i, int j) {
        Comet a = comets.get(i), b = comets.get(j);
        double dx = b.x - a.x, dy = b.y - a.y;
        return dx * dx + dy * dy;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }
}
