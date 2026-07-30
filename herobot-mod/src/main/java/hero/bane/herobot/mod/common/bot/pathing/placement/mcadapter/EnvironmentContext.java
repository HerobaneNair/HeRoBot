package hero.bane.herobot.mod.common.bot.pathing.placement.mcadapter;

import hero.bane.herobot.mod.common.bot.pathing.PathSettings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class EnvironmentContext implements de.bsommerfeld.pathetic.api.pathing.context.EnvironmentContext {
    private final Level level;
    private final PathSettings settings;
    private final Player actor;
    private final int maxJumpHeight;
    private final int maxFallDistance;

    public EnvironmentContext(Level level, PathSettings settings, Player actor,
                              int maxJumpHeight, int maxFallDistance) {
        this.level = level;
        this.settings = settings;
        this.actor = actor;
        this.maxJumpHeight = maxJumpHeight;
        this.maxFallDistance = maxFallDistance;
    }

    public Level level() { return level; }
    public PathSettings settings() { return settings; }
    public Player actor() { return actor; }
    public int maxJumpHeight() { return maxJumpHeight; }
    public int maxFallDistance() { return maxFallDistance; }
}
