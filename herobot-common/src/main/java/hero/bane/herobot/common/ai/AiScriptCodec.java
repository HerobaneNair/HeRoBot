package hero.bane.herobot.common.ai;

import com.google.gson.*;
import hero.bane.herobot.common.ai.block.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class AiScriptCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeroBot/script");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AiScriptCodec() {
    }

    public static String toJson(AiScript script) {
        return toJson(script, false);
    }

    public static String toJson(AiScript script, boolean omitPositions) {
        JsonObject root = new JsonObject();
        root.addProperty("version", script.version());
        root.addProperty("name", script.name());
        if (omitPositions) root.addProperty("sorted", true);

        JsonArray vars = new JsonArray();
        for (VarDecl v : script.variables()) {
            JsonObject vo = new JsonObject();
            vo.addProperty("name", v.name());
            vo.addProperty("type", v.type().name());
            vo.add("default", paramToJson(v.defaultValue()));
            if (!v.folder().isEmpty()) vo.addProperty("folder", v.folder());
            vars.add(vo);
        }
        root.add("variables", vars);

        JsonArray varFolders = new JsonArray();
        for (String f : script.varFolders()) varFolders.add(f);
        root.add("varFolders", varFolders);

        JsonArray functions = new JsonArray();
        for (FuncDecl f : script.functions()) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f.name());
            if (!f.folder().isEmpty()) fo.addProperty("folder", f.folder());
            JsonArray params = new JsonArray();
            for (VarType t : f.params()) params.add(t.name());
            fo.add("params", params);
            functions.add(fo);
        }
        root.add("functions", functions);

        JsonArray blocks = new JsonArray();
        for (BlockInstance b : script.blocks().values()) {
            blocks.add(blockToJson(b, omitPositions));
        }
        root.add("blocks", blocks);

        JsonArray wires = new JsonArray();
        for (Wire w : script.wires()) {
            JsonObject wo = new JsonObject();
            wo.addProperty("from", w.fromBlockId());
            wo.addProperty("out", w.outPort());
            wo.addProperty("to", w.toBlockId());
            if (w.toPort() != 0) wo.addProperty("toPort", w.toPort());
            wires.add(wo);
        }
        root.add("wires", wires);

        JsonArray comments = new JsonArray();
        for (Comment c : script.comments()) {
            JsonObject co = new JsonObject();
            co.addProperty("id", c.id());
            co.addProperty("x", c.x());
            co.addProperty("y", c.y());
            co.addProperty("text", c.text());
            JsonArray styles = encodeStyles(c);
            if (styles != null) co.add("styles", styles);
            if (c.attachedTo() >= 0) {
                co.addProperty("attachedTo", c.attachedTo());
                co.addProperty("offX", c.offX());
                co.addProperty("offY", c.offY());
            }
            comments.add(co);
        }
        root.add("comments", comments);

        return GSON.toJson(root);
    }

    public static String copyBlocks(AiScript s, Set<Integer> ids) {
        Set<Integer> expanded = new HashSet<>(ids);
        for (int id : ids) {
            BlockInstance b = s.block(id);
            if (b != null && b.pairedId() >= 0 && !b.type().refsOwner()) {
                expanded.add(b.pairedId());
            }
        }
        ids = expanded;
        JsonObject root = new JsonObject();
        JsonArray blocks = new JsonArray();
        for (int id : ids) {
            BlockInstance b = s.block(id);
            if (b != null) blocks.add(blockToJson(b));
        }
        root.add("blocks", blocks);
        JsonArray wires = new JsonArray();
        for (Wire w : s.wires()) {
            if (ids.contains(w.fromBlockId()) && ids.contains(w.toBlockId())) {
                JsonObject wo = new JsonObject();
                wo.addProperty("from", w.fromBlockId());
                wo.addProperty("out", w.outPort());
                wo.addProperty("to", w.toBlockId());
                if (w.toPort() != 0) wo.addProperty("toPort", w.toPort());
                wires.add(wo);
            }
        }
        root.add("wires", wires);
        return GSON.toJson(root);
    }

    public static double[] minCorner(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        boolean any = false;
        if (root.has("blocks")) {
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                JsonObject o = e.getAsJsonObject();
                double x = o.has("x") ? o.get("x").getAsDouble() : 0;
                double y = o.has("y") ? o.get("y").getAsDouble() : 0;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                any = true;
            }
        }
        return any ? new double[]{minX, minY} : null;
    }

    public static List<Integer> pasteBlocks(AiScript s, String json, double dx, double dy) {
        List<Integer> newTop = new ArrayList<>();
        Map<Integer, Integer> idMap = new HashMap<>();
        List<BlockInstance> clones = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("blocks")) {
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                BlockInstance parsed = blockFromJson(e.getAsJsonObject());
                BlockInstance clone = cloneWithNewIds(parsed, s, dx, dy, idMap, clones);
                s.putBlock(clone);
                newTop.add(clone.id());
            }
        }
        if (root.has("wires")) {
            for (JsonElement e : root.getAsJsonArray("wires")) {
                JsonObject wo = e.getAsJsonObject();
                Integer from = idMap.get(wo.get("from").getAsInt());
                Integer to = idMap.get(wo.get("to").getAsInt());
                int toPort = wo.has("toPort") ? wo.get("toPort").getAsInt() : 0;
                if (from != null && to != null) s.addWire(from, wo.get("out").getAsInt(), to, toPort);
            }
        }
        for (BlockInstance nb : clones) {
            if (nb.pairedId() < 0) continue;
            Integer mapped = idMap.get(nb.pairedId());
            if (mapped != null) {
                nb.setPairedId(mapped);
            } else if (!nb.type().refsOwner() || s.block(nb.pairedId()) == null) {
                nb.setPairedId(-1);
            }
        }
        return newTop;
    }

    private static BlockInstance cloneWithNewIds(BlockInstance src, AiScript s, double dx, double dy,
                                                 Map<Integer, Integer> idMap, List<BlockInstance> clones) {
        BlockInstance copy = new BlockInstance(s.freshId(), src.type(), src.x() + dx, src.y() + dy);
        idMap.put(src.id(), copy.id());
        clones.add(copy);
        copy.setPairedId(src.pairedId());
        for (Map.Entry<String, Object> e : src.params().entrySet()) copy.setParam(e.getKey(), e.getValue());
        for (Map.Entry<String, BlockInstance> e : src.reporterParams().entrySet()) {
            copy.setReporter(e.getKey(), cloneWithNewIds(e.getValue(), s, 0, 0, idMap, clones));
        }
        return copy;
    }

    public static AiScript fromJson(String json, String fallbackName) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String name = root.has("name") ? root.get("name").getAsString() : fallbackName;
        AiScript s = new AiScript(name);
        s.setVersion(root.has("version") ? root.get("version").getAsInt() : 1);

        if (root.has("variables")) {
            for (JsonElement e : root.getAsJsonArray("variables")) {
                JsonObject vo = e.getAsJsonObject();
                String vname = vo.get("name").getAsString();
                VarType type = VarType.valueOf(vo.get("type").getAsString());
                Object def = jsonToVarDefault(vo.get("default"), type);
                String folder = vo.has("folder") ? vo.get("folder").getAsString() : "";
                s.variables().add(new VarDecl(vname, type, def, folder));
            }
        }

        if (root.has("varFolders")) {
            for (JsonElement e : root.getAsJsonArray("varFolders")) {
                s.varFolders().add(e.getAsString());
            }
        }

        if (root.has("functions")) {
            for (JsonElement e : root.getAsJsonArray("functions")) {
                JsonObject fo = e.getAsJsonObject();
                List<VarType> params = new ArrayList<>();
                if (fo.has("params")) {
                    for (JsonElement pe : fo.getAsJsonArray("params")) {
                        params.add(VarType.valueOf(pe.getAsString()));
                    }
                }
                String ffolder = fo.has("folder") ? fo.get("folder").getAsString() : "";
                s.functions().add(new FuncDecl(fo.get("name").getAsString(), params, ffolder));
            }
        }

        if (root.has("blocks")) {
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                BlockInstance b = blockFromJson(e.getAsJsonObject());
                s.putBlock(b);
                registerNestedIds(s, b);
            }
        }

        if (root.has("wires")) {
            for (JsonElement e : root.getAsJsonArray("wires")) {
                JsonObject wo = e.getAsJsonObject();
                if (!wo.has("from") || !wo.has("out") || !wo.has("to")) {
                    LOGGER.warn("Script {}: skipping wire with missing from/out/to", name);
                    continue;
                }
                int from = wo.get("from").getAsInt();
                int to = wo.get("to").getAsInt();
                int toPort = wo.has("toPort") ? wo.get("toPort").getAsInt() : 0;
                if (s.block(from) == null || s.block(to) == null) {
                    LOGGER.warn("Script {}: dropping wire {} -> {} (no such block)", name, from, to);
                    continue;
                }
                s.addWire(from, wo.get("out").getAsInt(), to, toPort);
            }
        }

        for (BlockInstance b : s.blocks().values()) {
            if (b.pairedId() >= 0 && s.block(b.pairedId()) == null) {
                LOGGER.warn("Script {}: block {} paired to missing block {}", name, b.id(), b.pairedId());
                b.setPairedId(-1);
            }
        }

        if (root.has("comments")) {
            for (JsonElement e : root.getAsJsonArray("comments")) {
                JsonObject co = e.getAsJsonObject();
                int id = co.has("id") ? co.get("id").getAsInt() : s.freshId();
                double x = co.has("x") ? co.get("x").getAsDouble() : 0;
                double y = co.has("y") ? co.get("y").getAsDouble() : 0;
                String text = co.has("text") ? co.get("text").getAsString() : "";
                Comment c = new Comment(id, x, y, text);
                if (co.has("styles")) decodeStyles(co.getAsJsonArray("styles"), c);
                if (co.has("attachedTo")) {
                    c.setAttachedTo(co.get("attachedTo").getAsInt());
                    c.setOffset(co.has("offX") ? co.get("offX").getAsDouble() : 0,
                            co.has("offY") ? co.get("offY").getAsDouble() : 0);
                }
                s.putComment(c);
            }
        }
        return s;
    }

    private static JsonArray encodeStyles(Comment c) {
        byte[] styles = c.styles();
        boolean any = false;
        for (byte b : styles)
            if (b != 0) {
                any = true;
                break;
            }
        if (!any) return null;
        JsonArray arr = new JsonArray();
        int i = 0;
        while (i < styles.length) {
            int flag = styles[i] & 0xFF;
            int j = i + 1;
            while (j < styles.length && (styles[j] & 0xFF) == flag) j++;
            arr.add(j - i);
            arr.add(flag);
            i = j;
        }
        return arr;
    }

    private static void decodeStyles(JsonArray arr, Comment c) {
        byte[] styles = new byte[c.text().length()];
        int pos = 0;
        for (int k = 0; k + 1 < arr.size(); k += 2) {
            int len = arr.get(k).getAsInt();
            int flag = arr.get(k + 1).getAsInt();
            for (int n = 0; n < len && pos < styles.length; n++) styles[pos++] = (byte) flag;
        }
        c.setStyles(styles);
    }

    private static void registerNestedIds(AiScript s, BlockInstance b) {
        for (BlockInstance child : b.reporterParams().values()) {
            s.bumpNextId(child.id());
            registerNestedIds(s, child);
        }
    }

    private static JsonObject blockToJson(BlockInstance b) {
        return blockToJson(b, false);
    }

    private static JsonObject blockToJson(BlockInstance b, boolean omitPositions) {
        JsonObject o = new JsonObject();
        o.addProperty("id", b.id());
        o.addProperty("type", b.type().name());
        if (!omitPositions) {
            o.addProperty("x", b.x());
            o.addProperty("y", b.y());
        }
        if (b.pairedId() >= 0) o.addProperty("pairedId", b.pairedId());
        JsonObject params = new JsonObject();
        for (Map.Entry<String, Object> e : b.params().entrySet()) {
            params.add(e.getKey(), paramToJson(e.getValue()));
        }
        o.add("params", params);
        if (!b.reporterParams().isEmpty()) {
            JsonObject reps = new JsonObject();
            for (Map.Entry<String, BlockInstance> e : b.reporterParams().entrySet()) {
                reps.add(e.getKey(), blockToJson(e.getValue(), omitPositions));
            }
            o.add("reporters", reps);
        }
        return o;
    }

    public static boolean wasSorted(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("sorted") && root.get("sorted").getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static BlockInstance blockFromJson(JsonObject o) {
        if (!o.has("id") || !o.has("type")) {
            throw new IllegalArgumentException("block entry is missing id/type");
        }
        int id = o.get("id").getAsInt();
        String typeName = o.get("type").getAsString();
        double x = o.has("x") ? o.get("x").getAsDouble() : 0;
        double y = o.has("y") ? o.get("y").getAsDouble() : 0;

        String op = legacyArithOp(typeName);
        if (op != null) {
            BlockInstance calc = new BlockInstance(id, BlockType.NUM_CALC, x, y);
            calc.setParam("expression", legacyExpr(o, op));
            return calc;
        }

        if (typeName.equals("TRADE_EXECUTE")) {
            BlockInstance click = new BlockInstance(id, BlockType.INV_CLICK, x, y);
            click.setParam("menu", "container");
            click.setParam("mode", legacyTradeClickMode(o));
            click.setParam("slot", 2);
            return click;
        }

        boolean legacyPitch = typeName.equals("PITCH");
        if (legacyPitch) typeName = "YAW";

        boolean legacyOffhand = typeName.equals("INV_SWAP_OFFHAND");
        if (legacyOffhand) typeName = "INV_SWAP_HOTBAR";

        if (typeName.equals("LOOK_YAW_PITCH")) typeName = "LOOK_DIRECTION";
        else if (typeName.equals("LOOK_DIRECTION") && isLegacyCardinal(o)) typeName = "LOOK_CARDINAL";

        if (typeName.equals("OPEN_CONTAINER")) typeName = "OPEN_INVENTORY";

        if (typeName.equals("CONTAINER_OPEN")) typeName = "INVENTORY_OPEN";

        boolean legacyDropStack = typeName.equals("DROP_STACK");
        BlockType type = legacyDropStack ? BlockType.DROP_ITEM : BlockType.valueOf(typeName);
        BlockInstance b = new BlockInstance(id, type, x, y);
        if (legacyDropStack) b.setParam("amount", "stack");
        if (legacyPitch) b.setParam("axis", "pitch");
        if (o.has("pairedId")) b.setPairedId(o.get("pairedId").getAsInt());
        if (o.has("params")) {
            JsonObject p = o.getAsJsonObject("params");
            BlockDef def = BlockDefRegistry.get(type);
            for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                ParamSlot slot = findSlot(def, e.getKey());
                b.setParam(e.getKey(), jsonToParam(e.getValue(), slot));
            }
        }
        if (type == BlockType.LOOK_DIRECTION && b.getParam("direction") == null
                && (b.getParam("yaw") != null || b.getParam("pitch") != null)) {
            b.setParam("direction",
                    num(legacyDouble(b.getParam("yaw"))) + " " + num(legacyDouble(b.getParam("pitch"))));
        }
        if (type == BlockType.INV_SWAP_HOTBAR) {
            if (legacyOffhand) b.setParam("with", "offhand");
            else if (b.getParam("with") == null && b.getParam("hotbarSlot") != null) {
                b.setParam("with", b.getParam("hotbarSlot").toString());
            }
        }
        if (type == BlockType.RECIPE_BOOK && b.getParam("mode") == null && b.getParam("max") != null) {
            Object mx = b.getParam("max");
            boolean isMax = mx instanceof Boolean bb ? bb : Boolean.parseBoolean(String.valueOf(mx));
            b.setParam("mode", isMax ? "max" : "single");
        }
        if (type == BlockType.CONTAINS && b.getParam("checkCase") == null && b.getParam("ci") != null) {
            Object ci = b.getParam("ci");
            boolean insensitive = ci instanceof Boolean bb ? bb : Boolean.parseBoolean(String.valueOf(ci));
            b.setParam("checkCase", !insensitive);
            b.params().remove("ci");
        }
        if (o.has("reporters")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("reporters").entrySet()) {
                b.setReporter(e.getKey(), blockFromJson(e.getValue().getAsJsonObject()));
            }
        }
        return b;
    }

    private static String legacyArithOp(String typeName) {
        return switch (typeName) {
            case "ADD" -> "+";
            case "SUB" -> "-";
            case "MUL" -> "*";
            case "DIV" -> "/";
            case "MOD" -> "%";
            default -> null;
        };
    }

    private static String legacyExpr(JsonObject o, String op) {
        double a = 0, b = 0;
        if (o.has("params")) {
            JsonObject p = o.getAsJsonObject("params");
            if (p.has("a") && p.get("a").isJsonPrimitive()) try {
                a = p.get("a").getAsDouble();
            } catch (RuntimeException ignored) {
            }
            if (p.has("b") && p.get("b").isJsonPrimitive()) try {
                b = p.get("b").getAsDouble();
            } catch (RuntimeException ignored) {
            }
        }
        return num(a) + " " + op + " " + num(b);
    }

    private static String num(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private static double legacyDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static boolean isLegacyCardinal(JsonObject o) {
        if (!o.has("params")) return false;
        JsonObject p = o.getAsJsonObject("params");
        if (!p.has("direction") || !p.get("direction").isJsonPrimitive()) return false;
        return switch (p.get("direction").getAsString()) {
            case "north", "south", "east", "west", "up", "down" -> true;
            default -> false;
        };
    }

    private static String legacyTradeClickMode(JsonObject o) {
        String mode = "once";
        if (o.has("params")) {
            JsonObject p = o.getAsJsonObject("params");
            if (p.has("mode") && p.get("mode").isJsonPrimitive()) mode = p.get("mode").getAsString();
        }
        return switch (mode) {
            case "shiftClick" -> "shiftClick";
            case "drop" -> "throw";
            case "dropMax" -> "throwAll";
            default -> "click";
        };
    }

    private static ParamSlot findSlot(BlockDef def, String name) {
        for (ParamSlot s : def.params()) if (s.name().equals(name)) return s;
        return null;
    }

    private static JsonElement paramToJson(Object value) {
        return switch (value) {
            case null -> com.google.gson.JsonNull.INSTANCE;
            case Boolean b -> new com.google.gson.JsonPrimitive(b);
            case Number n -> new com.google.gson.JsonPrimitive(n);
            default -> new com.google.gson.JsonPrimitive(value.toString());
        };
    }

    private static Object jsonToParam(JsonElement el) {
        return jsonToParam(el, null);
    }

    private static Object jsonToVarDefault(JsonElement el, VarType type) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            var prim = el.getAsJsonPrimitive();
            if (type == VarType.BOOL && prim.isBoolean()) return prim.getAsBoolean();
            if (type == VarType.INT && prim.isNumber()) return prim.getAsInt();
            if (type == VarType.DOUBLE && prim.isNumber()) return prim.getAsDouble();
        }
        return jsonToParam(el);
    }

    private static Object jsonToParam(JsonElement el, ParamSlot slot) {
        if (el == null || el.isJsonNull()) return null;
        if (slot != null && el.isJsonPrimitive()) {
            var prim = el.getAsJsonPrimitive();
            ParamType t = slot.type();
            if (t == ParamType.BOOLEAN && prim.isBoolean()) return prim.getAsBoolean();
            if (t == ParamType.INT && prim.isNumber()) return prim.getAsInt();
            if (t == ParamType.DOUBLE && prim.isNumber()) return prim.getAsDouble();
            return prim.getAsString();
        }
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                if (d == Math.floor(d) && !Double.isInfinite(d)) return (int) d;
                return d;
            }
            return p.getAsString();
        }
        return el.toString();
    }
}
