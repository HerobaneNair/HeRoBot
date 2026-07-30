package hero.bane.herobot.mod.common.rule;

import java.lang.reflect.Field;

public final class RuleEntry {

    public final String name;
    public final String description;
    public final Class<?> type;
    public final Bounds bounds;
    private final Field field;
    private final Object defaultValue;

    public RuleEntry(Field field, Rule rule) {
        this.field = field;
        this.name = field.getName();
        this.description = rule.desc();
        this.type = field.getType();
        this.bounds = field.getAnnotation(Bounds.class);
        field.setAccessible(true);
        this.defaultValue = get();
    }

    public boolean hasBounds() {
        return bounds != null;
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