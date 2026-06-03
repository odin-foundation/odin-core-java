package foundation.odin.transform;

import foundation.odin.types.OdinTransformTypes.FieldExpression;
import foundation.odin.types.OdinTransformTypes.VerbArg;
import foundation.odin.types.OdinTransformTypes.VerbCall;
import foundation.odin.types.OdinValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * %expr formula macro. Compiles an infix arithmetic formula string into a tree
 * of existing transform verbs at parse time. No runtime evaluator: the result is
 * an ordinary verb expression, so the arithmetic is performed by the deterministic
 * verbs (add, subtract, multiply, divide, mod, negate, pow) and a whitelist of
 * numeric functions. Variables resolve under an explicit bindings object passed as
 * the second argument: in %expr "a + b" @.vars, the name a reads @.vars.a.
 *
 * Precedence, high to low:
 *   1. parentheses, function call
 *   2. ^ power (right-associative)
 *   3. unary - / +   (binds looser than ^, so -2^2 = -(2^2) = -4; (-2)^2 = 4)
 *   4. * / %  (left-associative)
 *   5. + -    (left-associative)
 */
final class Expr {

    private Expr() {}

    static final class ExprSyntaxException extends RuntimeException {
        ExprSyntaxException(String message) {
            super("[T015] Invalid %expr formula: " + message);
        }
    }

    private static final Map<Character, String> BINARY_OP = Map.of(
            '+', "add", '-', "subtract", '*', "multiply", '/', "divide", '%', "mod");

    private record FnSpec(String verb, int min, int max) {}

    private static final Map<String, FnSpec> FUNCTIONS = Map.ofEntries(
            Map.entry("abs", new FnSpec("abs", 1, 1)),
            Map.entry("floor", new FnSpec("floor", 1, 1)),
            Map.entry("ceil", new FnSpec("ceil", 1, 1)),
            Map.entry("trunc", new FnSpec("trunc", 1, 1)),
            Map.entry("sqrt", new FnSpec("sqrt", 1, 1)),
            Map.entry("round", new FnSpec("round", 1, 2)),
            Map.entry("pow", new FnSpec("pow", 2, 2)),
            Map.entry("min", new FnSpec("minOf", 1, Integer.MAX_VALUE)),
            Map.entry("max", new FnSpec("maxOf", 1, Integer.MAX_VALUE)));

    // ── Tokenizer ──

    private enum Kind { NUM, IDENT, OP, LPAREN, RPAREN, COMMA }

    private record Token(Kind kind, String value, boolean isFloat) {}

    private static List<Token> tokenize(String src) {
        var tokens = new ArrayList<Token>();
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c >= '0' && c <= '9') {
                int j = i;
                boolean isFloat = false;
                while (j < n && Character.isDigit(src.charAt(j))) j++;
                if (j < n && src.charAt(j) == '.') {
                    isFloat = true; j++;
                    while (j < n && Character.isDigit(src.charAt(j))) j++;
                }
                if (j < n && (src.charAt(j) == 'e' || src.charAt(j) == 'E')) {
                    isFloat = true; j++;
                    if (j < n && (src.charAt(j) == '+' || src.charAt(j) == '-')) j++;
                    while (j < n && Character.isDigit(src.charAt(j))) j++;
                }
                tokens.add(new Token(Kind.NUM, src.substring(i, j), isFloat));
                i = j;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_' || src.charAt(j) == '.')) j++;
                tokens.add(new Token(Kind.IDENT, src.substring(i, j), false));
                i = j;
                continue;
            }
            if (c == '(') { tokens.add(new Token(Kind.LPAREN, "(", false)); i++; continue; }
            if (c == ')') { tokens.add(new Token(Kind.RPAREN, ")", false)); i++; continue; }
            if (c == ',') { tokens.add(new Token(Kind.COMMA, ",", false)); i++; continue; }
            if ("+-*/%^".indexOf(c) >= 0) { tokens.add(new Token(Kind.OP, String.valueOf(c), false)); i++; continue; }
            throw new ExprSyntaxException("unexpected character '" + c + "'");
        }
        return tokens;
    }

    // ── Node builders ──

    private static VerbArg literal(String text, boolean isFloat) {
        OdinValue v = isFloat
                ? OdinValue.ofNumber(Double.parseDouble(text))
                : OdinValue.ofInteger(Long.parseLong(text));
        return VerbArg.lit(v);
    }

    private static VerbArg verbNode(String verb, List<VerbArg> args) {
        var call = new VerbCall();
        call.setVerb(verb);
        call.setIsCustom(false);
        call.setArgs(args);
        return VerbArg.nestedCall(call);
    }

    // ── Recursive-descent parser ──

    private static final class P {
        final List<Token> tokens;
        final String bindingPath;
        int pos = 0;

        P(List<Token> tokens, String bindingPath) {
            this.tokens = tokens;
            this.bindingPath = bindingPath;
        }

        Token peek() { return pos < tokens.size() ? tokens.get(pos) : null; }
        Token next() { return pos < tokens.size() ? tokens.get(pos++) : null; }

        VerbArg parse() {
            if (tokens.isEmpty()) throw new ExprSyntaxException("empty formula");
            VerbArg expr = additive();
            if (pos < tokens.size()) throw new ExprSyntaxException("unexpected token '" + peek().value() + "'");
            return expr;
        }

        VerbArg additive() {
            VerbArg left = multiplicative();
            while (peek() != null && peek().kind() == Kind.OP
                    && (peek().value().equals("+") || peek().value().equals("-"))) {
                char op = next().value().charAt(0);
                VerbArg right = multiplicative();
                left = verbNode(BINARY_OP.get(op), List.of(left, right));
            }
            return left;
        }

        VerbArg multiplicative() {
            VerbArg left = unary();
            while (peek() != null && peek().kind() == Kind.OP
                    && (peek().value().equals("*") || peek().value().equals("/") || peek().value().equals("%"))) {
                char op = next().value().charAt(0);
                VerbArg right = unary();
                left = verbNode(BINARY_OP.get(op), List.of(left, right));
            }
            return left;
        }

        VerbArg unary() {
            Token t = peek();
            if (t != null && t.kind() == Kind.OP && (t.value().equals("-") || t.value().equals("+"))) {
                next();
                VerbArg operand = unary();
                return t.value().equals("-") ? verbNode("negate", List.of(operand)) : operand;
            }
            return power();
        }

        VerbArg power() {
            VerbArg base = primary();
            if (peek() != null && peek().kind() == Kind.OP && peek().value().equals("^")) {
                next();
                VerbArg exponent = unary();
                return verbNode("pow", List.of(base, exponent));
            }
            return base;
        }

        VerbArg primary() {
            Token t = next();
            if (t == null) throw new ExprSyntaxException("unexpected end of formula");
            if (t.kind() == Kind.NUM) return literal(t.value(), t.isFloat());
            if (t.kind() == Kind.LPAREN) {
                VerbArg inner = additive();
                Token close = next();
                if (close == null || close.kind() != Kind.RPAREN)
                    throw new ExprSyntaxException("missing closing parenthesis");
                return inner;
            }
            if (t.kind() == Kind.IDENT) {
                if (peek() != null && peek().kind() == Kind.LPAREN) return call(t.value());
                if (bindingPath == null)
                    throw new ExprSyntaxException("variable '" + t.value()
                            + "' requires a bindings object, e.g. %expr \"...\" @.vars");
                return VerbArg.ref(bindingPath + "." + t.value());
            }
            throw new ExprSyntaxException("unexpected token '" + t.value() + "'");
        }

        VerbArg call(String name) {
            FnSpec fn = FUNCTIONS.get(name);
            if (fn == null) throw new ExprSyntaxException("unknown function '" + name + "'");
            next(); // consume '('
            var args = new ArrayList<VerbArg>();
            if (peek() != null && peek().kind() != Kind.RPAREN) {
                args.add(additive());
                while (peek() != null && peek().kind() == Kind.COMMA) {
                    next();
                    args.add(additive());
                }
            }
            Token close = next();
            if (close == null || close.kind() != Kind.RPAREN)
                throw new ExprSyntaxException("missing ) after " + name + "(");
            if (args.size() < fn.min() || args.size() > fn.max()) {
                String bounds = fn.min() == fn.max() ? String.valueOf(fn.min()) : fn.min() + "-" + fn.max();
                throw new ExprSyntaxException(name + "() takes " + bounds + " arguments, got " + args.size());
            }
            if (name.equals("round") && args.size() == 1) {
                args.add(VerbArg.lit(OdinValue.ofInteger(0)));
            }
            return verbNode(fn.verb(), args);
        }
    }

    // Compile a formula into a field expression. Variables resolve under
    // bindingPath (the path of the bindings object, e.g. ".vars"); a formula that
    // uses a variable without a bindings object is an error.
    static FieldExpression compile(String formula, String bindingPath) {
        VerbArg root = new P(tokenize(formula), bindingPath).parse();
        return toFieldExpression(root);
    }

    // The parser yields VerbArg nodes; wrap the root for use as a field expression.
    private static FieldExpression toFieldExpression(VerbArg root) {
        return switch (root) {
            case VerbArg.VerbCallArg vc -> FieldExpression.transform(vc.getNestedCall());
            case VerbArg.ReferenceArg ref -> FieldExpression.copy(ref.getPath());
            case VerbArg.LiteralArg lit -> FieldExpression.literal(lit.getValue());
        };
    }
}
