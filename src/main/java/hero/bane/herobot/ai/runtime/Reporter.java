package hero.bane.herobot.ai.runtime;

import hero.bane.herobot.ai.block.BlockInstance;

@FunctionalInterface
public interface Reporter {
    Object evaluate(BlockInstance block, ScriptRunner runner, Branch branch);
}
