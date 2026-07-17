package hero.bane.herobot.ai;

import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.block.Wire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AiScript {
    public static final int CURRENT_VERSION = 3;

    private String name;
    private int version = CURRENT_VERSION;
    private final List<VarDecl> variables = new ArrayList<>();
    private final List<String> varFolders = new ArrayList<>();
    private final Map<Integer, BlockInstance> blocks = new HashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    private final List<Comment> comments = new ArrayList<>();
    private int nextId = 1;

    public AiScript(String name) {
        this.name = name;
    }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public int version() { return version; }
    public void setVersion(int v) { this.version = v; }

    public List<VarDecl> variables() { return variables; }
    public List<String> varFolders() { return varFolders; }
    public Map<Integer, BlockInstance> blocks() { return blocks; }
    public List<Wire> wires() { return wires; }
    public List<Comment> comments() { return comments; }

    public Comment addComment(double x, double y, String text) {
        Comment c = new Comment(nextId++, x, y, text);
        comments.add(c);
        return c;
    }

    public void putComment(Comment c) {
        comments.add(c);
        bumpNextId(c.id());
    }

    public void removeComment(int id) {
        comments.removeIf(c -> c.id() == id);
    }

    public Comment comment(int id) {
        for (Comment c : comments) if (c.id() == id) return c;
        return null;
    }

    public BlockInstance addBlock(BlockType type, double x, double y) {
        BlockInstance b = new BlockInstance(nextId++, type, x, y);
        blocks.put(b.id(), b);
        return b;
    }

    public int freshId() {
        return nextId++;
    }

    public void putBlock(BlockInstance b) {
        blocks.put(b.id(), b);
        bumpNextId(b.id());
    }

    public void bumpNextId(int id) {
        if (id >= nextId) nextId = id + 1;
    }

    public void removeBlock(int id) {
        blocks.remove(id);
        wires.removeIf(w -> w.fromBlockId() == id || w.toBlockId() == id);
    }

    public void addWire(int fromId, int outPort, int toId) {
        wires.add(new Wire(fromId, outPort, toId));
    }

    public void addWire(int fromId, int outPort, int toId, int toPort) {
        wires.add(new Wire(fromId, outPort, toId, toPort));
    }

    public List<Wire> outgoing(int blockId, int outPort) {
        List<Wire> r = new ArrayList<>();
        for (Wire w : wires) {
            if (w.fromBlockId() == blockId && w.outPort() == outPort) r.add(w);
        }
        return r;
    }

    public int incomingCount(int blockId, int toPort) {
        int n = 0;
        for (Wire w : wires) {
            if (w.toBlockId() == blockId && w.toPort() == toPort) n++;
        }
        return n;
    }

    public List<BlockInstance> hatBlocks(BlockType hatType) {
        List<BlockInstance> r = new ArrayList<>();
        for (BlockInstance b : blocks.values()) {
            if (b.type() == hatType) r.add(b);
        }
        // blocks is a HashMap, so sort into canvas order: left to right, then top to bottom.
        r.sort((a, b) -> {
            int cmp = Double.compare(a.x(), b.x());
            return cmp != 0 ? cmp : Double.compare(a.y(), b.y());
        });
        return r;
    }

    public BlockInstance block(int id) {
        return blocks.get(id);
    }
}
