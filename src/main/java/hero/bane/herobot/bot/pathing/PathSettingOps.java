package hero.bane.herobot.bot.pathing;

public final class PathSettingOps {
    private PathSettingOps() {}

    public static final int MAX_HORIZONTAL = 0, MAX_VERTICAL = 1, NODE_HORIZONTAL = 2, NODE_VERTICAL = 3,
            HORIZONTAL_COST = 4, VERTICAL_COST = 5, SWIM_COST = 6, STOP_FOLLOWING = 7, DEBUG = 8;

    public static int keyIndex(String key) {
        if (key == null) return -1;
        return switch (key) {
            case "maxHorizontalDistance" -> MAX_HORIZONTAL;
            case "maxVerticalDistance" -> MAX_VERTICAL;
            case "nodeHorizontalDistance" -> NODE_HORIZONTAL;
            case "nodeVerticalDistance" -> NODE_VERTICAL;
            case "horizontalMoveCost" -> HORIZONTAL_COST;
            case "verticalMoveCost" -> VERTICAL_COST;
            case "swimCostMultiplier" -> SWIM_COST;
            case "stopFollowing" -> STOP_FOLLOWING;
            case "debug" -> DEBUG;
            default -> -1;
        };
    }

    public static Double parseValue(int index, String value) {
        if (value == null) return null;
        if (index == STOP_FOLLOWING || index == DEBUG) {
            return Boolean.parseBoolean(value.trim()) ? 1.0 : 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static void apply(PathSettings s, int index, double value) {
        switch (index) {
            case MAX_HORIZONTAL -> s.setMaxHorizontalDistance(value);
            case MAX_VERTICAL -> s.setMaxVerticalDistance(value);
            case NODE_HORIZONTAL -> s.setNodeHorizontalDistance(value);
            case NODE_VERTICAL -> s.setNodeVerticalDistance(value);
            case HORIZONTAL_COST -> s.setHorizontalMoveCost(value);
            case VERTICAL_COST -> s.setVerticalMoveCost(value);
            case SWIM_COST -> s.setSwimCostMultiplier(value);
            case STOP_FOLLOWING -> s.setStopFollowing(value != 0);
            case DEBUG -> s.setDebug(value != 0);
        }
    }
}
