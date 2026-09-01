package hero.bane.herobot.mod.common.ai;

import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.ai.AiScriptCodec;
import hero.bane.herobot.mod.common.HeroBot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AiScriptIO {
    public static final String DIR_NAME = "herobot_scripts";
    public static final String FILE_EXT = ".hbai.json";

    private AiScriptIO() {}

    public static Path scriptsDir(MinecraftServer server) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path dir = root.resolve(DIR_NAME);
        if (!Files.isDirectory(dir)) Files.createDirectories(dir);
        return dir;
    }

    public static Path scriptFile(MinecraftServer server, String name) throws IOException {
        return scriptsDir(server).resolve(name + FILE_EXT);
    }

    public static List<String> listScripts(MinecraftServer server) {
        try {
            Path dir = scriptsDir(server);
            List<String> names = new ArrayList<>();
            try (DirectoryStream<Path> s = Files.newDirectoryStream(dir, "*" + FILE_EXT)) {
                for (Path p : s) {
                    String fn = p.getFileName().toString();
                    names.add(fn.substring(0, fn.length() - FILE_EXT.length()));
                }
            }
            names.sort(Comparator.naturalOrder());
            return names;
        } catch (IOException e) {
            HeroBot.LOGGER.warn("Failed to list AI scripts", e);
            return List.of();
        }
    }

    public static AiScript loadByName(MinecraftServer server, String name) throws IOException {
        Path file = scriptFile(server, name);
        if (!Files.isRegularFile(file)) return null;
        String json = Files.readString(file);
        return AiScriptCodec.fromJson(json, name);
    }

    public static void saveByName(MinecraftServer server, String name, String json) throws IOException {
        Path file = scriptFile(server, name);
        Files.writeString(file, json);
    }

    public static String rawJsonByName(MinecraftServer server, String name) throws IOException {
        Path file = scriptFile(server, name);
        if (!Files.isRegularFile(file)) return null;
        return Files.readString(file);
    }

    public static void deleteByName(MinecraftServer server, String name) throws IOException {
        Path file = scriptFile(server, name);
        Files.deleteIfExists(file);
    }
}
