package hero.bane.herobot.ai.block;

public enum BlockCategory {
    EVENT("EVENT", 0xFFEEBB22),
    MOTION("MOTION", 0xFF4499FF),
    ACTION("ACTION", 0xFFFFAA11),
    LOOK("LOOK", 0xFF55BBDD),
    PATH("PATH", 0xFF55CC55),
    INVENTORY("INVENTORY", 0xFFCC66CC),
    CONTROL("CONTROL", 0xFFFFBB00),
    DATA("DATA", 0xFFFF8811),
    DATA_MANIPULATION("DATA MANIP", 0xFF11AAAA),
    SENSOR("SENSOR", 0xFF55BBDD),
    OPERATOR("OPERATOR", 0xFF44BB44);

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
