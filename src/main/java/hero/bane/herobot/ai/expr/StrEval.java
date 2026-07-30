package hero.bane.herobot.ai.expr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class StrEval {
    private StrEval() {}

    public static final String OPS_LEGEND =
            "\"text\"  +  {var}  Input1  ' \\\\,\\\" '";
    public static final String OPS_LEGEND_2 =
            "[start:stop:step]  [::-1]  .strip()";

    @FunctionalInterface
    public interface Expr {
        String eval(Function<String, String> vars);
    }

    private static final Expr EMPTY = vars -> "";
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Expr> CACHE = new ConcurrentHashMap<>();

    public static String eval(String expression, Function<String, String> vars) {
        return compile(expression).eval(vars);
    }

    public static boolean isValid(String expression) {
        if (expression == null || expression.isBlank()) return true;
        try {
            Parser p = new Parser(expression, true);
            p.expr();
            p.skipWs();
            return p.atEnd();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static Expr compile(String expression) {
        if (expression == null || expression.isBlank()) return EMPTY;
        Expr cached = CACHE.get(expression);
        if (cached != null) return cached;
        Expr compiled;
        try {
            Parser p = new Parser(expression, false);
            Expr e = p.expr();
            p.skipWs();
            compiled = p.atEnd() ? e : EMPTY;
        } catch (RuntimeException ex) {
            compiled = EMPTY;
        }
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(expression, compiled);
        return compiled;
    }

    static String slice(String s, Integer start, Integer stop, Integer step) {
        int st = step == null ? 1 : step;
        if (st == 0) return "";
        int len = s.length();
        StringBuilder sb = new StringBuilder();
        if (st > 0) {
            int b = norm(start, 0, len, false);
            int e = norm(stop, len, len, false);
            for (int i = b; i < e; i += st) sb.append(s.charAt(i));
        } else {
            int b = norm(start, len - 1, len, true);
            int e = norm(stop, -1, len, true);
            for (int i = b; i > e; i += st) sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static int norm(Integer v, int def, int len, boolean negStep) {
        if (v == null) return def;
        int i = v;
        if (i < 0) i += len;
        if (negStep) {
            if (i < 0) return -1;
            if (i >= len) return len - 1;
        } else {
            if (i < 0) return 0;
            if (i > len) return len;
        }
        return i;
    }

    static String index(String s, int i) {
        if (i < 0) i += s.length();
        if (i < 0 || i >= s.length()) return "";
        return String.valueOf(s.charAt(i));
    }

    private static final class Parser {
        private final String input;
        private final boolean strict;
        private int pos;

        Parser(String input, boolean strict) {
            this.input = input;
            this.strict = strict;
        }

        boolean atEnd() {
            return pos >= input.length();
        }

        void skipWs() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        private char peek() {
            return pos < input.length() ? input.charAt(pos) : '\0';
        }

        Expr expr() {
            Expr v = term();
            while (true) {
                skipWs();
                if (peek() == '+') {
                    pos++;
                    Expr l = v, r = term();
                    v = vars -> l.eval(vars) + r.eval(vars);
                } else {
                    break;
                }
            }
            return v;
        }

        Expr term() {
            Expr v = atom();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '[') {
                    v = sliceOf(v);
                } else if (c == '.') {
                    v = methodOf(v);
                } else {
                    break;
                }
            }
            return v;
        }

        Expr methodOf(Expr base) {
            pos++;
            int start = pos;
            while (pos < input.length() && Character.isLetter(input.charAt(pos))) pos++;
            String name = input.substring(start, pos);
            skipWs();
            if (peek() == '(') pos++;
            else if (strict) throw new RuntimeException("expected ( after ." + name);
            skipWs();
            if (peek() == ')') pos++;
            else if (strict) throw new RuntimeException("expected ) after ." + name + "(");
            if (name.equals("strip")) {
                return vars -> base.eval(vars).strip();
            }
            if (strict) throw new RuntimeException("unknown method: " + name);
            return base;
        }

        Expr atom() {
            skipWs();
            char c = peek();
            if (c == '"') return quoted();
            if (c == '{') return varRef();
            if (Character.isLetter(c)) return inputRef();
            throw new RuntimeException("expected \"string\", {var} or Input#");
        }

        /**
         * Quoted text is taken literally - {@code "{var}"} is the four characters, not a lookup - so
         * the only escapes are {@code \"} for a quote and {@code \\} for a backslash. Every other
         * backslash stands for itself, leaving {@code "\{var}"} as written.
         */
        Expr quoted() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && input.charAt(pos) != '"') {
                char c = input.charAt(pos);
                char next = pos + 1 < input.length() ? input.charAt(pos + 1) : '\0';
                if (c == '\\' && (next == '"' || next == '\\')) {
                    pos++;
                    sb.append(next);
                } else {
                    sb.append(c);
                }
                pos++;
            }
            if (peek() == '"') pos++;
            else if (strict) throw new RuntimeException("unclosed string");
            String val = sb.toString();
            return vars -> val;
        }

        Expr varRef() {
            pos++;
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != '}') pos++;
            String name = input.substring(start, pos).trim();
            if (peek() == '}') pos++;
            else if (strict) throw new RuntimeException("expected }");
            if (strict && name.isEmpty()) throw new RuntimeException("empty variable name");
            return vars -> {
                String v = vars.apply(name);
                return v == null ? "" : v;
            };
        }

        Expr inputRef() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') pos++;
                else break;
            }
            String name = input.substring(start, pos);
            if (!ExprEval.isInputRef(name)) {
                throw new RuntimeException("unknown name: " + name);
            }
            String canonical = ExprEval.canonicalInput(name);
            return vars -> {
                String v = vars.apply(canonical);
                return v == null ? "" : v;
            };
        }

        Expr sliceOf(Expr base) {
            pos++;
            Integer start = optInt();
            skipWs();
            if (peek() == ']') {
                pos++;
                if (start == null) {
                    if (strict) throw new RuntimeException("empty index");
                    return base;
                }
                int idx = start;
                return vars -> index(base.eval(vars), idx);
            }
            if (peek() != ':') {
                if (strict) throw new RuntimeException("expected : or ] in slice");
                return base;
            }
            pos++;
            Integer stop = optInt();
            Integer c = null;
            skipWs();
            if (peek() == ':') {
                pos++;
                c = optInt();
                skipWs();
            }
            if (peek() == ']') pos++;
            else if (strict) throw new RuntimeException("expected ] in slice");
            Integer step = c;
            return vars -> slice(base.eval(vars), start, stop, step);
        }

        private Integer optInt() {
            skipWs();
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            if (pos == start || (pos == start + 1 && input.charAt(start) == '-')) {
                pos = start;
                return null;
            }
            return Integer.parseInt(input.substring(start, pos));
        }
    }
}
