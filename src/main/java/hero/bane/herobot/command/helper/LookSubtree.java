package hero.bane.herobot.command.helper;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.control.PlayerController;
import hero.bane.herobot.control.PlayerControllers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public final class LookSubtree {
    private LookSubtree() {
    }

    private enum LookMode {EYES, FEET, CLOSEST}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("look")
                .then(makeLookDirectionCommand("north", Direction.NORTH))
                .then(makeLookDirectionCommand("south", Direction.SOUTH))
                .then(makeLookDirectionCommand("east", Direction.EAST))
                .then(makeLookDirectionCommand("west", Direction.WEST))

                .then(makeLookDirectionCommand("up", Direction.UP))
                .then(makeLookDirectionCommand("down", Direction.DOWN))

                .then(makeLookRelativeCommand("left", -90))
                .then(makeLookRelativeCommand("right", 90))
                .then(makeLookRelativeCommand("back", 180))

                .then(Commands.literal("relative")
                        .then(Commands.argument("rotation", RotationArgument.rotation())
                                .executes(c -> lookRelative(c, 0))
                                .then(Commands.literal("ticks")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> lookRelative(c, IntegerArgumentType.getInteger(c, "ticks")))))))

                .then(Commands.literal("random")
                        .executes(CommandHelper.control(ap -> {
                            float yaw = ThreadLocalRandom.current().nextFloat() * 360 - 180;
                            float pitch = ThreadLocalRandom.current().nextFloat() * 180 - 90;
                            ap.look(yaw, pitch);
                        })))
                .then(Commands.literal("upon")
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(c -> lookUpon(c, LookMode.EYES, 0))
                                .then(makeLookUponMode("eyes", LookMode.EYES))
                                .then(makeLookUponMode("feet", LookMode.FEET))
                                .then(makeLookUponMode("closest", LookMode.CLOSEST))
                                .then(Commands.literal("ticks")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> lookUpon(c, LookMode.EYES, IntegerArgumentType.getInteger(c, "ticks")))))
                                .then(makeOffsetBranch((c, ticks) -> lookUpon(c, LookMode.EYES, ticks)))))
                .then(Commands.literal("at")
                        .then(Commands.argument("position", Vec3Argument.vec3())
                                .executes(c -> CommandHelper.control(c,
                                        ap -> ap.lookAt(Vec3Argument.getVec3(c, "position"))))
                                .then(Commands.literal("ticks")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> CommandHelper.control(c,
                                                        ap -> ap.lookAt(Vec3Argument.getVec3(c, "position"),
                                                                IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(makeOffsetBranch((c, ticks) -> {
                                    Vec3 pos = Vec3Argument.getVec3(c, "position");
                                    return CommandHelper.control(c, ap -> ap.lookAt(pos, ticks));
                                }))))
                .then(Commands.literal("direction")
                        .then(Commands.argument("direction", RotationArgument.rotation())
                                .suggests((c, b) -> {
                                    b.suggest("~ ~");
                                    return b.buildFuture();
                                })
                                .executes(c -> CommandHelper.control(c,
                                        ap -> ap.look(
                                                RotationArgument.getRotation(c, "direction")
                                                        .getRotation(c.getSource()))))
                                .then(Commands.literal("ticks")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> CommandHelper.control(c,
                                                        ap -> ap.look(
                                                                RotationArgument.getRotation(c, "direction")
                                                                        .getRotation(c.getSource()),
                                                                IntegerArgumentType.getInteger(c, "ticks"))))))
                                .then(makeOffsetBranch((c, ticks) -> {
                                    Vec2 rot = RotationArgument.getRotation(c, "direction").getRotation(c.getSource());
                                    return CommandHelper.control(c, ap -> ap.look(rot, ticks));
                                }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeLookDirectionCommand(String name, Direction direction) {
        return Commands.literal(name)
                .executes(CommandHelper.control(ap -> ap.look(direction)))
                .then(Commands.literal("ticks")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> CommandHelper.control(c,
                                        ap -> ap.look(direction, IntegerArgumentType.getInteger(c, "ticks"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeLookRelativeCommand(String name, float yaw) {
        return Commands.literal(name)
                .executes(CommandHelper.control(ap -> ap.turn(yaw, (float) 0)))
                .then(Commands.literal("ticks")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> CommandHelper.control(c,
                                        ap -> ap.turn(yaw, (float) 0, IntegerArgumentType.getInteger(c, "ticks"))))));
    }

    private static int lookRelative(CommandContext<CommandSourceStack> context, int ticks)
            throws CommandSyntaxException {
        for (ServerPlayer player : CommandHelper.requireControllableTargets(context)) {
            CommandSourceStack playerSource = context.getSource().withRotation(new Vec2(player.getXRot(), player.getYRot()));
            Vec2 rotation = RotationArgument.getRotation(context, "rotation").getRotation(playerSource);
            PlayerController ap = PlayerControllers.of(player);
            if (ticks > 0) {
                ap.look(rotation, ticks);
            } else {
                ap.look(rotation);
            }
        }
        return 1;
    }

    private static int lookUpon(CommandContext<CommandSourceStack> context, LookMode mode, int ticks)
            throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(context, "entity");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");

        for (ServerPlayer player : players) {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookTarget = switch (mode) {
                case FEET -> target.position();
                case EYES -> target.getEyePosition();
                case CLOSEST -> closestPointToBox(eyePos, target.getBoundingBox());
            };

            PlayerControllers.of(player).lookAt(lookTarget, ticks);
        }

        return players.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeLookUponMode(String name, LookMode mode) {
        return Commands.literal(name)
                .executes(c -> lookUpon(c, mode, 0))
                .then(Commands.literal("ticks")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(c -> lookUpon(c, mode, IntegerArgumentType.getInteger(c, "ticks")))));
    }

    private static Vec3 closestPointToBox(Vec3 eye, AABB box) {
        return new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ)
        );
    }

    @FunctionalInterface
    private interface LookExecutor {
        int execute(CommandContext<CommandSourceStack> c, int ticks) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> makeOffsetBranch(LookExecutor baseLook) {
        return Commands.literal("offset")
                .then(Commands.argument("yawOffset", DoubleArgumentType.doubleArg(0, 360))
                        .suggests((c, b) -> {
                            b.suggest(0);
                            return b.buildFuture();
                        })
                        .then(Commands.argument("pitchOffset", DoubleArgumentType.doubleArg(0, 180))
                                .suggests((c, b) -> {
                                    b.suggest(0);
                                    return b.buildFuture();
                                })
                                .executes(c -> executeWithOffset(c, baseLook, 0))
                                .then(Commands.literal("ticks")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                .executes(c -> executeWithOffset(c, baseLook, IntegerArgumentType.getInteger(c, "ticks")))))));
    }

    private static int executeWithOffset(CommandContext<CommandSourceStack> c, LookExecutor baseLook, int ticks) throws CommandSyntaxException {
        double yawRange = DoubleArgumentType.getDouble(c, "yawOffset");
        double pitchRange = DoubleArgumentType.getDouble(c, "pitchOffset");

        int result = baseLook.execute(c, ticks);

        for (ServerPlayer player : CommandHelper.requireControllableTargets(c)) {
            float yawOff = (float) (ThreadLocalRandom.current().nextDouble() * 2 * yawRange - yawRange);
            float pitchOff = (float) (ThreadLocalRandom.current().nextDouble() * 2 * pitchRange - pitchRange);
            float newYaw = player.getYRot() + yawOff;
            float newPitch = Mth.clamp(player.getXRot() + pitchOff, -90.0f, 90.0f);
            PlayerController ap = PlayerControllers.of(player);
            if (ticks > 0) {
                ap.lookInterpolated(newYaw, newPitch, ticks);
            } else {
                ap.look(newYaw, newPitch);
            }
        }

        return result;
    }
}
