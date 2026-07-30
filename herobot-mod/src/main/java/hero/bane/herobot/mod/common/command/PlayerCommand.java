package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import hero.bane.herobot.mod.common.HeroBotSettings;
import hero.bane.herobot.mod.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.mod.common.command.helper.*;
import hero.bane.herobot.mod.common.control.PlayerController;
import hero.bane.herobot.mod.common.control.PlayerControllers;
import hero.bane.herobot.mod.common.mixin.ServerCommonPacketListenerImplAccessor;
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
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

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

                                .then(Commands.literal("ping")
                                        .executes(PlayerCommand::pingGet)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .suggests((c, b) -> {
                                                    b.suggest(0);
                                                    b.suggest(25);
                                                    b.suggest(50);
                                                    b.suggest(100);
                                                    b.suggest(150);
                                                    b.suggest(200);
                                                    return b.buildFuture();
                                                })
                                                .executes(PlayerCommand::pingSet)))

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
        MinecraftServer server = context.getSource().getServer();
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            if (message.startsWith("/")) {
                server.getCommands().performPrefixedCommand(
                        bot.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS), message);
            } else {
                PlayerChatMessage chatMessage = PlayerChatMessage.unsigned(bot.getUUID(), message);
                server.getPlayerList().broadcastChatMessage(
                        chatMessage, bot, ChatType.bind(ChatType.CHAT, bot));
            }
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

    private static int pingSet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int value = IntegerArgumentType.getInteger(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.ping = value;
            ((ServerCommonPacketListenerImplAccessor) bot.connection).setLatency(value);
            context.getSource().getServer().getPlayerList().broadcastAll(
                    new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, bot));
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s ping to " + value + "ms"), false);
        }
        return 1;
    }

    private static int pingGet(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<BotPlayer> botPlayerList = CommandHelper.requireBotTargets(context);
        if (!botPlayerList.isEmpty()) {
            int botPing = botPlayerList.getFirst().ping;
            int pingToTicks = HeroBotSettings.botPingToTicks;
            context.getSource().sendSuccess(() -> Component.literal("Bot Ping: " + botPing +
                    "ms\nDelay in Ticks: " + botPing / pingToTicks +
                    (botPing % pingToTicks > 0 ? "\n with a " + botPing % pingToTicks + "/" + pingToTicks + " chance to add a tick" : "")
            ), false);
            return botPing;
        } else {
            return 0;
        }
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
