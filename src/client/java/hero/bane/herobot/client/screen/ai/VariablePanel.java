package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.VarDecl;
import hero.bane.herobot.ai.VarType;
import hero.bane.herobot.ai.block.BlockDef;
import hero.bane.herobot.ai.block.BlockDefRegistry;
import hero.bane.herobot.ai.block.BlockDescriptions;
import hero.bane.herobot.ai.block.BlockType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class VariablePanel {
    private static final int ROW_H = 14;
    private static final int HEADER_H = 16;
    private static final int DESC_H = 208;
    private static final double DRAG_SLOP = 4;

    private static final int K_FOLDER = 0;
    private static final int K_VAR = 1;
    private static final int K_UNGROUPED = 2;
    private static final int K_ADD_VAR = 3;
    private static final int K_ADD_FOLDER = 4;

    private record Row(int kind, int varIndex, String folder) {}

    private final Font font;
    private final AiEditorScreen host;
    private final PopupMenu typeMenu;
    private int x, y, w, h;

    private int scroll;
    private final Set<String> collapsed = new HashSet<>();

    private int pressVar = -1;
    private boolean dragging;
    private double pressX, pressY, dragX, dragY;

    public VariablePanel(Font font, AiEditorScreen host) {
        this.font = font;
        this.host = host;
        this.typeMenu = new PopupMenu(font);
    }

    public boolean menuOpen() {
        return typeMenu.open;
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
    }

    public boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private AiScript script() {
        return host.script();
    }

    private int viewTop() { return y + HEADER_H; }
    private int viewBottom() { return y + h - (infoType() != null ? DESC_H : 0); }

    private BlockType infoType() {
        BlockType t = host.paletteHoverType();
        if (t == null) t = host.paletteDragType();
        if (t == null) t = host.canvasHoverType();
        return t;
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(K_ADD_VAR, -1, ""));
        rows.add(new Row(K_ADD_FOLDER, -1, ""));
        List<VarDecl> vars = script().variables();
        List<String> folders = script().varFolders();
        for (String f : folders) {
            rows.add(new Row(K_FOLDER, -1, f));
            if (!collapsed.contains(f)) {
                for (int i = 0; i < vars.size(); i++) {
                    if (f.equals(vars.get(i).folder())) rows.add(new Row(K_VAR, i, f));
                }
            }
        }
        if (!folders.isEmpty()) rows.add(new Row(K_UNGROUPED, -1, ""));
        for (int i = 0; i < vars.size(); i++) {
            String vf = vars.get(i).folder();
            if (vf.isEmpty() || !folders.contains(vf)) rows.add(new Row(K_VAR, i, ""));
        }
        return rows;
    }

    private int contentHeight(List<Row> rows) {
        return rows.size() * ROW_H + 2;
    }

    private int clampScroll(int s, List<Row> rows) {
        int max = Math.max(0, contentHeight(rows) - (viewBottom() - viewTop()));
        return Math.clamp(s, 0, max);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int descTop = viewBottom();
        g.fill(x, y, x + w, y + h, 0xF01A1A1A);
        g.fill(x, y, x + 1, y + h, 0xFF000000);
        g.fill(x, y, x + w, y + HEADER_H, 0xFF2A2A2A);
        g.drawString(font, "Variables", x + 5, y + 4, 0xFFFFFFFF, false);

        List<Row> rows = buildRows();
        scroll = clampScroll(scroll, rows);

        g.enableScissor(x, viewTop(), x + w, descTop);
        int baseY = viewTop() + 2 - scroll;
        for (int idx = 0; idx < rows.size(); idx++) {
            int ry = baseY + idx * ROW_H;
            if (ry + ROW_H < viewTop() || ry > descTop) continue;
            renderRow(g, rows.get(idx), ry, mouseX, mouseY);
        }
        g.disableScissor();

        renderScrollbar(g, rows, descTop);
        if (descTop < y + h) renderDescription(g, descTop);
        renderDragGhost(g);
        if (typeMenu.open) typeMenu.render(g, mouseX, mouseY);
    }

    private void renderRow(GuiGraphics g, Row row, int ry, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW_H;
        switch (row.kind()) {
            case K_FOLDER -> {
                boolean dropTarget = dragging && dragY >= ry && dragY < ry + ROW_H;
                g.fill(x + 1, ry, x + w, ry + ROW_H, dropTarget ? 0x556AB0FF : (hover ? 0xFF262626 : 0xFF1C1C1C));
                String arrow = collapsed.contains(row.folder()) ? "▸ " : "▾ ";
                g.drawString(font, arrow + trim(row.folder(), w - 30), x + 4, ry + 3, 0xFFFFD080, false);
                g.drawString(font, "✕", x + w - 10, ry + 3, 0xFFFF6060, false);
            }
            case K_UNGROUPED -> {
                boolean dropTarget = dragging && dragY >= ry && dragY < ry + ROW_H;
                if (dropTarget) g.fill(x + 1, ry, x + w, ry + ROW_H, 0x556AB0FF);
                g.drawString(font, "Ungrouped", x + 4, ry + 3, 0xFF808080, false);
            }
            case K_VAR -> {
                VarDecl v = script().variables().get(row.varIndex());
                int indent = row.folder().isEmpty() ? 0 : 8;
                if (hover) g.fill(x + 1, ry, x + w - 1, ry + ROW_H, 0x22FFFFFF);
                if (indent > 0) g.fill(x + 3, ry, x + 4, ry + ROW_H, 0x33FFD080);
                g.drawString(font, trim(v.name(), w - 80 - indent), x + 4 + indent, ry + 3, 0xFFE0E0E0, false);

                int tcx = x + w - 62;
                g.fill(tcx, ry + 1, x + w - 14, ry + ROW_H - 1, 0xFF2A2A2A);
                g.drawString(font, v.type().chipLabel(), tcx + 3, ry + 3, 0xFFE0E0E0, false);

                g.drawString(font, "✕", x + w - 10, ry + 3, 0xFFFF6060, false);
            }
            case K_ADD_VAR -> {
                g.fill(x + 4, ry + 1, x + w - 4, ry + ROW_H - 1, hover ? 0x66FFFFFF : 0x33FFFFFF);
                g.drawString(font, "+ Add variable", x + 8, ry + 3, 0xFFFFFFFF, false);
            }
            case K_ADD_FOLDER -> {
                g.fill(x + 4, ry + 1, x + w - 4, ry + ROW_H - 1, hover ? 0x66FFD080 : 0x33FFD080);
                g.drawString(font, "+ New folder", x + 8, ry + 3, 0xFFFFD080, false);
            }
            default -> {}
        }
    }

    private void renderScrollbar(GuiGraphics g, List<Row> rows, int descTop) {
        int viewH = descTop - viewTop();
        int contentH = contentHeight(rows);
        if (contentH <= viewH) return;
        int trackTop = viewTop();
        int barH = Math.max(12, viewH * viewH / contentH);
        int max = contentH - viewH;
        int barY = trackTop + (max == 0 ? 0 : (viewH - barH) * scroll / max);
        g.fill(x + w - 3, barY, x + w - 1, barY + barH, 0x88FFFFFF);
    }

    private void renderDragGhost(GuiGraphics g) {
        if (!dragging || pressVar < 0 || pressVar >= script().variables().size()) return;
        String nm = script().variables().get(pressVar).name();
        int gw = font.width(nm) + 10;
        int gx = (int) dragX + 6, gy = (int) dragY - 6;
        g.fill(gx, gy, gx + gw, gy + 13, 0xEE303030);
        g.fill(gx, gy, gx + gw, gy + 1, 0xFF6AB0FF);
        g.drawString(font, nm, gx + 4, gy + 3, 0xFFFFFFFF, false);
    }

    private void renderDescription(GuiGraphics g, int descTop) {
        g.fill(x + 1, descTop, x + w, descTop + 1, 0xFF000000);
        g.fill(x + 1, descTop + 1, x + w, descTop + 1 + HEADER_H, 0xFF2A2A2A);
        g.drawString(font, "Description", x + 5, descTop + 5, 0xFFFFFFFF, false);

        int bodyTop = descTop + HEADER_H + 5;
        BlockType infoType = infoType();
        if (infoType == null) return;

        BlockDef def = BlockDefRegistry.get(infoType);
        g.drawString(font, def.label(), x + 5, bodyTop, def.category().color() | 0xFF000000, false);

        int ty = bodyTop + font.lineHeight + 3;
        g.enableScissor(x + 1, ty, x + w, y + h);
        for (String line : wrap(BlockDescriptions.get(infoType), w - 10)) {
            g.drawString(font, line, x + 5, ty, 0xFFC8C8C8, false);
            ty += font.lineHeight;
        }
        g.disableScissor();
    }

    private Row rowAt(double my) {
        if (my < viewTop() || my >= viewBottom()) return null;
        List<Row> rows = buildRows();
        int idx = (int) Math.floor((my - (viewTop() + 2 - scroll)) / ROW_H);
        if (idx < 0 || idx >= rows.size()) return null;
        return rows.get(idx);
    }

    public boolean mousePressed(double mx, double my) {
        if (typeMenu.open) {
            typeMenu.click(mx, my);
            return true;
        }
        if (!inside(mx, my)) return false;
        pressVar = -1;
        dragging = false;
        Row row = rowAt(my);
        if (row == null) return true;
        switch (row.kind()) {
            case K_FOLDER -> {
                if (mx >= x + w - 14) { host.pushUndo(); deleteFolder(row.folder()); }
                else toggle(row.folder());
            }
            case K_VAR -> {
                List<VarDecl> vars = script().variables();
                int vi = row.varIndex();
                if (mx >= x + w - 14) {
                    host.pushUndo();
                    vars.remove(vi);
                } else if (mx >= x + w - 62) {
                    openTypeMenu(vi, mx, my);
                } else {
                    pressVar = vi;
                    pressX = mx;
                    pressY = my;
                }
            }
            case K_ADD_VAR -> { host.pushUndo(); addVariable(); }
            case K_ADD_FOLDER -> { host.pushUndo(); addFolder(); }
            default -> {}
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (pressVar < 0) return false;
        dragX = mx;
        dragY = my;
        if (!dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) > DRAG_SLOP) dragging = true;
        return true;
    }

    public boolean mouseReleased(double mx, double my) {
        if (pressVar < 0) return false;
        int vi = pressVar;
        boolean wasDragging = dragging;
        pressVar = -1;
        dragging = false;
        List<VarDecl> vars = script().variables();
        if (vi < 0 || vi >= vars.size()) return true;

        if (wasDragging) {
            if (!inside(mx, my)) {
                host.dropVariableBlock(vars.get(vi).qualifiedName(), mx, my);
                return true;
            }
            String target = folderTargetAt(mx, my);
            if (target != null && !target.equals(vars.get(vi).folder())) {
                host.pushUndo();
                VarDecl old = vars.get(vi);
                String name = uniqueName(old.name(), target, vi);
                VarDecl moved = old.withFolder(target).withName(name);
                vars.set(vi, moved);
                host.refactorVariableRef(old.qualifiedName(), moved.qualifiedName());
            }
        } else {
            final int idx = vi;
            host.promptText("Variable name", vars.get(idx).name(), name -> {
                if (name != null && !name.isBlank()) {
                    host.pushUndo();
                    List<VarDecl> vs = script().variables();
                    if (idx < vs.size()) {
                        VarDecl old = vs.get(idx);
                        String cleaned = name.trim().replace(" ", "");
                        if (cleaned.isEmpty()) return;
                        String unique = uniqueName(cleaned, old.folder(), idx);
                        VarDecl renamed = old.withName(unique);
                        vs.set(idx, renamed);
                        host.refactorVariableRef(old.qualifiedName(), renamed.qualifiedName());
                    }
                }
            });
        }
        return true;
    }

    private String folderTargetAt(double mx, double my) {
        if (!inside(mx, my)) return null;
        Row row = rowAt(my);
        if (row == null) return null;
        return switch (row.kind()) {
            case K_FOLDER, K_VAR -> row.folder();
            case K_UNGROUPED -> "";
            default -> null;
        };
    }

    public boolean scrolled(double amount) {
        if (typeMenu.open) { typeMenu.scroll(amount); return true; }
        scroll = clampScroll((int) (scroll - amount * ROW_H * 2), buildRows());
        return true;
    }

    private void toggle(String folder) {
        if (!collapsed.remove(folder)) collapsed.add(folder);
    }

    private void deleteFolder(String folder) {
        List<VarDecl> vars = script().variables();
        for (int i = 0; i < vars.size(); i++) {
            if (folder.equals(vars.get(i).folder())) {
                VarDecl old = vars.get(i);
                String name = uniqueName(old.name(), "", i);
                VarDecl moved = old.withFolder("").withName(name);
                vars.set(i, moved);
                host.refactorVariableRef(old.qualifiedName(), moved.qualifiedName());
            }
        }
        script().varFolders().remove(folder);
        collapsed.remove(folder);
    }

    private void addVariable() {
        List<VarDecl> vars = script().variables();
        String name = uniqueName("var" + (vars.size() + 1), "", -1);
        vars.add(new VarDecl(name, VarType.BOOL, false));
    }

    private boolean collides(String name, String folder, int excludeIndex) {
        List<VarDecl> vars = script().variables();
        for (int i = 0; i < vars.size(); i++) {
            if (i == excludeIndex) continue;
            VarDecl v = vars.get(i);
            if (v.folder().equals(folder) && v.name().equals(name)) return true;
        }
        return false;
    }

    private String uniqueName(String desired, String folder, int excludeIndex) {
        if (!collides(desired, folder, excludeIndex)) return desired;
        String stem = desired.replaceAll("\\d+$", "");
        if (stem.isEmpty()) stem = desired + "_";
        int n = 2;
        String candidate;
        do {
            candidate = stem + n;
            n++;
        } while (collides(candidate, folder, excludeIndex));
        return candidate;
    }

    private void addFolder() {
        List<String> folders = script().varFolders();
        int n = folders.size() + 1;
        String name = "folder" + n;
        while (folders.contains(name)) name = "folder" + (++n);
        folders.add(name);
    }

    private void openTypeMenu(int vi, double sx, double sy) {
        typeMenu.clear();
        for (VarType t : VarType.values()) {
            typeMenu.add(t.displayName(), () -> {
                List<VarDecl> vars = script().variables();
                if (vi < 0 || vi >= vars.size()) return;
                host.pushUndo();
                VarDecl v = vars.get(vi);
                vars.set(vi, v.withType(t, defaultFor(t)));
                host.onVariableTypeChanged(v.qualifiedName());
            });
        }
        typeMenu.openAt(sx, sy, x, y, x + w, y + h);
    }

    public static Object defaultFor(VarType t) {
        return switch (t) {
            case BOOL -> false;
            case INT -> 0;
            case DOUBLE -> 0.0;
            case POSITION -> "0 64 0";
            case ROTATION -> "0 0";
            case STRING -> "";
            case UUID -> "00000000-0000-0000-0000-000000000000";
            case ITEM -> "minecraft:air";
        };
    }

    private List<String> wrap(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (font.width(trial) > maxW && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(b + String.valueOf(s.charAt(i)) + "…") > maxW) break;
            b.append(s.charAt(i));
        }
        return b + "…";
    }
}
