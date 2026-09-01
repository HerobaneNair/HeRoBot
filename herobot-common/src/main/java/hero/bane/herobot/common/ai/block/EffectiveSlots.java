package hero.bane.herobot.common.ai.block;

import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.ai.FuncDecl;
import hero.bane.herobot.common.ai.VarDecl;
import hero.bane.herobot.common.ai.VarType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EffectiveSlots {
    private EffectiveSlots() {}

    private static final List<String> LOOK_EXTRA_SLOTS = List.of("ticks", "yawOffset", "pitchOffset");

    public static List<ParamSlot> forBlock(BlockInstance block, AiScript script) {
        BlockType type = block.type();
        if (type == BlockType.SET_VAR || type == BlockType.CHANGE_VAR || type == BlockType.READ_VAR) {
            return varBlockSlots(type, block, script);
        }
        if (isLookBlock(type)) {
            return lookBlockSlots(block);
        }
        if (type == BlockType.TERNARY) {
            return ternarySlots(block, script);
        }
        if (isCalcBlock(type)) {
            return calcSlots(type, block);
        }
        if (type == BlockType.FUNC_DEFINE) {
            return funcDefineSlots(block, script);
        }
        if (type == BlockType.FUNC_CALL) {
            return funcCallSlots(block, script);
        }
        if (type == BlockType.FUNC_PARAM) {
            return List.of();
        }
        if (sensorTakesTarget(type)) {
            return sensorSlots(type, block);
        }
        if (sendTakesOp(type)) {
            return sendSlots(block);
        }
        return BlockDefRegistry.get(type).params();
    }

    public static boolean sendTakesOp(BlockType type) {
        return type == BlockType.SEND_MESSAGE;
    }

    public static boolean isVarBlock(BlockType type) {
        return type == BlockType.SET_VAR || type == BlockType.CHANGE_VAR || type == BlockType.READ_VAR;
    }

    public static boolean isOpShown(BlockInstance block) {
        return block.getParam("op") != null || block.getReporter("op") != null;
    }

    private static List<ParamSlot> sendSlots(BlockInstance block) {
        List<ParamSlot> base = BlockDefRegistry.get(BlockType.SEND_MESSAGE).params();
        if (!isOpShown(block)) return base;
        List<ParamSlot> out = new ArrayList<>(base);
        out.add(ParamSlot.ofBool("op", false));
        return out;
    }

    public static boolean isCalcBlock(BlockType type) {
        return type == BlockType.NUM_CALC || type == BlockType.STRING_CALC || type == BlockType.BOOL_CALC
                || type == BlockType.POS_CALC || type == BlockType.DIR_CALC;
    }

    public static int calcInputCount(BlockInstance block) {
        Object v = block.getParam("inputs");
        int n = v instanceof Number num ? num.intValue() : 0;
        return Math.clamp(n, 0, MAX_CALC_INPUTS);
    }

    public static final int MAX_CALC_INPUTS = 9;

    private static List<ParamSlot> calcSlots(BlockType type, BlockInstance block) {
        List<ParamSlot> base = BlockDefRegistry.get(type).params();
        int n = calcInputCount(block);
        if (n == 0) return base;
        List<ParamSlot> out = new ArrayList<>(base);
        ParamType t = switch (type) {
            case STRING_CALC, BOOL_CALC -> ParamType.STRING;
            default -> ParamType.DOUBLE;
        };
        for (int i = 1; i <= n; i++) {
            out.add(new ParamSlot("Input" + i, t, t == ParamType.DOUBLE ? 0.0 : ""));
        }
        return out;
    }

    public static final int MAX_FUNC_PARAMS = 9;

    public static String funcName(BlockInstance block) {
        String n = asString(block.getParam("name"));
        return n == null ? "" : n;
    }

    public static List<String> defineNameChoices(BlockInstance block, AiScript script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;
        String own = funcName(block);
        Set<String> taken = new HashSet<>();
        for (BlockInstance b : script.blocks().values()) {
            if (b.type() != BlockType.FUNC_DEFINE || b.id() == block.id()) continue;
            String n = funcName(b);
            if (!n.isEmpty()) taken.add(n);
        }
        for (FuncDecl f : script.functions()) {
            String q = f.qualifiedName();
            if (!taken.contains(q) || q.equals(own)) out.add(q);
        }
        return out;
    }

    public static List<String> functionNames(AiScript script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;
        for (FuncDecl f : script.functions()) out.add(f.qualifiedName());
        return out;
    }

    private static List<ParamSlot> funcDefineSlots(BlockInstance block, AiScript script) {
        return List.of(ParamSlot.ofEnum("name", defineNameChoices(block, script), funcName(block)));
    }

    private static List<ParamSlot> funcCallSlots(BlockInstance block, AiScript script) {
        List<ParamSlot> out = new ArrayList<>();
        out.add(ParamSlot.ofEnum("name", functionNames(script), funcName(block)));
        FuncDecl decl = script == null ? null : script.function(funcName(block));
        if (decl == null) return out;
        for (int i = 0; i < decl.numParams(); i++) {
            VarType t = decl.paramType(i);
            out.add(new ParamSlot("Arg" + (i + 1), paramTypeOf(t), defaultForVar(t)));
        }
        return out;
    }

    public static VarType funcParamType(AiScript script, BlockInstance reporter) {
        if (script == null || reporter == null) return null;
        FuncDecl f = script.function(asString(reporter.getParam("func")));
        if (f == null) return null;
        Object idx = reporter.getParam("index");
        return f.paramType(idx instanceof Number n ? n.intValue() : -1);
    }

    public static Object defaultForVar(VarType t) {
        if (t == null) return "";
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

    public static boolean sensorTakesTarget(BlockType type) {
        return switch (type) {
            case HEALTH, POSITION, YAW, IS_TOUCHING_BLOCK, ON_DAMAGE, BLOCK_AT,
                 HAS_TAG, SCOREBOARD, EQUIPMENT, DISTANCE_TO, DOING_ACTION,
                 ATTACK_COOLDOWN, HURT_TIME, PING -> true;
            default -> false;
        };
    }

    public static String sensorSubjectSlot(BlockType type) {
        return type == BlockType.DISTANCE_TO ? "from" : "target";
    }

    public static boolean isSensorTargetShown(BlockInstance block) {
        String subject = sensorSubjectSlot(block.type());
        if (Boolean.TRUE.equals(block.getParam("targetOther"))) return true;
        return block.getParam(subject) != null || block.getReporter(subject) != null;
    }

    private static List<ParamSlot> sensorSlots(BlockType type, BlockInstance block) {
        List<ParamSlot> base = BlockDefRegistry.get(type).params();
        String subject = sensorSubjectSlot(type);
        boolean shown = isSensorTargetShown(block);
        ParamSlot subjectSlot = null;
        List<ParamSlot> out = new ArrayList<>(base.size() + 1);
        for (ParamSlot s : base) {
            if (s.name().equals(subject)) subjectSlot = s;
            else out.add(s);
        }
        if (shown) {
            if (subjectSlot == null) subjectSlot = new ParamSlot(subject, ParamType.UUID, "@s");
            out.addFirst(subjectSlot);
        }
        return out;
    }

    public static boolean isTernaryValueSlot(String name) {
        return "trueValue".equals(name) || "falseValue".equals(name);
    }

    public static ParamType ternaryLockedType(BlockInstance block, AiScript script) {
        BlockInstance a = block.getReporter("trueValue");
        if (a != null) {
            ParamType t = reporterOutputType(a, script);
            if (t != null) return t;
        }
        BlockInstance b = block.getReporter("falseValue");
        if (b != null) {
            return reporterOutputType(b, script);
        }
        return null;
    }

    public static boolean ternaryAccepts(BlockInstance block, AiScript script, String slotName, ParamType out) {
        if (out == null) return true;
        String other = "trueValue".equals(slotName) ? "falseValue" : "trueValue";
        BlockInstance sibling = block.getReporter(other);
        if (sibling == null) return true;
        return typesMatch(reporterOutputType(sibling, script), out);
    }

    private static List<ParamSlot> ternarySlots(BlockInstance block, AiScript script) {
        ParamType t = ternaryLockedType(block, script);
        if (t == null) t = ParamType.INT;
        return List.of(
                ParamSlot.ofBool("condition", false),
                ternaryValueSlot("trueValue", t),
                ternaryValueSlot("falseValue", t));
    }

    private static ParamSlot ternaryValueSlot(String name, ParamType t) {
        return switch (t) {
            case BOOLEAN -> ParamSlot.ofBool(name, false);
            case DOUBLE -> new ParamSlot(name, ParamType.DOUBLE, 0.0);
            case STRING -> ParamSlot.ofString(name, "");
            case POSITION -> ParamSlot.ofPos(name);
            case ROTATION -> ParamSlot.ofRot(name);
            case UUID -> ParamSlot.ofUuid(name);
            case ITEM -> ParamSlot.ofItem(name, "minecraft:air");
            default -> ParamSlot.ofInt(name, 0);
        };
    }

    public static boolean isLookBlock(BlockType type) {
        return type == BlockType.LOOK_DIRECTION || type == BlockType.LOOK_CARDINAL
                || type == BlockType.LOOK_AT_POS || type == BlockType.LOOK_AT_ENTITY
                || type == BlockType.TURN;
    }

    public static boolean isLookExpanded(BlockInstance block) {
        if (Boolean.TRUE.equals(block.getParam("expanded"))) return true;
        for (String slot : LOOK_EXTRA_SLOTS) {
            if (block.getParam(slot) != null || block.getReporter(slot) != null) return true;
        }
        return false;
    }

    public static List<String> lookExtraSlots() {
        return LOOK_EXTRA_SLOTS;
    }

    public static boolean isLoopBlock(BlockType type) {
        return type == BlockType.FOR || type == BlockType.WHILE;
    }

    public static boolean isMinOneSlot(BlockType type, String slot) {
        return switch (type) {
            case WAIT, EVERY_X_TICKS -> slot.equals("ticks");
            case BREAK, FOR -> slot.equals("count");
            default -> false;
        };
    }

    public static int loopDisplayId(AiScript script, int loopBlockId) {
        if (script == null) return 1;
        BlockInstance loop = script.block(loopBlockId);
        if (loop == null || !isLoopBlock(loop.type())) return 0;
        int rank = 1;
        for (BlockInstance b : script.blocks().values()) {
            if (!isLoopBlock(b.type())) continue;
            if (b.id() < loopBlockId) rank++;
        }
        return rank;
    }

    public static boolean loopHasIterator(AiScript script, int loopBlockId) {
        if (script == null) return false;
        for (BlockInstance b : script.blocks().values()) {
            if (subtreeHasIterator(b, loopBlockId)) return true;
        }
        return false;
    }

    private static boolean subtreeHasIterator(BlockInstance b, int loopBlockId) {
        if (b.type() == BlockType.LOOP_ITER && b.pairedId() == loopBlockId) return true;
        for (BlockInstance child : b.reporterParams().values()) {
            if (child != null && subtreeHasIterator(child, loopBlockId)) return true;
        }
        return false;
    }

    public static boolean isLoopIterShown(AiScript script, BlockInstance block) {
        return Boolean.TRUE.equals(block.getParam("iterShown"))
                || loopHasIterator(script, block.id());
    }

    public static List<ParamSlot> initialSlots(BlockType type) {
        List<ParamSlot> all = BlockDefRegistry.get(type).params();
        if (isLookBlock(type)) return withoutLookExtras(all);
        if (sensorTakesTarget(type)) {
            String subject = sensorSubjectSlot(type);
            List<ParamSlot> slots = new ArrayList<>(all.size());
            for (ParamSlot s : all) {
                if (!s.name().equals(subject)) slots.add(s);
            }
            return slots;
        }
        return all;
    }

    private static List<ParamSlot> lookBlockSlots(BlockInstance block) {
        List<ParamSlot> all = BlockDefRegistry.get(block.type()).params();
        if (isLookExpanded(block)) return all;
        return withoutLookExtras(all);
    }

    private static List<ParamSlot> withoutLookExtras(List<ParamSlot> all) {
        List<ParamSlot> slots = new ArrayList<>(all.size());
        for (ParamSlot s : all) {
            if (!LOOK_EXTRA_SLOTS.contains(s.name())) slots.add(s);
        }
        return slots;
    }

    private static List<ParamSlot> varBlockSlots(BlockType type, BlockInstance block, AiScript script) {
        List<String> names = variableNames(script);
        Object stored = block.getParam("name");
        String current = stored != null ? stored.toString()
                : (names.isEmpty() ? "" : names.getFirst());
        ParamSlot nameSlot = new ParamSlot("name", ParamType.VAR_REF, current, names);

        if (type == BlockType.READ_VAR) {
            return List.of(nameSlot);
        }

        VarType vt = varType(script, current);
        List<ParamSlot> slots = new ArrayList<>();
        slots.add(nameSlot);

        boolean set = type == BlockType.SET_VAR;
        ParamSlot valueSlot = valueSlotFor(vt, set);
        if (valueSlot != null) slots.add(valueSlot);
        return slots;
    }

    private static ParamSlot valueSlotFor(VarType vt, boolean set) {
        if (vt == null) vt = VarType.DOUBLE;
        String slotName = set ? "value" : "delta";
        return switch (vt) {
            case BOOL -> set
                    ? ParamSlot.ofBool(slotName, true)
                    : null;
            case INT -> new ParamSlot(slotName, ParamType.INT, set ? 0 : 1);
            case DOUBLE -> new ParamSlot(slotName, ParamType.DOUBLE, set ? 0.0 : 1.0);
            case POSITION -> new ParamSlot(slotName, ParamType.POSITION, set ? "~ ~ ~" : "0 0 0");
            case ROTATION -> new ParamSlot(slotName, ParamType.ROTATION, "0 0");
            case STRING -> new ParamSlot(slotName, ParamType.STRING, "");
            case UUID -> set ? ParamSlot.ofUuid(slotName) : null;
            case ITEM -> set ? ParamSlot.ofItem(slotName, "minecraft:air") : null;
        };
    }

    public static List<String> variableNames(AiScript script) {
        List<String> names = new ArrayList<>();
        if (script != null) {
            for (VarDecl v : script.variables()) names.add(v.qualifiedName());
        }
        return names;
    }

    public static VarType varType(AiScript script, String name) {
        if (script == null || name == null) return null;
        for (VarDecl v : script.variables()) {
            if (v.qualifiedName().equals(name)) return v.type();
        }
        return null;
    }

    public static ParamType reporterOutputType(BlockInstance reporter, AiScript script) {
        if (reporter == null) return null;
        return switch (reporter.type()) {
            case VEC3, POSITION, POS_CALC -> ParamType.POSITION;
            case ROT, DIR_CALC -> ParamType.ROTATION;
            case TO_STRING, STRING_CALC, MSG_TEXT -> ParamType.STRING;
            case NUM_CALC, RANDOM_DOUBLE, HEALTH, YAW, DISTANCE_TO, TIME_OF_DAY, SCOREBOARD, COMMAND_RESULT,
                 ATTACK_COOLDOWN -> ParamType.DOUBLE;
            case ITEM_IN_SLOT, EQUIPMENT -> ParamType.ITEM;
            case RANDOM_INT, GET_COUNT, MAX_COUNT, INVENTORY_OPEN, CONTAINER_SIZE, LOOP_ITER, HURT_TIME, PING -> ParamType.INT;
            case COMPARE, EQUALITY, LOGIC, AND, OR, NOT, CONTAINS, IS_TOUCHING_BLOCK, HAS_TAG, IN_PATH, EVERY_X_TICKS, ON_DAMAGE, BOOL_CALC, DOING_ACTION -> ParamType.BOOLEAN;
            case READ_VAR -> paramTypeOf(varType(script, asString(reporter.getParam("name"))));
            case FUNC_PARAM -> paramTypeOf(funcParamType(script, reporter));
            case TERNARY -> {
                ParamType t = ternaryLockedType(reporter, script);
                yield t == null ? ParamType.INT : t;
            }
            default -> null;
        };
    }

    public static boolean accepts(BlockType host, ParamType slot, ParamType out) {
        if (host == BlockType.TO_STRING) return true;
        if (host == BlockType.EQUALITY) return true;
        return accepts(slot, out);
    }

    public static boolean accepts(BlockType host, String slotName, ParamType slot, ParamType out) {
        if (isCalcBlock(host) && slotName != null && slotName.startsWith("Input")) {
            return calcInputAccepts(host, out);
        }
        return accepts(host, slot, out);
    }

    public static boolean calcInputAccepts(BlockType host, ParamType out) {
        if (out == null) return true;
        return switch (host) {
            case POS_CALC -> out == ParamType.INT || out == ParamType.DOUBLE || out == ParamType.POSITION;
            case DIR_CALC -> out == ParamType.INT || out == ParamType.DOUBLE || out == ParamType.ROTATION;
            default -> true;
        };
    }

    public static boolean typesMatch(ParamType a, ParamType b) {
        if (a == null || b == null) return true;
        boolean aNum = a == ParamType.INT || a == ParamType.DOUBLE;
        boolean bNum = b == ParamType.INT || b == ParamType.DOUBLE;
        if (aNum && bNum) return true;
        return a == b;
    }

    public static boolean accepts(ParamType slot, ParamType out) {
        if (out == null) return true;
        return switch (slot) {
            case STRING -> out == ParamType.STRING;
            case POSITION -> out == ParamType.POSITION;
            case ROTATION -> out == ParamType.ROTATION;
            case UUID -> out == ParamType.UUID;
            case ITEM -> out == ParamType.ITEM;
            case INT, DOUBLE -> out == ParamType.INT || out == ParamType.DOUBLE;
            case BOOLEAN -> out == ParamType.BOOLEAN || out == ParamType.INT || out == ParamType.DOUBLE;
            default -> true;
        };
    }

    public static ParamType paramTypeOf(VarType vt) {
        if (vt == null) return null;
        return switch (vt) {
            case BOOL -> ParamType.BOOLEAN;
            case INT -> ParamType.INT;
            case DOUBLE -> ParamType.DOUBLE;
            case POSITION -> ParamType.POSITION;
            case ROTATION -> ParamType.ROTATION;
            case STRING -> ParamType.STRING;
            case UUID -> ParamType.UUID;
            case ITEM -> ParamType.ITEM;
        };
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
