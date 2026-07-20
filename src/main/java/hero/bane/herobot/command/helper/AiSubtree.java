package hero.bane.herobot.command.helper;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import hero.bane.herobot.ai.AiHexCodec;
import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptRegistry;
import hero.bane.herobot.ai.runtime.ScriptRunner;
import hero.bane.herobot.bot.BotPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

public final class AiSubtree {
    private AiSubtree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        SuggestionProvider<CommandSourceStack> scriptSuggest = (ctx, builder) ->
                SharedSuggestionProvider.suggest(
                        AiScriptRegistry.list(ctx.getSource().getServer()),
                        builder);

        return Commands.literal("ai")
                .then(Commands.literal("set")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests(scriptSuggest)
                                .executes(ctx -> aiSet(ctx, false))
                                .then(Commands.literal("run")
                                        .executes(ctx -> aiSet(ctx, true)))))
                .then(Commands.literal("hex")
                        .then(Commands.literal("set")
                                .then(Commands.argument("hex", StringArgumentType.string())
                                        .executes(ctx -> aiHexSet(ctx, false))
                                        .then(Commands.literal("run")
                                                .executes(ctx -> aiHexSet(ctx, true))))))
                .then(Commands.literal("clear")
                        .executes(AiSubtree::aiClear))
                .then(Commands.literal("run")
                        .executes(AiSubtree::aiRun))
                .then(Commands.literal("stop")
                        .executes(AiSubtree::aiStop))
                .then(Commands.literal("pause")
                        .executes(AiSubtree::aiPause))
                .then(Commands.literal("resume")
                        .executes(AiSubtree::aiResume))
                .then(Commands.literal("status")
                        .executes(AiSubtree::aiStatus));
    }

    private static int aiSet(CommandContext<CommandSourceStack> context, boolean run) throws CommandSyntaxException {
        String fileName = StringArgumentType.getString(context, "file");
        AiScript script;
        try {
            script = AiScriptRegistry.load(context.getSource().getServer(), fileName);
        } catch (IOException e) {
            context.getSource().sendFailure(Component.literal("Failed to load script: " + e.getMessage()));
            return 0;
        }
        if (script == null) {
            context.getSource().sendFailure(Component.literal("Script not found: " + fileName));
            return 0;
        }
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            AiScriptRegistry.assign(player, fileName, script);
            context.getSource().sendSuccess(
                    () -> Component.literal("Assigned AI script '" + fileName + "' to " + player.getGameProfile().name()),
                    false);
            if (run) {
                AiScriptRegistry.fireStart(player);
                context.getSource().sendSuccess(
                        () -> Component.literal("Fired START on " + player.getGameProfile().name()),
                        false);
            }
        }
        return 1;
    }

    private static int aiHexSet(CommandContext<CommandSourceStack> context, boolean run) throws CommandSyntaxException {
        String hex = StringArgumentType.getString(context, "hex");
        AiScript script;
        try {
            script = AiHexCodec.decode(hex, "hex");
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Invalid AI hex: " + e.getMessage()));
            return 0;
        }
        String name = script.name() == null || script.name().isBlank() ? "hex" : script.name();
        AiScriptRegistry.put(name, script);
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            AiScriptRegistry.assign(player, name, script);
            context.getSource().sendSuccess(
                    () -> Component.literal("Assigned AI script '" + name + "' (from hex) to " + player.getGameProfile().name()),
                    false);
            if (run) {
                AiScriptRegistry.fireStart(player);
                context.getSource().sendSuccess(
                        () -> Component.literal("Fired START on " + player.getGameProfile().name()),
                        false);
            }
        }
        return 1;
    }

    private static int aiClear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            AiScriptRegistry.clear(player);
            context.getSource().sendSuccess(
                    () -> Component.literal("Cleared AI script on " + player.getGameProfile().name()),
                    false);
        }
        return 1;
    }

    private static int aiRun(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            ScriptRunner r = AiScriptRegistry.runner(player);
            if (r == null) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " has no AI script assigned"));
                continue;
            }
            if (r.isPaused()) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name()
                                + " is paused - use 'ai resume' to carry on, or 'ai stop' first to restart"));
                continue;
            }
            AiScriptRegistry.fireStart(player);
            context.getSource().sendSuccess(
                    () -> Component.literal("Fired START on " + player.getGameProfile().name()),
                    false);
            count++;
        }
        return count;
    }

    private static int aiStop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            ScriptRunner r = AiScriptRegistry.runner(player);
            if (r == null) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " has no AI script assigned"));
                continue;
            }
            AiScriptRegistry.stopRunning(player);
            context.getSource().sendSuccess(
                    () -> Component.literal("Stopped AI script on " + player.getGameProfile().name()
                            + " (still loaded)"),
                    false);
            count++;
        }
        return count;
    }

    private static int aiPause(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            ScriptRunner r = AiScriptRegistry.runner(player);
            if (r == null) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " has no AI script assigned"));
                continue;
            }
            if (r.isPaused()) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " is already paused"));
                continue;
            }
            AiScriptRegistry.pause(player);
            context.getSource().sendSuccess(
                    () -> Component.literal("Paused AI script on " + player.getGameProfile().name()),
                    false);
            count++;
        }
        return count;
    }

    private static int aiResume(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            ScriptRunner r = AiScriptRegistry.runner(player);
            if (r == null) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " has no AI script assigned"));
                continue;
            }
            if (!r.isPaused()) {
                context.getSource().sendFailure(
                        Component.literal(player.getGameProfile().name() + " is not paused"));
                continue;
            }
            AiScriptRegistry.resume(player);
            context.getSource().sendSuccess(
                    () -> Component.literal("Resumed AI script on " + player.getGameProfile().name()),
                    false);
            count++;
        }
        return count;
    }

    private static int aiStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            ScriptRunner r = AiScriptRegistry.runner(player);
            int branches = r == null ? 0 : r.branchCount();

            String name = player instanceof BotPlayer bot ? bot.getAssignedScriptName()
                    : (r == null ? null : "<assigned>");
            String playerName = player.getGameProfile().name();
            String paused = r != null && r.isPaused() ? " (paused)" : "";
            context.getSource().sendSuccess(
                    () -> Component.literal(playerName + ": " +
                            (name == null ? "<no script>" : "'" + name + "'") +
                            " - " + branches + " active branch" + (branches == 1 ? "" : "es") + paused),
                    false);
        }
        return 1;
    }
}
