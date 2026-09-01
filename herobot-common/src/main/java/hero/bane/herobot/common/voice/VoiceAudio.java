package hero.bane.herobot.common.voice;

import java.util.ArrayList;
import java.util.List;

public final class VoiceAudio {
    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SIZE = (SAMPLE_RATE / 1000) * 20;

    private VoiceAudio() {}

    public static short[] toMono(short[] samples, int channels) {
        if (channels <= 1) return samples;
        int frames = samples.length / channels;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += samples[i * channels + c];
            out[i] = (short) (sum / channels);
        }
        return out;
    }

    public static short[] resample(short[] mono, int sourceRate) {
        if (sourceRate == SAMPLE_RATE || mono.length == 0) return mono;
        long targetLength = (long) mono.length * SAMPLE_RATE / sourceRate;
        if (targetLength <= 0) return new short[0];
        short[] out = new short[(int) targetLength];
        double step = (double) sourceRate / SAMPLE_RATE;
        for (int i = 0; i < out.length; i++) {
            double src = i * step;
            int index = (int) src;
            double frac = src - index;
            short a = mono[Math.min(index, mono.length - 1)];
            short b = mono[Math.min(index + 1, mono.length - 1)];
            out[i] = (short) Math.round(a + (b - a) * frac);
        }
        return out;
    }

    public static short[] normalize(short[] samples, int sourceRate, int channels) {
        return resample(toMono(samples, channels), sourceRate);
    }

    public static List<short[]> frames(short[] pcm) {
        List<short[]> frames = new ArrayList<>(Math.max(1, pcm.length / FRAME_SIZE + 1));
        for (int offset = 0; offset < pcm.length; offset += FRAME_SIZE) {
            short[] frame = new short[FRAME_SIZE];
            int length = Math.min(FRAME_SIZE, pcm.length - offset);
            System.arraycopy(pcm, offset, frame, 0, length);
            frames.add(frame);
        }
        return frames;
    }
}
