package hero.bane.herobot.ai;

public enum VarType {
    BOOL,
    INT,
    DOUBLE,
    POSITION,
    ROTATION,
    STRING,
    UUID,
    ITEM;

    public String displayName() {
        return switch (this) {
            case BOOL -> "boolean";
            case INT -> "int";
            case DOUBLE -> "decimal";
            case POSITION -> "position";
            case ROTATION -> "direction";
            case STRING -> "String";
            case UUID -> "UUID";
            case ITEM -> "Item";
        };
    }

    public String chipLabel() {
        return switch (this) {
            case BOOL -> "bool";
            case INT -> "int";
            case DOUBLE -> "deci";
            case POSITION -> "Pos";
            case ROTATION -> "Dir";
            case STRING -> "String";
            case UUID -> "UUID";
            case ITEM -> "Item";
        };
    }
}
