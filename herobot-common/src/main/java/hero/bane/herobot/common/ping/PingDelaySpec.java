package hero.bane.herobot.common.ping;

public record PingDelaySpec(PingRange range, PingMode mode) {

    public static final PingDelaySpec NONE = new PingDelaySpec(PingRange.ZERO, PingMode.BALANCE);

    public PingDelaySpec {
        if (range == null) range = PingRange.ZERO;
        if (mode == null) mode = PingMode.BALANCE;
    }

    public static PingDelaySpec of(int millis) {
        return new PingDelaySpec(PingRange.of(millis), PingMode.BALANCE);
    }

    public boolean isActive() {
        return !range.isZero();
    }

    public int roll() {
        return range.roll();
    }

    public int averageMs() {
        return range.averageInt();
    }

    public String describe() {
        if (!isActive()) return "off";
        String text = range + "ms " + mode.id();
        return range.isRange() ? text + " (avg " + averageMs() + "ms)" : text;
    }
}
