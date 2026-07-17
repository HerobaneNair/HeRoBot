package hero.bane.herobot.ai;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;

import java.util.UUID;

public final class SelectorValidation {
    private SelectorValidation() {}

    public static boolean isValidSingle(String expr) {
        if (expr == null) return false;
        String s = expr.trim();
        if (s.isEmpty()) return false;
        return syntaxOk(s) && isSingleTarget(s);
    }

    public static boolean isUuid(String expr) {
        if (expr == null) return false;
        try {
            UUID.fromString(expr.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean syntaxOk(String s) {
        if (!s.startsWith("@")) return true;
        try {
            new EntitySelectorParser(new StringReader(s), true).parse();
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isSingleTarget(String expr) {
        String t = expr.trim();
        if (!t.startsWith("@")) return true;
        if (t.length() < 2) return false;
        Integer limit = parseLimit(t);
        if (limit != null) return limit == 1;
        char base = t.charAt(1);
        return base == 'p' || base == 'r' || base == 's' || base == 'n';
    }

    private static Integer parseLimit(String t) {
        int b = t.indexOf('[');
        if (b < 0) return null;
        String opts = t.substring(b + 1).replace("]", "");
        for (String part : opts.split(",")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            if (!p.substring(0, eq).trim().equals("limit")) continue;
            try {
                return Integer.parseInt(p.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
