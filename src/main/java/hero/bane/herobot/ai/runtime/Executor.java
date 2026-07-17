package hero.bane.herobot.ai.runtime;

import hero.bane.herobot.ai.block.BlockInstance;

@FunctionalInterface
public interface Executor {
    StepResult execute(BlockInstance block, ScriptRunner runner, Branch branch);
}
