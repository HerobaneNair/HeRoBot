package hero.bane.herobot.ai.runtime;

import hero.bane.herobot.HeroBot;
import hero.bane.herobot.ai.AiScript;
import hero.bane.herobot.ai.VarDecl;
import hero.bane.herobot.ai.VarType;
import hero.bane.herobot.ai.runtime.executors.DataExecutor;
import hero.bane.herobot.ai.runtime.executors.PathExecutor;
import hero.bane.herobot.control.PlayerControllers;
import hero.bane.herobot.ai.block.BlockCategory;
import hero.bane.herobot.ai.block.BlockDefRegistry;
import hero.bane.herobot.ai.block.BlockInstance;
import hero.bane.herobot.ai.block.BlockType;
import hero.bane.herobot.ai.block.Wire;
import hero.bane.herobot.bot.BotPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScriptRunner {
    /**
     * Rebound every tick: Minecraft replaces the ServerPlayer instance on respawn and on dimension
     * change while keeping the UUID, so a reference captured at construction goes stale and every
     * action would target the old entity.
     */
    private ServerPlayer player;
    private final AiScript script;
    private final List<Branch> branches = new ArrayList<>();
    private final Map<Integer, JoinState> joins = new HashMap<>();
    private final Map<String, RuntimeVariable> variables = new HashMap<>();
    private int activationSeq;

    /**
     * Wire/hat lookups, resolved once instead of scanning every wire on every block transition.
     *
     * Safe to cache because a script is immutable while a runner holds it: only the editor mutates
     * an AiScript (via the wires()/blocks() views), and it never runs a ScriptRunner over the
     * instance it is editing. Anything that changes a script hands the runner a new AiScript.
     */
    private final Map<Long, List<Wire>> outIndex = new HashMap<>();
    private final Map<Integer, List<Wire>> inIndex = new HashMap<>();
    private final Map<BlockType, List<BlockInstance>> hatIndex = new EnumMap<>(BlockType.class);

    private boolean paused;

    private volatile boolean damagedThisTick;
    private volatile boolean pathReachedThisTick;
    private volatile boolean pathFailedThisTick;

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
        // Pre-sorted once here rather than on every navigate. Ordering is semantic: it decides the
        // order forked branches run in, so it must stay identical to the old per-step sort.
        for (List<Wire> bucket : outIndex.values()) {
            if (bucket.size() > 1) bucket.sort(byTargetCanvasOrder());
        }
    }

    private static long outKey(int blockId, int outPort) {
        return ((long) blockId << 32) | (outPort & 0xFFFFFFFFL);
    }

    /** Outgoing wires for a port, pre-sorted into canvas order. Never null; do not mutate. */
    public List<Wire> outgoing(int blockId, int outPort) {
        return outIndex.getOrDefault(outKey(blockId, outPort), List.of());
    }

    /** Incoming wires for a block. Never null; do not mutate. */
    public List<Wire> incoming(int blockId) {
        return inIndex.getOrDefault(blockId, List.of());
    }

    private List<BlockInstance> hats(BlockType type) {
        return hatIndex.computeIfAbsent(type, script::hatBlocks);
    }

    public ServerPlayer player() { return player; }

    /** Points the runner at the live entity after Minecraft replaced it (respawn, dimension change). */
    public void rebind(ServerPlayer current) {
        if (current != null) this.player = current;
    }

    public BotPlayer bot() { return player instanceof BotPlayer b ? b : null; }

    public AiScript script() { return script; }
    public int branchCount() { return branches.size(); }

    public boolean isPaused() { return paused; }

    /**
     * Freezes the script exactly where it is: branches, frames and variables are all kept, so a
     * resume carries on from the same block. The bot is stopped too, otherwise a "paused" script
     * would leave it walking its current path and swinging.
     */
    public void pause() {
        if (paused) return;
        paused = true;
        releaseExternalState();
        // A branch parked on a goto is still sitting on that block, so clearing what it was waiting
        // for makes it re-issue the path on resume rather than treat the cancelled walk as arrived.
        for (Branch b : branches) b.setPathWaitFor(-1);
    }

    public void resume() { paused = false; }

    /**
     * Stops the script without unloading it: the runner stays assigned so the script can be started
     * again, but nothing it left running in the world keeps going.
     */
    public void stop() {
        for (Branch b : branches) b.kill();
        branches.clear();
        joins.clear();
        paused = false;
        releaseExternalState();
    }

    /** Anything the script drives that outlives a branch has to be handed back, or the bot keeps
     *  pathing and swinging with no script left to stop it. */
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

    /**
     * Releases the activations a dying branch was holding. Only the last branch out of a shared
     * activation may drop it, which is exactly what runningCount answers.
     */
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
            if (hero.bane.herobot.ai.runtime.ParamEval.evalBool(hat, "condition", this, tmp)) {
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
        for (Wire w : outgoing(event.id(), 0)) {
            Branch br = new Branch(w.toBlockId());
            br.setOriginId(event.id());
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

    /**
     * Script variables are shared by every branch: a value stored by one branch is visible to all
     * the others, including branches that were already running when the store happened.
     */
    public RuntimeVariable variable(String name) {
        return variables.get(name);
    }

    public void defineVariable(String name, RuntimeVariable v) {
        variables.put(name, v);
    }

    /** Branch order is canvas order: leftmost script first, ties broken top-to-bottom. */
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
        if (branches.isEmpty()) { damagedThisTick = false; pathReachedThisTick = false; pathFailedThisTick = false; return; }

        for (Branch br : branches) {
            if (br.isDead()) continue;
            br.resetTurns();
            br.setParked(br.decrementWait());
        }

        // Branches advance in lock-step rather than one draining before the next starts: each gets
        // a slice, runs top-to-bottom until it reaches a yield point, then hands off to the branch
        // on its right. Rounds repeat until every branch is parked for the tick or dead.
        boolean anyRan = true;
        while (anyRan) {
            anyRan = false;
            for (int i = 0; i < branches.size(); i++) {
                Branch br = branches.get(i);
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

    /** Runs one branch until it yields, parks for the tick, or dies. */
    private void runSlice(Branch br) {
        for (int budget = 0; budget < STEP_BUDGET; budget++) {
            BlockInstance b = script.block(br.currentBlockId());
            if (b == null) { killBranch(br); return; }

            Executor exec = ScriptDispatch.flow(b.type());
            if (exec == null) {
                HeroBot.LOGGER.warn("No flow executor for {} — ending branch", b.type());
                killBranch(br);
                return;
            }

            StepResult r = exec.execute(b, this, br);
            applyResult(br, b, r);

            if (br.isDead()) return;
            // A pause block takes effect immediately; the branch stays on the block it advanced to.
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

    /**
     * Blocks that hand the slice to the next branch once they have run: every control block
     * (wait included, break excluded, since a break is just a jump) plus the two pathing blocks.
     * Everything else runs straight through so plain block-to-block wires cost no time.
     */
    private static boolean yieldsAfter(BlockType t) {
        return switch (t) {
            case BREAK -> false;
            case GOTO_POS, GOTO_ENTITY -> true;
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
            killBranch(br);
            return;
        }
        Wire first = wires.getFirst();
        br.setCurrentBlockId(first.toBlockId());
        for (int i = 1; i < wires.size(); i++) {
            Wire w = wires.get(i);
            branches.add(br.forkAt(w.toBlockId()));
        }
    }

    public Object evalReporter(BlockInstance reporter, Branch branch) {
        Reporter r = ScriptDispatch.reporter(reporter.type());
        if (r == null) return null;
        return r.evaluate(reporter, this, branch);
    }
}
