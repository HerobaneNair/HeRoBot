package hero.bane.herobot.common.bot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Shadows {

    public record Shadow(UUID id, String name, String scriptName) {

        public boolean hasScript() {
            return scriptName != null;
        }
    }

    private static final Map<UUID, Shadow> armed = new ConcurrentHashMap<>();

    private Shadows() {
    }

    public static void arm(UUID id, String name, String scriptName) {
        if (id == null || name == null) return;
        armed.put(id, new Shadow(id, name, scriptName));
    }

    public static Shadow of(UUID id) {
        return id == null ? null : armed.get(id);
    }

    public static boolean isArmed(UUID id) {
        return of(id) != null;
    }

    public static Shadow disarm(UUID id) {
        return id == null ? null : armed.remove(id);
    }

    public static void clear() {
        armed.clear();
    }
}
