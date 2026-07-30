package hero.bane.herobot.mod.common.ai.runtime;

import hero.bane.herobot.mod.common.ai.block.BlockType;

public final class JoinState {
    private final BlockType startType;
    private final int startBlockId;
    private final int endBlockId;
    private int remaining;
    private int iteration;

    public JoinState(BlockType startType, int startBlockId, int endBlockId) {
        this.startType = startType;
        this.startBlockId = startBlockId;
        this.endBlockId = endBlockId;
    }

    public BlockType startType() { return startType; }
    public int startBlockId() { return startBlockId; }
    public int endBlockId() { return endBlockId; }

    public int remaining() { return remaining; }
    public void setRemaining(int r) { this.remaining = r; }

    public int iteration() { return iteration; }
    public void setIteration(int i) { this.iteration = i; }
}
