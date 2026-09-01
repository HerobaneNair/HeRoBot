package hero.bane.herobot.common.bot.pathing;

public class PathStats {
    public long elapsedNs;
    public int iterations;
    public boolean hitIterationCap;
    public int pathLength;
    public boolean success;

    public void copyFrom(PathStats other) {
        elapsedNs = other.elapsedNs;
        iterations = other.iterations;
        hitIterationCap = other.hitIterationCap;
        pathLength = other.pathLength;
        success = other.success;
    }

    public void reset() {
        elapsedNs = 0;
        iterations = 0;
        hitIterationCap = false;
        pathLength = 0;
        success = false;
    }

    public double elapsedMs() {
        return elapsedNs / 1_000_000.0;
    }

    @Override
    public String toString() {
        return String.format(
                "iterations=%d%s, elapsed=%.2fms, pathLength=%d, success=%s",
                iterations,
                hitIterationCap ? " (CAPPED)" : "",
                elapsedMs(),
                pathLength,
                success);
    }
}
