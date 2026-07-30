package hero.bane.herobot.mod.common.ai.block;

public enum BlockCategory {
    EVENT("EVENT", 0xFFEEBB22),
    ACTION("ACTION", 0xFFFFAA11),
    MOTION("MOTION", 0xFF4499FF),
    PATH("PATH", 0xFF55CC55),
    LOOK("LOOK", 0xFF55BBDD),
    CONTROL("CONTROL", 0xFFFFBB00),
    VARIABLE("VARIABLE", 0xFFFF8811),
    OPERATOR("OPERATOR", 0xFF44BB44),
    INVENTORY("INVENTORY", 0xFFCC66CC),
    DATA_MANIPULATION("DATA MANIP", 0xFF11AAAA),
    SENSOR("SENSOR", 0xFF55BBDD),
    FUNCTIONS("FUNCTIONS", 0xFF9B6BEF);

    private final String display;
    private final int color;

    BlockCategory(String display, int color) {
        this.display = display;
        this.color = color;
    }

    public String display() {
        return display;
    }

    public int color() {
        return color;
    }
}
