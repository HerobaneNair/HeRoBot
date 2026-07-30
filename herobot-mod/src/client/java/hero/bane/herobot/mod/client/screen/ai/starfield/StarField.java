package hero.bane.herobot.mod.client.screen.ai.starfield;

import hero.bane.herobot.mod.client.EditorPrefs;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class StarField {
    private static final double Z_MIN = 0.4;
    private static final double Z_MAX = 2.0;
    private static final double SPAWN_C0 = 0.025;
    private static final double SPAWN_FALLOFF = 4.0;
    private static final double EXTRA_CHANCE = 0.5;
    private static final int MAX_BURST = 3;
    private static final int SPAWN_TRIES = 5;
    private static final double SPAWN_MIN_DIST = 150.0;
    private static final double SPAWN_DIST_STEP = 30.0;
    private static final double SPEED_MIN = 100.0;
    private static final double SPEED_MAX = 165.0;

    private static final double GRID_PULL = 45.0;
    public static final double GRID_FADE_LO = 0.3;
    public static final double GRID_FADE_HI = 0.4;
    private static final double MOUSE_R = 75.0;
    private static final double MOUSE_FORCE = 1500.0;
    private static final double MARGIN = 3.0;
    private static final double MAX_DT = 0.1;

    private static final double WHITE_FRACTION = 0.3;

    private static final int LOAD_LOW = 40;
    private static final int LOAD_HIGH = 260;
    private static final double MIN_PARTICLE_SCALE = 0.15;

    private static final double LIFE_AT_1 = 10.0;
    private static final double LIFE_AT_20 = 2.0;
    private static final int LIFE_FULL_COUNT = 20;

    private final List<Comet> comets = new ArrayList<>();
    private final List<Comet> physicsScratch = new ArrayList<>();
    private final List<Comet> collideScratch = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Twinkle> twinkles = new ArrayList<>();
    private final PixelBatch batch = new PixelBatch();
    private final java.util.Random rng = new java.util.Random();
    private long lastNanos;
    private BlackHole blackHole;

    public static final int BLACK_HOLE_THRESHOLD = 10;
    private static final int BIG_SPARKLE_SPARKS = 8;
    private static final double COLLAPSE_COVER = 0.85;
    private static final double COLLAPSE_K = 0.05;
    private static final double COLLAPSE_MIN = 0.5;
    private static final double COLLAPSE_MAX = 3.0;

    public static double gridVisibility(double zoom) {
        return Math.clamp((zoom - GRID_FADE_LO) / (GRID_FADE_HI - GRID_FADE_LO), 0.0, 1.0);
    }

    public void tick(int left, int top, int right, int bottom, double panX, double panY, double zoom) {
        if (!EditorPrefs.cometsEnabled()) return;
        if (right - left < 32 || bottom - top < 32) return;
        double t = Math.clamp((Z_MAX - zoom) / (Z_MAX - Z_MIN), 0.0, 1.0);
        double chance = SPAWN_C0 * (Math.exp(SPAWN_FALLOFF * t) - 1) / (Math.exp(SPAWN_FALLOFF) - 1);
        if (chance <= 0 || rng.nextDouble() >= chance) return;

        int count = 1;
        while (count < MAX_BURST && rng.nextDouble() < EXTRA_CHANCE) count++;
        for (int i = 0; i < count; i++) spawn(left, top, right, bottom, panX, panY, zoom);
    }

    public boolean canSpawnBlackHole() {
        return blackHole == null || blackHole.drained();
    }

    public void spawnBlackHole(int left, int top, int right, int bottom) {
        if (blackHole != null && !blackHole.drained()) {
            blackHole.growToMax();
            return;
        }
        if (right - left < 32 || bottom - top < 32) return;
        double cx = (left + right) / 2.0, cy = (top + bottom) / 2.0;
        blackHole = new BlackHole(cx, cy);
        blackHole.fillToMax();
        sparkle(cx, cy);
    }

    public boolean blackHoleGrabbed(double x, double y) {
        return blackHole != null && blackHole.grabbed(x, y);
    }

    public double[] blackHolePos() {
        return blackHole == null ? null : new double[]{blackHole.x, blackHole.y};
    }

    public void moveBlackHole(double x, double y) {
        if (blackHole != null) blackHole.moveTo(x, y);
    }

    public void removeBlackHole() {
        if (blackHole == null) return;
        sparkle(blackHole.x, blackHole.y);
        blackHole = null;
    }

    public double[] blackHoleWarp() {
        return blackHole == null ? null : new double[]{blackHole.x, blackHole.y, blackHole.radius()};
    }

    public void sparkle(double x, double y) {
        twinkles.add(new Twinkle(x, y, Twinkle.LEN, true));
    }

    public void bigSparkle(double x, double y) {
        twinkles.add(new Twinkle(x, y, Twinkle.BIG_LEN, true));
        for (int i = 0; i < BIG_SPARKLE_SPARKS; i++) spark(x, y);
    }

    public void spark(double x, double y) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double sp = 110 + rng.nextDouble() * 140;
        double life = 0.4 + rng.nextDouble() * 0.4;
        sparks.add(new Spark(x, y, Math.cos(ang) * sp, Math.sin(ang) * sp, life, 0xFFFFFF));
    }

    public void spawnConverging(int count, int left, int top, int right, int bottom, double meetSeconds) {
        if (count <= 0 || right - left < 32 || bottom - top < 32) return;
        double cx = (left + right) / 2.0, cy = (top + bottom) / 2.0;
        double halfW = (right - left) / 2.0, halfH = (bottom - top) / 2.0;
        double base = rng.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double ang = base + i * (Math.PI * 2 / count);
            double dx = Math.cos(ang), dy = Math.sin(ang);
            double tx = Math.abs(dx) < 1e-6 ? Double.MAX_VALUE : halfW / Math.abs(dx);
            double ty = Math.abs(dy) < 1e-6 ? Double.MAX_VALUE : halfH / Math.abs(dy);
            double dist = Math.min(tx, ty);
            double speed = dist / meetSeconds;
            Comet c = new Comet(cx + dx * dist, cy + dy * dist, -dx * speed, -dy * speed);
            if (count > 1) c.makeStraight();
            comets.add(c);
        }
        if (count >= BLACK_HOLE_THRESHOLD && blackHole == null) blackHole = new BlackHole(cx, cy);
    }

    public void render(GuiGraphics g, int left, int top, int right, int bottom,
                       double panX, double panY, double zoom, double mouseX, double mouseY,
                       List<double[]> blocks, List<double[]> wires) {
        double dt = elapsedSeconds();
        batch.begin(g, left, top, right, bottom);

        double step = Math.max(8, 40 * zoom);
        double ox = panX % step, oy = panY % step;
        boolean mouseInside = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
        double gridFade = gridVisibility(zoom);

        if (gridFade > 0 || mouseInside) {
            for (Comet c : comets)
                if (!c.straight) applyFieldForces(c, dt, left, top, step, ox, oy, gridFade, mouseInside, mouseX, mouseY);
        }
        List<Comet> clusterComets = comets;
        if (blackHole != null) {
            physicsScratch.clear();
            for (Comet c : comets) if (!blackHole.inRange(c)) physicsScratch.add(c);
            clusterComets = physicsScratch;
        }
        ClusterPhysics.apply(clusterComets, dt);
        physicsScratch.clear();
        for (Comet c : comets) c.relock();
        if (mouseInside) for (Comet c : comets) if (c.straight) applyMouseForce(c, dt, mouseX, mouseY);
        for (Comet c : comets) {
            if (blackHole != null && blackHole.inRange(c)) {
                if (c.spiraling || blackHole.captures(c)) {
                    blackHole.spiral(c, dt);
                    if (!c.spiralFed) {
                        c.spiralFed = true;
                        blackHole.feed(true);
                    }
                } else {
                    blackHole.pull(c, dt);
                }
                c.setStretch(blackHole.tidal(c.x, c.y), blackHole.x, blackHole.y, dt);
            } else {
                c.spiraling = false;
                c.spiralFed = false;
                c.decayStretch(dt);
            }
        }

        double lifespan = lifespanFor(comets.size());
        Iterator<Comet> it = comets.iterator();
        while (it.hasNext()) {
            Comet c = it.next();
            c.advance(dt);
            if (blackHole != null && blackHole.swallows(c)) {
                blackHole.feed(!c.spiralFed);
                it.remove();
                continue;
            }
            if (c.spiraling) {
                c.recordTrail();
                c.draw(batch, left, top, right, bottom);
                continue;
            }
            if (!c.straight && (blackHole == null || !blackHole.inRange(c))) c.bounce(blocks, wires);
            if (c.exploded() && !swallowedInstead(c.x, c.y)) {
                explode(c.x, c.y, 1);
                it.remove();
                continue;
            }

            c.recordTrail();
            if (c.offscreen(left, top, right, bottom, MARGIN)) {
                it.remove();
                continue;
            }
            c.age(dt, lifespan);
            if (c.expired()) {
                twinkles.add(new Twinkle(c.x, c.y, Twinkle.EXPIRY_LEN, false));
                it.remove();
                continue;
            }
            c.draw(batch, left, top, right, bottom);
        }

        List<Comet> collidable = comets;
        boolean filtered = false;
        if (blackHole != null) {
            collideScratch.clear();
            for (Comet c : comets) if (!blackHole.inRange(c)) collideScratch.add(c);
            collidable = collideScratch;
            filtered = true;
            for (Comet c : collidable) c.consumed = true;
        }

        for (ClusterPhysics.Blast b : ClusterPhysics.collide(collidable)) {
            if (!swallowedInstead(b.x(), b.y())) explode(b.x(), b.y(), b.size());
        }

        if (filtered) {
            for (Comet c : collidable) c.consumed = false;
            comets.removeIf(c -> c.consumed);
            collideScratch.clear();
        }

        if (blackHole != null) {
            if (blackHole.update(dt)) blackHole.draw(batch);
            else blackHole = null;
        }

        drawSparks(dt, left, top, right, bottom);
        drawTwinkles(dt);
        batch.submit(g);
    }

    public void explodeSelection(double x0, double y0, double x1, double y1) {
        double lx = Math.min(x0, x1), hx = Math.max(x0, x1);
        double ly = Math.min(y0, y1), hy = Math.max(y0, y1);
        if (blackHole != null && blackHole.shadowCoverage(lx, ly, hx, hy) >= COLLAPSE_COVER) {
            double area = (hx - lx) * (hy - ly);
            blackHole.drain(Math.clamp(COLLAPSE_K * Math.cbrt(area), COLLAPSE_MIN, COLLAPSE_MAX));
        }
        Iterator<Comet> it = comets.iterator();
        while (it.hasNext()) {
            Comet c = it.next();
            if (!c.spiraling && c.x >= lx && c.x <= hx && c.y >= ly && c.y <= hy && !swallowedInstead(c.x, c.y)) {
                explode(c.x, c.y, 1);
                it.remove();
            }
        }
    }

    private boolean swallowedInstead(double x, double y) {
        return blackHole != null && (blackHole.inRange(x, y) || blackHole.smothers(x, y));
    }

    private void spawn(int left, int top, int right, int bottom, double panX, double panY, double zoom) {
        double step = Math.max(8, 40 * zoom);
        double ox = panX % step, oy = panY % step;
        double cx = (left + right) / 2.0, cy = (top + bottom) / 2.0;
        double rx = (right - left) * 0.42, ry = (bottom - top) * 0.42;

        double sx = 0, sy = 0;
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            sx = snap(cx + Math.cos(ang) * rx, left, ox, step);
            sy = snap(cy + Math.sin(ang) * ry, top, oy, step);
            if (attempt == SPAWN_TRIES - 1) break;
            if (!tooClose(sx, sy, SPAWN_MIN_DIST - attempt * SPAWN_DIST_STEP)) break;
        }

        double aim = Math.atan2(cy - sy, cx - sx) + (rng.nextDouble() - 0.5) * 0.45;
        double speed = SPEED_MIN + rng.nextDouble() * (SPEED_MAX - SPEED_MIN);
        comets.add(new Comet(sx, sy, Math.cos(aim) * speed, Math.sin(aim) * speed));
    }

    private boolean tooClose(double x, double y, double minDist) {
        double m2 = minDist * minDist;
        for (Comet c : comets) {
            double dx = c.x - x, dy = c.y - y;
            if (dx * dx + dy * dy < m2) return true;
        }
        return false;
    }

    private void applyFieldForces(Comet c, double dt, int left, int top, double step, double ox, double oy,
                                  double gridFade, boolean mouseInside, double mouseX, double mouseY) {
        if (gridFade > 0) {
            double gx = snap(c.x, left, ox, step) - c.x, gy = snap(c.y, top, oy, step) - c.y;
            double gd = Math.sqrt(gx * gx + gy * gy);
            if (gd > 0.01 && gd < step) {
                double pull = GRID_PULL * gridFade * (1.0 - gd / step);
                c.vx += gx / gd * pull * dt;
                c.vy += gy / gd * pull * dt;
            }
        }

        if (mouseInside) applyMouseForce(c, dt, mouseX, mouseY);
    }

    private void applyMouseForce(Comet c, double dt, double mouseX, double mouseY) {
        double mx = c.x - mouseX, my = c.y - mouseY, md = Math.sqrt(mx * mx + my * my);
        if (md > 0.01 && md < MOUSE_R) {
            double k = 1.0 - md / MOUSE_R;
            double push = MOUSE_FORCE * k * k;
            c.applyImpulse(mx / md * push * dt, my / md * push * dt);
        }
    }

    private void explode(double x, double y, int level) {
        twinkles.add(new Twinkle(x, y, Twinkle.LEN * 0.5 * level, true));
        if (level <= 1) {
            burst(x, y, 1, 4 + rng.nextInt(3), 0xFFFFFF, 1.0);
        } else {
            burst(x, y, level, 10 + level * 9 + rng.nextInt(8), groupRgb(level), WHITE_FRACTION);
        }
    }

    private void burst(double x, double y, int count, int particles, int color, double whiteFrac) {
        particles = scaleParticles(particles);
        double spread = 40 + count * 45;
        for (int i = 0; i < particles; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double sp = 30 + rng.nextDouble() * spread;
            double maxLife = Math.min(((double) count / (count + 1)), 0.4 + count * 0.22 + rng.nextDouble() * (0.4 + count * 0.1));
            int rgb = rng.nextDouble() < whiteFrac ? 0xFFFFFF : color;
            sparks.add(new Spark(x, y, Math.cos(a) * sp, Math.sin(a) * sp, maxLife, rgb));
        }
    }

    private int scaleParticles(int particles) {
        int load = comets.size() + sparks.size();
        double scale = Math.clamp(1.0 - (load - LOAD_LOW) / (double) (LOAD_HIGH - LOAD_LOW), MIN_PARTICLE_SCALE, 1.0);
        return Math.max(2, (int) Math.round(particles * scale));
    }

    private void drawSparks(double dt, int left, int top, int right, int bottom) {
        Iterator<Spark> it = sparks.iterator();
        while (it.hasNext()) {
            Spark p = it.next();
            if (p.update(dt, left, top, right, bottom)) p.draw(batch);
            else it.remove();
        }
    }

    private void drawTwinkles(double dt) {
        Iterator<Twinkle> it = twinkles.iterator();
        while (it.hasNext()) {
            Twinkle t = it.next();
            if (t.update(dt)) t.draw(batch);
            else it.remove();
        }
    }

    private static double lifespanFor(int count) {
        if (count <= 1) return LIFE_AT_1;
        double t = (count - 1.0) / (LIFE_FULL_COUNT - 1.0);
        return Math.max(LIFE_AT_20, LIFE_AT_1 + (LIFE_AT_20 - LIFE_AT_1) * t);
    }

    private double elapsedSeconds() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        return Math.min(dt, MAX_DT);
    }

    private static double snap(double v, int origin, double off, double step) {
        return origin + off + Math.round((v - origin - off) / step) * step;
    }

    private static int groupRgb(int size) {
        if (size <= 1) return 0xFFFFFF;
        double hue = (((60 - (size - 2) * 30.0) % 360) + 360) % 360;
        return hsv(hue);
    }

    private static int hsv(double h) {
        double s = 0.85, v = 0.5;
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = v - c;
        double r, g, b;
        if (h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        int ri = (int) ((r + m) * 255), gi = (int) ((g + m) * 255), bi = (int) ((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
