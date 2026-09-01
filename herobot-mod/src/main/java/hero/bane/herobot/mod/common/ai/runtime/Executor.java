package hero.bane.herobot.mod.common.ai.runtime;

import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.runtime.Branch;
import hero.bane.herobot.common.ai.runtime.StepResult;

@FunctionalInterface
public interface Executor {
    StepResult execute(BlockInstance block, ScriptRunner runner, Branch branch);
}
