package hero.bane.herobot.mod.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hero.bane.herobot.mod.common.HeroBot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BotNameSuggestions {
    private BotNameSuggestions() {}

    private static final File FILE =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot-bot-names.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static final Set<String> names = new LinkedHashSet<>();

    public static synchronized Set<String> all() {
        ensureLoaded();
        return new LinkedHashSet<>(names);
    }

    public static synchronized boolean add(String name) {
        ensureLoaded();
        if (!names.add(name)) return false;
        save();
        return true;
    }

    public static synchronized boolean remove(String name) {
        ensureLoaded();
        if (!names.remove(name)) return false;
        save();
        return true;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has("names") && json.get("names").isJsonArray()) {
                JsonArray arr = json.getAsJsonArray("names");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) names.add(el.getAsString());
                }
            }
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed reading bot name suggestions: {}", FILE.getAbsolutePath(), e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String name : names) arr.add(name);
        json.add("names", arr);
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed saving bot name suggestions: {}", FILE.getAbsolutePath(), e);
        }
    }
}
