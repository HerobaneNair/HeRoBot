package hero.bane.herobot.common.voice;

import de.maxhenkel.voicechat.api.audiochannel.ClientEntityAudioChannel;

import java.util.List;
import java.util.UUID;

final class LocalPump extends Thread {
    private static final long FRAME_NANOS = 20_000_000L;

    private final ClientEntityAudioChannel channel;
    private final List<short[]> frames;
    private final boolean loop;
    private final Runnable onStopped;

    private volatile boolean cancelled;

    LocalPump(UUID speakerId, ClientEntityAudioChannel channel, List<short[]> frames, boolean loop, Runnable onStopped) {
        this.channel = channel;
        this.frames = frames;
        this.loop = loop;
        this.onStopped = onStopped;
        setDaemon(true);
        setName("HeroBotVoice-" + speakerId);
    }

    void cancel() {
        cancelled = true;
        interrupt();
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        long start = System.nanoTime();
        long position = 0;
        try {
            int index = 0;
            while (!cancelled) {
                if (index >= frames.size()) {
                    if (!loop) break;
                    index = 0;
                }
                channel.play(frames.get(index++));
                position++;
                long wait = start + position * FRAME_NANOS - System.nanoTime();
                if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable t) {
            VoiceEngine.logger().warn("HeroBot local voice playback failed: {}", t.toString());
        } finally {
            onStopped.run();
        }
    }
}
