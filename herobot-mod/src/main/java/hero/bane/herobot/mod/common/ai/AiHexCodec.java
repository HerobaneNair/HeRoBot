package hero.bane.herobot.mod.common.ai;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class AiHexCodec {
    public static final byte VERSION = 0x01;

    private AiHexCodec() {}

    public static String encode(AiScript script) {
        byte[] json = AiScriptIO.toJson(script).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(json);

        byte[] framed = new byte[compressed.length + 1];
        framed[0] = VERSION;
        System.arraycopy(compressed, 0, framed, 1, compressed.length);

        return HexFormat.of().formatHex(framed);
    }

    public static AiScript decode(String hex, String fallbackName) {
        if (hex == null) throw new IllegalArgumentException("hex is null");
        String trimmed = hex.trim();

        byte[] framed;
        try {
            framed = HexFormat.of().parseHex(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a valid hex string", e);
        }
        if (framed.length < 1) throw new IllegalArgumentException("hex is empty");
        if (framed[0] != VERSION) {
            throw new IllegalArgumentException("unsupported AI hex version: 0x"
                    + Integer.toHexString(framed[0] & 0xFF));
        }

        byte[] json = inflate(framed, 1, framed.length - 1);
        try {
            return AiScriptIO.fromJson(new String(json, StandardCharsets.UTF_8), fallbackName);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("payload is not a valid AI script", e);
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, data.length / 3));
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

    private static byte[] inflate(byte[] data, int offset, int length) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data, offset, length);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, length * 3));
            byte[] buf = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IllegalArgumentException("truncated AI hex payload");
                    }
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("corrupt AI hex payload", e);
        } finally {
            inflater.end();
        }
    }
}
