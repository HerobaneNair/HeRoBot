package hero.bane.herobot.common.ai.block;

import java.util.*;

public final class BlockDefRegistry {
    private static final Map<BlockType, BlockDef> DEFS = new EnumMap<>(BlockType.class);
    private static final List<String> ACTION_MODES = List.of("once", "continuous", "interval");
    private static final List<String> TWICE_MODES = List.of("once", "twice", "continuous", "interval");
    private static final List<String> DIRECTIONS = List.of("north", "south", "east", "west", "up", "down");
    private static final List<String> LOOK_AT_MODES = List.of("eyes", "closest", "feet");
    private static final List<String> DISTANCE_SHAPES = List.of("position", "hitbox");
    private static final List<String> DISTANCE_MODES = List.of("normal", "horizontal", "vertical");
    private static final List<String> MOVE_TYPES = List.of("WALK", "SPRINT", "SPRINT_JUMP");
    private static final List<String> ACTION_TYPES =
            List.of("USE", "SWING", "JUMP", "ATTACK", "DROP_ITEM", "DROP_STACK", "SWAP_HANDS");
    private static final List<String> HANDEDNESS = List.of("left", "right");
    private static final List<String> DROP_AMOUNTS = List.of("1", "stack");
    private static final List<String> PICK_BLOCK_DATA = List.of("no data", "with data");
    private static final List<String> DOING_ACTIONS =
            List.of("walking", "strafing", "sprinting", "sneaking", "using",
                    "attacking", "swinging", "jumping", "dropping", "swapping");
    private static final List<String> COMPARATORS = List.of("<", ">", "≤", "≥");
    private static final List<String> EQUALITY_OPS = List.of("=", "≠");
    private static final List<String> LOGIC_OPS = List.of("and", "or", "xor");
    private static final List<String> HOTBAR_SLOTS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
    private static final List<String> SWAP_TARGETS =
            List.of("offhand", "1", "2", "3", "4", "5", "6", "7", "8", "9");
    private static final List<String> RECIPE_MODES = List.of("single", "max");
    private static final List<String> COUNT_KINDS = List.of("count", "durability");
    private static final List<String> COOLDOWN_KINDS = List.of("fraction", "ticksLeft");
    private static final List<String> MOVE_DIRS = List.of("forward", "backward", "stop");
    private static final List<String> STRAFE_DIRS = List.of("left", "right", "stop");
    private static final List<String> PATH_SETTINGS = List.of(
            "maxHorizontalDistance", "maxVerticalDistance",
            "nodeHorizontalDistance", "nodeVerticalDistance",
            "horizontalMoveCost", "verticalMoveCost", "swimCostMultiplier",
            "stopFollowing", "debug"
    );
    private static final List<String> MENUS = List.of("inventory", "container");
    private static final List<String> CLICK_MODES = List.of("click", "rightClick", "shiftClick", "throw", "throwAll");
    private static final List<String> DRAG_BUTTONS = List.of("left", "right");
    private static final List<String> LOOT_SOURCES = List.of("container", "inventory");
    private static final List<String> AXES = List.of("yaw", "pitch");
    private static final List<String> EQUIP_SLOTS = List.of(
            "weapon.mainhand", "weapon.offhand",
            "armor.head", "armor.chest", "armor.legs", "armor.feet"
    );
    public static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down", "any");
    static {
        start();

        register(new BlockDef(BlockType.ON_TOGGLE, BlockCategory.EVENT, BlockShape.HAT, "when toggled true",
                1, List.of(ParamSlot.ofBool("condition", false))));
        register(new BlockDef(BlockType.ON_MESSAGE, BlockCategory.EVENT, BlockShape.HAT, "on message",
                1, List.of()));
        reporter(BlockType.MSG_TEXT, BlockCategory.EVENT, "message");

        stmt(BlockType.MOVE, BlockCategory.MOTION, "walk",
                ParamSlot.ofEnum("direction", MOVE_DIRS, "forward"));
        stmt(BlockType.STRAFE, BlockCategory.MOTION, "strafe",
                ParamSlot.ofEnum("direction", STRAFE_DIRS, "left"));
        stmt(BlockType.STOP_MOVEMENT, BlockCategory.MOTION, "stop movement");
        stmt(BlockType.SNEAK, BlockCategory.MOTION, "set sneaking",
                ParamSlot.ofBool("value", true));
        stmt(BlockType.SPRINT, BlockCategory.MOTION, "set sprinting",
                ParamSlot.ofBool("value", true));
        stmt(BlockType.AUTOJUMP, BlockCategory.MOTION, "set auto-jump",
                ParamSlot.ofBool("value", true));

        actionBlock(BlockType.USE, "use", TWICE_MODES);
        actionBlock(BlockType.SWING, "swing");
        actionBlock(BlockType.JUMP, "jump");
        stmt(BlockType.ATTEMPT_AUTOJUMP, BlockCategory.ACTION, "auto jump");
        actionBlock(BlockType.ATTACK, "attack", TWICE_MODES);
        register(new BlockDef(BlockType.DROP_ITEM, BlockCategory.ACTION, BlockShape.STATEMENT, "drop", 1,
                List.of(
                        ParamSlot.ofEnum("amount", DROP_AMOUNTS, "1"),
                        ParamSlot.ofEnum("mode", ACTION_MODES, "once"),
                        ParamSlot.ofInt("interval", 1),
                        ParamSlot.ofInt("ticks", 0)
                )));
        actionBlock(BlockType.SWAP_HANDS, "swap hands");
        stmt(BlockType.PICK_BLOCK, BlockCategory.ACTION, "pick block",
                ParamSlot.ofEnum("data", PICK_BLOCK_DATA, "no data"));
        stmt(BlockType.SEND_MESSAGE, BlockCategory.ACTION, "send message",
                ParamSlot.ofString("message", "Hello World"));
        stmt(BlockType.STOP_ACTION, BlockCategory.ACTION, "stop action",
                ParamSlot.ofEnum("action", ACTION_TYPES, "USE"));
        stmt(BlockType.STOP_ALL, BlockCategory.ACTION, "stop all actions");
        stmt(BlockType.PLACE_BLOCK, BlockCategory.ACTION, "place",
                ParamSlot.ofPos("position"),
                ParamSlot.ofEnum("face", FACES, "any"),
                ParamSlot.ofBool("force", false));
        stmt(BlockType.BREAK_BLOCK, BlockCategory.ACTION, "mine",
                ParamSlot.ofPos("position"),
                ParamSlot.ofBool("force", false));
        bool(BlockType.DOING_ACTION, BlockCategory.ACTION, "doing action",
                ParamSlot.ofEnum("action", DOING_ACTIONS, "walking"));

        stmt(BlockType.LOOK_DIRECTION, BlockCategory.LOOK, "look direction",
                ParamSlot.ofRot("direction"),
                ParamSlot.ofDouble("yawOffset", 0.0),
                ParamSlot.ofDouble("pitchOffset", 0.0),
                ParamSlot.ofInt("ticks", 0));
        stmt(BlockType.LOOK_CARDINAL, BlockCategory.LOOK, "look cardinal",
                ParamSlot.ofEnum("direction", DIRECTIONS, "north"),
                ParamSlot.ofDouble("yawOffset", 0.0),
                ParamSlot.ofDouble("pitchOffset", 0.0),
                ParamSlot.ofInt("ticks", 0));
        stmt(BlockType.LOOK_AT_POS, BlockCategory.LOOK, "look position",
                ParamSlot.ofPos("position"),
                ParamSlot.ofDouble("yawOffset", 0.0),
                ParamSlot.ofDouble("pitchOffset", 0.0),
                ParamSlot.ofInt("ticks", 0));
        stmt(BlockType.LOOK_AT_ENTITY, BlockCategory.LOOK, "look entity",
                ParamSlot.ofUuid("target"),
                ParamSlot.ofEnum("mode", LOOK_AT_MODES, "eyes"),
                ParamSlot.ofDouble("yawOffset", 0.0),
                ParamSlot.ofDouble("pitchOffset", 0.0),
                ParamSlot.ofInt("ticks", 0));
        stmt(BlockType.TURN, BlockCategory.LOOK, "turn",
                ParamSlot.ofDouble("dYaw", 0.0),
                ParamSlot.ofDouble("dPitch", 0.0),
                ParamSlot.ofDouble("yawOffset", 0.0),
                ParamSlot.ofDouble("pitchOffset", 0.0),
                ParamSlot.ofInt("ticks", 0));

        stmt(BlockType.GOTO_POS, BlockCategory.PATH, "path pos",
                ParamSlot.ofPos("position"));
        stmt(BlockType.GOTO_ENTITY, BlockCategory.PATH, "path entity",
                ParamSlot.ofUuid("target"));
        stmt(BlockType.STOP_PATH, BlockCategory.PATH, "stop pathing");
        stmt(BlockType.PATH_SETTING, BlockCategory.PATH, "path setting",
                ParamSlot.ofEnum("key", PATH_SETTINGS, "horizontalMoveCost"),
                ParamSlot.ofString("value", "1.0"));
        stmt(BlockType.PATH_MOVE_TYPE, BlockCategory.PATH, "path move type",
                ParamSlot.ofEnum("type", MOVE_TYPES, "WALK"));
        bool(BlockType.IN_PATH, BlockCategory.PATH, "in path");
        bool(BlockType.PATH_REACHED, BlockCategory.PATH, "reached end");
        bool(BlockType.PATH_FAILED, BlockCategory.PATH, "failed path");

        stmt(BlockType.PLAY_SOUND, BlockCategory.VOICE, "play sound",
                ParamSlot.ofString("sound", "hello.wav"),
                ParamSlot.ofBool("loop", false));
        stmt(BlockType.STOP_SOUND, BlockCategory.VOICE, "stop sound");
        bool(BlockType.IS_SPEAKING, BlockCategory.VOICE, "is speaking");
        stmt(BlockType.BLUETOOTH, BlockCategory.VOICE, "bluetooth",
                ParamSlot.ofUuid("source"));
        stmt(BlockType.STOP_BLUETOOTH, BlockCategory.VOICE, "stop bluetooth");
        bool(BlockType.IS_BLUETOOTHED, BlockCategory.VOICE, "is bluetoothed");

        stmt(BlockType.SELECT_HOTBAR, BlockCategory.INVENTORY, "hotbar",
                ParamSlot.ofEnum("slot", HOTBAR_SLOTS, "1"));
        stmt(BlockType.OPEN_INVENTORY, BlockCategory.INVENTORY, "open inventory");
        stmt(BlockType.CLOSE_SCREEN, BlockCategory.INVENTORY, "close screen");
        stmt(BlockType.HANDEDNESS, BlockCategory.LOOK, "handedness",
                ParamSlot.ofEnum("side", HANDEDNESS, "right"));
        stmt(BlockType.INV_CLICK, BlockCategory.INVENTORY, "menu click",
                ParamSlot.ofEnum("menu", MENUS, "inventory"),
                ParamSlot.ofEnum("mode", CLICK_MODES, "click"),
                ParamSlot.ofInt("slot", 0));
        stmt(BlockType.INV_SWAP_HOTBAR, BlockCategory.INVENTORY, "swap slot",
                ParamSlot.ofEnum("menu", MENUS, "inventory"),
                ParamSlot.ofInt("slot", 0),
                ParamSlot.ofEnum("with", SWAP_TARGETS, "1"));
        stmt(BlockType.INV_HELD_THROW, BlockCategory.INVENTORY, "throw held",
                ParamSlot.ofEnum("menu", MENUS, "inventory"));
        stmt(BlockType.INV_HELD_DRAG, BlockCategory.INVENTORY, "drag held",
                ParamSlot.ofEnum("menu", MENUS, "inventory"),
                ParamSlot.ofEnum("button", DRAG_BUTTONS, "left"),
                ParamSlot.ofString("slots", "1,2,3"));
        stmt(BlockType.QUICK_LOOT, BlockCategory.INVENTORY, "quick loot",
                ParamSlot.ofEnum("from", LOOT_SOURCES, "container"),
                ParamSlot.ofInt("slot", 0));
        stmt(BlockType.RECIPE_BOOK, BlockCategory.INVENTORY, "place recipe",
                ParamSlot.ofItem("item", "minecraft:crafting_table"),
                ParamSlot.ofEnum("mode", RECIPE_MODES, "single"));
        reporter(BlockType.INVENTORY_OPEN, BlockCategory.INVENTORY, "menu open");
        reporter(BlockType.CONTAINER_SIZE, BlockCategory.INVENTORY, "container size");
        stmt(BlockType.TRADE_SELECT, BlockCategory.INVENTORY, "trade select",
                ParamSlot.ofInt("index", 1));
        stmt(BlockType.TRADE_RESTOCK, BlockCategory.INVENTORY, "trade restock");
        reporter(BlockType.TRADE_CHECK, BlockCategory.INVENTORY, "trade check",
                ParamSlot.ofInt("index", 0));

        register(new BlockDef(BlockType.IF, BlockCategory.CONTROL, BlockShape.STATEMENT, "if",
                2, List.of(ParamSlot.ofBool("condition", false))));
        register(new BlockDef(BlockType.ELSE_IF, BlockCategory.CONTROL, BlockShape.STATEMENT, "else (if)",
                2, List.of(ParamSlot.ofBool("condition", true))));
        register(new BlockDef(BlockType.FOR, BlockCategory.CONTROL, BlockShape.STATEMENT, "for",
                1, List.of(ParamSlot.ofInt("count", 10))));
        register(new BlockDef(BlockType.WHILE, BlockCategory.CONTROL, BlockShape.STATEMENT, "while",
                1, List.of(ParamSlot.ofBool("condition", true))));
        reporter(BlockType.LOOP_ITER, BlockCategory.CONTROL, "i");
        register(new BlockDef(BlockType.BLOCK_END, BlockCategory.CONTROL, BlockShape.C_END, "end",
                1, List.of()));
        register(new BlockDef(BlockType.BREAK, BlockCategory.CONTROL, BlockShape.STATEMENT, "break",
                0, List.of(ParamSlot.ofInt("count", 1))));
        stmt(BlockType.WAIT, BlockCategory.CONTROL, "wait",
                ParamSlot.ofInt("ticks", 20));
        stmt(BlockType.WAIT_UNTIL, BlockCategory.CONTROL, "wait until",
                ParamSlot.ofBool("condition", false));
        register(new BlockDef(BlockType.STOP_SCRIPT, BlockCategory.EVENT, BlockShape.STATEMENT, "stop",
                0, List.of()));
        stmt(BlockType.PAUSE_SCRIPT, BlockCategory.EVENT, "pause");
        register(new BlockDef(BlockType.SET_SCRIPT, BlockCategory.EVENT, BlockShape.STATEMENT, "run script",
                0, List.of(ParamSlot.ofString("script", ""))));

        stmt(BlockType.SET_VAR, BlockCategory.VARIABLE, "set",
                ParamSlot.ofVarRef("name", ""),
                ParamSlot.ofDouble("value", 0.0));
        stmt(BlockType.CHANGE_VAR, BlockCategory.VARIABLE, "change",
                ParamSlot.ofVarRef("name", ""),
                ParamSlot.ofDouble("delta", 1.0));
        reporter(BlockType.READ_VAR, BlockCategory.VARIABLE, "var",
                ParamSlot.ofVarRef("name", ""));

        reporter(BlockType.SCOREBOARD, BlockCategory.SENSOR, "scoreboard",
                ParamSlot.ofString("objective", "score"),
                new ParamSlot("target", ParamType.UUID, "@s"));
        bool(BlockType.HAS_TAG, BlockCategory.SENSOR, "has tag",
                new ParamSlot("target", ParamType.UUID, "@s"),
                ParamSlot.ofString("tag", "tag"));
        reporter(BlockType.HEALTH, BlockCategory.SENSOR, "health");
        reporter(BlockType.POSITION, BlockCategory.SENSOR, "position");
        reporter(BlockType.YAW, BlockCategory.SENSOR, "",
                ParamSlot.ofEnum("axis", AXES, "yaw"));
        reporter(BlockType.BLOCK_AT, BlockCategory.SENSOR, "block at",
                ParamSlot.ofPos("position"));
        bool(BlockType.IS_TOUCHING_BLOCK, BlockCategory.SENSOR, "is touching block",
                ParamSlot.ofString("block", "minecraft:water"));
        reporter(BlockType.DISTANCE_TO, BlockCategory.SENSOR, "distance from",
                ParamSlot.ofEnum("fromShape", DISTANCE_SHAPES, "position"),
                ParamSlot.ofEnum("toShape", DISTANCE_SHAPES, "position"),
                ParamSlot.ofUuid("target"),
                ParamSlot.ofEnum("mode", DISTANCE_MODES, "normal"));
        reporter(BlockType.TIME_OF_DAY, BlockCategory.SENSOR, "time of day");

        bool(BlockType.EVERY_X_TICKS, BlockCategory.SENSOR, "every",
                ParamSlot.ofInt("ticks", 1));
        bool(BlockType.ON_DAMAGE, BlockCategory.SENSOR, "on damage");
        reporter(BlockType.ATTACK_COOLDOWN, BlockCategory.SENSOR, "attack cooldown",
                ParamSlot.ofEnum("kind", COOLDOWN_KINDS, "fraction"));
        reporter(BlockType.HURT_TIME, BlockCategory.SENSOR, "hurt time");
        reporter(BlockType.PING, BlockCategory.SENSOR, "ping");

        reporter(BlockType.ITEM_IN_SLOT, BlockCategory.INVENTORY, "inventorySlot",
                ParamSlot.ofInt("slot", 0));
        reporter(BlockType.EQUIPMENT, BlockCategory.INVENTORY, "equipment",
                new ParamSlot("target", ParamType.UUID, "@s"),
                ParamSlot.ofEnum("slot", EQUIP_SLOTS, "weapon.mainhand"));
        reporter(BlockType.GET_COUNT, BlockCategory.INVENTORY, "get",
                ParamSlot.ofEnum("kind", COUNT_KINDS, "count"),
                ParamSlot.ofEnum("slot", ItemSlots.NAMES, "weapon.mainhand"));
        reporter(BlockType.MAX_COUNT, BlockCategory.INVENTORY, "max",
                ParamSlot.ofEnum("kind", COUNT_KINDS, "count"),
                ParamSlot.ofItem("item", "minecraft:diamond_sword"));
        reporter(BlockType.COMMAND_RESULT, BlockCategory.SENSOR, "command result",
                ParamSlot.ofString("command", "data get entity @s Health"));

        reporter(BlockType.NUM_CALC, BlockCategory.DATA_MANIPULATION, "num calc",
                ParamSlot.ofString("expression", "0"));
        reporter(BlockType.STRING_CALC, BlockCategory.DATA_MANIPULATION, "String calc",
                ParamSlot.ofString("expression", "\"\""));
        bool(BlockType.BOOL_CALC, BlockCategory.DATA_MANIPULATION, "bool calc",
                ParamSlot.ofString("expression", "true"));
        reporter(BlockType.POS_CALC, BlockCategory.DATA_MANIPULATION, "Pos calc",
                ParamSlot.ofString("expression", "pos(0,0,0)"));
        reporter(BlockType.DIR_CALC, BlockCategory.DATA_MANIPULATION, "Dir calc",
                ParamSlot.ofString("expression", "dir(0,0)"));
        reporter(BlockType.TERNARY, BlockCategory.OPERATOR, "if",
                ParamSlot.ofBool("condition", false),
                ParamSlot.ofInt("trueValue", 0),
                ParamSlot.ofInt("falseValue", 0));
        bool(BlockType.COMPARE, BlockCategory.OPERATOR, "compare",
                ParamSlot.ofDouble("a", 0.0),
                ParamSlot.ofEnum("op", COMPARATORS, "<"),
                ParamSlot.ofDouble("b", 0.0));
        bool(BlockType.EQUALITY, BlockCategory.OPERATOR, "equals",
                ParamSlot.ofDouble("a", 0.0),
                ParamSlot.ofEnum("op", EQUALITY_OPS, "="),
                ParamSlot.ofDouble("b", 0.0));
        bool(BlockType.LOGIC, BlockCategory.OPERATOR, "logic",
                ParamSlot.ofBool("a", false),
                ParamSlot.ofEnum("op", LOGIC_OPS, "and"),
                ParamSlot.ofBool("b", false));
        bool(BlockType.NOT, BlockCategory.OPERATOR, "not",
                ParamSlot.ofBool("a", false));
        bool(BlockType.CONTAINS, BlockCategory.OPERATOR, "contains",
                ParamSlot.ofString("a", ""), ParamSlot.ofString("b", ""),
                ParamSlot.ofBool("checkCase", false));
        bool(BlockType.AND, BlockCategory.OPERATOR, "and",
                ParamSlot.ofBool("a", false), ParamSlot.ofBool("b", false));
        bool(BlockType.OR, BlockCategory.OPERATOR, "or",
                ParamSlot.ofBool("a", false), ParamSlot.ofBool("b", false));
        reporter(BlockType.RANDOM_INT, BlockCategory.OPERATOR, "random int",
                ParamSlot.ofInt("min", 0), ParamSlot.ofInt("max", 10));
        reporter(BlockType.RANDOM_DOUBLE, BlockCategory.OPERATOR, "random double",
                ParamSlot.ofDouble("min", 0.0), ParamSlot.ofDouble("max", 1.0));

        reporter(BlockType.VEC3, BlockCategory.DATA_MANIPULATION, "make position",
                ParamSlot.ofDouble("x", 0.0), ParamSlot.ofDouble("y", 0.0), ParamSlot.ofDouble("z", 0.0));
        reporter(BlockType.ROT, BlockCategory.DATA_MANIPULATION, "make direction",
                ParamSlot.ofDouble("yaw", 0.0), ParamSlot.ofDouble("pitch", 0.0));
        reporter(BlockType.TO_STRING, BlockCategory.DATA_MANIPULATION, "toString",
                ParamSlot.ofString("value", ""));

        register(new BlockDef(BlockType.FUNC_DEFINE, BlockCategory.FUNCTIONS, BlockShape.HAT, "define",
                1, List.of(ParamSlot.ofEnum("name", List.of(), ""))));
        stmt(BlockType.FUNC_CALL, BlockCategory.FUNCTIONS, "call",
                ParamSlot.ofEnum("name", List.of(), ""));
        reporter(BlockType.FUNC_PARAM, BlockCategory.FUNCTIONS, "input",
                ParamSlot.ofString("func", ""), ParamSlot.ofInt("index", 0));
    }

    private static void start() {
        register(new BlockDef(BlockType.START, BlockCategory.EVENT, BlockShape.HAT, "start", 1, List.of()));
    }

    private static void stmt(BlockType type, BlockCategory cat, String label, ParamSlot... slots) {
        register(new BlockDef(type, cat, BlockShape.STATEMENT, label, 1, List.of(slots)));
    }

    private static void actionBlock(BlockType type, String label) {
        actionBlock(type, label, ACTION_MODES);
    }

    private static void actionBlock(BlockType type, String label, List<String> modes) {
        register(new BlockDef(type, BlockCategory.ACTION, BlockShape.STATEMENT, label, 1,
                List.of(
                        ParamSlot.ofEnum("mode", modes, "once"),
                        ParamSlot.ofInt("interval", 1),
                        ParamSlot.ofInt("ticks", 0)
                )));
    }

    private static void reporter(BlockType type, BlockCategory cat, String label, ParamSlot... slots) {
        register(new BlockDef(type, cat, BlockShape.REPORTER, label, 0, List.of(slots)));
    }

    private static void bool(BlockType type, BlockCategory cat, String label, ParamSlot... slots) {
        register(new BlockDef(type, cat, BlockShape.BOOLEAN, label, 0, List.of(slots)));
    }

    private static void register(BlockDef def) {
        DEFS.put(def.type(), def);
    }

    public static BlockDef get(BlockType type) {
        BlockDef def = DEFS.get(type);
        if (def == null) throw new IllegalArgumentException("No BlockDef for " + type);
        return def;
    }

    public static List<BlockDef> all() {
        return Collections.unmodifiableList(new ArrayList<>(DEFS.values()));
    }
}
