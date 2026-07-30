package hero.bane.herobot.mod.client.screen.ai;

import hero.bane.herobot.mod.common.ai.AiScript;
import hero.bane.herobot.mod.common.ai.VarType;
import hero.bane.herobot.mod.common.ai.block.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class BlockRenderer {
    public static final int HEADER_H = 20;
    public static final int PAD = 6;
    public static final int MIN_W = 20;
    public static final int CHIP_H = 12;
    public static final int PORT_W = 10;
    public static final int PORT_H = 4;
    public static final int PORT_NOTCH = 3;

    public static final int SPINE_W = 12;
    public static final int MOUTH_H = 26;
    public static final int ARM_H = 12;

    public static final String CYCLE_GLYPH = "§l⟳";
    // ↺ ↻ ⟲ ⟳ 🔁 🔃 🔄 🗘

    private BlockRenderer() {}

    public record ChipRect(String name, int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public record Nested(String slot, BlockInstance block, Layout layout) {}

    public record Word(String text, int x) {}

    public record ParamRow(int index, String typeLabel, String chipLabel,
                           int[] typeRect, int[] dragRect) {
        public static boolean hits(int[] r, double px, double py) {
            return px >= r[0] && px <= r[0] + r[2] && py >= r[1] && py <= r[1] + r[3];
        }
    }

    public static final int PARAM_BLOCK_H = CHIP_H * 2 + 12;

    public static String iterTag(int iterId) {
        return iterId <= 0 ? "i?" : "i" + iterId;
    }

    public static final class Layout {
        public int x, y, w, h;
        public boolean hasInput;
        public boolean sideInput;
        public int sideOutPort = -1;
        public int[] plus;
        public int[] expander;
        public boolean expanderMinus;
        public int[] iterChip;
        public int[] msgChip;
        public int[] cycleButton;
        public int iterId;
        public String suffix;
        public int suffixX;
        public String infix;
        public int infixX;
        public int inX, inY;
        public int inHalfW = PORT_W / 2;
        public int labelX, labelY;
        public final List<int[]> outPorts = new ArrayList<>();
        public final List<ChipRect> chips = new ArrayList<>();
        public final List<Nested> nested = new ArrayList<>();
        public final List<Word> words = new ArrayList<>();
        public final List<ParamRow> paramRows = new ArrayList<>();

        public boolean cShape;
        public int spineW;
        public final List<int[]> arms = new ArrayList<>();
    }

    public static String chipText(Object value) {
        return switch (value) {
            case null -> "";
            case Boolean b -> b.toString();
            case Double d -> (d == Math.floor(d) && !Double.isInfinite(d))
                    ? String.valueOf(d.longValue()) : d.toString();
            default -> value.toString();
        };
    }

    public static Layout layout(BlockDef def, BlockInstance inst, Font font, AiScript script) {
        return layoutAt(def, inst, font, (int) Math.round(inst.x()), (int) Math.round(inst.y()), script);
    }

    private static Layout layoutAt(BlockDef def, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        Layout L = new Layout();
        L.x = ox;
        L.y = oy;
        L.hasInput = def.hasInput();

        int maxChildH = 0;
        List<ParamSlot> slots = visibleSlots(inst, script);
        Layout[] childLayouts = new Layout[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            BlockInstance rep = inst.getReporter(slots.get(i).name());
            if (rep != null) {
                Layout cl = layoutAt(BlockDefRegistry.get(rep.type()), rep, font, 0, 0, script);
                childLayouts[i] = cl;
                maxChildH = Math.max(maxChildH, cl.h);
            }
        }
        L.h = Math.max(HEADER_H, maxChildH + 6);
        if (def.type() == BlockType.FUNC_DEFINE && funcNumArgs(inst, script) > 0) {
            L.h = Math.max(L.h, PARAM_BLOCK_H);
        }

        int chipY = oy + (L.h - CHIP_H) / 2;
        int midY = oy + L.h / 2;
        L.labelY = oy + (L.h - 8) / 2;

        int cx;
        int contentRight;
        boolean infix = isInfix(def.type()) && slots.size() >= 2;
        if (infix) {
            cx = ox + PAD;
            cx = placeSlot(L, inst, slots.getFirst(), childLayouts[0], cx, chipY, midY, font);
            L.labelX = cx;
            cx += font.width(blockLabel(def, inst)) + 6;
            cx = placeSlot(L, inst, slots.get(1), childLayouts[1], cx, chipY, midY, font);
            contentRight = cx - 4;
            for (int i = 2; i < slots.size(); i++) {
                String prefix = slotPrefix(def.type(), slots.get(i).name());
                if (prefix != null) {
                    L.words.add(new Word(prefix, cx));
                    cx += font.width(prefix) + 4;
                }
                cx = placeSlot(L, inst, slots.get(i), childLayouts[i], cx, chipY, midY, font);
                contentRight = cx - 4;
            }
        } else {
            L.labelX = ox + PAD;
            contentRight = ox + PAD + font.width(blockLabel(def, inst));
            cx = contentRight + PAD;
            String infixWord = varInfixWord(def.type());
            for (int i = 0; i < slots.size(); i++) {
                String slotName = slots.get(i).name();
                if (EffectiveSlots.isLookBlock(def.type())
                        && (slotName.equals("yawOffset") || slotName.equals("ticks"))) cx += 5;
                if (infixWord != null && i == 1) {
                    L.infix = infixWord;
                    L.infixX = cx;
                    cx += font.width(infixWord) + PAD;
                }
                String prefix = slotPrefix(def.type(), slotName);
                if (prefix != null) {
                    L.words.add(new Word(prefix, cx));
                    cx += font.width(prefix) + 4;
                }
                cx = placeSlot(L, inst, slots.get(i), childLayouts[i], cx, chipY, midY, font);
                contentRight = cx - 4;
            }
        }
        BlockDecorators.SuffixDecorator suffixDecorator = BlockDecorators.suffixFor(def.type());
        if (suffixDecorator != null) {
            contentRight = suffixDecorator.apply(L, cx, contentRight, font, inst, script);
        }
        L.w = Math.max(MIN_W, (contentRight - ox) + PAD);
        if (def.type() == BlockType.STOP_SCRIPT) {
            L.w = font.width(blockLabel(def)) + PAD * 2;
        }

        BlockDecorators.ButtonDecorator buttonDecorator = BlockDecorators.buttonsFor(def.type());
        if (buttonDecorator != null) {
            buttonDecorator.apply(L, inst, font, ox, oy, script);
        }

        L.inX = ox + L.w / 2;
        L.inY = oy;
        int n = def.outPorts();

        if (def.shape() == BlockShape.C_END) {
            L.inHalfW = PORT_W;
            boolean elifEnd = false;
            if (script != null && inst.pairedId() >= 0) {
                BlockInstance start = script.block(inst.pairedId());
                if (start != null) {
                    elifEnd = start.type() == BlockType.ELSE_IF;
                    L.w = layoutAt(BlockDefRegistry.get(start.type()), start, font, 0, 0, script).w;
                    L.inX = ox + L.w / 2;
                }
            }
            if (!elifEnd) L.outPorts.add(new int[]{ox + L.w / 2, oy + L.h});
            return L;
        }

        if (def.shape() == BlockShape.C_SHAPE) {
            int armTopH = L.h;
            L.cShape = true;
            L.spineW = SPINE_W;
            boolean hasAfter = n >= 2;
            int bodyPorts = hasAfter ? n - 1 : n;

            int bodyPortX = ox + SPINE_W + 12;
            int afterPortX = ox + PAD + PORT_W / 2;

            L.arms.add(new int[]{oy, oy + armTopH});
            int y = oy + armTopH;
            for (int i = 0; i < bodyPorts; i++) {
                L.outPorts.add(new int[]{bodyPortX, y});
                y += MOUTH_H;
                if (i < bodyPorts - 1) {
                    L.arms.add(new int[]{y, y + ARM_H});
                    y += ARM_H;
                }
            }
            L.arms.add(new int[]{y, y + ARM_H});
            if (hasAfter) L.outPorts.add(new int[]{afterPortX, y + ARM_H});
            y += ARM_H;
            L.h = y - oy;
        } else if (def.type() == BlockType.IF || def.type() == BlockType.ELSE_IF) {
            boolean sideWired = false;
            if (script != null) {
                for (Wire w : script.wires()) {
                    if (w.fromBlockId() == inst.id() && w.outPort() == 1) { sideWired = true; break; }
                }
            }
            if (!sideWired) {
                L.w += 13;
                L.inX = ox + L.w / 2;
                L.plus = new int[]{ox + L.w - 12, oy + 3, 9, 9};
            }
            L.outPorts.add(new int[]{ox + L.w / 2, oy + L.h});
            L.outPorts.add(new int[]{ox + L.w, oy + L.h / 2});
            L.sideOutPort = 1;
            if (def.type() == BlockType.ELSE_IF) {
                L.sideInput = true;
                L.inX = ox;
                L.inY = oy + L.h / 2;
            }
        } else {
            for (int i = 0; i < n; i++) {
                int px = ox + (int) ((long) L.w * (i + 1) / (n + 1));
                L.outPorts.add(new int[]{px, oy + L.h});
            }
        }
        return L;
    }

    private static int funcNumArgs(BlockInstance inst, AiScript script) {
        if (script == null) return 0;
        hero.bane.herobot.mod.common.ai.FuncDecl decl = script.function(EffectiveSlots.funcName(inst));
        return decl == null ? 0 : decl.numParams();
    }

    static void layoutParamColumns(Layout L, BlockInstance inst, Font font, int ox, int oy, AiScript script) {
        String fname = EffectiveSlots.funcName(inst);
        hero.bane.herobot.mod.common.ai.FuncDecl decl = script == null ? null : script.function(fname);
        int numArgs = decl == null ? 0 : decl.numParams();
        if (numArgs > 0) {
            int topY = oy + (L.h - (CHIP_H * 2 + 2)) / 2;
            int botY = topY + CHIP_H + 2;
            int cx = ox + L.w;
            for (int i = 0; i < numArgs; i++) {
                VarType pt = decl.paramType(i);
                if (pt == null) continue;
                String tlabel = pt.chipLabel();
                String chip = (fname.isEmpty() ? "?" : fname) + ":" + (i + 1);
                int tw = font.width(tlabel) + 8;
                int dw = font.width(chip) + 8;
                L.paramRows.add(new ParamRow(i, tlabel, chip,
                        new int[]{cx, topY, tw, CHIP_H}, new int[]{cx, botY, dw, CHIP_H}));
                cx += Math.max(tw, dw) + 6;
            }
            L.w = (cx - ox) + PAD - 6;
        }
        L.w += numArgs > 0 ? 26 : 13;
        L.plus = new int[]{ox + L.w - 12, oy + 3, 9, 9};
        if (numArgs > 0) {
            L.expander = new int[]{ox + L.w - 24, oy + 3, 9, 9};
            L.expanderMinus = true;
        }
    }

    private static int placeSlot(Layout L, BlockInstance inst, ParamSlot slot, Layout cl,
                                 int cx, int chipY, int midY, Font font) {
        if (cl != null) {
            translate(cl, cx - cl.x, (midY - cl.h / 2) - cl.y);
            L.nested.add(new Nested(slot.name(), inst.getReporter(slot.name()), cl));
            return cx + cl.w + 4;
        }
        Object v = inst.getParam(slot.name());
        if (v == null) v = slot.defaultValue();
        int cw = Math.max(14, font.width(chipText(v)) + 8);
        L.chips.add(new ChipRect(slot.name(), cx, chipY, cw, CHIP_H));
        return cx + cw + 4;
    }

    private static final Set<BlockType> INFIX = EnumSet.of(
            BlockType.AND, BlockType.OR, BlockType.CONTAINS);

    private static boolean isInfix(BlockType type) {
        return INFIX.contains(type);
    }

    private static String varInfixWord(BlockType type) {
        if (type == BlockType.SET_VAR) return "to";
        if (type == BlockType.CHANGE_VAR) return "by";
        return null;
    }

    private static String slotPrefix(BlockType type, String slotName) {
        if (type == BlockType.TERNARY) {
            if (slotName.equals("trueValue")) return "then";
            if (slotName.equals("falseValue")) return "else";
        }
        if (type == BlockType.DISTANCE_TO && slotName.equals("toShape")) return "to";
        if (type == BlockType.SEND_MESSAGE && slotName.equals("op")) return "op";
        if ((type == BlockType.PLACE_BLOCK || type == BlockType.BREAK_BLOCK) && slotName.equals("force")) return "force";
        if (type == BlockType.CONTAINS && slotName.equals("checkCase")) return "case";
        return null;
    }

    public static String blockLabel(BlockDef def) {
        return def.type() == BlockType.ELSE_IF ? "else (if" : def.label();
    }

    public static String blockLabel(BlockDef def, BlockInstance inst) {
        if (def.type() == BlockType.FUNC_PARAM && inst != null) {
            Object f = inst.getParam("func");
            Object i = inst.getParam("index");
            String fn = f == null || f.toString().isEmpty() ? "?" : f.toString();
            return fn + ":" + (i instanceof Number n ? n.intValue() + 1 : 1);
        }
        return blockLabel(def);
    }

    static String tickWord(BlockInstance inst) {
        Object v = inst.getParam("ticks");
        return (v instanceof Number n && n.intValue() == 1) ? "tick" : "ticks";
    }

    static String loopWord(BlockInstance inst) {
        Object v = inst.getParam("count");
        return (v instanceof Number n && n.intValue() == 1) ? "loop" : "loops";
    }

    private static void translate(Layout L, int dx, int dy) {
        L.x += dx; L.y += dy; L.inX += dx; L.inY += dy;
        L.labelX += dx; L.labelY += dy;
        L.suffixX += dx;
        L.infixX += dx;
        if (L.plus != null) { L.plus[0] += dx; L.plus[1] += dy; }
        if (L.expander != null) { L.expander[0] += dx; L.expander[1] += dy; }
        if (L.iterChip != null) { L.iterChip[0] += dx; L.iterChip[1] += dy; }
        if (L.msgChip != null) { L.msgChip[0] += dx; L.msgChip[1] += dy; }
        if (L.cycleButton != null) { L.cycleButton[0] += dx; L.cycleButton[1] += dy; }
        for (int i = 0; i < L.words.size(); i++) {
            Word w = L.words.get(i);
            L.words.set(i, new Word(w.text(), w.x() + dx));
        }
        for (int[] p : L.outPorts) { p[0] += dx; p[1] += dy; }
        for (int[] a : L.arms) { a[0] += dy; a[1] += dy; }
        for (int i = 0; i < L.chips.size(); i++) {
            ChipRect c = L.chips.get(i);
            L.chips.set(i, new ChipRect(c.name(), c.x() + dx, c.y() + dy, c.w(), c.h()));
        }
        for (ParamRow p : L.paramRows) {
            for (int[] r : new int[][]{p.typeRect(), p.dragRect()}) { r[0] += dx; r[1] += dy; }
        }
        for (Nested ns : L.nested) translate(ns.layout(), dx, dy);
    }

    public static final int INPUT_NONE = 0;
    public static final int INPUT_OK = 1;
    public static final int INPUT_BAD = 2;

    public static void draw(GuiGraphics g, Font font, BlockDef def, BlockInstance inst,
                            Layout L, int selectedId, int hoveredId, int hoverPort, int litPort,
                            int targetPort, boolean targetOk, int inputHighlight, boolean conflict, AiScript script) {
        int base = def.category().color();
        int body = darken(base, 0.78f);
        boolean reporter = def.shape() == BlockShape.REPORTER || def.shape() == BlockShape.BOOLEAN;
        boolean selected = inst.id() == selectedId;
        boolean hovered = inst.id() == hoveredId;

        int border = selected ? 0xFFFFFFFF : hovered ? 0xFFB0B0C0 : 0xFF202020;
        if (L.cShape) {
            drawCShape(g, L, body, base, border);
        } else {
            int topH = reporter ? Math.max(9, L.h / 2) : 9;
            g.fill(L.x - 1, L.y - 1, L.x + L.w + 1, L.y + L.h + 1, border);
            g.fill(L.x, L.y, L.x + L.w, L.y + L.h, body);
            g.fill(L.x, L.y, L.x + L.w, L.y + topH, base);
        }

        if (conflict) {
            int red = 0xFFFF3030;
            g.fill(L.x - 2, L.y - 2, L.x + L.w + 2, L.y - 1, red);
            g.fill(L.x - 2, L.y + L.h + 1, L.x + L.w + 2, L.y + L.h + 2, red);
            g.fill(L.x - 2, L.y - 1, L.x - 1, L.y + L.h + 1, red);
            g.fill(L.x + L.w + 1, L.y - 1, L.x + L.w + 2, L.y + L.h + 1, red);
        }

        if (L.hasInput) {
            int inCol = inputHighlight == INPUT_OK ? 0xFFFFFFFF
                    : inputHighlight == INPUT_BAD ? 0xFFFF3030
                    : lighten(base, 0.1f);
            if (L.sideInput) {
                drawSideInputPort(g, L.inX, L.inY, L.inHalfW, inCol);
            } else {
                drawInputPort(g, L.inX, L.y, L.inHalfW, inCol);
            }
        }
        for (int i = 0; i < L.outPorts.size(); i++) {
            int[] p = L.outPorts.get(i);
            boolean lit = i == litPort;
            boolean target = i == targetPort;
            boolean hover = i == hoverPort;
            int pcol = target ? (targetOk ? 0xFFFFFFFF : 0xFFFF3030)
                    : hover ? 0xFFFFFFFF
                    : lit ? lighten(base, 0.55f) : darken(base, 0.6f);

            if (i == L.sideOutPort) {
                int x0 = p[0], y0 = p[1] - PORT_W / 2, x1 = p[0] + PORT_H, y1 = p[1] + PORT_W / 2;
                int pgy0 = y0 + PORT_NOTCH, pgy1 = y1 - PORT_NOTCH;
                g.fill(x0, y0, x0 + PORT_H / 2, y1, pcol);
                g.fill(x0 + PORT_H / 2, pgy0, x1, pgy1, pcol);
                continue;
            }

            int x0 = p[0] - PORT_W / 2, y0 = p[1], x1 = p[0] + PORT_W / 2, y1 = p[1] + PORT_H;
            int pgx0 = x0 + PORT_NOTCH, pgx1 = x1 - PORT_NOTCH;
            g.fill(x0, y0, x1, y0 + PORT_H / 2, pcol);
            g.fill(pgx0, y0 + PORT_H / 2, pgx1, y1, pcol);
        }

        int labelColor = def.type() == BlockType.ELSE_IF ? 0xFFFFFFBB : 0xFFFFFFFF;
        if (def.type() == BlockType.ELSE_IF) {
            g.drawString(font, "else", L.labelX, L.labelY, 0xFFFFFFFF, false);
            int restX = L.labelX + font.width("else");
            g.drawString(font, blockLabel(def, inst).substring("else".length()), restX, L.labelY, labelColor, false);
        } else {
            g.drawString(font, blockLabel(def, inst), L.labelX, L.labelY, labelColor, false);
        }
        if (L.suffix != null) {
            g.drawString(font, L.suffix, L.suffixX, L.labelY, labelColor, false);
        }
        if (L.infix != null) {
            g.drawString(font, L.infix, L.infixX, L.labelY, labelColor, false);
        }
        for (Word wd : L.words) {
            g.drawString(font, wd.text(), wd.x(), L.labelY, labelColor, false);
        }
        if (L.plus != null) {
            int px = L.plus[0], py = L.plus[1], pw = L.plus[2], ph = L.plus[3];
            g.fill(px, py, px + pw, py + ph, darken(base, 0.45f));
            g.fill(px + 2, py + ph / 2, px + pw - 2, py + ph / 2 + 1, 0xFFFFFFFF);
            g.fill(px + pw / 2, py + 2, px + pw / 2 + 1, py + ph - 2, 0xFFFFFFFF);
        }
        if (L.expander != null) {
            int px = L.expander[0], py = L.expander[1], pw = L.expander[2], ph = L.expander[3];
            g.fill(px, py, px + pw, py + ph, darken(base, 0.45f));
            g.fill(px + 2, py + ph / 2, px + pw - 2, py + ph / 2 + 1, 0xFFFFFFFF);
            if (!L.expanderMinus) {
                g.fill(px + pw / 2, py + 2, px + pw / 2 + 1, py + ph - 2, 0xFFFFFFFF);
            }
        }
        if (L.iterChip != null) {
            int px = L.iterChip[0], py = L.iterChip[1], pw = L.iterChip[2], ph = L.iterChip[3];
            g.fill(px, py, px + pw, py + ph, darken(base, 0.3f));
            g.drawString(font, iterTag(L.iterId), px + 2, py + 1, labelColor, false);
        }
        if (L.msgChip != null) {
            int px = L.msgChip[0], py = L.msgChip[1], pw = L.msgChip[2], ph = L.msgChip[3];
            g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, darken(base, 0.15f));
            g.fill(px, py, px + pw, py + ph, base);
            g.drawString(font, "message", px + 4, py + 2, labelColor, false);
        }
        if (L.cycleButton != null) {
            int px = L.cycleButton[0], py = L.cycleButton[1], pw = L.cycleButton[2], ph = L.cycleButton[3];
            g.fill(px, py, px + pw, py + ph, darken(base, 0.45f));
            float gw = font.width(CYCLE_GLYPH), gh = font.lineHeight;
            float s = Math.min((pw - 2f) / gw, (ph - 2f) / gh);
            g.pose().pushMatrix();
            g.pose().translate(px + (pw - gw * s) / 2f + 1f, py + (ph - gh * s) / 2f);
            g.pose().scale(s, s);
            g.drawString(font, CYCLE_GLYPH, 0, 0, labelColor, false);
            g.pose().popMatrix();
        }

        List<ParamSlot> effective = EffectiveSlots.forBlock(inst, script);
        for (ChipRect c : L.chips) {
            ParamSlot slot = slotFor(effective, c.name());
            Object v = inst.getParam(c.name());
            if (v == null && slot != null) v = slot.defaultValue();

            g.fill(c.x() - 1, c.y() - 1, c.x() + c.w() + 1, c.y() + c.h() + 1, darken(base, 0.3f));
            g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), darken(base, 0.45f));
            g.drawString(font, chipText(v), c.x() + 4, c.y() + 2, 0xFFFFFFFF, false);
        }

        for (ParamRow p : L.paramRows) {
            int[] t = p.typeRect();
            g.fill(t[0] - 1, t[1] - 1, t[0] + t[2] + 1, t[1] + t[3] + 1, darken(base, 0.3f));
            g.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], darken(base, 0.45f));
            g.drawString(font, p.typeLabel(), t[0] + 4, t[1] + 2, 0xFFFFFFFF, false);

            int[] c = p.dragRect();
            g.fill(c[0] - 1, c[1] - 1, c[0] + c[2] + 1, c[1] + c[3] + 1, darken(base, 0.15f));
            g.fill(c[0], c[1], c[0] + c[2], c[1] + c[3], base);
            g.drawString(font, p.chipLabel(), c[0] + 4, c[1] + 2, labelColor, false);
        }

        for (Nested ns : L.nested) {
            BlockDef nd = BlockDefRegistry.get(ns.block().type());
            draw(g, font, nd, ns.block(), ns.layout(), selectedId, hoveredId, -1, -1, -1, false, INPUT_NONE, false, script);
        }

        if (hovered && !selected) {
            if (L.cShape) {
                int sx = L.x + L.spineW;
                g.fill(L.x, L.arms.getFirst()[1], sx, L.arms.getLast()[0], 0x18FFFFFF);
                for (int[] a : L.arms) g.fill(L.x, a[0], L.x + L.w, a[1], 0x18FFFFFF);
            } else {
                g.fill(L.x, L.y, L.x + L.w, L.y + L.h, 0x18FFFFFF);
            }
        }
    }

    private static void drawSideInputPort(GuiGraphics g, int left, int cy, int halfW, int bcol) {
        int by0 = cy - halfW, by1 = cy + halfW;
        int bx0 = left - PORT_H;
        int bgy0 = by0 + PORT_NOTCH, bgy1 = by1 - PORT_NOTCH;
        g.fill(bx0, by0, left, bgy0, bcol);
        g.fill(bx0, bgy1, left, by1, bcol);
        g.fill(bx0 + PORT_H / 2, bgy0, left, bgy1, bcol);
    }

    private static void drawInputPort(GuiGraphics g, int cx, int top, int halfW, int bcol) {
        int bx0 = cx - halfW, bx1 = cx + halfW;
        int by0 = top - PORT_H;
        int bgx0 = bx0 + PORT_NOTCH, bgx1 = bx1 - PORT_NOTCH;
        g.fill(bx0, by0, bgx0, top, bcol);
        g.fill(bgx1, by0, bx1, top, bcol);
        g.fill(bgx0, by0 + PORT_H / 2, bgx1, top, bcol);
    }

    private static void drawCShape(GuiGraphics g, Layout L, int body, int base, int border) {
        List<int[]> arms = L.arms;
        int x0 = L.x, x1 = L.x + L.w;
        int sx = x0 + L.spineW;
        int top = arms.getFirst()[0];
        int bottom = arms.getLast()[1];
        int spineTop = arms.getFirst()[1];
        int spineBottom = arms.getLast()[0];

        g.fill(x0, spineTop, sx, spineBottom, body);
        for (int[] a : arms) g.fill(x0, a[0], x1, a[1], body);

        g.fill(x0, top, x1, Math.min(top + 9, arms.getFirst()[1]), base);

        g.fill(x0 - 1, top - 1, x0, bottom + 1, border);
        g.fill(x0 - 1, top - 1, x1 + 1, top, border);
        g.fill(x0 - 1, bottom, x1 + 1, bottom + 1, border);
        for (int[] a : arms) g.fill(x1, a[0], x1 + 1, a[1], border);

        for (int k = 0; k + 1 < arms.size(); k++) {
            int ceil = arms.get(k)[1];
            int floor = arms.get(k + 1)[0];
            g.fill(sx - 1, ceil - 1, x1 + 1, ceil, border);
            g.fill(sx - 1, floor, x1 + 1, floor + 1, border);
            g.fill(sx - 1, ceil - 1, sx, floor + 1, border);
        }
    }

    private static ParamSlot slotFor(List<ParamSlot> slots, String name) {
        for (ParamSlot s : slots) if (s.name().equals(name)) return s;
        return null;
    }

    public static List<ParamSlot> visibleSlots(BlockInstance inst, AiScript script) {
        List<ParamSlot> all = EffectiveSlots.forBlock(inst, script);
        ParamSlot modeSlot = null;
        for (ParamSlot s : all) {
            if (s.name().equals("mode")) { modeSlot = s; break; }
        }
        if (modeSlot == null) return all;
        Object v = inst.getParam("mode");
        if (v == null) v = modeSlot.defaultValue();
        String mode = String.valueOf(v);
        List<ParamSlot> vis = new ArrayList<>(all.size());
        for (ParamSlot s : all) {
            if (s.name().equals("ticks") && (mode.equals("once") || mode.equals("twice"))) continue;
            if (s.name().equals("interval") && !mode.equals("interval")) continue;
            vis.add(s);
        }
        return vis;
    }

    public static int lighten(int argb, float t) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int argb, float f) {
        int a = (argb >> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
