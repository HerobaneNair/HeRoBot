package hero.bane.herobot.common.ai.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

public final class Branch {
    private int currentBlockId;
    private int waitTicks;
    private boolean dead;
    private boolean parked;
    private int turns;
    private int callsThisTick;

    private int originId = -1;
    private int pathWaitFor = -1;
    private String eventMessage;
    private final Deque<ControlFrame> frames = new ArrayDeque<>();

    public Branch(int startBlockId) {
        this.currentBlockId = startBlockId;
    }

    public int currentBlockId() { return currentBlockId; }
    public void setCurrentBlockId(int id) { this.currentBlockId = id; }

    public int waitTicks() { return waitTicks; }
    public void setWaitTicks(int t) { this.waitTicks = t; }
    public boolean decrementWait() {
        if (waitTicks <= 0) return false;
        waitTicks--;
        return true;
    }

    public boolean isDead() { return dead; }
    public void kill() { this.dead = true; }

    public boolean parked() { return parked; }
    public void setParked(boolean p) { this.parked = p; }

    public int turns() { return turns; }
    public void useTurn() { turns++; }
    public void resetTurns() { turns = 0; }

    public int callsThisTick() { return callsThisTick; }
    public void useCall() { callsThisTick++; }
    public void resetCalls() { callsThisTick = 0; }

    public int originId() { return originId; }
    public void setOriginId(int id) { this.originId = id; }

    public String eventMessage() { return eventMessage; }
    public void setEventMessage(String msg) { this.eventMessage = msg; }

    public int pathWaitFor() { return pathWaitFor; }
    public void setPathWaitFor(int id) { this.pathWaitFor = id; }

    public Deque<ControlFrame> frames() { return frames; }

    public Branch forkAt(int blockId) {
        Branch copy = new Branch(blockId);
        copy.originId = this.originId;
        copy.pathWaitFor = this.pathWaitFor;
        copy.eventMessage = this.eventMessage;
        copy.turns = this.turns;
        copy.callsThisTick = this.callsThisTick;
        for (ControlFrame f : this.frames) {
            ControlFrame nf = new ControlFrame(f.containerBlockId(), f.activationId());
            nf.data().putAll(f.data());
            copy.frames.addLast(nf);
        }
        return copy;
    }
}
