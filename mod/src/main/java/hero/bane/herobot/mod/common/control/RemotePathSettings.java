package hero.bane.herobot.mod.common.control;

import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Server-side mirror of a real player's client-side PathSettings. Bots keep their settings on the
// server, but a remote player's live in ClientOps, so the server has nothing to read back. Every
// mutation the server sends as a ControlOp is applied here too, letting the getters report values.
// Both sides drop back to defaults on disconnect (see HeroBot / HeroBotClient) so they stay in step.
public final class RemotePathSettings {
    private RemotePathSettings() {}

    private static final Map<UUID, PathSettings> SETTINGS = new ConcurrentHashMap<>();

    public static PathSettings of(ServerPlayer player) {
        return SETTINGS.computeIfAbsent(player.getUUID(), id -> new PathSettings());
    }

    public static void clear(UUID id) {
        SETTINGS.remove(id);
    }
}
