package hero.bane.herobot.common.bot.pathing;

public enum DebugChannel {
    NODES("nodes", "pending path nodes"),
    REACHED("reached", "burst when a node is reached"),
    RETRY("retry", "stuck-retry target node"),
    WAYPOINT("waypoint", "current waypoint being steered at"),
    LOOK("look", "point the bot is looking at"),
    JUMP("jump", "burst when the bot jumps"),
    STUCK("stuck", "stuck timer and backtracks"),
    RECALC("recalc", "burst when the path is recalculated");

    private final String id;
    private final String description;

    DebugChannel(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public static DebugChannel byId(String id) {
        for (DebugChannel channel : values()) {
            if (channel.id.equals(id)) return channel;
        }
        return null;
    }
}
