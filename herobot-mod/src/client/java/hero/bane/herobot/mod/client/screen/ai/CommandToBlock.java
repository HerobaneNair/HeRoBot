package hero.bane.herobot.mod.client.screen.ai;

import hero.bane.herobot.common.ai.block.BlockDefRegistry;
import hero.bane.herobot.common.ai.block.BlockType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class CommandToBlock {
    private CommandToBlock() {}

    public record Result(BlockType type, Map<String, Object> params) {}

    private static final Map<String, String> ACTION_NAMES = Map.of(
            "use", "USE",
            "swing", "SWING",
            "jump", "JUMP",
            "attack", "ATTACK",
            "drop", "DROP_ITEM",
            "dropStack", "DROP_STACK",
            "swapHands", "SWAP_HANDS"
    );

    private static final Map<String, BlockType> ACTION_BLOCKS = Map.of(
            "use", BlockType.USE,
            "swing", BlockType.SWING,
            "jump", BlockType.JUMP,
            "attack", BlockType.ATTACK,
            "drop", BlockType.DROP_ITEM,
            "dropStack", BlockType.DROP_ITEM,
            "swapHands", BlockType.SWAP_HANDS
    );

    private static final Pattern COORD = Pattern.compile("~|~?-?\\d+(\\.\\d+)?");

    public static Result parse(String input) {
        if (input == null) return null;
        String s = input.strip();
        if (s.startsWith("/")) s = s.substring(1);

        int i = wordEnd(s, 0);
        if (!s.substring(0, i).equals("player")) return null;
        i = skipSpaces(s, i);

        int targetStart = i;
        i = targetEnd(s, i);
        if (i < 0 || i == targetStart) return null;

        i = skipSpaces(s, i);
        if (i >= s.length()) return null;

        String[] head = s.substring(i).split("\\s+", 2);
        String sub = head[0];
        String rest = head.length == 2 ? head[1] : "";
        String[] args = rest.isEmpty() ? new String[0] : rest.split("\\s+");

        return switch (sub) {
            case "stop" -> args.length == 0 ? result(BlockType.STOP_ALL) : null;
            case "use", "swing", "jump", "attack", "drop", "dropStack", "swapHands" -> action(sub, args);
            case "pickBlock" -> pickBlock(args);
            case "move" -> move(args);
            case "look" -> look(args);
            case "sneak" -> toggle(BlockType.SNEAK, true, args);
            case "unsneak" -> toggle(BlockType.SNEAK, false, args);
            case "sprint" -> toggle(BlockType.SPRINT, true, args);
            case "unsprint" -> toggle(BlockType.SPRINT, false, args);
            case "autojump" -> autojump(args);
            case "hotbar" -> hotbar(args);
            case "place" -> place(args);
            case "mine", "break" -> breakBlock(args);
            case "path" -> path(args);
            case "msg" -> rest.isBlank() ? null : result(BlockType.SEND_MESSAGE, "message", rest);
            case "handedness" -> args.length == 1 && (args[0].equals("left") || args[0].equals("right"))
                    ? result(BlockType.HANDEDNESS, "side", args[0]) : null;
            case "inventory" -> menu("inventory", args);
            case "container" -> menu("container", args);
            case "trade" -> trade(args);
            case "ai" -> ai(args);
            default -> null;
        };
    }

    private static int wordEnd(String s, int i) {
        while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int targetEnd(String s, int i) {
        if (i >= s.length() || s.charAt(i) != '@') return wordEnd(s, i);
        i++;
        if (i < s.length()) i++;
        if (i < s.length() && s.charAt(i) == '[') {
            int close = s.indexOf(']', i);
            if (close < 0) return -1;
            i = close + 1;
        }
        return i;
    }

    private static Result pickBlock(String[] args) {
        if (args.length == 0) return result(BlockType.PICK_BLOCK, "data", "no data");
        if (args.length == 1 && args[0].equals("withData")) {
            return result(BlockType.PICK_BLOCK, "data", "with data");
        }
        return null;
    }

    private static Result action(String sub, String[] args) {
        if (args.length == 0) {
            return result(BlockType.STOP_ACTION, "action", ACTION_NAMES.get(sub));
        }
        BlockType type = ACTION_BLOCKS.get(sub);
        Map<String, Object> params = new LinkedHashMap<>();
        if (sub.equals("drop")) params.put("amount", "1");
        if (sub.equals("dropStack")) params.put("amount", "stack");
        switch (args[0]) {
            case "once" -> {
                if (args.length != 1) return null;
                params.put("mode", "once");
            }
            case "twice" -> {
                if (args.length != 1) return null;
                if (!sub.equals("attack") && !sub.equals("use")) return null;
                params.put("mode", "twice");
            }
            case "continuous" -> {
                if (args.length > 2) return null;
                params.put("mode", "continuous");
                if (args.length == 2) {
                    Integer ticks = positiveInt(args[1]);
                    if (ticks == null) return null;
                    params.put("ticks", ticks);
                }
            }
            case "interval" -> {
                if (args.length < 2 || args.length > 3) return null;
                Integer interval = positiveInt(args[1]);
                if (interval == null) return null;
                params.put("mode", "interval");
                params.put("interval", interval);
                if (args.length == 3) {
                    Integer ticks = positiveInt(args[2]);
                    if (ticks == null) return null;
                    params.put("ticks", ticks);
                }
            }
            default -> {
                return null;
            }
        }
        return new Result(type, params);
    }

    private static Result look(String[] args) {
        if (args.length == 0) return null;
        switch (args[0]) {
            case "north", "south", "east", "west", "up", "down" -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("direction", args[0]);
                if (!parseTicksOnly(args, params)) return null;
                return new Result(BlockType.LOOK_CARDINAL, params);
            }
            case "left", "right", "back" -> {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("dYaw", args[0].equals("left") ? -90.0 : args[0].equals("right") ? 90.0 : 180.0);
                params.put("dPitch", 0.0);
                if (!parseTicksOnly(args, params)) return null;
                return new Result(BlockType.TURN, params);
            }
            case "at" -> {
                if (args.length < 4) return null;
                for (int i = 1; i <= 3; i++) {
                    if (!COORD.matcher(args[i]).matches()) return null;
                }
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("position", args[1] + " " + args[2] + " " + args[3]);
                if (!parseLookTail(args, 4, params)) return null;
                return new Result(BlockType.LOOK_AT_POS, params);
            }
            case "direction" -> {
                if (args.length < 3) return null;
                for (int i = 1; i <= 2; i++) {
                    if (!COORD.matcher(args[i]).matches()) return null;
                }
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("direction", args[1] + " " + args[2]);
                if (!parseLookTail(args, 3, params)) return null;
                return new Result(BlockType.LOOK_DIRECTION, params);
            }
            case "upon" -> {
                if (args.length < 2 || args[1].isEmpty()) return null;
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("target", args[1]);
                int i = 2;
                if (i < args.length
                        && (args[i].equals("eyes") || args[i].equals("feet") || args[i].equals("closest"))) {
                    params.put("mode", args[i]);
                    i++;
                }
                if (!parseLookTail(args, i, params)) return null;
                return new Result(BlockType.LOOK_AT_ENTITY, params);
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean parseTicksOnly(String[] args, Map<String, Object> params) {
        if (1 == args.length) return true;
        if (args.length == 1 + 2 && args[1].equals("ticks")) {
            Integer ticks = positiveInt(args[1 + 1]);
            if (ticks == null) return false;
            params.put("ticks", ticks);
            return true;
        }
        return false;
    }

    private static boolean parseLookTail(String[] args, int i, Map<String, Object> params) {
        if (i < args.length && args[i].equals("offset")) {
            if (args.length < i + 3) return false;
            Double yaw = tryDouble(args[i + 1]);
            Double pitch = tryDouble(args[i + 2]);
            if (yaw == null || pitch == null || yaw < 0 || yaw > 360 || pitch < 0 || pitch > 180) return false;
            params.put("yawOffset", yaw);
            params.put("pitchOffset", pitch);
            i += 3;
        }
        if (i < args.length) {
            if (args.length != i + 2 || !args[i].equals("ticks")) return false;
            Integer ticks = positiveInt(args[i + 1]);
            if (ticks == null) return false;
            params.put("ticks", ticks);
            i += 2;
        }
        return i == args.length;
    }

    private static Double tryDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Result menu(String menu, String[] args) {
        if (args.length == 0) return null;
        boolean container = menu.equals("container");
        switch (args[0]) {
            case "open" -> {
                return !container && args.length == 1 ? result(BlockType.OPEN_INVENTORY) : null;
            }
            case "close", "leave" -> {
                return args.length == 1 ? result(BlockType.CLOSE_SCREEN) : null;
            }
            case "click", "rightClick", "shiftClick", "throw", "throwAll" -> {
                if (args.length != 2) return null;
                Integer slot = nonNegInt(args[1]);
                if (slot == null) return null;
                return result(BlockType.INV_CLICK, "menu", menu, "mode", args[0], "slot", slot);
            }
            case "keybindSwap" -> {
                if (args.length != 3) return null;
                Integer slot = nonNegInt(args[1]);
                Integer hotbar = positiveInt(args[2]);
                if (slot == null || hotbar == null || hotbar > 9) return null;
                return result(BlockType.INV_SWAP_HOTBAR, "menu", menu, "slot", slot, "with", args[2]);
            }
            case "swapToOffhand" -> {
                if (args.length != 2) return null;
                Integer slot = nonNegInt(args[1]);
                if (slot == null) return null;
                return result(BlockType.INV_SWAP_HOTBAR, "menu", menu, "slot", slot, "with", "offhand");
            }
            case "held" -> {
                if (args.length < 2) return null;
                switch (args[1]) {
                    case "throw" -> {
                        return args.length == 2 ? result(BlockType.INV_HELD_THROW, "menu", menu) : null;
                    }
                    case "drag" -> {
                        if (args.length < 3) return null;
                        String slots = String.join("", Arrays.copyOfRange(args, 2, args.length));
                        if (!slots.matches("[0-9,\\-]+")) return null;
                        return result(BlockType.INV_HELD_DRAG, "menu", menu, "button", "left", "slots", slots);
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case "quickLoot" -> {
                if (!container || args.length != 3) return null;
                String from = switch (args[1]) {
                    case "containerSlot" -> "container";
                    case "inventorySlot" -> "inventory";
                    default -> null;
                };
                Integer slot = nonNegInt(args[2]);
                if (from == null || slot == null) return null;
                return result(BlockType.QUICK_LOOT, "from", from, "slot", slot);
            }
            case "recipeBook", "shiftRecipeBook" -> {
                if (!container || args.length != 2 || args[1].isEmpty()) return null;
                return result(BlockType.RECIPE_BOOK, "item", args[1],
                        "mode", args[0].equals("shiftRecipeBook") ? "max" : "single");
            }
            default -> {
                return null;
            }
        }
    }

    private static Result trade(String[] args) {
        if (args.length == 0) return null;
        switch (args[0]) {
            case "select" -> {
                if (args.length != 2) return null;
                Integer index = positiveInt(args[1]);
                return index == null ? null : result(BlockType.TRADE_SELECT, "index", index);
            }
            case "restock" -> {
                return args.length == 1 ? result(BlockType.TRADE_RESTOCK) : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static Result ai(String[] args) {
        if (args.length == 0) return null;
        switch (args[0]) {
            case "clear" -> {
                return args.length == 1 ? result(BlockType.STOP_SCRIPT) : null;
            }
            case "set" -> {
                if (args.length != 3 || !args[2].equals("run")) return null;
                String file = unquote(args[1]);
                return file.isEmpty() ? null : result(BlockType.SET_SCRIPT, "script", file);
            }
            default -> {
                return null;
            }
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Integer nonNegInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Result move(String[] args) {
        if (args.length == 0) return result(BlockType.STOP_MOVEMENT);
        if (args.length != 1) return null;
        return switch (args[0]) {
            case "forward", "backward" -> result(BlockType.MOVE, "direction", args[0]);
            case "left", "right" -> result(BlockType.STRAFE, "direction", args[0]);
            default -> null;
        };
    }

    private static Result toggle(BlockType type, boolean value, String[] args) {
        return args.length == 0 ? result(type, "value", value) : null;
    }

    private static Result autojump(String[] args) {
        if (args.length == 0) return result(BlockType.ATTEMPT_AUTOJUMP);
        if (args.length != 1) return null;
        if (!args[0].equals("true") && !args[0].equals("false")) return null;
        return result(BlockType.AUTOJUMP, "value", args[0].equals("true"));
    }

    private static Result hotbar(String[] args) {
        if (args.length != 1) return null;
        Integer slot = positiveInt(args[0]);
        if (slot == null || slot > 9) return null;
        return result(BlockType.SELECT_HOTBAR, "slot", args[0]);
    }

    private static Result place(String[] args) {
        if (args.length < 3 || args.length > 5) return null;
        for (int i = 0; i < 3; i++) {
            if (!COORD.matcher(args[i]).matches()) return null;
        }
        int i = 3;
        String face = "any";
        if (i < args.length && !args[i].equals("force")) {
            if (!BlockDefRegistry.FACES.contains(args[i])) return null;
            face = args[i++];
        }
        boolean force = false;
        if (i < args.length) {
            if (!args[i].equals("force") || args.length != i + 1) return null;
            force = true;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("position", args[0] + " " + args[1] + " " + args[2]);
        params.put("face", face);
        params.put("force", force);
        return new Result(BlockType.PLACE_BLOCK, params);
    }

    private static Result breakBlock(String[] args) {
        if (args.length < 3 || args.length > 4) return null;
        for (int i = 0; i < 3; i++) {
            if (!COORD.matcher(args[i]).matches()) return null;
        }
        if (args.length == 4 && !args[3].equals("force")) return null;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("position", args[0] + " " + args[1] + " " + args[2]);
        params.put("force", args.length == 4);
        return new Result(BlockType.BREAK_BLOCK, params);
    }

    private static Result path(String[] args) {
        if (args.length == 0) return null;
        switch (args[0]) {
            case "stop" -> {
                return args.length == 1 ? result(BlockType.STOP_PATH) : null;
            }
            case "pos" -> {
                if (args.length != 4) return null;
                for (int i = 1; i <= 3; i++) {
                    if (!COORD.matcher(args[i]).matches()) return null;
                }
                return result(BlockType.GOTO_POS, "position", args[1] + " " + args[2] + " " + args[3]);
            }
            case "entity" -> {
                if (args.length < 2) return null;
                return result(BlockType.GOTO_ENTITY, "target",
                        String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "settings" -> {
                return pathSettings(args);
            }
            default -> {
                return null;
            }
        }
    }

    private static Result pathSettings(String[] args) {
        if (args.length < 2) return null;
        switch (args[1]) {
            case "moveType" -> {
                if (args.length != 3) return null;
                String type = switch (args[2]) {
                    case "walk" -> "WALK";
                    case "sprint" -> "SPRINT";
                    case "sprintjump" -> "SPRINT_JUMP";
                    default -> null;
                };
                return type == null ? null : result(BlockType.PATH_MOVE_TYPE, "type", type);
            }
            case "target", "node", "cost" -> {
                if (args.length != 4) return null;
                String key = settingKey(args[1], args[2]);
                if (key == null || !isDouble(args[3])) return null;
                return result(BlockType.PATH_SETTING, "key", key, "value", args[3]);
            }
            case "stopFollowing" -> {
                if (args.length != 3) return null;
                if (!args[2].equals("true") && !args[2].equals("false")) return null;
                return result(BlockType.PATH_SETTING, "key", "stopFollowing", "value", args[2]);
            }
            case "debug" -> {
                if (args.length != 3) return null;
                String value = switch (args[2]) {
                    case "true", "all" -> "true";
                    case "false", "none" -> "false";
                    default -> null;
                };
                return value == null ? null : result(BlockType.PATH_SETTING, "key", "debug", "value", value);
            }
            default -> {
                return null;
            }
        }
    }

    private static String settingKey(String group, String axis) {
        return switch (group + "." + axis) {
            case "target.horizontal" -> "maxHorizontalDistance";
            case "target.vertical" -> "maxVerticalDistance";
            case "node.horizontal" -> "nodeHorizontalDistance";
            case "node.vertical" -> "nodeVerticalDistance";
            case "cost.horizontal" -> "horizontalMoveCost";
            case "cost.vertical" -> "verticalMoveCost";
            case "cost.swim" -> "swimCostMultiplier";
            default -> null;
        };
    }

    private static boolean isDouble(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Result result(BlockType type, Object... kv) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            params.put((String) kv[i], kv[i + 1]);
        }
        return new Result(type, params);
    }

    private static Integer positiveInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v >= 1 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
