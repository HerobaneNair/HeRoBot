package hero.bane.herobot.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hero.bane.herobot.HeroBot;
import hero.bane.herobot.client.screen.ai.SidebarMode;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class EditorPrefs {
    private EditorPrefs() {}

    private static final File FILE =
            new File(FabricLoader.getInstance().getConfigDir().toFile(), "herobot-editor.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean loaded;
    private static boolean cometsEnabled = true;
    private static int autosaveSeconds = 30;
    private static boolean recordSingleTree = false;
    private static SidebarMode leftSidebarMode = SidebarMode.MAXIMIZED;
    private static SidebarMode rightSidebarMode = SidebarMode.MAXIMIZED;
    private static List<String> categoryOrder = List.of();

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

    public static List<String> categoryOrder() {
        ensureLoaded();
        return List.copyOf(categoryOrder);
    }

    public static void setCategoryOrder(List<String> value) {
        ensureLoaded();
        if (categoryOrder.equals(value)) return;
        categoryOrder = List.copyOf(value);
        save();
    }

    public static SidebarMode leftPanelMode() {
        ensureLoaded();
        return leftSidebarMode;
    }

    public static void setLeftPanelMode(SidebarMode value) {
        ensureLoaded();
        if (leftSidebarMode == value) return;
        leftSidebarMode = value;
        save();
    }

    public static SidebarMode rightPanelMode() {
        ensureLoaded();
        return rightSidebarMode;
    }

    public static void setRightPanelMode(SidebarMode value) {
        ensureLoaded();
        if (rightSidebarMode == value) return;
        rightSidebarMode = value;
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
            if (json != null && json.has("leftPanelMode")) {
                leftSidebarMode = SidebarMode.fromName(json.get("leftPanelMode").getAsString());
            }
            if (json != null && json.has("rightPanelMode")) {
                rightSidebarMode = SidebarMode.fromName(json.get("rightPanelMode").getAsString());
            }
            if (json != null && json.has("categoryOrder")) {
                List<String> names = new ArrayList<>();
                for (var e : json.getAsJsonArray("categoryOrder")) names.add(e.getAsString());
                categoryOrder = List.copyOf(names);
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
        json.addProperty("leftPanelMode", leftSidebarMode.name());
        json.addProperty("rightPanelMode", rightSidebarMode.name());
        JsonArray order = new JsonArray();
        for (String name : categoryOrder) order.add(name);
        json.add("categoryOrder", order);
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            HeroBot.LOGGER.error("Failed saving editor prefs: {}", FILE.getAbsolutePath(), e);
        }
    }
}
