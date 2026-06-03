package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the integer-theory numeric verbs (gcd, lcm, factorial) and the
 * dated cash-flow financial verbs (xnpv, xirr).
 */
class NumericNewVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue Arr(DynValue... items) {
        return DynValue.ofArray(new ArrayList<>(List.of(items)));
    }

    // =========================================================================
    // gcd
    // =========================================================================

    @Nested
    class Gcd {
        @Test
        void basic() {
            assertEquals(6L, invoke("gcd", I(12), I(18)).asInt64());
        }

        @Test
        void withZeroReturnsOther() {
            assertEquals(12L, invoke("gcd", I(0), I(12)).asInt64());
        }

        @Test
        void negativeUsesAbsoluteValue() {
            assertEquals(6L, invoke("gcd", I(-12), I(18)).asInt64());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("gcd", I(12)).isNull());
        }
    }

    // =========================================================================
    // lcm
    // =========================================================================

    @Nested
    class Lcm {
        @Test
        void basic() {
            assertEquals(12L, invoke("lcm", I(4), I(6)).asInt64());
        }

        @Test
        void withZeroYieldsZero() {
            assertEquals(0L, invoke("lcm", I(0), I(4)).asInt64());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("lcm", I(4)).isNull());
        }
    }

    // =========================================================================
    // factorial
    // =========================================================================

    @Nested
    class Factorial {
        @Test
        void five() {
            assertEquals(120L, invoke("factorial", I(5)).asInt64());
        }

        @Test
        void zeroIsOne() {
            assertEquals(1L, invoke("factorial", I(0)).asInt64());
        }

        @Test
        void maxEighteen() {
            assertEquals(6402373705728000L, invoke("factorial", I(18)).asInt64());
        }

        @Test
        void overNineteenYieldsNull() {
            assertTrue(invoke("factorial", I(19)).isNull());
        }

        @Test
        void negativeYieldsNull() {
            assertTrue(invoke("factorial", I(-1)).isNull());
        }
    }

    // =========================================================================
    // xnpv
    // =========================================================================

    @Nested
    class Xnpv {
        private DynValue amounts() {
            return Arr(F(-1000), F(110), F(110), F(110), F(1100));
        }

        private DynValue dates() {
            return Arr(S("2020-01-01"), S("2021-01-01"), S("2022-01-01"), S("2023-01-01"), S("2024-01-01"));
        }

        @Test
        void discountsDatedFlows() {
            var result = invoke("xnpv", F(0.09), amounts(), dates());
            var d = result.asDouble();
            assertNotNull(d);
            assertTrue(Math.abs(d - 57.460446077146344) < 1e-6, "xnpv=" + d);
        }

        @Test
        void mismatchedLengthsYieldNull() {
            var shortDates = Arr(S("2020-01-01"), S("2021-01-01"));
            assertTrue(invoke("xnpv", F(0.09), amounts(), shortDates).isNull());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("xnpv", F(0.09), amounts()).isNull());
        }
    }

    // =========================================================================
    // xirr
    // =========================================================================

    @Nested
    class Xirr {
        private DynValue amounts() {
            return Arr(F(-1000), F(110), F(110), F(110), F(1100));
        }

        private DynValue dates() {
            return Arr(S("2020-01-01"), S("2021-01-01"), S("2022-01-01"), S("2023-01-01"), S("2024-01-01"));
        }

        @Test
        void solvesRateOfReturn() {
            var result = invoke("xirr", amounts(), dates());
            var d = result.asDouble();
            assertNotNull(d);
            assertTrue(Math.abs(d - 0.10777982564924497) < 1e-6, "xirr=" + d);
        }

        @Test
        void singleFlowYieldsNull() {
            assertTrue(invoke("xirr", Arr(F(-1000)), Arr(S("2020-01-01"))).isNull());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("xirr", amounts()).isNull());
        }
    }
}
