package hero.bane.herobot.ai.runtime.executors;

import hero.bane.herobot.HeroBot;
import hero.bane.herobot.ai.FuncDecl;
import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.block.Wire;
import hero.bane.herobot.ai.runtime.Branch;
import hero.bane.herobot.ai.runtime.ControlFrame;
import hero.bane.herobot.ai.runtime.Executor;
import hero.bane.herobot.ai.runtime.ParamEval;
import hero.bane.herobot.ai.runtime.Reporter;
import hero.bane.herobot.ai.runtime.RuntimeVariable;
import hero.bane.herobot.ai.runtime.ScriptRunner;
import hero.bane.herobot.ai.runtime.StepResult;

import java.util.*;

public final class FunctionExecutor {
    public static final int MAX_CALL_DEPTH = 50;
    public static final int MAX_CALLS_PER_TICK = 50;

    private static final String KEY_CALL = "fnCall";
    private static final String KEY_SAVED = "fnSaved";

    private FunctionExecutor() {}

    /** Reserved prefix; user variable names cannot contain '§', so params can never collide. */
    public static String paramKey(String func, int index) {
        return "§fn/" + func + "/" + index;
    }

    public static boolean isCallFrame(ControlFrame f) {
        return f != null && f.data().containsKey(KEY_CALL);
    }

    public static int callBlockId(ControlFrame f) {
        Object v = f.data().get(KEY_CALL);
        return v instanceof Number n ? n.intValue() : -1;
    }

    @SuppressWarnings("unchecked")
    public static void restoreParams(ControlFrame f, ScriptRunner r) {
        Object saved = f.data().get(KEY_SAVED);
        if (!(saved instanceof Map<?, ?> map)) return;
        for (Map.Entry<String, RuntimeVariable> e : ((Map<String, RuntimeVariable>) map).entrySet()) {
            r.defineVariable(e.getKey(), e.getValue());
        }
    }

    public static void register(Map<BlockType, Executor> flow, Map<BlockType, Reporter> reporter) {
        flow.put(BlockType.FUNC_DEFINE, (b, r, br) -> StepResult.end());

        flow.put(BlockType.FUNC_CALL, FunctionExecutor::stepCall);

        reporter.put(BlockType.FUNC_PARAM, (b, r, br) -> {
            String func = ParamEval.asString(b.getParam("func"));
            Object idx = b.getParam("index");
            RuntimeVariable v = r.variable(paramKey(func, idx instanceof Number n ? n.intValue() : -1));
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
        List<Object> values = new ArrayList<>(decl.arity());
        for (int i = 0; i < decl.arity(); i++) {
            Object raw = ParamEval.raw(b, "Arg" + (i + 1), r, br);
            values.add(DataExecutor.coerce(Objects.requireNonNull(decl.paramType(i)), raw, r));
        }

        Map<String, RuntimeVariable> saved = new HashMap<>();
        for (int i = 0; i < decl.arity(); i++) {
            String key = paramKey(name, i);
            saved.put(key, r.variable(key));
            r.defineVariable(key, new RuntimeVariable(decl.paramType(i), values.get(i)));
        }

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_CALL, b.id());
        data.put(KEY_SAVED, saved);

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
