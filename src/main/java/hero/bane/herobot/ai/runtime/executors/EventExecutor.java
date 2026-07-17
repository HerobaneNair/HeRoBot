package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.runtime.Executor;
import hero.bane.herobot.ai.runtime.StepResult;

import java.util.Map;

public final class EventExecutor {
    private EventExecutor() {}

    public static void register(Map<BlockType, Executor> flow) {
        Executor passthrough = (b, r, br) -> StepResult.continueVia(0);
        flow.put(BlockType.START, passthrough);
        flow.put(BlockType.ON_TOGGLE, passthrough);
    }
}
