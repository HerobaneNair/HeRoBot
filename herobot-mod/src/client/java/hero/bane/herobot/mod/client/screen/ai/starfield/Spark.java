package hero.bane.herobot.mod.client.screen.ai.starfield;

final class Spark {
    private static final double DRAG = 0.92;

    private double x, y, vx, vy, life;
    private final double maxLife;
    private final int rgb;

    Spark(double x, double y, double vx, double vy, double maxLife, int rgb) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.maxLife = maxLife;
        this.rgb = rgb;
    }

    boolean update(double dt, int left, int top, int right, int bottom) {
        life += dt;
        if (life >= maxLife) return false;
        vx *= DRAG;
        vy *= DRAG;
        x += vx * dt;
        y += vy * dt;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    void draw(PixelBatch batch) {
        double a = 1.0 - life / maxLife;
        int px = (int) x, py = (int) y;
        batch.pixel(px, py, ((int) (a * 255) << 24) | rgb);
        if (a > 0.6) batch.rect(px - 1, py - 1, px + 2, py + 2, ((int) (a * 120) << 24) | rgb);
    }
}
