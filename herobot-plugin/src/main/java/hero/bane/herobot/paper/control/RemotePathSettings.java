package hero.bane.herobot.paper.control;

import hero.bane.herobot.paper.bot.pathing.PathSettings;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
