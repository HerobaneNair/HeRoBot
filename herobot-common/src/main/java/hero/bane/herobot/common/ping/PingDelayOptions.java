package hero.bane.herobot.common.ping;

import java.util.EnumSet;
import java.util.Locale;

public final class PingDelayOptions {

    public enum Category {
        KNOCKBACK,
        ATTACK,
        USE,
        LOOK,
        CHAT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Category byId(String id) {
            for (Category category : values()) {
                if (category.id().equalsIgnoreCase(id)) return category;
            }
            return null;
        }
    }

    private static final EnumSet<Category> DEFAULTS =
            EnumSet.of(Category.KNOCKBACK, Category.ATTACK, Category.USE, Category.LOOK);

    private final EnumSet<Category> enabled = EnumSet.copyOf(DEFAULTS);

    public boolean isEnabled(Category category) {
        return enabled.contains(category);
    }

    public void set(Category category, boolean value) {
        if (value) enabled.add(category);
        else enabled.remove(category);
    }

    public boolean isDefault() {
        return enabled.equals(DEFAULTS);
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        for (Category category : Category.values()) {
            if (!builder.isEmpty()) builder.append("\n ");
            builder.append(category.id()).append(": ").append(isEnabled(category) ? "on" : "off");
        }
        return builder.toString();
    }
}
