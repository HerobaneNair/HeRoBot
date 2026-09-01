package hero.bane.herobot.common.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public final class VoiceEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeroBotVoice");

    private static final Map<UUID, Speaker> SPEAKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LINKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> DISTANCES = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> GROUPS = new ConcurrentHashMap<>();

    private static volatile VoicechatServerApi serverApi;
    private static volatile VoicechatClientApi clientApi;
    private static volatile BooleanSupplier localMode = () -> false;
    private static volatile Function<UUID, Object> entityResolver = id -> null;
    private static volatile Supplier<Collection<UUID>> onlinePlayers = List::of;
    private static volatile Supplier<Collection<UUID>> botPlayers = List::of;

    private VoiceEngine() {}

    public static Logger logger() {
        return LOGGER;
    }

    public static void setEntityResolver(Function<UUID, Object> resolver) {
        entityResolver = resolver == null ? id -> null : resolver;
    }

    public static void setOnlinePlayers(Supplier<Collection<UUID>> supplier) {
        onlinePlayers = supplier == null ? List::of : supplier;
    }

    public static void setBotPlayers(Supplier<Collection<UUID>> supplier) {
        botPlayers = supplier == null ? List::of : supplier;
    }

    public static void refreshBotStates() {
        VoicechatServerApi api = serverApi;
        if (api == null) return;

        for (UUID id : botPlayers.get()) {
            try {
                VoicechatConnection connection = api.getConnectionOf(id);
                if (connection == null || connection.isInstalled()) continue;
                connection.setConnected(true);
                connection.setDisabled(GROUPS.get(id) == null);
            } catch (Throwable t) {
                LOGGER.warn("Failed to update the voice state of {}: {}", id, t.toString());
            }
        }
    }

    public static void setLocalMode(BooleanSupplier supplier) {
        localMode = supplier == null ? () -> false : supplier;
    }

    static void setServerApi(VoicechatServerApi api) {
        serverApi = api;
    }

    static void setClientApi(VoicechatClientApi api) {
        clientApi = api;
    }

    static VoicechatServerApi serverApi() {
        return serverApi;
    }

    static VoicechatClientApi clientApi() {
        return clientApi;
    }

    static Object resolveEntity(UUID id) {
        try {
            return entityResolver.apply(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static VoicechatApi api() {
        VoicechatApi api = serverApi;
        return api != null ? api : clientApi;
    }

    public static boolean useLocal() {
        try {
            return localMode.getAsBoolean() && clientApi != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean ready() {
        return useLocal() || serverApi != null;
    }

    public static boolean playSound(UUID speakerId, short[] pcm, boolean loop) {
        if (pcm == null || pcm.length == 0 || !ready()) return false;
        return speaker(speakerId).playFile(pcm, loop);
    }

    public static void stopSound(UUID speakerId) {
        Speaker speaker = SPEAKERS.get(speakerId);
        if (speaker != null) speaker.stopAudio();
    }

    public static boolean isSpeaking(UUID speakerId) {
        Speaker speaker = SPEAKERS.get(speakerId);
        return speaker != null && speaker.isSpeaking();
    }

    public static boolean startBluetooth(UUID speakerId, UUID sourceId) {
        if (sourceId == null || !ready()) return false;
        if (speakerId.equals(sourceId)) return false;
        if (!speaker(speakerId).startBluetooth(sourceId)) return false;
        LINKS.put(speakerId, sourceId);
        return true;
    }

    public static void stopBluetooth(UUID speakerId) {
        LINKS.remove(speakerId);
        Speaker speaker = SPEAKERS.get(speakerId);
        if (speaker != null) speaker.stopBluetooth();
    }

    public static UUID bluetoothSource(UUID speakerId) {
        return LINKS.get(speakerId);
    }

    public static boolean isBluetoothed(UUID speakerId) {
        return LINKS.containsKey(speakerId);
    }

    public static void setDistance(UUID speakerId, float distance) {
        if (distance <= 0f) DISTANCES.remove(speakerId);
        else DISTANCES.put(speakerId, distance);
        Speaker speaker = SPEAKERS.get(speakerId);
        if (speaker != null) speaker.setDistance(distance);
    }

    public static String joinGroup(UUID speakerId, String name, String password) {
        VoicechatServerApi api = serverApi;
        if (api == null) return "Simple Voice Chat is not running yet";

        VoicechatConnection connection = api.getConnectionOf(speakerId);
        if (connection == null) return "Simple Voice Chat does not track that player";

        Group target = findGroup(api, name);
        if (target == null)
            return "There is no voice group called '" + name + "'";
        if (target.hasPassword() && (password == null || password.isEmpty()))
            return "The voice group '" + target.getName() + "' needs a password";

        Group attempt;
        try {
            attempt = api.groupBuilder()
                    .setId(target.getId())
                    .setName(target.getName())
                    .setPassword(password)
                    .setPersistent(false)
                    .build();
        } catch (Throwable t) {
            return "Could not join '" + target.getName() + "': " + t.getMessage();
        }

        return apply(api, speakerId, connection, attempt, target.getId(),
                target.hasPassword()
                        ? "Wrong password for the voice group '" + target.getName() + "'"
                        : "Could not join the voice group '" + target.getName() + "'");
    }

    public static String createGroup(UUID speakerId, String name, String password) {
        VoicechatServerApi api = serverApi;
        if (api == null) return "Simple Voice Chat is not running yet";

        VoicechatConnection connection = api.getConnectionOf(speakerId);
        if (connection == null) return "Simple Voice Chat does not track that player";

        Group clash = findGroupExact(api, name);
        if (clash != null)
            return "A voice group called '" + clash.getName() + "' already exists";

        Group created;
        try {
            created = api.groupBuilder()
                    .setName(name)
                    .setPassword(password == null || password.isEmpty() ? null : password)
                    .setPersistent(true)
                    .build();
        } catch (Throwable t) {
            return "Could not create '" + name + "': " + t.getMessage();
        }

        return apply(api, speakerId, connection, created, created.getId(),
                "Could not create the voice group '" + name + "'");
    }

    private static String apply(VoicechatServerApi api, UUID speakerId, VoicechatConnection connection,
                                Group group, UUID expectedId, String failure) {
        try {
            connection.setGroup(group);
        } catch (Throwable t) {
            LOGGER.warn("Failed to put {} into group '{}': {}", speakerId, group.getName(), t.toString());
            return failure;
        }

        VoicechatConnection joined = api.getConnectionOf(speakerId);
        Group actual = joined == null ? null : joined.getGroup();
        if (actual == null || !expectedId.equals(actual.getId())) return failure;

        GROUPS.put(speakerId, expectedId);
        respawnSpeaker(speakerId);
        refreshBotStates();
        return null;
    }

    public static String leaveGroup(UUID speakerId) {
        VoicechatServerApi api = serverApi;
        if (api == null) return "Simple Voice Chat is not running yet";

        GROUPS.remove(speakerId);

        VoicechatConnection connection = api.getConnectionOf(speakerId);
        if (connection != null) {
            try {
                connection.setGroup(null);
            } catch (Throwable t) {
                LOGGER.warn("Failed to remove {} from its group: {}", speakerId, t.toString());
                return "Could not leave the voice group";
            }
        }

        respawnSpeaker(speakerId);
        refreshBotStates();
        return null;
    }

    public static List<String> groupNames() {
        VoicechatServerApi api = serverApi;
        if (api == null) return List.of();
        return api.getGroups().stream().filter(g -> !g.isHidden()).map(Group::getName).toList();
    }

    public static UUID groupOf(UUID speakerId) {
        return GROUPS.get(speakerId);
    }

    static void refreshGroupTargets(StaticAudioChannel channel, UUID groupId) {
        VoicechatServerApi api = serverApi;
        if (api == null || groupId == null) return;

        channel.clearTargets();
        for (UUID playerId : onlinePlayers.get()) {
            VoicechatConnection connection = api.getConnectionOf(playerId);
            if (connection == null) continue;
            Group group = connection.getGroup();
            if (group != null && groupId.equals(group.getId())) channel.addTarget(connection);
        }
    }

    public static void onMicrophonePacket(UUID senderId, byte[] opusData) {
        if (senderId == null || opusData == null || LINKS.isEmpty()) return;
        for (Map.Entry<UUID, UUID> entry : LINKS.entrySet()) {
            if (!senderId.equals(entry.getValue())) continue;
            Speaker speaker = SPEAKERS.get(entry.getKey());
            if (speaker == null) continue;
            try {
                speaker.acceptOpus(opusData);
            } catch (Throwable t) {
                LOGGER.warn("Failed to relay voice to {}: {}", entry.getKey(), t.toString());
            }
        }
    }

    public static void forget(UUID speakerId) {
        LINKS.remove(speakerId);
        DISTANCES.remove(speakerId);
        GROUPS.remove(speakerId);
        Speaker speaker = SPEAKERS.remove(speakerId);
        if (speaker != null) speaker.close();
    }

    public static void reset() {
        LINKS.clear();
        DISTANCES.clear();
        GROUPS.clear();
        for (Speaker speaker : SPEAKERS.values()) speaker.close();
        SPEAKERS.clear();
        SoundLibrary.clearCache();
    }

    private static Group findGroupExact(VoicechatServerApi api, String name) {
        for (Group group : api.getGroups()) {
            if (group.getName().equalsIgnoreCase(name.trim())) return group;
        }
        return null;
    }

    private static Group findGroup(VoicechatServerApi api, String name) {
        Group exact = findGroupExact(api, name);
        if (exact != null) return exact;
        String prefix = name.trim().toLowerCase(Locale.ROOT);
        for (Group group : api.getGroups()) {
            if (group.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) return group;
        }
        return null;
    }

    private static void respawnSpeaker(UUID speakerId) {
        Speaker existing = SPEAKERS.get(speakerId);
        if (existing == null) return;

        UUID source = LINKS.get(speakerId);
        existing.close();
        SPEAKERS.remove(speakerId);
        if (source != null) startBluetooth(speakerId, source);
    }

    private static Speaker speaker(UUID speakerId) {
        UUID group = GROUPS.get(speakerId);
        Speaker.Mode mode = group != null
                ? Speaker.Mode.GROUP
                : useLocal() ? Speaker.Mode.LOCAL : Speaker.Mode.ENTITY;

        Speaker existing = SPEAKERS.get(speakerId);
        if (existing != null && existing.mode() == mode
                && java.util.Objects.equals(existing.groupId(), group)) {
            return existing;
        }
        if (existing != null) existing.close();

        Speaker created = new Speaker(speakerId, mode, group);
        Float override = DISTANCES.get(speakerId);
        if (override != null) created.setDistance(override);
        SPEAKERS.put(speakerId, created);
        return created;
    }
}
