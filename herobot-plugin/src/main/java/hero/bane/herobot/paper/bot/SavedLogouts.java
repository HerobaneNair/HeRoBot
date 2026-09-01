package hero.bane.herobot.paper.bot;

import hero.bane.herobot.common.bot.PlayerLogouts;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class SavedLogouts {

    private SavedLogouts() {
    }

    public static UUID resolve(MinecraftServer server, String username) {
        return BotPlayer.resolveId(server, username);
    }

    public static PlayerLogouts.Logout read(MinecraftServer server, UUID id, String username) {
        if (id == null) return null;
        Optional<ValueInput> input = server.getPlayerList()
                .loadPlayerData(new NameAndId(id, username))
                .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), tag));
        ServerPlayer.SavedPosition saved = input
                .flatMap(value -> value.read(ServerPlayer.SavedPosition.MAP_CODEC))
                .orElse(ServerPlayer.SavedPosition.EMPTY);

        Vec3 pos = saved.position().orElse(null);
        if (pos == null) return null;
        ResourceKey<Level> dimension = saved.dimension().orElse(Level.OVERWORLD);
        Vec2 rotation = saved.rotation().orElse(Vec2.ZERO);

        return new PlayerLogouts.Logout(id, username, dimension.identifier().toString(),
                pos.x, pos.y, pos.z, rotation.x, rotation.y, 0L);
    }
}
