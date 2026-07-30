package hero.bane.herobot.mod.common.ai.runtime.executors;

import hero.bane.herobot.mod.common.HeroBot;
import hero.bane.herobot.mod.common.ai.FuncDecl;
import hero.bane.herobot.mod.common.ai.block.BlockInstance;
import hero.bane.herobot.mod.common.ai.block.BlockType;
import hero.bane.herobot.mod.common.ai.block.Wire;
import hero.bane.herobot.mod.common.ai.runtime.Branch;
import hero.bane.herobot.mod.common.ai.runtime.ControlFrame;
import hero.bane.herobot.mod.common.ai.runtime.Executor;
import hero.bane.herobot.mod.common.ai.runtime.ParamEval;
import hero.bane.herobot.mod.common.ai.runtime.Reporter;
import hero.bane.herobot.mod.common.ai.runtime.RuntimeVariable;
import hero.bane.herobot.mod.common.ai.runtime.ScriptRunner;
import hero.bane.herobot.mod.common.ai.runtime.StepResult;

import java.util.*;

public final class FunctionExecutor {
    public static final int MAX_CALL_DEPTH = 50;
    public static final int MAX_CALLS_PER_TICK = 50;

    private static final String KEY_CALL = "fnCall";
    private static final String KEY_FUNC = "fnName";
    private static final String KEY_SEQ = "fnSeq";

    private FunctionExecutor() {}

    /**
     * Reserved prefix; user variable names cannot contain '§', so params can never collide.
     * The call sequence number gives every invocation its own storage slot, so recursive or
     * concurrently-running calls to the same function don't clobber each other's arguments.
     */
    public static String paramKey(String func, int index, long callSeq) {
        return "§fn/" + func + "/" + index + "/" + callSeq;
    }

    public static boolean isCallFrame(ControlFrame f) {
        return f != null && f.data().containsKey(KEY_CALL);
    }

    public static int callBlockId(ControlFrame f) {
        Object v = f.data().get(KEY_CALL);
        return v instanceof Number n ? n.intValue() : -1;
    }

    /** Finds the call sequence of the innermost active invocation of {@code func} on this branch's stack. */
    public static long currentCallSeq(Branch br, String func) {
        for (ControlFrame f : br.frames()) {
            if (!isCallFrame(f)) continue;
            if (!Objects.equals(func, f.data().get(KEY_FUNC))) continue;
            Object seq = f.data().get(KEY_SEQ);
            if (seq instanceof Number n) return n.longValue();
        }
        return -1;
    }

    public static void clearParams(ControlFrame f, ScriptRunner r) {
        Object nameObj = f.data().get(KEY_FUNC);
        Object seqObj = f.data().get(KEY_SEQ);
        if (!(nameObj instanceof String name) || !(seqObj instanceof Number seqNum)) return;
        FuncDecl decl = r.script().function(name);
        if (decl == null) return;
        long seq = seqNum.longValue();
        for (int i = 0; i < decl.numParams(); i++) {
            r.removeVariable(paramKey(name, i, seq));
        }
    }

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        flow.put(BlockType.FUNC_DEFINE, (b, r, br) -> StepResult.end());

        flow.put(BlockType.FUNC_CALL, FunctionExecutor::stepCall);

        reporter.put(BlockType.FUNC_PARAM, (b, r, br) -> {
            String func = ParamEval.asString(b.getParam("func"));
            Object idx = b.getParam("index");
            long seq = currentCallSeq(br, func);
            if (seq < 0) return null;
            RuntimeVariable v = r.variable(paramKey(func, idx instanceof Number n ? n.intValue() : -1, seq));
            return v == null ? null : v.value();
        });
    }

    private static StepResult stepCall(BlockInstance b, ScriptRunner r, Branch br) {
        String name = ParamEval.asString(b.getParam("name"));
        if (name == null || name.isEmpty()) return StepResult.continueVia(0);

        FuncDecl decl = r.script().function(name);
        BlockInstance define = findDefine(r, name);
        if (decl == null || define == null) return StepResult.continueVia(0);

        List<Wire> body = r.outgoing(define.id(), 0);
        if (body.isEmpty()) return StepResult.continueVia(0);

        int depth = 0;
        for (ControlFrame f : br.frames()) if (isCallFrame(f)) depth++;
        if (depth >= MAX_CALL_DEPTH) {
            HeroBot.LOGGER.warn("Function '{}' exceeded call depth {} - ending branch", name, MAX_CALL_DEPTH);
            return StepResult.end();
        }
        if (br.callsThisTick() >= MAX_CALLS_PER_TICK) return StepResult.wait(1);

        // Evaluate every argument before rebinding, so a recursive call can pass its own params along.
        List<Object> values = new ArrayList<>(decl.numParams());
        for (int i = 0; i < decl.numParams(); i++) {
            Object raw = ParamEval.raw(b, "Arg" + (i + 1), r, br);
            values.add(DataExecutor.coerce(Objects.requireNonNull(decl.paramType(i)), raw, r));
        }

        long seq = r.nextCallSeq();
        for (int i = 0; i < decl.numParams(); i++) {
            r.defineVariable(paramKey(name, i, seq), new RuntimeVariable(decl.paramType(i), values.get(i)));
        }

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_CALL, b.id());
        data.put(KEY_FUNC, name);
        data.put(KEY_SEQ, seq);

        br.useCall();
        return StepResult.pushAndJump(body.getFirst().toBlockId(), -1, data);
    }

    private static BlockInstance findDefine(ScriptRunner r, String name) {
        for (BlockInstance b : r.script().hatBlocks(BlockType.FUNC_DEFINE)) {
            if (name.equals(ParamEval.asString(b.getParam("name")))) return b;
        }
        return null;
    }
}
