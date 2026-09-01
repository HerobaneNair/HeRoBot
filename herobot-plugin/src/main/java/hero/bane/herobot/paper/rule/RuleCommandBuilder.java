package hero.bane.herobot.paper.rule;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import hero.bane.herobot.common.rule.Bounds;
import hero.bane.herobot.common.rule.RuleEntry;
import hero.bane.herobot.common.rule.RuleRegistry;

public final class RuleCommandBuilder {

    private enum Permanence { TEMP, PERM }

    private static final PermissionCheck PERMISSION_CHECK =
            new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    private RuleCommandBuilder() {
    }

    public static ArgumentBuilder<CommandSourceStack, ?> build() {
        return argument("rule", StringArgumentType.word())
                .suggests((c, b) -> {
                    String remaining = b.getRemaining().toLowerCase();
                    for (String name : RuleRegistry.all().keySet()) {
                        if (name.startsWith(remaining)) b.suggest(name);
                    }
                    return b.buildFuture();
                })
                .requires(Commands.hasPermission(PERMISSION_CHECK))
                .executes(c -> {
                    RuleEntry rule = RuleRegistry.get(StringArgumentType.getString(c, "rule"));
                    if (rule == null) return 0;

                    c.getSource().sendSuccess(
                            () -> Component.literal(rule.name + " = " + rule.get() + "\n")
                                    .append(Component.literal(rule.description)
                                            .withStyle(s -> s.withColor(TextColor.fromRgb(0xFFFFAA)))),
                            false
                    );
                    return 1;
                })
                .then(literal("reset")
                        .executes(c -> reset(c, Permanence.TEMP))
                        .then(literal("temp").executes(c -> reset(c, Permanence.TEMP)))
                        .then(literal("perm").executes(c -> reset(c, Permanence.PERM))))
                .then(buildValueNode());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> buildValueNode() {
        return argument("value", StringArgumentType.word())
                .suggests((c, b) -> {
                    RuleEntry rule = RuleRegistry.get(StringArgumentType.getString(c, "rule"));
                    if (rule == null) return b.buildFuture();

                    if (rule.type == boolean.class) {
                        b.suggest("true");
                        b.suggest("false");
                    } else if (rule.type.isEnum()) {
                        for (String s : enumNames(rule.type)) b.suggest(s);
                    } else {
                        if (rule.hasBounds()) {
                            Bounds bounds = rule.bounds;
                            String boundsHint = boundsString(bounds);
                            b.suggest(String.valueOf(rule.getDefaultValue()), Component.literal(boundsHint));
                        } else {
                            b.suggest(String.valueOf(rule.getDefaultValue()));
                        }
                    }
                    return b.buildFuture();
                })
                .executes(c -> apply(c, Permanence.TEMP))
                .then(literal("temp").executes(c -> apply(c, Permanence.TEMP)))
                .then(literal("perm").executes(c -> apply(c, Permanence.PERM)));
    }

    private static int reset(CommandContext<CommandSourceStack> c, Permanence perm) {
        RuleEntry rule = RuleRegistry.get(StringArgumentType.getString(c, "rule"));
        if (rule == null) return 0;

        Object defaultValue = rule.getDefaultValue();
        rule.resetToDefault();

        if (!store(c, rule, defaultValue, perm)) return 0;

        reply(c.getSource(), rule, defaultValue, perm);
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> c, Permanence perm) {
        RuleEntry rule = RuleRegistry.get(StringArgumentType.getString(c, "rule"));
        if (rule == null) return 0;

        Object value;
        try {
            value = parseValue(rule, StringArgumentType.getString(c, "value"));
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.getMessage() == null ? "Invalid value" : e.getMessage()));
            return 0;
        }

        if (!store(c, rule, value, perm)) return 0;

        reply(c.getSource(), rule, value, perm);
        return 1;
    }

    private static boolean store(CommandContext<CommandSourceStack> c, RuleEntry rule, Object value, Permanence perm) {
        if (perm == Permanence.TEMP) {
            RuleConfigIO.setTemp(rule.name, value);
            return true;
        }
        if (RuleConfigIO.setPermWorld(rule.name, value)) return true;

        c.getSource().sendFailure(Component.literal("No world loaded, cannot write per-world rules"));
        return false;
    }

    private static Object parseValue(RuleEntry rule, String input) {
        if (rule.type == boolean.class) return Boolean.parseBoolean(input);
        if (rule.type == int.class) {
            int v = Integer.parseInt(input);
            if (rule.hasBounds()) {
                Bounds b = rule.bounds;
                int min = (int) b.min();
                int max = (int) b.max();
                if (v < min || v > max) {
                    throw new IllegalArgumentException(rule.name + " must be in range " + boundsString(b));
                }
            }
            return v;
        }
        if (rule.type == float.class) {
            float v = Float.parseFloat(input);
            if (rule.hasBounds()) {
                Bounds b = rule.bounds;
                if (v < b.min() || v > b.max()) {
                    throw new IllegalArgumentException(rule.name + " must be in range " + boundsString(b));
                }
            }
            return v;
        }
        if (rule.type == double.class) {
            double v = Double.parseDouble(input);
            if (rule.hasBounds()) {
                Bounds b = rule.bounds;
                if (v < b.min() || v > b.max()) {
                    throw new IllegalArgumentException(rule.name + " must be in range " + boundsString(b));
                }
            }
            return v;
        }
        if (rule.type.isEnum()) return parseEnum(rule.type, input.toUpperCase());
        throw new IllegalStateException();
    }

    private static String boundsString(Bounds b) {
        String min = b.min() == Double.NEGATIVE_INFINITY ? "-∞" : formatBound(b.min());
        String max = b.max() == Double.POSITIVE_INFINITY ? "∞" : formatBound(b.max());
        return "[" + min + ", " + max + "]";
    }

    private static String formatBound(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static void reply(CommandSourceStack src, RuleEntry rule, Object v, Permanence perm) {
        int tagColor = perm == Permanence.PERM ? 0xAAFFFF : 0xFFFFAA;
        String permTag = perm == Permanence.PERM ? "perm" : "temp";

        src.sendSuccess(
                () -> Component.literal(rule.name + " = " + v)
                        .append(Component.literal(" [" + permTag + "]")
                                .withStyle(s -> s.withColor(TextColor.fromRgb(tagColor)))),
                false
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T parseEnum(Class<?> type, String value) {
        return Enum.valueOf((Class<T>) type, value);
    }

    private static String[] enumNames(Class<?> type) {
        Object[] constants = type.getEnumConstants();
        String[] out = new String[constants.length];
        for (int i = 0; i < constants.length; i++) out[i] = ((Enum<?>) constants[i]).name().toLowerCase();
        return out;
    }
}
