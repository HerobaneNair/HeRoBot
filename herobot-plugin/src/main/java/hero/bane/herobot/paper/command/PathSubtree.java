package hero.bane.herobot.paper.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.paper.bot.pathing.traversal.BotPathing;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.sched.Sched;
import hero.bane.herobot.common.bot.pathing.DebugChannel;
import hero.bane.herobot.paper.bot.pathing.PathSettingOps;
import hero.bane.herobot.paper.bot.pathing.PathSettings;
import hero.bane.herobot.paper.control.ControlOp;
import hero.bane.herobot.paper.control.RemoteOps;
import hero.bane.herobot.paper.control.RemotePathSettings;
import hero.bane.herobot.paper.control.RemotePathState;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PathSubtree {

    private PathSubtree() {}

    private record PathTarget(ServerPlayer player, PathSettings settings, boolean remote) {
        String name() {
            return player.getGameProfile().name();
        }

        void send(ControlOp op) {
            if (remote) RemoteOps.send(player, op);
        }
    }

    private static List<PathTarget> pathTargets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        List<PathTarget> targets = new ArrayList<>();
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            if (player instanceof BotPlayer bot) {
                targets.add(new PathTarget(player, bot.getPathSettings(), false));
            } else if (RemoteOps.canSend(player)) {
                targets.add(new PathTarget(player, RemotePathSettings.of(player), true));
            } else {
                context.getSource().sendFailure(Component.literal(
                        player.getGameProfile().name() + " can't be made to path: they don't have HeroBot installed"));
            }
        }
        return targets;
    }

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
        for (PathTarget t : pathTargets(context)) {
            PathSettings settings = t.settings();

            if (!t.remote()) {
                BotPlayer bot = (BotPlayer) t.player();
                BotPathing existing = bot.getPathFollower();
                if (existing != null && !existing.isDone() && !existing.isEntityMode()
                        && existing.getTarget().distanceTo(target) < 0.01) {
                    context.getSource().sendFailure(Component.literal(t.name() + " is already pathing to that target"));
                    return 0;
                }
            }

            if (settings.isWithinTarget(
                    Math.sqrt(hDistSq(t.player().position(), target)),
                    Math.abs(t.player().position().y - target.y))) {
                context.getSource().sendSuccess(() -> Component.literal(t.name() + " is already at target"), false);
                continue;
            }

            if (t.remote()) {
                int seq = RemotePathState.begin(t.player());
                RemoteOps.send(t.player(), ControlOp.pathGotoPos(target, seq));
            } else {
                BotPlayer bot = (BotPlayer) t.player();
                CommandSourceStack source = context.getSource();
                Sched.entity(bot, () -> bot.setPathFollower(new BotPathing(bot, target, source, settings)));
            }
            context.getSource().sendSuccess(() -> Component.literal(t.name() + " is pathing to " +
                    String.format("%.1f, %.1f, %.1f", target.x, target.y, target.z)), false);
        }
        return 1;
    }

    private static int pathToEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "entity");
        for (PathTarget t : pathTargets(context)) {
            if (!t.remote()) {
                BotPlayer bot = (BotPlayer) t.player();
                BotPathing existing = bot.getPathFollower();
                if (existing != null && !existing.isDone() && existing.isEntityMode()
                        && existing.getTargetEntity() == target) {
                    context.getSource().sendFailure(Component.literal(t.name() + " is already pathing to that target"));
                    return 0;
                }
            }

            if (t.remote()) {
                int seq = RemotePathState.begin(t.player());
                RemoteOps.send(t.player(), ControlOp.pathGotoEntity(target.getId(), seq));
            } else {
                BotPlayer bot = (BotPlayer) t.player();
                CommandSourceStack source = context.getSource();
                Sched.entity(bot, () -> bot.setPathFollower(new BotPathing(bot, target, source, t.settings())));
            }
            String targetName = target.getName().getString();
            context.getSource().sendSuccess(() -> Component.literal(t.name() + " is following " + targetName), false);
        }
        return 1;
    }

    public static int pathStop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            if (t.remote()) {
                RemotePathState.deactivate(t.player());
                RemoteOps.send(t.player(), ControlOp.pathStop());
            } else {
                BotPlayer stopping = (BotPlayer) t.player();
                Sched.entity(stopping, stopping::clearPathFollower);
            }
            context.getSource().sendSuccess(() -> Component.literal(t.name() + " stopped pathing"), false);
        }
        return 1;
    }

    private static int setSetting(CommandContext<CommandSourceStack> context, int index, double value,
                                  String label, String display) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            PathSettingOps.apply(t.settings(), index, value);
            t.send(ControlOp.pathSetting(index, value));
            context.getSource().sendSuccess(() -> Component.literal(
                    "Set " + t.name() + "'s " + label + " to " + display), false);
        }
        return 1;
    }

    private static int getSetting(CommandContext<CommandSourceStack> context, String label,
                                  Function<PathSettings, String> read, String suffix)
            throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            String value = read.apply(t.settings());
            context.getSource().sendSuccess(() -> Component.literal(
                    t.name() + "'s " + label + ": " + value + suffix), false);
        }
        return 1;
    }

    private static int listPathSettings(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            PathSettings s = t.settings();
            String msg = t.name() + "'s path settings:" +
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
        for (PathTarget t : pathTargets(context)) {
            PathSettings settings = t.settings();
            if (settings.getAvoidedBlocks().isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal(t.name() + " has no avoided blocks"), false);
            } else {
                String list = settings.getAvoidedBlocks().stream()
                        .map(b -> BuiltInRegistries.BLOCK.getKey(b).toString())
                        .collect(Collectors.joining(", "));
                context.getSource().sendSuccess(() -> Component.literal(t.name() + " avoided blocks: " + list), false);
            }
        }
        return 1;
    }

    private static int addAvoidedBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Block block = BlockStateArgument.getBlock(context, "block").getState().getBlock();
        for (PathTarget t : pathTargets(context)) {
            t.settings().addAvoidedBlock(block);
            t.send(ControlOp.pathAvoidBlock(BuiltInRegistries.BLOCK.getKey(block).toString(), ControlOp.AVOID_ADD));
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            context.getSource().sendSuccess(() -> Component.literal("Added " + name + " to " + t.name() + "'s avoided blocks"), false);
        }
        return 1;
    }

    private static int removeAvoidedBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Block block = BlockStateArgument.getBlock(context, "block").getState().getBlock();
        for (PathTarget t : pathTargets(context)) {
            boolean removed = t.settings().removeAvoidedBlock(block);
            t.send(ControlOp.pathAvoidBlock(BuiltInRegistries.BLOCK.getKey(block).toString(), ControlOp.AVOID_REMOVE));
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (removed) {
                context.getSource().sendSuccess(() -> Component.literal("Removed " + name + " from " + t.name() + "'s avoided blocks"), false);
            } else {
                context.getSource().sendFailure(Component.literal(name + " was not in " + t.name() + "'s avoided blocks"));
            }
        }
        return 1;
    }

    private static int clearAvoidedBlocks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            t.settings().clearAvoidedBlocks();
            t.send(ControlOp.pathAvoidBlock("", ControlOp.AVOID_CLEAR));
            context.getSource().sendSuccess(() -> Component.literal("Cleared " + t.name() + "'s avoided blocks"), false);
        }
        return 1;
    }

    private static int getMoveType(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "move type", s -> s.getMoveType().displayName(), "");
    }

    private static int setMoveType(CommandContext<CommandSourceStack> context, PathSettings.MoveType type) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            t.settings().setMoveType(type);
            t.send(ControlOp.pathMoveType(type.ordinal()));
            context.getSource().sendSuccess(() -> Component.literal("Set " + t.name() + "'s move type to " + type.displayName()), false);
        }
        return 1;
    }

    private static int getMaxHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "target horizontal",
                s -> String.valueOf(s.getMaxHorizontalDistance()), " (default: 1.0)");
    }

    private static int setMaxHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.MAX_HORIZONTAL, value, "target horizontal", String.valueOf(value));
    }

    private static int getMaxVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "target vertical",
                s -> s.getMaxVerticalDistance() < 0 ? "ground-seek" : String.valueOf(s.getMaxVerticalDistance()),
                " (default: 2.0)");
    }

    private static int setMaxVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.MAX_VERTICAL, value, "target vertical",
                value < 0 ? "ground-seek" : String.valueOf(value));
    }

    private static int getNodeHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "node horizontal",
                s -> String.valueOf(s.getNodeHorizontalDistance()), " (default: 0.5)");
    }

    private static int setNodeHorizontalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.NODE_HORIZONTAL, value, "node horizontal", String.valueOf(value));
    }

    private static int getNodeVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "node vertical",
                s -> s.getNodeVerticalDistance() < 0 ? "disabled" : String.valueOf(s.getNodeVerticalDistance()),
                " (default: 1.0)");
    }

    private static int setNodeVerticalDistance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.NODE_VERTICAL, value, "node vertical",
                value < 0 ? "disabled" : String.valueOf(value));
    }

    private static int getStopFollowing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "stopFollowing",
                s -> String.valueOf(s.isStopFollowing()), " (default: true)");
    }

    private static int setStopFollowing(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        return setSetting(context, PathSettingOps.STOP_FOLLOWING, value ? 1 : 0, "stopFollowing", String.valueOf(value));
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
        for (PathTarget t : pathTargets(context)) {
            String enabled = t.settings().describeDebug();
            String available = java.util.Arrays.stream(DebugChannel.values())
                    .map(ch -> ch.id() + " (" + ch.description() + ")")
                    .collect(Collectors.joining("\n  "));
            context.getSource().sendSuccess(() -> Component.literal(t.name() + "'s debug channels: " + enabled +
                    "\nAvailable channels:\n  " + available), false);
        }
        return 1;
    }

    private static int setDebugAll(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            t.settings().setDebug(value);
            t.send(ControlOp.pathSetting(PathSettingOps.DEBUG, value ? 1 : 0));
            context.getSource().sendSuccess(() -> Component.literal((value ? "Enabled all" : "Disabled all") + " debug particles for " + t.name()), false);
        }
        return 1;
    }

    private static int toggleDebugChannel(CommandContext<CommandSourceStack> context, DebugChannel channel) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            boolean enabled = t.settings().toggleDebugChannel(channel);
            t.send(ControlOp.pathDebugChannel(channel.ordinal(), enabled));
            context.getSource().sendSuccess(() -> Component.literal((enabled ? "Enabled " : "Disabled ") + channel.id() + " debug particles for " + t.name()), false);
        }
        return 1;
    }

    private static int setDebugChannel(CommandContext<CommandSourceStack> context, DebugChannel channel, boolean value) throws CommandSyntaxException {
        for (PathTarget t : pathTargets(context)) {
            t.settings().setDebugChannel(channel, value);
            t.send(ControlOp.pathDebugChannel(channel.ordinal(), value));
            context.getSource().sendSuccess(() -> Component.literal((value ? "Enabled " : "Disabled ") + channel.id() + " debug particles for " + t.name()), false);
        }
        return 1;
    }

    private static int getHorizontalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "cost horizontal",
                s -> String.valueOf(s.getHorizontalMoveCost()), " (default: 1.0)");
    }

    private static int setHorizontalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.HORIZONTAL_COST, value, "cost horizontal", String.valueOf(value));
    }

    private static int getVerticalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "cost vertical",
                s -> String.valueOf(s.getVerticalMoveCost()), " (default: 1.5)");
    }

    private static int setVerticalMoveCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.VERTICAL_COST, value, "cost vertical", String.valueOf(value));
    }

    private static int getSwimCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return getSetting(context, "cost swim",
                s -> String.format("%.2f", s.getSwimCostMultiplier()), " (auto-calculated from gear)");
    }

    private static int setSwimCost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        double value = DoubleArgumentType.getDouble(context, "value");
        return setSetting(context, PathSettingOps.SWIM_COST, value, "cost swim", String.valueOf(value));
    }

    private static double hDistSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
