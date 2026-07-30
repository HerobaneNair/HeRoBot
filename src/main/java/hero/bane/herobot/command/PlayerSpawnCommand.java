package hero.bane.herobot.command;

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
import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.config.BotNameSuggestions;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public class PlayerSpawnCommand {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]+");

    private static final DynamicCommandExceptionType ERROR_NOT_A_NAME =
            new DynamicCommandExceptionType(name -> Component.literal(
                    "'" + name + "' is not a player name - spawn needs a plain name, not a target selector"));

    private static final DynamicCommandExceptionType ERROR_INVALID_CARDINAL =
            new DynamicCommandExceptionType(dir -> Component.literal("Unknown direction '" + dir + "'"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("playerspawn")
                        .requires(s -> !s.isPlayer() || s.getServer().getPlayerList().isOp(Objects.requireNonNull(s.getPlayer()).nameAndId()))
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
                                                        new String[]{"north", "south", "east", "west", "up", "down", "~ ~"}, b))
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

    private static String resolveName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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

        if (BotPlayer.isSpawningPlayer(name)) return 0;
        if (playerList.getPlayerByName(name) != null) return 0;
        if (name.length() > maxNameLength(server)) return 0;

        boolean bypassWhitelist = !source.isPlayer()
                || playerList.isOp(Objects.requireNonNull(source.getPlayer()).nameAndId());

        Vec3 pos = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("position"))
                ? Vec3Argument.getVec3(context, "position")
                : source.getPosition();

        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) return 0;

        Vec2 rot =
                context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("direction"))
                        ? RotationArgument.getRotation(context, "direction").getRotation(source)
                        : context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("cardinal"))
                        ? cardinalRotation(StringArgumentType.getString(context, "cardinal"))
                        : source.getRotation();

        GameType mode = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("gamemode"))
                ? GameModeArgument.getGameMode(context, "gamemode")
                : defaultGameMode(source);

        ResourceKey<Level> dim = context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals("dimension"))
                ? DimensionArgument.getDimension(context, "dimension").dimension()
                : source.getLevel().dimension();

        boolean flying;
        if (mode == GameType.SPECTATOR) {
            flying = true;
        } else if (mode.isSurvival()) {
            flying = false;
        } else if (source.getEntity() instanceof ServerPlayer p) {
            flying = p.getAbilities().flying;
        } else {
            flying = false;
        }

        return BotPlayer.createFake(
                name,
                server,
                pos,
                rot.y,
                rot.x,
                dim,
                mode,
                flying,
                bypassWhitelist
        );
    }

    private static GameType defaultGameMode(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer p)) return GameType.SURVIVAL;
        GameType mode = p.gameMode.getGameModeForPlayer();
        return mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode;
    }

    private static int maxNameLength(MinecraftServer server) {
        return server.getPort() >= 0 ? SharedConstants.MAX_PLAYER_NAME_LENGTH : 40;
    }

    private static Vec2 cardinalRotation(String dir) throws CommandSyntaxException {
        return switch (dir) {
            case "south" -> new Vec2(0.0F, 0.0F);
            case "west" -> new Vec2(0.0F, 90.0F);
            case "north" -> new Vec2(0.0F, 180.0F);
            case "east" -> new Vec2(0.0F, -90.0F);
            case "up" -> new Vec2(-90.0F, 0.0F);
            case "down" -> new Vec2(90.0F, 0.0F);
            default -> throw ERROR_INVALID_CARDINAL.create(dir);
        };
    }
}
