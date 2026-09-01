package hero.bane.herobot.mod.common.voice;

import hero.bane.herobot.common.voice.VoiceEngine;
import hero.bane.herobot.mod.common.HeroBot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class VoiceOps {
    public static final String NO_VOICECHAT = "Simple Voice Chat is not installed on this server";
    public static final String NOT_READY = "Simple Voice Chat is not running yet";

    private VoiceOps() {}

    public static String unavailable() {
        if (!ModVoice.installed()) return NO_VOICECHAT;
        if (!VoiceEngine.ready()) return NOT_READY;
        return null;
    }

    public static List<String> soundNames(MinecraftServer server) {
        if (!ModVoice.installed() || server == null) return List.of();
        return ModVoice.listSounds(server);
    }

    public static List<String> groupNames() {
        if (!ModVoice.installed()) return List.of();
        return VoiceEngine.groupNames();
    }

    public static String play(ServerPlayer speaker, String name, boolean loop) {
        String blocked = unavailable();
        if (blocked != null) return blocked;

        MinecraftServer server = speaker.level().getServer();
        if (server == null) server = HeroBot.currentServer;
        if (server == null) return NOT_READY;

        short[] pcm;
        try {
            pcm = ModVoice.loadSound(server, name);
        } catch (IOException e) {
            return "Could not read sound '" + name + "': " + e.getMessage();
        }
        if (pcm == null) return "No sound named '" + name + "' in the herobot_sounds folder";

        if (!VoiceEngine.playSound(speaker.getUUID(), pcm, loop))
            return "Could not open a voice channel for " + speaker.getGameProfile().name();
        return null;
    }

    public static String stop(ServerPlayer speaker) {
        if (!ModVoice.installed()) return NO_VOICECHAT;
        VoiceEngine.stopSound(speaker.getUUID());
        return null;
    }

    public static String bluetooth(ServerPlayer speaker, UUID sourceId) {
        String blocked = unavailable();
        if (blocked != null) return blocked;
        if (sourceId == null) return "No source player to listen to";
        if (speaker.getUUID().equals(sourceId)) return "A bot cannot bluetooth to itself";

        if (!VoiceEngine.startBluetooth(speaker.getUUID(), sourceId))
            return "Could not open a voice channel for " + speaker.getGameProfile().name();
        return null;
    }

    public static String stopBluetooth(ServerPlayer speaker) {
        if (!ModVoice.installed()) return NO_VOICECHAT;
        VoiceEngine.stopBluetooth(speaker.getUUID());
        return null;
    }

    public static String joinGroup(ServerPlayer speaker, String name, String password) {
        String blocked = unavailable();
        if (blocked != null) return blocked;
        if (name == null || name.isBlank()) return "Group name cannot be empty";
        return VoiceEngine.joinGroup(speaker.getUUID(), name.trim(), password);
    }

    public static String createGroup(ServerPlayer speaker, String name, String password) {
        String blocked = unavailable();
        if (blocked != null) return blocked;
        if (name == null || name.isBlank()) return "Group name cannot be empty";
        return VoiceEngine.createGroup(speaker.getUUID(), name.trim(), password);
    }

    public static String leaveGroup(ServerPlayer speaker) {
        String blocked = unavailable();
        if (blocked != null) return blocked;
        return VoiceEngine.leaveGroup(speaker.getUUID());
    }

    public static String setDistance(ServerPlayer speaker, float blocks) {
        if (!ModVoice.installed()) return NO_VOICECHAT;
        VoiceEngine.setDistance(speaker.getUUID(), blocks);
        return null;
    }

    public static boolean isSpeaking(ServerPlayer speaker) {
        return ModVoice.installed() && VoiceEngine.isSpeaking(speaker.getUUID());
    }

    public static boolean isBluetoothed(ServerPlayer speaker) {
        return ModVoice.installed() && VoiceEngine.isBluetoothed(speaker.getUUID());
    }

    public static boolean inGroup(ServerPlayer speaker) {
        return ModVoice.installed() && VoiceEngine.groupOf(speaker.getUUID()) != null;
    }

    public static void forget(UUID speakerId) {
        if (ModVoice.installed()) VoiceEngine.forget(speakerId);
    }
}
