package hero.bane.herobot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.util.DistanceCalculator;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.Vec3;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class DistanceCommand {
    private interface VecSupplier {
        Vec3 get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
                literal("distance")
                        .then(literal("from")
                                .then(argument("fromPos", Vec3Argument.vec3())
                                        .then(literal("to")
                                                .then(withDistanceExecutors(
                                                        argument("toPos", Vec3Argument.vec3()),
                                                        c -> Vec3Argument.getVec3(c, "fromPos"),
                                                        c -> Vec3Argument.getVec3(c, "toPos")
                                                ))
                                                .then(withDistanceExecutors(
                                                        argument("toEntity", EntityArgument.entity()),
                                                        c -> Vec3Argument.getVec3(c, "fromPos"),
                                                        c -> EntityArgument.getEntity(c, "toEntity").position()
                                                ))))
                                .then(argument("fromEntity", EntityArgument.entity())
                                        .then(literal("to")
                                                .then(withDistanceExecutors(
                                                        argument("toPos", Vec3Argument.vec3()),
                                                        c -> EntityArgument.getEntity(c, "fromEntity").position(),
                                                        c -> Vec3Argument.getVec3(c, "toPos")
                                                ))
                                                .then(withDistanceExecutors(
                                                        argument("toEntity", EntityArgument.entity()),
                                                        c -> EntityArgument.getEntity(c, "fromEntity").position(),
                                                        c -> EntityArgument.getEntity(c, "toEntity").position()
                                                ))))
                        )
        );
    }

    private static <T> ArgumentBuilder<CommandSourceStack, ?> withDistanceExecutors(
            RequiredArgumentBuilder<CommandSourceStack, T> arg,
            VecSupplier from,
            VecSupplier to
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

    private static int run(CommandSourceStack source, Vec3 from, Vec3 to, int exp) {
        return DistanceCalculator.distance(source, from, to, exp);
    }

    private static int runXZ(CommandSourceStack source, Vec3 from, Vec3 to, int exp) {
        return DistanceCalculator.distance(
                source,
                new Vec3(from.x, 0, from.z),
                new Vec3(to.x, 0, to.z),
                exp
        );
    }

    private static int runY(CommandSourceStack source, Vec3 from, Vec3 to, int exp) {
        return DistanceCalculator.distance(
                source,
                new Vec3(0, from.y, 0),
                new Vec3(0, to.y, 0),
                exp
        );
    }
}
