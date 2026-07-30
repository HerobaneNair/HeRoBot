package hero.bane.herobot.ai.block;

import java.util.EnumSet;
import java.util.Set;

public enum BlockType {
    START,
    ON_TOGGLE,
    ON_MESSAGE,
    MSG_TEXT,

    MOVE,
    STRAFE,
    STOP_MOVEMENT,
    SNEAK,
    SPRINT,
    AUTOJUMP,

    USE,
    SWING,
    JUMP,
    ATTEMPT_AUTOJUMP,
    ATTACK,
    DROP_ITEM,
    SWAP_HANDS,
    PICK_BLOCK,
    SEND_MESSAGE,
    STOP_ACTION,
    STOP_ALL,

    LOOK_DIRECTION,
    LOOK_CARDINAL,
    LOOK_AT_POS,
    LOOK_AT_ENTITY,
    TURN,

    GOTO_POS,
    GOTO_ENTITY,
    STOP_PATH,
    PATH_SETTING,
    PATH_MOVE_TYPE,
    IN_PATH,
    PATH_REACHED,
    PATH_FAILED,

    SELECT_HOTBAR,
    OPEN_INVENTORY,
    CLOSE_SCREEN,
    HANDEDNESS,
    INV_CLICK,
    INV_SWAP_HOTBAR,
    INV_HELD_THROW,
    INV_HELD_DRAG,
    QUICK_LOOT,
    RECIPE_BOOK,
    INVENTORY_OPEN,
    CONTAINER_SIZE,
    TRADE_SELECT,
    TRADE_RESTOCK,
    TRADE_CHECK,

    IF,
    ELSE_IF,
    FOR,
    WHILE,
    LOOP_ITER,
    BLOCK_END,
    BREAK,
    WAIT,
    WAIT_UNTIL,
    STOP_SCRIPT,
    PAUSE_SCRIPT,
    SET_SCRIPT,

    SET_VAR,
    CHANGE_VAR,
    READ_VAR,

    SCOREBOARD,
    HAS_TAG,
    HEALTH,
    POSITION,
    YAW,
    BLOCK_AT,
    IS_TOUCHING_BLOCK,
    DISTANCE_TO,
    TIME_OF_DAY,
    EVERY_X_TICKS,
    ON_DAMAGE,
    ATTACK_COOLDOWN,
    HURT_TIME,
    PING,
    ITEM_IN_SLOT,
    EQUIPMENT,
    GET_COUNT,
    MAX_COUNT,
    COMMAND_RESULT,

    COMPARE,
    EQUALITY,
    LOGIC,
    AND,
    OR,
    NOT,
    CONTAINS,
    RANDOM_INT,
    RANDOM_DOUBLE,

    BOOL_CALC,
    NUM_CALC,
    DIR_CALC,
    POS_CALC,
    STRING_CALC,
    VEC3,
    ROT,
    TO_STRING,

    TERNARY,
    PLACE_BLOCK,
    BREAK_BLOCK,
    DOING_ACTION,

    FUNC_DEFINE,
    FUNC_CALL,
    FUNC_PARAM;

    private static final Set<BlockType> OWNER_REFS =
            EnumSet.of(LOOP_ITER, FUNC_PARAM, MSG_TEXT);

    public boolean refsOwner() {
        return OWNER_REFS.contains(this);
    }
}
