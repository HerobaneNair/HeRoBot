package hero.bane.herobot.rule;

import java.lang.reflect.Field;

public final class RuleEntry {

    public final String name;
    public final String description;
    public final Class<?> type;
    private final Field field;
    private final Object defaultValue;

    public RuleEntry(Field field, Rule rule) {
        this.field = field;
        this.name = field.getName();
        this.description = rule.desc();
        this.type = field.getType();
        field.setAccessible(true);
        this.defaultValue = get();
    }

    public Object get() {
        try {
            return field.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void set(Object value) {
        try {
            field.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void resetToDefault() {
        set(defaultValue);
    }
}