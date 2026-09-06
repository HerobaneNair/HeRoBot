package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import hero.bane.herobot.common.rule.HeroBotSettings;
import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.common.bot.Shadows;
import hero.bane.herobot.common.ping.PingBurstSpec;
import hero.bane.herobot.common.ping.PingDelayOptions;
import hero.bane.herobot.common.ping.PingDelaySpec;
import hero.bane.herobot.common.ping.PingMode;
import hero.bane.herobot.common.ping.PingRange;
import hero.bane.herobot.common.ping.PingDelays;
import hero.bane.herobot.mod.common.ai.AiScriptRegistry;
import hero.bane.herobot.mod.common.bot.BotChat;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.mod.common.command.helper.*;
import hero.bane.herobot.mod.common.control.PlayerController;
import hero.bane.herobot.mod.common.control.PlayerControllers;
import hero.bane.herobot.mod.common.ping.PingBoostHandler;
import hero.bane.herobot.mod.common.ping.PingBoosters;
import hero.bane.herobot.mod.common.util.BlockBreakTasks;
import hero.bane.herobot.mod.common.util.BlockBreaker;
import hero.bane.herobot.mod.common.util.BlockPlacer;
import hero.bane.herobot.mod.common.util.ItemCooldown;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class PlayerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        EntityArgument targetsArg = EntityArgument.players();
        dispatcher.register(
                Commands.literal("player")
                        .requires(s -> !s.isPlayer() || s.getServer().getPlayerList().isOp(Objects.requireNonNull(s.getPlayer()).nameAndId()))
                        .then(Commands.argument("targets", targetsArg)
                                .suggests((c, b) -> mergedTargetSuggestions(targetsArg, c, b))

                                .then(Commands.literal("stop")
                                        .executes(c -> {
                                            AiSubtree.stopQuietly(c);
                                            for (ServerPlayer p : CommandHelper.requireControllableTargets(c)) {
                                                BlockBreakTasks.cancel(p);
                                            }
                                            return CommandHelper.control(c, PlayerController::stopAll);
                                        }))

                                .then(makeActionCommand("use", ActionType.USE)
                                        .then(Commands.literal("twice")
                                                .executes(CommandHelper.control(ap -> ap.start(ActionType.USE, Action.once(2))))))
                                .then(makeActionCommand("swing", ActionType.SWING))
                                .then(makeActionCommand("jump", ActionType.JUMP))
                                .then(makeActionCommand("attack", ActionType.ATTACK)
                                        .then(Commands.literal("twice")
                                                .executes(CommandHelper.control(ap -> ap.start(ActionType.ATTACK, Action.once(2))))))
                                .then(makeActionCommand("drop", ActionType.DROP_ITEM))
                                .then(makeActionCommand("dropStack", ActionType.DROP_STACK))
                                .then(makeActionCommand("swapHands", ActionType.SWAP_HANDS))
                                .then(makePlaceCommand())
                                .then(makeBreakCommand())

                                .then(Commands.literal("itemCd")
                                        .executes(ItemCooldown::itemCdClearAll)
                                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                                .executes(ItemCooldown::itemCdAsk)
                                                .then(Commands.literal("reset")
                                                        .executes(ItemCooldown::itemCdReset))
                                                .then(Commands.literal("set")
                                                        .executes(ItemCooldown::itemCdSetDefault)
                                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                                .executes(ItemCooldown::itemCdSetCustom)))))

                                .then(Commands.literal("hotbar")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 9))
                                                .executes(c -> CommandHelper.control(c,
                                                        ap -> ap.setSlot(IntegerArgumentType.getInteger(c, "slot"))))))

                                .then(Commands.literal("pickBlock")
                                        .executes(CommandHelper.control(ap -> ap.pickBlock(false)))
                                        .then(Commands.literal("withData")
                                                .executes(CommandHelper.control(ap -> ap.pickBlock(true)))))

                                .then(Commands.literal("msg")
                                        .then(Commands.argument("msg", StringArgumentType.greedyString())
                                                .executes(PlayerCommand::message)))

                                .then(Commands.literal("kill")
                                        .executes(PlayerCommand::kill))
                                .then(Commands.literal("disconnect")
                                        .executes(PlayerCommand::disconnect))

                                .then(Commands.literal("sneak")
                                        .executes(CommandHelper.control(ap -> ap.setSneaking(true))))
                                .then(Commands.literal("unsneak")
                                        .executes(CommandHelper.control(ap -> ap.setSneaking(false))))
                                .then(Commands.literal("sprint")
                                        .executes(CommandHelper.control(ap -> ap.setSprinting(true))))
                                .then(Commands.literal("unsprint")
                                        .executes(CommandHelper.control(ap -> ap.setSprinting(false))))

                                .then(Commands.literal("move")
                                        .executes(CommandHelper.controlAndStopPath(PlayerController::stopMovement))
                                        .then(Commands.literal("forward")
                                                .executes(CommandHelper.controlAndStopPath(ap -> ap.setForward(1))))
                                        .then(Commands.literal("backward")
                                                .executes(CommandHelper.controlAndStopPath(ap -> ap.setForward(-1))))
                                        .then(Commands.literal("left")
                                                .executes(CommandHelper.controlAndStopPath(ap -> ap.setStrafing(1))))
                                        .then(Commands.literal("right")
                                                .executes(CommandHelper.controlAndStopPath(ap -> ap.setStrafing(-1)))))

                                .then(LookSubtree.build())

                                .then(SoundSubtree.build())

                                .then(Commands.literal("shadow")
                                        .executes(c -> shadowSet(c, null))
                                        .then(Commands.literal("reset")
                                                .executes(PlayerCommand::shadowReset))
                                        .then(Commands.argument("ai", StringArgumentType.string())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        AiScriptRegistry.list(c.getSource().getServer()), b))
                                                .executes(c -> shadowSet(c, StringArgumentType.getString(c, "ai")))))

                                .then(Commands.literal("ping")
                                        .executes(PlayerCommand::pingReport)
                                        .then(Commands.literal("settings")
                                                .executes(PlayerCommand::pingSettingsGet)
                                                .then(Commands.argument("category", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            for (PingDelayOptions.Category value : PingDelayOptions.Category.values()) {
                                                                b.suggest(value.id());
                                                            }
                                                            return b.buildFuture();
                                                        })
                                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                                .executes(PlayerCommand::pingSettingsSet))))
                                        .then(Commands.literal("reset")
                                                .executes(PlayerCommand::pingReset))
                                        .then(Commands.literal("delay")
                                                .executes(PlayerCommand::pingDelayReport)
                                                .then(Commands.argument("amount", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("reset");
                                                            b.suggest("0");
                                                            b.suggest("50");
                                                            b.suggest("100");
                                                            b.suggest("150");
                                                            b.suggest("200");
                                                            b.suggest("100-150");
                                                            return b.buildFuture();
                                                        })
                                                        .executes(c -> pingDelaySet(c, PingMode.BALANCE))
                                                        .then(Commands.literal("balance")
                                                                .executes(c -> pingDelaySet(c, PingMode.BALANCE)))
                                                        .then(Commands.literal("add")
                                                                .executes(c -> pingDelaySet(c, PingMode.ADD)))))
                                        .then(Commands.literal("burst")
                                                .executes(PlayerCommand::pingBurstReport)
                                                .then(Commands.argument("length", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("reset");
                                                            b.suggest("0");
                                                            b.suggest("10");
                                                            b.suggest("30");
                                                            b.suggest("10-20");
                                                            return b.buildFuture();
                                                        })
                                                        .executes(c -> pingBurstSet(c, false))
                                                        .then(Commands.argument("interval", StringArgumentType.word())
                                                                .suggests((c, b) -> {
                                                                    b.suggest("20");
                                                                    b.suggest("40");
                                                                    b.suggest("25-30");
                                                                    return b.buildFuture();
                                                                })
                                                                .executes(c -> pingBurstSet(c, true))))))

                                .then(Commands.literal("copycat")
                                        .then(Commands.argument("source", EntityArgument.player())
                                                .executes(context -> {
                                                    for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
                                                        bot.copycat(EntityArgument.getPlayer(context, "source"));
                                                    }
                                                    return 1;
                                                })))

                                .then(SkinSubtree.build())

                                .then(Commands.literal("autojump")
                                        .executes(CommandHelper.control(PlayerController::attemptAutoJump))
                                        .then(Commands.literal("true")
                                                .executes(c -> autoJump(c, true)))
                                        .then(Commands.literal("false")
                                                .executes(c -> autoJump(c, false))))

                                .then(Commands.literal("handedness")
                                        .then(Commands.literal("left")
                                                .executes(c -> setHandedness(c, true)))
                                        .then(Commands.literal("right")
                                                .executes(c -> setHandedness(c, false))))

                                .then(InventorySubtree.buildInventory(ctx))
                                .then(InventorySubtree.buildContainer(ctx))

                                .then(TradeSubtree.build())

                                .then(PathSubtree.build(ctx))

                                .then(AiSubtree.build())

                                .then(PlayerSpawnCommand.spawnSubtree())
                        )
        );
    }

    private static CompletableFuture<Suggestions> mergedTargetSuggestions(
            EntityArgument targetsArg,
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        CompletableFuture<Suggestions> entitySuggestions = targetsArg.listSuggestions(context, builder);
        SuggestionsBuilder nameBuilder = builder.createOffset(builder.getStart());
        SharedSuggestionProvider.suggest(PlayerSpawnCommand.getNameSuggestions(context.getSource()), nameBuilder);
        return entitySuggestions.thenCombine(nameBuilder.buildFuture(),
                (entity, names) -> Suggestions.merge(context.getInput(), List.of(entity, names)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makePlaceCommand() {
        var pos = Commands.argument("position", Vec3Argument.vec3())
                .executes(c -> doPlace(c, "any", false))
                .then(Commands.literal("force").executes(c -> doPlace(c, "any", true)));
        for (String face : BlockDefRegistry.FACES) {
            pos.then(Commands.literal(face)
                    .executes(c -> doPlace(c, face, false))
                    .then(Commands.literal("force").executes(c -> doPlace(c, face, true))));
        }
        return Commands.literal("place").then(pos);
    }

    private static int doPlace(CommandContext<CommandSourceStack> c, String face, boolean force) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(c, "position");
        int placed = 0;
        for (ServerPlayer p : CommandHelper.requireControllableTargets(c)) {
            if (BlockPlacer.place(p, pos, face, force)) placed++;
        }
        return placed;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeBreakCommand() {
        var pos = Commands.argument("position", Vec3Argument.vec3())
                .executes(c -> doBreak(c, false))
                .then(Commands.literal("force").executes(c -> doBreak(c, true)));
        return Commands.literal("break").then(pos);
    }

    private static int doBreak(CommandContext<CommandSourceStack> c, boolean force) throws CommandSyntaxException {
        Vec3 pos = Vec3Argument.getVec3(c, "position");
        int started = 0;
        for (ServerPlayer p : CommandHelper.requireControllableTargets(c)) {
            if (BlockBreaker.start(p, pos, force)) started++;
        }
        return started;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeActionCommand(String name, ActionType type) {
        return Commands.literal(name)
                .executes(CommandHelper.control(ap -> ap.stop(type)))
                .then(Commands.literal("once")
                        .executes(CommandHelper.control(ap -> ap.start(type, Action.once()))))
                .then(Commands.literal("continuous")
                        .executes(CommandHelper.control(ap -> ap.start(type, Action.continuous())))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                            int ticks = IntegerArgumentType.getInteger(c, "ticks");
                                            return CommandHelper.control(c,
                                                    ap -> ap.startOrExtender(type, ticks));
                                        }
                                )))
                .then(Commands.literal("interval")
                        .then(Commands.argument("interval", IntegerArgumentType.integer(1))
                                .executes(c -> CommandHelper.control(c,
                                        ap -> ap.start(type,
                                                Action.interval(IntegerArgumentType.getInteger(c, "interval")))))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(c -> CommandHelper.control(c,
                                                ap -> ap.start(type,
                                                        Action.interval(
                                                                IntegerArgumentType.getInteger(c, "interval"),
                                                                IntegerArgumentType.getInteger(c, "ticks"))))))));
    }

    private static int message(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String message = StringArgumentType.getString(context, "msg");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            BotChat.send(bot, message, true);
        }
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context))
            bot.kill(bot.level());
        return 1;
    }

    private static int disconnect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.ping = 0;
            bot.botPlayerDisconnect(Component.literal(""));
        }
        return 1;
    }

    private static PingRange readRange(CommandContext<CommandSourceStack> context, String argument) {
        String raw = StringArgumentType.getString(context, argument);
        if (raw.equalsIgnoreCase("reset") || raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("none")) {
            return PingRange.ZERO;
        }
        return PingRange.parse(raw);
    }

    private static int pingDelaySet(CommandContext<CommandSourceStack> context, PingMode mode)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "amount");
        PingRange range = readRange(context, "amount");
        if (range == null) {
            context.getSource().sendFailure(Component.literal(
                    "Could not read a ping delay from '" + raw + "'; expected 100, 100-150 or reset"));
            return 0;
        }

        PingDelaySpec spec = new PingDelaySpec(range, mode);
        int first = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();

            if (player instanceof BotPlayer bot) {
                bot.setPingSpec(spec);
                context.getSource().sendSuccess(() -> Component.literal(spec.isActive()
                        ? "Set " + name + "'s ping delay to " + spec.describe()
                        : "Cleared " + name + "'s ping delay"), false);
                if (first == 0) first = spec.averageMs();
                continue;
            }

            if (!spec.isActive()) {
                PingBoosters.setDelay(player, PingDelaySpec.NONE);
                context.getSource().sendSuccess(() -> Component.literal("Cleared " + name + "'s ping delay"), false);
                continue;
            }

            int real = player.connection.latency();
            if (!PingBoosters.setDelay(player, spec)) {
                context.getSource().sendFailure(Component.literal("Could not set " + name + "'s ping delay"));
                continue;
            }

            PingBoostHandler handler = PingBoosters.handlerOf(player);
            int added = handler == null ? 0 : handler.averageAddedMs();
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set " + name + "'s ping delay to " + spec.describe()
                            + " (real " + real + "ms, +" + added + "ms on average)"), false);
            if (first == 0) first = spec.averageMs();
        }
        return first;
    }

    private static int pingBurstSet(CommandContext<CommandSourceStack> context, boolean hasInterval)
            throws CommandSyntaxException {
        String rawLength = StringArgumentType.getString(context, "length");
        PingRange length = readRange(context, "length");
        if (length == null) {
            context.getSource().sendFailure(Component.literal(
                    "Could not read a burst length from '" + rawLength + "'; expected 30, 10-20 or reset"));
            return 0;
        }

        PingRange interval = null;
        if (hasInterval && !length.isZero()) {
            String rawInterval = StringArgumentType.getString(context, "interval");
            interval = readRange(context, "interval");
            if (interval == null) {
                context.getSource().sendFailure(Component.literal(
                        "Could not read a burst interval from '" + rawInterval + "'; expected 40 or 25-30"));
                return 0;
            }
        }

        PingBurstSpec spec = new PingBurstSpec(length, interval);
        int first = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();

            if (player instanceof BotPlayer bot) {
                bot.setBurstSpec(spec);
                context.getSource().sendSuccess(() -> Component.literal(spec.isActive()
                        ? "Set " + name + "'s ping burst to " + spec.describe()
                                + "\n Bots hold their attack, use and knockback actions rather than packets"
                        : "Cleared " + name + "'s ping burst"), false);
                if (first == 0) first = spec.averageAddedMs();
                continue;
            }

            if (!spec.isActive()) {
                PingBoosters.setBurst(player, PingBurstSpec.NONE);
                context.getSource().sendSuccess(() -> Component.literal("Cleared " + name + "'s ping burst"), false);
                continue;
            }

            if (!PingBoosters.setBurst(player, spec)) {
                context.getSource().sendFailure(Component.literal("Could not set " + name + "'s ping burst"));
                continue;
            }
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set " + name + "'s ping burst to " + spec.describe()), false);
            if (first == 0) first = spec.averageAddedMs();
        }
        return first;
    }

    private static int shadowSet(CommandContext<CommandSourceStack> context, String scriptName)
            throws CommandSyntaxException {
        if (scriptName != null) {
            AiScript script;
            try {
                script = AiScriptRegistry.load(context.getSource().getServer(), scriptName);
            } catch (IOException e) {
                context.getSource().sendFailure(Component.literal("Failed to load script: " + e.getMessage()));
                return 0;
            }
            if (script == null) {
                context.getSource().sendFailure(Component.literal("Script not found: " + scriptName));
                return 0;
            }
        }

        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            if (player instanceof BotPlayer) {
                context.getSource().sendFailure(Component.literal(
                        name + " is a bot, so it has nothing to shadow"));
                continue;
            }
            Shadows.arm(player.getUUID(), name, scriptName);
            context.getSource().sendSuccess(() -> Component.literal(scriptName == null
                    ? "Shadowing " + name + " with no AI when they disconnect"
                    : "Shadowing " + name + " with '" + scriptName + "' when they disconnect"), false);
            count++;
        }
        return count;
    }

    private static int shadowReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            if (Shadows.disarm(player.getUUID()) == null) {
                context.getSource().sendSuccess(() -> Component.literal(name + " was not being shadowed"), false);
                continue;
            }
            context.getSource().sendSuccess(() -> Component.literal("Stopped shadowing " + name), false);
            count++;
        }
        return count;
    }

    private static int pingReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            if (player instanceof BotPlayer bot) {
                bot.setPingSpec(PingDelaySpec.NONE);
                bot.setBurstSpec(PingBurstSpec.NONE);
            } else {
                PingBoosters.reset(player);
            }
            context.getSource().sendSuccess(() -> Component.literal(
                    "Cleared " + name + "'s ping delay and burst"), false);
        }
        return 1;
    }

    private static int pingSettingsGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            String summary = PingDelays.of(player.getUUID()).summary();
            context.getSource().sendSuccess(() -> Component.literal(name + " ping delays: \n" + summary), false);
        }
        return 1;
    }

    private static int pingSettingsSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "category");
        PingDelayOptions.Category category = PingDelayOptions.Category.byId(id);
        if (category == null) {
            context.getSource().sendFailure(Component.literal("Unknown ping delay category: " + id));
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            PingDelays.of(player.getUUID()).set(category, enabled);

            if (!(player instanceof BotPlayer)) {
                PingBoostHandler handler = PingBoosters.handlerOf(player);
                if (handler != null) handler.setOptions(PingDelays.of(player.getUUID()));
                if (category == PingDelayOptions.Category.KNOCKBACK) {
                    context.getSource().sendSuccess(() -> Component.literal(
                            name + ": knockback is server-side physics, so this has no effect on a real client"), false);
                    continue;
                }
            }

            context.getSource().sendSuccess(() -> Component.literal(
                    name + " " + category.id() + " delay " + (enabled ? "on" : "off")), false);
        }
        return 1;
    }

    private static int pingReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return pingReport(context, true, true);
    }

    private static int pingDelayReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return pingReport(context, true, false);
    }

    private static int pingBurstReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return pingReport(context, false, true);
    }

    private static int pingReport(CommandContext<CommandSourceStack> context, boolean showDelay, boolean showBurst)
            throws CommandSyntaxException {
        int first = 0;
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            String name = player.getGameProfile().name();
            StringBuilder text = new StringBuilder(name + " Ping");
            boolean isBot = player instanceof BotPlayer;

            PingDelaySpec delay;
            PingBurstSpec burst;
            boolean bursting;
            int regular;
            int added;

            if (player instanceof BotPlayer bot) {
                delay = bot.pingSpec();
                burst = bot.burstSpec();
                bursting = bot.isBursting();
                regular = -1;
                added = delay.averageMs();
            } else {
                delay = PingBoosters.delayOf(player);
                burst = PingBoosters.burstOf(player);
                PingBoostHandler handler = PingBoosters.handlerOf(player);
                bursting = handler != null && handler.isBursting();
                int base = handler == null ? -1 : handler.baseMs();
                regular = base > 0 ? base : player.connection.latency();
                added = handler == null ? delay.averageMs() : handler.averageAddedMs();
            }

            if (showDelay) {
                text.append("\n Delay: ").append(delay.describe());
                int pingToTicks = HeroBotSettings.botPingToTicks;
                if (isBot && delay.isActive() && pingToTicks > 0) {
                    int avg = delay.averageMs();
                    text.append("\n Delay in Ticks: ").append(avg / pingToTicks);
                    int remainder = avg % pingToTicks;
                    if (remainder > 0) {
                        text.append("\n with a ").append(remainder).append("/").append(pingToTicks)
                                .append(" chance to add a tick");
                    }
                }
            }
            if (showBurst) {
                text.append("\n Burst: ").append(burst.describe());
                if (bursting) {
                    text.append(isBot
                            ? "\n Currently holding actions for a burst"
                            : "\n Currently holding packets for a burst");
                }
            }
            if (regular >= 0) text.append("\n Regular: ").append(regular).append("ms");

            int average = Math.max(regular, 0)
                    + (showDelay ? added : 0)
                    + (showBurst ? burst.averageAddedMs() : 0);
            text.append("\n Average: ").append(average).append("ms");

            String line = text.toString();
            context.getSource().sendSuccess(() -> Component.literal(line), false);
            context.getSource().sendSuccess(
                    () -> Component.literal("Returns: " + average).withColor(0xAAAAAA), false);
            if (first == 0) first = average;
        }
        return first;
    }

    private static int autoJump(CommandContext<CommandSourceStack> context, boolean value)
            throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            PlayerControllers.of(player).setAutoJump(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + player.getGameProfile().name() + "'s auto jump " + (value ? "on" : "off")), false);
        }
        return 1;
    }

    private static int setHandedness(CommandContext<CommandSourceStack> context, boolean leftHanded)
            throws CommandSyntaxException {
        HumanoidArm arm = leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.setMainHand(arm);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + (leftHanded ? " Left-Handed" : " Right-Handed")), false);
        }
        return 1;
    }

}
