package hero.bane.herobot.common.ping;

import java.util.Locale;

public enum PingMode {
    BALANCE,
    ADD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PingMode byId(String id) {
        for (PingMode mode : values()) {
            if (mode.id().equalsIgnoreCase(id)) return mode;
        }
        return null;
    }
}
