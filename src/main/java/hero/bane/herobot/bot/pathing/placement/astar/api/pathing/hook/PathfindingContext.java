package hero.bane.herobot.bot.pathing.placement.astar.api.pathing.hook;

import hero.bane.herobot.bot.pathing.placement.astar.api.pathing.context.EnvironmentContext;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.Depth;
import hero.bane.herobot.bot.pathing.placement.astar.api.wrapper.PathPosition;

import java.util.Objects;

public final class PathfindingContext {
    private final PathPosition currentPosition;
    private final Depth depth;
    private final PathPosition target;
    private final EnvironmentContext environmentContext;

    public PathfindingContext(
        PathPosition position,
        Depth depth,
        PathPosition target,
        EnvironmentContext environmentContext) {
        this.currentPosition = position;
        this.depth = depth;
        this.target = target;
        this.environmentContext = environmentContext;
    }

    public PathPosition currentPosition() {
        return currentPosition;
    }

    public Depth getDepth() {
        return this.depth;
    }

    public PathPosition target() {
        return target;
    }

    public EnvironmentContext environmentContext() {
        return environmentContext;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof PathfindingContext)) return false;
        final PathfindingContext other = (PathfindingContext) o;
        final Object this$depth = this.getDepth();
        final Object other$depth = other.getDepth();
        return Objects.equals(this$depth, other$depth);
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $depth = this.getDepth();
        result = result * PRIME + ($depth == null ? 43 : $depth.hashCode());
        return result;
    }

    public String toString() {
        return "PathfindingContext(depth=" + this.getDepth() + ")";
    }
}
