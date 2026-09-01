package hero.bane.herobot.paper.ai.runtime;

import hero.bane.herobot.common.ai.block.BlockDef;
import hero.bane.herobot.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.block.ParamSlot;
import hero.bane.herobot.common.ai.block.ParamType;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import hero.bane.herobot.common.ai.runtime.Branch;

public final class ParamEval {
    private ParamEval() {}

    public static Object raw(BlockInstance block, String name, ScriptRunner runner, Branch branch) {
        ParamSlot slot = slotOf(block, name);
        BlockInstance reporter = block.getReporter(name);
        Object v;
        if (reporter != null) {
            v = runner.evalReporter(reporter, branch);
        } else {
            v = block.getParam(name);
            if (v == null && slot != null) v = slot.defaultValue();
        }
        return coerceEnum(slot, v);
    }

    private static final Map<BlockType, Map<String, ParamSlot>> SLOTS = new ConcurrentHashMap<>();

    private static ParamSlot slotOf(BlockInstance block, String name) {
        BlockDef def = BlockDefRegistry.get(block.type());
        if (def == null) return null;
        return SLOTS.computeIfAbsent(block.type(), t -> {
            Map<String, ParamSlot> m = new HashMap<>();
            for (ParamSlot s : def.params()) m.putIfAbsent(s.name(), s);
            return m;
        }).get(name);
    }

    private static Object coerceEnum(ParamSlot slot, Object v) {
        if (slot == null || slot.type() != ParamType.ENUM) return v;
        List<String> choices = slot.enumChoices();
        if (choices.isEmpty() || allNumeric(choices)) return v;
        if (v instanceof String s && choices.contains(s.trim())) return v;
        Integer idx = asIndex(v);
        if (idx == null || idx < 0 || idx >= choices.size()) return v;
        return choices.get(idx);
    }

    private static boolean allNumeric(List<String> choices) {
        for (String c : choices) {
            try { Integer.parseInt(c.trim()); } catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    private static Integer asIndex(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.valueOf(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public static boolean asBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() >= 1;
        if (v instanceof String s) {
            String t = s.trim();
            try { return Double.parseDouble(t) >= 1; } catch (NumberFormatException ignored) {}
            return Boolean.parseBoolean(t);
        }
        return v != null;
    }

    public static int asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {
                try { return (int) Double.parseDouble(s.trim()); } catch (NumberFormatException ex) { return 0; }
            }
        }
        return 0;
    }

    public static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    public static String asString(Object v) {
        return v == null ? "" : v.toString();
    }

    public static Vec3 asVec3(Object v, Entity base) {
        if (v instanceof Vec3 vec) return vec;
        if (v instanceof String s) return parseVec3(s, base);
        return base.position();
    }

    public static Vec3 parseVec3(String s, Entity base) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length < 3) return base.position();
        try {
            double x = relativeOrAbsolute(parts[0], base.getX());
            double y = relativeOrAbsolute(parts[1], base.getY());
            double z = relativeOrAbsolute(parts[2], base.getZ());
            return new Vec3(x, y, z);
        } catch (NumberFormatException ignored) {
            return base.position();
        }
    }

    private static double relativeOrAbsolute(String token, double base) {
        if (token.startsWith("~")) {
            String rest = token.substring(1);
            if (rest.isEmpty()) return base;
            return base + Double.parseDouble(rest);
        }
        return Double.parseDouble(token);
    }

    public static float[] asRotation(Object v) {
        if (v instanceof float[] arr && arr.length >= 2) return new float[]{arr[0], arr[1]};
        if (v instanceof String s) {
            String[] parts = s.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    return new float[]{Float.parseFloat(parts[0]), Float.parseFloat(parts[1])};
                } catch (NumberFormatException ignored) {}
            }
        }
        return new float[]{0, 0};
    }

    public static boolean evalBool(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asBool(raw(block, name, r, b));
    }

    public static int evalInt(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asInt(raw(block, name, r, b));
    }

    public static double evalDouble(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asDouble(raw(block, name, r, b));
    }

    public static String evalString(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asString(raw(block, name, r, b));
    }

    public static Vec3 evalVec3(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asVec3(raw(block, name, r, b), r.player());
    }

    public static float[] evalRotation(BlockInstance block, String name, ScriptRunner r, Branch b) {
        return asRotation(raw(block, name, r, b));
    }

    @SuppressWarnings("unused")
    private static WorldCoordinates dummyImport() { return null; }
}
