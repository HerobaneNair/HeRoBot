package hero.bane.herobot.common.voice;

import java.util.List;
import java.util.function.Supplier;

final class FrameSupplier implements Supplier<short[]> {
    private final List<short[]> frames;
    private final boolean loop;
    private int index;

    FrameSupplier(List<short[]> frames, boolean loop) {
        this.frames = frames;
        this.loop = loop;
    }

    @Override
    public short[] get() {
        if (index >= frames.size()) {
            if (!loop) return null;
            index = 0;
        }
        return frames.get(index++);
    }
}
