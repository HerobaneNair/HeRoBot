package hero.bane.herobot.rule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hero.bane.herobot.HeroBot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RuleConfigIO {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, Object> CLIENT_OVERRIDES = new LinkedHashMap<>();
    private static final Map<String, Object> WORLD_OVERRIDES = new LinkedHashMap<>();

    private static volatile File clientFile;
    private static volatile File worldFile;

    public static void initClient(File file) {
        clientFile = file;
        CLIENT_OVERRIDES.clear();
        CLIENT_OVERRIDES.putAll(loadOverrides(file));
        reapplyLayers();
    }

    public static void initWorld(MinecraftServer server) {
        File root = server.getWorldPath(LevelResource.ROOT).toFile();
        worldFile = new File(root, "herobot.gson");
        WORLD_OVERRIDES.clear();
        WORLD_OVERRIDES.putAll(loadOverrides(worldFile));
        reapplyLayers();
    }

    public static void clearWorld() {
        WORLD_OVERRIDES.clear();
        worldFile = null;
        reapplyLayers();
    }

    public static void setTemp(String ruleName, Object value) {
        RuleEntry rule = RuleRegistry.get(ruleName);
        if (rule == null) return;
        rule.set(value);
    }

    public static void setPermClient(String ruleName, Object value) {
        RuleEntry rule = RuleRegistry.get(ruleName);
        if (rule == null) return;

        if (Objects.equals(value, rule.getDefaultValue())) CLIENT_OVERRIDES.remove(ruleName);
        else CLIENT_OVERRIDES.put(ruleName, value);

        if (clientFile != null) saveOverrides(clientFile, CLIENT_OVERRIDES);
        reapplyLayers();
    }

    public static boolean setPermWorld(String ruleName, Object value) {
        RuleEntry rule = RuleRegistry.get(ruleName);
        if (rule == null) return false;
        if (worldFile == null) return false;

        if (Objects.equals(value, rule.getDefaultValue())) WORLD_OVERRIDES.remove(ruleName);
        else WORLD_OVERRIDES.put(ruleName, value);

        saveOverrides(worldFile, WORLD_OVERRIDES);
        reapplyLayers();
        return true;
    }

    public static void reapplyLayers() {
        RuleRegistry.applyDefaults();
        RuleRegistry.applyOverrides(CLIENT_OVERRIDES);
        RuleRegistry.applyOverrides(WORLD_OVERRIDES);
    }

    private static Map<String, Object> loadOverrides(File file) {
        if (!file.exists()) {
            saveOverrides(file, Map.of());
            return Map.of();
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return Map.of();

            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, RuleEntry> e : RuleRegistry.all().entrySet()) {
                JsonElement el = json.get(e.getKey());
                if (el == null) continue;

                Object value = GSON.fromJson(el, e.getValue().type);
                if (value == null) continue;

                if (Objects.equals(value, e.getValue().getDefaultValue())) continue;
                out.put(e.getKey(), value);
            }
            return out;
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed reading rules config: {}", file.getAbsolutePath(), e);
            return Map.of();
        }
    }

    private static void saveOverrides(File file, Map<String, Object> overrides) {
        JsonObject json = new JsonObject();

        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            RuleEntry rule = RuleRegistry.get(e.getKey());
            if (rule == null) continue;

            if (Objects.equals(e.getValue(), rule.getDefaultValue())) continue;
            json.add(rule.name, GSON.toJsonTree(e.getValue()));
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed saving rules config: {}", file.getAbsolutePath(), e);
        }
    }
}