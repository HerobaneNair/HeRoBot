package hero.bane.herobot.networking;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ScriptCompression {
    public static final int CHUNK_SIZE = 30000;
    public static final int MAX_CHUNK = 1 << 16;

    private ScriptCompression() {}

    public static byte[] compress(String text) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, raw.length / 3));
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static String decompress(byte[] data) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length * 3));
            byte[] buf = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("truncated compressed payload");
                }
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("corrupt compressed payload", e);
        } finally {
            inflater.end();
        }
    }

    public static List<byte[]> chunk(byte[] data) {
        List<byte[]> out = new ArrayList<>();
        if (data.length == 0) {
            out.add(new byte[0]);
            return out;
        }
        for (int off = 0; off < data.length; off += CHUNK_SIZE) {
            out.add(Arrays.copyOfRange(data, off, Math.min(off + CHUNK_SIZE, data.length)));
        }
        return out;
    }
}
