package hero.bane.herobot.mod.common.ai.expr;

import hero.bane.herobot.mod.common.ai.block.ParamType;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class BoolEval {
    private BoolEval() {}

    public static final String OPS_LEGEND =
            "==  !=  >  <  >=  <=  and/&&  or/||  xor";
    public static final String OPS_LEGEND_2 =
            "!  not()  {var}  Input1  \"text\"  randomint(1,3)";

    @FunctionalInterface
    public interface Expr {
        boolean eval(Function<String, Object> vars);
    }

    private static final Expr FALSE = vars -> false;
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Expr> CACHE = new ConcurrentHashMap<>();

    public static boolean eval(String expression, Function<String, Object> vars) {
        return compile(expression).eval(vars);
    }

    public static boolean isValid(String expression) {
        return isValid(expression, null);
    }

    public static boolean isValid(String expression, Function<String, ParamType> types) {
        if (expression == null || expression.isBlank()) return true;
        try {
            Parser p = new Parser(expression, true, types);
            p.orExpr();
            p.skipWs();
            return p.atEnd();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static Expr compile(String expression) {
        if (expression == null || expression.isBlank()) return FALSE;
        Expr cached = CACHE.get(expression);
        if (cached != null) return cached;
        Expr compiled;
        try {
            Parser p = new Parser(expression, true, null);
            Expr e = p.orExpr();
            p.skipWs();
            if (p.atEnd()) {
                compiled = e;
            } else {
                compiled = FALSE;
                ExprLog.badExpression(expression, "unexpected trailing input");
            }
        } catch (RuntimeException ex) {
            compiled = FALSE;
            ExprLog.badExpression(expression, ex.getMessage());
        }
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(expression, compiled);
        return compiled;
    }

    static boolean asBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return v != null;
    }

    static boolean compare(Object a, Object b, String op) {
        boolean eqOp = op.equals("==") || op.equals("=");
        boolean neOp = op.equals("!=");
        if (a instanceof String sa && b instanceof String sb) {
            if (eqOp) return sa.equals(sb);
            if (neOp) return !sa.equals(sb);
            return cmpNum(sa.length(), sb.length(), op);
        }
        Double na = numOrLen(a), nb = numOrLen(b);
        if (na != null && nb != null) {
            if (eqOp) return na.doubleValue() == nb.doubleValue();
            if (neOp) return na.doubleValue() != nb.doubleValue();
            return cmpNum(na, nb, op);
        }
        if (a instanceof Boolean || b instanceof Boolean) {
            boolean ba = asBool(a), bb = asBool(b);
            if (eqOp) return ba == bb;
            if (neOp) return ba != bb;
            return false;
        }
        boolean eq = valuesEqual(a, b);
        if (eqOp) return eq;
        if (neOp) return !eq;
        return false;
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof float[] fa && b instanceof float[] fb) return Arrays.equals(fa, fb);
        if (Objects.equals(a, b)) return true;
        return a != null && b != null && String.valueOf(a).equals(String.valueOf(b));
    }

    private static double refToDouble(Object raw, int idx) {
        if (idx >= 0) {
            if (raw instanceof float[] f) return idx < f.length ? f[idx] : 0;
            if (raw instanceof double[] d) return idx < d.length ? d[idx] : 0;
            return 0;
        }
        if (raw instanceof Boolean b) return b ? 1 : 0;
        Double d = numOrLen(raw);
        return d == null ? 0 : d;
    }

    private static Double numOrLen(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) return (double) s.length();
        return null;
    }

    private static boolean cmpNum(double a, double b, String op) {
        return switch (op) {
            case ">" -> a > b;
            case "<" -> a < b;
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            default -> false;
        };
    }

    private record Operand(Function<Function<String, Object>, Object> fn, ParamType type) {}

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

        private boolean matchWord(String w) {
            skipWs();
            if (!input.regionMatches(true, pos, w, 0, w.length())) return false;
            int end = pos + w.length();
            if (end < input.length()) {
                char c = input.charAt(end);
                if (Character.isLetterOrDigit(c) || c == '_') return false;
            }
            pos = end;
            return true;
        }

        private boolean matchSymbol(String sym) {
            skipWs();
            if (!lookahead(sym)) return false;
            pos += sym.length();
            return true;
        }

        Expr orExpr() {
            Expr v = xorExpr();
            while (matchWord("or") || matchSymbol("||")) {
                Expr l = v, r = xorExpr();
                v = vars -> l.eval(vars) || r.eval(vars);
            }
            return v;
        }

        Expr xorExpr() {
            Expr v = andExpr();
            while (matchWord("xor")) {
                Expr l = v, r = andExpr();
                v = vars -> l.eval(vars) ^ r.eval(vars);
            }
            return v;
        }

        Expr andExpr() {
            Expr v = unary();
            while (matchWord("and") || matchSymbol("&&")) {
                Expr l = v, r = unary();
                v = vars -> l.eval(vars) && r.eval(vars);
            }
            return v;
        }

        Expr unary() {
            skipWs();
            if (peek() == '!' && !lookahead("!=")) {
                pos++;
                Expr a = unary();
                return vars -> !a.eval(vars);
            }
            if (matchWord("not")) {
                skipWs();
                if (peek() != '(') throw new RuntimeException("not needs (...)");
                pos++;
                Expr a = orExpr();
                skipWs();
                if (peek() == ')') pos++;
                else if (strict) throw new RuntimeException("expected )");
                return vars -> !a.eval(vars);
            }
            return primary();
        }

        Expr primary() {
            skipWs();
            if (peek() == '(') {
                pos++;
                Expr v = orExpr();
                skipWs();
                if (peek() == ')') pos++;
                else if (strict) throw new RuntimeException("expected )");
                return v;
            }
            Operand a = operand();
            String op = cmpOp();
            if (op == null) {
                if (strict && types != null && a.type() != null && a.type() != ParamType.BOOLEAN) {
                    throw new RuntimeException("not a boolean");
                }
                return vars -> asBool(a.fn().apply(vars));
            }
            Operand b = operand();
            if (strict && types != null) checkComparable(a.type(), b.type(), op);
            Expr chain = vars -> compare(a.fn().apply(vars), b.fn().apply(vars), op);
            Operand prev = b;
            String nextOp;
            while ((nextOp = cmpOp()) != null) {
                Operand next = operand();
                if (strict && types != null) checkComparable(prev.type(), next.type(), nextOp);
                Operand cl = prev;
                String cop = nextOp;
                Expr link = vars -> compare(cl.fn().apply(vars), next.fn().apply(vars), cop);
                Expr acc = chain;
                chain = vars -> acc.eval(vars) && link.eval(vars);
                prev = next;
            }
            return chain;
        }

        private void checkComparable(ParamType a, ParamType b, String op) {
            if (a == null || b == null) return;
            boolean aFlex = isNumOrString(a), bFlex = isNumOrString(b);
            if (aFlex && bFlex) return;
            boolean eqOp = op.equals("==") || op.equals("=") || op.equals("!=");
            if (typesSame(a, b) && eqOp) return;
            throw new RuntimeException("cannot compare " + a + " " + op + " " + b);
        }

        private static boolean isNumOrString(ParamType t) {
            return t == ParamType.INT || t == ParamType.DOUBLE || t == ParamType.STRING;
        }

        private static boolean typesSame(ParamType a, ParamType b) {
            if (a == b) return true;
            boolean aNum = a == ParamType.INT || a == ParamType.DOUBLE;
            boolean bNum = b == ParamType.INT || b == ParamType.DOUBLE;
            return aNum && bNum;
        }

        private String cmpOp() {
            skipWs();
            if (lookahead("==")) { pos += 2; return "=="; }
            if (lookahead("!=")) { pos += 2; return "!="; }
            if (lookahead(">=")) { pos += 2; return ">="; }
            if (lookahead("<=")) { pos += 2; return "<="; }
            if (peek() == '>') { pos++; return ">"; }
            if (peek() == '<') { pos++; return "<"; }
            if (peek() == '=') { pos++; return "="; }
            return null;
        }

        Operand operand() {
            skipWs();
            char c = peek();
            if (c == '"') return quoted();
            int start = pos;
            if (c == '-' || c == '.' || Character.isDigit(c) || functionCallAhead()) return numeric(start);
            if (c != '{' && !Character.isLetter(c) && c != '_') throw new RuntimeException("expected a value");
            Operand a = c == '{' ? varRef() : identifier();
            skipWs();
            return arithOpAhead() ? numeric(start) : a;
        }

        /** An identifier followed by '(' is a call, which only the numeric language knows how to parse. */
        private boolean functionCallAhead() {
            int p = pos;
            if (p >= input.length() || !(Character.isLetter(input.charAt(p)) || input.charAt(p) == '_')) return false;
            while (p < input.length() && (Character.isLetterOrDigit(input.charAt(p)) || input.charAt(p) == '_')) p++;
            while (p < input.length() && Character.isWhitespace(input.charAt(p))) p++;
            return p < input.length() && input.charAt(p) == '(';
        }

        private boolean arithOpAhead() {
            return switch (peek()) {
                case '+', '-', '*', '/', '%', '^' -> true;
                default -> false;
            };
        }

        private Operand numeric(int start) {
            ExprEval.Sub sub = ExprEval.parseNumeric(input, start, strict, types);
            pos = sub.end();
            ExprEval.Expr e = sub.expr();
            return new Operand(vars -> e.eval((name, idx) -> refToDouble(vars.apply(name), idx)),
                    ParamType.DOUBLE);
        }

        private Operand varRef() {
            pos++;
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != '}') pos++;
            String name = input.substring(start, pos).trim();
            if (peek() == '}') pos++;
            else if (strict) throw new RuntimeException("expected }");
            if (strict && name.isEmpty()) throw new RuntimeException("empty variable name");
            ParamType t = types == null ? null : types.apply(name);
            return new Operand(vars -> vars.apply(name), t);
        }

        private Operand quoted() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && input.charAt(pos) != '"') {
                char ch = input.charAt(pos);
                if (ch == '\\' && pos + 1 < input.length()) {
                    pos++;
                    sb.append(input.charAt(pos));
                } else {
                    sb.append(ch);
                }
                pos++;
            }
            if (peek() == '"') pos++;
            else if (strict) throw new RuntimeException("unclosed string");
            String val = sb.toString();
            return new Operand(vars -> val, ParamType.STRING);
        }

        private Operand identifier() {
            int start = pos;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isLetterOrDigit(ch) || ch == '_') pos++;
                else break;
            }
            String name = input.substring(start, pos);
            if (name.equalsIgnoreCase("true")) return new Operand(vars -> Boolean.TRUE, ParamType.BOOLEAN);
            if (name.equalsIgnoreCase("false")) return new Operand(vars -> Boolean.FALSE, ParamType.BOOLEAN);
            if (ExprEval.isInputRef(name)) {
                String canonical = ExprEval.canonicalInput(name);
                ParamType t = types == null ? null : types.apply(canonical);
                return new Operand(vars -> vars.apply(canonical), t);
            }
            throw new RuntimeException("unknown name: " + name);
        }
    }
}
