package hero.bane.herobot.common.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.ClientEntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.List;
import java.util.UUID;

final class Speaker {
    enum Mode {
        LOCAL,
        ENTITY,
        GROUP
    }

    private static final long TARGET_REFRESH_NANOS = 1_000_000_000L;

    private final UUID id;
    private final Mode mode;
    private final UUID groupId;

    private ClientEntityAudioChannel clientChannel;
    private EntityAudioChannel entityChannel;
    private StaticAudioChannel staticChannel;
    private Object boundEntity;
    private long lastTargetRefresh;

    private AudioPlayer player;
    private LocalPump pump;
    private OpusDecoder decoder;

    private UUID bluetoothSource;
    private boolean playingFile;
    private int generation;

    private float distance = -1f;

    Speaker(UUID id, Mode mode, UUID groupId) {
        this.id = id;
        this.mode = mode;
        this.groupId = groupId;
    }

    Mode mode() {
        return mode;
    }

    UUID groupId() {
        return groupId;
    }

    synchronized boolean isSpeaking() {
        return bluetoothSource != null || playingFile;
    }

    synchronized void setDistance(float distance) {
        this.distance = distance;
        applyDistance();
    }

    synchronized boolean playFile(short[] pcm, boolean loop) {
        stopAudio();
        List<short[]> frames = VoiceAudio.frames(pcm);
        if (frames.isEmpty() || !ensureChannel()) return false;

        int gen = ++generation;
        playingFile = true;

        if (mode == Mode.LOCAL) {
            pump = new LocalPump(id, clientChannel, frames, loop, () -> playbackStopped(gen));
            pump.start();
            return true;
        }

        VoicechatServerApi api = VoiceEngine.serverApi();
        if (api == null) {
            playingFile = false;
            return false;
        }
        player = api.createAudioPlayer(outChannel(), api.createEncoder(), new FrameSupplier(frames, loop));
        player.setOnStopped(() -> playbackStopped(gen));
        player.startPlaying();
        return true;
    }

    synchronized boolean startBluetooth(UUID source) {
        stopAudio();
        if (!ensureChannel()) return false;
        bluetoothSource = source;
        return true;
    }

    synchronized void stopBluetooth() {
        bluetoothSource = null;
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Throwable ignored) {
            }
            decoder = null;
        }
        flush();
    }

    synchronized void acceptOpus(byte[] opusData) {
        if (bluetoothSource == null || !ensureChannel()) return;

        if (mode == Mode.LOCAL) {
            if (decoder == null) {
                VoicechatClientApi api = VoiceEngine.clientApi();
                if (api == null) return;
                decoder = api.createDecoder();
            }
            short[] raw = decoder.decode(opusData);
            if (raw != null && raw.length > 0) clientChannel.play(raw);
        } else {
            outChannel().send(opusData);
        }
    }

    synchronized void stopAudio() {
        generation++;
        playingFile = false;
        if (player != null) {
            player.stopPlaying();
            player = null;
        }
        if (pump != null) {
            pump.cancel();
            pump = null;
        }
        flush();
    }

    synchronized void stopAll() {
        stopBluetooth();
        stopAudio();
    }

    synchronized void close() {
        stopAll();
        if (staticChannel != null) staticChannel.clearTargets();
        clientChannel = null;
        entityChannel = null;
        staticChannel = null;
        boundEntity = null;
    }

    private synchronized void playbackStopped(int gen) {
        if (gen == generation) {
            playingFile = false;
            flush();
        }
    }

    private AudioChannel outChannel() {
        return mode == Mode.GROUP ? staticChannel : entityChannel;
    }

    private void flush() {
        AudioChannel channel = mode == Mode.LOCAL ? null : outChannel();
        if (channel != null && !channel.isClosed()) {
            try {
                channel.flush();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean ensureChannel() {
        return switch (mode) {
            case LOCAL -> ensureClientChannel();
            case ENTITY -> ensureEntityChannel();
            case GROUP -> ensureGroupChannel();
        };
    }

    private boolean ensureClientChannel() {
        VoicechatClientApi api = VoiceEngine.clientApi();
        if (api == null) return false;

        Object entity = VoiceEngine.resolveEntity(id);
        if (entity == null) return false;

        if (clientChannel == null || entity != boundEntity) {
            clientChannel = api.createEntityAudioChannel(id, api.fromEntity(entity));
            boundEntity = entity;
            applyDistance();
        }
        return clientChannel != null;
    }

    private boolean ensureEntityChannel() {
        VoicechatServerApi api = VoiceEngine.serverApi();
        if (api == null) return false;

        Object entity = VoiceEngine.resolveEntity(id);
        if (entity == null) return false;

        if (entityChannel == null || entityChannel.isClosed()) {
            entityChannel = api.createEntityAudioChannel(id, api.fromEntity(entity));
            boundEntity = entity;
            applyDistance();
        } else if (entity != boundEntity) {
            entityChannel.updateEntity(api.fromEntity(entity));
            boundEntity = entity;
        }
        return entityChannel != null;
    }

    private boolean ensureGroupChannel() {
        VoicechatServerApi api = VoiceEngine.serverApi();
        if (api == null) return false;

        if (staticChannel == null || staticChannel.isClosed()) {
            staticChannel = api.createStaticAudioChannel(id);
            if (staticChannel == null) return false;
            staticChannel.setBypassGroupIsolation(true);
            lastTargetRefresh = 0L;
        }

        long now = System.nanoTime();
        if (now - lastTargetRefresh >= TARGET_REFRESH_NANOS) {
            lastTargetRefresh = now;
            VoiceEngine.refreshGroupTargets(staticChannel, groupId);
        }
        return true;
    }

    private void applyDistance() {
        float value = distance;
        if (value <= 0f) {
            VoicechatApi api = VoiceEngine.api();
            if (api == null) return;
            value = (float) api.getVoiceChatDistance();
            if (value <= 0f) return;
        }
        if (entityChannel != null) entityChannel.setDistance(value);
        if (clientChannel != null) clientChannel.setDistance(value);
    }
}
