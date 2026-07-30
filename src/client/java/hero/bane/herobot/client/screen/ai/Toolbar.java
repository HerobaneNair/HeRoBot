package hero.bane.herobot.client.screen.ai;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class Toolbar {
    public static final int HEIGHT = 22;
    private static final int ROW_H = 12;

    private record Item(String label, Runnable action, BooleanSupplier enabled) {
        Item(String label, Runnable action) {
            this(label, action, () -> true);
        }
    }

    private static final class Entry {
        final String label;
        final int color;
        final Runnable action;
        final List<Item> menu;
        int x, w, off;

        Entry(String label, int color, Runnable action, List<Item> menu) {
            this.label = label;
            this.color = color;
            this.action = action;
            this.menu = menu;
        }
    }

    private static final int FLASH_TICKS = 8;
    private static final int SORT_FLASH = 0xFFFF7F27;

    private final Font font;
    private final AiEditorScreen host;
    private final List<Entry> entries = new ArrayList<>();
    private final Entry fileEntry;
    private final Entry sortEntry;
    private int openMenu = -1;
    private int groupW;
    private int fileFlash;
    private int sortFlash;

    public Toolbar(Font font, AiEditorScreen host) {
        this.font = font;
        this.host = host;

        fileEntry = new Entry("File", 0xFFCFC030, null, List.of(
                new Item("New", host::newScript, host::hasScriptContent),
                new Item("Open", host::loadDialog),
                new Item("Save", host::saveScript, host::hasScriptContent),
                new Item("Save As", host::saveScriptAs, host::hasScriptContent),
                new Item("Run on Target", host::runOnTarget, host::hasScriptContent),
                new Item("Import", host::importDialog),
                new Item("Copy JSON", host::copyJson),
                new Item("Paste JSON", host::pasteJson),
                new Item("Delete", host::deleteDialog)));
        entries.add(fileEntry);
        sortEntry = new Entry("Sort", 0xFFD8842C, host::sortPressed, null);
        entries.add(sortEntry);
        entries.add(new Entry("Record", 0xFFD84C4C, host::startRecording, null));
        entries.add(new Entry("Shortcuts", 0xFF9B54C6, host::openShortcuts, null));
        entries.add(new Entry("Settings", 0xFF3E74C8, host::openSettings, null));
        entries.add(new Entry("Tutorial", 0xFF3FB255, host::openTutorial, null));

        int off = 0;
        for (Entry e : entries) {
            e.w = font.width(e.label) + 12;
            e.off = off;
            off += e.w + 3;
        }
        groupW = Math.max(0, off - 3);
    }

    private void layout(int width) {
        int startX = Math.max(12, (width - groupW) / 2);
        for (Entry e : entries) e.x = startX + e.off;
    }

    public boolean isMenuOpen() {
        return openMenu >= 0;
    }

    public void closeMenu() {
        openMenu = -1;
    }

    public void flashFile() {
        fileFlash = FLASH_TICKS;
    }

    public void flashSort() {
        sortFlash = FLASH_TICKS;
    }

    public void tick() {
        if (fileFlash > 0) fileFlash--;
        if (sortFlash > 0) sortFlash--;
    }

    public void render(GuiGraphics g, int width, int mouseX, int mouseY) {
        layout(width);
        g.fill(0, 0, width, HEIGHT, 0xFF2A2A2A);
        g.fill(0, HEIGHT, width, HEIGHT + 1, 0xFF000000);

        for (Entry e : entries) {
            boolean hover = mouseX >= e.x && mouseX < e.x + e.w && mouseY >= 2 && mouseY < 20;
            int base = e.color;
            if (e == fileEntry && fileFlash > 0) {
                base = blend(e.color, 0xFFFFD24A, fileFlash / (float) FLASH_TICKS);
            } else if (e == sortEntry && sortFlash > 0) {
                base = blend(e.color, SORT_FLASH, sortFlash / (float) FLASH_TICKS);
            }
            int fill = hover ? brighten(base) : base;
            int label = e == sortEntry
                    ? blend(0xFFFFFFFF, SORT_FLASH, host.sortHoldProgress())
                    : 0xFFFFFFFF;
            g.fill(e.x, 2, e.x + e.w, 20, fill);
            g.fill(e.x, 2, e.x + e.w, 3, brighten(fill));
            g.drawString(font, e.label, e.x + 6, 7, label, false);
        }

        String status = "Script: " + host.script().name();
        g.drawString(font, status, width - font.width(status) - 8, 7, 0xFFB0FFB0, false);

        if (openMenu >= 0) renderMenu(g, mouseX, mouseY);
    }

    private void renderMenu(GuiGraphics g, int mouseX, int mouseY) {
        Entry e = entries.get(openMenu);
        int mw = menuWidth(e);
        int top = HEIGHT + 1;
        int mh = e.menu.size() * ROW_H;

        g.fill(e.x, top, e.x + mw, top + mh, 0xFF1E1E1E);
        g.fill(e.x, top, e.x + mw, top + 1, 0xFF000000);
        g.fill(e.x + mw, top, e.x + mw + 1, top + mh + 1, 0xFF000000);
        g.fill(e.x, top + mh, e.x + mw + 1, top + mh + 1, 0xFF000000);
        g.fill(e.x, top, e.x + 2, top + mh, e.color);

        for (int i = 0; i < e.menu.size(); i++) {
            int ry = top + i * ROW_H;
            Item it = e.menu.get(i);
            boolean enabled = it.enabled().getAsBoolean();
            boolean hover = enabled && mouseX >= e.x && mouseX < e.x + mw && mouseY >= ry && mouseY < ry + ROW_H;
            if (hover) g.fill(e.x + 2, ry, e.x + mw, ry + ROW_H, 0xFF333333);
            g.drawString(font, it.label(), e.x + 6, ry + 2, enabled ? 0xFFE0E0E0 : 0xFF606060, false);
        }
    }

    public boolean mouseClicked(double mx, double my) {
        if (openMenu >= 0) {
            Entry e = entries.get(openMenu);
            int mw = menuWidth(e);
            int top = HEIGHT + 1;
            int mh = e.menu.size() * ROW_H;
            if (mx >= e.x && mx < e.x + mw && my >= top && my < top + mh) {
                int i = (int) ((my - top) / ROW_H);
                if (i >= 0 && i < e.menu.size()) {
                    Item it = e.menu.get(i);
                    if (!it.enabled().getAsBoolean()) return true;
                    openMenu = -1;
                    it.action().run();
                    return true;
                }
            }
        }

        for (int idx = 0; idx < entries.size(); idx++) {
            Entry e = entries.get(idx);
            if (mx >= e.x && mx < e.x + e.w && my >= 2 && my < 20) {
                if (e.menu != null) {
                    openMenu = (openMenu == idx) ? -1 : idx;
                } else {
                    openMenu = -1;
                    e.action.run();
                }
                return true;
            }
        }

        if (openMenu >= 0) {
            openMenu = -1;
            return true;
        }
        return false;
    }

    private int menuWidth(Entry e) {
        int mw = 0;
        for (Item it : e.menu) mw = Math.max(mw, font.width(it.label()));
        return Math.max(mw + 12, e.w);
    }

    private static int blend(int from, int to, float t) {
        t = Math.clamp(t, 0f, 1f);
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static int brighten(int argb) {
        int a = argb >>> 24;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 38);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 38);
        int b = Math.min(255, (argb & 0xFF) + 38);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }
}
