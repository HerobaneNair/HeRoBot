package hero.bane.herobot.client.screen.ai;

import com.mojang.blaze3d.platform.InputConstants;
import hero.bane.herobot.ai.block.BlockCategory;
import hero.bane.herobot.client.EditorPrefs;
import hero.bane.herobot.ai.block.BlockDef;
import hero.bane.herobot.ai.block.BlockDefRegistry;
import hero.bane.herobot.ai.block.BlockType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class BlockPalette {
    private static final int ROW_H = 13;
    private static final int SEARCH_H = 16;

    private record Row(BlockType type, String label, int color, BlockCategory cat) {
        boolean isHeader() { return type == null; }
    }

    private static final Set<BlockType> HIDDEN =
            EnumSet.of(BlockType.AND, BlockType.OR);

    private static final double DRAG_SLOP = 6;

    private final List<Row> rows = new ArrayList<>();
    private final List<BlockCategory> order = new ArrayList<>();

    private final Set<BlockCategory> collapsed = EnumSet.noneOf(BlockCategory.class);
    private final Font font;
    private int x, y, w, h;
    private int scroll;

    private EditBox searchBox;
    private boolean searchFocused;
    private String query = "";

    private BlockCategory pressCat;
    private double pressX, pressY, dragY;
    private boolean dragging;

    public BlockPalette(Font font) {
        this.font = font;
        loadOrder();
        rebuildRows();
    }

    private void loadOrder() {
        for (String name : EditorPrefs.categoryOrder()) {
            for (BlockCategory cat : BlockCategory.values()) {
                if (cat.name().equals(name) && !order.contains(cat)) order.add(cat);
            }
        }
        for (BlockCategory cat : BlockCategory.values()) {
            if (!order.contains(cat)) order.add(cat);
        }
    }

    private void saveOrder() {
        List<String> names = new ArrayList<>(order.size());
        for (BlockCategory cat : order) names.add(cat.name());
        EditorPrefs.setCategoryOrder(names);
    }

    private void rebuildRows() {
        rows.clear();
        for (BlockCategory cat : order) {
            rows.add(new Row(null, cat.display(), cat.color(), cat));
            for (BlockDef def : BlockDefRegistry.all()) {
                if (def.category() == cat && def.type() != BlockType.BLOCK_END
                        && !HIDDEN.contains(def.type()) && !def.type().refsOwner()) {
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
                    boolean held = dragging && row.cat() == pressCat;
                    g.fill(x, ry, x + w, ry + ROW_H,
                            held ? 0xFF242424 : (hover || bulkHover) ? 0xFF151515 : 0xFF000000);
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
        if (dragging && pressCat != null) renderDropLine(g);
        g.disableScissor();
    }

    private void renderDropLine(GuiGraphics g) {
        int idx = dropIndex(dragY);
        int ry = rowsTop() - scroll;
        for (int i = 0; i < idx; i++) ry += categorySpan(order.get(i));
        int lineY = Math.clamp(ry, y + SEARCH_H + 1, y + h - 1);
        g.fill(x, lineY - 1, x + w - 1, lineY + 1, 0xFF6AA9FF);
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
            pressCat = row.cat();
            pressX = mx;
            pressY = my;
            dragY = my;
            dragging = false;
            return null;
        }
        return row.type();
    }

    public boolean mouseDragged(double mx, double my) {
        if (pressCat == null) return false;
        if (!dragging && Math.hypot(mx - pressX, my - pressY) >= DRAG_SLOP) dragging = true;
        if (dragging) dragY = my;
        return true;
    }

    public boolean mouseReleased(double my) {
        if (pressCat == null) return false;
        BlockCategory cat = pressCat;
        pressCat = null;
        if (!dragging) {
            if (shiftHeld()) toggleAll(allCollapsed());
            else toggle(cat);
            return true;
        }
        dragging = false;
        reorder(cat, dropIndex(my));
        return true;
    }

    private int categorySpan(BlockCategory cat) {
        if (collapsed.contains(cat)) return ROW_H;
        int n = 1;
        for (Row r : rows) if (r.cat() == cat && !r.isHeader()) n++;
        return n * ROW_H;
    }

    private int dropIndex(double my) {
        int ry = rowsTop() - scroll;
        for (int i = 0; i < order.size(); i++) {
            int span = categorySpan(order.get(i));
            if (my < ry + span / 2.0) return i;
            ry += span;
        }
        return order.size();
    }

    private void reorder(BlockCategory cat, int target) {
        int from = order.indexOf(cat);
        if (from < 0) return;
        if (target > from) target--;
        target = Math.clamp(target, 0, order.size() - 1);
        if (target == from) return;
        order.remove(from);
        order.add(target, cat);
        rebuildRows();
        scroll = clamp(scroll);
        saveOrder();
    }

    private boolean allCollapsed() {
        return collapsed.size() == BlockCategory.values().length;
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
