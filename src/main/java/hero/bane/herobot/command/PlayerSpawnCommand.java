package hero.bane.herobot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import hero.bane.herobot.fakeplayer.FakePlayer;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public class PlayerSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                literal("playerspawn")
                        .requires(s -> !s.isPlayer() || s.getServer().getPlayerList().isOp(s.getPlayer().nameAndId()))
                        .then(argument("player", StringArgumentType.word())
                                .suggests((c, b) -> suggest(getNameSuggestions(c.getSource()), b))
                                .executes(PlayerSpawnCommand::spawn)
                                .then(literal("at")
                                        .then(argument("position", Vec3Argument.vec3())
                                                .executes(PlayerSpawnCommand::spawn)
                                                .then(literal("facing")
                                                        .then(argument("direction", RotationArgument.rotation())
                                                                .executes(PlayerSpawnCommand::spawn)
                                                                .then(literal("in")
                                                                        .then(argument("gamemode", GameModeArgument.gameMode())
                                                                                .executes(PlayerSpawnCommand::spawn)
                                                                                .then(literal("on")
                                                                                        .then(argument("dimension",
                                                                                                DimensionArgument.dimension())
                                                                                                .executes(PlayerSpawnCommand::spawn)
                                                                                        )))))
                                                        .then(argument("cardinal", StringArgumentType.word())
                                                                .suggests((c, b) -> suggest(
                                                                        new String[]{"north", "south", "east", "west", "up", "down", "0 0"}, b))
                                                                .executes(PlayerSpawnCommand::spawn)
                                                                .then(literal("in")
                                                                        .then(argument("gamemode", GameModeArgument.gameMode())
                                                                                .executes(PlayerSpawnCommand::spawn)
                                                                                .then(literal("on")
                                                                                        .then(argument("dimension",
                                                                                                DimensionArgument.dimension())
                                                                                                .executes(PlayerSpawnCommand::spawn)
                                                                                        )))))
                                                )
                                        )
                                )
                        )
        );
    }

    private static Set<String> getNameSuggestions(CommandSourceStack source) {
        Set<String> names = new LinkedHashSet<>();
        names.add("Steve");
        names.add("Alex");
        names.add("TheobaldTheBot");
        names.addAll(source.getOnlinePlayerNames());
        return names;
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        PlayerList playerList = server.getPlayerList();

        if (FakePlayer.isSpawningPlayer(name)) return 0;
        if (playerList.getPlayerByName(name) != null) return 0;
        if (name.length() > maxNameLength(server)) return 0;

        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
        if (uuid == null) {
            uuid = UUIDUtil.createOfflinePlayerUUID(name);
        }

        var profile = server.services().nameToIdCache().get(uuid).orElse(null);
        if (profile != null && playerList.getBans().isBanned(profile)) return 0;

        if (playerList.isUsingWhitelist()
                && profile != null
                && !playerList.isWhiteListed(profile)
                && source.isPlayer()
                && !playerList.isOp(source.getPlayer().nameAndId())) {
            return 0;
        }

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
                : GameType.CREATIVE;

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

        FakePlayer.createFake(
                name,
                server,
                pos,
                rot.y,
                rot.x,
                dim,
                mode,
                flying
        );
        return 1;
    }

    private static int maxNameLength(MinecraftServer server) {
        return server.getPort() >= 0 ? SharedConstants.MAX_PLAYER_NAME_LENGTH : 40;
    }

    private static Vec2 cardinalRotation(String dir) {
        return switch (dir) {
            case "south" -> new Vec2(0.0F, 0.0F);
            case "west" -> new Vec2(90.0F, 0.0F);
            case "north" -> new Vec2(180.0F, 0.0F);
            case "east" -> new Vec2(-90.0F, 0.0F);
            case "up" -> new Vec2(0.0F, -90.0F);
            case "down" -> new Vec2(0.0F, 90.0F);
            default -> throw new IllegalStateException();
        };
    }
}
