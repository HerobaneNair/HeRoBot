package hero.bane.herobot.mod.common.ai.runtime;

import hero.bane.herobot.mod.common.ai.block.BlockInstance;

@FunctionalInterface
public interface Executor {
    StepResult execute(BlockInstance block, ScriptRunner runner, Branch branch);
}
