package hero.bane.herobot.paper.voice;

import hero.bane.herobot.common.voice.VoiceEngine;
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
        if (!PluginVoice.installed()) return NO_VOICECHAT;
        if (!VoiceEngine.ready()) return NOT_READY;
        return null;
    }

    public static List<String> soundNames(MinecraftServer server) {
        if (!PluginVoice.installed() || server == null) return List.of();
        return PluginVoice.listSounds(server);
    }

    public static List<String> groupNames() {
        if (!PluginVoice.installed()) return List.of();
        return VoiceEngine.groupNames();
    }

    public static String play(ServerPlayer speaker, String name, boolean loop) {
        String blocked = unavailable();
        if (blocked != null) return blocked;

        MinecraftServer server = speaker.level().getServer();

        short[] pcm;
        try {
            pcm = PluginVoice.loadSound(server, name);
        } catch (IOException e) {
            return "Could not read sound '" + name + "': " + e.getMessage();
        }
        if (pcm == null) return "No sound named '" + name + "' in the herobot_sounds folder";

        if (!VoiceEngine.playSound(speaker.getUUID(), pcm, loop))
            return "Could not open a voice channel for " + speaker.getGameProfile().name();
        return null;
    }

    public static String stop(ServerPlayer speaker) {
        if (!PluginVoice.installed()) return NO_VOICECHAT;
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
        if (!PluginVoice.installed()) return NO_VOICECHAT;
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
        if (!PluginVoice.installed()) return NO_VOICECHAT;
        VoiceEngine.setDistance(speaker.getUUID(), blocks);
        return null;
    }

    public static boolean isSpeaking(ServerPlayer speaker) {
        return PluginVoice.installed() && VoiceEngine.isSpeaking(speaker.getUUID());
    }

    public static boolean isBluetoothed(ServerPlayer speaker) {
        return PluginVoice.installed() && VoiceEngine.isBluetoothed(speaker.getUUID());
    }

    public static boolean inGroup(ServerPlayer speaker) {
        return PluginVoice.installed() && VoiceEngine.groupOf(speaker.getUUID()) != null;
    }

    public static void forget(UUID speakerId) {
        if (PluginVoice.installed()) VoiceEngine.forget(speakerId);
    }
}
