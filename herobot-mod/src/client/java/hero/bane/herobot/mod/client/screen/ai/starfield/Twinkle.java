package hero.bane.herobot.mod.client.screen.ai.starfield;

final class Twinkle {
    static final double LEN = 8.0;
    static final double BIG_LEN = 22.0;
    static final double EXPIRY_LEN = 4.0;

    private static final double DUR = 0.5;

    private final double x, y, length;
    private final boolean eightPoint;
    private final double cos, sin;
    private double age;

    Twinkle(double x, double y, double length, boolean eightPoint) {
        this.x = x;
        this.y = y;
        this.length = length;
        this.eightPoint = eightPoint;
        double rad = Math.toRadians(Slant.slanter(0.8));
        this.cos = Math.cos(rad);
        this.sin = Math.sin(rad);
    }

    boolean update(double dt) {
        age += dt;
        return age < DUR;
    }

    void draw(PixelBatch batch) {
        double env = Math.sin(age / DUR * Math.PI);
        int cx = (int) x, cy = (int) y;

        int reach = (int) Math.round(length * env);
        for (int d = 1; d <= reach; d++) {
            int a = (int) ((1.0 - (double) d / (reach + 1)) * env * 255);
            if (a <= 4) continue;
            int col = (a << 24) | 0xFFFFFF;
            plot(batch, cx, cy, d, 0, col);
            plot(batch, cx, cy, -d, 0, col);
            plot(batch, cx, cy, 0, d, col);
            plot(batch, cx, cy, 0, -d, col);
        }

        if (eightPoint) {
            int diag = reach / 2;
            for (int d = 1; d <= diag; d++) {
                int a = (int) ((1.0 - (double) d / (diag + 1)) * env * 160);
                if (a <= 4) continue;
                int col = (a << 24) | 0xFFFFFF;
                plot(batch, cx, cy, d, d, col);
                plot(batch, cx, cy, -d, d, col);
                plot(batch, cx, cy, d, -d, col);
                plot(batch, cx, cy, -d, -d, col);
            }
        }

        batch.rect(cx - 1, cy - 1, cx + 2, cy + 2, ((int) (env * 120) << 24) | 0xFFFFFF);
        batch.pixel(cx, cy, ((int) (env * 255) << 24) | 0xFFFFFF);
    }

    private void plot(PixelBatch batch, int cx, int cy, double dx, double dy, int col) {
        batch.pixel(cx + (int) Math.round(dx * cos - dy * sin), cy + (int) Math.round(dx * sin + dy * cos), col);
    }
}
