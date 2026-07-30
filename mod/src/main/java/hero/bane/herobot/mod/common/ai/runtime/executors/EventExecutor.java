package hero.bane.herobot.mod.common.ai.runtime.executors;

import hero.bane.herobot.mod.common.ai.block.BlockType;
import hero.bane.herobot.mod.common.ai.runtime.Executor;
import hero.bane.herobot.mod.common.ai.runtime.Reporter;
import hero.bane.herobot.mod.common.ai.runtime.StepResult;

import java.util.Map;

public final class EventExecutor {
    private EventExecutor() {}

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        Executor passthrough = (b, r, br) -> StepResult.continueVia(0);
        flow.put(BlockType.START, passthrough);
        flow.put(BlockType.ON_TOGGLE, passthrough);

        reporter.put(BlockType.MSG_TEXT, (b, r, br) -> {
            if (br.originId() != b.pairedId()) return "";
            String msg = br.eventMessage();
            return msg == null ? "" : msg;
        });
    }
}
