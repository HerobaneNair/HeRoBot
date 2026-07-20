package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.runtime.Branch;
import hero.bane.herobot.ai.runtime.Executor;
import hero.bane.herobot.ai.runtime.ParamEval;
import hero.bane.herobot.ai.runtime.StepResult;
import hero.bane.herobot.bot.pathing.PathSettingOps;
import hero.bane.herobot.bot.pathing.traversal.BotPathing;
import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.bot.pathing.PathSettings;
import hero.bane.herobot.control.ControlOp;
import hero.bane.herobot.control.RemoteOps;
import hero.bane.herobot.control.RemotePathSettings;
import hero.bane.herobot.control.RemotePathState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;

public final class PathExecutor {
    private PathExecutor() {}

    public static void register(Map<BlockType, Executor> flow) {
        flow.put(BlockType.GOTO_POS, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) {
                if (br.pathWaitFor() == b.id()) return pollPath(bot, br);
                Vec3 target = ParamEval.evalVec3(b, "position", r, br);
                PathSettings settings = bot.getPathSettings();
                try {
                    BotPathing follower = new BotPathing(bot, target, bot.createCommandSourceStack(), settings);
                    bot.setPathFollower(follower);
                    br.setPathWaitFor(b.id());
                    return StepResult.wait(1);
                } catch (Throwable ignored) {}
                return StepResult.continueVia(0);
            }
            ServerPlayer player = r.player();
            if (!RemoteOps.canSend(player)) return StepResult.continueVia(0);
            if (br.pathWaitFor() == b.id()) return pollRemote(player, br);
            Vec3 target = ParamEval.evalVec3(b, "position", r, br);
            int seq = RemotePathState.begin(player);
            RemoteOps.send(player, ControlOp.pathGotoPos(target, seq));
            br.setPathWaitFor(b.id());
            return StepResult.wait(1);
        });
        flow.put(BlockType.GOTO_ENTITY, (b, r, br) -> {
            BotPlayer bot = r.bot();
            if (bot != null) {
                if (br.pathWaitFor() == b.id()) return pollPath(bot, br);
                Object sel = ParamEval.raw(b, "target", r, br);
                Entity target = SelectorExecutor.resolveFirstEntity(sel, r);
                if (target != null) {
                    PathSettings settings = bot.getPathSettings();
                    BotPathing follower = new BotPathing(bot, target, bot.createCommandSourceStack(), settings);
                    bot.setPathFollower(follower);
                    br.setPathWaitFor(b.id());
                    return StepResult.wait(1);
                }
                return StepResult.continueVia(0);
            }
            ServerPlayer player = r.player();
            if (!RemoteOps.canSend(player)) return StepResult.continueVia(0);
            if (br.pathWaitFor() == b.id()) return pollRemote(player, br);
            Object sel = ParamEval.raw(b, "target", r, br);
            Entity target = SelectorExecutor.resolveFirstEntity(sel, r);
            if (target != null) {
                int seq = RemotePathState.begin(player);
                RemoteOps.send(player, ControlOp.pathGotoEntity(target.getId(), seq));
                br.setPathWaitFor(b.id());
                return StepResult.wait(1);
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.STOP_PATH, (b, r, br) -> {
            if (r.bot() != null) {
                r.bot().clearPathFollower();
            } else {
                stopRemotePath(r.player());
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.PATH_SETTING, (b, r, br) -> {
            String key = ParamEval.evalString(b, "key", r, br);
            String value = ParamEval.evalString(b, "value", r, br);
            int index = PathSettingOps.keyIndex(key);
            Double parsed = index < 0 ? null : PathSettingOps.parseValue(index, value);
            if (parsed == null) return StepResult.continueVia(0);
            if (r.bot() != null) {
                PathSettingOps.apply(r.bot().getPathSettings(), index, parsed);
            } else {
                PathSettingOps.apply(RemotePathSettings.of(r.player()), index, parsed);
                RemoteOps.send(r.player(), ControlOp.pathSetting(index, parsed));
            }
            return StepResult.continueVia(0);
        });
        flow.put(BlockType.PATH_MOVE_TYPE, (b, r, br) -> {
            String t = ParamEval.evalString(b, "type", r, br);
            PathSettings.MoveType type;
            try {
                type = PathSettings.MoveType.valueOf(t.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return StepResult.continueVia(0);
            }
            if (r.bot() != null) {
                r.bot().getPathSettings().setMoveType(type);
            } else {
                RemotePathSettings.of(r.player()).setMoveType(type);
                RemoteOps.send(r.player(), ControlOp.pathMoveType(type.ordinal()));
            }
            return StepResult.continueVia(0);
        });
    }

    public static void stopRemotePath(ServerPlayer player) {
        if (!RemotePathState.isActive(player)) return;
        RemotePathState.deactivate(player);
        RemoteOps.send(player, ControlOp.pathStop());
    }

    private static StepResult pollPath(BotPlayer bot, Branch br) {
        BotPathing follower = bot.getPathFollower();
        if (follower != null && !follower.isDone()) return StepResult.wait(1);
        br.setPathWaitFor(-1);
        return StepResult.continueVia(0);
    }

    private static StepResult pollRemote(ServerPlayer player, Branch br) {
        if (RemotePathState.isActive(player)) return StepResult.wait(1);
        br.setPathWaitFor(-1);
        return StepResult.continueVia(0);
    }
}
