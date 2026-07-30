package hero.bane.herobot.mod.common.ai.block;

public record Wire(int fromBlockId, int outPort, int toBlockId, int toPort) {
    public Wire(int fromBlockId, int outPort, int toBlockId) {
        this(fromBlockId, outPort, toBlockId, 0);
    }
}
