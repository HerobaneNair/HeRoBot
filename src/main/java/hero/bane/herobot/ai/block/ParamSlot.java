package hero.bane.herobot.ai.block;

import java.util.List;

public record ParamSlot(String name, ParamType type, Object defaultValue, List<String> enumChoices) {
    public ParamSlot(String name, ParamType type, Object defaultValue) {
        this(name, type, defaultValue, List.of());
    }

    public static ParamSlot of(String name, ParamType type, Object defaultValue) {
        return new ParamSlot(name, type, defaultValue);
    }

    public static ParamSlot ofBool(String name, boolean defaultValue) {
        return new ParamSlot(name, ParamType.BOOLEAN, defaultValue);
    }

    public static ParamSlot ofInt(String name, int defaultValue) {
        return new ParamSlot(name, ParamType.INT, defaultValue);
    }

    public static ParamSlot ofDouble(String name, double defaultValue) {
        return new ParamSlot(name, ParamType.DOUBLE, defaultValue);
    }

    public static ParamSlot ofString(String name, String defaultValue) {
        return new ParamSlot(name, ParamType.STRING, defaultValue);
    }

    public static ParamSlot ofEnum(String name, List<String> choices, String defaultValue) {
        return new ParamSlot(name, ParamType.ENUM, defaultValue, choices);
    }

    public static ParamSlot ofVarRef(String name, String defaultValue) {
        return new ParamSlot(name, ParamType.VAR_REF, defaultValue);
    }

    public static ParamSlot ofUuid(String name) {
        return new ParamSlot(name, ParamType.UUID, "@n[]");
    }

    public static ParamSlot ofPos(String name) {
        return new ParamSlot(name, ParamType.POSITION, "~ ~ ~");
    }

    public static ParamSlot ofRot(String name) {
        return new ParamSlot(name, ParamType.ROTATION, "0 0");
    }

    public static ParamSlot ofItem(String name, String defaultValue) {
        return new ParamSlot(name, ParamType.ITEM, defaultValue);
    }
}
