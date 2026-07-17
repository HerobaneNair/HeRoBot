package hero.bane.herobot.client.screen.ai.starfield;

import hero.bane.herobot.client.EditorPrefs;
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
    private final List<Spark> sparks = new ArrayList<>();
    private final List<Twinkle> twinkles = new ArrayList<>();
    private final PixelBatch batch = new PixelBatch();
    private final java.util.Random rng = new java.util.Random();
    private long lastNanos;

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

    public void sparkle(double x, double y) {
        twinkles.add(new Twinkle(x, y, Twinkle.LEN, true));
    }

    public void spark(double x, double y) {
        double ang = rng.nextDouble() * Math.PI * 2;
        double sp = 25 + rng.nextDouble() * 35;
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
    }

    public void render(GuiGraphics g, int left, int top, int right, int bottom,
                       double panX, double panY, double zoom, double mouseX, double mouseY,
                       List<double[]> blocks, List<double[]> wires) {
        double dt = elapsedSeconds();
        batch.begin(g, left, top, right, bottom);

        double step = Math.max(8, 40 * zoom);
        double ox = panX % step, oy = panY % step;
        boolean mouseInside = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;

        for (Comet c : comets)
            if (!c.straight) applyFieldForces(c, dt, left, top, step, ox, oy, mouseInside, mouseX, mouseY);
        ClusterPhysics.apply(comets, dt);
        for (Comet c : comets) c.relock();
        if (mouseInside) for (Comet c : comets) if (c.straight) applyMouseForce(c, dt, mouseX, mouseY);

        double lifespan = lifespanFor(comets.size());
        Iterator<Comet> it = comets.iterator();
        while (it.hasNext()) {
            Comet c = it.next();
            c.advance(dt);
            if (!c.straight) c.bounce(blocks, wires);
            if (c.exploded()) {
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

        for (ClusterPhysics.Blast b : ClusterPhysics.collide(comets)) explode(b.x(), b.y(), b.size());

        drawSparks(dt, left, top, right, bottom);
        drawTwinkles(dt);
        batch.submit(g);
    }

    public void explodeSelection(double x0, double y0, double x1, double y1) {
        double lx = Math.min(x0, x1), hx = Math.max(x0, x1);
        double ly = Math.min(y0, y1), hy = Math.max(y0, y1);
        Iterator<Comet> it = comets.iterator();
        while (it.hasNext()) {
            Comet c = it.next();
            if (c.x >= lx && c.x <= hx && c.y >= ly && c.y <= hy) {
                explode(c.x, c.y, 1);
                it.remove();
            }
        }
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
                                  boolean mouseInside, double mouseX, double mouseY) {
        double gx = snap(c.x, left, ox, step) - c.x, gy = snap(c.y, top, oy, step) - c.y;
        double gd = Math.hypot(gx, gy);
        if (gd > 0.01 && gd < step) {
            double pull = GRID_PULL * (1.0 - gd / step);
            c.vx += gx / gd * pull * dt;
            c.vy += gy / gd * pull * dt;
        }

        if (mouseInside) applyMouseForce(c, dt, mouseX, mouseY);
    }

    private void applyMouseForce(Comet c, double dt, double mouseX, double mouseY) {
        double mx = c.x - mouseX, my = c.y - mouseY, md = Math.hypot(mx, my);
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
        double s = 0.85, v = 1.0;
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
