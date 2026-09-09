package hero.bane.herobot.common.ping;

import java.util.concurrent.ThreadLocalRandom;

public final class PingLatency {

    private int heldTicks = -1;
    private long lastUseTick = Long.MIN_VALUE;

    public int ticks(long now, PingDelaySpec spec, int fixedMs, int millisPerTick) {
        if (millisPerTick <= 0) {
            reset();
            return 0;
        }
        if (heldTicks < 0 || now < lastUseTick || now - lastUseTick > heldTicks) {
            int millis = spec != null && spec.isActive() ? spec.roll() : fixedMs;
            heldTicks = roll(millis, millisPerTick);
        }
        lastUseTick = now;
        return heldTicks;
    }

    public void reset() {
        heldTicks = -1;
        lastUseTick = Long.MIN_VALUE;
    }

    private static int roll(int millis, int millisPerTick) {
        if (millis <= 0) return 0;
        int whole = millis / millisPerTick;
        int remainder = millis % millisPerTick;
        if (remainder == 0) return whole;
        return ThreadLocalRandom.current().nextInt(millisPerTick) < remainder ? whole + 1 : whole;
    }
}
