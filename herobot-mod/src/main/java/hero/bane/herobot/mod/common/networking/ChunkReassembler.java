package hero.bane.herobot.mod.common.networking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkReassembler {
    private static final class Buffer {
        final byte[][] chunks;
        int received;

        Buffer(int count) {
            this.chunks = new byte[count][];
        }
    }

    private final Map<String, Buffer> pending = new ConcurrentHashMap<>();

    public byte[] accept(String key, int index, int count, byte[] data) {
        if (count <= 1) {
            pending.remove(key);
            return data;
        }
        if (index < 0 || index >= count) return null;

        Buffer buf = pending.compute(key, (k, existing) ->
                (existing != null && existing.chunks.length == count) ? existing : new Buffer(count));
        if (buf.chunks[index] == null) {
            buf.chunks[index] = data;
            buf.received++;
        }
        if (buf.received < count) return null;

        pending.remove(key);
        int total = 0;
        for (byte[] c : buf.chunks) total += c.length;
        byte[] joined = new byte[total];
        int off = 0;
        for (byte[] c : buf.chunks) {
            System.arraycopy(c, 0, joined, off, c.length);
            off += c.length;
        }
        return joined;
    }

    public void discard(String key) {
        pending.remove(key);
    }
}
