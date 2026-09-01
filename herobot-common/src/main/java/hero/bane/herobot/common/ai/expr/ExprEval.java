package hero.bane.herobot.common.ai.expr;

import hero.bane.herobot.common.ai.block.ParamType;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ExprEval {
    private ExprEval() {}

    public static final String OPS_LEGEND =
            "+  -  *  /  ^  sqrt  %  floor  ++  --";
    public static final String OPS_LEGEND_2 =
            "sin  cos  tan  atan  pi  rand  randomint(1,3)  {pos}[0]";

    @FunctionalInterface
    public interface IndexedVars {
        double get(String name, int index);
    }

    @FunctionalInterface
    public interface Expr {
        double eval(IndexedVars vars);
    }

    private static final Expr ZERO = vars -> 0;
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Expr> CACHE = new ConcurrentHashMap<>();

    public static double eval(String expression, IndexedVars vars) {
        return compile(expression).eval(vars);
    }

    public static boolean isInputRef(String name) {
        if (name == null || name.length() < 6) return false;
        if (!name.regionMatches(true, 0, "Input", 0, 5)) return false;
        for (int i = 5; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }

    public static String canonicalInput(String name) {
        return "Input" + name.substring(5);
    }

    public static boolean isValid(String expression) {
        return isValid(expression, null);
    }

    public static boolean isValid(String expression, Function<String, ParamType> types) {
        if (expression == null || expression.isBlank()) return true;
        try {
            Parser p = new Parser(expression, true, types);
            p.expr();
            p.skipWs();
            return p.atEnd();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static Expr compile(String expression) {
        if (expression == null || expression.isBlank()) return ZERO;
        Expr cached = CACHE.get(expression);
        if (cached != null) return cached;
        Expr compiled;
        try {
            Parser p = new Parser(expression, true, null);
            Expr e = p.expr();
            p.skipWs();
            if (p.atEnd()) {
                compiled = e;
            } else {
                compiled = ZERO;
                ExprLog.badExpression(expression, "unexpected trailing input");
            }
        } catch (RuntimeException ex) {
            compiled = ZERO;
            ExprLog.badExpression(expression, ex.getMessage());
        }
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(expression, compiled);
        return compiled;
    }

    static Sub parseNumeric(String s, int from, boolean strict, Function<String, ParamType> types) {
        Parser p = new Parser(s, strict, types);
        p.pos = from;
        Expr e = p.expr();
        return new Sub(e, p.pos);
    }

    record Sub(Expr expr, int end) {}

    private static final class Parser {
        private final String input;
        private final boolean strict;
        private final Function<String, ParamType> types;
        private int pos;

        Parser(String input, boolean strict, Function<String, ParamType> types) {
            this.input = input;
            this.strict = strict;
            this.types = types;
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

        private boolean lookahead(String tok) {
            return input.regionMatches(pos, tok, 0, tok.length());
        }

        Expr expr() {
            Expr v = term();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '+' && !lookahead("++")) {
                    pos++;
                    Expr l = v, r = term();
                    v = vars -> l.eval(vars) + r.eval(vars);
                } else if (c == '-' && !lookahead("--")) {
                    pos++;
                    Expr l = v, r = term();
                    v = vars -> l.eval(vars) - r.eval(vars);
                } else {
                    break;
                }
            }
            return v;
        }

        Expr term() {
            Expr v = power();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '*') {
                    pos++;
                    Expr l = v, r = power();
                    v = vars -> l.eval(vars) * r.eval(vars);
                } else if (c == '/') {
                    pos++;
                    Expr l = v, r = power();
                    v = vars -> {
                        double d = r.eval(vars);
                        return d == 0 ? 0 : l.eval(vars) / d;
                    };
                } else if (c == '%') {
                    pos++;
                    Expr l = v, r = power();
                    v = vars -> {
                        double d = r.eval(vars);
                        return d == 0 ? 0 : l.eval(vars) % d;
                    };
                } else {
                    break;
                }
            }
            return v;
        }

        Expr power() {
            Expr base = unary();
            skipWs();
            if (peek() == '^') {
                pos++;
                Expr e = power();
                return vars -> Math.pow(base.eval(vars), e.eval(vars));
            }
            return base;
        }

        Expr unary() {
            skipWs();
            if (peek() == '-' && !lookahead("--")) {
                pos++;
                Expr a = unary();
                return vars -> -a.eval(vars);
            }
            if (peek() == '+' && !lookahead("++")) {
                pos++;
                return unary();
            }
            return postfix();
        }

        Expr postfix() {
            Expr v = primary();
            while (true) {
                skipWs();
                if (lookahead("++")) {
                    pos += 2;
                    Expr a = v;
                    v = vars -> a.eval(vars) + 1;
                } else if (lookahead("--")) {
                    pos += 2;
                    Expr a = v;
                    v = vars -> a.eval(vars) - 1;
                } else {
                    break;
                }
            }
            return v;
        }

        Expr primary() {
            skipWs();
            char c = peek();
            if (c == '(') {
                pos++;
                Expr v = expr();
                skipWs();
                if (peek() == ')') pos++;
                else if (strict) throw new RuntimeException("expected )");
                return v;
            }
            if (c == '{') return variable();
            if (c == '.' || Character.isDigit(c)) return number();
            if (Character.isLetter(c) || c == '_') return identifier();
            throw new RuntimeException("unexpected '" + c + "'");
        }

        Expr variable() {
            pos++;
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != '}') pos++;
            String name = input.substring(start, pos).trim();
            if (peek() == '}') pos++;
            else if (strict) throw new RuntimeException("expected }");
            int idx = parseIndex();
            checkRef(name, idx);
            return vars -> vars.get(name, idx);
        }

        private int parseIndex() {
            skipWs();
            if (peek() != '[') return -1;
            pos++;
            skipWs();
            int start = pos;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            if (pos == start) throw new RuntimeException("expected index");
            int idx = Integer.parseInt(input.substring(start, pos));
            skipWs();
            if (peek() == ']') pos++;
            else if (strict) throw new RuntimeException("expected ]");
            return idx;
        }

        private void checkRef(String name, int idx) {
            if (!strict || types == null) return;
            ParamType t = types.apply(name);
            if (t == null) return;
            boolean vec = t == ParamType.POSITION || t == ParamType.ROTATION;
            if (idx < 0) {
                if (vec) throw new RuntimeException(name + " needs an index like [0]");
                return;
            }
            if (!vec) throw new RuntimeException(name + " can't be indexed");
            int max = t == ParamType.POSITION ? 2 : 1;
            if (idx > max) throw new RuntimeException("index out of range");
        }

        Expr number() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c) || c == '.') pos++;
                else break;
            }
            double val = Double.parseDouble(input.substring(start, pos));
            return vars -> val;
        }

        Expr identifier() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') pos++;
                else break;
            }
            String name = input.substring(start, pos);
            skipWs();
            if (peek() == '(') return callFunction(name);
            return constant(name);
        }

        Expr constant(String name) {
            if (isInputRef(name)) {
                String canonical = canonicalInput(name);
                int idx = parseIndex();
                checkRef(canonical, idx);
                return vars -> vars.get(canonical, idx);
            }
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "pi" -> vars -> Math.PI;
                case "e" -> vars -> Math.E;
                case "tau" -> vars -> Math.PI * 2;
                case "rand", "random" -> vars -> Math.random();
                default -> {
                    if (strict) throw new RuntimeException("unknown name: " + name);
                    yield ZERO;
                }
            };
        }

        Expr callFunction(String name) {
            pos++;
            Expr arg = expr();
            Expr arg2 = ZERO;
            skipWs();
            boolean twoArgs = false;
            if (peek() == ',') {
                pos++;
                arg2 = expr();
                twoArgs = true;
                skipWs();
            }
            if (peek() == ')') pos++;
            else if (strict) throw new RuntimeException("expected )");
            Expr b = arg2;
            boolean two = twoArgs;
            return switch (name) {
                case "sin" -> vars -> Math.sin(arg.eval(vars));
                case "cos" -> vars -> Math.cos(arg.eval(vars));
                case "tan" -> vars -> Math.tan(arg.eval(vars));
                case "asin", "arcsin" -> vars -> Math.asin(arg.eval(vars));
                case "acos", "arccos" -> vars -> Math.acos(arg.eval(vars));
                case "atan", "arctan" -> vars -> Math.atan(arg.eval(vars));
                case "sqrt" -> vars -> Math.sqrt(arg.eval(vars));
                case "floor" -> vars -> Math.floor(arg.eval(vars));
                case "ceil" -> vars -> Math.ceil(arg.eval(vars));
                case "abs" -> vars -> Math.abs(arg.eval(vars));
                case "pow" -> vars -> two ? Math.pow(arg.eval(vars), b.eval(vars)) : arg.eval(vars);
                case "rand", "random" -> vars -> two
                        ? arg.eval(vars) + Math.random() * (b.eval(vars) - arg.eval(vars))
                        : Math.random() * arg.eval(vars);
                case "randomint", "randint" -> vars -> {
                    double x = arg.eval(vars);
                    double y = two ? b.eval(vars) : 0;
                    long lo = (long) Math.floor(Math.min(x, y));
                    long hi = (long) Math.floor(Math.max(x, y));
                    return lo + (long) (Math.random() * (hi - lo + 1));
                };
                default -> {
                    if (strict) throw new RuntimeException("unknown function: " + name);
                    yield ZERO;
                }
            };
        }
    }
}
