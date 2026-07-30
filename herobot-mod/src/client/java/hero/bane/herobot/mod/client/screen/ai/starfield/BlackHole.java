package hero.bane.herobot.mod.client.screen.ai.starfield;

final class BlackHole {
    private static final double MIN_LIFE = 5.0;
    private static final double MAX_LIFE = 15.0;
    private static final double ABSORB_BONUS = 0.5;
    private static final double GROW_STEP = 0.25;

    private static final double GROW_IN = 0.7;
    private static final double CLOSE_FADE = 0.6;
    private static final double BASE_R = 10.0;
    private static final double R_PER_LIFE = 2.0;
    private static final double MAX_R = 60;
    private static final double BLACK_FRAC = 0.8;
    private static final double PHOTON = 2.0;
    private static final double HALO_W = 8.0;
    private static final double BAND = 1.5;
    private static final double FADE = 6.0;
    private static final double LOBE_K = 4.0;
    private static final double LOBE_FLAT = 1.6;
    private static final double LOBE_NEAR = 0.6;
    private static final double LOBE_CROWN = 1.6;
    private static final double DISK_K = 2.8;
    private static final double LOBE_END = 0.07;
    private static final double LOBE_PEAK = 0.75;
    private static final double DISK_DROP = 0.18;
    private static final double SUB_RING_FRAC = 0.9;
    private static final double SUB_RING_DIM = 0.7;
    private static final double SUB_RING_HALF = 0.5;
    private static final double PHOTON_HALF = 1.5;
    private static final double RING_EDGE = 0.4;
    private static final double SMOTHER_PAD = 6.0;
    private static final int COVER_SAMPLES = 24;

    private static final double LUT_STEP = 0.25;
    private static final double LUT_INV = 1.0 / LUT_STEP;

    private static final double ORBIT_R = 260.0;
    private static final double GRAVITY = 500.0;
    private static final double TANGENT = 260.0;
    private static final double KICK = 200.0;
    private static final double MAX_SPEED = 300.0;
    private static final double SPAGHETTI_FRAC = 2.5;

    private static final double GRAB_PAD = 6.0;
    private static final double GRAB_MIN = 12.0;

    private static final double SPIN = -1.0;
    private static final double SPIRAL_LEAD = 1.1;
    private static final double SPIRAL_MIN_PAD = 3.0;
    private static final double SPIRAL_BLEND = 2.2;
    private static final double SPIRAL_PITCH_OUT = 0.05;
    private static final double SPIRAL_PITCH_IN = 0.45;
    private static final double SPIRAL_PITCH_K = 2.0;
    private static final double SPIRAL_RAMP = 0.8;
    private static final double SPIRAL_PITCH_MAX = 2.5;
    private static final double SPIRAL_OMEGA = 3.0;
    private static final double SPIRAL_OMEGA_MAX = 16.0;

    double x, y;
    private final double hand;
    private final double cos, sin;
    private double age;
    private double life = MIN_LIFE;
    private boolean absorbedThisFrame;
    private double growBucket, growLeft, growRate, growTimer;
    private double birthFrom = MIN_LIFE;

    private double[] envLut, diskLut, bandHLut;
    private int lutN;
    private int rowLo, rowHi;

    BlackHole(double x, double y) {
        this.x = x;
        this.y = y;
        double rad = Math.toRadians(Slant.slanter(0.25));
        this.cos = Math.cos(rad);
        this.sin = Math.sin(rad);
        this.hand = SPIN;
    }

    void moveTo(double nx, double ny) {
        x = nx;
        y = ny;
    }

    double grabRadius() {
        return Math.max(GRAB_MIN, radius() + GRAB_PAD);
    }

    boolean grabbed(double px, double py) {
        double r = grabRadius();
        double dx = x - px, dy = y - py;
        return dx * dx + dy * dy <= r * r;
    }

    void fillToMax() {
        life = MAX_LIFE;
        birthFrom = 0;
    }

    boolean drained() {
        return life <= 0;
    }

    private double appear() {
        return Math.min(1.0, age / GROW_IN);
    }

    private double closeFrac() {
        return Math.clamp(life / CLOSE_FADE, 0, 1);
    }

    double coreRadius() {
        double l = life;
        if (age < GROW_IN) l = birthFrom + (l - birthFrom) * (age / GROW_IN);
        return Math.min(MAX_R, BASE_R + Math.max(0, l) * R_PER_LIFE);
    }

    double radius() {
        return coreRadius() * appear() * closeFrac();
    }

    double blackRadius() {
        return radius() * BLACK_FRAC;
    }

    boolean inRange(Comet c) {
        return inRange(c.x, c.y);
    }

    boolean inRange(double px, double py) {
        double dx = x - px, dy = y - py;
        return dx * dx + dy * dy <= ORBIT_R * ORBIT_R;
    }

    double tidal(double px, double py) {
        double outer = coreRadius() * SPAGHETTI_FRAC;
        double innerR = Math.max(1.0, blackRadius());
        if (outer <= innerR) return 0;
        double d = Math.hypot(x - px, y - py);
        double t = Math.clamp((outer - d) / (outer - innerR), 0, 1);
        return t * t;
    }

    void pull(Comet c, double dt) {
        c.straight = false;
        double dx = x - c.x, dy = y - c.y;
        double d = Math.hypot(dx, dy);
        if (d < 0.01) return;
        double inx = dx / d, iny = dy / d;
        double tnx = -iny * hand, tny = inx * hand;
        double w = Math.clamp(1.0 - d / ORBIT_R, 0, 1);
        double grav = w * w;

        double sizeMul = coreRadius() / (BASE_R + MIN_LIFE * R_PER_LIFE);
        double gAcc = Math.pow(GRAVITY * (0.15 + grav), 1.5) * sizeMul;
        c.vx += inx * gAcc * dt;
        c.vy += iny * gAcc * dt;

        double tAcc = TANGENT * (1 - grav);
        c.vx += tnx * tAcc * dt;
        c.vy += tny * tAcc * dt;

        double vin = Math.hypot(c.vx, c.vy);
        if (vin > 1e-3) {
            double tangentialFrac = Math.abs(c.vx * tnx + c.vy * tny) / vin;
            c.vx += tnx * KICK * (1 - tangentialFrac) * dt;
            c.vy += tny * KICK * (1 - tangentialFrac) * dt;
        }

        double radialToward = c.vx * inx + c.vy * iny;
        if (radialToward < 0) {
            double outward = -radialToward;
            c.vx += inx * outward + tnx * outward;
            c.vy += iny * outward + tny * outward;
        }

        double sp = Math.hypot(c.vx, c.vy);
        if (sp > MAX_SPEED) {
            double k = MAX_SPEED / sp;
            c.vx *= k;
            c.vy *= k;
        }
    }

    double lockRadius() {
        return Math.max(blackRadius() + SPIRAL_MIN_PAD, coreRadius() * SPAGHETTI_FRAC * SPIRAL_LEAD);
    }

    boolean captures(Comet c) {
        double r = lockRadius();
        double dx = x - c.x, dy = y - c.y;
        return dx * dx + dy * dy <= r * r;
    }

    void spiral(Comet c, double dt) {
        if (dt <= 1e-6) return;
        double dx = c.x - x, dy = c.y - y;
        double d = Math.max(1e-4, Math.hypot(dx, dy));
        if (!c.spiraling) {
            c.spiraling = true;
            c.spiralAngle = Math.atan2(dy, dx);
            c.spiralR = d;
            c.spiralBlend = 0;
            c.spiralTime = 0;
            c.spiralHand = SPIN;
        }
        c.straight = false;
        c.spiralBlend = Math.min(1.0, c.spiralBlend + dt * SPIRAL_BLEND);
        c.spiralTime += dt;
        double e = c.spiralBlend;
        double b = e * e * (3 - 2 * e);

        c.spiralAngle += wrapPi(Math.atan2(dy, dx) - c.spiralAngle) * (1 - b);
        c.spiralR += (d - c.spiralR) * (1 - b);

        double lock = lockRadius();
        double t = Math.clamp(c.spiralR / lock, 0, 1);
        double omega = Math.min(SPIRAL_OMEGA_MAX, SPIRAL_OMEGA * Math.sqrt(lock / Math.max(1.0, c.spiralR)));
        double pitch = SPIRAL_PITCH_OUT + (SPIRAL_PITCH_IN - SPIRAL_PITCH_OUT) * Math.pow(1 - t, SPIRAL_PITCH_K);
        pitch = Math.min(SPIRAL_PITCH_MAX, pitch * (1 + SPIRAL_RAMP * c.spiralTime));
        c.spiralAngle += omega * c.spiralHand * dt;
        c.spiralR *= Math.exp(-omega * pitch * dt);

        double tx = x + Math.cos(c.spiralAngle) * c.spiralR;
        double ty = y + Math.sin(c.spiralAngle) * c.spiralR;
        double bx = c.x + c.vx * dt, by = c.y + c.vy * dt;
        c.vx = (bx + (tx - bx) * b - c.x) / dt;
        c.vy = (by + (ty - by) * b - c.y) / dt;
    }

    private static double wrapPi(double a) {
        double t = (a + Math.PI) % (2 * Math.PI);
        if (t < 0) t += 2 * Math.PI;
        return t - Math.PI;
    }

    double shadowCoverage(double x0, double y0, double x1, double y1) {
        double r = blackRadius();
        if (r < 0.5) return 0;
        int inside = 0, total = 0;
        for (int j = 0; j < COVER_SAMPLES; j++) {
            double sy = y + r * (2.0 * (j + 0.5) / COVER_SAMPLES - 1);
            for (int i = 0; i < COVER_SAMPLES; i++) {
                double sx = x + r * (2.0 * (i + 0.5) / COVER_SAMPLES - 1);
                double dx = sx - x, dy = sy - y;
                if (dx * dx + dy * dy > r * r) continue;
                total++;
                if (sx >= x0 && sx <= x1 && sy >= y0 && sy <= y1) inside++;
            }
        }
        return total == 0 ? 0 : (double) inside / total;
    }

    void drain(double seconds) {
        life -= seconds;
        if (life <= 0) age = Math.max(age, MIN_LIFE);
    }

    boolean smothers(double px, double py) {
        double r = Math.max(3.0, blackRadius()) + SMOTHER_PAD;
        double dx = x - px, dy = y - py;
        return dx * dx + dy * dy <= r * r;
    }

    boolean swallows(Comet c) {
        double r = Math.max(3.0, blackRadius() + PHOTON);
        double dx = x - c.x, dy = y - c.y;
        return dx * dx + dy * dy <= r * r;
    }

    void feed(boolean bonus) {
        if (bonus) growBucket += ABSORB_BONUS;
        absorbedThisFrame = true;
    }

    void growToMax() {
        growBucket += Math.max(0, MAX_LIFE - (life + growLeft + growBucket));
    }

    boolean update(double dt) {
        age += dt;
        if (!absorbedThisFrame) life -= dt;
        absorbedThisFrame = false;
        growTimer += dt;
        while (growTimer >= GROW_STEP) {
            growTimer -= GROW_STEP;
            growLeft += growBucket;
            growRate = growLeft / GROW_STEP;
            growBucket = 0;
        }
        if (growLeft > 0) {
            double add = Math.min(growLeft, growRate * dt);
            life = Math.min(MAX_LIFE, life + add);
            growLeft -= add;
        }
        return life > 0 || age < MIN_LIFE || growLeft > 0 || growBucket > 0;
    }

    void draw(PixelBatch batch) {
        double r = radius();
        if (r < 1) return;
        double s = appear() * closeFrac();
        int cx = (int) x, cy = (int) y;
        double core = blackRadius();
        double haloW = HALO_W + r * 0.35;
        double rPhoton = core + PHOTON;
        double rGlow = r + haloW;
        double bandSpan = rGlow * 1.5;
        double bandHalf = BAND + r * 0.04;
        double bandDrop = rGlow * DISK_DROP;
        double spin = age * 2.2;
        double band4 = bandHalf * 4.0;
        double capR = Math.max(1.5, bandSpan * 0.06);
        double inner = bandSpan - capR;
        double capH = rGlow * LOBE_END;
        double lobePeak = rGlow * LOBE_PEAK;
        double rSub = core * SUB_RING_FRAC;
        int reach = (int) Math.ceil(Math.max(bandSpan, rGlow + bandDrop + FADE)) + 1;

        int coreArgb = (int) (255 * s) << 24;
        double k255 = 255 * s, k205 = 205 * s, k90 = 90 * s;
        double twoBandHalfSq = 2.0 * bandHalf * bandHalf;
        double ringGate = (core + 5.0) * (core + 5.0);
        double envGate = lobePeak + FADE;
        double bandGateHi = band4 + bandDrop;
        boolean subOn = rSub >= 2, photonOn = rPhoton >= 2;

        double cosSpin = Math.cos(spin), sinSpin = Math.sin(spin);
        double flowPhase = spin * 1.6;
        buildTables(bandSpan, inner, capH, lobePeak, bandDrop);

        double spanY = Math.max(envGate, Math.max(bandGateHi, band4));
        int reachY = Math.min(reach, (int) Math.ceil(bandSpan * Math.abs(sin) + spanY * Math.abs(cos)) + 1);

        for (int py = -reachY; py <= reachY; py++) {
            double pySin = py * sin, pyCos = py * cos;
            if (!rowRange(pySin, pyCos, bandSpan, spanY, reach)) continue;
            int pxFrom = rowLo, pxTo = rowHi;
            for (int px = pxFrom; px <= pxTo; px++) {
                double lx = px * cos + pySin;
                double ly = -px * sin + pyCos;
                double hx = Math.abs(lx);
                int d2 = px * px + py * py;
                boolean nearCore = d2 <= ringGate;
                double d = nearCore ? Math.sqrt(d2) : 0;

                if (nearCore && d <= core) {
                    batch.pixel(cx + px, cy + py, coreArgb);
                } else if (hx <= bandSpan) {
                    double ay = Math.abs(ly);
                    if (ay <= envGate) {
                        double env = sample(envLut, hx);
                        if (hx > inner) {
                            double u = (hx - inner) / capR;
                            env *= Math.sqrt(Math.max(0, 1 - u * u));
                        }
                        double out = ay - env;
                        if (env > 0.5 && out <= FADE) {
                            int a, rgb;
                            if (out <= 0) {
                                double aziSym = 0.82 + 0.18 * sinDouble(lx, ay, cosSpin, sinSpin);
                                double q = ay / env;
                                a = (int) (k205 * (1 - q * q) * aziSym);
                                rgb = 0xFFFFFF;
                            } else {
                                a = (int) (k90 * (1 - out / FADE));
                                rgb = 0;
                            }
                            if (a > 4) batch.pixel(cx + px, cy + py, (a << 24) | rgb);
                        }
                    }
                }

                if (nearCore) {
                    double sub = subOn ? ringWeight(d, rSub, SUB_RING_HALF) : 0;
                    double photon = photonOn ? ringWeight(d, rPhoton, PHOTON_HALF) : 0;
                    if (sub > 0 || photon > 0) {
                        double azi = 0.82 + 0.18 * sinDouble(lx, ly, cosSpin, sinSpin);
                        if (sub > 0) {
                            int a = (int) (k255 * azi * sub * SUB_RING_DIM);
                            if (a > 4) batch.pixel(cx + px, cy + py, (a << 24) | 0xFFFFFF);
                        }
                        if (photon > 0) {
                            int a = (int) (k255 * azi * photon);
                            if (a > 4) batch.pixel(cx + px, cy + py, (a << 24) | 0xFFFFFF);
                        }
                    }
                }

                if (hx <= bandSpan && ly >= -band4 && ly <= bandGateHi) {
                    double centerY = sample(diskLut, hx);
                    double dyb = ly - centerY;
                    if (dyb <= band4 && dyb >= -band4) {
                        double flow = 0.72 + 0.28 * Math.sin(lx * 0.18 - flowPhase);
                        double bandV = Math.exp(-(dyb * dyb) / twoBandHalfSq);
                        double bandH = sample(bandHLut, hx);
                        int a = (int) (k255 * Math.clamp(bandV * bandH * flow, 0, 1));
                        if (a > 4) batch.pixel(cx + px, cy + py, (a << 24) | goldColor(Math.min(1.0, hx / bandSpan)));
                    }
                }
            }
        }
    }

    private boolean rowRange(double pySin, double pyCos, double spanX, double spanY, int reach) {
        double lo = -reach, hi = reach;
        if (Math.abs(cos) > 1e-9) {
            double a = (-spanX - pySin) / cos, b = (spanX - pySin) / cos;
            if (a > b) {
                double t = a;
                a = b;
                b = t;
            }
            if (a > lo) lo = a;
            if (b < hi) hi = b;
        } else if (Math.abs(pySin) > spanX) {
            return false;
        }
        if (Math.abs(sin) > 1e-9) {
            double a = (pyCos - spanY) / sin, b = (pyCos + spanY) / sin;
            if (a > b) {
                double t = a;
                a = b;
                b = t;
            }
            if (a > lo) lo = a;
            if (b < hi) hi = b;
        } else if (Math.abs(pyCos) > spanY) {
            return false;
        }
        rowLo = Math.max(-reach, (int) Math.ceil(lo - 1e-9));
        rowHi = Math.min(reach, (int) Math.floor(hi + 1e-9));
        return rowLo <= rowHi;
    }

    private void buildTables(double bandSpan, double inner, double capH, double lobePeak, double bandDrop) {
        int n = (int) Math.ceil(bandSpan * LUT_INV) + 2;
        if (envLut == null || envLut.length < n) {
            envLut = new double[n];
            diskLut = new double[n];
            bandHLut = new double[n];
        }
        lutN = n;
        for (int k = 0; k < n; k++) {
            double h = k * LUT_STEP;
            envLut[k] = capH + (lobePeak - capH) * glowProfile(h, inner);
            diskLut[k] = bandDrop * lobeProfile(h, bandSpan, DISK_K);
            double t = Math.min(1.0, h / bandSpan);
            bandHLut[k] = Math.clamp(1.0 - t * Math.sqrt(t), 0, 1);
        }
    }

    private double sample(double[] table, double hx) {
        double f = hx * LUT_INV;
        int k = (int) f;
        if (k < 0) return table[0];
        if (k + 1 >= lutN) return table[lutN - 1];
        return table[k] + (table[k + 1] - table[k]) * (f - k);
    }

    private static double sinDouble(double ax, double ay, double cosPhase, double sinPhase) {
        double m2 = ax * ax + ay * ay;
        if (m2 < 1e-12) return sinPhase;
        return (2.0 * ay * ax / m2) * cosPhase + ((ax * ax - ay * ay) / m2) * sinPhase;
    }

    private static double ringWeight(double d, double radius, double half) {
        if (radius < 2) return 0;
        double off = Math.abs(d - radius);
        if (off <= half) return 1.0;
        return off <= half + 1.0 ? RING_EDGE : 0.0;
    }

    private static double lobeProfile(double hx, double span, double k) {
        double t = Math.clamp(hx / span, 0, 1);
        if (t < 1e-4) return 1.0;
        double u = Math.PI * t;
        double base = Math.max(0, Math.sin(u) / u);
        if (k == LOBE_K) {
            double b2 = base * base;
            return b2 * b2;
        }
        return Math.pow(base, k);
    }

    private static double glowProfile(double hx, double span) {
        double t = Math.clamp(hx / span, 0, 1);
        double shoulder = 1 - Math.pow(1 - lobeProfile(hx, span, LOBE_K), LOBE_FLAT);
        double crown = Math.pow(1 - t * t, LOBE_CROWN);
        return LOBE_NEAR * shoulder + (1 - LOBE_NEAR) * crown;
    }

    private static int goldColor(double t) {
        double heat = Math.clamp(1 - t, 0, 1);
        int g = (int) Math.clamp(205 + heat * 50, 0, 255);
        int b = (int) Math.clamp(120 + heat * 110, 0, 255);
        return (255 << 16) | (g << 8) | b;
    }
}
