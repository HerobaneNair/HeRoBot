package hero.bane.herobot.client.screen.ai.starfield;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

    static void apply(List<Comet> comets, double dt) {
        int n = comets.size();
        if (n < 2) return;

        Grid grid = new Grid(comets, LINK_R);
        UnionFind clusters = connect(comets, grid, LINK_R);
        double blend = Math.min(1.0, ORBIT_RATE * dt);
        boolean[] orbiting = new boolean[n];
        boolean[] clustered = new boolean[n];

        for (List<Integer> members : clusters.groups().values()) {
            if (members.size() < 2) continue;
            for (int idx : members) clustered[idx] = true;
            double[] center = mean(comets, members);
            pullToCenter(comets, members, center, dt);
            if (members.size() == 2) spring(comets, members, dt);
            else orbit(comets, members, center, blend, dt, orbiting);
        }

        for (int i = 0; i < n; i++) {
            Comet c = comets.get(i);
            if (!orbiting[i]) c.groupAge = 0;
            c.gravityMul = clustered[i] ? c.gravityMul * GRAVITY_GROWTH : 1.0;
        }
    }

    private static void spring(List<Comet> comets, List<Integer> members, double dt) {
        Comet a = comets.get(members.getFirst()), b = comets.get(members.get(1));
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

    private static void orbit(List<Comet> comets, List<Integer> members, double[] center,
                              double blend, double dt, boolean[] orbiting) {
        int cnt = members.size();
        double cx = center[0], cy = center[1], cvx = center[2], cvy = center[3];

        double[] omg = new double[cnt], spd = new double[cnt];
        double omega = 0, omegaDen = 0, maxR = 0, sumOmg = 0, sumSpeed = 0;
        for (int m = 0; m < cnt; m++) {
            Comet s = comets.get(members.get(m));
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
            int idx = members.get(m);
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

    private static void pullToCenter(List<Comet> comets, List<Integer> members, double[] center, double dt) {
        for (int idx : members) {
            Comet s = comets.get(idx);
            s.vx += (center[0] - s.x) * CENTER_PULL * s.gravityMul * dt;
            s.vy += (center[1] - s.y) * CENTER_PULL * s.gravityMul * dt;
        }
    }

    static List<Blast> collide(List<Comet> comets) {
        int n = comets.size();
        List<Blast> blasts = new ArrayList<>();
        if (n < 2) return blasts;

        Grid grid = new Grid(comets, LINK_R);
        UnionFind clusters = connect(comets, grid, LINK_R);
        int[] clusterSize = clusters.sizes();

        UnionFind merged = new UnionFind(n);
        grid.eachNeighborPair((i, j) -> {
            if (clusters.find(i) != clusters.find(j)) return;
            double thresh = COLLIDE_R * Math.max(1, clusterSize[i] - 1);
            if (dist2(comets, i, j) <= thresh * thresh) merged.union(i, j);
        });

        int[] mergedSize = merged.sizes();
        boolean[] claimed = new boolean[n];
        double absorbR = COLLIDE_R * 3;
        Set<Integer> toRemove = new TreeSet<>(Collections.reverseOrder());

        for (List<Integer> members : merged.groups().values()) {
            if (members.size() < 2) continue;

            double[] center = mean(comets, members);
            List<Integer> blast = new ArrayList<>(members);
            for (int idx : members) claimed[idx] = true;

            grid.eachWithin(center[0], center[1], absorbR, i -> {
                if (claimed[i] || mergedSize[i] >= 2) return;
                Comet s = comets.get(i);
                if (Math.hypot(s.x - center[0], s.y - center[1]) <= absorbR) {
                    blast.add(i);
                    claimed[i] = true;
                }
            });

            double[] at = mean(comets, blast);
            blasts.add(new Blast(at[0], at[1], blast.size()));
            repel(comets, grid, at[0], at[1], blast);
            toRemove.addAll(blast);
        }

        for (int idx : toRemove) comets.remove(idx);
        return blasts;
    }

    private static void repel(List<Comet> comets, Grid grid, double x, double y, List<Integer> exploding) {
        Set<Integer> blast = new HashSet<>(exploding);
        grid.eachWithin(x, y, BLAST_R, i -> {
            if (blast.contains(i)) return;
            Comet s = comets.get(i);
            double dx = s.x - x, dy = s.y - y, d = Math.hypot(dx, dy);
            if (d < 0.01 || d > BLAST_R) return;
            double push = BLAST_PUSH * (1.0 - d / BLAST_R);
            s.vx += dx / d * push;
            s.vy += dy / d * push;
        });
    }

    private static UnionFind connect(List<Comet> comets, Grid grid, double radius) {
        UnionFind uf = new UnionFind(comets.size());
        double r2 = radius * radius;
        grid.eachNeighborPair((i, j) -> {
            if (dist2(comets, i, j) < r2) uf.union(i, j);
        });
        return uf;
    }

    private static double[] mean(List<Comet> comets, List<Integer> members) {
        double cx = 0, cy = 0, cvx = 0, cvy = 0;
        for (int idx : members) {
            Comet s = comets.get(idx);
            cx += s.x; cy += s.y; cvx += s.vx; cvy += s.vy;
        }
        int n = members.size();
        return new double[]{cx / n, cy / n, cvx / n, cvy / n};
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
