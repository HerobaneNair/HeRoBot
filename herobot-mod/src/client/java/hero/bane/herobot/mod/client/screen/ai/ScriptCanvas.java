package hero.bane.herobot.mod.client.screen.ai;

import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.ai.Comment;
import hero.bane.herobot.common.ai.VarType;
import hero.bane.herobot.common.ai.block.BlockDef;
import hero.bane.herobot.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.block.BlockShape;
import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.block.EffectiveSlots;
import hero.bane.herobot.common.ai.block.ParamSlot;
import hero.bane.herobot.common.ai.block.ParamType;
import hero.bane.herobot.common.ai.block.Wire;
import hero.bane.herobot.mod.client.screen.ai.starfield.PixelBatch;
import hero.bane.herobot.mod.client.screen.ai.starfield.StarField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScriptCanvas {
    private final AiEditorScreen host;
    private final Font font;

    private static final double MIN_ZOOM = 0.15;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 1.15;

    private final PixelBatch gridBatch = new PixelBatch();

    private int left, top, right, bottom;
    private double panX = 60, panY = 60, zoom = 1.0;

    private boolean panning;
    private double lastMx, lastMy;
    private BlockInstance dragBlock;
    private double dragAnchorX, dragAnchorY;
    private boolean dragDidSnapshot;
    private final Map<Integer, double[]> dragGroup = new LinkedHashMap<>();
    private int wireFromId = -1, wireFromPort;
    private int wireToId = -1;
    private double wireMx, wireMy;

    private boolean holeDrag;
    private double holeGrabDx, holeGrabDy;

    private boolean selecting;
    private boolean marqueeAdditive;
    private double selStartX, selStartY, selCurX, selCurY;

    private double rightDownX, rightDownY;
    private boolean rightPressed;
    private boolean shiftRightSpawn;
    private boolean shiftRightDragged;
    private int starTick;
    private int spawnStartTick;
    private int spawnCount;
    private double spawnDelay;
    private double spawnThreshold;
    private double spawnMx, spawnMy;
    private boolean dragActive;
    private final Map<Integer, double[]> dragGroupComments = new LinkedHashMap<>();
    private Comment editing;
    private int caret;
    private int selAnchor;
    private int activeStyle;
    private boolean commentEditDidSnapshot;
    private final PopupMenu menu;

    private static final double RIGHT_CLICK_SLOP = 6;
    private static final double SPAWN_DELAY_START = 5.0;
    private static final double SPAWN_DELAY_MULT = 1.1;
    private static final double SPAWN_BURST_MEET = 1.0;
    private static final double COMMENT_W = 120;
    private static final int COMMENT_PAD = 5;
    private static final int FMT_BTN = 14;
    private static final int[] FMT_FLAGS = {Comment.BOLD, Comment.ITALIC, Comment.UNDERLINE, Comment.STRIKE};
    private static final String[] FMT_LABELS = {"B", "I", "U", "S"};

    private final Map<Integer, BlockRenderer.Layout> frameLayouts = new HashMap<>();
    private boolean caching;
    private String dropReason;

    private final StarField stars = new StarField();

    public ScriptCanvas(AiEditorScreen host, Font font) {
        this.host = host;
        this.font = font;
        this.menu = new PopupMenu(font);
    }

    public void setBounds(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }

    public void resetView() {
        zoom = 1.0;
        panX = left + 10;
        panY = top + 10;
    }

    public void fitView() {
        if (script().blocks().isEmpty()) { resetView(); return; }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            minX = Math.min(minX, L.x);       minY = Math.min(minY, L.y);
            maxX = Math.max(maxX, L.x + L.w); maxY = Math.max(maxY, L.y + L.h);
        }
        double pad = 24;
        minX -= pad; minY -= pad; maxX += pad; maxY += pad;
        double contentW = Math.max(1, maxX - minX), contentH = Math.max(1, maxY - minY);
        double z = Math.min((right - left) / contentW, (bottom - top) / contentH);
        zoom = Math.clamp(z, MIN_ZOOM, MAX_ZOOM);
        double cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        panX = (left + right) / 2.0 - cx * zoom;
        panY = (top + bottom) / 2.0 - cy * zoom;
    }

    public boolean inside(double mx, double my) {
        return mx >= left && mx < right && my >= top && my < bottom;
    }

    public void tickStars() {
        starTick++;
        if (shiftRightSpawn && shiftRightDragged) {
            double elapsed = starTick - spawnStartTick;
            while (elapsed >= spawnThreshold) {
                spawnCount++;
                if (spawnCount == StarField.BLACK_HOLE_THRESHOLD && stars.canSpawnBlackHole()) {
                    stars.bigSparkle(spawnMx, spawnMy);
                } else {
                    stars.sparkle(spawnMx, spawnMy);
                }
                spawnDelay *= SPAWN_DELAY_MULT;
                spawnThreshold += spawnDelay;
            }
        }
        stars.tick(left, top, right, bottom, panX, panY, zoom);
    }

    private AiScript script() { return host.script(); }

    private double wx(double sx) { return (sx - panX) / zoom; }
    private double wy(double sy) { return (sy - panY) / zoom; }
    private int sx(double worldX) { return (int) Math.round(worldX * zoom + panX); }
    private int sy(double worldY) { return (int) Math.round(worldY * zoom + panY); }

    public double[] screenToWorld(double screenX, double screenY) {
        return new double[]{wx(screenX), wy(screenY)};
    }

    public int draggingBlockId() {
        return dragBlock != null ? dragBlock.id() : -1;
    }

    public List<Integer> draggedBlockIds() {
        return new ArrayList<>(dragGroup.keySet());
    }

    public boolean isDraggingComments() {
        return !dragGroupComments.isEmpty();
    }

    public List<Integer> draggedCommentIds() {
        return new ArrayList<>(dragGroupComments.keySet());
    }

    public boolean isDraggingBlackHole() {
        return holeDrag;
    }

    public boolean isDraggingWire() {
        return wireFromId >= 0;
    }

    public void deleteBlackHole() {
        holeDrag = false;
        stars.removeBlackHole();
    }

    public void cancelDrag() {
        holeDrag = false;
        dragBlock = null;
        dragActive = false;
        dragGroup.clear();
        dragGroupComments.clear();
        panning = false;
        selecting = false;
        wireFromId = -1;
        wireToId = -1;
        shiftRightSpawn = false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        frameLayouts.clear();
        caching = true;
        try {
            renderInner(g, mouseX, mouseY);
        } finally {
            caching = false;
            frameLayouts.clear();
        }
    }

    private void renderInner(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left, top, right, bottom, 0xFF12131A);
        g.enableScissor(left, top, right, bottom);
        drawGrid(g);
        clampEndBlocks();
        List<double[]> blockRects = new ArrayList<>();
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            blockRects.add(new double[]{sx(L.x), sy(L.y), sx(L.x + L.w), sy(L.y + L.h)});
        }
        computeWireLanes();
        List<double[]> wireSegs = new ArrayList<>();
        for (Wire w : script().wires()) {
            BlockInstance from = script().block(w.fromBlockId());
            BlockInstance to = script().block(w.toBlockId());
            if (from == null || to == null) continue;
            BlockRenderer.Layout lf = layout(from);
            BlockRenderer.Layout lt = layout(to);
            if (w.outPort() >= lf.outPorts.size()) continue;
            int[] p = lf.outPorts.get(w.outPort());
            int x1 = sx(p[0]), y1 = sy(p[1]);
            int x2 = sx(lt.inX), y2 = sy(lt.inY);
            int midY = sy(midYFor(w, p[1], lt.inY));
            wireSegs.add(new double[]{x1, y1, x1, midY});
            wireSegs.add(new double[]{x1, midY, x2, midY});
            wireSegs.add(new double[]{x2, midY, x2, y2});
        }
        for (Comment c : script().comments()) {
            measure(c);
            blockRects.add(new double[]{sx(cX(c)), sy(cY(c)), sx(cX(c) + c.w), sy(cY(c) + c.h)});
        }
        if (menu.open) blockRects.add(new double[]{menu.x, menu.y, menu.x + menu.w, menu.y + menu.h});
        stars.render(g, left, top, right, bottom, panX, panY, zoom, mouseX, mouseY, blockRects, wireSegs);

        g.pose().pushMatrix();
        g.pose().translate((float) panX, (float) panY);
        g.pose().scale((float) zoom, (float) zoom);

        double wmx = wx(mouseX), wmy = wy(mouseY);

        Wire hoveredWire = (dragBlock == null && wireFromId < 0 && !panning) ? wireAt(wmx, wmy) : null;
        SplicePreview splice = (dragBlock != null && dragDidSnapshot && !panning
                && spliceDragEligible() && canSplice(dragBlock))
                ? splicePreview(dragBlock, wmx, wmy) : null;
        for (Wire w : script().wires()) {
            BlockInstance from = script().block(w.fromBlockId());
            BlockInstance to = script().block(w.toBlockId());
            if (from == null || to == null) continue;
            BlockRenderer.Layout lf = layout(from);
            BlockRenderer.Layout lt = layout(to);
            if (w.outPort() >= lf.outPorts.size()) continue;
            int[] p = lf.outPorts.get(w.outPort());
            if (isSideWire(from, w)) {
                int sc = w.equals(hoveredWire) ? 0xFFFFE066 : PAIR_WIRE_COLOR;
                if (splice != null && w.equals(splice.wire())) sc = splice.valid() ? SPLICE_OK : SPLICE_BAD;
                drawThickWireH(g, p[0], p[1], lt.inX, lt.inY, sc);
                continue;
            }
            int wc = w.equals(hoveredWire) ? 0xFFFFE066 : 0xFFCCCCCC;
            if (splice != null && w.equals(splice.wire())) wc = splice.valid() ? SPLICE_OK : SPLICE_BAD;
            drawWire(g, p[0], p[1], lt.inX, lt.inY, midYFor(w, p[1], lt.inY), wc);
        }

        for (BlockInstance b : script().blocks().values()) {
            if (!AiEditorScreen.isContainer(b.type()) || b.pairedId() < 0) continue;
            BlockInstance end = script().block(b.pairedId());
            if (end == null) continue;
            BlockRenderer.Layout ls = layout(b);
            BlockRenderer.Layout le = layout(end);
            drawThickWire(g, ls.x + 5, ls.y + ls.h, le.x + 5, le.y);
        }

        dropReason = null;
        int dropTargetId = -1;
        boolean dropConflicts = false;
        int targetOutBlockId = -1;
        int targetOutIdx = -1;
        boolean targetOutValid = false;
        if (wireFromId >= 0) {
            int tId = resolveInputTargetId(wmx, wmy);
            if (tId >= 0 && tId != wireFromId) {
                dropTargetId = tId;
                dropReason = wireReason(wireFromId, wireFromPort, tId);
                dropConflicts = dropReason != null;
            }
        } else if (wireToId >= 0) {
            int[] hit = resolveOutTarget(wmx, wmy, wireToId);
            if (hit != null && hit[0] != wireToId) {
                dropReason = wireReason(hit[0], hit[1], wireToId);
                targetOutBlockId = hit[0];
                targetOutIdx = hit[1];
                targetOutValid = dropReason == null;
                if (dropReason != null) dropConflicts = true;
            }
        }

        BlockType paletteType = host.paletteDragType();
        Object[] slotTarget = null;
        if (dragBlock != null && BlockDefRegistry.get(dragBlock.type()).isReporter()) {
            slotTarget = findSlotAt(wmx, wmy, dragBlock.id());
        } else if (dragBlock == null && wireFromId < 0 && paletteType != null
                && BlockDefRegistry.get(paletteType).isReporter()) {
            slotTarget = findSlotAt(wmx, wmy, -1);
        }

        int hoveredId = -1;
        if (dragBlock == null && wireFromId < 0 && !panning) {
            hoveredId = deepHitId(wmx, wmy);
            if (hoveredId < 0) {
                int[] hp = outPortHit(wmx, wmy);
                if (hp != null) hoveredId = hp[0];
            }
        }
        int wireTargetId = wireFromId >= 0 ? dropTargetId
                : (wireToId >= 0 ? targetOutBlockId : -1);
        boolean idlePorts = wireFromId < 0 && wireToId < 0 && dragBlock == null && !panning;
        int inHover = idlePorts ? inputPortHit(wmx, wmy) : -1;

        for (BlockInstance b : script().blocks().values()) {
            BlockDef def = BlockDefRegistry.get(b.type());
            BlockRenderer.Layout L = layout(b);
            int hoverPort = -1;
            if (idlePorts) {
                for (int i = 0; i < L.outPorts.size(); i++) {
                    if (inPortRect(wmx, wmy, L.outPorts.get(i), i == L.sideOutPort)) hoverPort = i;
                }
            }
            int litPort = (wireFromId == b.id()) ? wireFromPort : -1;
            int inputHL = BlockRenderer.INPUT_NONE;
            boolean conflictMark = false;
            if (b.id() == dropTargetId) {
                inputHL = dropConflicts ? BlockRenderer.INPUT_BAD : BlockRenderer.INPUT_OK;
            } else if (dropConflicts && conflictsWithSibling(b.id(), dropTargetId)) {
                conflictMark = true;
            }
            if (inputHL == BlockRenderer.INPUT_NONE && b.id() == inHover) {
                inputHL = BlockRenderer.INPUT_OK;
            }
            if (b.type() == BlockType.BLOCK_END) {
                int extraFrom = (b.id() == dropTargetId && wireFromId >= 0) ? wireFromId : -1;
                if (endConflict(b, extraFrom)) {
                    conflictMark = true;
                    if (dropReason == null) {
                        dropReason = "this end joins conflicting movement blocks (only one move/strafe/sneak/sprint each)";
                    }
                }
            }
            int targetPort = (b.id() == targetOutBlockId) ? targetOutIdx : -1;
            BlockRenderer.draw(g, font, def, b, L, host.selectedId(), hoveredId, hoverPort, litPort,
                    targetPort, targetOutValid, inputHL, conflictMark, script());

            if (host.isSelected(b.id()) && b.id() != host.selectedId()) {
                int c = 0xFF55AAFF;
                g.fill(L.x - 1, L.y - 1, L.x + L.w + 1, L.y, c);
                g.fill(L.x - 1, L.y + L.h, L.x + L.w + 1, L.y + L.h + 1, c);
                g.fill(L.x - 1, L.y, L.x, L.y + L.h, c);
                g.fill(L.x + L.w, L.y, L.x + L.w + 1, L.y + L.h, c);
            }
            if (b.id() == wireTargetId) {
                int c = 0xFFFFFFFF;
                g.fill(L.x - 1, L.y - 1, L.x + L.w + 1, L.y, c);
                g.fill(L.x - 1, L.y + L.h, L.x + L.w + 1, L.y + L.h + 1, c);
                g.fill(L.x - 1, L.y, L.x, L.y + L.h, c);
                g.fill(L.x + L.w, L.y, L.x + L.w + 1, L.y + L.h, c);
            }
        }
        if (splice != null) {
            BlockInstance from = script().block(splice.wire().fromBlockId());
            BlockInstance to = script().block(splice.wire().toBlockId());
            if (from != null) drawSpliceBorder(g, layout(from), splice.inputValid());
            if (to != null) drawSpliceBorder(g, layout(to), splice.outputValid());
            BlockRenderer.Layout lb = layout(dragBlock);
            if (!splice.inputValid() && from != null) {
                BlockRenderer.Layout lf = layout(from);
                if (splice.wire().outPort() < lf.outPorts.size()) {
                    int[] p = lf.outPorts.get(splice.wire().outPort());
                    drawWire(g, p[0], p[1], lb.inX, lb.inY, SPLICE_BAD);
                }
            }
            if (!splice.outputValid() && to != null) {
                BlockInstance exit = script().block(splice.exitId());
                BlockRenderer.Layout le = exit != null ? layout(exit) : lb;
                if (!le.outPorts.isEmpty()) {
                    int[] p = le.outPorts.getFirst();
                    BlockRenderer.Layout lt = layout(to);
                    drawWire(g, p[0], p[1], lt.inX, lt.inY, SPLICE_BAD);
                }
            }
        }
        if (slotTarget != null) {
            BlockInstance target = (BlockInstance) slotTarget[0];
            String slot = (String) slotTarget[1];
            String reason = dragBlock != null
                    ? nestReason(target, slot, dragBlock)
                    : nestReason(target, slot, new BlockInstance(-1, paletteType, 0, 0));
            if (reason != null) dropReason = reason;
            drawSlotHighlight(g, target, slot, reason == null);
        }

        for (Comment c : script().comments()) {
            measure(c);
            double px = cX(c), py = cY(c);
            int x0 = (int) Math.round(px), y0 = (int) Math.round(py);
            int x1 = (int) Math.round(px + c.w), y1 = (int) Math.round(py + c.h);
            boolean active = c == editing;
            boolean sel = host.isSelected(c.id());
            boolean hovered = active || (hoveredId < 0 && wireFromId < 0 && !panning
                    && wmx >= px && wmx <= px + c.w && wmy >= py && wmy <= py + c.h);
            int border = active ? 0xFF3A7AE0 : sel ? 0xFF55AAFF : hovered ? 0xFFE8C84A : 0xFF000000;
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
            g.fill(x0, y0, x1, y1, 0xFFFCEE9A);
            g.fill(x0, y0, x1, y0 + 2, 0xFFE8C84A);
            if (c.attachedTo() >= 0) g.fill(x0, y0, x0 + 3, y1, 0xFFE8C84A);
            String t = c.text();
            List<int[]> lines = wrapLines(c);
            if (active && hasSelection()) {
                int ss = selStart(), se = selEnd();
                for (int i = 0; i < lines.size(); i++) {
                    int ls = lines.get(i)[0], le = lines.get(i)[1];
                    int a = Math.max(ss, ls), b = Math.min(se, le);
                    if (a >= b) continue;
                    int hx0 = x0 + COMMENT_PAD + CommentText.width(font, c, ls, a);
                    int hx1 = x0 + COMMENT_PAD + CommentText.width(font, c, ls, b);
                    int hy = y0 + COMMENT_PAD + i * font.lineHeight;
                    g.fill(hx0, hy - 1, hx1, hy + font.lineHeight - 1, 0x803A7AE0);
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                CommentText.draw(g, font, c, lines.get(i)[0], lines.get(i)[1],
                        x0 + COMMENT_PAD, y0 + COMMENT_PAD + i * font.lineHeight, 0xFF1A1A1A);
            }
            if (active && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cc = Math.clamp(caret, 0, t.length());
                int[] lc = caretLineCol(lines, cc);
                int cx = x0 + COMMENT_PAD + CommentText.width(font, c, lc[1], cc);
                int cy = y0 + COMMENT_PAD + lc[0] * font.lineHeight;
                g.fill(cx, cy - 1, cx + 1, cy + font.lineHeight, 0xFF1A1A1A);
            }
        }
        g.pose().popMatrix();

        if (editing != null) renderFormatBar(g, mouseX, mouseY);

        if (selecting) {
            int x0 = (int) Math.min(selStartX, selCurX), x1 = (int) Math.max(selStartX, selCurX);
            int y0 = (int) Math.min(selStartY, selCurY), y1 = (int) Math.max(selStartY, selCurY);
            g.fill(x0, y0, x1, y1, 0x3055AAFF);
            g.fill(x0, y0, x1, y0 + 1, 0xFF55AAFF);
            g.fill(x0, y1 - 1, x1, y1, 0xFF55AAFF);
            g.fill(x0, y0, x0 + 1, y1, 0xFF55AAFF);
            g.fill(x1 - 1, y0, x1, y1, 0xFF55AAFF);
        }

        if (wireFromId >= 0) {
            BlockInstance from = script().block(wireFromId);
            if (from != null) {
                BlockRenderer.Layout lf = layout(from);
                if (wireFromPort < lf.outPorts.size()) {
                    int[] p = lf.outPorts.get(wireFromPort);
                    if (wireFromPort == lf.sideOutPort) {
                        drawWireH(g, sx(p[0]), sy(p[1]), (int) wireMx, (int) wireMy);
                    } else {
                        drawWire(g, sx(p[0]), sy(p[1]), (int) wireMx, (int) wireMy, 0xFFFFFF66);
                    }
                }
            }
        }
        if (wireToId >= 0) {
            BlockInstance to = script().block(wireToId);
            if (to != null) {
                BlockRenderer.Layout lt = layout(to);
                if (lt.sideInput) {
                    drawWireH(g, (int) wireMx, (int) wireMy, sx(lt.inX), sy(lt.inY));
                } else {
                    drawWire(g, (int) wireMx, (int) wireMy, sx(lt.inX), sy(lt.inY), 0xFFFFFF66);
                }
            }
        }
        if (menu.open) menu.render(g, mouseX, mouseY);
        g.disableScissor();
    }

    private static final double GRID_WARP_REACH = 6.0;
    private static final double GRID_WARP_STRENGTH = 5.0;
    private static final int GRID_DOT_ALPHA = 0x20;

    private void drawGrid(GuiGraphics g) {
        int alpha = (int) Math.round(GRID_DOT_ALPHA * StarField.gridVisibility(zoom));
        if (alpha <= 0) return;
        int argb = (alpha << 24) | 0xFFFFFF;

        int step = (int) Math.max(8, 40 * zoom);
        int startX = (int) (panX + Math.ceil((left - panX) / step) * step);
        int startY = (int) (panY + Math.ceil((top - panY) / step) * step);

        double[] bh = stars.blackHoleWarp();
        boolean warp = bh != null && bh[2] > 0.5;
        double cx = 0, cy = 0, hr = 0, reach = 0, hr2 = 0, reach2 = 0;
        double wx0 = 0, wx1 = 0, wy0 = 0, wy1 = 0;
        if (warp) {
            cx = bh[0];
            cy = bh[1];
            hr = bh[2];
            reach = hr * GRID_WARP_REACH;
            hr2 = hr * hr;
            reach2 = reach * reach;
            wx0 = cx - reach;
            wx1 = cx + reach;
            wy0 = cy - reach;
            wy1 = cy + reach;
        }

        gridBatch.begin(g, left, top, right, bottom);
        for (int x = startX; x < right; x += step) {
            boolean colWarp = warp && x >= wx0 && x <= wx1;
            for (int y = startY; y < bottom; y += step) {
                if (colWarp && y >= wy0 && y <= wy1) {
                    double ddx = cx - x, ddy = cy - y;
                    double d2 = ddx * ddx + ddy * ddy;
                    if (d2 <= hr2) continue;
                    if (d2 < reach2) {
                        double dist = Math.sqrt(d2);
                        double t = 1.0 - dist / reach;
                        double disp = Math.min(dist - hr, hr * GRID_WARP_STRENGTH * t * t);
                        gridBatch.pixel((int) Math.round(x + ddx / dist * disp),
                                (int) Math.round(y + ddy / dist * disp), argb);
                        continue;
                    }
                }
                gridBatch.pixel(x, y, argb);
            }
        }
        gridBatch.submit(g);
    }

    private void drawWire(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        drawWire(g, x1, y1, x2, y2, (y1 + y2) / 2, color);
    }

    private void drawWire(GuiGraphics g, int x1, int y1, int x2, int y2, int midY, int color) {
        fillV(g, x1, y1, midY, color);
        fillH(g, x1, x2, midY, color);
        fillV(g, x2, midY, y2, color);
    }

    private void drawWireH(GuiGraphics g, int x1, int y1, int x2, int y2) {
        int midX = (x1 + x2) / 2;
        int color = 0xFFFFFF66;
        fillH(g, x1, midX, y1, color);
        fillV(g, midX, y1, y2, color);
        fillH(g, midX, x2, y2, color);
    }

    private static void fillV(GuiGraphics g, int x, int ya, int yb, int color) {
        g.fill(x - 1, Math.min(ya, yb) - 1, x + 1, Math.max(ya, yb) + 1, color);
    }

    private static void fillH(GuiGraphics g, int xa, int xb, int y, int color) {
        g.fill(Math.min(xa, xb) - 1, y - 1, Math.max(xa, xb) + 1, y + 1, color);
    }

    private static final int PAIR_WIRE_COLOR = 0xFFAA7700;
    private static final int SPLICE_OK = 0xFF55E055;
    private static final int SPLICE_BAD = 0xFFFF5050;

    private static final int LANE_GAP = 10;
    private static final int LANE_STEP = 4;
    private final Map<Wire, Integer> wireLanes = new HashMap<>();

    private void computeWireLanes() {
        wireLanes.clear();
        Map<Integer, List<Wire>> byTarget = new HashMap<>();
        for (Wire w : script().wires()) {
            BlockInstance from = script().block(w.fromBlockId());
            BlockInstance to = script().block(w.toBlockId());
            if (from == null || to == null || isSideWire(from, w)) continue;
            if (w.outPort() >= layout(from).outPorts.size()) continue;
            byTarget.computeIfAbsent(w.toBlockId(), k -> new ArrayList<>()).add(w);
        }
        for (List<Wire> group : byTarget.values()) {
            if (group.size() < 2) continue;
            BlockRenderer.Layout lt = layout(script().block(group.getFirst().toBlockId()));
            int top = lt.inY - LANE_GAP - (group.size() - 1) * LANE_STEP;
            boolean allAbove = true;
            for (Wire w : group) {
                if (srcPort(w)[1] >= top) { allAbove = false; break; }
            }
            if (!allAbove) continue;
            group.sort(Comparator.comparingInt(w -> Math.abs(srcPort(w)[0] - lt.inX)));
            for (int i = 0; i < group.size(); i++) {
                wireLanes.put(group.get(i), lt.inY - LANE_GAP - i * LANE_STEP);
            }
        }
    }

    private int[] srcPort(Wire w) {
        return layout(script().block(w.fromBlockId())).outPorts.get(w.outPort());
    }

    private int midYFor(Wire w, int y1, int y2) {
        Integer lane = wireLanes.get(w);
        return lane != null ? lane : (y1 + y2) / 2;
    }

    private static void drawSpliceBorder(GuiGraphics g, BlockRenderer.Layout L, boolean valid) {
        int c = valid ? SPLICE_OK : SPLICE_BAD;
        g.fill(L.x - 2, L.y - 2, L.x + L.w + 2, L.y - 1, c);
        g.fill(L.x - 2, L.y + L.h + 1, L.x + L.w + 2, L.y + L.h + 2, c);
        g.fill(L.x - 2, L.y - 1, L.x - 1, L.y + L.h + 1, c);
        g.fill(L.x + L.w + 1, L.y - 1, L.x + L.w + 2, L.y + L.h + 1, c);
    }

    private void drawThickWire(GuiGraphics g, int x1, int y1, int x2, int y2) {
        int midY = (y1 + y2) / 2;
        fillThickV(g, x1, y1, midY, PAIR_WIRE_COLOR);
        fillThickH(g, x1, x2, midY, PAIR_WIRE_COLOR);
        fillThickV(g, x2, midY, y2, PAIR_WIRE_COLOR);
    }

    private void drawThickWireH(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int midX = (x1 + x2) / 2;
        fillThickH(g, x1, midX, y1, color);
        fillThickV(g, midX, y1, y2, color);
        fillThickH(g, midX, x2, y2, color);
    }

    private static void fillThickV(GuiGraphics g, int x, int ya, int yb, int color) {
        g.fill(x - 2, Math.min(ya, yb) - 2, x + 3, Math.max(ya, yb) + 2, color);
    }

    private static void fillThickH(GuiGraphics g, int xa, int xb, int y, int color) {
        g.fill(Math.min(xa, xb) - 2, y - 2, Math.max(xa, xb) + 2, y + 3, color);
    }

    private static boolean isSideWire(BlockInstance from, Wire w) {
        return w.outPort() == 1
                && (from.type() == BlockType.IF || from.type() == BlockType.ELSE_IF);
    }

    private void clampEndBlocks() {
        for (BlockInstance b : script().blocks().values()) {
            if (b.type() != BlockType.BLOCK_END) continue;
            BlockInstance start = b.pairedId() >= 0 ? script().block(b.pairedId()) : null;
            if (start == null) continue;
            double minY = start.y() + layout(start).h;
            for (Wire w : script().wires()) {
                if (w.toBlockId() != b.id()) continue;
                BlockInstance src = script().block(w.fromBlockId());
                if (src == null) continue;
                minY = Math.max(minY, src.y() + layout(src).h);
            }
            for (BlockInstance it : script().blocks().values()) {
                if (it.type() == BlockType.LOOP_ITER && it.pairedId() == start.id()) {
                    minY = Math.max(minY, it.y() + layout(it).h);
                }
            }
            b.setPos(b.x(), Math.max(b.y(), minY));
        }
    }

    private BlockInstance endOfLoop(BlockInstance loop) {
        for (BlockInstance b : script().blocks().values()) {
            if (b.type() == BlockType.BLOCK_END && b.pairedId() == loop.id()) return b;
        }
        return null;
    }

    private static boolean facing(boolean side, double px, double py, double qx, double qy) {
        return side ? qx >= px : qy >= py;
    }

    private void connectNearestFromOutput(int fromId, int port) {
        BlockInstance from = script().block(fromId);
        if (from == null) return;
        BlockRenderer.Layout fl = layout(from);
        if (port < 0 || port >= fl.outPorts.size()) return;
        int[] p = fl.outPorts.get(port);
        boolean side = port == fl.sideOutPort;

        int bestId = -1;
        double bestD = Double.MAX_VALUE;
        for (BlockInstance t : script().blocks().values()) {
            if (t.id() == fromId) continue;
            BlockRenderer.Layout tl = layout(t);
            if (!tl.hasInput) continue;
            if (t.type() == BlockType.ELSE_IF && inputHasWire(t.id())) continue;
            if (!facing(side, p[0], p[1], tl.inX, tl.inY)) continue;
            if (!wireAllowed(fromId, port, t.id())) continue;
            double d = Math.hypot(tl.inX - p[0], tl.inY - p[1]);
            if (d < bestD) { bestD = d; bestId = t.id(); }
        }
        if (bestId < 0) return;
        addWire(fromId, port, bestId);
    }

    private void connectNearestFromInput(int toId) {
        BlockInstance to = script().block(toId);
        if (to == null) return;
        BlockRenderer.Layout tl = layout(to);
        if (!tl.hasInput) return;
        if (to.type() == BlockType.ELSE_IF && inputHasWire(toId)) return;

        int bestId = -1;
        int bestPort = -1;
        double bestD = Double.MAX_VALUE;
        for (BlockInstance s : script().blocks().values()) {
            if (s.id() == toId) continue;
            BlockRenderer.Layout sl = layout(s);
            for (int i = 0; i < sl.outPorts.size(); i++) {
                int[] p = sl.outPorts.get(i);
                if (!facing(i == sl.sideOutPort, p[0], p[1], tl.inX, tl.inY)) continue;
                if (!wireAllowed(s.id(), i, toId)) continue;
                double d = Math.hypot(tl.inX - p[0], tl.inY - p[1]);
                if (d < bestD) { bestD = d; bestId = s.id(); bestPort = i; }
            }
        }
        if (bestId < 0) return;
        addWire(bestId, bestPort, toId);
    }

    private void connectNearestAllPorts(BlockInstance b) {
        BlockRenderer.Layout L = layout(b);
        if (L.hasInput && !inputHasWire(b.id())) connectNearestFromInput(b.id());
        for (int i = 0; i < L.outPorts.size(); i++) {
            if (!outPortHasWire(b.id(), i)) connectNearestFromOutput(b.id(), i);
        }
        if (AiEditorScreen.isContainer(b.type()) && b.pairedId() >= 0) {
            BlockInstance end = script().block(b.pairedId());
            if (end != null) connectNearestAllPorts(end);
        }
    }

    private boolean outPortHasWire(int blockId, int port) {
        for (Wire w : script().wires()) {
            if (w.fromBlockId() == blockId && w.outPort() == port) return true;
        }
        return false;
    }

    private boolean inputHasWire(int blockId) {
        for (Wire w : script().wires()) {
            if (w.toBlockId() == blockId) return true;
        }
        return false;
    }

    private int resolveInputTargetId(double worldX, double worldY) {
        int id = inputPortHit(worldX, worldY);
        if (id >= 0) return id;
        BlockInstance b = topBlockAt(worldX, worldY);
        return b != null && layout(b).hasInput ? b.id() : -1;
    }

    private int[] resolveOutTarget(double worldX, double worldY, int toId) {
        int[] hit = outPortHit(worldX, worldY);
        if (hit != null) return hit;
        BlockInstance b = topBlockAt(worldX, worldY);
        if (b == null || b.id() == toId) return null;
        BlockRenderer.Layout L = layout(b);
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < L.outPorts.size(); i++) {
            if (!wireAllowed(b.id(), i, toId)) continue;
            int[] p = L.outPorts.get(i);
            double d = Math.hypot(p[0] - worldX, p[1] - worldY);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best < 0 ? null : new int[]{b.id(), best};
    }

    private boolean sourceConnectsToStart(BlockInstance end, int fromId) {
        int startId = end.pairedId();
        if (startId < 0) return false;
        if (fromId == startId) return true;
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (id == end.id() || !seen.add(id)) continue;
            BlockInstance cur = script().block(id);
            if (cur != null && AiEditorScreen.isContainer(cur.type()) && cur.pairedId() >= 0) {
                if (cur.pairedId() == fromId) return true;
                stack.push(cur.pairedId());
            }
            for (Wire w : script().wires()) {
                if (w.fromBlockId() != id) continue;
                if (w.toBlockId() == fromId) return true;
                stack.push(w.toBlockId());
            }
        }
        return false;
    }

    private BlockRenderer.Layout layout(BlockInstance b) {
        if (caching) {
            BlockRenderer.Layout cached = frameLayouts.get(b.id());
            if (cached != null) return cached;
            BlockRenderer.Layout L = BlockRenderer.layout(BlockDefRegistry.get(b.type()), b, font, script());
            frameLayouts.put(b.id(), L);
            return L;
        }
        return BlockRenderer.layout(BlockDefRegistry.get(b.type()), b, font, script());
    }

    private static boolean within(BlockRenderer.Layout L, double x, double y) {
        if (x < L.x || x > L.x + L.w || y < L.y || y > L.y + L.h) return false;
        if (!L.cShape) return true;

        if (x <= L.x + L.spineW) return true;
        for (int k = 0; k + 1 < L.arms.size(); k++) {
            int ceil = L.arms.get(k)[1];
            int floor = L.arms.get(k + 1)[0];
            if (y > ceil && y < floor) return false;
        }
        return true;
    }

    private record Hit(BlockInstance block, BlockInstance parent, String slot, BlockRenderer.Layout layout) {}

    private Hit deepHit(double worldX, double worldY) {
        Hit top = null;
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            if (within(L, worldX, worldY)) top = new Hit(b, null, null, L);
        }
        return top == null ? null : descend(top, worldX, worldY);
    }

    private Hit descend(Hit h, double worldX, double worldY) {
        for (BlockRenderer.Nested ns : h.layout().nested) {
            if (within(ns.layout(), worldX, worldY)) {
                return descend(new Hit(ns.block(), h.block(), ns.slot(), ns.layout()), worldX, worldY);
            }
        }
        return h;
    }

    private int deepHitId(double worldX, double worldY) {
        Hit h = deepHit(worldX, worldY);
        return h == null ? -1 : h.block().id();
    }

    public boolean overBlockOrComment(double mx, double my) {
        if (!inside(mx, my)) return false;
        double[] w = screenToWorld(mx, my);
        return deepHitId(w[0], w[1]) >= 0 || commentAt(w[0], w[1]) != null;
    }

    public BlockType hoveredType(double mx, double my) {
        if (!inside(mx, my) || dragBlock != null || wireFromId >= 0 || panning) return null;
        double[] w = screenToWorld(mx, my);
        int id = deepHitId(w[0], w[1]);
        if (id < 0) {
            int[] hit = outPortHit(w[0], w[1]);
            if (hit != null) id = hit[0];
        }
        if (id < 0) return null;
        BlockInstance b = script().block(id);
        return b == null ? null : b.type();
    }

    public BlockType wireTargetType(double mx, double my) {
        if (!inside(mx, my)) return null;
        double[] w = screenToWorld(mx, my);
        int id = -1;
        if (wireFromId >= 0) {
            int t = resolveInputTargetId(w[0], w[1]);
            if (t != wireFromId) id = t;
        } else if (wireToId >= 0) {
            int[] hit = resolveOutTarget(w[0], w[1], wireToId);
            if (hit != null && hit[0] != wireToId) id = hit[0];
        }
        if (id < 0) return null;
        BlockInstance b = script().block(id);
        return b == null ? null : b.type();
    }

    private int inputPortHit(double worldX, double worldY) {
        int best = -1;
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            if (!L.hasInput) continue;
            int hw = L.inHalfW + 3;
            if (L.sideInput) {
                if (worldX >= L.inX - BlockRenderer.PORT_H - 3 && worldX <= L.inX + 3
                        && worldY >= L.inY - hw && worldY <= L.inY + hw) best = b.id();
            } else if (worldX >= L.inX - hw && worldX <= L.inX + hw
                    && worldY >= L.inY - BlockRenderer.PORT_H - 3 && worldY <= L.inY + 3) best = b.id();
        }
        return best;
    }

    private int[] outPortHit(double worldX, double worldY) {
        int[] best = null;
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            for (int i = 0; i < L.outPorts.size(); i++) {
                if (inPortRect(worldX, worldY, L.outPorts.get(i), i == L.sideOutPort)) best = new int[]{b.id(), i};
            }
        }
        return best;
    }

    private boolean inPortRect(double x, double y, int[] p, boolean side) {
        int hw = BlockRenderer.PORT_W / 2;
        if (side) {
            return x >= p[0] - 1 && x <= p[0] + BlockRenderer.PORT_H + 1
                    && y >= p[1] - hw - 1 && y <= p[1] + hw + 1;
        }
        return x >= p[0] - hw - 1 && x <= p[0] + hw + 1
                && y >= p[1] - 1 && y <= p[1] + BlockRenderer.PORT_H + 1;
    }

    private Object[] findSlotAt(double worldX, double worldY, int ignoreId) {
        Object[] best = null;
        for (BlockInstance b : script().blocks().values()) {
            if (b.id() == ignoreId) continue;
            Object[] r = slotIn(b, layout(b), worldX, worldY);
            if (r != null) best = r;
        }
        return best;
    }

    private Object[] slotIn(BlockInstance b, BlockRenderer.Layout L, double worldX, double worldY) {
        for (BlockRenderer.Nested ns : L.nested) {
            Object[] r = slotIn(ns.block(), ns.layout(), worldX, worldY);
            if (r != null) return r;
        }
        for (BlockRenderer.ChipRect c : L.chips) {
            if (c.contains(worldX, worldY)) return new Object[]{b, c.name()};
        }
        return null;
    }

    boolean canNest(BlockInstance host, String slotName, BlockInstance reporter) {
        return nestReason(host, slotName, reporter) == null;
    }

    private String nestReason(BlockInstance host, String slotName, BlockInstance reporter) {
        ParamType slotType = slotType(host, slotName);
        BlockShape shape = BlockDefRegistry.get(reporter.type()).shape();

        if (reporter.type().refsOwner() && reporter.pairedId() >= 0) {
            BlockInstance owner = topLevelOwner(host);
            if (owner == null || !inOwnerScope(reporter.pairedId(), owner.id())) {
                return ownerRefError(reporter.type());
            }
        }

        if (host.type() == BlockType.EQUIPMENT && "target".equals(slotName)) {
            return "equipment target can't take a block";
        }

        if (EffectiveSlots.isCalcBlock(host.type()) && slotName.startsWith("Input")) {
            if (shape != BlockShape.REPORTER && shape != BlockShape.BOOLEAN) {
                return "calc inputs need a value block";
            }
            ParamType out = EffectiveSlots.reporterOutputType(reporter, script());
            if (!EffectiveSlots.calcInputAccepts(host.type(), out)) {
                return host.type() == BlockType.POS_CALC
                        ? "pos calc inputs take numbers or positions"
                        : "dir calc inputs take numbers or directions";
            }
            return null;
        }

        if (host.type() == BlockType.TERNARY && EffectiveSlots.isTernaryValueSlot(slotName)) {
            if (shape != BlockShape.REPORTER && shape != BlockShape.BOOLEAN) {
                return "needs a value block";
            }
            ParamType out = EffectiveSlots.reporterOutputType(reporter, script());
            if (!EffectiveSlots.ternaryAccepts(host, script(), slotName, out)) {
                return "locked to " + typeName(EffectiveSlots.ternaryLockedType(host, script()))
                        + " by the other input";
            }
            return null;
        }

        if (host.type() == BlockType.RECIPE_BOOK && "mode".equals(slotName)) {
            if (shape == BlockShape.BOOLEAN) return null;
            ParamType modeOut = EffectiveSlots.reporterOutputType(reporter, script());
            if (modeOut == ParamType.BOOLEAN || modeOut == ParamType.INT || modeOut == ParamType.DOUBLE) return null;
            return "needs a boolean or a number (0 = single, 1 = max)";
        }

        if (host.type() == BlockType.EQUALITY && ("a".equals(slotName) || "b".equals(slotName))) {
            if (shape != BlockShape.REPORTER) return "needs a value block, not a boolean";
            ParamType out = EffectiveSlots.reporterOutputType(reporter, script());
            if (!EffectiveSlots.typesMatch(equalitySiblingType(host, slotName), out)) {
                return "must match the other side (" + typeName(equalitySiblingType(host, slotName)) + ")";
            }
            return null;
        }

        if (slotType == ParamType.BOOLEAN) {
            if (shape == BlockShape.BOOLEAN) return null;
            ParamType boolOut = EffectiveSlots.reporterOutputType(reporter, script());
            if (boolOut == ParamType.BOOLEAN || boolOut == ParamType.INT || boolOut == ParamType.DOUBLE) return null;
            return "needs a boolean or a number (0 or less is false, 1 or more is true)";
        }
        if (shape != BlockShape.REPORTER) return "a boolean can't go in a " + typeName(slotType) + " input";
        ParamType out = EffectiveSlots.reporterOutputType(reporter, script());
        if (!EffectiveSlots.accepts(host.type(), slotName, slotType, out)) {
            return "needs " + typeName(slotType) + ", got " + typeName(out);
        }
        return null;
    }

    private static String typeName(ParamType t) {
        if (t == null) return "any";
        return switch (t) {
            case BOOLEAN -> "a boolean";
            case INT, DOUBLE -> "a number";
            case STRING -> "text";
            case POSITION -> "a position";
            case ROTATION -> "a rotation";
            case UUID -> "an entity";
            case ITEM -> "an item";
            default -> t.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private BlockInstance topLevelOwner(BlockInstance b) {
        if (script().block(b.id()) == b) return b;
        for (BlockInstance top : script().blocks().values()) {
            if (holdsDeep(top, b)) return top;
        }
        return null;
    }

    private boolean holdsDeep(BlockInstance host, BlockInstance target) {
        for (BlockInstance child : host.reporterParams().values()) {
            if (child == target || holdsDeep(child, target)) return true;
        }
        return false;
    }

    private boolean inOwnerScope(int ownerId, int blockId) {
        BlockInstance owner = script().block(ownerId);
        if (owner != null && EffectiveSlots.isLoopBlock(owner.type())) {
            return inLoopBody(ownerId, blockId);
        }
        return isDownstreamOf(ownerId, blockId);
    }

    private static String ownerRefError(BlockType type) {
        return switch (type) {
            case LOOP_ITER -> "iterators only work inside their own loop";
            case FUNC_PARAM -> "inputs only work inside their own function";
            case MSG_TEXT -> "message only works inside its On message block";
            default -> "reference only works inside its owner";
        };
    }

    private boolean isDownstreamOf(int fromId, int blockId) {
        if (script().block(fromId) == null) return false;
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        seen.add(fromId);
        pending.add(fromId);
        while (!pending.isEmpty()) {
            int cur = pending.poll();
            for (Wire w : script().wires()) {
                if (w.fromBlockId() != cur || !seen.add(w.toBlockId())) continue;
                if (w.toBlockId() == blockId) return true;
                pending.add(w.toBlockId());
            }
        }
        return false;
    }

    private boolean inLoopBody(int loopId, int blockId) {
        BlockInstance loop = script().block(loopId);
        if (loop == null) return false;
        if (blockId == loopId) return true;
        int ports = BlockDefRegistry.get(loop.type()).outPorts();
        int bodyPorts = ports >= 2 ? ports - 1 : ports;
        BlockInstance end = endOfLoop(loop);
        int stopId = end == null ? -1 : end.id();

        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        seen.add(loopId);
        for (Wire w : script().wires()) {
            if (w.fromBlockId() == loopId && w.outPort() < bodyPorts && seen.add(w.toBlockId())) {
                if (w.toBlockId() == blockId) return true;
                pending.add(w.toBlockId());
            }
        }
        while (!pending.isEmpty()) {
            int cur = pending.poll();
            if (cur == stopId) continue;
            for (Wire w : script().wires()) {
                if (w.fromBlockId() != cur || !seen.add(w.toBlockId())) continue;
                if (w.toBlockId() == blockId) return true;
                pending.add(w.toBlockId());
            }
        }
        return false;
    }

    boolean canNestType(BlockInstance host, String slotName, BlockType type) {
        return canNest(host, slotName, new BlockInstance(-1, type, 0, 0));
    }

    public Object[] slotTargetAt(double worldX, double worldY) {
        return findSlotAt(worldX, worldY, -1);
    }

    public String dropReason() {
        return dropReason;
    }

    private ParamType equalitySiblingType(BlockInstance host, String slotName) {
        BlockInstance rep = host.getReporter("a".equals(slotName) ? "b" : "a");
        return rep == null ? null : EffectiveSlots.reporterOutputType(rep, script());
    }

    private ParamType slotType(BlockInstance block, String slot) {
        for (ParamSlot s : EffectiveSlots.forBlock(block, script())) {
            if (s.name().equals(slot)) return s.type();
        }
        return null;
    }

    private void detach(BlockInstance parent, String slot, BlockInstance child, BlockRenderer.Layout childLayout) {
        parent.reporterParams().remove(slot);
        child.setPos(childLayout.x, childLayout.y);
        script().putBlock(child);
    }

    private void drawSlotHighlight(GuiGraphics g, BlockInstance target, String slot, boolean valid) {
        for (BlockRenderer.ChipRect c : nestedLayoutOf(target).chips) {
            if (!c.name().equals(slot)) continue;
            int col = valid ? 0xFFE6E600 : 0xFFFF3030;
            int right = c.x() + c.w();
            int bottom = c.y() + c.h();
            g.fill(c.x() - 1, c.y() - 1, right + 1, c.y(), col);
            g.fill(c.x() - 1, bottom, right + 1, bottom + 1, col);
            g.fill(c.x() - 1, c.y(), c.x(), bottom, col);
            g.fill(right, c.y(), right + 1, bottom, col);
            return;
        }
    }

    private BlockRenderer.Layout nestedLayoutOf(BlockInstance target) {
        if (script().blocks().containsKey(target.id())) return layout(target);
        for (BlockInstance root : script().blocks().values()) {
            BlockRenderer.Layout r = findLayout(layout(root), target.id());
            if (r != null) return r;
        }
        return layout(target);
    }

    private BlockRenderer.Layout findLayout(BlockRenderer.Layout L, int id) {
        for (BlockRenderer.Nested ns : L.nested) {
            if (ns.block().id() == id) return ns.layout();
            BlockRenderer.Layout r = findLayout(ns.layout(), id);
            if (r != null) return r;
        }
        return null;
    }

    public boolean mouseClicked(double mx, double my, int button, boolean ctrl, boolean shift, boolean doubled) {
        if (menu.open) {
            if (button == 0) return menu.click(mx, my);
            menu.open = false;
        }
        if (!inside(mx, my)) return false;
        lastMx = mx; lastMy = my;

        double worldX = wx(mx), worldY = wy(my);

        if (editing != null && button == 0 && formatBarClick(mx, my)) return true;

        if (editing != null && button == 0) {
            if (withinComment(editing, worldX, worldY)) {
                caret = caretFromWorld(editing, worldX, worldY);
                if (!shift) selAnchor = caret;
                return true;
            }
            stopEditingComment();
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && shift) {
            shiftRightSpawn = true;
            shiftRightDragged = false;
            spawnStartTick = starTick;
            spawnCount = 0;
            spawnDelay = SPAWN_DELAY_START;
            spawnThreshold = SPAWN_DELAY_START;
            spawnMx = mx; spawnMy = my;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { rightPressed = true; rightDownX = mx; rightDownY = my; return true; }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shift && doubled) {
            Hit bypass = deepHit(worldX, worldY);
            if (bypass != null && bypass.parent() == null) {
                host.bypassBlock(bypass.block().id());
                host.select(bypass.block().id());
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                || (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && shift)) { panning = true; return true; }

        Comment cm = commentAt(worldX, worldY);
        if (cm != null) {
            if (doubled) {
                startEditingComment(cm);
                caret = caretFromWorld(cm, worldX, worldY);
                return true;
            }
            if (ctrl) {
                host.toggleSelect(cm.id());
                if (!host.isSelected(cm.id())) return true;
            } else if (!host.isSelected(cm.id())) {
                host.select(cm.id());
            }
            beginCommentDrag(cm, worldX, worldY);
            return true;
        }

        int[] portHit = outPortHit(worldX, worldY);
        if (portHit != null) {
            if (doubled) {
                if (!outPortHasWire(portHit[0], portHit[1])) connectNearestFromOutput(portHit[0], portHit[1]);
                wireFromId = -1;
                host.select(portHit[0]);
                return true;
            }
            wireFromId = portHit[0]; wireFromPort = portHit[1]; wireMx = mx; wireMy = my;
            host.select(portHit[0]);
            return true;
        }

        int inHit = inputPortHit(worldX, worldY);
        if (inHit >= 0) {
            if (doubled) {
                if (!inputHasWire(inHit)) connectNearestFromInput(inHit);
                wireToId = -1;
                host.select(inHit);
                return true;
            }
            wireToId = inHit; wireMx = mx; wireMy = my;
            host.select(inHit);
            return true;
        }

        Hit hit = deepHit(worldX, worldY);
        if (hit != null) {
            BlockInstance b = hit.block();
            int[] plus = hit.layout().plus;
            if (plus != null && worldX >= plus[0] && worldX <= plus[0] + plus[2]
                    && worldY >= plus[1] && worldY <= plus[1] + plus[3]) {
                if (EffectiveSlots.isCalcBlock(b.type())) host.addCalcInput(b);
                else if (b.type() == BlockType.FUNC_DEFINE) host.addFuncParam(b);
                else host.spawnElseIf(b);
                return true;
            }
            for (BlockRenderer.ParamRow p : hit.layout().paramRows) {
                if (BlockRenderer.ParamRow.hits(p.typeRect(), worldX, worldY)) {
                    pickParamType(b, p.index(), mx, my);
                    host.select(b.id());
                    return true;
                }
                if (BlockRenderer.ParamRow.hits(p.dragRect(), worldX, worldY)) {
                    BlockInstance ref = host.spawnFuncParam(b, p.index(), worldX, worldY);
                    if (ref != null) {
                        host.select(ref.id());
                        beginBlockDrag(ref, worldX, worldY);
                        dragDidSnapshot = true;
                    }
                    return true;
                }
            }
            int[] expander = hit.layout().expander;
            if (expander != null && worldX >= expander[0] && worldX <= expander[0] + expander[2]
                    && worldY >= expander[1] && worldY <= expander[1] + expander[3]) {
                if (EffectiveSlots.isLookBlock(b.type())) host.toggleLookExpand(b);
                else if (EffectiveSlots.isCalcBlock(b.type())) host.removeCalcInput(b);
                else if (EffectiveSlots.sensorTakesTarget(b.type())) host.toggleSensorTarget(b);
                else if (EffectiveSlots.isLoopBlock(b.type())) host.toggleLoopIter(b);
                else if (EffectiveSlots.sendTakesOp(b.type())) host.toggleSendOp(b);
                else if (b.type() == BlockType.FUNC_DEFINE) host.removeLastFuncParam(b);
                return true;
            }
            int[] iterChip = hit.layout().iterChip;
            if (iterChip != null && worldX >= iterChip[0] && worldX <= iterChip[0] + iterChip[2]
                    && worldY >= iterChip[1] && worldY <= iterChip[1] + iterChip[3]) {
                BlockInstance iter = host.spawnLoopIterator(b, worldX, b.y() + hit.layout().h);
                if (iter != null) {
                    host.select(iter.id());
                    beginBlockDrag(iter, worldX, worldY);
                    dragDidSnapshot = true;
                }
                return true;
            }
            int[] msgChip = hit.layout().msgChip;
            if (msgChip != null && worldX >= msgChip[0] && worldX <= msgChip[0] + msgChip[2]
                    && worldY >= msgChip[1] && worldY <= msgChip[1] + msgChip[3]) {
                BlockInstance ref = host.spawnMessageRef(b, worldX, b.y() + hit.layout().h);
                if (ref != null) {
                    host.select(ref.id());
                    beginBlockDrag(ref, worldX, worldY);
                    dragDidSnapshot = true;
                }
                return true;
            }
            int[] cycle = hit.layout().cycleButton;
            if (cycle != null && worldX >= cycle[0] && worldX <= cycle[0] + cycle[2]
                    && worldY >= cycle[1] && worldY <= cycle[1] + cycle[3]) {
                host.cycleVarBlock(b);
                return true;
            }
            for (BlockRenderer.ChipRect c : hit.layout().chips) {
                if (c.contains(worldX, worldY)) {
                    ParamType pt = slotType(b, c.name());
                    if (b.type() == BlockType.SET_SCRIPT && c.name().equals("script")) pickScript(b, mx, my);
                    else if (b.type() == BlockType.FUNC_DEFINE && c.name().equals("name")) pickDefineName(b, mx, my);
                    else if (b.type() == BlockType.FUNC_CALL && c.name().equals("name")) pickCallName(b, mx, my);
                    else if (pt == ParamType.VAR_REF) pickVarRef(b, c.name(), mx, my);
                    else if (pt == ParamType.ENUM) pickEnum(b, c.name(), mx, my);
                    else host.editParam(b, c.name());
                    host.select(b.id());
                    return true;
                }
            }

            boolean detached = hit.parent() != null;
            if (doubled && !detached) {
                connectNearestAllPorts(b);
                host.select(b.id());
                return true;
            }
            if (detached) {
                host.pushUndo();
                detach(hit.parent(), hit.slot(), b, hit.layout());
                host.ternarySlotChanged(hit.parent(), hit.slot());
                host.select(b.id());
            } else if (ctrl) {
                host.toggleSelect(b.id());
                if (!host.isSelected(b.id())) return true;
            } else if (!host.isSelected(b.id())) {
                host.select(b.id());
            }
            beginBlockDrag(b, worldX, worldY);
            if (detached) dragDidSnapshot = true;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && stars.blackHoleGrabbed(mx, my)) {
            double[] pos = stars.blackHolePos();
            if (pos != null) {
                holeDrag = true;
                holeGrabDx = mx - pos[0];
                holeGrabDy = my - pos[1];
                return true;
            }
        }

        if (deleteWireNear(worldX, worldY)) return true;
        if (!ctrl) host.select(-1);
        selecting = true; marqueeAdditive = ctrl;
        selStartX = selCurX = mx; selStartY = selCurY = my;
        return true;
    }

    private void beginBlockDrag(BlockInstance grabbed, double worldX, double worldY) {
        dragBlock = grabbed;
        dragActive = true;
        dragAnchorX = worldX;
        dragAnchorY = worldY;
        dragDidSnapshot = false;
        snapshotGroup();
        dragGroup.putIfAbsent(grabbed.id(), new double[]{grabbed.x(), grabbed.y()});
        addPairedEnds();
        snapOwnedRefsIntoBounds();
    }

    private void snapOwnedRefsIntoBounds() {
        for (Map.Entry<Integer, double[]> e : dragGroup.entrySet()) {
            BlockInstance b = script().block(e.getKey());
            if (b == null || !b.type().refsOwner()) continue;
            BlockInstance owner = b.pairedId() >= 0 ? script().block(b.pairedId()) : null;
            if (owner == null || dragGroup.containsKey(owner.id())) continue;
            double minY = owner.y() + layout(owner).h;
            double y = Math.max(b.y(), minY);
            if (EffectiveSlots.isLoopBlock(owner.type())) {
                BlockInstance end = endOfLoop(owner);
                if (end != null && !dragGroup.containsKey(end.id())) {
                    double maxY = end.y() - layout(b).h;
                    y = maxY > minY ? Math.min(y, maxY) : minY;
                }
            }
            if (y == b.y()) continue;
            if (!dragDidSnapshot) { host.pushUndo(); dragDidSnapshot = true; }
            b.setPos(b.x(), y);
            e.setValue(new double[]{b.x(), y});
        }
    }

    private void addPairedEnds() {
        for (int id : new ArrayList<>(dragGroup.keySet())) {
            BlockInstance b = script().block(id);
            if (b == null || !AiEditorScreen.isContainer(b.type()) || b.pairedId() < 0) continue;
            BlockInstance end = script().block(b.pairedId());
            if (end != null) dragGroup.putIfAbsent(end.id(), new double[]{end.x(), end.y()});
            if (!EffectiveSlots.isLoopBlock(b.type())) continue;
            for (BlockInstance it : script().blocks().values()) {
                if (it.type() == BlockType.LOOP_ITER && it.pairedId() == b.id()) {
                    dragGroup.putIfAbsent(it.id(), new double[]{it.x(), it.y()});
                }
            }
        }
    }

    private void beginCommentDrag(Comment grabbed, double worldX, double worldY) {
        dragBlock = null;
        dragActive = true;
        dragAnchorX = worldX;
        dragAnchorY = worldY;
        dragDidSnapshot = false;
        snapshotGroup();
        if (!dragGroupComments.containsKey(grabbed.id())) {
            detachComment(grabbed);
            dragGroupComments.put(grabbed.id(), new double[]{grabbed.x(), grabbed.y()});
        }
    }

    private void snapshotGroup() {
        dragGroup.clear();
        dragGroupComments.clear();
        for (int id : host.selection()) {
            BlockInstance b = script().block(id);
            if (b != null) { dragGroup.put(id, new double[]{b.x(), b.y()}); continue; }
            Comment c = script().comment(id);
            if (c != null) {
                detachComment(c);
                dragGroupComments.put(id, new double[]{c.x(), c.y()});
            }
        }
    }

    private void detachComment(Comment c) {
        if (c.attachedTo() >= 0) {
            double ex = cX(c), ey = cY(c);
            c.setAttachedTo(-1);
            c.setPos(ex, ey);
        }
    }

    private boolean deleteWireNear(double worldX, double worldY) {
        Wire w = wireAt(worldX, worldY);
        if (w != null) {
            host.pushUndo();
            script().wires().remove(w);
            return true;
        }
        return false;
    }

    private Wire wireAt(double worldX, double worldY) {
        Wire best = null;
        double bestD = 5;
        for (Wire w : script().wires()) {
            BlockInstance from = script().block(w.fromBlockId());
            BlockInstance to = script().block(w.toBlockId());
            if (from == null || to == null) continue;
            BlockRenderer.Layout lf = layout(from);
            BlockRenderer.Layout lt = layout(to);
            if (w.outPort() >= lf.outPorts.size()) continue;
            int[] p = lf.outPorts.get(w.outPort());
            double d = isSideWire(from, w)
                    ? distToWireH(worldX, worldY, p[0], p[1], lt.inX, lt.inY)
                    : distToWire(worldX, worldY, p[0], p[1], lt.inX, lt.inY, midYFor(w, p[1], lt.inY));
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    private double distToWire(double px, double py, double x1, double y1, double x2, double y2, double midY) {
        double d = distToSeg(px, py, x1, y1, x1, midY);
        d = Math.min(d, distToSeg(px, py, x1, midY, x2, midY));
        d = Math.min(d, distToSeg(px, py, x2, midY, x2, y2));
        return d;
    }

    private double distToWireH(double px, double py, double x1, double y1, double x2, double y2) {
        double midX = (x1 + x2) / 2.0;
        double d = distToSeg(px, py, x1, y1, midX, y1);
        d = Math.min(d, distToSeg(px, py, midX, y1, midX, y2));
        d = Math.min(d, distToSeg(px, py, midX, y2, x2, y2));
        return d;
    }

    private double distToSeg(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.clamp(((px - x1) * dx + (py - y1) * dy) / len2, 0, 1);
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    public boolean mouseDragged(double mx, double my) {
        if (shiftRightSpawn) { shiftRightDragged = true; spawnMx = mx; spawnMy = my; return true; }
        if (rightPressed && !panning
                && Math.hypot(mx - rightDownX, my - rightDownY) >= RIGHT_CLICK_SLOP) {
            panning = true;
            lastMx = mx; lastMy = my;
        }
        if (holeDrag) {
            stars.moveBlackHole(mx - holeGrabDx, my - holeGrabDy);
            return true;
        }
        if (panning) {
            panX += mx - lastMx;
            panY += my - lastMy;
            lastMx = mx; lastMy = my;
            return true;
        }
        if (selecting) {
            selCurX = mx; selCurY = my;
            return true;
        }
        if (dragActive) {
            if (!dragDidSnapshot) { host.pushUndo(); dragDidSnapshot = true; }
            boolean draggingEnd = dragBlock != null && dragBlock.type() == BlockType.BLOCK_END;
            double dx = (dragGroup.isEmpty() || draggingEnd)
                    ? (wx(mx) - dragAnchorX) : clampGroupDeltaX(wx(mx) - dragAnchorX);
            double dy = (dragGroup.isEmpty() || draggingEnd)
                    ? (wy(my) - dragAnchorY) : clampGroupDeltaY(wy(my) - dragAnchorY);
            for (Map.Entry<Integer, double[]> e : dragGroup.entrySet()) {
                BlockInstance s = script().block(e.getKey());
                if (s != null) s.setPos(e.getValue()[0] + dx, e.getValue()[1] + dy);
            }
            for (Map.Entry<Integer, double[]> e : dragGroupComments.entrySet()) {
                Comment s = script().comment(e.getKey());
                if (s != null) s.setPos(e.getValue()[0] + dx, e.getValue()[1] + dy);
            }
            return true;
        }
        if (wireFromId >= 0) {
            if (draggingFromSidePort()) {
                wireMx = Math.max(mx, leftmostWireStartScreenX());
                wireMy = my;
            } else {
                wireMx = mx;
                wireMy = Math.max(my, lowestWireStartScreenY());
            }
            return true;
        }
        if (wireToId >= 0) {
            wireMx = mx;
            wireMy = my;
            return true;
        }
        return false;
    }

    private boolean draggingFromSidePort() {
        BlockInstance from = script().block(wireFromId);
        if (from == null) return false;
        return layout(from).sideOutPort == wireFromPort;
    }

    private double lowestWireStartScreenY() {
        BlockInstance from = script().block(wireFromId);
        if (from == null) return Double.NEGATIVE_INFINITY;
        BlockRenderer.Layout lf = layout(from);
        if (wireFromPort >= lf.outPorts.size()) return Double.NEGATIVE_INFINITY;
        return sy(lf.outPorts.get(wireFromPort)[1] + BlockRenderer.PORT_H) + 1;
    }

    private double leftmostWireStartScreenX() {
        BlockInstance from = script().block(wireFromId);
        if (from == null) return Double.NEGATIVE_INFINITY;
        BlockRenderer.Layout lf = layout(from);
        if (wireFromPort >= lf.outPorts.size()) return Double.NEGATIVE_INFINITY;
        return sx(lf.outPorts.get(wireFromPort)[0] + BlockRenderer.PORT_H) + 1;
    }

    private double clampGroupDeltaY(double dy) {
        double lower = Double.NEGATIVE_INFINITY, upper = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, double[]> e : dragGroup.entrySet()) {
            int id = e.getKey();
            BlockInstance b = script().block(id);
            if (b == null) continue;
            BlockRenderer.Layout L = layout(b);
            double oy = e.getValue()[1];
            double inOff = L.inY - b.y();
            if (b.type().refsOwner()) {
                BlockInstance owner = b.pairedId() >= 0 ? script().block(b.pairedId()) : null;
                if (owner != null && !dragGroup.containsKey(owner.id())) {
                    lower = Math.max(lower, (owner.y() + layout(owner).h) - oy);
                    if (EffectiveSlots.isLoopBlock(owner.type())) {
                        BlockInstance end = endOfLoop(owner);
                        if (end != null && !dragGroup.containsKey(end.id())) {
                            upper = Math.min(upper, (end.y() - L.h) - oy);
                        }
                    }
                }
            }
            for (Wire w : script().wires()) {
                if (w.toBlockId() == id && !dragGroup.containsKey(w.fromBlockId())) {
                    BlockInstance f = script().block(w.fromBlockId());
                    if (f == null) continue;
                    BlockRenderer.Layout lf = layout(f);
                    if (w.outPort() >= lf.outPorts.size()) continue;
                    double outFY = lf.outPorts.get(w.outPort())[1];
                    double minInY = isSideWire(f, w) ? outFY : outFY + 1;
                    lower = Math.max(lower, minInY - (oy + inOff));
                }
                if (w.fromBlockId() == id && !dragGroup.containsKey(w.toBlockId())) {
                    BlockInstance t = script().block(w.toBlockId());
                    if (t == null || w.outPort() >= L.outPorts.size()) continue;
                    BlockRenderer.Layout lt = layout(t);
                    if (!lt.hasInput) continue;
                    double outOff = L.outPorts.get(w.outPort())[1] - b.y();
                    double maxOutY = isSideWire(b, w) ? lt.inY : lt.inY - 1;
                    upper = Math.min(upper, maxOutY - (oy + outOff));
                }
            }
        }
        if (lower > upper) return lower;
        return Math.clamp(dy, lower, upper);
    }

    private double clampGroupDeltaX(double dx) {
        double lower = Double.NEGATIVE_INFINITY, upper = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, double[]> e : dragGroup.entrySet()) {
            int id = e.getKey();
            BlockInstance b = script().block(id);
            if (b == null) continue;
            BlockRenderer.Layout L = layout(b);
            double ox = e.getValue()[0];
            double inOff = L.inX - b.x();
            for (Wire w : script().wires()) {
                if (w.toBlockId() == id && !dragGroup.containsKey(w.fromBlockId())) {
                    BlockInstance f = script().block(w.fromBlockId());
                    if (f == null || !isSideWire(f, w)) continue;
                    BlockRenderer.Layout lf = layout(f);
                    if (w.outPort() >= lf.outPorts.size()) continue;
                    double outFX = lf.outPorts.get(w.outPort())[0];
                    lower = Math.max(lower, (outFX + 1) - (ox + inOff));
                }
                if (w.fromBlockId() == id && !dragGroup.containsKey(w.toBlockId())) {
                    BlockInstance t = script().block(w.toBlockId());
                    if (t == null || !isSideWire(b, w) || w.outPort() >= L.outPorts.size()) continue;
                    BlockRenderer.Layout lt = layout(t);
                    if (!lt.hasInput) continue;
                    double outOff = L.outPorts.get(w.outPort())[0] - b.x();
                    upper = Math.min(upper, (lt.inX - 1) - (ox + outOff));
                }
            }
        }
        if (lower > upper) return lower;
        return Math.clamp(dx, lower, upper);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && shiftRightSpawn) {
            shiftRightSpawn = false;
            if (shiftRightDragged) {
                stars.spawnConverging(Math.max(1, spawnCount), left, top, right, bottom, SPAWN_BURST_MEET);
            } else {
                stars.spawnConverging(1, left, top, right, bottom, SPAWN_BURST_MEET);
                stars.spark(mx, my);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && rightPressed) {
            rightPressed = false;
            panning = false;
            if (Math.hypot(mx - rightDownX, my - rightDownY) < RIGHT_CLICK_SLOP) openContextMenu(mx, my);
            return true;
        }
        if (holeDrag) {
            holeDrag = false;
            return true;
        }
        boolean handled = false;
        double worldX = wx(mx), worldY = wy(my);
        if (selecting) {
            applyMarquee();
            selecting = false;
            return true;
        }
        if (wireToId >= 0) {
            int[] hit = resolveOutTarget(worldX, worldY, wireToId);
            if (hit != null && hit[0] != wireToId) {
                addWire(hit[0], hit[1], wireToId);
            }
            wireToId = -1;
            handled = true;
        } else if (wireFromId >= 0) {
            int toId = resolveInputTargetId(worldX, worldY);
            if (toId >= 0 && toId != wireFromId) {
                addWire(wireFromId, wireFromPort, toId);
            }
            wireFromId = -1;
            handled = true;
        } else if (dragBlock != null && BlockDefRegistry.get(dragBlock.type()).isReporter()) {
            Object[] tgt = findSlotAt(worldX, worldY, dragBlock.id());
            boolean nested = false;
            if (tgt != null) {
                BlockInstance target = (BlockInstance) tgt[0];
                String slot = (String) tgt[1];
                if (canNest(target, slot, dragBlock)) {
                    script().blocks().remove(dragBlock.id());
                    target.setReporter(slot, dragBlock);
                    host.ternarySlotChanged(target, slot);
                    nested = true;
                    handled = true;
                }
            }
            if (!nested && dragBlock.type().refsOwner()) {
                host.deleteBlock(dragBlock.id());
                handled = true;
            }
        } else if (dragBlock != null && dragDidSnapshot && spliceDragEligible() && canSplice(dragBlock)) {
            if (trySplice(dragBlock, worldX, worldY)) handled = true;
        }

        for (int id : dragGroupComments.keySet()) {
            Comment c = script().comment(id);
            if (c == null) continue;
            BlockInstance hit = topBlockAt(worldX, worldY);
            if (hit != null) {
                c.setAttachedTo(hit.id());
                c.setOffset(c.x() - hit.x(), c.y() - hit.y());
            } else {
                c.setAttachedTo(-1);
            }
        }
        panning = false;
        dragBlock = null;
        dragActive = false;
        dragGroup.clear();
        dragGroupComments.clear();
        return handled;
    }

    private void applyMarquee() {
        stars.explodeSelection(Math.min(selStartX, selCurX), Math.min(selStartY, selCurY),
                Math.max(selStartX, selCurX), Math.max(selStartY, selCurY));
        double wx0 = wx(Math.min(selStartX, selCurX)), wx1 = wx(Math.max(selStartX, selCurX));
        double wy0 = wy(Math.min(selStartY, selCurY)), wy1 = wy(Math.max(selStartY, selCurY));
        List<Integer> hits = new ArrayList<>();
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            boolean overlap = L.x <= wx1 && L.x + L.w >= wx0 && L.y <= wy1 && L.y + L.h >= wy0;
            if (overlap) hits.add(b.id());
        }
        for (Comment c : script().comments()) {
            measure(c);
            double px = cX(c), py = cY(c);
            boolean overlap = px <= wx1 && px + c.w >= wx0 && py <= wy1 && py + c.h >= wy0;
            if (overlap) hits.add(c.id());
        }
        host.selectMany(hits, marqueeAdditive);
    }

    private boolean wireAllowed(int fromId, int port, int toId) {
        return wireReason(fromId, port, toId) == null;
    }

    private String wireReason(int fromId, int port, int toId) {
        BlockInstance source = script().block(fromId);
        BlockInstance target = script().block(toId);
        if (source == null || target == null) return "that isn't a valid connection";
        if (wireWouldConflict(fromId, port, toId)) {
            return "this output already drives a conflicting " + shortLabel(target.type()) + " block";
        }
        boolean sidePort = port == 1
                && (source.type() == BlockType.IF || source.type() == BlockType.ELSE_IF);
        if (sidePort != (target.type() == BlockType.ELSE_IF)) {
            return sidePort
                    ? "the side port only connects to an else (if)"
                    : "an else (if) only accepts the side port of an if / else (if)";
        }
        if (target.type() == BlockType.BLOCK_END) {
            if (port == 0 && AiEditorScreen.isContainer(source.type()) && target.id() != source.pairedId()) {
                return "a container can only connect to its own end block";
            }
            if (!sourceConnectsToStart(target, fromId)) {
                return "this block isn't inside that end block's container";
            }
            if (endConflict(target, fromId)) {
                return "the end would join conflicting movement blocks";
            }
        }
        return null;
    }

    private void addWire(int fromId, int port, int toId) {
        if (!wireAllowed(fromId, port, toId)) return;
        BlockInstance source = script().block(fromId);
        BlockInstance target = script().block(toId);
        boolean sidePort = port == 1
                && (source.type() == BlockType.IF || source.type() == BlockType.ELSE_IF);
        boolean isEnd = target.type() == BlockType.BLOCK_END;
        for (Wire w : script().wires()) {
            if (w.fromBlockId() == fromId && w.outPort() == port && w.toBlockId() == toId) return;
        }
        host.pushUndo();
        List<BlockInstance> capsToMove = sidePort ? List.of() : endCapsToRelocate(fromId, toId);
        if (sidePort) script().wires().removeIf(w -> w.fromBlockId() == fromId && w.outPort() == 1);
        if (!isEnd) script().wires().removeIf(w -> w.toBlockId() == toId);
        script().addWire(fromId, port, toId);
        for (BlockInstance cap : capsToMove) {
            double bottom = chainBottom(toId, cap.id());
            cap.setPos(cap.x(), bottom + 18);
        }
    }

    private List<BlockInstance> endCapsToRelocate(int fromId, int toId) {
        List<BlockInstance> caps = new ArrayList<>();
        BlockInstance target = script().block(toId);
        if (target == null || target.type() == BlockType.BLOCK_END) return caps;
        for (BlockInstance c : script().blocks().values()) {
            if (!AiEditorScreen.isContainer(c.type()) || c.pairedId() < 0) continue;
            BlockInstance end = script().block(c.pairedId());
            if (end == null || end.id() == toId || end.id() == fromId) continue;
            if (sourceConnectsToStart(end, toId)) continue;
            if (fromId != c.id() && !sourceConnectsToStart(end, fromId)) continue;
            caps.add(end);
        }
        return caps;
    }

    private double chainBottom(int startId, int stopId) {
        double bottom = Double.NEGATIVE_INFINITY;
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (id == stopId || !seen.add(id)) continue;
            BlockInstance b = script().block(id);
            if (b == null) continue;
            BlockRenderer.Layout L = layout(b);
            bottom = Math.max(bottom, L.y + L.h);
            if (b.type() != BlockType.BLOCK_END && b.pairedId() >= 0) stack.push(b.pairedId());
            for (Wire w : script().wires()) {
                if (w.fromBlockId() == id) stack.push(w.toBlockId());
            }
        }
        return bottom;
    }

    private record SplicePreview(Wire wire, int exitId, boolean inputValid, boolean outputValid) {
        boolean valid() { return inputValid && outputValid; }
    }

    static boolean canSplice(BlockInstance b) {
        if (b.type() == BlockType.BLOCK_END) return false;
        BlockDef def = BlockDefRegistry.get(b.type());
        return def.hasInput() && def.outPorts() >= 1 && !def.isReporter();
    }

    private boolean spliceDragEligible() {
        if (dragBlock == null || !dragGroup.containsKey(dragBlock.id())) return false;
        if (dragGroup.size() == 1) return true;
        return dragGroup.size() == 2 && dragBlock.pairedId() >= 0
                && dragGroup.containsKey(dragBlock.pairedId());
    }

    private Set<Integer> spliceChain(int blockId) {
        Set<Integer> ids = new LinkedHashSet<>();
        ids.add(blockId);
        BlockInstance b = script().block(blockId);
        if (b != null && AiEditorScreen.isContainer(b.type()) && b.pairedId() >= 0) {
            BlockInstance end = script().block(b.pairedId());
            if (end != null) ids.add(end.id());
        }
        return ids;
    }

    private SplicePreview splicePreview(BlockInstance b, double worldX, double worldY) {
        Wire w = wireAt(worldX, worldY);
        if (w == null) return null;
        Set<Integer> chain = spliceChain(b.id());
        if (chain.contains(w.fromBlockId()) || chain.contains(w.toBlockId())) return null;
        BlockInstance from = script().block(w.fromBlockId());
        BlockInstance to = script().block(w.toBlockId());
        if (from == null || to == null) return null;
        boolean side = isSideWire(from, w);
        if ((b.type() == BlockType.ELSE_IF) != side) return null;
        if (side) return new SplicePreview(w, b.id(), true, true);

        int exitId = b.id();

        boolean inputValid = !wireWouldConflict(w.fromBlockId(), w.outPort(), b.id(), w);
        boolean outputValid;
        if (to.type() == BlockType.BLOCK_END) {
            outputValid = spliceConnects(to, exitId, w, w.fromBlockId(), b.id())
                    && !endConflict(to, exitId, w);
        } else {
            outputValid = !wireWouldConflict(exitId, 0, to.id(), w);
        }
        return new SplicePreview(w, exitId, inputValid, outputValid);
    }

    boolean trySplice(BlockInstance b, double worldX, double worldY) {
        if (!canSplice(b)) return false;
        SplicePreview p = splicePreview(b, worldX, worldY);
        if (p == null || !p.valid()) return false;
        Wire w = p.wire();
        if (b.type() == BlockType.ELSE_IF) {
            script().wires().remove(w);
            script().wires().removeIf(x -> x.toBlockId() == b.id());
            script().wires().removeIf(x -> x.fromBlockId() == b.id() && x.outPort() == 1);
            script().addWire(w.fromBlockId(), w.outPort(), b.id(), 0);
            script().addWire(b.id(), 1, w.toBlockId(), w.toPort());
            return true;
        }
        script().wires().remove(w);
        script().wires().removeIf(x -> x.toBlockId() == b.id());
        script().addWire(w.fromBlockId(), w.outPort(), b.id(), 0);
        boolean exists = false;
        for (Wire x : script().wires()) {
            if (x.fromBlockId() == p.exitId() && x.outPort() == 0 && x.toBlockId() == w.toBlockId()) {
                exists = true;
                break;
            }
        }
        if (!exists) script().addWire(p.exitId(), 0, w.toBlockId(), w.toPort());
        return true;
    }

    private boolean spliceConnects(BlockInstance end, int exitId, Wire removed, int addFrom, int addTo) {
        int startId = end.pairedId();
        if (startId < 0) return false;
        if (exitId == startId) return true;
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(startId);
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (id == exitId) return true;
            if (id == end.id() || !seen.add(id)) continue;
            for (Wire w : script().wires()) {
                if (w.equals(removed) || w.fromBlockId() != id) continue;
                stack.push(w.toBlockId());
            }
            if (id == addFrom) stack.push(addTo);
            BlockInstance b = script().block(id);
            if (b != null && b.type() != BlockType.BLOCK_END && b.pairedId() >= 0) {
                stack.push(b.pairedId());
            }
        }
        return false;
    }

    private boolean wireWouldConflict(int fromId, int port, int targetId) {
        return wireWouldConflict(fromId, port, targetId, null);
    }

    private boolean wireWouldConflict(int fromId, int port, int targetId, Wire ignore) {
        BlockInstance target = script().block(targetId);
        if (target == null) return false;
        for (Wire w : script().wires()) {
            if (w.equals(ignore)) continue;
            if (w.fromBlockId() != fromId || w.outPort() != port) continue;
            BlockInstance other = script().block(w.toBlockId());
            if (other != null && other.id() != targetId && incompatible(target.type(), other.type())) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithSibling(int id, int targetId) {
        BlockInstance target = script().block(targetId);
        BlockInstance b = script().block(id);
        if (target == null || b == null) return false;
        for (Wire w : script().wires()) {
            if (w.fromBlockId() == wireFromId && w.outPort() == wireFromPort && w.toBlockId() == id) {
                return incompatible(target.type(), b.type());
            }
        }
        return false;
    }

    private boolean endConflict(BlockInstance end, int extraFromId) {
        return endConflict(end, extraFromId, null);
    }

    private boolean endConflict(BlockInstance end, int extraFromId, Wire ignore) {
        List<BlockType> sources = new ArrayList<>();
        for (Wire w : script().wires()) {
            if (w.equals(ignore)) continue;
            if (w.toBlockId() != end.id()) continue;
            BlockInstance from = script().block(w.fromBlockId());
            if (from != null) sources.add(from.type());
        }
        if (extraFromId >= 0) {
            BlockInstance ex = script().block(extraFromId);
            if (ex != null) sources.add(ex.type());
        }
        for (int i = 0; i < sources.size(); i++) {
            for (int j = i + 1; j < sources.size(); j++) {
                if (incompatible(sources.get(i), sources.get(j))) return true;
            }
        }
        return false;
    }

    private static final Set<BlockType> SINGLE_PER_OUTPUT = EnumSet.of(
            BlockType.MOVE, BlockType.STRAFE, BlockType.SNEAK, BlockType.SPRINT);

    private static boolean incompatible(BlockType a, BlockType b) {
        return a == b && SINGLE_PER_OUTPUT.contains(a);
    }

    private static String shortLabel(BlockType t) {
        return BlockDefRegistry.get(t).label();
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (menu.open) { menu.scroll(amount); return true; }
        if (!inside(mx, my)) return false;
        double oldZoom = zoom;
        zoom = Math.clamp(zoom * Math.pow(ZOOM_STEP, amount), MIN_ZOOM, MAX_ZOOM);

        panX = mx - (mx - panX) * (zoom / oldZoom);
        panY = my - (my - panY) * (zoom / oldZoom);
        return true;
    }

    private double cX(Comment c) {
        if (c.attachedTo() >= 0) {
            BlockInstance b = script().block(c.attachedTo());
            if (b != null) return b.x() + c.offX();
        }
        return c.x();
    }

    private double cY(Comment c) {
        if (c.attachedTo() >= 0) {
            BlockInstance b = script().block(c.attachedTo());
            if (b != null) return b.y() + c.offY();
        }
        return c.y();
    }

    private void measure(Comment c) {
        c.w = COMMENT_W;
        c.h = Math.max(1, wrapLines(c).size()) * font.lineHeight + COMMENT_PAD * 2.0;
    }

    public double measureComment(Comment c) {
        measure(c);
        return c.h;
    }

    private List<int[]> wrapLines(Comment c) {
        String t = c.text();
        double maxW = COMMENT_W - COMMENT_PAD * 2;
        List<int[]> lines = new ArrayList<>();
        int n = t.length();
        int segStart = 0;
        while (true) {
            int hard = t.indexOf('\n', segStart);
            int segEnd = hard < 0 ? n : hard;
            int next = hard < 0 ? segEnd + 1 : hard + 1;
            int start = segStart;
            while (true) {
                double w = 0;
                int lastSpace = -1;
                int i = start;
                for (; i < segEnd; i++) {
                    double cw = CommentText.charWidth(font, c, i);
                    if (w + cw > maxW && i > start) break;
                    w += cw;
                    if (t.charAt(i) == ' ') lastSpace = i;
                }
                if (i >= segEnd) {
                    lines.add(new int[]{start, segEnd, next});
                    break;
                }
                int breakAt = (lastSpace >= start) ? lastSpace + 1 : i;
                lines.add(new int[]{start, breakAt, breakAt});
                start = breakAt;
            }
            if (hard < 0) break;
            segStart = hard + 1;
        }
        return lines;
    }

    private int[] caretLineCol(List<int[]> lines, int caret) {
        for (int k = 0; k < lines.size(); k++) {
            if (caret < lines.get(k)[2]) return new int[]{k, lines.get(k)[0]};
        }
        return new int[]{lines.size() - 1, lines.getLast()[0]};
    }

    private Comment commentAt(double worldX, double worldY) {
        Comment best = null;
        for (Comment c : script().comments()) {
            if (withinComment(c, worldX, worldY)) best = c;
        }
        return best;
    }

    private boolean withinComment(Comment c, double worldX, double worldY) {
        measure(c);
        double px = cX(c), py = cY(c);
        return worldX >= px && worldX <= px + c.w && worldY >= py && worldY <= py + c.h;
    }

    private int caretFromWorld(Comment c, double worldX, double worldY) {
        measure(c);
        List<int[]> lines = wrapLines(c);
        int li = (int) Math.floor((worldY - (cY(c) + COMMENT_PAD)) / font.lineHeight);
        li = Math.clamp(li, 0, lines.size() - 1);
        int s = lines.get(li)[0], e = lines.get(li)[1];
        double localX = worldX - (cX(c) + COMMENT_PAD);
        if (localX <= 0) return s;
        for (int i = s + 1; i <= e; i++) {
            double cur = CommentText.width(font, c, s, i);
            if (cur >= localX) {
                double prev = CommentText.width(font, c, s, i - 1);
                return (localX - prev < cur - localX) ? i - 1 : i;
            }
        }
        return e;
    }

    private BlockInstance topBlockAt(double worldX, double worldY) {
        BlockInstance top = null;
        for (BlockInstance b : script().blocks().values()) {
            if (within(layout(b), worldX, worldY)) top = b;
        }
        return top;
    }

    public boolean isEditingComment() { return editing != null; }

    public void startEditingComment(Comment c) {
        menu.open = false;
        dragActive = false;
        dragBlock = null;
        dragGroup.clear();
        dragGroupComments.clear();
        editing = c;
        caret = c.text().length();
        selAnchor = caret;
        activeStyle = 0;
        commentEditDidSnapshot = false;
    }

    public void stopEditingComment() {
        editing = null;
        commentEditDidSnapshot = false;
    }

    public void cancelCommentEdit() {
        editing = null;
        commentEditDidSnapshot = false;
    }

    private void commentEditSnapshot() {
        if (!commentEditDidSnapshot) { host.pushUndo(); commentEditDidSnapshot = true; }
    }

    private void toggleStyle(int flag) {
        if (editing == null) return;
        if (hasSelection()) {
            commentEditSnapshot();
            editing.toggleStyle(selStart(), selEnd(), flag);
        } else {
            activeStyle ^= flag;
        }
    }

    private boolean formatActive(int flag) {
        if (hasSelection()) return editing.rangeHasStyle(selStart(), selEnd(), flag);
        return (activeStyle & flag) != 0;
    }

    private int[] formatBarRect() {
        measure(editing);
        return new int[]{sx(cX(editing)), sy(cY(editing)) - FMT_BTN - 2};
    }

    private void renderFormatBar(GuiGraphics g, double mx, double my) {
        int[] r = formatBarRect();
        int bx = r[0], by = r[1];
        g.fill(bx - 1, by - 1, bx + FMT_BTN * FMT_FLAGS.length + 1, by + FMT_BTN + 1, 0xFF000000);
        for (int i = 0; i < FMT_FLAGS.length; i++) {
            int x = bx + i * FMT_BTN;
            boolean on = formatActive(FMT_FLAGS[i]);
            boolean hover = mx >= x && mx < x + FMT_BTN && my >= by && my < by + FMT_BTN;
            g.fill(x, by, x + FMT_BTN - 1, by + FMT_BTN, on ? 0xFF3A7AE0 : hover ? 0xFF555555 : 0xFF2A2A2A);
            int tw = font.width(FMT_LABELS[i]);
            g.drawString(font, FMT_LABELS[i], x + (FMT_BTN - tw) / 2,
                    by + (FMT_BTN - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
        }
    }

    private boolean formatBarClick(double mx, double my) {
        int[] r = formatBarRect();
        int bx = r[0], by = r[1];
        if (my < by || my >= by + FMT_BTN) return false;
        for (int i = 0; i < FMT_FLAGS.length; i++) {
            int x = bx + i * FMT_BTN;
            if (mx >= x && mx < x + FMT_BTN) { toggleStyle(FMT_FLAGS[i]); return true; }
        }
        return false;
    }

    public boolean commentCharTyped(int codepoint) {
        if (editing == null || codepoint < 32) return false;
        insertText(String.valueOf((char) codepoint), activeStyle);
        return true;
    }

    public boolean commentKeyPressed(int key, boolean ctrl, boolean shift) {
        if (editing == null) return false;
        String t = editing.text();
        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> { selAnchor = 0; caret = t.length(); }
                case GLFW.GLFW_KEY_C -> copySelectionToClipboard();
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) { copySelectionToClipboard(); commentEditSnapshot(); deleteSelection(); }
                }
                case GLFW.GLFW_KEY_V -> pasteClipboard();
                case GLFW.GLFW_KEY_B -> toggleStyle(Comment.BOLD);
                case GLFW.GLFW_KEY_I -> toggleStyle(Comment.ITALIC);
                case GLFW.GLFW_KEY_U -> toggleStyle(Comment.UNDERLINE);
                case GLFW.GLFW_KEY_S -> { if (shift) toggleStyle(Comment.STRIKE); }
                case GLFW.GLFW_KEY_LEFT -> moveCaret(wordStart(t, caret), shift);
                case GLFW.GLFW_KEY_RIGHT -> moveCaret(wordEnd(t, caret), shift);
                case GLFW.GLFW_KEY_HOME -> moveCaret(0, shift);
                case GLFW.GLFW_KEY_END -> moveCaret(t.length(), shift);
                case GLFW.GLFW_KEY_BACKSPACE -> deleteTo(wordStart(t, caret));
                case GLFW.GLFW_KEY_DELETE -> deleteTo(wordEnd(t, caret));
                default -> { }
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> stopEditingComment();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (shift) insertText("\n", activeStyle);
                else stopEditingComment();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> deleteTo(Math.max(0, caret - 1));
            case GLFW.GLFW_KEY_DELETE -> deleteTo(Math.min(t.length(), caret + 1));
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && hasSelection()) moveCaret(selStart(), false);
                else moveCaret(Math.max(0, caret - 1), shift);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && hasSelection()) moveCaret(selEnd(), false);
                else moveCaret(Math.min(t.length(), caret + 1), shift);
            }
            case GLFW.GLFW_KEY_UP -> moveCaretLine(-1, shift);
            case GLFW.GLFW_KEY_DOWN -> moveCaretLine(1, shift);
            case GLFW.GLFW_KEY_HOME -> moveCaret(caretLine()[0], shift);
            case GLFW.GLFW_KEY_END -> moveCaret(caretLine()[1], shift);
            default -> {  }
        }
        return true;
    }

    private static boolean isWordSep(char ch) { return ch == ' ' || ch == '\n'; }

    private static int wordStart(String t, int from) {
        int p = Math.clamp(from, 0, t.length());
        while (p > 0 && isWordSep(t.charAt(p - 1))) p--;
        while (p > 0 && !isWordSep(t.charAt(p - 1))) p--;
        return p;
    }

    private static int wordEnd(String t, int from) {
        int n = t.length();
        int p = Math.clamp(from, 0, n);
        while (p < n && isWordSep(t.charAt(p))) p++;
        while (p < n && !isWordSep(t.charAt(p))) p++;
        return p;
    }

    private void moveCaret(int pos, boolean keepAnchor) {
        caret = Math.clamp(pos, 0, editing.text().length());
        if (!keepAnchor) selAnchor = caret;
    }

    private void deleteTo(int pos) {
        if (hasSelection()) { commentEditSnapshot(); deleteSelection(); return; }
        int p = Math.clamp(pos, 0, editing.text().length());
        if (p == caret) return;
        commentEditSnapshot();
        int s = Math.min(p, caret), e = Math.max(p, caret);
        editing.delete(s, e);
        caret = s;
        selAnchor = s;
    }

    private void insertText(String s, int style) {
        if (s.isEmpty()) return;
        commentEditSnapshot();
        if (hasSelection()) deleteSelection();
        caret = Math.clamp(caret, 0, editing.text().length());
        editing.insert(caret, s, style);
        caret += s.length();
        selAnchor = caret;
    }

    private int[] caretLine() {
        List<int[]> lines = wrapLines(editing);
        int[] lc = caretLineCol(lines, Math.clamp(caret, 0, editing.text().length()));
        int[] line = lines.get(lc[0]);
        return new int[]{line[0], line[1]};
    }

    private void moveCaretLine(int dir, boolean keepAnchor) {
        List<int[]> lines = wrapLines(editing);
        int cc = Math.clamp(caret, 0, editing.text().length());
        int[] lc = caretLineCol(lines, cc);
        int target = lc[0] + dir;
        if (target < 0 || target >= lines.size()) {
            moveCaret(dir < 0 ? 0 : editing.text().length(), keepAnchor);
            return;
        }
        int goal = CommentText.width(font, editing, lc[1], cc);
        int s = lines.get(target)[0], e = lines.get(target)[1];
        int best = e;
        for (int i = s + 1; i <= e; i++) {
            int cur = CommentText.width(font, editing, s, i);
            if (cur >= goal) {
                int prev = CommentText.width(font, editing, s, i - 1);
                best = (goal - prev < cur - goal) ? i - 1 : i;
                break;
            }
        }
        moveCaret(best, keepAnchor);
    }

    private boolean hasSelection() { return selAnchor != caret; }
    private int selStart() { return Math.min(caret, selAnchor); }
    private int selEnd() { return Math.max(caret, selAnchor); }

    private String selectedText() {
        String t = editing.text();
        int s = Math.clamp(selStart(), 0, t.length());
        int e = Math.clamp(selEnd(), 0, t.length());
        return t.substring(s, e);
    }

    private void deleteSelection() {
        int len = editing.text().length();
        int s = Math.clamp(selStart(), 0, len);
        int e = Math.clamp(selEnd(), 0, len);
        editing.delete(s, e);
        caret = s;
        selAnchor = s;
    }

    private void copySelectionToClipboard() {
        if (!hasSelection()) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
    }

    private void pasteClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clip.length(); i++) {
            char ch = clip.charAt(i);
            if (ch == '\r') {
                if (i + 1 < clip.length() && clip.charAt(i + 1) == '\n') continue;
                sb.append('\n');
            } else if (ch == '\n') sb.append('\n');
            else if (ch == '\t') sb.append(' ');
            else if (ch >= 32) sb.append(ch);
        }
        insertText(sb.toString(), 0);
    }

    public double[] scriptWorldBounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (BlockInstance b : script().blocks().values()) {
            BlockRenderer.Layout L = layout(b);
            minX = Math.min(minX, L.x); minY = Math.min(minY, L.y);
            maxX = Math.max(maxX, L.x + L.w); maxY = Math.max(maxY, L.y + L.h);
            any = true;
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
    }

    public double[] selectionWorldBounds() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (int id : host.selection()) {
            BlockInstance b = script().block(id);
            if (b == null) continue;
            BlockRenderer.Layout L = layout(b);
            minX = Math.min(minX, L.x); minY = Math.min(minY, L.y);
            maxX = Math.max(maxX, L.x + L.w); maxY = Math.max(maxY, L.y + L.h);
            any = true;
        }
        return any ? new double[]{minX, minY, maxX, maxY} : null;
    }

    private void pickVarRef(BlockInstance b, String slot, double sx, double sy) {
        int n = EffectiveSlots.variableNames(script()).size();
        if (n == 1) return;
        if (n <= 3) { host.editParam(b, slot); return; }
        openVarPicker(b, slot, sx, sy);
    }

    private void openVarPicker(BlockInstance b, String slot, double sx, double sy) {
        List<String> names = EffectiveSlots.variableNames(script());
        menu.clear();
        if (names.isEmpty()) {
            menu.add("(no variables)", false, () -> {});
        } else {
            for (String n : names) menu.add(n, () -> host.setVarRef(b, slot, n));
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private void pickScript(BlockInstance b, double sx, double sy) {
        ScriptTransfer.requestList();
        List<String> names = host.serverScriptNames();
        menu.clear();
        if (names.isEmpty()) {
            menu.add("(no scripts on server)", false, () -> {});
        } else {
            for (String n : names) menu.add(n, () -> host.setEnumChoice(b, "script", n));
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private void pickDefineName(BlockInstance b, double sx, double sy) {
        List<String> choices = EffectiveSlots.defineNameChoices(b, script());
        menu.clear();
        if (choices.isEmpty()) {
            menu.add("(all functions defined)", false, () -> {});
        } else {
            for (String ch : choices) menu.add(ch, () -> host.setEnumChoice(b, "name", ch));
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private void pickCallName(BlockInstance b, double sx, double sy) {
        List<String> names = EffectiveSlots.functionNames(script());
        menu.clear();
        if (names.isEmpty()) {
            menu.add("(no functions)", false, () -> {});
        } else {
            for (String n : names) menu.add(n, () -> host.setEnumChoice(b, "name", n));
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private void pickEnum(BlockInstance b, String slot, double sx, double sy) {
        List<String> choices = enumChoices(b, slot);
        int n = choices.size();
        if (n <= 1) return;
        if (n <= 3) { host.editParam(b, slot); return; }
        menu.clear();
        for (String ch : choices) menu.add(ch, () -> host.setEnumChoice(b, slot, ch));
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private List<String> enumChoices(BlockInstance b, String slot) {
        for (ParamSlot s : EffectiveSlots.forBlock(b, script())) {
            if (s.name().equals(slot)) return s.enumChoices();
        }
        return List.of();
    }

    private void pickParamType(BlockInstance b, int index, double sx, double sy) {
        menu.clear();
        for (VarType t : VarType.values()) {
            menu.add(t.displayName(), () -> host.setFuncParamType(b, index, t));
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }

    private void openContextMenu(double sx, double sy) {
        double worldX = wx(sx), worldY = wy(sy);
        menu.clear();
        Comment c = commentAt(worldX, worldY);
        if (c != null) {
            menu.add("Edit comment", () -> host.editComment(c));
            menu.add("Delete comment", () -> host.deleteComment(c.id()));
        } else {
            Hit hit = deepHit(worldX, worldY);
            if (hit != null && !host.isSelected(hit.block().id())) host.select(hit.block().id());
            int attachId = hit != null ? hit.block().id() : -1;
            if (!host.selection().isEmpty()) {
                menu.add("Copy", host::copySelection);
                menu.add("Duplicate", host::duplicateSelection);
                menu.add("Add comment", () -> host.addComment(worldX, worldY, attachId));
                menu.add("Delete", host::deleteSelected);
            } else {
                menu.add("Paste", host.hasClipboard(), () -> host.pasteAt(worldX, worldY));
                menu.add("Add comment", () -> host.addComment(worldX, worldY, -1));
                menu.add("Convert command", () -> host.convertCommandAt(worldX, worldY));
                menu.add("Doom", () -> stars.spawnBlackHole(left, top, right, bottom));
            }
        }
        menu.openAt(sx, sy, left, top, right, bottom);
    }
}
