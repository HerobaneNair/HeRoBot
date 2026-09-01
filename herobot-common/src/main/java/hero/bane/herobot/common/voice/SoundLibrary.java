package hero.bane.herobot.common.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundLibrary {
    public static final String DIR_NAME = "herobot_sounds";
    private static final List<String> EXTENSIONS = List.of(".wav", ".mp3");
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_CACHED_SOUNDS = 16;

    private static final Map<String, short[]> CACHE = new ConcurrentHashMap<>();

    private SoundLibrary() {}

    public static Path soundsDir(Path worldRoot) throws IOException {
        Path dir = worldRoot.resolve(DIR_NAME);
        if (!Files.isDirectory(dir)) Files.createDirectories(dir);
        return dir;
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    public static List<String> list(Path worldRoot) {
        List<String> names = new ArrayList<>();
        try {
            Path dir = soundsDir(worldRoot);
            for (String ext : EXTENSIONS) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + ext)) {
                    for (Path p : stream) {
                        String file = p.getFileName().toString();
                        if (!names.contains(file)) names.add(file);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static Path resolve(Path worldRoot, String name) throws IOException {
        if (!validName(name)) return null;
        Path dir = soundsDir(worldRoot);
        for (String ext : EXTENSIONS) {
            Path candidate = dir.resolve(name + ext);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        Path direct = dir.resolve(name);
        if (Files.isRegularFile(direct) && hasKnownExtension(name)) return direct;
        return null;
    }

    public static short[] load(Path worldRoot, String name) throws IOException {
        Path file = resolve(worldRoot, name);
        if (file == null) return null;

        String key = file.toAbsolutePath() + "@" + Files.getLastModifiedTime(file).toMillis();
        short[] cached = CACHE.get(key);
        if (cached != null) return cached;

        if (Files.size(file) > MAX_FILE_BYTES)
            throw new IOException("Sound file is larger than " + (MAX_FILE_BYTES / 1024 / 1024) + "MB");

        short[] pcm = decode(file);
        if (CACHE.size() >= MAX_CACHED_SOUNDS) CACHE.clear();
        CACHE.put(key, pcm);
        return pcm;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static boolean hasKnownExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (lower.endsWith(ext)) return true;
        return false;
    }

    private static short[] decode(Path file) throws IOException {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3")) return decodeMp3(file);
        return decodeWav(file);
    }

    private static short[] decodeWav(Path file) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file));
             AudioInputStream in = AudioSystem.getAudioInputStream(raw)) {

            AudioFormat source = in.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    source.getSampleRate(),
                    16,
                    source.getChannels(),
                    source.getChannels() * 2,
                    source.getSampleRate(),
                    false);

            try (AudioInputStream pcmStream = AudioSystem.isConversionSupported(target, source)
                    ? AudioSystem.getAudioInputStream(target, in)
                    : in) {

                AudioFormat format = pcmStream.getFormat();
                if (format.getSampleSizeInBits() != 16)
                    throw new IOException("Unsupported WAV sample size " + format.getSampleSizeInBits());

                byte[] bytes = pcmStream.readAllBytes();
                short[] samples = bytesToShorts(bytes, format.isBigEndian());
                return VoiceAudio.normalize(samples, (int) format.getSampleRate(), format.getChannels());
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file: " + e.getMessage(), e);
        }
    }

    private static short[] decodeMp3(Path file) throws IOException {
        VoicechatApi api = VoiceEngine.api();
        if (api == null) throw new IOException("Simple Voice Chat is not available");

        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            Mp3Decoder decoder = api.createMp3Decoder(raw);
            if (decoder == null) throw new IOException("Simple Voice Chat could not decode MP3 files");
            short[] samples = decoder.decode();
            AudioFormat format = decoder.getAudioFormat();
            if (samples == null || format == null) throw new IOException("Empty MP3 file");
            return VoiceAudio.normalize(samples, (int) format.getSampleRate(), format.getChannels());
        }
    }

    private static short[] bytesToShorts(byte[] bytes, boolean bigEndian) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes)
                .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[bytes.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = buffer.getShort();
        return out;
    }
}
