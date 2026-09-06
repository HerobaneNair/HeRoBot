package hero.bane.herobot.common.ping;

import java.util.concurrent.ThreadLocalRandom;

public record PingRange(int min, int max) {

    public static final PingRange ZERO = new PingRange(0, 0);

    public PingRange {
        if (min < 0) min = 0;
        if (max < min) max = min;
    }

    public static PingRange of(int value) {
        int clamped = Math.max(0, value);
        return new PingRange(clamped, clamped);
    }

    public static PingRange parse(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;

        int dash = trimmed.indexOf('-', 1);
        try {
            if (dash < 0) {
                int single = Integer.parseInt(trimmed);
                return single < 0 ? null : of(single);
            }
            int low = Integer.parseInt(trimmed.substring(0, dash).trim());
            int high = Integer.parseInt(trimmed.substring(dash + 1).trim());
            if (low < 0 || high < 0) return null;
            return low <= high ? new PingRange(low, high) : new PingRange(high, low);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isRange() {
        return max > min;
    }

    public boolean isZero() {
        return max <= 0;
    }

    public int roll() {
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public double average() {
        return (min + max) / 2.0;
    }

    public int averageInt() {
        return (int) Math.round(average());
    }

    @Override
    public String toString() {
        return isRange() ? min + "-" + max : Integer.toString(min);
    }
}
