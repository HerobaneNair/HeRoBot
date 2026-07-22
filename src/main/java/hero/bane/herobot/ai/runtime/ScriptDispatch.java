package hero.bane.herobot.ai.runtime;

import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.runtime.executors.ActionExecutor;
import hero.bane.herobot.ai.runtime.executors.ControlExecutor;
import hero.bane.herobot.ai.runtime.executors.DataExecutor;
import hero.bane.herobot.ai.runtime.executors.EventExecutor;
import hero.bane.herobot.ai.runtime.executors.FunctionExecutor;
import hero.bane.herobot.ai.runtime.executors.InventoryExecutor;
import hero.bane.herobot.ai.runtime.executors.LookExecutor;
import hero.bane.herobot.ai.runtime.executors.MotionExecutor;
import hero.bane.herobot.ai.runtime.executors.OperatorExecutor;
import hero.bane.herobot.ai.runtime.executors.PathExecutor;
import hero.bane.herobot.ai.runtime.executors.SensorExecutor;

import java.util.EnumMap;
import java.util.Map;

public final class ScriptDispatch {
    private static final Map<BlockType, Executor> FLOW = new EnumMap<>(BlockType.class);
    private static final Map<BlockType, Reporter> REPORTER = new EnumMap<>(BlockType.class);

    static {
        EventExecutor.register(FLOW, REPORTER);
        MotionExecutor.register(FLOW);
        ActionExecutor.register(FLOW);
        LookExecutor.register(FLOW);
        PathExecutor.register(FLOW);
        InventoryExecutor.register(FLOW, REPORTER);
        ControlExecutor.register(FLOW, REPORTER);
        DataExecutor.register(FLOW, REPORTER);
        SensorExecutor.register(REPORTER);
        OperatorExecutor.register(REPORTER);
        FunctionExecutor.register(FLOW, REPORTER);
    }

    private ScriptDispatch() {}

    public static Executor flow(BlockType type) { return FLOW.get(type); }
    public static Reporter reporter(BlockType type) { return REPORTER.get(type); }
}
