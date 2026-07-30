package hero.bane.herobot.ai.block;

import java.util.EnumMap;
import java.util.Map;

public final class BlockDescriptions {
    private static final Map<BlockType, String> DESCRIPTIONS = new EnumMap<>(BlockType.class);

    static {
        DESCRIPTIONS.put(BlockType.START, "When the ai is ran, this will automatically run the connected block tree");
        DESCRIPTIONS.put(BlockType.ON_TOGGLE, "Whenever the boolean value in this is set to true, it will run the connected block tree");
        DESCRIPTIONS.put(BlockType.ON_MESSAGE, "Runs the connected block tree whenever a chat message reaches the bot. Drag the message chip to read the message that was sent");
        DESCRIPTIONS.put(BlockType.MSG_TEXT, "The chat message that triggered this On Message block");

        DESCRIPTIONS.put(BlockType.MOVE, "Walk forwards or backwards; or stop walking f/b");
        DESCRIPTIONS.put(BlockType.STRAFE, "Strafe left or right; or stop strafing l/r");
        DESCRIPTIONS.put(BlockType.STOP_MOVEMENT, "Stop all horizontal movement");
        DESCRIPTIONS.put(BlockType.SNEAK, "Toggle sneaking/crouching");
        DESCRIPTIONS.put(BlockType.SPRINT, "Toggle sprinting (must also walk forwards to sprint forwards)");
        DESCRIPTIONS.put(BlockType.AUTOJUMP, "Toggle auto-jumping on/off (will automatically jump when necessary)");

        DESCRIPTIONS.put(BlockType.USE, "Simulate a right click (can be held or put in interval); twice fires both hands on the same tick");
        DESCRIPTIONS.put(BlockType.ATTACK, "Simulate a left click (can be held or put in interval); twice is meant for shield stunning");
        DESCRIPTIONS.put(BlockType.SWING, "Fake swing the arm (can be held or put in interval)");
        DESCRIPTIONS.put(BlockType.JUMP, "Jump... (can be held or put in interval)");
        DESCRIPTIONS.put(BlockType.ATTEMPT_AUTOJUMP, "Try to auto-jump right now, once, without turning auto-jump on");
        DESCRIPTIONS.put(BlockType.DROP_ITEM, "Drop 1 item or the entire stack from the held stack (can be held or put in interval)");
        DESCRIPTIONS.put(BlockType.SWAP_HANDS, "Swap the items in the main hand and off hand");
        DESCRIPTIONS.put(BlockType.PICK_BLOCK, "Pick block whatever it is looking at, once (with data copies the block's contents; creative only)");
        DESCRIPTIONS.put(BlockType.SEND_MESSAGE, "Send a chat message (or command by starting with /) as it. Click the ⁺ to enable sending the command as if the bot had op");
        DESCRIPTIONS.put(BlockType.STOP_ACTION, "Stop a specific ongoing action (i.e. using or attacking)");
        DESCRIPTIONS.put(BlockType.STOP_ALL, "Stop all ongoing actions at once");
        DESCRIPTIONS.put(BlockType.DOING_ACTION, "True while the target bot is driving the chosen input/action");

        DESCRIPTIONS.put(BlockType.LOOK_DIRECTION, "Look at an exact direction (yaw pitch)");
        DESCRIPTIONS.put(BlockType.LOOK_CARDINAL, "Look towards a cardinal direction (north, up, down, etc.)");
        DESCRIPTIONS.put(BlockType.LOOK_AT_POS, "Look at a specific block");
        DESCRIPTIONS.put(BlockType.LOOK_AT_ENTITY, "Look at an entity (at its eyes, closest point, or feet)");
        DESCRIPTIONS.put(BlockType.TURN, "Turn by a relative amount from where it's currently facing");
        DESCRIPTIONS.put(BlockType.HANDEDNESS, "Set which hand is dominant [in look cause it changes the look, get it]");

        DESCRIPTIONS.put(BlockType.GOTO_POS, "Pathfind to a position");
        DESCRIPTIONS.put(BlockType.GOTO_ENTITY, "Pathfind to and follow an entity");
        DESCRIPTIONS.put(BlockType.STOP_PATH, "Stop the bot's current pathing");
        DESCRIPTIONS.put(BlockType.PATH_SETTING, "Change a pathfinding setting (costs, distances, etc.)");
        DESCRIPTIONS.put(BlockType.PATH_MOVE_TYPE, "Set how the bot moves while pathing (walk|sprint|sprint-jump)");
        DESCRIPTIONS.put(BlockType.IN_PATH, "True while the bot is currently pathing to a position or entity");
        DESCRIPTIONS.put(BlockType.PATH_REACHED, "Flashes true when the bot finishes pathing by reaching its target");
        DESCRIPTIONS.put(BlockType.PATH_FAILED, "Flashes true when the bot's pathing fails (no path found, got stuck, or lost the target)");

        DESCRIPTIONS.put(BlockType.SELECT_HOTBAR, "Select a hotbar slot (1-9)");
        DESCRIPTIONS.put(BlockType.OPEN_INVENTORY, "Open the bot's own inventory screen");
        DESCRIPTIONS.put(BlockType.CLOSE_SCREEN, "Close the currently open screen (i.e. inventory or chest)");
        DESCRIPTIONS.put(BlockType.INV_CLICK, "Click a slot in the open inventory or container menu (left, right, shift, throw one, or throw all)");
        DESCRIPTIONS.put(BlockType.INV_SWAP_HOTBAR, "Swap a menu slot with a hotbar slot 1-9 or the offhand (like pressing 1-9 or F while hovering it)");
        DESCRIPTIONS.put(BlockType.INV_HELD_THROW, "Throw the item stack currently held on the cursor");
        DESCRIPTIONS.put(BlockType.INV_HELD_DRAG, "Drag the cursor-held stack across slots (accepts ranges and singles, like 1-9,11,13-19): left distributes the stack, right drops 1 item per slot");
        DESCRIPTIONS.put(BlockType.QUICK_LOOT, "Shift-click all matching items in the given slot, from the container or from the inventory side");
        DESCRIPTIONS.put(BlockType.RECIPE_BOOK, "Place a crafting recipe for the item into the open crafting table (single places one set, max fills as many as possible)");
        DESCRIPTIONS.put(BlockType.INVENTORY_OPEN, "Which menu the bot has open: 0 for none, 1 for its own inventory, 2 for a separate container (chest, furnace, etc.)");
        DESCRIPTIONS.put(BlockType.CONTAINER_SIZE, "Slot count of the open container menu, or 0 if none are open");
        DESCRIPTIONS.put(BlockType.TRADE_SELECT, "Select a villager trade by index (1 = first trade) and load its input slots with up to a full stack of each cost item");
        DESCRIPTIONS.put(BlockType.TRADE_RESTOCK, "Refill the selected trade's input slots back up to a full stack of each cost item (inputs are only refilled on select/restock, not on every trade)");
        DESCRIPTIONS.put(BlockType.TRADE_CHECK, "First input is trade index | -2 locked + can't afford; -1 locked + can afford; 0 unlocked + can't afford, 1 unlocked and can afford, -3 if invalid");

        DESCRIPTIONS.put(BlockType.IF, "Run the inner tree once if the condition is true");
        DESCRIPTIONS.put(BlockType.ELSE_IF, "Runs its inner tree if no earlier branch in the chain ran and its condition is true; leave the condition empty to act as a plain else");
        DESCRIPTIONS.put(BlockType.FOR, "Run the inner tree a set number of times");
        DESCRIPTIONS.put(BlockType.WHILE, "Run the inner tree over and over while the condition stays true");
        DESCRIPTIONS.put(BlockType.LOOP_ITER, "The current pass of its loop, starting from 0");
        DESCRIPTIONS.put(BlockType.BREAK, "Break out of the given number of enclosing control blocks (if; else-if; for; while) and continue after the outermost one's end block");
        DESCRIPTIONS.put(BlockType.BLOCK_END, "Marks the end of a loop/if. With one tree it repeats when that tree finishes; with several it waits for all of the connected ones to finish");
        DESCRIPTIONS.put(BlockType.WAIT, "Wait a number of ticks before continuing");
        DESCRIPTIONS.put(BlockType.WAIT_UNTIL, "Pause here until the condition becomes true");
        DESCRIPTIONS.put(BlockType.STOP_SCRIPT, "Stop the currently running AI script");
        DESCRIPTIONS.put(BlockType.PAUSE_SCRIPT, "Pause the AI script here and stop the bot moving. /player <bot> ai resume carries on from the next block");
        DESCRIPTIONS.put(BlockType.SET_SCRIPT, "Stop the current AI script, then load and run a different saved script by name");

        DESCRIPTIONS.put(BlockType.SET_VAR, "Set a variable to a value");
        DESCRIPTIONS.put(BlockType.CHANGE_VAR, "Change a variable by an amount (add to its current value)");
        DESCRIPTIONS.put(BlockType.READ_VAR, "Returns the current value of a variable (you can drag a variable from the right side as well)");

        DESCRIPTIONS.put(BlockType.SCOREBOARD, "Returns a target's score for the given objective");
        DESCRIPTIONS.put(BlockType.HAS_TAG, "True if the target entity has the given tag");
        DESCRIPTIONS.put(BlockType.HEALTH, "Returns the bot's current health");
        DESCRIPTIONS.put(BlockType.POSITION, "Returns the bot's current position");
        DESCRIPTIONS.put(BlockType.YAW, "Returns the bot's current yaw (horizontal look angle) or pitch (vertical); click the box to switch axis");
        DESCRIPTIONS.put(BlockType.BLOCK_AT, "Returns the block id at a position");
        DESCRIPTIONS.put(BlockType.IS_TOUCHING_BLOCK, "True if the bot's hitbox is touching the given block");
        DESCRIPTIONS.put(BlockType.DISTANCE_TO, "Returns the distance from the bot to a target, measuring each side by its position or its hitbox, in 3D, horizontal only, or vertical only");
        DESCRIPTIONS.put(BlockType.TIME_OF_DAY, "Returns the world time of day (0-24000)");
        DESCRIPTIONS.put(BlockType.EVERY_X_TICKS, "Returns once every x ticks");
        DESCRIPTIONS.put(BlockType.ON_DAMAGE, "True on the tick the bot takes damage");
        DESCRIPTIONS.put(BlockType.ATTACK_COOLDOWN, "Returns the attack cooldown charge as a fraction from 0 to 1, or the ticks left until it is charged. 1 (or 0 ticks) means not on cooldown");
        DESCRIPTIONS.put(BlockType.HURT_TIME, "Returns how many ticks are left in the current damage tick (0 when not hurt) [same as data get entity @s HurtTime]");
        DESCRIPTIONS.put(BlockType.PING, "Returns the ping in ms: a bot's stored ping value (set by /player <targets> ping <value>), or a real player's measured latency to the server");
        DESCRIPTIONS.put(BlockType.ITEM_IN_SLOT, "Returns the item in one of the bot's own slots by index 0-41 (0-8 hotbar, 9-35 inventory, 36-37 main/off hand, 38-41 armor head to feet)");
        DESCRIPTIONS.put(BlockType.EQUIPMENT, "Returns the item worn/held by any entity in a slot (main hand, off hand, or armor)");
        DESCRIPTIONS.put(BlockType.GET_COUNT, "Get the count or durability (remaining, 0 if none) of an item; index 0-41 (0-8 hotbar, 9-35 inventory, 36-37 main/off hand, 38-41 armor head to feet)");
        DESCRIPTIONS.put(BlockType.MAX_COUNT, "Get the max stack size or max durability (0 if the item has none) of an item type");
        DESCRIPTIONS.put(BlockType.COMMAND_RESULT, "Run a command as the bot and return its result as a decimal (i.e. data get entity @s Health)");

        DESCRIPTIONS.put(BlockType.NUM_CALC, "Calculate numbers within block (more directions inside block); add inputs with ⁺ and use them as Input#");
        DESCRIPTIONS.put(BlockType.STRING_CALC, "Calculate Strings within block (more directions inside block); add inputs with ⁺ and use them as Input#");
        DESCRIPTIONS.put(BlockType.BOOL_CALC, "Calculate booleans within block (more directions inside block); add inputs with ⁺ and use them as Input#");
        DESCRIPTIONS.put(BlockType.POS_CALC, "Calculate Positions within block (more directions inside block); add inputs with ⁺ and use them as Input#");
        DESCRIPTIONS.put(BlockType.DIR_CALC, "Calculate Directions within block (more directions inside block); add inputs with ⁺ and use them as Input#");
        DESCRIPTIONS.put(BlockType.TERNARY, "Pick between two values: returns the first if the condition is true, else the second (type locking). Basically a tiny if-else block");
        DESCRIPTIONS.put(BlockType.PLACE_BLOCK, "Place the held block at a position if a corner of the chosen face is in reach and sight (any tries every possible face); force skips that check and aims at the middle of the face instead");
        DESCRIPTIONS.put(BlockType.BREAK_BLOCK, "Mine the block at a position until it breaks, using the first face that is in reach and sight; force mines through blocks by aiming at the middle of the closest face instead. Does nothing if the block is unbreakable or out of reach, and keeps running while the script continues");
        DESCRIPTIONS.put(BlockType.COMPARE, "Compare two numbers; <, >, ≤, ≥");
        DESCRIPTIONS.put(BlockType.EQUALITY, "Check whether two values (type locking) are equal (=) or not (≠)");
        DESCRIPTIONS.put(BlockType.LOGIC, "Combine two booleans; click the operator to cycle through and, or, xor");
        DESCRIPTIONS.put(BlockType.AND, "True only if both boolean inputs are true");
        DESCRIPTIONS.put(BlockType.OR, "True if either boolean input is true");
        DESCRIPTIONS.put(BlockType.NOT, "True if boolean value is false");
        DESCRIPTIONS.put(BlockType.CONTAINS, "True if the first string contains the second. Set check case to true for a case-sensitive match");
        DESCRIPTIONS.put(BlockType.RANDOM_INT, "Returns a random whole number between two bounds");
        DESCRIPTIONS.put(BlockType.RANDOM_DOUBLE, "Returns a random decimal number between two bounds");
        DESCRIPTIONS.put(BlockType.VEC3, "Build a position from x, y and z values");
        DESCRIPTIONS.put(BlockType.ROT, "Build a rotation from yaw and pitch values");
        DESCRIPTIONS.put(BlockType.TO_STRING, "Convert a value into text");

        DESCRIPTIONS.put(BlockType.FUNC_DEFINE, "Define a function; add typed inputs with ⁺ and drag the chip under an input to use it in the body. Only one define block per function per script");
        DESCRIPTIONS.put(BlockType.FUNC_CALL, "Run a function, passing a value for each of its inputs");
        DESCRIPTIONS.put(BlockType.FUNC_PARAM, "The value passed into a function input for this call");
    }

    private BlockDescriptions() {}

    public static String get(BlockType type) {
        return DESCRIPTIONS.get(type);
    }
}
