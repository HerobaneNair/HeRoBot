package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.HeroBot;
import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.AiScriptRegistry;
import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.block.Wire;
import hero.bane.herobot.ai.runtime.Branch;
import hero.bane.herobot.ai.runtime.ControlFrame;
import hero.bane.herobot.ai.runtime.Executor;
import hero.bane.herobot.ai.runtime.JoinState;
import hero.bane.herobot.ai.runtime.ParamEval;
import hero.bane.herobot.ai.runtime.Reporter;
import hero.bane.herobot.ai.runtime.ScriptRunner;
import hero.bane.herobot.ai.runtime.StepResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ControlExecutor {
    private ControlExecutor() {}

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        reporter.put(BlockType.LOOP_ITER, (b, r, br) -> {
            for (ControlFrame f : br.frames()) {
                if (f.containerBlockId() != b.pairedId()) continue;
                JoinState js = r.joinState(f.activationId());
                if (js != null) return js.iteration();
            }
            return 0;
        });

        flow.put(BlockType.WAIT, (b, r, br) ->
                StepResult.waitThenContinue(Math.max(1, ParamEval.evalInt(b, "ticks", r, br)), 0));

        flow.put(BlockType.WAIT_UNTIL, (b, r, br) -> {
            if (ParamEval.evalBool(b, "condition", r, br)) return StepResult.continueVia(0);
            return StepResult.wait(1);
        });

        flow.put(BlockType.BREAK, (b, r, br) -> {
            int want = Math.max(1, ParamEval.evalInt(b, "count", r, br));
            ControlFrame target = null;
            int endId = -1;
            int found = 0;
            for (ControlFrame f : br.frames()) {
                JoinState js = r.joinState(f.activationId());
                if (js == null) continue;
                target = f;
                endId = js.endBlockId();
                if (++found >= want) break;
            }
            if (target == null) return StepResult.end();

            while (!br.frames().isEmpty()) {
                ControlFrame f = br.frames().pop();
                if (r.joinState(f.activationId()) != null) {
                    r.killActivation(f.activationId(), br);
                    r.removeJoin(f.activationId());
                }
                if (f == target) break;
            }
            for (Wire w : r.outgoing(endId, 0)) {
                return StepResult.jumpTo(w.toBlockId());
            }
            return StepResult.end();
        });

        flow.put(BlockType.STOP_SCRIPT, (b, r, br) -> {
            AiScriptRegistry.clear(r.player());
            return StepResult.end();
        });

        flow.put(BlockType.PAUSE_SCRIPT, (b, r, br) -> {
            r.pause();
            return StepResult.continueVia(0);
        });

        flow.put(BlockType.SET_SCRIPT, (b, r, br) -> {
            String name = ParamEval.evalString(b, "script", r, br);
            if (name != null && !name.isBlank()) {
                ServerPlayer player = r.player();
                MinecraftServer server = player.level().getServer();
                if (server != null) {
                    // Not just IOException: BlockType/VarType.valueOf and the JSON parser all throw
                    // unchecked, and a script from a newer version would otherwise kill the branch.
                    try {
                        AiScript next = AiScriptRegistry.load(server, name);
                        if (next != null) {
                            AiScriptRegistry.assign(player, name, next);
                            AiScriptRegistry.fireStart(player);
                        }
                    } catch (Exception e) {
                        HeroBot.LOGGER.warn("Failed to load AI script {}: {}", name, e.toString());
                    }
                }
            }
            return StepResult.end();
        });

        flow.put(BlockType.IF, (b, r, br) -> {
            int act = r.openActivation(BlockType.IF, b.id(), b.pairedId(), 0);
            if (ParamEval.evalBool(b, "condition", r, br)) return StepResult.enterBody(0, act);
            BlockInstance elif = sideTarget(r, b.id());
            if (elif != null && b.pairedId() >= 0) return StepResult.pushAndJump(elif.id(), act);
            return skipToEnd(b, act);
        });

        flow.put(BlockType.ELSE_IF, (b, r, br) -> {
            if (ParamEval.evalBool(b, "condition", r, br)) {
                int act = r.openActivation(BlockType.ELSE_IF, b.id(), b.pairedId(), 0);
                return StepResult.enterBody(0, act);
            }
            BlockInstance next = sideTarget(r, b.id());
            if (next != null) return StepResult.jumpTo(next.id());
            BlockInstance root = chainRoot(r, b.id());
            if (root != null && root.pairedId() >= 0) return StepResult.jumpTo(root.pairedId());
            int act = r.openActivation(BlockType.ELSE_IF, b.id(), b.pairedId(), 0);
            return skipToEnd(b, act);
        });

        flow.put(BlockType.FOR, (b, r, br) -> {
            int count = ParamEval.evalInt(b, "count", r, br);
            int act = r.openActivation(BlockType.FOR, b.id(), b.pairedId(), count);
            if (count <= 0) return skipToEnd(b, act);
            return StepResult.enterBody(0, act);
        });

        flow.put(BlockType.WHILE, (b, r, br) -> {
            int act = r.openActivation(BlockType.WHILE, b.id(), b.pairedId(), 0);
            if (!ParamEval.evalBool(b, "condition", r, br)) return skipToEnd(b, act);
            return StepResult.enterBody(0, act);
        });

        flow.put(BlockType.BLOCK_END, ControlExecutor::stepEnd);
    }

    private static StepResult skipToEnd(BlockInstance start, int activationId) {
        if (start.pairedId() < 0) return StepResult.end();
        return StepResult.pushAndJump(start.pairedId(), activationId);
    }

    private static BlockInstance sideTarget(ScriptRunner r, int blockId) {
        for (Wire w : r.outgoing(blockId, 1)) {
            BlockInstance target = r.script().block(w.toBlockId());
            if (target != null && target.type() == BlockType.ELSE_IF) return target;
        }
        return null;
    }

    private static BlockInstance chainRoot(ScriptRunner r, int elifId) {
        Set<Integer> visited = new HashSet<>();
        int current = elifId;
        while (visited.add(current)) {
            BlockInstance source = null;
            for (Wire w : r.incoming(current)) {
                if (w.outPort() != 1) continue;
                source = r.script().block(w.fromBlockId());
                break;
            }
            if (source == null) return null;
            if (source.type() == BlockType.IF) return source;
            if (source.type() != BlockType.ELSE_IF) return null;
            current = source.id();
        }
        return null;
    }

    private static StepResult stepEnd(BlockInstance b, ScriptRunner r, Branch br) {
        ControlFrame top = br.frames().peek();
        int act = top != null ? top.activationId() : -1;
        JoinState js = r.joinState(act);
        if (js == null) {
            return StepResult.continueVia(0);
        }

        if (r.runningCount(act, br) != 0) {
            return StepResult.end();
        }

        r.killActivation(act, br);

        boolean repeat;
        switch (js.startType()) {
            case FOR -> {
                int rem = js.remaining() - 1;
                js.setRemaining(rem);
                repeat = rem > 0;
            }
            case WHILE -> {
                BlockInstance start = r.script().block(js.startBlockId());
                boolean cond = start != null && ParamEval.evalBool(start, "condition", r, br);
                repeat = cond;
            }
            default -> repeat = false;
        }

        if (repeat) {
            js.setIteration(js.iteration() + 1);
            r.relaunchBody(js, br);
            return StepResult.handled();
        }
        r.removeJoin(act);
        if (b.pairedId() >= 0) {
            BlockInstance start = r.script().block(b.pairedId());
            if (start != null && start.type() == BlockType.ELSE_IF) {
                BlockInstance root = chainRoot(r, start.id());
                if (root != null && root.pairedId() >= 0) {
                    return StepResult.popAndJump(root.pairedId());
                }
            }
        }
        return StepResult.exitBody(0);
    }
}
