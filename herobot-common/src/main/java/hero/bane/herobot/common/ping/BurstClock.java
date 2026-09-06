package hero.bane.herobot.common.ping;

public final class BurstClock {

    public enum Transition {
        NONE,
        START_BURST,
        END_BURST
    }

    private enum State {
        IDLE,
        BURSTING,
        WAITING,
        DONE
    }

    private volatile PingBurstSpec spec = PingBurstSpec.NONE;
    private volatile State state = State.DONE;
    private int ticksLeft;

    public PingBurstSpec spec() {
        return spec;
    }

    public void setSpec(PingBurstSpec value) {
        this.spec = value == null ? PingBurstSpec.NONE : value;
        reset();
    }

    public void reset() {
        state = spec.isActive() ? State.IDLE : State.DONE;
        ticksLeft = 0;
    }

    public boolean isBursting() {
        return state == State.BURSTING;
    }

    public boolean isFinished() {
        return state == State.DONE;
    }

    public int ticksUntilRelease() {
        return state == State.BURSTING ? ticksLeft : 0;
    }

    public Transition tick() {
        switch (state) {
            case IDLE -> {
                startBurst();
                return Transition.START_BURST;
            }
            case BURSTING -> {
                if (--ticksLeft > 0) return Transition.NONE;
                if (spec.repeats()) {
                    state = State.WAITING;
                    ticksLeft = Math.max(1, spec.intervalTicks().roll());
                } else {
                    state = State.DONE;
                    spec = PingBurstSpec.NONE;
                }
                return Transition.END_BURST;
            }
            case WAITING -> {
                if (--ticksLeft > 0) return Transition.NONE;
                startBurst();
                return Transition.START_BURST;
            }
            default -> {
                return Transition.NONE;
            }
        }
    }

    private void startBurst() {
        state = State.BURSTING;
        ticksLeft = Math.max(1, spec.burstTicks().roll());
    }
}
