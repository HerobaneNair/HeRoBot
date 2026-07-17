package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.runtime.Executor;
import hero.bane.herobot.ai.runtime.ParamEval;
import hero.bane.herobot.ai.runtime.ScriptRunner;
import hero.bane.herobot.ai.runtime.StepResult;
import hero.bane.herobot.control.PlayerController;
import hero.bane.herobot.control.PlayerControllers;

import java.util.Map;

public final class MotionExecutor {
    private MotionExecutor() {}

    public static void register(Map<BlockType, Executor> flow) {
        flow.put(BlockType.MOVE, (b, r, br) -> {
            String dir = ParamEval.evalString(b, "direction", r, br);
            float v = "stop".equals(dir) ? 0f : "backward".equals(dir) ? -1f : 1f;
            ap(r).setForward(v); clearPath(r); return StepResult.continueVia(0);
        });
        flow.put(BlockType.STRAFE, (b, r, br) -> {
            String dir = ParamEval.evalString(b, "direction", r, br);
            float v = "stop".equals(dir) ? 0f : "right".equals(dir) ? -1f : 1f;
            ap(r).setStrafing(v); clearPath(r); return StepResult.continueVia(0);
        });
        flow.put(BlockType.STOP_MOVEMENT, (b, r, br) -> { ap(r).stopMovement(); return StepResult.continueVia(0); });
        flow.put(BlockType.SNEAK, (b, r, br) -> { ap(r).setSneaking(ParamEval.evalBool(b, "value", r, br)); return StepResult.continueVia(0); });
        flow.put(BlockType.SPRINT, (b, r, br) -> { ap(r).setSprinting(ParamEval.evalBool(b, "value", r, br)); return StepResult.continueVia(0); });
        flow.put(BlockType.AUTOJUMP, (b, r, br) -> { ap(r).setAutoJump(ParamEval.evalBool(b, "value", r, br)); return StepResult.continueVia(0); });
    }

    private static PlayerController ap(ScriptRunner r) {
        return PlayerControllers.of(r.player());
    }

    private static void clearPath(ScriptRunner r) {
        if (r.bot() != null) r.bot().clearPathFollower();
        else PathExecutor.stopRemotePath(r.player());
    }
}
