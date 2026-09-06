package hero.bane.herobot.common.ping;

public final class PingProfile {

    private final PingDelayOptions options = new PingDelayOptions();

    private volatile PingDelaySpec delay = PingDelaySpec.NONE;
    private volatile PingBurstSpec burst = PingBurstSpec.NONE;

    public PingDelayOptions options() {
        return options;
    }

    public PingDelaySpec delay() {
        return delay;
    }

    public void setDelay(PingDelaySpec spec) {
        this.delay = spec == null ? PingDelaySpec.NONE : spec;
    }

    public PingBurstSpec burst() {
        return burst;
    }

    public void setBurst(PingBurstSpec spec) {
        this.burst = spec == null ? PingBurstSpec.NONE : spec;
    }

    public void reset() {
        this.delay = PingDelaySpec.NONE;
        this.burst = PingBurstSpec.NONE;
    }

    public boolean isDefault() {
        return options.isDefault() && !delay.isActive() && !burst.isActive();
    }
}
