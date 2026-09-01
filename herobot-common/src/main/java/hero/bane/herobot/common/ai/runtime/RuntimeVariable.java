package hero.bane.herobot.common.ai.runtime;

import hero.bane.herobot.common.ai.VarType;

public final class RuntimeVariable {
    private final VarType type;
    private Object value;

    public RuntimeVariable(VarType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public VarType type() { return type; }
    public Object value() { return value; }
    public void set(Object v) { this.value = v; }
}
