package hero.bane.herobot.mod.common.ai.runtime.executors;

import com.mojang.brigadier.StringReader;
import hero.bane.herobot.common.ai.VarType;
import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.runtime.RuntimeVariable;
import hero.bane.herobot.common.ai.runtime.StepResult;
import hero.bane.herobot.mod.common.ai.SelectorValidation;
import hero.bane.herobot.mod.common.ai.runtime.Executor;
import hero.bane.herobot.mod.common.ai.runtime.ParamEval;
import hero.bane.herobot.mod.common.ai.runtime.Reporter;
import hero.bane.herobot.mod.common.ai.runtime.ScriptRunner;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DataExecutor {
    private DataExecutor() {}

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        flow.put(BlockType.SET_VAR, (b, r, br) -> {
            String name = ParamEval.evalString(b, "name", r, br);
            Object value = ParamEval.raw(b, "value", r, br);
            RuntimeVariable v = r.variable(name);
            if (v != null) {
                v.set(coerce(v.type(), value, r));
            } else {
                r.defineVariable(name, new RuntimeVariable(VarType.STRING, ParamEval.asString(value)));
            }
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.CHANGE_VAR, (b, r, br) -> {
            String name = ParamEval.evalString(b, "name", r, br);
            RuntimeVariable v = r.variable(name);
            if (v == null) {
                r.defineVariable(name, new RuntimeVariable(VarType.DOUBLE, ParamEval.evalDouble(b, "delta", r, br)));
                return StepResult.continueVia(0);
            }
            switch (v.type()) {
                case BOOL -> v.set(!ParamEval.asBool(v.value()));
                case INT -> v.set(ParamEval.asInt(v.value()) + ParamEval.evalInt(b, "delta", r, br));
                case DOUBLE -> v.set(ParamEval.asDouble(v.value()) + ParamEval.evalDouble(b, "delta", r, br));
                case POSITION -> {
                    Vec3 cur = ParamEval.asVec3(v.value(), r.player());
                    Vec3 d = ParamEval.asVec3(ParamEval.raw(b, "delta", r, br), r.player());
                    v.set(cur.add(d));
                }
                case ROTATION -> {
                    float[] cur = ParamEval.asRotation(v.value());
                    float[] d = ParamEval.asRotation(ParamEval.raw(b, "delta", r, br));
                    v.set(new float[]{cur[0] + d[0], cur[1] + d[1]});
                }
                case STRING -> v.set(ParamEval.asString(v.value()) + ParamEval.asString(ParamEval.raw(b, "delta", r, br)));
                case UUID -> {  }
                case ITEM -> {  }
            }
            return StepResult.continueVia(0);
        });

        reporter.put(BlockType.READ_VAR, (b, r, br) -> {
            String name = ParamEval.evalString(b, "name", r, br);
            RuntimeVariable v = r.variable(name);
            return v == null ? null : v.value();
        });

        reporter.put(BlockType.TO_STRING, (b, r, br) -> stringify(ParamEval.raw(b, "value", r, br), r));
    }

    public static String stringify(Object v, ScriptRunner r) {
        switch (v) {
            case null -> {
                return "";
            }
            case ItemStack st -> {
                return st.getHoverName().getString();
            }
            case Vec3 p -> {
                return p.x + " " + p.y + " " + p.z;
            }
            case float[] rot when rot.length >= 2 -> {
                return rot[0] + " " + rot[1];
            }
            case Entity e -> {
                return e.getDisplayName().getString();
            }
            case UUID u -> {
                return entityName(u, r);
            }
            default -> {
                String s = ParamEval.asString(v);
                if (SelectorValidation.isUuid(s)) return entityName(UUID.fromString(s.trim()), r);
                return s;
            }
        }
    }

    private static String entityName(UUID id, ScriptRunner r) {
        Entity e = r.player().level().getEntity(id);
        return e != null ? e.getDisplayName().getString() : id.toString();
    }

    public static Object coerce(VarType type, Object value, ScriptRunner r) {
        return switch (type) {
            case BOOL -> ParamEval.asBool(value);
            case INT -> ParamEval.asInt(value);
            case DOUBLE -> ParamEval.asDouble(value);
            case STRING -> ParamEval.asString(value);
            case POSITION -> ParamEval.asVec3(value, r.player());
            case ROTATION -> ParamEval.asRotation(value);
            case UUID -> coerceUuid(value, r);
            case ITEM -> asItemStack(value, r);
        };
    }

    public static ItemStack asItemStack(Object value, ScriptRunner r) {
        if (value instanceof ItemStack st) return st.copy();
        String s = ParamEval.asString(value).trim();
        if (s.isEmpty()) return ItemStack.EMPTY;
        try {
            return new ItemParser(r.player().level().registryAccess())
                    .parse(new StringReader(s))
                    .createItemStack(1);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static UUID coerceUuid(Object value, ScriptRunner r) {
        if (value instanceof UUID u) return u;
        if (value instanceof Entity e) return e.getUUID();
        if (value instanceof List<?> list) {
            for (Object o : list) if (o instanceof Entity e) return e.getUUID();
            return NIL_UUID;
        }
        String s = ParamEval.asString(value).trim();
        if (s.isEmpty()) return NIL_UUID;
        if (SelectorValidation.isUuid(s)) return UUID.fromString(s);
        Entity e = SelectorExecutor.resolveSingle(s, r);
        return e == null ? NIL_UUID : e.getUUID();
    }
}
