package foundation.odin.validation;

import foundation.odin.types.OdinValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Recursive-descent evaluator over the invariant grammar:
 *   expression     = logic_or
 *   logic_or       = logic_and , { "||" , logic_and }
 *   logic_and      = equality , { "&&" , equality }
 *   equality       = comparison , { ( "==" | "!=" | "=" ) , comparison }
 *   comparison     = additive , { ( ">" | "<" | ">=" | "<=" ) , additive }
 *   additive       = multiplicative , { ( "+" | "-" ) , multiplicative }
 *   multiplicative = unary , { ( "*" | "/" | "%" ) , unary }
 *   unary          = [ "!" ] , primary
 *   primary        = path | number | string | "(" , expression , ")"
 *
 * An expression is parsed to an AST once and cached by its source string; each
 * document validation evaluates the cached AST against that document's values.
 */
public final class InvariantEvaluator {

    private InvariantEvaluator() {}

    /** Field resolver: returns the document value at a name, or null if absent. */
    public interface FieldResolver extends Function<String, OdinValue> {}

    /** Outcome of evaluating an invariant expression. */
    public record InvariantResult(Boolean value, boolean nullOperand) {}

    private static final double EPSILON = 1e-9;
    private static final List<String> MULTI_CHAR_OPS = List.of("==", "!=", ">=", "<=", "&&", "||");
    private static final String SINGLE_CHAR_OPS = "+-*/%><=!";

    private enum Kind { OP, NUMBER, STRING, IDENT, LPAREN, RPAREN }

    private record Token(Kind kind, String text) {}

    private static List<Token> tokenize(String expr) {
        var tokens = new ArrayList<Token>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == ' ' || c == '\t') { i++; continue; }
            if (c == '(') { tokens.add(new Token(Kind.LPAREN, "(")); i++; continue; }
            if (c == ')') { tokens.add(new Token(Kind.RPAREN, ")")); i++; continue; }
            if (c == '"' || c == '\'') {
                char quote = c;
                int j = i + 1;
                var sb = new StringBuilder();
                while (j < expr.length() && expr.charAt(j) != quote) { sb.append(expr.charAt(j)); j++; }
                if (j >= expr.length()) throw new IllegalArgumentException("Unterminated string literal");
                tokens.add(new Token(Kind.STRING, sb.toString()));
                i = j + 1;
                continue;
            }
            if (i + 1 < expr.length() && MULTI_CHAR_OPS.contains(expr.substring(i, i + 2))) {
                tokens.add(new Token(Kind.OP, expr.substring(i, i + 2)));
                i += 2;
                continue;
            }
            if (SINGLE_CHAR_OPS.indexOf(c) >= 0) {
                tokens.add(new Token(Kind.OP, String.valueOf(c)));
                i++;
                continue;
            }
            if (c >= '0' && c <= '9') {
                int j = i;
                while (j < expr.length() && (Character.isDigit(expr.charAt(j)) || expr.charAt(j) == '.')) j++;
                tokens.add(new Token(Kind.NUMBER, expr.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < expr.length()
                        && (Character.isLetterOrDigit(expr.charAt(j)) || expr.charAt(j) == '_' || expr.charAt(j) == '.')) j++;
                tokens.add(new Token(Kind.IDENT, expr.substring(i, j)));
                i = j;
                continue;
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' in invariant expression");
        }
        return tokens;
    }

    // ── AST ──

    private sealed interface Node
            permits NumberNode, StringNode, BoolNode, FieldNode, NotNode, LogicNode,
                    EqualityNode, CompareNode, AdditiveNode, MultiplicativeNode {}

    private record NumberNode(double value) implements Node {}
    private record StringNode(String value) implements Node {}
    private record BoolNode(boolean value) implements Node {}
    private record FieldNode(String name) implements Node {}
    private record NotNode(Node operand) implements Node {}
    private record LogicNode(String op, Node left, Node right) implements Node {}
    private record EqualityNode(String op, Node left, Node right) implements Node {}
    private record CompareNode(String op, Node left, Node right) implements Node {}
    private record AdditiveNode(String op, Node left, Node right) implements Node {}
    private record MultiplicativeNode(String op, Node left, Node right) implements Node {}

    // ── Parse (string → AST), cached per expression source ──

    private static final ConcurrentHashMap<String, Node> AST_CACHE = new ConcurrentHashMap<>();

    private static Node getAst(String expr) {
        return AST_CACHE.computeIfAbsent(expr, e -> new AstParser(tokenize(e)).run());
    }

    private static final class AstParser {
        private final List<Token> tokens;
        private int pos = 0;

        AstParser(List<Token> tokens) { this.tokens = tokens; }

        Node run() {
            Node ast = parseExpression();
            if (peek() != null) throw new IllegalArgumentException("Unexpected trailing tokens in invariant expression");
            return ast;
        }

        private Token peek() { return pos < tokens.size() ? tokens.get(pos) : null; }
        private Token next() { return pos < tokens.size() ? tokens.get(pos++) : null; }
        private String peekText() { Token t = peek(); return t != null ? t.text() : null; }

        private Node parseExpression() { return parseLogicOr(); }

        private Node parseLogicOr() {
            Node left = parseLogicAnd();
            while ("||".equals(peekText())) {
                next();
                Node right = parseLogicAnd();
                left = new LogicNode("||", left, right);
            }
            return left;
        }

        private Node parseLogicAnd() {
            Node left = parseEquality();
            while ("&&".equals(peekText())) {
                next();
                Node right = parseEquality();
                left = new LogicNode("&&", left, right);
            }
            return left;
        }

        private Node parseEquality() {
            Node left = parseComparison();
            while ("==".equals(peekText()) || "!=".equals(peekText()) || "=".equals(peekText())) {
                String op = next().text();
                Node right = parseComparison();
                left = new EqualityNode(op, left, right);
            }
            return left;
        }

        private Node parseComparison() {
            Node left = parseAdditive();
            while (isOneOf(peekText(), ">", "<", ">=", "<=")) {
                String op = next().text();
                Node right = parseAdditive();
                left = new CompareNode(op, left, right);
            }
            return left;
        }

        private Node parseAdditive() {
            Node left = parseMultiplicative();
            while ("+".equals(peekText()) || "-".equals(peekText())) {
                String op = next().text();
                Node right = parseMultiplicative();
                left = new AdditiveNode(op, left, right);
            }
            return left;
        }

        private Node parseMultiplicative() {
            Node left = parseUnary();
            while (isOneOf(peekText(), "*", "/", "%")) {
                String op = next().text();
                Node right = parseUnary();
                left = new MultiplicativeNode(op, left, right);
            }
            return left;
        }

        private Node parseUnary() {
            if ("!".equals(peekText())) {
                next();
                return new NotNode(parseUnary());
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            Token tok = next();
            if (tok == null) throw new IllegalArgumentException("Unexpected end of invariant expression");

            switch (tok.kind()) {
                case LPAREN -> {
                    Node inner = parseExpression();
                    Token close = next();
                    if (close == null || close.kind() != Kind.RPAREN) {
                        throw new IllegalArgumentException("Expected closing parenthesis");
                    }
                    return inner;
                }
                case NUMBER -> {
                    try {
                        return new NumberNode(Double.parseDouble(tok.text()));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid number '" + tok.text() + "'");
                    }
                }
                case STRING -> {
                    return new StringNode(tok.text());
                }
                case IDENT -> {
                    if ("true".equals(tok.text())) return new BoolNode(true);
                    if ("false".equals(tok.text())) return new BoolNode(false);
                    return new FieldNode(tok.text());
                }
                default -> throw new IllegalArgumentException("Unexpected token '" + tok.text() + "'");
            }
        }
    }

    // ── Evaluate (AST + resolver → result), per document ──

    /** Parse and evaluate an invariant expression against document field values. */
    public static InvariantResult evaluate(String expr, FieldResolver resolve) {
        Node ast = getAst(expr);
        var state = new EvalState(resolve);
        Object finalVal = state.eval(ast);

        Boolean result;
        if (state.nullOperand) {
            result = Boolean.FALSE;
        } else if (state.absentOperand) {
            result = null;
        } else {
            result = toBool(finalVal);
        }
        return new InvariantResult(result, state.nullOperand);
    }

    private static final class EvalState {
        private final FieldResolver resolve;
        private boolean absentOperand = false;
        private boolean nullOperand = false;

        EvalState(FieldResolver resolve) { this.resolve = resolve; }

        Object eval(Node node) {
            return switch (node) {
                case NumberNode n -> n.value();
                case StringNode s -> s.value();
                case BoolNode b -> b.value();
                case FieldNode f -> {
                    OdinValue value = resolve.apply(f.name());
                    if (value == null) {
                        absentOperand = true;
                        yield Double.NaN;
                    }
                    if (value instanceof OdinValue.OdinNull) {
                        nullOperand = true;
                        yield null;
                    }
                    yield operandFromValue(value);
                }
                case NotNode not -> !toBool(eval(not.operand()));
                case LogicNode logic -> {
                    Object left = eval(logic.left());
                    Object right = eval(logic.right());
                    yield "||".equals(logic.op()) ? toBool(left) || toBool(right) : toBool(left) && toBool(right);
                }
                case EqualityNode equality -> {
                    Object left = eval(equality.left());
                    Object right = eval(equality.right());
                    boolean eq = looseEquals(left, right);
                    yield "!=".equals(equality.op()) ? !eq : eq;
                }
                case CompareNode cmp -> compare(eval(cmp.left()), cmp.op(), eval(cmp.right()));
                case AdditiveNode add -> {
                    Double ln = toNum(eval(add.left()));
                    Double rn = toNum(eval(add.right()));
                    if (ln == null || rn == null) yield Double.NaN;
                    yield "+".equals(add.op()) ? ln + rn : ln - rn;
                }
                case MultiplicativeNode mul -> {
                    Double ln = toNum(eval(mul.left()));
                    Double rn = toNum(eval(mul.right()));
                    if (ln == null || rn == null) yield Double.NaN;
                    if ("*".equals(mul.op())) yield ln * rn;
                    if ("/".equals(mul.op())) yield rn == 0 ? Double.NaN : ln / rn;
                    yield rn == 0 ? Double.NaN : ln % rn;
                }
            };
        }
    }

    private static boolean isOneOf(String s, String... opts) {
        if (s == null) return false;
        for (String o : opts) if (o.equals(s)) return true;
        return false;
    }

    private static Object operandFromValue(OdinValue value) {
        if (value instanceof OdinValue.OdinInteger i) return (double) i.getValue();
        if (value instanceof OdinValue.OdinNumber n) return n.getValue();
        if (value instanceof OdinValue.OdinCurrency c) return c.getValue();
        if (value instanceof OdinValue.OdinPercent p) return p.getValue();
        if (value instanceof OdinValue.OdinString s) return s.getValue();
        if (value instanceof OdinValue.OdinBoolean b) return b.getValue();
        if (value instanceof OdinValue.OdinDate d) {
            Long ms = temporalMs(d.getRaw());
            return ms != null ? (double) ms : Double.NaN;
        }
        if (value instanceof OdinValue.OdinTimestamp ts) return (double) ts.getEpochMs();
        return Double.NaN;
    }

    private static Long temporalMs(String iso) {
        if (iso == null) return null;
        String s = iso.trim();
        try {
            if (s.length() == 10) {
                return java.time.LocalDate.parse(s)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return java.time.Instant.parse(s).toEpochMilli();
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private static Double toNum(Object v) {
        if (v instanceof Double d) return d.isNaN() ? null : d;
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        return null;
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Double d) return !d.isNaN() && d != 0;
        if (v instanceof String s) return !s.isEmpty();
        return false;
    }

    private static boolean looseEquals(Object a, Object b) {
        if (a instanceof Double da && b instanceof Double db) {
            return Math.abs(da - db) < EPSILON;
        }
        if (a instanceof String sa && b instanceof String sb) {
            return sa.equals(sb);
        }
        if (a instanceof Boolean ba && b instanceof Boolean bb) {
            return ba.equals(bb);
        }
        Double an = toNum(a);
        Double bn = toNum(b);
        if (an != null && bn != null) {
            return Math.abs(an - bn) < EPSILON;
        }
        return java.util.Objects.equals(a, b);
    }

    private static boolean compare(Object a, String op, Object b) {
        Double an = toNum(a);
        Double bn = toNum(b);
        if (an != null && bn != null) {
            return switch (op) {
                case ">" -> an > bn;
                case "<" -> an < bn;
                case ">=" -> an >= bn;
                case "<=" -> an <= bn;
                default -> false;
            };
        }
        if (a instanceof String sa && b instanceof String sb) {
            return switch (op) {
                case ">" -> sa.compareTo(sb) > 0;
                case "<" -> sa.compareTo(sb) < 0;
                case ">=" -> sa.compareTo(sb) >= 0;
                case "<=" -> sa.compareTo(sb) <= 0;
                default -> false;
            };
        }
        return false;
    }
}
