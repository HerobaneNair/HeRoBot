package hero.bane.herobot.common.ai.runtime;

import java.util.HashMap;
import java.util.Map;

public final class ControlFrame {
    private final int containerBlockId;
    private final int activationId;
    private final Map<String, Object> data;

    public ControlFrame(int containerBlockId) {
        this(containerBlockId, -1);
    }

    public ControlFrame(int containerBlockId, int activationId) {
        this.containerBlockId = containerBlockId;
        this.activationId = activationId;
        this.data = new HashMap<>();
    }

    public int containerBlockId() { return containerBlockId; }
    public int activationId() { return activationId; }
    public Map<String, Object> data() { return data; }

    public int intData(String key, int defaultValue) {
        Object v = data.get(key);
        return v instanceof Number n ? n.intValue() : defaultValue;
    }

    public void setData(String key, Object value) {
        data.put(key, value);
    }
}
