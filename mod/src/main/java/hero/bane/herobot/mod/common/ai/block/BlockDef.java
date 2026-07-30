package hero.bane.herobot.mod.common.ai.block;

import java.util.List;

public record BlockDef(BlockType type,
                       BlockCategory category,
                       BlockShape shape,
                       String label,
                       int outPorts,
                       List<ParamSlot> params) {
    public boolean hasInput() {
        return shape != BlockShape.HAT && shape != BlockShape.REPORTER && shape != BlockShape.BOOLEAN;
    }

    public boolean isReporter() {
        return shape == BlockShape.REPORTER || shape == BlockShape.BOOLEAN;
    }
}
