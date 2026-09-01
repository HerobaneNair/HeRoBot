package hero.bane.herobot.paper.ai.runtime;

import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.runtime.Branch;

@FunctionalInterface
public interface Reporter {
    Object evaluate(BlockInstance block, ScriptRunner runner, Branch branch);
}
