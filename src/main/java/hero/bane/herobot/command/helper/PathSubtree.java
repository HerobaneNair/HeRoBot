package hero.bane.herobot.command.helper;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.bot.pathing.traversal.BotPathing;
import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.bot.pathing.DebugChannel;
import hero.bane.herobot.bot.pathing.PathSettings;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Collectors;

public final class PathSubtree {

    private PathSubtree() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext ctx) {
        return Commands.literal("path")
                .then(Commands.literal("pos")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(PathSubtree::pathToPos)))
                .then(Commands.literal("entity")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(PathSubtree::pathToEntity)))
                .then(Commands.literal("stop")
                        .executes(PathSubtree::pathStop))
                .then(Commands.literal("settings")
                        .executes(PathSubtree::listPathSettings)
                        .then(Commands.literal("avoidedBlocks")
                                .executes(PathSubtree::listAvoidedBlocks)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("block", BlockStateArgument.block(ctx))
                                                .executes(PathSubtree::addAvoidedBlock)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("block", BlockStateArgument.block(ctx))
                                                .executes(PathSubtree::removeAvoidedBlock)))
                                .then(Commands.literal("clear")
                                        .executes(PathSubtree::clearAvoidedBlocks)))
                        .then(Commands.literal("moveType")
                                .executes(PathSubtree::getMoveType)
                                .then(Commands.literal("walk")
                                        .executes(c -> setMoveType(c, PathSettings.MoveType.WALK)))
                                .then(Commands.literal("sprint")
                                        .executes(c -> setMoveType(c, PathSettings.MoveType.SPRINT)))
                                .then(Commands.literal("sprintjump")
                                        .executes(c -> setMoveType(c, PathSettings.MoveType.SPRINT_JUMP))))
                        .then(Commands.literal("target")
                                .then(Commands.literal("horizontal")
                                        .executes(PathSubtree::getMaxHorizontalDistance)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01))
                                                .executes(PathSubtree::setMaxHorizontalDistance)))
                                .then(Commands.literal("vertical")
                                        .executes(PathSubtree::getMaxVerticalDistance)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1))
                                                .executes(PathSubtree::setMaxVerticalDistance))))
                        .then(Commands.literal("node")
                                .then(Commands.literal("horizontal")
                                        .executes(PathSubtree::getNodeHorizontalDistance)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01))
                                                .executes(PathSubtree::setNodeHorizontalDistance)))
                                .then(Commands.literal("vertical")
                                        .executes(PathSubtree::getNodeVerticalDistance)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-1))
                                                .executes(PathSubtree::setNodeVerticalDistance))))
                        .then(Commands.literal("stopFollowing")
                                .executes(PathSubtree::getStopFollowing)
                                .then(Commands.literal("true")
                                        .executes(c -> setStopFollowing(c, true)))
                                .then(Commands.literal("false")
                                        .executes(c -> setStopFollowing(c, false))))
                        .then(buildDebugNode())
                        .then(Commands.literal("cost")
                                .then(Commands.literal("horizontal")
                                        .executes(PathSubtree::getHorizontalMoveCost)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01))
                                                .executes(PathSubtree::setHorizontalMoveCost)))
                                .then(Commands.literal("vertical")
                                        .executes(PathSubtree::getVerticalMoveCost)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.01))
                                                .executes(PathSubtree::setVerticalMoveCost)))
                                .then(Commands.literal("swim")
                                        .executes(PathSubtree::getSwimCost)
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1))
                                                .executes(PathSubtree::setSwimCost)))));
    }

    private static int pathToPos(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Vec3 target = Vec3Argument.getVec3(context, "position");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            PathSettings settings = bot.getPathSettings();

            BotPathing existing = bot.getPathFollower();
            if (existing != null && !existing.isDone() && !existing.isEntityMode()
                    && existing.getTarget().distanceTo(target) < 0.01) {
                context.getSource().sendFailure(Component.literal(bot.getGameProfile().name() + " is already pathing to that target"));
                return 0;
            }

            if (settings.isWithinTarget(
                    Math.sqrt(hDistSq(bot.position(), target)),
                    Math.abs(bot.position().y - target.y))) {
                context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " is already at target"), false);
                continue;
            }

            BotPathing follower = new BotPathing(bot, target, context.getSource(), settings);
            bot.setPathFollower(follower);
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " is pathing to " +
                    String.format("%.1f, %.1f, %.1f", target.x, target.y, target.z)), false);
        }
        return 1;
    }

    private static int pathToEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "entity");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            BotPathing existing = bot.getPathFollower();
            if (existing != null && !existing.isDone() && existing.isEntityMode()
                    && existing.getTargetEntity() == target) {
                context.getSource().sendFailure(Component.literal(bot.getGameProfile().name() + " is already pathing to that target"));
                return 0;
            }

            PathSettings settings = bot.getPathSettings();
            BotPathing follower = new BotPathing(bot, target, context.getSource(), settings);
            bot.setPathFollower(follower);
            String targetName = target.getName().getString();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " is following " + targetName), false);
        }
        return 1;
    }

    public static int pathStop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.clearPathFollower();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " stopped pathing"), false);
        }
        return 1;
    }

    private static int listPathSettings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            PathSettings s = bot.getPathSettings();
            String msg = bot.getGameProfile().name() + "'s path settings:" +
                    "\n  moveType: " + s.getMoveType().displayName() +
                    "\n  final horizontal: " + s.getMaxHorizontalDistance() +
                    "\n  final vertical: " + (s.getMaxVerticalDistance() < 0 ? "ground-seek" : s.getMaxVerticalDistance()) +
                    "\n  node horizontal: " + s.getNodeHorizontalDistance() +
                    "\n  node vertical: " + (s.getNodeVerticalDistance() < 0 ? "disabled" : s.getNodeVerticalDistance()) +
                    "\n  stopFollowing: " + s.isStopFollowing() +
                    "\n  debug: " + s.describeDebug() +
                    "\n  cost horizontal: " + s.getHorizontalMoveCost() +
                    "\n  cost vertical: " + s.getVerticalMoveCost() +
                    "\n  cost swim: " + String.format("%.2f", s.getSwimCostMultiplier()) + " (auto-calculated)";
            context.getSource().sendSuccess(() -> Component.literal(msg), false);
        }
        return 1;
    }

    private static int listAvoidedBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            PathSettings settings = bot.getPathSettings();
            if (settings.getAvoidedBlocks().isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " has no avoided blocks"), false);
            } else {
                String list = settings.getAvoidedBlocks().stream()
                        .map(b -> BuiltInRegistries.BLOCK.getKey(b).toString())
                        .collect(Collectors.joining(", "));
                context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + " avoided blocks: " + list), false);
            }
        }
        return 1;
    }

    private static int addAvoidedBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Block block = BlockStateArgument.getBlock(context, "block").getState().getBlock();
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().addAvoidedBlock(block);
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            context.getSource().sendSuccess(() -> Component.literal("Added " + name + " to " + bot.getGameProfile().name() + "'s avoided blocks"), false);
        }
        return 1;
    }

    private static int removeAvoidedBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Block block = BlockStateArgument.getBlock(context, "block").getState().getBlock();
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            boolean removed = bot.getPathSettings().removeAvoidedBlock(block);
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (removed) {
                context.getSource().sendSuccess(() -> Component.literal("Removed " + name + " from " + bot.getGameProfile().name() + "'s avoided blocks"), false);
            } else {
                context.getSource().sendFailure(Component.literal(name + " was not in " + bot.getGameProfile().name() + "'s avoided blocks"));
            }
        }
        return 1;
    }

    private static int clearAvoidedBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().clearAvoidedBlocks();
            context.getSource().sendSuccess(() -> Component.literal("Cleared " + bot.getGameProfile().name() + "'s avoided blocks"), false);
        }
        return 1;
    }

    private static int getMoveType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            String type = bot.getPathSettings().getMoveType().displayName();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s move type: " + type), false);
        }
        return 1;
    }

    private static int setMoveType(CommandContext<CommandSourceStack> context, PathSettings.MoveType type) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setMoveType(type);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s move type to " + type.displayName()), false);
        }
        return 1;
    }

    private static int getMaxHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getMaxHorizontalDistance();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s target horizontal: " + val + " (default: 1.0)"), false);
        }
        return 1;
    }

    private static int setMaxHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setMaxHorizontalDistance(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s target horizontal to " + value), false);
        }
        return 1;
    }

    private static int getMaxVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getMaxVerticalDistance();
            String display = val < 0 ? "ground-seek" : String.valueOf(val);
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s target vertical: " + display + " (default: 2.0)"), false);
        }
        return 1;
    }

    private static int setMaxVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setMaxVerticalDistance(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s target vertical to " + (value < 0 ? "ground-seek" : value)), false);
        }
        return 1;
    }

    private static int getNodeHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getNodeHorizontalDistance();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s node horizontal: " + val + " (default: 0.5)"), false);
        }
        return 1;
    }

    private static int setNodeHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setNodeHorizontalDistance(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s node horizontal to " + value), false);
        }
        return 1;
    }

    private static int getNodeVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getNodeVerticalDistance();
            String display = val < 0 ? "disabled" : String.valueOf(val);
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s node vertical: " + display + " (default: 1.0)"), false);
        }
        return 1;
    }

    private static int setNodeVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setNodeVerticalDistance(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s node vertical to " + (value < 0 ? "disabled" : value)), false);
        }
        return 1;
    }

    private static int getStopFollowing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            boolean val = bot.getPathSettings().isStopFollowing();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s stopFollowing: " + val + " (default: true)"), false);
        }
        return 1;
    }

    private static int setStopFollowing(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setStopFollowing(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s stopFollowing to " + value), false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugNode() {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
                .executes(PathSubtree::getDebug)
                .then(Commands.literal("all")
                        .executes(c -> setDebugAll(c, true)))
                .then(Commands.literal("none")
                        .executes(c -> setDebugAll(c, false)))
                .then(Commands.literal("true")
                        .executes(c -> setDebugAll(c, true)))
                .then(Commands.literal("false")
                        .executes(c -> setDebugAll(c, false)));
        for (DebugChannel channel : DebugChannel.values()) {
            debug = debug.then(Commands.literal(channel.id())
                    .executes(c -> toggleDebugChannel(c, channel))
                    .then(Commands.literal("on")
                            .executes(c -> setDebugChannel(c, channel, true)))
                    .then(Commands.literal("off")
                            .executes(c -> setDebugChannel(c, channel, false))));
        }
        return debug;
    }

    private static int getDebug(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            String enabled = bot.getPathSettings().describeDebug();
            String available = java.util.Arrays.stream(DebugChannel.values())
                    .map(ch -> ch.id() + " (" + ch.description() + ")")
                    .collect(Collectors.joining("\n  "));
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s debug channels: " + enabled +
                    "\nAvailable channels:\n  " + available), false);
        }
        return 1;
    }

    private static int setDebugAll(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setDebug(value);
            context.getSource().sendSuccess(() -> Component.literal((value ? "Enabled all" : "Disabled all") + " debug particles for " + bot.getGameProfile().name()), false);
        }
        return 1;
    }

    private static int toggleDebugChannel(CommandContext<CommandSourceStack> context, DebugChannel channel) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            boolean enabled = bot.getPathSettings().toggleDebugChannel(channel);
            context.getSource().sendSuccess(() -> Component.literal((enabled ? "Enabled " : "Disabled ") + channel.id() + " debug particles for " + bot.getGameProfile().name()), false);
        }
        return 1;
    }

    private static int setDebugChannel(CommandContext<CommandSourceStack> context, DebugChannel channel, boolean value) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setDebugChannel(channel, value);
            context.getSource().sendSuccess(() -> Component.literal((value ? "Enabled " : "Disabled ") + channel.id() + " debug particles for " + bot.getGameProfile().name()), false);
        }
        return 1;
    }

    private static int getHorizontalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getHorizontalMoveCost();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s cost horizontal: " + val + " (default: 1.0)"), false);
        }
        return 1;
    }

    private static int setHorizontalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setHorizontalMoveCost(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s cost horizontal to " + value), false);
        }
        return 1;
    }

    private static int getVerticalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getVerticalMoveCost();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s cost vertical: " + val + " (default: 1.5)"), false);
        }
        return 1;
    }

    private static int setVerticalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setVerticalMoveCost(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s cost vertical to " + value), false);
        }
        return 1;
    }

    private static int getSwimCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            double val = bot.getPathSettings().getSwimCostMultiplier();
            context.getSource().sendSuccess(() -> Component.literal(bot.getGameProfile().name() + "'s cost swim: " + String.format("%.2f", val) + " (auto-calculated from gear)"), false);
        }
        return 1;
    }

    private static int setSwimCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        for (BotPlayer bot : CommandHelper.requireBotTargets(context)) {
            bot.getPathSettings().setSwimCostMultiplier(value);
            context.getSource().sendSuccess(() -> Component.literal("Set " + bot.getGameProfile().name() + "'s cost swim to " + value), false);
        }
        return 1;
    }

    private static double hDistSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
