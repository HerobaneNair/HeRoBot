package hero.bane.herobot.client.screen.ai;

import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.block.*;
import net.minecraft.client.gui.Font;

import java.util.*;

public final class BlockSorter {
    public static final double SPREAD = 20;

    private static final double MARGIN = 20;
    private static final double V_GAP = 10;
    private static final double INDENT = 28;
    private static final double COL_GAP = 48;
    private static final double H_GAP = 24;
    private static final double SIDE_GAP = 60;
    private static final double DEFAULT_END_GAP = 80;
    private static final int BODY_PORT = 0;
    private static final int SIDE_PORT = 1;

    private final AiScript script;
    private final Font font;
    private final double spread;

    private BlockSorter(AiScript script, Font font, double spread) {
        this.script = script;
        this.font = font;
        this.spread = spread;
    }

    public static void tidy(AiScript script, Font font) {
        tidy(script, font, 0);
    }

    /**
     * Same layout as {@link #tidy(AiScript, Font)}, but every connection is opened up by
     * {@code spread} extra pixels so wires are visible instead of ports sitting flush.
     */
    public static void tidy(AiScript script, Font font, double spread) {
        new BlockSorter(script, font, spread).run();
    }

    private double vGap() { return V_GAP + spread; }
    private double portGap() { return BlockRenderer.PORT_H + 2 + spread; }
    private double endGap() { return DEFAULT_END_GAP + spread; }

    private void run() {
        if (script.blocks().isEmpty()) return;

        Set<Integer> hasIncoming = new HashSet<>();
        Set<Integer> hasOutgoing = new HashSet<>();
        for (Wire w : script.wires()) {
            hasIncoming.add(w.toBlockId());
            hasOutgoing.add(w.fromBlockId());
        }

        List<BlockInstance> roots = new ArrayList<>();
        for (BlockInstance b : script.blocks().values()) {
            if (!hasIncoming.contains(b.id()) && hasOutgoing.contains(b.id())) roots.add(b);
        }

        roots.sort(Comparator
                .comparingInt((BlockInstance b) -> isHat(b) ? 0 : 1)
                .thenComparingDouble(this::centerY)
                .thenComparingDouble(this::centerX));

        Set<Integer> visited = new HashSet<>();
        double colX = MARGIN;
        for (BlockInstance root : roots) {
            Set<Integer> before = new HashSet<>(visited);
            place(root, colX, MARGIN, visited);

            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            for (int id : visited) {
                if (before.contains(id)) continue;
                BlockInstance b = script.block(id);
                minX = Math.min(minX, b.x());
                maxX = Math.max(maxX, b.x() + width(b));
            }
            if (minX == Double.POSITIVE_INFINITY) continue;
            double dx = colX - minX;
            if (dx != 0) {
                for (int id : visited) {
                    if (before.contains(id)) continue;
                    BlockInstance b = script.block(id);
                    b.setPos(b.x() + dx, b.y());
                }
                maxX += dx;
            }
            colX = maxX + COL_GAP;
        }

        for (BlockInstance b : script.blocks().values()) {
            if (b.type() != BlockType.BLOCK_END || visited.contains(b.id())) continue;
            BlockInstance start = pairedStart(b);
            if (start == null || !visited.contains(start.id())) continue;
            place(b, start.x(), start.y() + height(start) + vGap(), visited);
        }

        double oy = MARGIN;
        for (BlockInstance b : script.blocks().values()) {
            if (visited.contains(b.id())) continue;
            if (b.type() == BlockType.BLOCK_END && pairedStart(b) != null) continue;
            b.setPos(colX, oy);
            visited.add(b.id());
            oy += height(b) + vGap();
            if (AiEditorScreen.isContainer(b.type()) && b.pairedId() >= 0) {
                BlockInstance end = script.block(b.pairedId());
                if (end != null && !visited.contains(end.id())) {
                    place(end, colX, b.y() + endGap(), visited);
                    oy = Math.max(oy, end.y() + height(end) + vGap());
                }
            }
        }

        fixContainerEnds();
    }

    private void fixContainerEnds() {
        List<BlockInstance> containers = new ArrayList<>();
        for (BlockInstance b : script.blocks().values()) {
            if (AiEditorScreen.isContainer(b.type()) && b.pairedId() >= 0
                    && script.block(b.pairedId()) != null) containers.add(b);
        }
        Map<Integer, Set<Integer>> bodies = new HashMap<>();
        for (BlockInstance c : containers) {
            bodies.put(c.id(), bodyBlocks(c, script.block(c.pairedId())));
        }
        containers.sort(Comparator.comparingInt((BlockInstance c) -> {
            int depth = 0;
            for (Set<Integer> body : bodies.values()) if (body.contains(c.id())) depth++;
            return -depth;
        }));
        for (BlockInstance c : containers) {
            BlockInstance end = script.block(c.pairedId());
            double lowest = c.y() + height(c);
            for (int id : bodies.get(c.id())) {
                BlockInstance b = script.block(id);
                if (b != null) lowest = Math.max(lowest, b.y() + height(b));
            }
            double required = Math.max(lowest + portGap(), c.y() + endGap());
            double delta = required - end.y();
            if (delta > 0) shiftDown(end, delta);
        }
    }

    private Set<Integer> bodyBlocks(BlockInstance c, BlockInstance end) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        for (Wire w : script.outgoing(c.id(), 0)) stack.push(w.toBlockId());
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (id == end.id() || id == c.id() || !seen.add(id)) continue;
            for (Wire w : script.wires()) {
                if (w.fromBlockId() == id) stack.push(w.toBlockId());
            }
        }
        return seen;
    }

    private void shiftDown(BlockInstance end, double delta) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(end.id());
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (!seen.add(id)) continue;
            BlockInstance b = script.block(id);
            if (b != null) b.setPos(b.x(), b.y() + delta);
            for (Wire w : script.wires()) {
                if (w.fromBlockId() == id) stack.push(w.toBlockId());
            }
        }
    }

    private double place(BlockInstance block, double x, double y, Set<Integer> visited) {
        if (visited.contains(block.id())) return y;
        if (sideChild(block, visited) != null) return placeChain(block, x, y, visited);
        visited.add(block.id());
        block.setPos(x, y);

        BlockRenderer.Layout L = layout(block);

        BlockDef def = BlockDefRegistry.get(block.type());
        int ports = def.outPorts();
        boolean cShape = def.shape() == BlockShape.C_SHAPE;
        int afterPort = (cShape && ports >= 2) ? ports - 1 : -1;

        List<BlockInstance> rowChildren = new ArrayList<>();
        List<BlockInstance> stmtChildren = new ArrayList<>();
        List<int[]> stmtPorts = new ArrayList<>();
        List<BlockInstance> afterChildren = new ArrayList<>();
        for (int port = 0; port < ports; port++) {
            int[] outPort = port < L.outPorts.size() ? L.outPorts.get(port) : null;
            boolean bodyPort = cShape && port != afterPort;
            for (Wire wire : script.outgoing(block.id(), port)) {
                BlockInstance child = script.block(wire.toBlockId());
                if (child == null || visited.contains(child.id())) continue;
                if (bodyPort) rowChildren.add(child);
                else if (cShape) afterChildren.add(child);
                else { stmtChildren.add(child); stmtPorts.add(outPort); }
            }
        }

        double bottom = y + L.h;

        BlockInstance spineChild = null;
        int[] spinePort = null;
        if (stmtChildren.size() == 1 && stmtPorts.getFirst() != null) {
            spineChild = stmtChildren.getFirst();
            spinePort = stmtPorts.getFirst();
        } else {
            rowChildren.addAll(stmtChildren);
        }

        if (!rowChildren.isEmpty()) {
            double rowY = bottom + vGap();
            double cursorX = x + INDENT;
            for (BlockInstance child : rowChildren) {
                double[] box = placeRow(child, cursorX, rowY, visited);
                cursorX = box[0] + H_GAP;
                bottom = Math.max(bottom, box[1]);
            }
        }

        if (spineChild != null) {
            bottom = Math.max(bottom, slot(spineChild, spinePort, visited));
        }

        double ay = bottom + vGap();
        for (BlockInstance child : afterChildren) {
            double cb = place(child, x, ay, visited);
            ay = cb + vGap();
            bottom = Math.max(bottom, cb);
        }

        return bottom;
    }

    private BlockInstance sideChild(BlockInstance b, Set<Integer> visited) {
        if (b.type() != BlockType.IF && b.type() != BlockType.ELSE_IF) return null;
        for (Wire w : script.outgoing(b.id(), SIDE_PORT)) {
            BlockInstance c = script.block(w.toBlockId());
            if (c != null && !visited.contains(c.id())) return c;
        }
        return null;
    }

    /**
     * Lays an if / else-if / else chain out as parallel columns: every header sits on the rail
     * formed by the previous header's side port, and every branch body starts at one shared Y so
     * the branches run down beside each other instead of stepping right and down as the chain grows.
     */
    private double placeChain(BlockInstance head, double x, double y, Set<Integer> visited) {
        List<BlockInstance> members = new ArrayList<>();
        BlockInstance m = head;
        double my = y;
        double headerBottom = y;
        double bodyTop = Double.NEGATIVE_INFINITY;

        while (m != null && visited.add(m.id())) {
            m.setPos(x, my);
            members.add(m);
            BlockRenderer.Layout ml = layout(m);
            headerBottom = Math.max(headerBottom, my + ml.h);
            int[] bp = bodyPort(ml);
            if (bp != null) bodyTop = Math.max(bodyTop, bp[1] + portGap());

            BlockInstance next = sideChild(m, visited);
            if (next == null) break;
            int[] sp = sidePort(ml);
            if (sp == null) break;
            next.setPos(x, my);
            BlockRenderer.Layout nl = layout(next);
            my = sp[1] + spread - (nl.inY - nl.y);
            m = next;
        }

        double bodyY = Math.max(headerBottom + vGap(), bodyTop);
        double bottom = headerBottom;
        double colX = x;

        for (int i = 0; i < members.size(); i++) {
            BlockInstance mem = members.get(i);
            if (i > 0) mem.setPos(colX, mem.y());

            Set<Integer> before = new HashSet<>(visited);
            BlockRenderer.Layout ml = layout(mem);
            int[] bp = bodyPort(ml);
            List<BlockInstance> bodies = new ArrayList<>();
            for (Wire w : script.outgoing(mem.id(), BODY_PORT)) {
                BlockInstance c = script.block(w.toBlockId());
                if (c != null && !visited.contains(c.id())) bodies.add(c);
            }

            if (bp != null && bodies.size() == 1) {
                bottom = Math.max(bottom, slotAt(bodies.getFirst(), bp, bodyY, visited));
            } else {
                double cursorX = mem.x() + INDENT;
                for (BlockInstance c : bodies) {
                    double[] box = placeRow(c, cursorX, bodyY, visited);
                    cursorX = box[0] + H_GAP;
                    bottom = Math.max(bottom, box[1]);
                }
            }

            double colLeft = mem.x(), colRight = mem.x() + ml.w;
            for (int id : visited) {
                if (before.contains(id)) continue;
                BlockInstance b = script.block(id);
                if (b == null) continue;
                colLeft = Math.min(colLeft, b.x());
                colRight = Math.max(colRight, b.x() + width(b));
                bottom = Math.max(bottom, b.y() + height(b));
            }

            double dx = i > 0 ? colX - colLeft : 0;
            if (dx > 0) {
                mem.setPos(mem.x() + dx, mem.y());
                for (int id : visited) {
                    if (before.contains(id)) continue;
                    BlockInstance b = script.block(id);
                    if (b != null) b.setPos(b.x() + dx, b.y());
                }
                colRight += dx;
            }
            colX = colRight + SIDE_GAP;
        }
        return bottom;
    }

    private double slotAt(BlockInstance child, int[] outPort, double childY, Set<Integer> visited) {
        BlockRenderer.Layout cl = layout(child);
        double childX = outPort[0] - (cl.inX - cl.x);
        if (child.type() == BlockType.BLOCK_END) {
            BlockInstance start = pairedStart(child);
            if (start != null && visited.contains(start.id())) childX = start.x();
        }
        return place(child, childX, childY, visited);
    }

    private static int[] sidePort(BlockRenderer.Layout L) {
        return L.sideOutPort >= 0 && L.sideOutPort < L.outPorts.size() ? L.outPorts.get(L.sideOutPort) : null;
    }

    private static int[] bodyPort(BlockRenderer.Layout L) {
        return !L.outPorts.isEmpty() ? L.outPorts.get(BODY_PORT) : null;
    }

    private double slot(BlockInstance child, int[] outPort, Set<Integer> visited) {
        return slotAt(child, outPort, outPort[1] + portGap(), visited);
    }

    private BlockInstance pairedStart(BlockInstance end) {
        return end.pairedId() >= 0 ? script.block(end.pairedId()) : null;
    }

    private double[] placeRow(BlockInstance child, double leftX, double y, Set<Integer> visited) {
        Set<Integer> before = new HashSet<>(visited);
        place(child, leftX, y, visited);

        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int id : visited) {
            if (before.contains(id)) continue;
            BlockInstance b = script.block(id);
            BlockRenderer.Layout L = layout(b);
            minX = Math.min(minX, b.x());
            maxX = Math.max(maxX, b.x() + L.w);
            maxY = Math.max(maxY, b.y() + L.h);
        }
        if (minX == Double.POSITIVE_INFINITY) return new double[]{leftX, y};

        double dx = leftX - minX;
        if (dx != 0) {
            for (int id : visited) {
                if (before.contains(id)) continue;
                BlockInstance b = script.block(id);
                b.setPos(b.x() + dx, b.y());
            }
            maxX += dx;
        }
        return new double[]{maxX, maxY};
    }

    private BlockRenderer.Layout layout(BlockInstance b) {
        return BlockRenderer.layout(BlockDefRegistry.get(b.type()), b, font, script);
    }

    private double width(BlockInstance b) { return layout(b).w; }
    private double height(BlockInstance b) { return layout(b).h; }
    private double centerX(BlockInstance b) { return b.x() + width(b) / 2; }
    private double centerY(BlockInstance b) { return b.y() + height(b) / 2; }

    private static boolean isHat(BlockInstance b) {
        return BlockDefRegistry.get(b.type()).shape() == BlockShape.HAT;
    }
}
