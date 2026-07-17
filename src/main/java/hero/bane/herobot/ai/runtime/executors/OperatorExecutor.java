package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.expr.BoolEval;
import hero.bane.herobot.ai.expr.ExprEval;
import hero.bane.herobot.ai.expr.StrEval;
import hero.bane.herobot.ai.expr.VecEval;
import hero.bane.herobot.ai.runtime.Branch;
import hero.bane.herobot.ai.runtime.ParamEval;
import hero.bane.herobot.ai.runtime.Reporter;
import hero.bane.herobot.ai.runtime.RuntimeVariable;
import hero.bane.herobot.ai.runtime.ScriptRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class OperatorExecutor {
    private OperatorExecutor() {}

    public static void register(Map<BlockType, Reporter> reporter) {
        reporter.put(BlockType.NUM_CALC, (b, r, br) -> {
            String expr = ParamEval.evalString(b, "expression", r, br);
            return ExprEval.eval(expr, (name, idx) -> {
                Object raw;
                if (ExprEval.isInputRef(name)) {
                    raw = ParamEval.raw(b, name, r, br);
                } else {
                    RuntimeVariable v = r.variable(name);
                    raw = v == null ? null : v.value();
                }
                if (idx >= 0) {
                    if (raw instanceof Vec3 v) return idx == 0 ? v.x : idx == 1 ? v.y : idx == 2 ? v.z : 0;
                    if (raw instanceof float[] f) return idx < f.length ? f[idx] : 0;
                    return 0;
                }
                return ParamEval.asDouble(raw);
            });
        });
        reporter.put(BlockType.POS_CALC, (b, r, br) -> {
            String expr = ParamEval.evalString(b, "expression", r, br);
            double[] v = VecEval.eval(expr, 3, vecVars(b, r, br), levelWorld(r));
            return new Vec3(v[0], v[1], v[2]);
        });
        reporter.put(BlockType.DIR_CALC, (b, r, br) -> {
            String expr = ParamEval.evalString(b, "expression", r, br);
            double[] v = VecEval.eval(expr, 2, vecVars(b, r, br), levelWorld(r));
            return new float[]{(float) v[0], (float) v[1]};
        });
        reporter.put(BlockType.STRING_CALC, (b, r, br) -> {
            String expr = ParamEval.evalString(b, "expression", r, br);
            return StrEval.eval(expr, name -> {
                if (ExprEval.isInputRef(name)) return DataExecutor.stringify(ParamEval.raw(b, name, r, br), r);
                RuntimeVariable v = r.variable(name);
                return v == null ? "" : DataExecutor.stringify(v.value(), r);
            });
        });
        reporter.put(BlockType.BOOL_CALC, (b, r, br) -> {
            String expr = ParamEval.evalString(b, "expression", r, br);
            return BoolEval.eval(expr, name -> {
                if (ExprEval.isInputRef(name)) return ParamEval.raw(b, name, r, br);
                RuntimeVariable v = r.variable(name);
                return v == null ? null : v.value();
            });
        });
        reporter.put(BlockType.TERNARY, (b, r, br) -> {
            boolean cond = ParamEval.evalBool(b, "condition", r, br);
            return ParamEval.raw(b, cond ? "trueValue" : "falseValue", r, br);
        });
        reporter.put(BlockType.COMPARE, (b, r, br) -> {
            double a = ParamEval.evalDouble(b, "a", r, br);
            double bv = ParamEval.evalDouble(b, "b", r, br);
            return switch (ParamEval.evalString(b, "op", r, br)) {
                case ">" -> a > bv;
                case "=" -> a == bv;
                case "≠" -> a != bv;
                case "≤" -> a <= bv;
                case "≥" -> a >= bv;
                default -> a < bv;
            };
        });
        reporter.put(BlockType.EQUALITY, (b, r, br) -> {
            boolean eq = valuesEqual(ParamEval.raw(b, "a", r, br), ParamEval.raw(b, "b", r, br));
            return "≠".equals(ParamEval.evalString(b, "op", r, br)) ? !eq : eq;
        });
        reporter.put(BlockType.LOGIC, (b, r, br) -> {
            boolean a = ParamEval.evalBool(b, "a", r, br);
            boolean bv = ParamEval.evalBool(b, "b", r, br);
            return switch (ParamEval.evalString(b, "op", r, br)) {
                case "or" -> a || bv;
                case "xor" -> a ^ bv;
                default -> a && bv;
            };
        });
        reporter.put(BlockType.AND,(b, r, br) -> ParamEval.evalBool(b, "a", r, br) && ParamEval.evalBool(b, "b", r, br));
        reporter.put(BlockType.OR, (b, r, br) -> ParamEval.evalBool(b, "a", r, br) || ParamEval.evalBool(b, "b", r, br));
        reporter.put(BlockType.NOT,(b, r, br) -> !ParamEval.evalBool(b, "a", r, br));

        reporter.put(BlockType.RANDOM_INT, (b, r, br) -> {
            int lo = ParamEval.evalInt(b, "min", r, br);
            int hi = ParamEval.evalInt(b, "max", r, br);
            if (hi < lo) { int t = lo; lo = hi; hi = t; }
            // Widened: hi + 1 overflows to MIN_VALUE when max is Integer.MAX_VALUE.
            return (int) ThreadLocalRandom.current().nextLong(lo, (long) hi + 1);
        });
        reporter.put(BlockType.RANDOM_DOUBLE, (b, r, br) -> {
            double lo = ParamEval.evalDouble(b, "min", r, br);
            double hi = ParamEval.evalDouble(b, "max", r, br);
            if (hi < lo) { double t = lo; lo = hi; hi = t; }
            return lo + (hi - lo) * ThreadLocalRandom.current().nextDouble();
        });
        reporter.put(BlockType.VEC3, (b, r, br) -> new Vec3(
                ParamEval.evalDouble(b, "x", r, br),
                ParamEval.evalDouble(b, "y", r, br),
                ParamEval.evalDouble(b, "z", r, br)));
        reporter.put(BlockType.ROT, (b, r, br) -> new float[]{
                (float) ParamEval.evalDouble(b, "yaw", r, br),
                (float) ParamEval.evalDouble(b, "pitch", r, br)
        });
    }

    private static Function<String, Object> vecVars(BlockInstance b, ScriptRunner r, Branch br) {
        return name -> {
            Object raw;
            if (ExprEval.isInputRef(name)) {
                raw = ParamEval.raw(b, name, r, br);
            } else {
                RuntimeVariable v = r.variable(name);
                raw = v == null ? null : v.value();
            }
            return raw instanceof Vec3 v ? new double[]{v.x, v.y, v.z} : raw;
        };
    }

    private static VecEval.World levelWorld(ScriptRunner r) {
        Level level = r.player().level();
        return new VecEval.World() {
            @Override
            public boolean isAir(int x, int y, int z) {
                return level.getBlockState(new BlockPos(x, y, z)).isAir();
            }

            @Override
            public int minY() {
                return level.getMinY();
            }

            @Override
            public int maxY() {
                return level.getMaxY();
            }

            @Override
            public double base(int axis, boolean rotation) {
                if (rotation) return axis == 0 ? r.player().getYRot() : r.player().getXRot();
                return axis == 0 ? r.player().getX() : axis == 1 ? r.player().getY() : r.player().getZ();
            }
        };
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ParamEval.asDouble(a) == ParamEval.asDouble(b);
        }
        if (a instanceof ItemStack ia && b instanceof ItemStack ib) {
            return ItemStack.isSameItemSameComponents(ia, ib) && ia.getCount() == ib.getCount();
        }
        if (a instanceof float[] fa && b instanceof float[] fb) {
            return Arrays.equals(fa, fb);
        }
        return Objects.equals(a, b);
    }
}
