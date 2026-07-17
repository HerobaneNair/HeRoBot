package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.block.BlockCategory;
import hero.bane.herobot.ai.block.BlockDef;
import hero.bane.herobot.ai.block.BlockDefRegistry;
import hero.bane.herobot.ai.block.BlockType;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockPalette {
    private static final int ROW_H = 13;
    private static final int SEARCH_H = 16;

    private record Row(BlockType type, String label, int color, BlockCategory cat) {
        boolean isHeader() { return type == null; }
    }

    private static final Set<BlockType> HIDDEN =
            EnumSet.of(BlockType.AND, BlockType.OR, BlockType.LOOP_ITER);

    private final List<Row> rows = new ArrayList<>();

    private final Set<BlockCategory> collapsed = EnumSet.noneOf(BlockCategory.class);
    private final Font font;
    private int x, y, w, h;
    private int scroll;

    private EditBox searchBox;
    private boolean searchFocused;
    private String query = "";

    public BlockPalette(Font font) {
        this.font = font;
        for (BlockCategory cat : BlockCategory.values()) {
            rows.add(new Row(null, cat.display(), cat.color(), cat));
            for (BlockDef def : BlockDefRegistry.all()) {
                if (def.category() == cat && def.type() != BlockType.BLOCK_END && !HIDDEN.contains(def.type())) {
                    rows.add(new Row(def.type(), paletteLabel(def), cat.color(), cat));
                }
            }
        }
    }

    private static String paletteLabel(BlockDef def) {
        if (def.type() == BlockType.SET_VAR) return "set var";
        if (def.type() == BlockType.CHANGE_VAR) return "change var";
        if (def.type() == BlockType.YAW) return "yaw/pitch";
        if (def.type() == BlockType.EVERY_X_TICKS) return "every x ticks";
        if (def.type() == BlockType.BREAK) return "break x";
        if (def.type() == BlockType.WAIT) return "wait x ticks";
        if (def.type() == BlockType.TERNARY) return "ternary";
        if (def.type() == BlockType.GET_COUNT) return "get count/dura";
        if (def.type() == BlockType.MAX_COUNT) return "max count/dura";
        return def.label();
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        searchBox = new EditBox(font, x + 3, y + 3, w - 6, 11, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean isSearchFocused() {
        return searchFocused;
    }

    private int rowsTop() {
        return y + SEARCH_H + 2;
    }

    private List<Row> visibleRows() {
        List<Row> v = new ArrayList<>();
        if (!query.isBlank()) {
            for (Row r : rows) {
                if (!r.isHeader() && r.label().toLowerCase(Locale.ROOT).contains(query)) v.add(r);
            }
            return v;
        }
        for (Row r : rows) {
            if (r.isHeader() || !collapsed.contains(r.cat())) v.add(r);
        }
        return v;
    }

    private int maxScroll() {
        return Math.max(0, visibleRows().size() * ROW_H - (h - SEARCH_H) + 4);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, 0xF01A1A1A);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF000000);

        g.fill(x, y, x + w, y + SEARCH_H, 0xFF101010);
        if (searchBox != null) searchBox.render(g, mouseX, mouseY, 0f);

        boolean bulkHover = shiftHeld() && hoveredHeader(mouseX, mouseY);
        g.enableScissor(x, y + SEARCH_H, x + w, y + h);
        int ry = rowsTop() - scroll;
        for (Row row : visibleRows()) {
            if (ry + ROW_H >= y + SEARCH_H && ry <= y + h) {
                boolean hover = mouseX >= x && mouseX < x + w - 1 && mouseY >= ry && mouseY < ry + ROW_H;
                if (row.isHeader()) {
                    g.fill(x, ry, x + w, ry + ROW_H, (hover || bulkHover) ? 0xFF151515 : 0xFF000000);
                    String arrow = collapsed.contains(row.cat()) ? "▸ " : "▾ ";
                    g.drawString(font, arrow + row.label(), x + 4, ry + 3, row.color(), false);
                } else {
                    if (hover) g.fill(x, ry, x + w - 1, ry + ROW_H, 0x66FFFFFF);
                    g.fill(x + 3, ry + 3, x + 9, ry + ROW_H - 2, row.color());
                    String label = trim(row.label(), w - 16);
                    g.drawString(font, label, x + 12, ry + 3, 0xFFE0E0E0, false);
                }
            }
            ry += ROW_H;
        }
        g.disableScissor();
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(b.toString() + s.charAt(i) + "…") > maxW) break;
            b.append(s.charAt(i));
        }
        return b + "…";
    }

    public boolean scrolled(double amount) {
        scroll = clamp((int) (scroll - amount * ROW_H * 2));
        return true;
    }

    private int clamp(int s) {
        return Math.clamp(s, 0, maxScroll());
    }

    private void toggle(BlockCategory cat) {
        if (!collapsed.remove(cat)) collapsed.add(cat);
        scroll = clamp(scroll);
    }

    public boolean clickedSearch(MouseButtonEvent event, boolean doubled) {
        double my = event.y();
        if (my >= y && my < y + SEARCH_H && inside(event.x(), my)) {
            searchFocused = true;
            if (searchBox != null) {
                searchBox.setFocused(true);
                searchBox.mouseClicked(event, doubled);
            }
            return true;
        }
        unfocusSearch();
        return false;
    }

    public void unfocusSearch() {
        searchFocused = false;
        if (searchBox != null) searchBox.setFocused(false);
    }

    private void refreshQuery() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (!q.equals(query)) {
            query = q;
            scroll = 0;
        }
    }

    public boolean keyPressed(KeyEvent e) {
        if (!searchFocused) return false;
        int key = e.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searchBox != null) searchBox.setValue("");
            refreshQuery();
            unfocusSearch();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            unfocusSearch();
            return true;
        }
        if (searchBox != null) searchBox.keyPressed(e);
        refreshQuery();
        return true;
    }

    public boolean charTyped(CharacterEvent e) {
        if (!searchFocused) return false;
        if (searchBox != null) searchBox.charTyped(e);
        refreshQuery();
        return true;
    }

    public BlockType hovered(double mx, double my) {
        if (!inside(mx, my) || my < y + SEARCH_H) return null;
        List<Row> visible = visibleRows();
        int idx = (int) ((my - (rowsTop() - scroll)) / ROW_H);
        if (idx < 0 || idx >= visible.size()) return null;
        Row row = visible.get(idx);
        return row.isHeader() ? null : row.type();
    }

    public BlockType clicked(double mx, double my) {
        if (!inside(mx, my) || my < y + SEARCH_H) return null;
        List<Row> visible = visibleRows();
        int idx = (int) ((my - (rowsTop() - scroll)) / ROW_H);
        if (idx < 0 || idx >= visible.size()) return null;
        Row row = visible.get(idx);
        if (row.isHeader()) {
            if (shiftHeld()) toggleAll(collapsed.contains(row.cat()));
            else toggle(row.cat());
            return null;
        }
        return row.type();
    }

    private boolean hoveredHeader(double mx, double my) {
        if (!inside(mx, my) || my < y + SEARCH_H) return false;
        List<Row> visible = visibleRows();
        int idx = (int) ((my - (rowsTop() - scroll)) / ROW_H);
        if (idx < 0 || idx >= visible.size()) return false;
        return visible.get(idx).isHeader();
    }

    private void toggleAll(boolean expand) {
        collapsed.clear();
        if (!expand) collapsed.addAll(EnumSet.allOf(BlockCategory.class));
        scroll = clamp(scroll);
    }

    private static boolean shiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
