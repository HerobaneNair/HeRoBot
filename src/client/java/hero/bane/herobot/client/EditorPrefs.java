package hero.bane.herobot.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hero.bane.herobot.HeroBot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class EditorPrefs {
    private EditorPrefs() {}

    private static final File FILE =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot-editor.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static boolean cometsEnabled = true;
    private static int autosaveSeconds = 30;
    private static boolean recordSingleTree = false;

    public static boolean cometsEnabled() {
        ensureLoaded();
        return cometsEnabled;
    }

    public static void setCometsEnabled(boolean value) {
        ensureLoaded();
        if (cometsEnabled == value) return;
        cometsEnabled = value;
        save();
    }

    public static int autosaveSeconds() {
        ensureLoaded();
        return autosaveSeconds;
    }

    public static void setAutosaveSeconds(int value) {
        ensureLoaded();
        value = Math.max(0, value);
        if (autosaveSeconds == value) return;
        autosaveSeconds = value;
        save();
    }

    public static boolean recordSingleTree() {
        ensureLoaded();
        return recordSingleTree;
    }

    public static void setRecordSingleTree(boolean value) {
        ensureLoaded();
        if (recordSingleTree == value) return;
        recordSingleTree = value;
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("cometsEnabled")) {
                cometsEnabled = json.get("cometsEnabled").getAsBoolean();
            }
            if (json != null && json.has("autosaveSeconds")) {
                autosaveSeconds = Math.max(0, json.get("autosaveSeconds").getAsInt());
            } else if (json != null && json.has("autosaveMinutes")) {
                autosaveSeconds = Math.max(0, json.get("autosaveMinutes").getAsInt() * 60);
            }
            if (json != null && json.has("recordSingleTree")) {
                recordSingleTree = json.get("recordSingleTree").getAsBoolean();
            }
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed reading editor prefs: {}", FILE.getAbsolutePath(), e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("cometsEnabled", cometsEnabled);
        json.addProperty("autosaveSeconds", autosaveSeconds);
        json.addProperty("recordSingleTree", recordSingleTree);
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed saving editor prefs: {}", FILE.getAbsolutePath(), e);
        }
    }
}
