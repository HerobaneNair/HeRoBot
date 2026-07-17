package hero.bane.herobot.ai.runtime;

import java.util.Map;

public final class StepResult {
    public enum Kind { CONTINUE, WAIT, JUMP, END, HANDLED }

    private final Kind kind;
    private final int port;
    private final int waitTicks;
    private final int jumpTo;
    private final boolean pushFrame;
    private final boolean popFrame;
    private final int activationId;
    private final Map<String, Object> frameData;

    private StepResult(Kind kind, int port, int waitTicks, int jumpTo,
                       boolean pushFrame, boolean popFrame, int activationId,
                       Map<String, Object> frameData) {
        this.kind = kind;
        this.port = port;
        this.waitTicks = waitTicks;
        this.jumpTo = jumpTo;
        this.pushFrame = pushFrame;
        this.popFrame = popFrame;
        this.activationId = activationId;
        this.frameData = frameData;
    }

    public Kind kind() { return kind; }
    public int port() { return port; }
    public int waitTicks() { return waitTicks; }
    public int jumpTo() { return jumpTo; }
    public boolean pushFrame() { return pushFrame; }
    public boolean popFrame() { return popFrame; }
    public int activationId() { return activationId; }
    public Map<String, Object> frameData() { return frameData; }

    public static StepResult continueVia(int port) {
        return new StepResult(Kind.CONTINUE, port, 0, 0, false, false, -1, null);
    }

    public static StepResult enterBody(int port) {
        return new StepResult(Kind.CONTINUE, port, 0, 0, true, false, -1, null);
    }

    public static StepResult enterBody(int port, int activationId) {
        return new StepResult(Kind.CONTINUE, port, 0, 0, true, false, activationId, null);
    }

    public static StepResult enterBody(int port, int activationId, Map<String, Object> data) {
        return new StepResult(Kind.CONTINUE, port, 0, 0, true, false, activationId, data);
    }

    public static StepResult exitBody(int port) {
        return new StepResult(Kind.CONTINUE, port, 0, 0, false, true, -1, null);
    }

    public static StepResult pushAndJump(int blockId, int activationId) {
        return new StepResult(Kind.JUMP, 0, 0, blockId, true, false, activationId, null);
    }

    public static StepResult popAndJump(int blockId) {
        return new StepResult(Kind.JUMP, 0, 0, blockId, false, true, -1, null);
    }

    public static StepResult wait(int ticks) {
        return new StepResult(Kind.WAIT, 0, Math.max(0, ticks), 0, false, false, -1, null);
    }

    public static StepResult waitThenContinue(int ticks, int port) {
        return new StepResult(Kind.CONTINUE, port, Math.max(0, ticks), 0, false, false, -1, null);
    }

    public static StepResult jumpTo(int blockId) {
        return new StepResult(Kind.JUMP, 0, 0, blockId, false, false, -1, null);
    }

    public static StepResult end() {
        return new StepResult(Kind.END, 0, 0, 0, false, false, -1, null);
    }

    public static StepResult handled() {
        return new StepResult(Kind.HANDLED, 0, 0, 0, false, false, -1, null);
    }
}
