package hero.bane.herobot.client.screen.ai;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class PopupMenu {
    static final int ROW_H = 13;
    static final int MAX_VISIBLE = 5;

    private final Font font;
    public boolean open;
    public int x, y, w, h;
    private int scrollOffset;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    public PopupMenu(Font font) {
        this.font = font;
    }

    public void clear() {
        labels.clear();
        actions.clear();
    }

    public void add(String label, Runnable r) {
        labels.add(label);
        actions.add(r);
    }

    public boolean isEmpty() {
        return labels.isEmpty();
    }

    private int visibleCount() {
        return Math.min(labels.size(), MAX_VISIBLE);
    }

    private int maxScroll() {
        return Math.max(0, labels.size() - MAX_VISIBLE);
    }

    public void openAt(double sx, double sy, int left, int top, int right, int bottom) {
        int maxW = 0;
        for (String l : labels) maxW = Math.max(maxW, font.width(l));
        boolean scrollable = labels.size() > MAX_VISIBLE;
        w = maxW + 14 + (scrollable ? 4 : 0);
        h = visibleCount() * ROW_H + 2;
        x = Math.max(left + 1, (int) Math.min(sx, right - w - 1));
        y = Math.max(top + 1, (int) Math.min(sy, bottom - h - 1));
        scrollOffset = 0;
        open = true;
    }

    public int hitTest(double mx, double my) {
        if (!open || mx < x || mx >= x + w || my < y + 1 || my >= y + h - 1) return -1;
        int local = (int) ((my - (y + 1)) / ROW_H);
        int idx = scrollOffset + local;
        return (local >= 0 && local < visibleCount() && idx < labels.size()) ? idx : -1;
    }

    public boolean scroll(double amount) {
        if (!open || labels.size() <= MAX_VISIBLE) return false;
        scrollOffset = Math.clamp(scrollOffset - (int) Math.signum(amount), 0, maxScroll());
        return true;
    }

    public boolean click(double mx, double my) {
        if (!open) return false;
        int idx = hitTest(mx, my);
        open = false;
        if (idx >= 0) actions.get(idx).run();
        return true;
    }

    public void render(GuiGraphics g, double mx, double my) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
        int hi = hitTest(mx, my);
        int vis = visibleCount();
        for (int local = 0; local < vis; local++) {
            int i = scrollOffset + local;
            int ry = y + 1 + local * ROW_H;
            if (i == hi) g.fill(x, ry, x + w, ry + ROW_H, 0xFF3A5A8A);
            g.drawString(font, labels.get(i), x + 6, ry + 3, 0xFFE0E0E0, false);
        }
        if (labels.size() > MAX_VISIBLE) renderScrollbar(g);
    }

    private void renderScrollbar(GuiGraphics g) {
        int trackX = x + w - 3;
        g.fill(trackX, y + 1, trackX + 2, y + h - 1, 0xFF1A1A1A);
        int trackH = h - 2;
        int thumbH = Math.max(6, trackH * visibleCount() / labels.size());
        int thumbY = y + 1 + (maxScroll() == 0 ? 0 : (trackH - thumbH) * scrollOffset / maxScroll());
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF6A6A6A);
    }
}
