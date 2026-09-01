package hero.bane.herobot.common.bot;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLogouts {

    public record Logout(UUID id, String name, String dimension,
                         double x, double y, double z,
                         float yaw, float pitch, long time) {
    }

    private static final Map<UUID, Logout> byId = new ConcurrentHashMap<>();
    private static final Map<String, Logout> byName = new ConcurrentHashMap<>();

    private PlayerLogouts() {
    }

    public static void record(UUID id, String name, String dimension,
                              double x, double y, double z, float yaw, float pitch) {
        if (id == null || name == null || dimension == null) return;
        Logout logout = new Logout(id, name, dimension, x, y, z, yaw, pitch, System.currentTimeMillis());
        byId.put(id, logout);
        byName.put(key(name), logout);
    }

    public static Logout of(UUID id) {
        return id == null ? null : byId.get(id);
    }

    public static Logout of(String name) {
        return name == null ? null : byName.get(key(name));
    }

    public static void forget(UUID id) {
        Logout removed = id == null ? null : byId.remove(id);
        if (removed != null) byName.remove(key(removed.name()));
    }

    public static void clear() {
        byId.clear();
        byName.clear();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
