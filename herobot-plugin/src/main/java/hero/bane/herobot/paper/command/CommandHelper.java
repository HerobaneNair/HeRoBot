package hero.bane.herobot.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.bot.BotPlayerActionPack;
import hero.bane.herobot.paper.control.PlayerController;
import hero.bane.herobot.paper.control.PlayerControllers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class CommandHelper {
    private static final SimpleCommandExceptionType NOT_BOT =
            new SimpleCommandExceptionType(Component.literal("Only bot players can be targeted"));

    private CommandHelper() {}

    public static List<BotPlayer> requireBotTargets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        List<BotPlayer> bots = new ArrayList<>();

        for (ServerPlayer p : players) {
            if (!(p instanceof BotPlayer bot))
                throw NOT_BOT.create();
            bots.add(bot);
        }

        if (bots.isEmpty())
            throw NOT_BOT.create();

        return bots;
    }

    public static int manipulateAndStopPath(CommandContext<CommandSourceStack> context,
                                            Consumer<BotPlayerActionPack> action)
            throws CommandSyntaxException {
        for (BotPlayer bot : requireBotTargets(context)) {
            InventorySubtree.requireNoScreen(bot);
            bot.clearPathFollower();
            action.accept(bot.getActionPack());
        }
        return 1;
    }

    public static Command<CommandSourceStack> manipulationAndStopPath(Consumer<BotPlayerActionPack> action) {
        return c -> manipulateAndStopPath(c, action);
    }

    public static List<ServerPlayer> requireControllableTargets(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        if (players.isEmpty())
            throw NOT_BOT.create();
        return new ArrayList<>(players);
    }

    public static int control(CommandContext<CommandSourceStack> context,
                              Consumer<PlayerController> action)
            throws CommandSyntaxException {
        for (ServerPlayer player : requireControllableTargets(context)) {
            if (player instanceof BotPlayer bot)
                InventorySubtree.requireNoScreen(bot);
            action.accept(PlayerControllers.of(player));
        }
        return 1;
    }

    public static Command<CommandSourceStack> control(Consumer<PlayerController> action) {
        return c -> control(c, action);
    }

    public static int controlAndStopPath(CommandContext<CommandSourceStack> context,
                                         Consumer<PlayerController> action)
            throws CommandSyntaxException {
        for (ServerPlayer player : requireControllableTargets(context)) {
            if (player instanceof BotPlayer bot) {
                InventorySubtree.requireNoScreen(bot);
                bot.clearPathFollower();
            }
            action.accept(PlayerControllers.of(player));
        }
        return 1;
    }

    public static Command<CommandSourceStack> controlAndStopPath(Consumer<PlayerController> action) {
        return c -> controlAndStopPath(c, action);
    }
}
