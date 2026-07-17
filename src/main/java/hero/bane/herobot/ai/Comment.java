package hero.bane.herobot.ai;

import java.util.Arrays;

public final class Comment {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int UNDERLINE = 4;
    public static final int STRIKE = 8;

    private final int id;
    private double x;
    private double y;
    private String text;
    private byte[] styles;

    private int attachedTo = -1;

    private double offX;
    private double offY;

    public transient double w;
    public transient double h;

    public Comment(int id, double x, double y, String text) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.text = text == null ? "" : text;
        this.styles = new byte[this.text.length()];
    }

    public int id() { return id; }
    public double x() { return x; }
    public double y() { return y; }
    public void setPos(double x, double y) { this.x = x; this.y = y; }

    public String text() { return text; }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        this.styles = new byte[this.text.length()];
    }

    public int styleAt(int i) {
        return i >= 0 && i < styles.length ? styles[i] & 0xFF : 0;
    }

    public byte[] styles() { return styles; }

    public void setStyles(byte[] s) {
        if (s != null && s.length == text.length()) this.styles = s;
    }

    public void insert(int idx, String s, int style) {
        if (s == null || s.isEmpty()) return;
        idx = Math.clamp(idx, 0, text.length());
        text = text.substring(0, idx) + s + text.substring(idx);
        byte[] ns = new byte[styles.length + s.length()];
        System.arraycopy(styles, 0, ns, 0, idx);
        Arrays.fill(ns, idx, idx + s.length(), (byte) style);
        System.arraycopy(styles, idx, ns, idx + s.length(), styles.length - idx);
        styles = ns;
    }

    public void delete(int from, int to) {
        from = Math.clamp(from, 0, text.length());
        to = Math.clamp(to, from, text.length());
        if (from == to) return;
        text = text.substring(0, from) + text.substring(to);
        byte[] ns = new byte[styles.length - (to - from)];
        System.arraycopy(styles, 0, ns, 0, from);
        System.arraycopy(styles, to, ns, from, styles.length - to);
        styles = ns;
    }

    public void toggleStyle(int from, int to, int flag) {
        from = Math.clamp(from, 0, styles.length);
        to = Math.clamp(to, from, styles.length);
        if (from == to) return;
        boolean all = true;
        for (int i = from; i < to; i++) {
            if ((styles[i] & flag) == 0) { all = false; break; }
        }
        for (int i = from; i < to; i++) {
            styles[i] = (byte) (all ? styles[i] & ~flag : styles[i] | flag);
        }
    }

    public boolean rangeHasStyle(int from, int to, int flag) {
        from = Math.clamp(from, 0, styles.length);
        to = Math.clamp(to, from, styles.length);
        if (from == to) return false;
        for (int i = from; i < to; i++) {
            if ((styles[i] & flag) == 0) return false;
        }
        return true;
    }

    public int attachedTo() { return attachedTo; }
    public void setAttachedTo(int blockId) { this.attachedTo = blockId; }

    public double offX() { return offX; }
    public double offY() { return offY; }
    public void setOffset(double offX, double offY) { this.offX = offX; this.offY = offY; }
}
