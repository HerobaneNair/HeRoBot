package hero.bane.herobot.common.ping;

public record PingBurstSpec(PingRange burstTicks, PingRange intervalTicks) {

    public static final int MS_PER_TICK = 50;

    public static final PingBurstSpec NONE = new PingBurstSpec(PingRange.ZERO, null);

    public PingBurstSpec {
        if (burstTicks == null) burstTicks = PingRange.ZERO;
        if (intervalTicks != null && intervalTicks.isZero()) intervalTicks = null;
    }

    public boolean isActive() {
        return !burstTicks.isZero();
    }

    public boolean repeats() {
        return intervalTicks != null;
    }

    public int averageAddedMs() {
        if (!isActive()) return 0;
        double burst = burstTicks.average();
        double meanWait = burst / 2.0 * MS_PER_TICK;
        if (!repeats()) return (int) Math.round(meanWait);

        double total = burst + intervalTicks.average();
        if (total <= 0) return 0;
        return (int) Math.round(burst / total * meanWait);
    }

    public String describe() {
        if (!isActive()) return "off";
        String text = burstTicks + " ticks";
        text += repeats() ? " every " + intervalTicks + " ticks" : " once";
        return text + " (avg +" + averageAddedMs() + "ms)";
    }
}
