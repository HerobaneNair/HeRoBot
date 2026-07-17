package hero.bane.herobot.client;

import hero.bane.herobot.HeroBot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class EditorDraft {
    private EditorDraft() {}

    private static final File FILE =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot-editor-draft.json");

    public static void save(String json) {
        try {
            Files.writeString(FILE.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed saving editor draft: {}", FILE.getAbsolutePath(), e);
        }
    }

    public static String load() {
        if (!FILE.exists()) return null;
        try {
            return Files.readString(FILE.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed reading editor draft: {}", FILE.getAbsolutePath(), e);
            return null;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(FILE.toPath());
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed deleting editor draft: {}", FILE.getAbsolutePath(), e);
        }
    }
}
