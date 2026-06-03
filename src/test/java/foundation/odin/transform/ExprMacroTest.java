package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the %expr formula macro: precedence, associativity, unary handling,
 * operators, functions, bindings resolution, and compile errors. Formulas are
 * compiled at parse time and evaluated end-to-end through the transform engine.
 */
class ExprMacroTest {

    private static final String HEADER =
            "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\ntarget.format = \"json\"\n\n";

    // Evaluate a formula and return the resulting value field.
    private static DynValue eval(String formula) {
        var body = "{out}\nr = %expr \"" + formula + "\"";
        var transform = TransformParser.parse(HEADER + body);
        TransformResult result = TransformEngine.execute(transform, DynValue.ofObject(new java.util.ArrayList<>()));
        assertTrue(result.isSuccess(), () -> "errors: " + result.getErrors());
        return result.getOutput().get("out").get("r");
    }

    // Evaluate a formula whose variables resolve under the bindings object @.v.
    private static DynValue evalVars(String formula, java.util.Map<String, DynValue> vars) {
        var body = "{out}\nr = %expr \"" + formula + "\" @.v";
        var entries = new java.util.ArrayList<java.util.Map.Entry<String, DynValue>>();
        var vEntries = new java.util.ArrayList<java.util.Map.Entry<String, DynValue>>();
        for (var e : vars.entrySet()) vEntries.add(java.util.Map.entry(e.getKey(), e.getValue()));
        entries.add(java.util.Map.entry("v", DynValue.ofObject(vEntries)));
        var transform = TransformParser.parse(HEADER + body);
        TransformResult result = TransformEngine.execute(transform, DynValue.ofObject(entries));
        assertTrue(result.isSuccess(), () -> "errors: " + result.getErrors());
        return result.getOutput().get("out").get("r");
    }

    private static double num(DynValue v) {
        var d = v.asDouble();
        assertNotNull(d, "expected numeric, got " + v.getType());
        return d;
    }

    // =========================================================================
    // precedence and associativity
    // =========================================================================

    @Nested
    class PrecedenceAndAssociativity {
        @Test
        void multiplicationBindsTighterThanAddition() {
            assertEquals(14.0, num(eval("2 + 3 * 4")), 1e-10);
        }

        @Test
        void powerIsRightAssociative() {
            assertEquals(512.0, num(eval("2^3^2")), 1e-10);
        }

        @Test
        void powerBindsTighterThanUnaryMinus() {
            assertEquals(-4.0, num(eval("-2^2")), 1e-10);
        }

        @Test
        void parenthesesOverrideUnaryBinding() {
            assertEquals(4.0, num(eval("(-2)^2")), 1e-10);
        }

        @Test
        void nestedParentheses() {
            assertEquals(9.0, num(eval("((1 + 2) * 3)")), 1e-10);
        }

        @Test
        void stackedUnaryMinus() {
            assertEquals(2.0, num(eval("--2")), 1e-10);
        }
    }

    // =========================================================================
    // operators
    // =========================================================================

    @Nested
    class Operators {
        @Test
        void integerDivisionYieldsFraction() {
            assertEquals(0.5, num(eval("1 / 2")), 1e-10);
        }

        @Test
        void modulo() {
            assertEquals(1.0, num(eval("5 % 2")), 1e-10);
        }

        @Test
        void divisionByZeroYieldsNull() {
            assertTrue(eval("1 / 0").isNull());
        }
    }

    // =========================================================================
    // functions
    // =========================================================================

    @Nested
    class Functions {
        @Test
        void abs() {
            assertEquals(7.0, num(eval("abs(-7)")), 1e-10);
        }

        @Test
        void minAndMax() {
            assertEquals(1.0, num(eval("min(3, 5, 1)")), 1e-10);
            assertEquals(5.0, num(eval("max(3, 5, 1)")), 1e-10);
        }

        @Test
        void roundDefaultsToScaleZero() {
            assertEquals(4.0, num(eval("round(3.7)")), 1e-10);
        }

        @Test
        void roundExplicitScale() {
            assertEquals(3.14, num(eval("round(3.14159, 2)")), 1e-10);
        }

        @Test
        void pythagorasWithBindings() {
            var vars = java.util.Map.of("x", DynValue.ofInteger(3), "y", DynValue.ofInteger(4));
            assertEquals(5.0, num(evalVars("sqrt(x^2 + y^2)", vars)), 1e-10);
        }
    }

    // =========================================================================
    // explicit bindings
    // =========================================================================

    @Nested
    class Bindings {
        @Test
        void variablesResolveUnderBindingsObject() {
            var vars = java.util.Map.of(
                    "a", DynValue.ofInteger(2),
                    "b", DynValue.ofInteger(3),
                    "c", DynValue.ofInteger(4),
                    "x", DynValue.ofInteger(5));
            assertEquals(69.0, num(evalVars("a*x^2 + b*x + c", vars)), 1e-10);
        }

        @Test
        void variableWithoutBindingsIsCompileError() {
            assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("a + b", null));
        }
    }

    // =========================================================================
    // compile errors
    // =========================================================================

    @Nested
    class CompileErrors {
        @Test
        void unknownFunction() {
            assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("sin(x)", ".v"));
        }

        @Test
        void incompleteExpression() {
            assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("2 +", null));
        }

        @Test
        void unbalancedParenthesis() {
            assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("(1 + 2", null));
        }

        @Test
        void wrongFunctionArity() {
            assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("pow(2)", null));
        }

        @Test
        void carriesT015Code() {
            var ex = assertThrows(Expr.ExprSyntaxException.class, () -> Expr.compile("sin(x)", ".v"));
            assertTrue(ex.getMessage().contains("T015"), ex.getMessage());
        }
    }
}
