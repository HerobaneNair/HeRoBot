package hero.bane.herobot.common.ping;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PingDelays {

    private static final PingDelayOptions DEFAULTS = new PingDelayOptions();

    private static final Map<UUID, PingDelayOptions> options = new ConcurrentHashMap<>();

    private PingDelays() {}

    public static PingDelayOptions of(UUID id) {
        return options.computeIfAbsent(id, key -> new PingDelayOptions());
    }

    public static boolean enabled(UUID id, PingDelayOptions.Category category) {
        PingDelayOptions existing = options.get(id);
        return (existing == null ? DEFAULTS : existing).isEnabled(category);
    }

    public static void forget(UUID id) {
        options.remove(id);
    }
}
