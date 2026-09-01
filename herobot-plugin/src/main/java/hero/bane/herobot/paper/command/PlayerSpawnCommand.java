package hero.bane.herobot.paper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import hero.bane.herobot.paper.bot.BotPlayer;
import hero.bane.herobot.paper.config.BotNameSuggestions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public final class PlayerSpawnCommand {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private static final DynamicCommandExceptionType ERROR_NOT_A_NAME =
            new DynamicCommandExceptionType(name -> Component.literal(
                    "'" + name + "' is not a player name - spawn needs a plain name, not a target selector"));

    private static final DynamicCommandExceptionType ERROR_INVALID_CARDINAL =
            new DynamicCommandExceptionType(dir -> Component.literal("Unknown direction '" + dir + "'"));

    private static final PermissionCheck PERMISSION_CHECK =
            new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

    private PlayerSpawnCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("playerspawn")
                        .requires(Commands.hasPermission(PERMISSION_CHECK))
                        .then(appendSpawnOptions(
                                argument("player", StringArgumentType.word())
                                        .suggests((c, b) -> suggest(getNameSuggestions(c.getSource()), b))))
        );
    }

    public static LiteralArgumentBuilder<CommandSourceStack> spawnSubtree() {
        return appendSpawnOptions(literal("spawn"));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T appendSpawnOptions(T builder) {
        return builder
                .executes(PlayerSpawnCommand::spawn)
                .then(literal("at")
                        .then(argument("position", Vec3Argument.vec3())
                                .executes(PlayerSpawnCommand::spawn)
                                .then(literal("facing")
                                        .then(argument("direction", RotationArgument.rotation())
                                                .executes(PlayerSpawnCommand::spawn)
                                                .then(inSubtree()))
                                        .then(argument("cardinal", StringArgumentType.word())
                                                .suggests((c, b) -> suggest(
                                                        new String[]{"north", "south", "east", "west", "up", "down"}, b))
                                                .executes(PlayerSpawnCommand::spawn)
                                                .then(inSubtree())))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> inSubtree() {
        return literal("in")
                .then(argument("gamemode", GameModeArgument.gameMode())
                        .executes(PlayerSpawnCommand::spawn)
                        .then(literal("on")
                                .then(argument("dimension", DimensionArgument.dimension())
                                        .executes(PlayerSpawnCommand::spawn))));
    }

    public static String resolveName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            if (!(node.getNode() instanceof ArgumentCommandNode<?, ?>)) continue;
            String argName = node.getNode().getName();
            if (argName.equals("player")) {
                return StringArgumentType.getString(context, "player");
            }
            if (argName.equals("targets")) {
                StringRange range = node.getRange();
                String raw = context.getInput().substring(range.getStart(), range.getEnd());
                if (!VALID_NAME.matcher(raw).matches()) throw ERROR_NOT_A_NAME.create(raw);
                return raw;
            }
        }
        return StringArgumentType.getString(context, "player");
    }

    public static Set<String> getNameSuggestions(CommandSourceStack source) {
        Set<String> names = new LinkedHashSet<>();
        names.add("HerobaneNair");
        names.add("herosbot");
        names.add("Steve");
        names.add("Alex");
        names.addAll(BotNameSuggestions.all());
        names.removeAll(source.getOnlinePlayerNames());
        return names;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = resolveName(context);
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();

        if (BotPlayer.isSpawningPlayer(name)) {
            source.sendFailure(Component.literal(name + " is already being spawned"));
            return 0;
        }
        if (playerList.getPlayerByName(name) != null) {
            source.sendFailure(Component.literal(name + " is already online"));
            return 0;
        }
        if (name.length() > 16) {
            source.sendFailure(Component.literal(name + " is not a valid player name"));
            return 0;
        }

        Vec3 pos = hasNode(context, "position")
                ? Vec3Argument.getVec3(context, "position")
                : source.getPosition();

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) {
            source.sendFailure(Component.literal("Cannot spawn outside of the world"));
            return 0;
        }

        Vec2 rot = hasNode(context, "direction")
                ? RotationArgument.getRotation(context, "direction").getRotation(source)
                : hasNode(context, "cardinal")
                ? cardinalRotation(StringArgumentType.getString(context, "cardinal"))
                : source.getRotation();

        GameType mode = hasNode(context, "gamemode")
                ? GameModeArgument.getGameMode(context, "gamemode")
                : defaultGameMode(source);

        ServerLevel level = hasNode(context, "dimension")
                ? DimensionArgument.getDimension(context, "dimension")
                : source.getLevel();

        if (!BotPlayer.spawn(server, level, name, pos, rot.y, rot.x, mode)) {
            source.sendFailure(Component.literal("Could not spawn " + name));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawning " + name), true);
        return 1;
    }

    private static boolean hasNode(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals(name));
    }

    private static GameType defaultGameMode(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.gameMode.getGameModeForPlayer();
        }
        return source.getServer().getDefaultGameType();
    }

    private static Vec2 cardinalRotation(String direction) throws CommandSyntaxException {
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "north" -> new Vec2(0, 180);
            case "south" -> new Vec2(0, 0);
            case "east" -> new Vec2(0, -90);
            case "west" -> new Vec2(0, 90);
            case "up" -> new Vec2(-90, 0);
            case "down" -> new Vec2(90, 0);
            default -> throw ERROR_INVALID_CARDINAL.create(direction);
        };
    }
}
