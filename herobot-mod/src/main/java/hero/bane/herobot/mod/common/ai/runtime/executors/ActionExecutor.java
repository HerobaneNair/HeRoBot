package hero.bane.herobot.mod.common.ai.runtime.executors;

import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.runtime.Branch;
import hero.bane.herobot.common.ai.runtime.StepResult;
import hero.bane.herobot.mod.common.ai.runtime.Executor;
import hero.bane.herobot.mod.common.ai.runtime.ParamEval;
import hero.bane.herobot.mod.common.ai.runtime.ScriptRunner;
import hero.bane.herobot.mod.common.bot.BotChat;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.Action;
import hero.bane.herobot.mod.common.bot.BotPlayerActionPack.ActionType;
import hero.bane.herobot.mod.common.control.PlayerController;
import hero.bane.herobot.mod.common.control.PlayerControllers;
import hero.bane.herobot.mod.common.util.BlockBreakTasks;
import hero.bane.herobot.mod.common.util.BlockBreaker;
import hero.bane.herobot.mod.common.util.BlockPlacer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class ActionExecutor {
    private ActionExecutor() {}

    public static void register(Map<BlockType, Executor> flow) {
        flow.put(BlockType.USE, action(ActionType.USE));
        flow.put(BlockType.SWING, action(ActionType.SWING));
        flow.put(BlockType.JUMP, action(ActionType.JUMP));
        flow.put(BlockType.ATTACK, action(ActionType.ATTACK));
        flow.put(BlockType.DROP_ITEM, (b, r, br) -> {
            String amount = ParamEval.evalString(b, "amount", r, br);
            ActionType type = "stack".equalsIgnoreCase(amount) ? ActionType.DROP_STACK : ActionType.DROP_ITEM;
            return action(type).execute(b, r, br);
        });
        flow.put(BlockType.SWAP_HANDS, action(ActionType.SWAP_HANDS));

        flow.put(BlockType.ATTEMPT_AUTOJUMP, (b, r, br) -> {
            ap(r).attemptAutoJump();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.PICK_BLOCK, (b, r, br) -> {
            String data = ParamEval.evalString(b, "data", r, br);
            ap(r).pickBlock("with data".equalsIgnoreCase(data));
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.STOP_ACTION, (b, r, br) -> {
            String name = ParamEval.evalString(b, "action", r, br);
            try {
                ap(r).stop(ActionType.valueOf(name));
            } catch (IllegalArgumentException ignored) {}
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.STOP_ALL, (b, r, br) -> {
            BlockBreakTasks.cancel(r.player());
            ap(r).stopAll();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.PLACE_BLOCK, (b, r, br) -> {
            Vec3 pos = ParamEval.evalVec3(b, "position", r, br);
            String face = ParamEval.evalString(b, "face", r, br);
            boolean force = ParamEval.evalBool(b, "force", r, br);
            BlockPlacer.place(r.player(), pos, face, force);
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.BREAK_BLOCK, (b, r, br) -> {
            Vec3 pos = ParamEval.evalVec3(b, "position", r, br);
            boolean force = ParamEval.evalBool(b, "force", r, br);
            BlockBreaker.start(r.player(), pos, force);
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.SEND_MESSAGE, (b, r, br) -> {
            String message = ParamEval.evalString(b, "message", r, br);
            if (message != null && !message.isBlank()) {
                ServerPlayer player = r.player();
                BotChat.send(player, message, () -> ParamEval.evalBool(b, "op", r, br));
            }
            return StepResult.continueVia(0);
        });
    }

    private static Executor action(ActionType type) {
        return (BlockInstance b, ScriptRunner r, Branch br) -> {
            String mode = ParamEval.evalString(b, "mode", r, br);
            int interval = Math.max(1, ParamEval.evalInt(b, "interval", r, br));
            int ticks = Math.max(0, ParamEval.evalInt(b, "ticks", r, br));
            PlayerController pack = ap(r);
            switch (mode == null ? "once" : mode) {
                case "continuous" -> {
                    if (ticks > 0) pack.start(type, Action.continuous(ticks));
                    else pack.start(type, Action.continuous());
                }
                case "interval" -> {
                    if (ticks > 0) pack.start(type, Action.interval(interval, ticks));
                    else pack.start(type, Action.interval(interval));
                }
                case "twice" -> pack.start(type, Action.once(2));
                default -> pack.start(type, Action.once());
            }
            return StepResult.continueVia(0);
        };
    }

    private static PlayerController ap(ScriptRunner r) {
        return PlayerControllers.of(r.player());
    }
}
