package hero.bane.herobot.common.ping;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingDelays {

    private static final PingProfile DEFAULTS = new PingProfile();

    private static final Map<UUID, PingProfile> profiles = new ConcurrentHashMap<>();

    private PingDelays() {}

    public static PingProfile profile(UUID id) {
        return profiles.computeIfAbsent(id, key -> new PingProfile());
    }

    public static PingDelayOptions of(UUID id) {
        return profile(id).options();
    }

    public static boolean enabled(UUID id, PingDelayOptions.Category category) {
        PingProfile existing = profiles.get(id);
        return (existing == null ? DEFAULTS : existing).options().isEnabled(category);
    }

    public static void forget(UUID id) {
        profiles.remove(id);
    }
}
