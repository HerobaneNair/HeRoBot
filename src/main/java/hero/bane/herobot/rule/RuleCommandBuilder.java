package hero.bane.herobot.rule;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RuleCommandBuilder {

    private enum Permanence { TEMP, PERM }
    private enum Scope { WORLD, CLIENT }

    public static ArgumentBuilder<CommandSourceStack, ?> build() {
        return argument("rule", StringArgumentType.word())
                .suggests((c, b) -> {
                    String remaining = b.getRemaining().toLowerCase();
                    for (String name : RuleRegistry.all().keySet()) {
                        if (name.startsWith(remaining)) b.suggest(name);
                    }
                    return b.buildFuture();
                })
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
                    }
                    return b.buildFuture();
                })
                .executes(c -> apply(c, Permanence.TEMP, Scope.CLIENT))
                .then(literal("temp").executes(c -> apply(c, Permanence.TEMP, Scope.WORLD))
                        .then(literal("world").executes(c -> apply(c, Permanence.TEMP, Scope.WORLD)))
                        .then(literal("client").executes(c -> apply(c, Permanence.TEMP, Scope.CLIENT))))
                .then(literal("perm").executes(c -> apply(c, Permanence.PERM, Scope.WORLD))
                        .then(literal("world").executes(c -> apply(c, Permanence.PERM, Scope.WORLD)))
                        .then(literal("client").executes(c -> apply(c, Permanence.PERM, Scope.CLIENT))));
    }

    private static int apply(CommandContext<CommandSourceStack> c, Permanence perm, Scope scope) {
        RuleEntry rule = RuleRegistry.get(StringArgumentType.getString(c, "rule"));
        if (rule == null) return 0;

        Object value;
        try {
            value = parseValue(rule, StringArgumentType.getString(c, "value"));
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.getMessage() == null ? "Invalid value" : e.getMessage()));
            return 0;
        }

        boolean saved = false;

        if (perm == Permanence.TEMP) {
            RuleConfigIO.setTemp(rule.name, value);
        } else {
            if (scope == Scope.CLIENT) {
                RuleConfigIO.setPermClient(rule.name, value);
                saved = true;
            } else {
                saved = RuleConfigIO.setPermWorld(rule.name, value);
                if (!saved) {
                    c.getSource().sendFailure(Component.literal("No world loaded, cannot write per-world rules"));
                    return 0;
                }
            }
        }

        reply(c.getSource(), rule, value, perm, scope, saved);
        return 1;
    }

    private static Object parseValue(RuleEntry rule, String input) {
        if (rule.type == boolean.class) return Boolean.parseBoolean(input);
        if (rule.type == int.class) return Integer.parseInt(input);
        if (rule.type == double.class) {
            double v = Double.parseDouble(input);

            if (rule.name.equals("creativeFlyDrag")) {
                if (v < 0.0 || v > 1.0) throw new IllegalArgumentException("creativeFlyDrag must be within the range 0-1");
            } else if (rule.name.equals("creativeFlySpeed")) {
                if (v < 0.0) throw new IllegalArgumentException("creativeFlySpeed must be nonnegative");
            }

            return v;
        }
        if (rule.type.isEnum()) return parseEnum(rule.type, input.toUpperCase());
        throw new IllegalStateException();
    }

    private static void reply(CommandSourceStack src, RuleEntry rule, Object v, Permanence perm, Scope scope, boolean saved) {
        int tagColor = perm == Permanence.PERM ? 0xAAFFFF : 0xFFFFAA;
        String permTag = perm == Permanence.PERM ? "perm" : "temp";
        String scopeTag = scope == Scope.WORLD ? "world" : "client";

        src.sendSuccess(
                () -> Component.literal(rule.name + " = " + v)
                        .append(Component.literal(" [" + permTag + " " + scopeTag + "]")
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