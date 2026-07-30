package hero.bane.herobot.mod.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.mod.common.util.DistanceCalculator;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DistanceCommand {
    private interface ShapeSupplier {
        AABB get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    private static ShapeSupplier vecToShape(VecSupplier vs) {
        return c -> {
            Vec3 v = vs.get(c);
            return new AABB(v, v);
        };
    }

    private interface VecSupplier {
        Vec3 get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                literal("distance")
                        .then(fromSubtree("from", false))
                        .then(fromSubtree("fromHitbox", true))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> fromSubtree(String name, boolean hitbox) {
        if (hitbox) {
            return literal(name)
                    .then(toSubtree(
                            argument("fromHitboxBlock", BlockPosArgument.blockPos()),
                            c -> new AABB(BlockPosArgument.getBlockPos(c, "fromHitboxBlock"))
                    ))
                    .then(toSubtree(
                            argument("fromHitboxEntity", EntityArgument.entity()),
                            c -> EntityArgument.getEntity(c, "fromHitboxEntity").getBoundingBox()
                    ));
        }
        return literal(name)
                .then(toSubtree(
                        argument("fromPos", Vec3Argument.vec3()),
                        vecToShape(c -> Vec3Argument.getVec3(c, "fromPos"))
                ))
                .then(toSubtree(
                        argument("fromEntity", EntityArgument.entity()),
                        vecToShape(c -> EntityArgument.getEntity(c, "fromEntity").position())
                ));
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> toSubtree(
            RequiredArgumentBuilder<CommandSourceStack, T> arg,
            ShapeSupplier from
    ) {
        arg.then(literal("to")
                .then(withDistanceExecutors(
                        argument("toPos", Vec3Argument.vec3()),
                        from,
                        vecToShape(c -> Vec3Argument.getVec3(c, "toPos"))
                ))
                .then(withDistanceExecutors(
                        argument("toEntity", EntityArgument.entity()),
                        from,
                        vecToShape(c -> EntityArgument.getEntity(c, "toEntity").position())
                )));

        arg.then(literal("toHitbox")
                .then(withDistanceExecutors(
                        argument("toHitboxBlock", BlockPosArgument.blockPos()),
                        from,
                        c -> new AABB(BlockPosArgument.getBlockPos(c, "toHitboxBlock"))
                ))
                .then(withDistanceExecutors(
                        argument("toHitboxEntity", EntityArgument.entity()),
                        from,
                        c -> EntityArgument.getEntity(c, "toHitboxEntity").getBoundingBox()
                )));

        return arg;
    }

    private static <T> ArgumentBuilder<CommandSourceStack, ?> withDistanceExecutors(
            RequiredArgumentBuilder<CommandSourceStack, T> arg,
            ShapeSupplier from,
            ShapeSupplier to
    ) {
        arg.executes(c -> run(c.getSource(), from.get(c), to.get(c), 0));

        arg.then(literal("e")
                .then(argument("exp", IntegerArgumentType.integer(0))
                        .executes(c -> run(
                                c.getSource(),
                                from.get(c),
                                to.get(c),
                                IntegerArgumentType.getInteger(c, "exp")
                        ))));

        arg.then(literal("horizontal")
                .executes(c -> runXZ(c.getSource(), from.get(c), to.get(c), 0))
                .then(literal("e")
                        .then(argument("exp", IntegerArgumentType.integer(0))
                                .executes(c -> runXZ(
                                        c.getSource(),
                                        from.get(c),
                                        to.get(c),
                                        IntegerArgumentType.getInteger(c, "exp")
                                )))));

        arg.then(literal("vertical")
                .executes(c -> runY(c.getSource(), from.get(c), to.get(c), 0))
                .then(literal("e")
                        .then(argument("exp", IntegerArgumentType.integer(0))
                                .executes(c -> runY(
                                        c.getSource(),
                                        from.get(c),
                                        to.get(c),
                                        IntegerArgumentType.getInteger(c, "exp")
                                )))));

        return arg;
    }

    private static int run(CommandSourceStack source, AABB from, AABB to, int exp) {
        return DistanceCalculator.distanceBetweenBoxes(source, from, to, exp);
    }

    private static int runXZ(CommandSourceStack source, AABB from, AABB to, int exp) {
        Vec3[] closest = DistanceCalculator.closestPointsBetween(from, to);
        return DistanceCalculator.distance(
                source,
                new Vec3(closest[0].x, 0, closest[0].z),
                new Vec3(closest[1].x, 0, closest[1].z),
                exp
        );
    }

    private static int runY(CommandSourceStack source, AABB from, AABB to, int exp) {
        Vec3[] closest = DistanceCalculator.closestPointsBetween(from, to);
        return DistanceCalculator.distance(
                source,
                new Vec3(0, closest[0].y, 0),
                new Vec3(0, closest[1].y, 0),
                exp
        );
    }
}
