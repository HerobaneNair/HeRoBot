package hero.bane.herobot.paper.voice;

import hero.bane.herobot.common.voice.SoundLibrary;
import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PluginVoice {
    public static final String VOICECHAT_PLUGIN_NAME = "voicechat";

    private static final int STATE_REFRESH_TICKS = 20;

    private static MinecraftServer currentServer;
    private static boolean installed;
    private static int tickCounter;

    private PluginVoice() {}

    public static void init(Plugin plugin, MinecraftServer server) {
        currentServer = server;
        installed = false;

        if (Bukkit.getPluginManager().getPlugin(VOICECHAT_PLUGIN_NAME) == null) {
            HeroBot.LOGGER.info("Simple Voice Chat is not installed; HeroBot voice features are disabled");
            return;
        }

        try {
            installed = VoicechatBinding.bind(plugin);
        } catch (LinkageError e) {
            HeroBot.LOGGER.warn("Simple Voice Chat is installed but its API is unusable: {}", e.toString());
        }
    }

    public static void tick() {
        if (!installed) return;
        if (++tickCounter < STATE_REFRESH_TICKS) return;
        tickCounter = 0;
        refreshBotStates();
    }

    private static void refreshBotStates() {
        VoicechatBinding.refreshBotStates();
    }

    public static boolean installed() {
        return installed;
    }

    public static void shutdown() {
        if (installed) unbind();
        currentServer = null;
    }

    private static void unbind() {
        VoicechatBinding.unbind();
    }

    public static MinecraftServer server() {
        return currentServer;
    }

    public static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    public static List<String> listSounds(MinecraftServer server) {
        return SoundLibrary.list(worldRoot(server));
    }

    public static short[] loadSound(MinecraftServer server, String name) throws IOException {
        return SoundLibrary.load(worldRoot(server), name);
    }

    static Collection<UUID> onlinePlayers() {
        MinecraftServer server = currentServer;
        if (server == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            ids.add(player.getUUID());
        }
        return ids;
    }

    static Collection<UUID> botPlayers() {
        MinecraftServer server = currentServer;
        if (server == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer) ids.add(player.getUUID());
        }
        return ids;
    }

    static Object playerByUuid(UUID id) {
        MinecraftServer server = currentServer;
        if (server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }
}
