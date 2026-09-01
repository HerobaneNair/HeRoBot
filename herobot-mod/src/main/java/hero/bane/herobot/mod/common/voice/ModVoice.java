package hero.bane.herobot.mod.common.voice;

import hero.bane.herobot.common.voice.SoundLibrary;
import hero.bane.herobot.common.voice.VoiceEngine;
import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.mod.common.bot.BotPlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ModVoice {
    public static final String VOICECHAT_MOD_ID = "voicechat";

    private static final int STATE_REFRESH_TICKS = 20;

    private static boolean installed;
    private static int tickCounter;

    private ModVoice() {}

    public static void init() {
        installed = FabricLoader.getInstance().isModLoaded(VOICECHAT_MOD_ID);
        if (installed) bind();
    }

    private static void bind() {
        VoiceEngine.setEntityResolver(ModVoice::playerByUuid);
        VoiceEngine.setLocalMode(ModVoice::singleplayer);
        VoiceEngine.setOnlinePlayers(ModVoice::onlinePlayers);
        VoiceEngine.setBotPlayers(ModVoice::botPlayers);
    }

    public static void tick(MinecraftServer server) {
        if (!installed) return;
        if (++tickCounter < STATE_REFRESH_TICKS) return;
        tickCounter = 0;
        refreshBotStates();
    }

    private static void refreshBotStates() {
        VoiceEngine.refreshBotStates();
    }

    public static boolean installed() {
        return installed;
    }

    public static boolean ready() {
        return installed && VoiceEngine.ready();
    }

    public static void shutdown() {
        if (installed) unbind();
    }

    private static void unbind() {
        VoiceEngine.reset();
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

    private static Collection<UUID> botPlayers() {
        MinecraftServer server = HeroBot.currentServer;
        if (server == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer) ids.add(player.getUUID());
        }
        return ids;
    }

    private static Object playerByUuid(UUID id) {
        MinecraftServer server = HeroBot.currentServer;
        if (server == null) return null;
        return server.getPlayerList().getPlayer(id);
    }

    private static Collection<UUID> onlinePlayers() {
        MinecraftServer server = HeroBot.currentServer;
        if (server == null) return List.of();
        List<UUID> ids = new ArrayList<>();
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            ids.add(player.getUUID());
        }
        return ids;
    }

    private static boolean singleplayer() {
        MinecraftServer server = HeroBot.currentServer;
        return server != null && server.isSingleplayer() && !server.isPublished();
    }
}
