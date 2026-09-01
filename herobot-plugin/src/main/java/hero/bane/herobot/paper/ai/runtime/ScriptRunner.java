package hero.bane.herobot.paper.ai.runtime;

import hero.bane.herobot.paper.HeroBot;
import hero.bane.herobot.common.ai.AiScript;
import hero.bane.herobot.common.ai.VarDecl;
import hero.bane.herobot.common.ai.VarType;
import hero.bane.herobot.paper.ai.runtime.executors.DataExecutor;
import hero.bane.herobot.paper.ai.runtime.executors.FunctionExecutor;
import hero.bane.herobot.paper.ai.runtime.executors.PathExecutor;
import hero.bane.herobot.paper.control.PlayerControllers;
import hero.bane.herobot.common.ai.block.BlockCategory;
import hero.bane.herobot.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.common.ai.block.BlockInstance;
import hero.bane.herobot.common.ai.block.BlockType;
import hero.bane.herobot.common.ai.block.Wire;
import hero.bane.herobot.paper.bot.BotPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import hero.bane.herobot.common.ai.runtime.Branch;
import hero.bane.herobot.common.ai.runtime.ControlFrame;
import hero.bane.herobot.common.ai.runtime.JoinState;
import hero.bane.herobot.common.ai.runtime.RuntimeVariable;
import hero.bane.herobot.common.ai.runtime.StepResult;

public final class ScriptRunner {
    private ServerPlayer player;
    private final AiScript script;
    private final List<Branch> branches = new ArrayList<>();
    private final Map<Integer, JoinState> joins = new HashMap<>();
    private final Map<String, RuntimeVariable> variables = new HashMap<>();
    private int activationSeq;
    private long callSeq;

    private final Map<Long, List<Wire>> outIndex = new HashMap<>();
    private final Map<Integer, List<Wire>> inIndex = new HashMap<>();
    private final Map<BlockType, List<BlockInstance>> hatIndex = new EnumMap<>(BlockType.class);

    private boolean paused;

    private volatile boolean damagedThisTick;
    private volatile boolean pathReachedThisTick;
    private volatile boolean pathFailedThisTick;

    private static final int MAX_CHAT_QUEUE = 256;
    private final java.util.Queue<String> chatQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public ScriptRunner(ServerPlayer player, AiScript script) {
        this.player = player;
        this.script = script;
        buildIndexes();
        initVariables();
    }

    private void buildIndexes() {
        for (Wire w : script.wires()) {
            outIndex.computeIfAbsent(outKey(w.fromBlockId(), w.outPort()), k -> new ArrayList<>()).add(w);
            inIndex.computeIfAbsent(w.toBlockId(), k -> new ArrayList<>()).add(w);
        }
        for (List<Wire> bucket : outIndex.values()) {
            if (bucket.size() > 1) bucket.sort(byTargetCanvasOrder());
        }
    }

    private static long outKey(int blockId, int outPort) {
        return ((long) blockId << 32) | (outPort & 0xFFFFFFFFL);
    }

    public List<Wire> outgoing(int blockId, int outPort) {
        return outIndex.getOrDefault(outKey(blockId, outPort), List.of());
    }

    public List<Wire> incoming(int blockId) {
        return inIndex.getOrDefault(blockId, List.of());
    }

    private List<BlockInstance> hats(BlockType type) {
        return hatIndex.computeIfAbsent(type, script::hatBlocks);
    }

    public ServerPlayer player() { return player; }

    public void rebind(ServerPlayer current) {
        if (current != null) this.player = current;
    }

    public BotPlayer bot() { return player instanceof BotPlayer b ? b : null; }

    public AiScript script() { return script; }
    public int branchCount() { return branches.size(); }

    public boolean isPaused() { return paused; }

    public void pause() {
        if (paused) return;
        paused = true;
        releaseExternalState();
        for (Branch b : branches) b.setPathWaitFor(-1);
    }

    public void resume() { paused = false; }

    public void stop() {
        for (Branch b : branches) b.kill();
        branches.clear();
        joins.clear();
        chatQueue.clear();
        paused = false;
        releaseExternalState();
    }

    private void releaseExternalState() {
        try {
            BotPlayer bot = bot();
            if (bot != null) bot.clearPathFollower();
            else PathExecutor.stopRemotePath(player);
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Failed to stop pathing on script stop: {}", t.toString());
        }
        try {
            PlayerControllers.of(player).stopAll();
        } catch (Throwable t) {
            HeroBot.LOGGER.warn("Failed to stop actions on script stop: {}", t.toString());
        }
    }

    private void killBranch(Branch br) {
        br.kill();
        for (ControlFrame f : br.frames()) {
            int act = f.activationId();
            if (act < 0 || !joins.containsKey(act)) continue;
            if (runningCount(act, br) == 0) joins.remove(act);
        }
    }

    public int openActivation(BlockType startType, int startId, int endId, int remaining) {
        int act = ++activationSeq;
        JoinState js = new JoinState(startType, startId, endId);
        js.setRemaining(remaining);
        joins.put(act, js);
        return act;
    }

    public JoinState joinState(int activationId) { return joins.get(activationId); }

    public void removeJoin(int activationId) { joins.remove(activationId); }

    public int runningCount(int activationId, Branch exclude) {
        int n = 0;
        for (Branch b : branches) {
            if (b == exclude || b.isDead()) continue;
            if (inActivation(b, activationId)) n++;
        }
        return n;
    }

    public void killActivation(int activationId, Branch exclude) {
        for (Branch b : branches) {
            if (b == exclude || b.isDead()) continue;
            if (inActivation(b, activationId)) b.kill();
        }
    }

    public void relaunchBody(JoinState js, Branch br) {
        navigate(br, js.startBlockId(), 0);
    }

    private static boolean inActivation(Branch b, int activationId) {
        for (ControlFrame f : b.frames()) {
            if (f.activationId() == activationId) return true;
        }
        return false;
    }

    public void fireStart() {
        for (BlockInstance start : hats(BlockType.START)) {
            spawnFromEvent(start);
        }
    }

    public void fireEvent(BlockType eventType) {
        if (paused) return;
        for (BlockInstance b : hats(eventType)) {
            if (isEventActive(b.id())) continue;
            spawnFromEvent(b);
        }
    }

    public void enqueueChatMessage(String message) {
        if (message == null) return;
        while (chatQueue.size() >= MAX_CHAT_QUEUE) chatQueue.poll();
        chatQueue.add(message);
    }

    private void dispatchMessage(String message) {
        for (BlockInstance b : hats(BlockType.ON_MESSAGE)) {
            spawnFromEvent(b, message);
        }
    }

    public void markDamaged() { this.damagedThisTick = true; }

    public boolean wasDamaged() { return damagedThisTick; }

    public void markPathReached() { this.pathReachedThisTick = true; }

    public void markPathFailed() { this.pathFailedThisTick = true; }

    public boolean pathReached() { return pathReachedThisTick; }

    public boolean pathFailed() { return pathFailedThisTick; }

    private void fireToggleEvents() {
        List<BlockInstance> toggles = hats(BlockType.ON_TOGGLE);
        if (toggles.isEmpty()) return;
        for (BlockInstance hat : toggles) {
            if (isEventActive(hat.id())) continue;
            Branch tmp = new Branch(hat.id());
            if (hero.bane.herobot.paper.ai.runtime.ParamEval.evalBool(hat, "condition", this, tmp)) {
                spawnFromEvent(hat);
            }
        }
    }

    private boolean isEventActive(int eventId) {
        for (Branch br : branches) {
            if (!br.isDead() && br.originId() == eventId) return true;
        }
        return false;
    }

    private void spawnFromEvent(BlockInstance event) {
        spawnFromEvent(event, null);
    }

    private void spawnFromEvent(BlockInstance event, String payload) {
        for (Wire w : outgoing(event.id(), 0)) {
            Branch br = new Branch(w.toBlockId());
            br.setOriginId(event.id());
            br.setEventMessage(payload);
            branches.add(br);
        }
    }

    private void initVariables() {
        for (VarDecl v : script.variables()) {
            Object value = v.defaultValue();
            if (v.type() == VarType.ITEM) value = DataExecutor.asItemStack(value, this);
            variables.put(v.qualifiedName(), new RuntimeVariable(v.type(), value));
        }
    }

    public RuntimeVariable variable(String name) {
        return variables.get(name);
    }

    public void defineVariable(String name, RuntimeVariable v) {
        variables.put(name, v);
    }

    public void removeVariable(String name) {
        variables.remove(name);
    }

    public long nextCallSeq() {
        return ++callSeq;
    }

    private Comparator<Wire> byTargetCanvasOrder() {
        return (a, b) -> {
            BlockInstance ba = script.block(a.toBlockId());
            BlockInstance bb = script.block(b.toBlockId());
            if (ba == null || bb == null) return 0;
            int cmp = Double.compare(ba.x(), bb.x());
            return cmp != 0 ? cmp : Double.compare(ba.y(), bb.y());
        };
    }

    public void tick(ServerPlayer current) {
        if (current != null) this.player = current;
        if (paused) { damagedThisTick = false; pathReachedThisTick = false; pathFailedThisTick = false; return; }
        fireToggleEvents();
        String chat;
        while ((chat = chatQueue.poll()) != null) {
            dispatchMessage(chat);
        }
        if (branches.isEmpty()) { damagedThisTick = false; pathReachedThisTick = false; pathFailedThisTick = false; return; }

        for (Branch br : branches) {
            if (br.isDead()) continue;
            br.resetTurns();
            br.resetCalls();
            br.setParked(br.decrementWait());
        }

        boolean anyRan = true;
        while (anyRan) {
            anyRan = false;
            for (Branch br : branches) {
                if (br.isDead() || br.parked() || br.turns() >= MAX_TURNS_PER_TICK) continue;
                br.useTurn();
                try {
                    runSlice(br);
                } catch (Throwable t) {
                    HeroBot.LOGGER.warn("Branch failure at block {}: {}", br.currentBlockId(), t.toString());
                    killBranch(br);
                }
                anyRan = true;
            }
        }
        branches.removeIf(Branch::isDead);
        damagedThisTick = false;
        pathReachedThisTick = false;
        pathFailedThisTick = false;
    }

    private static final int STEP_BUDGET = 1_000_000;
    private static final int MAX_TURNS_PER_TICK = 256;

    private void runSlice(Branch br) {
        for (int budget = 0; budget < STEP_BUDGET; budget++) {
            BlockInstance b = script.block(br.currentBlockId());
            if (b == null) { killBranch(br); return; }

            Executor exec = ScriptDispatch.flow(b.type());
            if (exec == null) {
                HeroBot.LOGGER.warn("No flow executor for {} - ending branch", b.type());
                killBranch(br);
                return;
            }

            StepResult r = exec.execute(b, this, br);
            applyResult(br, b, r);

            if (br.isDead()) return;
            if (paused) {
                br.setParked(true);
                return;
            }
            if (r.kind() == StepResult.Kind.WAIT || br.waitTicks() > 0) {
                br.setParked(true);
                return;
            }
            if (yieldsAfter(b.type())) return;
        }
    }

    private static boolean yieldsAfter(BlockType t) {
        return switch (t) {
            case BREAK -> false;
            case GOTO_POS, GOTO_ENTITY, FUNC_CALL -> true;
            default -> BlockDefRegistry.get(t).category() == BlockCategory.CONTROL;
        };
    }

    private void applyResult(Branch br, BlockInstance current, StepResult r) {
        switch (r.kind()) {
            case END -> killBranch(br);
            case WAIT -> br.setWaitTicks(r.waitTicks());
            case HANDLED -> {  }
            case JUMP -> {
                applyFrames(br, current, r);
                br.setCurrentBlockId(r.jumpTo());
                if (r.waitTicks() > 0) br.setWaitTicks(r.waitTicks());
            }
            case CONTINUE -> {
                applyFrames(br, current, r);
                navigate(br, current.id(), r.port());
                if (r.waitTicks() > 0) br.setWaitTicks(r.waitTicks());
            }
        }
    }

    private void applyFrames(Branch br, BlockInstance current, StepResult r) {
        if (r.popFrame() && !br.frames().isEmpty()) {
            br.frames().pop();
        }
        if (r.pushFrame()) {
            ControlFrame f = new ControlFrame(current.id(), r.activationId());
            if (r.frameData() != null) f.data().putAll(r.frameData());
            br.frames().push(f);
        }
    }

    private void navigate(Branch br, int fromBlockId, int outPort) {
        List<Wire> wires = outgoing(fromBlockId, outPort);
        if (wires.isEmpty()) {
            if (!returnFromFunction(br)) killBranch(br);
            return;
        }
        Wire first = wires.getFirst();
        br.setCurrentBlockId(first.toBlockId());
        for (int i = 1; i < wires.size(); i++) {
            Wire w = wires.get(i);
            branches.add(br.forkAt(w.toBlockId()));
        }
    }

    public boolean returnFromFunction(Branch br) {
        ControlFrame top = br.frames().peek();
        if (!FunctionExecutor.isCallFrame(top)) return false;
        br.frames().pop();
        FunctionExecutor.clearParams(top, this);
        int callId = FunctionExecutor.callBlockId(top);
        if (script.block(callId) == null) return false;
        navigate(br, callId, 0);
        return true;
    }

    public Object evalReporter(BlockInstance reporter, Branch branch) {
        Reporter r = ScriptDispatch.reporter(reporter.type());
        if (r == null) return null;
        return r.evaluate(reporter, this, branch);
    }
}
