package hero.bane.herobot.ai.block;

import java.util.HashMap;
import java.util.Map;

public final class BlockInstance {
    private final int id;
    private final BlockType type;
    private double x;
    private double y;
    private int pairedId = -1;
    private final Map<String, Object> params;
    private final Map<String, BlockInstance> reporterParams;

    public BlockInstance(int id, BlockType type, double x, double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.params = new HashMap<>();
        this.reporterParams = new HashMap<>();
    }

    public int id() { return id; }
    public BlockType type() { return type; }
    public double x() { return x; }
    public double y() { return y; }
    public void setPos(double x, double y) { this.x = x; this.y = y; }

    public int pairedId() { return pairedId; }
    public void setPairedId(int pairedId) { this.pairedId = pairedId; }

    public Map<String, Object> params() { return params; }
    public Map<String, BlockInstance> reporterParams() { return reporterParams; }

    public Object getParam(String name) { return params.get(name); }
    public void setParam(String name, Object value) { params.put(name, value); }

    public BlockInstance getReporter(String name) { return reporterParams.get(name); }
    public void setReporter(String name, BlockInstance reporter) { reporterParams.put(name, reporter); }
}
