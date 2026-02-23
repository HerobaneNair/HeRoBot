package hero.bane.herobot.rule;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleRegistry {

    private static final Map<String, RuleEntry> RULES = new LinkedHashMap<>();

    public static void register(Class<?> holder) {
        for (Field field : holder.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            Rule rule = field.getAnnotation(Rule.class);
            if (rule == null) continue;
            RULES.put(field.getName(), new RuleEntry(field, rule));
        }
    }

    public static Map<String, RuleEntry> all() {
        return RULES;
    }

    public static RuleEntry get(String name) {
        return RULES.get(name);
    }

    public static void applyDefaults() {
        for (RuleEntry e : RULES.values()) {
            e.resetToDefault();
        }
    }

    public static void applyOverrides(Map<String, Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return;
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            RuleEntry rule = RULES.get(e.getKey());
            if (rule == null) continue;
            rule.set(e.getValue());
        }
    }
}