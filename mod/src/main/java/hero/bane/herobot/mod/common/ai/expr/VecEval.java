package hero.bane.herobot.mod.common.ai.expr;

import hero.bane.herobot.mod.common.ai.block.ParamType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;

public final class VecEval {
    private VecEval() {}

    public static final String OPS_LEGEND =
            "pos(x,y,z)  dir(y,p)  +  -  *  /  %";
    public static final String OPS_LEGEND_2 =
            "climb(pos)  fall(pos)  floor(vector)  {var}  Input1  ~  ^";

    public interface World {
        boolean isAir(int x, int y, int z);
        int minY();
        int maxY();

        default double base(int axis, boolean rotation) {
            return 0;
        }
    }

    @FunctionalInterface
    interface Expr {
        double[] eval(Function<String, Object> vars, World world);
    }

    private static final class Mismatch extends RuntimeException {
        Mismatch() { super(null, null, false, false); }
    }

    private static final Mismatch MISMATCH = new Mismatch();
    private static final Expr ZERO = (vars, world) -> new double[]{0};
    private static final int CACHE_LIMIT = 256;
    private static final Map<String, Expr> CACHE = new ConcurrentHashMap<>();

    public static double[] eval(String expression, int requiredNumArgs,
                                Function<String, Object> vars, World world) {
        double[] v;
        try {
            v = compile(expression).eval(vars, world);
        } catch (RuntimeException ex) {
            v = null;
        }
        return v != null && v.length == requiredNumArgs ? v : new double[requiredNumArgs];
    }

    public static boolean isValid(String expression, int requiredNumArgs,
                                  Function<String, ParamType> types) {
        if (expression == null || expression.isBlank()) return true;
        try {
            Parser p = new Parser(expression, true, requiredNumArgs, types);
            Node n = p.expr();
            p.skipWs();
            if (!p.atEnd()) return false;
            return n.numArgs() == 0 || n.numArgs() == requiredNumArgs;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    static Expr compile(String expression) {
        if (expression == null || expression.isBlank()) return ZERO;
        Expr cached = CACHE.get(expression);
        if (cached != null) return cached;
        Expr compiled;
        try {
            Parser p = new Parser(expression, false, 0, null);
            Node n = p.expr();
            p.skipWs();
            compiled = p.atEnd() ? n.fn() : ZERO;
        } catch (RuntimeException ex) {
            compiled = ZERO;
        }
        if (CACHE.size() >= CACHE_LIMIT) CACHE.clear();
        CACHE.put(expression, compiled);
        return compiled;
    }

    static double[] coerce(Object v) {
        if (v instanceof double[] d) return d;
        if (v instanceof float[] f) {
            double[] out = new double[f.length];
            for (int i = 0; i < f.length; i++) out[i] = f[i];
            return out;
        }
        if (v instanceof Number n) return new double[]{n.doubleValue()};
        if (v instanceof Boolean b) return new double[]{b ? 1 : 0};
        return new double[]{0};
    }

    /** Local (^left ^up ^forwards) offset relative to a yaw/pitch facing, matching vanilla carrot coordinates. */
    static double[] localOffset(double yawDeg, double pitchDeg, double left, double up, double forwards) {
        double f = Math.cos(Math.toRadians(yawDeg + 90.0));
        double g = Math.sin(Math.toRadians(yawDeg + 90.0));
        double h = Math.cos(Math.toRadians(-pitchDeg));
        double i = Math.sin(Math.toRadians(-pitchDeg));
        double j = Math.cos(Math.toRadians(-pitchDeg + 90.0));
        double k = Math.sin(Math.toRadians(-pitchDeg + 90.0));
        double[] fwdBasis = {f * h, i, g * h};
        double[] upBasis = {f * j, k, g * j};
        double[] leftBasis = cross(fwdBasis, upBasis);
        leftBasis[0] = -leftBasis[0];
        leftBasis[1] = -leftBasis[1];
        leftBasis[2] = -leftBasis[2];
        return new double[]{
                fwdBasis[0] * forwards + upBasis[0] * up + leftBasis[0] * left,
                fwdBasis[1] * forwards + upBasis[1] * up + leftBasis[1] * left,
                fwdBasis[2] * forwards + upBasis[2] * up + leftBasis[2] * left
        };
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    static double[] applyOp(double[] a, double[] b, DoubleBinaryOperator op) {
        if (a.length == b.length) {
            double[] out = new double[a.length];
            for (int i = 0; i < out.length; i++) out[i] = op.applyAsDouble(a[i], b[i]);
            return out;
        }
        if (a.length == 1) {
            double[] out = new double[b.length];
            for (int i = 0; i < out.length; i++) out[i] = op.applyAsDouble(a[0], b[i]);
            return out;
        }
        if (b.length == 1) {
            double[] out = new double[a.length];
            for (int i = 0; i < out.length; i++) out[i] = op.applyAsDouble(a[i], b[0]);
            return out;
        }
        throw MISMATCH;
    }

    private record Node(Expr fn, int numArgs) {}

    private static final class Parser {
        private final String input;
        private final boolean strict;
        private final int requiredNumArgs;
        private final Function<String, ParamType> types;
        private int pos;

        Parser(String input, boolean strict, int requiredNumArgs, Function<String, ParamType> types) {
            this.input = input;
            this.strict = strict;
            this.requiredNumArgs = requiredNumArgs;
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

        Node expr() {
            Node v = term();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '+') {
                    pos++;
                    v = bin(v, term(), Double::sum);
                } else if (c == '-') {
                    pos++;
                    v = bin(v, term(), (x, y) -> x - y);
                } else {
                    break;
                }
            }
            return v;
        }

        Node term() {
            Node v = unary();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '*') {
                    pos++;
                    v = bin(v, unary(), (x, y) -> x * y);
                } else if (c == '/') {
                    pos++;
                    v = bin(v, unary(), (x, y) -> y == 0 ? 0 : x / y);
                } else if (c == '%') {
                    pos++;
                    v = bin(v, unary(), (x, y) -> y == 0 ? 0 : x % y);
                } else {
                    break;
                }
            }
            return v;
        }

        Node unary() {
            skipWs();
            if (peek() == '-') {
                pos++;
                return map(unary(), x -> -x);
            }
            if (peek() == '+') {
                pos++;
                return unary();
            }
            return primary();
        }

        Node primary() {
            skipWs();
            char c = peek();
            if (c == '(') {
                pos++;
                Node v = expr();
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

        Node variable() {
            pos++;
            int start = pos;
            while (pos < input.length() && input.charAt(pos) != '}') pos++;
            String name = input.substring(start, pos).trim();
            if (peek() == '}') pos++;
            else if (strict) throw new RuntimeException("expected }");
            return ref(name);
        }

        private Node ref(String name) {
            int numArgs = 0;
            if (types != null) {
                ParamType t = types.apply(name);
                if (t == ParamType.POSITION) numArgs = 3;
                else if (t == ParamType.ROTATION) numArgs = 2;
                else if (t == ParamType.INT || t == ParamType.DOUBLE) numArgs = 1;
                else if (t != null) throw new RuntimeException(name + " is not a number, position, or direction");
            }
            return new Node((vars, world) -> coerce(vars.apply(name)), numArgs);
        }

        Node number() {
            int start = pos;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isDigit(c) || c == '.') pos++;
                else break;
            }
            double val = Double.parseDouble(input.substring(start, pos));
            return new Node((vars, world) -> new double[]{val}, 1);
        }

        Node identifier() {
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

        Node constant(String name) {
            if (ExprEval.isInputRef(name)) return ref(ExprEval.canonicalInput(name));
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "pi" -> new Node((vars, world) -> new double[]{Math.PI}, 1);
                case "e" -> new Node((vars, world) -> new double[]{Math.E}, 1);
                case "tau" -> new Node((vars, world) -> new double[]{Math.PI * 2}, 1);
                case "rand", "random" -> new Node((vars, world) -> new double[]{Math.random()}, 1);
                default -> {
                    if (strict) throw new RuntimeException("unknown name: " + name);
                    yield new Node(ZERO, 1);
                }
            };
        }

        Node callFunction(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("pos")) return relConstruct(3);
            if (lower.equals("dir")) return relConstruct(2);
            List<Node> a = args();
            return switch (lower) {
                case "floor" -> perComponent(a, Math::floor);
                case "ceil" -> perComponent(a, Math::ceil);
                case "abs" -> perComponent(a, Math::abs);
                case "sqrt" -> perComponent(a, Math::sqrt);
                case "sin" -> perComponent(a, Math::sin);
                case "cos" -> perComponent(a, Math::cos);
                case "tan" -> perComponent(a, Math::tan);
                case "asin", "arcsin" -> perComponent(a, Math::asin);
                case "acos", "arccos" -> perComponent(a, Math::acos);
                case "atan", "arctan" -> perComponent(a, Math::atan);
                case "pow" -> {
                    if (strict && a.size() != 2) throw new RuntimeException("pow needs 2 arguments");
                    if (a.isEmpty()) yield new Node(ZERO, 1);
                    if (a.size() < 2) yield a.getFirst();
                    if (strict && (a.getFirst().numArgs() > 1 || a.get(1).numArgs() > 1)) {
                        throw new RuntimeException("pow arguments must be numbers");
                    }
                    yield bin(a.getFirst(), a.get(1), Math::pow);
                }
                case "climb" -> terrain(a, true);
                case "fall" -> terrain(a, false);
                default -> {
                    if (strict) throw new RuntimeException("unknown function: " + name);
                    yield new Node(ZERO, 1);
                }
            };
        }

        private List<Node> args() {
            pos++;
            List<Node> list = new ArrayList<>();
            skipWs();
            if (peek() == ')') {
                pos++;
                return list;
            }
            list.add(expr());
            skipWs();
            while (peek() == ',') {
                pos++;
                list.add(expr());
                skipWs();
            }
            if (peek() == ')') pos++;
            else if (strict) throw new RuntimeException("expected )");
            return list;
        }

        private Node bin(Node l, Node r, DoubleBinaryOperator op) {
            int numArgs = combineNumArgs(l.numArgs(), r.numArgs());
            Expr a = l.fn(), b = r.fn();
            return new Node((vars, world) -> applyOp(a.eval(vars, world), b.eval(vars, world), op), numArgs);
        }

        private int combineNumArgs(int a, int b) {
            if (a == b) return a;
            if (a == 0) return b == 1 ? 0 : b;
            if (b == 0) return a == 1 ? 0 : a;
            if (a == 1) return b;
            if (b == 1) return a;
            throw new RuntimeException("cannot mix a position with a direction");
        }

        private Node map(Node n, DoubleUnaryOperator op) {
            Expr f = n.fn();
            return new Node((vars, world) -> {
                double[] v = f.eval(vars, world);
                double[] out = new double[v.length];
                for (int i = 0; i < v.length; i++) out[i] = op.applyAsDouble(v[i]);
                return out;
            }, n.numArgs());
        }

        private Node relConstruct(int n) {
            boolean rotation = n == 2;
            pos++; // consume '('
            List<Expr> parts = new ArrayList<>();
            List<Double> offsets = new ArrayList<>(); // null entry = plain expression argument
            List<Double> carrots = new ArrayList<>(); // non-null entry = ^ local-space offset
            skipWs();
            if (peek() != ')') {
                parseRelArg(parts, offsets, carrots);
                skipWs();
                while (peek() == ',') {
                    pos++;
                    parseRelArg(parts, offsets, carrots);
                    skipWs();
                }
            }
            if (peek() == ')') pos++;
            else if (strict) throw new RuntimeException("expected )");
            if (strict && parts.size() != n) {
                throw new RuntimeException((n == 3 ? "pos" : "dir") + " needs " + n + " numbers");
            }
            int carrotCount = 0;
            for (Double c : carrots) if (c != null) carrotCount++;
            if (carrotCount > 0) {
                if (strict && n != 3) throw new RuntimeException("^ local coordinates only work in pos calc");
                if (strict && carrotCount != n) throw new RuntimeException("cannot mix ^ with ~ or absolute coordinates");
            }
            Expr[] fns = parts.toArray(new Expr[0]);
            Double[] offs = offsets.toArray(new Double[0]);
            Double[] cars = carrots.toArray(new Double[0]);
            boolean usecarrot = carrotCount == n && n == 3;
            return new Node((vars, world) -> {
                double[] out = new double[n];
                if (usecarrot) {
                    double left = cars.length > 0 && cars[0] != null ? cars[0] : 0;
                    double up = cars.length > 1 && cars[1] != null ? cars[1] : 0;
                    double forwards = cars.length > 2 && cars[2] != null ? cars[2] : 0;
                    if (world == null) return new double[]{left, up, forwards};
                    double yaw = world.base(0, true);
                    double pitch = world.base(1, true);
                    double[] local = localOffset(yaw, pitch, left, up, forwards);
                    out[0] = world.base(0, false) + local[0];
                    out[1] = world.base(1, false) + local[1];
                    out[2] = world.base(2, false) + local[2];
                    return out;
                }
                for (int i = 0; i < n && i < fns.length; i++) {
                    if (offs[i] != null) {
                        double basis = world == null ? 0 : world.base(i, rotation);
                        out[i] = basis + offs[i];
                    } else if (fns[i] != null) {
                        out[i] = fns[i].eval(vars, world)[0];
                    } else {
                        out[i] = 0;
                    }
                }
                return out;
            }, n);
        }

        private void parseRelArg(List<Expr> parts, List<Double> offsets, List<Double> carrots) {
            skipWs();
            if (peek() == '^') {
                pos++;
                double offset = 0;
                char c = peek();
                if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) {
                    int start = pos;
                    if (c == '-' || c == '+') pos++;
                    while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
                    try {
                        offset = Double.parseDouble(input.substring(start, pos));
                    } catch (NumberFormatException e) {
                        if (strict) throw new RuntimeException("bad ^ offset");
                    }
                }
                parts.add(null);
                offsets.add(null);
                carrots.add(offset);
            } else if (peek() == '~') {
                pos++;
                double offset = 0;
                char c = peek();
                if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) {
                    int start = pos;
                    if (c == '-' || c == '+') pos++;
                    while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) pos++;
                    try {
                        offset = Double.parseDouble(input.substring(start, pos));
                    } catch (NumberFormatException e) {
                        if (strict) throw new RuntimeException("bad ~ offset");
                    }
                }
                parts.add(null);
                offsets.add(offset);
                carrots.add(null);
            } else {
                Node arg = expr();
                if (strict && arg.numArgs() > 1) throw new RuntimeException("arguments must be numbers");
                parts.add(arg.fn());
                offsets.add(null);
                carrots.add(null);
            }
        }

        private Node perComponent(List<Node> a, DoubleUnaryOperator op) {
            if (strict && a.size() != 1) throw new RuntimeException("needs 1 argument");
            if (a.isEmpty()) return new Node(ZERO, 1);
            return map(a.getFirst(), op);
        }

        private Node terrain(List<Node> a, boolean up) {
            if (strict) {
                if (requiredNumArgs != 3) throw new RuntimeException((up ? "climb" : "fall") + " only works in pos calc");
                if (a.size() != 1) throw new RuntimeException("needs 1 argument");
                int numArgs = a.getFirst().numArgs();
                if (numArgs != 0 && numArgs != 3) throw new RuntimeException("argument must be a position");
            }
            if (a.isEmpty()) return new Node(ZERO, 1);
            Expr f = a.getFirst().fn();
            return new Node((vars, world) -> {
                double[] p = f.eval(vars, world);
                if (p.length != 3) throw MISMATCH;
                if (world == null) return p;
                int bx = (int) Math.floor(p[0]);
                int bz = (int) Math.floor(p[2]);
                int startY = (int) Math.floor(p[1]);
                int y = startY;
                if (up) {
                    if (world.isAir(bx, y, bz)) return p;
                    while (y < world.maxY() && !world.isAir(bx, y, bz)) y++;
                } else {
                    if (!world.isAir(bx, y, bz)) return p;
                    while (y - 1 >= world.minY() && world.isAir(bx, y - 1, bz)) y--;
                }
                return y == startY ? p : new double[]{p[0], y, p[2]};
            }, 3);
        }
    }
}
