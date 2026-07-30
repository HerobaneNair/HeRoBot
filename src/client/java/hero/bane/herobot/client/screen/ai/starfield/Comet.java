package hero.bane.herobot.client.screen.ai.starfield;

import java.util.ArrayList;
import java.util.List;

final class Comet {
    private static final double FADE_IN = 0.4;
    private static final int TRAIL = 14;
    private static final int MAX_BOUNCES = 3;
    private static final double WIRE_T = 3.0;

    private static final double STRETCH_RATE = 6.0;
    private static final double NOSE_LEN = 22.0;
    private static final double ELONGATE = 1.6;
    private static final double MAX_ELONGATE = 3.0;
    private static final double PINCH = 0.75;
    private static final double SPAGHETTI_ALPHA = 0.3;

    private static final int HEAD_RGB = 0xFFFFFF;
    private static final int TRAIL_RGB = 0xEAF1FF;

    double x, y, vx, vy;
    double life;
    double deathProgress;
    int bounces;
    double groupAge;
    double gravityMul = 1.0;
    boolean straight;
    boolean spiraling;
    boolean spiralFed;
    double spiralAngle, spiralR, spiralHand, spiralBlend, spiralTime;
    private double lockVx, lockVy;

    private double stretch, hnx, hny, holeDist;

    boolean consumed;

    private static int[] coverBuf = new int[0];
    private static int[] stampBuf = new int[0];
    private static int[] touchBuf = new int[0];
    private static int stampGen;

    private final List<double[]> trail = new ArrayList<>();
    private double[][] trailPts;
    private double[][] warpPts;
    private double[] nosePt;
    private double[] headPt;

    Comet(double x, double y, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
    }

    void makeStraight() {
        straight = true;
        lockVx = vx;
        lockVy = vy;
    }

    void relock() {
        if (straight) {
            vx = lockVx;
            vy = lockVy;
        }
    }

    void applyImpulse(double dvx, double dvy) {
        vx += dvx;
        vy += dvy;
        if (straight) {
            lockVx += dvx;
            lockVy += dvy;
        }
    }

    void setStretch(double target, double holeX, double holeY, double dt) {
        double dx = holeX - x, dy = holeY - y;
        double d = Math.hypot(dx, dy);
        if (d > 1e-6) {
            hnx = dx / d;
            hny = dy / d;
            holeDist = d;
        }
        stretch += (target - stretch) * Math.min(1.0, dt * STRETCH_RATE);
    }

    void decayStretch(double dt) {
        if (stretch <= 1e-4) {
            stretch = 0;
            return;
        }
        stretch += (0 - stretch) * Math.min(1.0, dt * STRETCH_RATE);
    }

    void advance(double dt) {
        x += vx * dt;
        y += vy * dt;
        life += dt;
    }

    void recordTrail() {
        double[] p = trail.size() >= TRAIL ? trail.removeLast() : new double[2];
        p[0] = x;
        p[1] = y;
        trail.addFirst(p);
    }

    boolean exploded() {
        return bounces >= MAX_BOUNCES;
    }

    void age(double dt, double lifespan) {
        if (lifespan > 0) deathProgress += dt / lifespan;
    }

    boolean expired() {
        return deathProgress >= 1.0;
    }

    boolean offscreen(int left, int top, int right, int bottom, double margin) {
        if (onView(x, y, left, top, right, bottom, margin)) return false;
        for (double[] p : trail) if (onView(p[0], p[1], left, top, right, bottom, margin)) return false;
        return true;
    }

    private static boolean onView(double px, double py, int left, int top, int right, int bottom, double margin) {
        return px >= left - margin && px <= right + margin && py >= top - margin && py <= bottom + margin;
    }

    void bounce(List<double[]> blocks, List<double[]> wires) {
        if (blocks != null) for (double[] r : blocks) bounceOffRect(r);
        if (wires != null) for (double[] w : wires) bounceOffWire(w);
    }

    private void bounceOffRect(double[] r) {
        if (x <= r[0] || x >= r[2] || y <= r[1] || y >= r[3]) return;
        double pl = x - r[0], pr = r[2] - x, pt = y - r[1], pb = r[3] - y;
        double min = Math.min(Math.min(pl, pr), Math.min(pt, pb));
        if (min == pl)      { x = r[0]; vx = -Math.abs(vx); }
        else if (min == pr) { x = r[2]; vx =  Math.abs(vx); }
        else if (min == pt) { y = r[1]; vy = -Math.abs(vy); }
        else                { y = r[3]; vy =  Math.abs(vy); }
        bounces++;
    }

    private void bounceOffWire(double[] w) {
        double ax = w[0], ay = w[1], bx = w[2], by = w[3];
        if (Math.abs(ax - bx) < 0.5) {
            if (y < Math.min(ay, by) - WIRE_T || y > Math.max(ay, by) + WIRE_T) return;
            double side = x - ax;
            if (side != 0 && Math.abs(side) <= WIRE_T && vx * side < 0) {
                x = ax + Math.signum(side) * WIRE_T;
                vx = Math.signum(side) * Math.abs(vx);
                bounces++;
            }
        } else if (Math.abs(ay - by) < 0.5) {
            if (x < Math.min(ax, bx) - WIRE_T || x > Math.max(ax, bx) + WIRE_T) return;
            double side = y - ay;
            if (side != 0 && Math.abs(side) <= WIRE_T && vy * side < 0) {
                y = ay + Math.signum(side) * WIRE_T;
                vy = Math.signum(side) * Math.abs(vy);
                bounces++;
            }
        }
    }

    private static final double HEAD_R = 1.9;
    private static final double TAIL_R = 0.4;

    void draw(PixelBatch batch, int left, int top, int right, int bottom) {
        double alpha = Math.min(1.0, life / FADE_IN);
        if (alpha <= 0.001) return;
        drawTrail(batch, alpha, left, top, right, bottom);
        int hx = (int) x, hy = (int) y;
        batch.rect(hx - 1, hy - 1, hx + 2, hy + 2, ((int) (alpha * 90 * (1.0 - stretch)) << 24) | HEAD_RGB);
        batch.pixel(hx, hy, ((int) (alpha * 255) << 24) | HEAD_RGB);
    }

    private void drawTrail(PixelBatch batch, double alpha, int left, int top, int right, int bottom) {
        int n = trail.size();
        if (n == 0) return;
        if (trailPts == null) trailPts = new double[TRAIL + 2][];
        double[][] pts = trailPts;
        boolean warped = stretch > 1e-3;
        int base = warped ? 1 : 0;
        if (warped) {
            if (nosePt == null) nosePt = new double[2];
            double len = Math.clamp(holeDist - 1, 0, NOSE_LEN * stretch);
            nosePt[0] = x + hnx * len;
            nosePt[1] = y + hny * len;
            pts[0] = nosePt;
        }
        if (headPt == null) headPt = new double[2];
        headPt[0] = x;
        headPt[1] = y;
        pts[base] = headPt;
        for (int i = 0; i < n; i++) pts[base + i + 1] = trail.get(i);
        int segs = n + base;

        double thin = 1.0 - stretch * PINCH;
        double rThin = Math.max(0.45, thin);
        double headR = HEAD_R * rThin, tailR = TAIL_R * rThin;

        if (warped) {
            if (warpPts == null) warpPts = new double[TRAIL + 2][2];
            double elong = Math.min(1.0 + stretch * ELONGATE, MAX_ELONGATE);
            for (int i = 0; i <= segs; i++) {
                double[] p = pts[i];
                double dx = p[0] - x, dy = p[1] - y;
                double rad = (dx * hnx + dy * hny) * elong;
                double tx = dx - (dx * hnx + dy * hny) * hnx, ty = dy - (dx * hnx + dy * hny) * hny;
                warpPts[i][0] = x + hnx * rad + tx * thin;
                warpPts[i][1] = y + hny * rad + ty * thin;
            }
            pts = warpPts;
        }

        double minX = x, maxX = x, minY = y, maxY = y;
        for (int i = 0; i <= segs; i++) {
            double[] p = pts[i];
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        int x0 = Math.max(left, (int) Math.floor(minX - HEAD_R));
        int x1 = Math.min(right - 1, (int) Math.ceil(maxX + HEAD_R));
        int y0 = Math.max(top, (int) Math.floor(minY - HEAD_R));
        int y1 = Math.min(bottom - 1, (int) Math.ceil(maxY + HEAD_R));
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        if (w <= 0 || h <= 0) return;

        int area = w * h;
        if (coverBuf.length < area) {
            int cap = Math.max(4096, area);
            coverBuf = new int[cap];
            stampBuf = new int[cap];
            touchBuf = new int[cap];
            stampGen = 0;
        }
        if (stampGen == Integer.MAX_VALUE) {
            java.util.Arrays.fill(stampBuf, 0);
            stampGen = 0;
        }
        int gen = ++stampGen;
        int[] cover = coverBuf, stamp = stampBuf, touch = touchBuf;
        int touched = 0;

        for (int s = 0; s < segs; s++) {
            double[] pa = pts[s], pb = pts[s + 1];
            double dx = pb[0] - pa[0], dy = pb[1] - pa[1];
            double len2 = dx * dx + dy * dy;
            double segR = tailR + (headR - tailR) * (1.0 - (double) s / segs);
            double segAlpha = warped && s == 0 ? alpha * SPAGHETTI_ALPHA : alpha;

            int sx0 = Math.max(x0, (int) Math.floor(Math.min(pa[0], pb[0]) - segR));
            int sx1 = Math.min(x1, (int) Math.ceil(Math.max(pa[0], pb[0]) + segR));
            int sy0 = Math.max(y0, (int) Math.floor(Math.min(pa[1], pb[1]) - segR));
            int sy1 = Math.min(y1, (int) Math.ceil(Math.max(pa[1], pb[1]) + segR));

            for (int py = sy0; py <= sy1; py++) {
                double cy = py + 0.5;
                int row = (py - y0) * w;
                for (int px = sx0; px <= sx1; px++) {
                    double cx = px + 0.5;
                    double t = len2 <= 1e-9 ? 0 : ((cx - pa[0]) * dx + (cy - pa[1]) * dy) / len2;
                    t = clamp01(t);
                    double ex = cx - (pa[0] + dx * t), ey = cy - (pa[1] + dy * t);
                    double d = Math.sqrt(ex * ex + ey * ey);
                    double f = 1.0 - (s + t) / segs;
                    double radius = tailR + (headR - tailR) * f;
                    double edge = clamp01(radius - d);
                    int a = (int) (segAlpha * f * f * 230 * edge);
                    int idx = row + (px - x0);
                    if (stamp[idx] != gen) {
                        stamp[idx] = gen;
                        cover[idx] = a;
                        touch[touched++] = idx;
                    } else if (a > cover[idx]) {
                        cover[idx] = a;
                    }
                }
            }
        }

        for (int k = 0; k < touched; k++) {
            int idx = touch[k];
            int a = cover[idx];
            if (a > 3) batch.pixel(x0 + idx % w, y0 + idx / w, (a << 24) | TRAIL_RGB);
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }
}
