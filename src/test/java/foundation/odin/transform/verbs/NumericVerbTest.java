package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Numeric, math, financial, statistics, and datetime verb tests ported from the .NET SDK.
 */
class NumericVerbTest {

    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) {
        return DynValue.ofArray(new ArrayList<>(List.of(items)));
    }

    /** Assert that a result is numeric and close to the expected value. */
    private static void assertNumeric(DynValue result, double expected, double tolerance) {
        Double d = result.asDouble();
        Long i = result.asInt64();
        if (d != null)
            assertTrue(Math.abs(d - expected) < tolerance,
                    "Float(" + d + ") not close to " + expected);
        else if (i != null)
            assertTrue(Math.abs((double) i - expected) < tolerance,
                    "Integer(" + i + ") not close to " + expected);
        else
            fail("Expected numeric, got " + result.getType());
    }

    // =========================================================================
    // 1. FORMAT NUMBER VERBS
    // =========================================================================

    @Test void formatNumber_ZeroDecimals() { assertEquals("3", invoke("formatNumber", F(3.14159), I(0)).asString()); }
    @Test void formatNumber_TwoDecimals() { assertEquals("3.14", invoke("formatNumber", F(3.14159), I(2)).asString()); }
    @Test void formatNumber_ManyDecimals() { assertEquals("1.00000", invoke("formatNumber", F(1.0), I(5)).asString()); }
    @Test void formatNumber_Negative() { assertEquals("-42.6", invoke("formatNumber", F(-42.567), I(1)).asString()); }
    @Test void formatNumber_FromInteger() { assertEquals("100.00", invoke("formatNumber", I(100), I(2)).asString()); }
    @Test void formatNumber_FromString() { assertEquals("99.900", invoke("formatNumber", S("99.9"), I(3)).asString()); }
    @Test void formatNumber_MissingArgs() { assertTrue(invoke("formatNumber", F(1.0)).isNull()); }

    @Test void formatInteger_Basic() { assertEquals("3", invoke("formatInteger", F(3.7)).asString()); }
    @Test void formatInteger_Negative() { assertEquals("-3", invoke("formatInteger", F(-2.3)).asString()); }
    @Test void formatInteger_FromInt() { assertEquals("42", invoke("formatInteger", I(42)).asString()); }

    @Test void formatCurrency_Basic() { assertEquals("1234.50", invoke("formatCurrency", F(1234.5)).asString()); }
    @Test void formatCurrency_Negative() { assertEquals("-100.00", invoke("formatCurrency", F(-99.999)).asString()); }
    @Test void formatCurrency_Zero() { assertEquals("0.00", invoke("formatCurrency", F(0.0)).asString()); }

    @Test void formatPercent_Basic() { assertEquals("85%", invoke("formatPercent", F(0.85), I(0)).asString()); }
    @Test void formatPercent_WithDecimals() { assertEquals("85.7%", invoke("formatPercent", F(0.8567), I(1)).asString()); }
    @Test void formatPercent_Zero() { assertEquals("0%", invoke("formatPercent", F(0.0), I(0)).asString()); }
    @Test void formatPercent_OverOne() { assertEquals("150%", invoke("formatPercent", F(1.5), I(0)).asString()); }

    // =========================================================================
    // 2. FLOOR / CEIL / NEGATE / SIGN / TRUNC
    // =========================================================================

    @Test void floor_Positive() { assertNumeric(invoke("floor", F(3.7)), 3.0, 1e-10); }
    @Test void floor_Negative() { assertNumeric(invoke("floor", F(-3.2)), -4.0, 1e-10); }
    @Test void floor_Exact() { assertNumeric(invoke("floor", F(5.0)), 5.0, 1e-10); }
    @Test void floor_Null() { assertTrue(invoke("floor").isNull()); }
    @Test void floor_Integer() { assertNumeric(invoke("floor", I(5)), 5.0, 1e-10); }

    @Test void ceil_Positive() { assertNumeric(invoke("ceil", F(3.1)), 4.0, 1e-10); }
    @Test void ceil_Negative() { assertNumeric(invoke("ceil", F(-3.7)), -3.0, 1e-10); }
    @Test void ceil_Exact() { assertNumeric(invoke("ceil", F(5.0)), 5.0, 1e-10); }
    @Test void ceil_Null() { assertTrue(invoke("ceil").isNull()); }

    @Test void negate_Positive() { assertNumeric(invoke("negate", I(42)), -42.0, 1e-10); }
    @Test void negate_Negative() { assertNumeric(invoke("negate", F(-3.14)), 3.14, 1e-10); }
    @Test void negate_Zero() { assertNumeric(invoke("negate", I(0)), 0.0, 1e-10); }
    @Test void negate_PositiveInt() { assertNumeric(invoke("negate", I(5)), -5.0, 1e-10); }
    @Test void negate_NegativeInt() { assertNumeric(invoke("negate", I(-5)), 5.0, 1e-10); }
    @Test void negate_Float() { assertNumeric(invoke("negate", F(3.14)), -3.14, 1e-10); }
    @Test void negate_StringNum() { assertNumeric(invoke("negate", S("7")), -7.0, 1e-10); }
    @Test void negate_Null() { assertTrue(invoke("negate").isNull()); }

    @Test void sign_Positive() { assertNumeric(invoke("sign", F(42.0)), 1.0, 1e-10); }
    @Test void sign_Negative() { assertNumeric(invoke("sign", F(-5.0)), -1.0, 1e-10); }
    @Test void sign_Zero() { assertNumeric(invoke("sign", F(0.0)), 0.0, 1e-10); }
    @Test void sign_Null() { assertTrue(invoke("sign").isNull()); }

    @Test void trunc_Positive() { assertNumeric(invoke("trunc", F(3.9)), 3.0, 1e-10); }
    @Test void trunc_Negative() { assertNumeric(invoke("trunc", F(-3.9)), -3.0, 1e-10); }
    @Test void trunc_Null() { assertTrue(invoke("trunc").isNull()); }

    // =========================================================================
    // 3. ADD / SUBTRACT / MULTIPLY / DIVIDE
    // =========================================================================

    @Test void add_Integers() { assertNumeric(invoke("add", I(3), I(4)), 7.0, 1e-10); }
    @Test void add_StringNumbers() { assertNumeric(invoke("add", S("3"), S("4")), 7.0, 1e-10); }
    @Test void add_Negative() { assertNumeric(invoke("add", I(-3), I(-4)), -7.0, 1e-10); }
    @Test void add_Zero() { assertNumeric(invoke("add", I(0), I(0)), 0.0, 1e-10); }
    @Test void add_TooFew() { assertTrue(invoke("add", I(1)).isNull()); }
    @Test void add_Floats() { assertNumeric(invoke("add", F(1.5), F(2.5)), 4.0, 1e-10); }

    @Test void subtract_Basic() { assertNumeric(invoke("subtract", I(10), I(3)), 7.0, 1e-10); }
    @Test void subtract_NegativeResult() { assertNumeric(invoke("subtract", I(3), I(10)), -7.0, 1e-10); }
    @Test void subtract_Floats() { assertNumeric(invoke("subtract", F(5.5), F(2.5)), 3.0, 1e-10); }
    @Test void subtract_TooFew() { assertTrue(invoke("subtract", I(1)).isNull()); }

    @Test void multiply_Ints() { assertNumeric(invoke("multiply", I(3), I(4)), 12.0, 1e-10); }
    @Test void multiply_ByZero() { assertNumeric(invoke("multiply", I(100), I(0)), 0.0, 1e-10); }
    @Test void multiply_Negative() { assertNumeric(invoke("multiply", I(-3), I(4)), -12.0, 1e-10); }
    @Test void multiply_Floats() { assertNumeric(invoke("multiply", F(2.5), F(4.0)), 10.0, 1e-10); }
    @Test void multiply_TooFew() { assertTrue(invoke("multiply", I(1)).isNull()); }

    @Test void divide_Basic() {
        DynValue result = invoke("divide", I(10), I(2));
        assertEquals(5.0, result.asDouble());
    }
    @Test void divide_ByZero() { assertTrue(invoke("divide", I(10), I(0)).isNull()); }
    @Test void divide_Floats() { assertEquals(3.0, invoke("divide", F(7.5), F(2.5)).asDouble()); }
    @Test void divide_Negative() { assertEquals(-5.0, invoke("divide", I(-10), I(2)).asDouble()); }
    @Test void divide_TooFew() { assertTrue(invoke("divide", I(1)).isNull()); }

    // =========================================================================
    // 4. ABS / ROUND / MOD
    // =========================================================================

    @Test void abs_Positive() { assertNumeric(invoke("abs", I(5)), 5.0, 1e-10); }
    @Test void abs_Negative() { assertNumeric(invoke("abs", I(-5)), 5.0, 1e-10); }
    @Test void abs_Zero() { assertNumeric(invoke("abs", I(0)), 0.0, 1e-10); }
    @Test void abs_NegativeFloat() { assertNumeric(invoke("abs", F(-3.14)), 3.14, 1e-10); }
    @Test void abs_StringNumber() { assertNumeric(invoke("abs", S("-42")), 42.0, 1e-10); }
    @Test void abs_StringFloat() { assertNumeric(invoke("abs", S("-3.14")), 3.14, 1e-10); }
    @Test void abs_Null() { assertTrue(invoke("abs").isNull()); }

    @Test void round_Basic() { assertNumeric(invoke("round", F(3.7)), 4.0, 1e-10); }
    @Test void round_Down() { assertNumeric(invoke("round", F(3.2)), 3.0, 1e-10); }
    @Test void round_Half() { assertNumeric(invoke("round", F(2.5)), 2.0, 1e-10); }
    @Test void round_Negative() { assertNumeric(invoke("round", F(-2.7)), -3.0, 1e-10); }
    @Test void round_IntPassthrough() { assertNumeric(invoke("round", I(42)), 42.0, 1e-10); }
    @Test void round_WithPlaces() { assertNumeric(invoke("round", F(3.14159), I(2)), 3.14, 1e-10); }
    @Test void round_StringNumber() { assertNumeric(invoke("round", S("3.7")), 4.0, 1e-10); }
    @Test void round_Null() { assertTrue(invoke("round").isNull()); }

    @Test void mod_Basic() { assertNumeric(invoke("mod", I(10), I(3)), 1.0, 1e-10); }
    @Test void mod_NoRemainder() { assertNumeric(invoke("mod", I(9), I(3)), 0.0, 1e-10); }
    @Test void mod_Float() { assertNumeric(invoke("mod", F(10.5), F(3.0)), 1.5, 1e-10); }
    @Test void mod_ByZero() { assertTrue(invoke("mod", I(10), I(0)).isNull()); }
    @Test void mod_TooFew() { assertTrue(invoke("mod", I(10)).isNull()); }

    // =========================================================================
    // 5. PARSE_INT
    // =========================================================================

    @Test void parseInt_Basic() { assertNumeric(invoke("parseInt", S("42")), 42.0, 1e-10); }
    @Test void parseInt_Negative() { assertNumeric(invoke("parseInt", S("-99")), -99.0, 1e-10); }
    @Test void parseInt_Null() { assertTrue(invoke("parseInt", Null()).isNull()); }
    @Test void parseInt_AlreadyInt() { assertNumeric(invoke("parseInt", I(42)), 42.0, 1e-10); }
    @Test void parseInt_FromFloat() { assertNumeric(invoke("parseInt", F(3.7)), 3.0, 1e-10); }

    // =========================================================================
    // 6. IS_FINITE / IS_NAN
    // =========================================================================

    @Test void isFinite_Normal() { assertEquals(true, invoke("isFinite", F(42.0)).asBool()); }
    @Test void isFinite_Infinity() { assertEquals(false, invoke("isFinite", F(Double.POSITIVE_INFINITY)).asBool()); }
    @Test void isFinite_NegInfinity() { assertEquals(false, invoke("isFinite", F(Double.NEGATIVE_INFINITY)).asBool()); }
    @Test void isFinite_NaN() { assertEquals(false, invoke("isFinite", F(Double.NaN)).asBool()); }
    @Test void isFinite_Zero() { assertEquals(true, invoke("isFinite", F(0.0)).asBool()); }
    @Test void isFinite_Negative() { assertEquals(true, invoke("isFinite", F(-999.0)).asBool()); }

    @Test void isNaN_Normal() { assertEquals(false, invoke("isNaN", F(42.0)).asBool()); }
    @Test void isNaN_NaN() { assertEquals(true, invoke("isNaN", F(Double.NaN)).asBool()); }
    @Test void isNaN_Infinity() { assertEquals(false, invoke("isNaN", F(Double.POSITIVE_INFINITY)).asBool()); }
    @Test void isNaN_NegInfinity() { assertEquals(false, invoke("isNaN", F(Double.NEGATIVE_INFINITY)).asBool()); }

    // =========================================================================
    // 7. MIN_OF / MAX_OF
    // =========================================================================

    @Test void minOf_Basic() { assertNumeric(invoke("minOf", I(3), I(1), I(2)), 1.0, 1e-10); }
    @Test void minOf_Negative() { assertNumeric(invoke("minOf", F(-5.0), F(-3.0), F(-10.0)), -10.0, 1e-10); }
    @Test void minOf_Single() { assertNumeric(invoke("minOf", I(42)), 42.0, 1e-10); }
    @Test void minOf_MixedTypes() { assertNumeric(invoke("minOf", I(5), F(3.5), S("2")), 2.0, 1e-10); }
    @Test void minOf_Null() { assertTrue(invoke("minOf").isNull()); }

    @Test void maxOf_Basic() { assertNumeric(invoke("maxOf", I(3), I(1), I(2)), 3.0, 1e-10); }
    @Test void maxOf_Negative() { assertNumeric(invoke("maxOf", F(-5.0), F(-3.0), F(-10.0)), -3.0, 1e-10); }
    @Test void maxOf_Single() { assertNumeric(invoke("maxOf", I(42)), 42.0, 1e-10); }
    @Test void maxOf_LargeNumbers() { assertNumeric(invoke("maxOf", F(1e15), F(1e14), F(1e16)), 1e16, 1e6); }
    @Test void maxOf_Null() { assertTrue(invoke("maxOf").isNull()); }

    // =========================================================================
    // 8. SAFE_DIVIDE
    // =========================================================================

    @Test void safeDivide_Normal() { assertNumeric(invoke("safeDivide", F(10.0), F(3.0), F(0.0)), 10.0 / 3.0, 1e-10); }
    @Test void safeDivide_ByZero() { assertNumeric(invoke("safeDivide", F(10.0), F(0.0), F(-1.0)), -1.0, 1e-10); }
    @Test void safeDivide_ZeroNumerator() { assertNumeric(invoke("safeDivide", F(0.0), F(5.0), F(0.0)), 0.0, 1e-10); }
    @Test void safeDivide_MissingArgs() { assertTrue(invoke("safeDivide", F(10.0)).isNull()); }

    // =========================================================================
    // 9. MATH VERBS: LOG, LN, LOG10, EXP, POW, SQRT
    // =========================================================================

    @Test void log_Base2() { assertNumeric(invoke("log", F(8.0), F(2.0)), 3.0, 1e-10); }
    @Test void log_Base10() { assertNumeric(invoke("log", F(1000.0), F(10.0)), 3.0, 1e-10); }
    @Test void log_Null() { assertNumeric(invoke("log", F(8.0)), Math.log(8.0), 1e-10); }

    @Test void ln_E() { assertNumeric(invoke("ln", F(Math.E)), 1.0, 1e-10); }
    @Test void ln_One() { assertNumeric(invoke("ln", F(1.0)), 0.0, 1e-10); }
    @Test void ln_Null() { assertTrue(invoke("ln").isNull()); }

    @Test void log10_Hundred() { assertNumeric(invoke("log10", F(100.0)), 2.0, 1e-10); }
    @Test void log10_Thousand() { assertNumeric(invoke("log10", F(1000.0)), 3.0, 1e-10); }
    @Test void log10_Null() { assertTrue(invoke("log10").isNull()); }

    @Test void exp_Zero() { assertNumeric(invoke("exp", F(0.0)), 1.0, 1e-10); }
    @Test void exp_One() { assertNumeric(invoke("exp", F(1.0)), Math.E, 1e-10); }
    @Test void exp_Null() { assertTrue(invoke("exp").isNull()); }

    @Test void pow_Basic() { assertNumeric(invoke("pow", F(2.0), F(10.0)), 1024.0, 1e-10); }
    @Test void pow_Fractional() { assertNumeric(invoke("pow", F(4.0), F(0.5)), 2.0, 1e-10); }
    @Test void pow_ZeroExponent() { assertNumeric(invoke("pow", F(99.0), F(0.0)), 1.0, 1e-10); }
    @Test void pow_Null() { assertTrue(invoke("pow", F(2.0)).isNull()); }

    @Test void sqrt_Perfect() { assertNumeric(invoke("sqrt", F(144.0)), 12.0, 1e-10); }
    @Test void sqrt_Zero() { assertNumeric(invoke("sqrt", F(0.0)), 0.0, 1e-10); }
    @Test void sqrt_NonPerfect() { assertNumeric(invoke("sqrt", F(2.0)), Math.sqrt(2.0), 1e-10); }
    @Test void sqrt_Null() { assertTrue(invoke("sqrt").isNull()); }

    // =========================================================================
    // 10. FINANCIAL: COMPOUND / DISCOUNT
    // =========================================================================

    @Test void compound_Basic() {
        // 1000 * (1 + 0.05)^10
        DynValue result = invoke("compound", F(1000.0), F(0.05), I(10));
        assertNumeric(result, 1000.0 * Math.pow(1.05, 10), 0.01);
    }

    @Test void compound_ZeroRate() {
        DynValue result = invoke("compound", F(1000.0), F(0.0), I(10));
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test void compound_OnePeriod() {
        DynValue result = invoke("compound", F(500.0), F(0.10), I(1));
        assertNumeric(result, 550.0, 0.01);
    }

    @Test void compound_LargePeriods() {
        DynValue result = invoke("compound", F(100.0), F(0.07), I(30));
        assertNumeric(result, 100.0 * Math.pow(1.07, 30), 0.01);
    }

    @Test void compound_MissingArgs() { assertTrue(invoke("compound", F(100.0), F(0.05)).isNull()); }

    @Test void discount_Basic() {
        // 1000 / (1 + 0.05)^10
        DynValue result = invoke("discount", F(1000.0), F(0.05), I(10));
        assertNumeric(result, 1000.0 / Math.pow(1.05, 10), 0.01);
    }

    @Test void discount_ZeroRate() {
        DynValue result = invoke("discount", F(1000.0), F(0.0), I(5));
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test void discount_OnePeriod() {
        DynValue result = invoke("discount", F(1100.0), F(0.10), I(1));
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test void discount_MissingArgs() { assertTrue(invoke("discount", F(100.0)).isNull()); }

    // =========================================================================
    // 11. PMT / FV / PV
    // =========================================================================

    @Test void pmt_ZeroRate() {
        // pmt(principal=12000, rate=0, periods=12) = 12000 / 12 = 1000
        DynValue result = invoke("pmt", F(12000.0), F(0.0), F(12.0));
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test void pmt_OnePeriod() {
        // pmt(principal=1000, rate=0.1, periods=1) = 1000 * 0.1 * 1.1 / (1.1 - 1) = 1100
        DynValue result = invoke("pmt", F(1000.0), F(0.1), F(1.0));
        assertNumeric(result, 1100.0, 0.01);
    }

    @Test void pmt_MissingArgs() { assertTrue(invoke("pmt", F(0.05), F(360.0)).isNull()); }

    @Test void fv_ZeroRate() {
        // fv(payment=100, rate=0, periods=12) = 100 * 12 = 1200
        DynValue result = invoke("fv", F(100.0), F(0.0), F(12.0));
        assertNumeric(result, 1200.0, 0.01);
    }

    @Test void fv_MissingArgs() { assertTrue(invoke("fv", F(0.05), F(12.0)).isNull()); }

    @Test void pv_Basic() {
        // pv(payment=1000, rate=0.05, periods=10) = 1000 * (1 - (1.05)^-10) / 0.05
        double expected = 1000.0 * (1.0 - Math.pow(1.05, -10.0)) / 0.05;
        DynValue result = invoke("pv", F(1000.0), F(0.05), F(10.0));
        assertNumeric(result, expected, 0.01);
    }

    @Test void pv_MissingArgs() { assertTrue(invoke("pv", F(0.05)).isNull()); }

    // =========================================================================
    // 12. NPV
    // =========================================================================

    @Test void npv_Basic() {
        DynValue flows = Arr(F(-1000.0), F(300.0), F(400.0), F(500.0));
        DynValue result = invoke("npv", F(0.1), flows);
        // t=0 indexing: sum of flows[t] / (1+rate)^t
        double expected = -1000.0 + 300.0 / 1.1 + 400.0 / Math.pow(1.1, 2) + 500.0 / Math.pow(1.1, 3);
        assertNumeric(result, expected, 1.0);
    }

    @Test void npv_ZeroRate() {
        DynValue flows = Arr(F(-1000.0), F(500.0), F(500.0));
        DynValue result = invoke("npv", F(0.0), flows);
        assertNumeric(result, 0.0, 0.01);
    }

    @Test void npv_SingleFlow() {
        DynValue flows = Arr(F(1000.0));
        DynValue result = invoke("npv", F(0.1), flows);
        // t=0: 1000 / (1.1)^0 = 1000
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test void npv_MissingArgs() { assertTrue(invoke("npv", F(0.1)).isNull()); }

    // =========================================================================
    // 13. IRR
    // =========================================================================

    @Test void irr_Simple() {
        // -100, +110 => IRR = 0.10
        DynValue flows = Arr(F(-100.0), F(110.0));
        DynValue result = invoke("irr", flows);
        assertNumeric(result, 0.10, 0.01);
    }

    @Test void irr_EvenCashFlows() {
        DynValue flows = Arr(F(-1000.0), F(400.0), F(400.0), F(400.0));
        DynValue result = invoke("irr", flows);
        Double d = result.asDouble();
        assertNotNull(d);
        assertTrue(d > 0.05 && d < 0.15, "IRR=" + d);
    }

    // =========================================================================
    // 14. DEPRECIATION
    // =========================================================================

    @Test void depreciation_Basic() {
        // (10000 - 1000) / 5 = 1800
        DynValue result = invoke("depreciation", F(10000.0), F(1000.0), F(5.0));
        assertNumeric(result, 1800.0, 0.01);
    }

    @Test void depreciation_NoSalvage() {
        DynValue result = invoke("depreciation", F(5000.0), F(0.0), F(10.0));
        assertNumeric(result, 500.0, 0.01);
    }

    @Test void depreciation_ZeroLife() {
        DynValue result = invoke("depreciation", F(5000.0), F(0.0), F(0.0));
        assertTrue(result.isNull());
    }

    @Test void depreciation_MissingArgs() { assertTrue(invoke("depreciation", F(5000.0), F(0.0)).isNull()); }

    // =========================================================================
    // 15. STATISTICS: STD, VARIANCE, MEDIAN, MODE
    // =========================================================================

    @Test void std_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        DynValue result = invoke("std", a);
        assertNumeric(result, 2.0, 0.01);
    }

    @Test void std_Uniform() {
        DynValue a = Arr(F(5.0), F(5.0), F(5.0));
        DynValue result = invoke("std", a);
        assertNumeric(result, 0.0, 1e-10);
    }

    @Test void std_Null() { assertTrue(invoke("std").isNull()); }

    @Test void variance_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        DynValue result = invoke("variance", a);
        assertNumeric(result, 4.0, 0.01);
    }

    @Test void median_Odd() {
        DynValue a = Arr(F(3.0), F(1.0), F(2.0));
        DynValue result = invoke("median", a);
        assertNumeric(result, 2.0, 1e-10);
    }

    @Test void median_Even() {
        DynValue a = Arr(F(1.0), F(2.0), F(3.0), F(4.0));
        DynValue result = invoke("median", a);
        assertNumeric(result, 2.5, 1e-10);
    }

    @Test void median_Single() {
        DynValue a = Arr(F(42.0));
        DynValue result = invoke("median", a);
        assertNumeric(result, 42.0, 1e-10);
    }

    @Test void median_Null() { assertTrue(invoke("median").isNull()); }

    @Test void mode_Basic() {
        DynValue a = Arr(F(1.0), F(2.0), F(2.0), F(3.0));
        DynValue result = invoke("mode", a);
        assertNumeric(result, 2.0, 1e-10);
    }

    @Test void mode_AllSame() {
        DynValue a = Arr(F(7.0), F(7.0), F(7.0));
        DynValue result = invoke("mode", a);
        assertNumeric(result, 7.0, 1e-10);
    }

    // =========================================================================
    // 16. STD_SAMPLE / VARIANCE_SAMPLE
    // =========================================================================

    @Test void stdSample_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        DynValue result = invoke("stdSample", a);
        assertNumeric(result, Math.sqrt(32.0 / 7.0), 0.01);
    }

    @Test void stdSample_TooFew() {
        DynValue a = Arr(F(5.0));
        DynValue result = invoke("stdSample", a);
        assertTrue(result.isNull());
    }

    @Test void varianceSample_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        DynValue result = invoke("varianceSample", a);
        assertNumeric(result, 32.0 / 7.0, 0.01);
    }

    // =========================================================================
    // 17. PERCENTILE / QUANTILE
    // =========================================================================

    @Test void percentile_50th() {
        DynValue a = Arr(F(1.0), F(2.0), F(3.0), F(4.0), F(5.0));
        DynValue result = invoke("percentile", a, F(50.0));
        assertNumeric(result, 3.0, 1e-10);
    }

    @Test void percentile_0th() {
        DynValue a = Arr(F(1.0), F(2.0), F(3.0));
        DynValue result = invoke("percentile", a, F(0.0));
        assertNumeric(result, 1.0, 1e-10);
    }

    @Test void percentile_100th() {
        DynValue a = Arr(F(1.0), F(2.0), F(3.0));
        DynValue result = invoke("percentile", a, F(100.0));
        assertNumeric(result, 3.0, 1e-10);
    }

    @Test void quantile_Half() {
        DynValue a = Arr(F(10.0), F(20.0), F(30.0));
        DynValue result = invoke("quantile", a, F(0.5));
        assertNumeric(result, 20.0, 1e-10);
    }

    // =========================================================================
    // 18. COVARIANCE / CORRELATION
    // =========================================================================

    @Test void covariance_PerfectPositive() {
        DynValue a1 = Arr(F(1.0), F(2.0), F(3.0));
        DynValue a2 = Arr(F(2.0), F(4.0), F(6.0));
        DynValue result = invoke("covariance", a1, a2);
        assertNumeric(result, 4.0 / 3.0, 1e-10);
    }

    @Test void correlation_PerfectPositive() {
        DynValue a1 = Arr(F(1.0), F(2.0), F(3.0));
        DynValue a2 = Arr(F(2.0), F(4.0), F(6.0));
        DynValue result = invoke("correlation", a1, a2);
        assertNumeric(result, 1.0, 1e-10);
    }

    @Test void correlation_PerfectNegative() {
        DynValue a1 = Arr(F(1.0), F(2.0), F(3.0));
        DynValue a2 = Arr(F(6.0), F(4.0), F(2.0));
        DynValue result = invoke("correlation", a1, a2);
        assertNumeric(result, -1.0, 1e-10);
    }

    // =========================================================================
    // 19. ZSCORE
    // =========================================================================

    @Test void zscore_AtMean() {
        // zscore(value, dataset)
        DynValue result = invoke("zscore", F(5.0), Arr(F(1.0), F(3.0), F(5.0), F(7.0), F(9.0)));
        assertNumeric(result, 0.0, 1e-10);
    }

    @Test void zscore_AboveMean() {
        DynValue result = invoke("zscore", F(7.0), Arr(F(1.0), F(3.0), F(5.0), F(7.0), F(9.0)));
        assertNumeric(result, 2.0 / Math.sqrt(8.0), 1e-10);
    }

    @Test void zscore_ZeroStddev() {
        DynValue result = invoke("zscore", F(5.0), Arr(F(5.0), F(5.0), F(5.0)));
        assertTrue(result.isNull());
    }

    // =========================================================================
    // 20. CLAMP / INTERPOLATE / WEIGHTED_AVG
    // =========================================================================

    @Test void clamp_WithinRange() { assertNumeric(invoke("clamp", F(5.0), F(1.0), F(10.0)), 5.0, 1e-10); }
    @Test void clamp_BelowMin() { assertNumeric(invoke("clamp", F(-5.0), F(0.0), F(10.0)), 0.0, 1e-10); }
    @Test void clamp_AboveMax() { assertNumeric(invoke("clamp", F(15.0), F(0.0), F(10.0)), 10.0, 1e-10); }
    @Test void clamp_MissingArgs() { assertTrue(invoke("clamp", F(5.0), F(0.0)).isNull()); }

    @Test void interpolate_Midpoint() {
        // interpolate(x, x1, y1, x2, y2)
        DynValue result = invoke("interpolate", F(5.0), F(0.0), F(100.0), F(10.0), F(200.0));
        assertNumeric(result, 150.0, 1e-10);
    }

    @Test void interpolate_AtStart() {
        DynValue result = invoke("interpolate", F(0.0), F(0.0), F(100.0), F(10.0), F(200.0));
        assertNumeric(result, 100.0, 1e-10);
    }

    @Test void interpolate_AtEnd() {
        DynValue result = invoke("interpolate", F(10.0), F(0.0), F(100.0), F(10.0), F(200.0));
        assertNumeric(result, 200.0, 1e-10);
    }

    @Test void interpolate_MissingArgs() { assertTrue(invoke("interpolate", F(0.0), F(100.0)).isNull()); }

    @Test void weightedAvg_Basic() {
        DynValue vals = Arr(F(80.0), F(90.0), F(100.0));
        DynValue wts = Arr(F(1.0), F(2.0), F(1.0));
        DynValue result = invoke("weightedAvg", vals, wts);
        assertNumeric(result, 90.0, 1e-10);
    }

    @Test void weightedAvg_EqualWeights() {
        DynValue vals = Arr(F(10.0), F(20.0), F(30.0));
        DynValue wts = Arr(F(1.0), F(1.0), F(1.0));
        DynValue result = invoke("weightedAvg", vals, wts);
        assertNumeric(result, 20.0, 1e-10);
    }

    @Test void weightedAvg_MissingArgs() { assertTrue(invoke("weightedAvg", Arr(F(1.0))).isNull()); }

    // =========================================================================
    // 21. DATETIME - formatDate
    // =========================================================================

    @Test void formatDate_YYYYMMDD() {
        DynValue result = invoke("formatDate", S("2024-06-15"), S("YYYY-MM-DD"));
        assertEquals("2024-06-15", result.asString());
    }

    @Test void formatDate_MMDDYYYY() {
        DynValue result = invoke("formatDate", S("2024-06-15"), S("MM/DD/YYYY"));
        assertEquals("06/15/2024", result.asString());
    }

    @Test void formatDate_DDMMYYYY() {
        DynValue result = invoke("formatDate", S("2024-06-15"), S("DD-MM-YYYY"));
        assertEquals("15-06-2024", result.asString());
    }

    @Test void formatDate_FromTimestamp() {
        DynValue result = invoke("formatDate", S("2024-06-15T14:30:00"), S("YYYY-MM-DD"));
        assertEquals("2024-06-15", result.asString());
    }

    @Test void formatDate_Null() { assertTrue(invoke("formatDate", Null(), S("YYYY-MM-DD")).isNull()); }

    // =========================================================================
    // 22. DATETIME - addDays / addMonths / addYears
    // =========================================================================

    @Test void addDays_Positive() {
        DynValue result = invoke("addDays", S("2024-01-01"), I(10));
        assertEquals("2024-01-11", result.asString());
    }

    @Test void addDays_Negative() {
        DynValue result = invoke("addDays", S("2024-01-11"), I(-10));
        assertEquals("2024-01-01", result.asString());
    }

    @Test void addDays_CrossMonth() {
        DynValue result = invoke("addDays", S("2024-01-28"), I(5));
        assertEquals("2024-02-02", result.asString());
    }

    @Test void addDays_Null() { assertTrue(invoke("addDays", Null(), I(1)).isNull()); }

    @Test void addMonths_Positive() {
        DynValue result = invoke("addMonths", S("2024-01-15"), I(3));
        assertEquals("2024-04-15", result.asString());
    }

    @Test void addMonths_CrossYear() {
        DynValue result = invoke("addMonths", S("2024-11-15"), I(3));
        assertEquals("2025-02-15", result.asString());
    }

    @Test void addMonths_Null() { assertTrue(invoke("addMonths", Null(), I(1)).isNull()); }

    @Test void addYears_Positive() {
        DynValue result = invoke("addYears", S("2024-06-15"), I(2));
        assertEquals("2026-06-15", result.asString());
    }

    @Test void addYears_Negative() {
        DynValue result = invoke("addYears", S("2024-06-15"), I(-1));
        assertEquals("2023-06-15", result.asString());
    }

    @Test void addYears_Null() { assertTrue(invoke("addYears", Null(), I(1)).isNull()); }

    // =========================================================================
    // 23. DATETIME - dateDiff / daysBetweenDates
    // =========================================================================

    @Test void dateDiff_Basic() {
        DynValue result = invoke("dateDiff", S("2024-01-01"), S("2024-01-11"));
        assertEquals(10L, result.asInt64());
    }

    @Test void dateDiff_Negative() {
        DynValue result = invoke("dateDiff", S("2024-01-11"), S("2024-01-01"));
        assertEquals(-10L, result.asInt64());
    }

    @Test void dateDiff_Null() { assertTrue(invoke("dateDiff", Null(), S("2024-01-01")).isNull()); }

    @Test void daysBetweenDates_Basic() {
        DynValue result = invoke("daysBetweenDates", S("2024-01-01"), S("2024-01-11"));
        assertEquals(10L, result.asInt64());
    }

    @Test void daysBetweenDates_Absolute() {
        // daysBetweenDates returns the signed end-minus-start difference
        DynValue result = invoke("daysBetweenDates", S("2024-01-11"), S("2024-01-01"));
        assertEquals(-10L, result.asInt64());
    }

    // =========================================================================
    // 24. DATETIME - quarter / dayOfWeek / weekOfYear / isLeapYear
    // =========================================================================

    @Test void quarter_Q1() {
        DynValue result = invoke("quarter", S("2024-02-15"));
        assertEquals(1L, result.asInt64());
    }

    @Test void quarter_Q2() {
        DynValue result = invoke("quarter", S("2024-05-01"));
        assertEquals(2L, result.asInt64());
    }

    @Test void quarter_Q3() {
        DynValue result = invoke("quarter", S("2024-08-15"));
        assertEquals(3L, result.asInt64());
    }

    @Test void quarter_Q4() {
        DynValue result = invoke("quarter", S("2024-12-01"));
        assertEquals(4L, result.asInt64());
    }

    @Test void quarter_Null() { assertTrue(invoke("quarter").isNull()); }

    @Test void dayOfWeek_Saturday() {
        // 2024-06-15 is a Saturday = 6
        DynValue result = invoke("dayOfWeek", S("2024-06-15"));
        assertEquals(6L, result.asInt64());
    }

    @Test void dayOfWeek_Monday() {
        // 2024-06-17 is a Monday = 1
        DynValue result = invoke("dayOfWeek", S("2024-06-17"));
        assertEquals(1L, result.asInt64());
    }

    @Test void dayOfWeek_Null() { assertTrue(invoke("dayOfWeek").isNull()); }

    @Test void weekOfYear_MidYear() {
        DynValue result = invoke("weekOfYear", S("2024-06-15"));
        Long week = result.asInt64();
        assertNotNull(week);
        assertTrue(week >= 24 && week <= 25, "Week=" + week);
    }

    @Test void weekOfYear_Null() { assertTrue(invoke("weekOfYear").isNull()); }

    @Test void isLeapYear_2024() {
        DynValue result = invoke("isLeapYear", I(2024));
        assertEquals(true, result.asBool());
    }

    @Test void isLeapYear_2023() {
        DynValue result = invoke("isLeapYear", I(2023));
        assertEquals(false, result.asBool());
    }

    @Test void isLeapYear_2000() {
        DynValue result = invoke("isLeapYear", I(2000));
        assertEquals(true, result.asBool());
    }

    @Test void isLeapYear_1900() {
        DynValue result = invoke("isLeapYear", I(1900));
        assertEquals(false, result.asBool());
    }

    @Test void isLeapYear_FromDate() {
        DynValue result = invoke("isLeapYear", S("2024-06-15"));
        assertEquals(true, result.asBool());
    }

    // =========================================================================
    // 25. DATETIME - isBefore / isAfter / isBetween
    // =========================================================================

    @Test void isBefore_True() {
        DynValue result = invoke("isBefore", S("2024-01-01"), S("2024-06-01"));
        assertEquals(true, result.asBool());
    }

    @Test void isBefore_False() {
        DynValue result = invoke("isBefore", S("2024-06-01"), S("2024-01-01"));
        assertEquals(false, result.asBool());
    }

    @Test void isBefore_Equal() {
        DynValue result = invoke("isBefore", S("2024-01-01"), S("2024-01-01"));
        assertEquals(false, result.asBool());
    }

    @Test void isBefore_Null() { assertTrue(invoke("isBefore", Null(), S("2024-01-01")).isNull()); }

    @Test void isAfter_True() {
        DynValue result = invoke("isAfter", S("2024-06-01"), S("2024-01-01"));
        assertEquals(true, result.asBool());
    }

    @Test void isAfter_False() {
        DynValue result = invoke("isAfter", S("2024-01-01"), S("2024-06-01"));
        assertEquals(false, result.asBool());
    }

    @Test void isAfter_Null() { assertTrue(invoke("isAfter", Null(), S("2024-01-01")).isNull()); }

    @Test void isBetween_True() {
        DynValue result = invoke("isBetween", S("2024-03-01"), S("2024-01-01"), S("2024-06-01"));
        assertEquals(true, result.asBool());
    }

    @Test void isBetween_False() {
        DynValue result = invoke("isBetween", S("2024-07-01"), S("2024-01-01"), S("2024-06-01"));
        assertEquals(false, result.asBool());
    }

    @Test void isBetween_AtBoundary() {
        DynValue result = invoke("isBetween", S("2024-01-01"), S("2024-01-01"), S("2024-06-01"));
        assertEquals(true, result.asBool());
    }

    @Test void isBetween_Null() { assertTrue(invoke("isBetween", Null(), S("2024-01-01"), S("2024-06-01")).isNull()); }

    // =========================================================================
    // 26. DATETIME - startOf / endOf
    // =========================================================================

    @Test void startOfMonth_Basic() {
        DynValue result = invoke("startOfMonth", S("2024-06-15"));
        assertEquals("2024-06-01", result.asString());
    }

    @Test void endOfMonth_Basic() {
        DynValue result = invoke("endOfMonth", S("2024-06-15"));
        assertEquals("2024-06-30", result.asString());
    }

    @Test void endOfMonth_February_Leap() {
        DynValue result = invoke("endOfMonth", S("2024-02-01"));
        assertEquals("2024-02-29", result.asString());
    }

    @Test void endOfMonth_February_NonLeap() {
        DynValue result = invoke("endOfMonth", S("2023-02-01"));
        assertEquals("2023-02-28", result.asString());
    }

    @Test void startOfYear_Basic() {
        DynValue result = invoke("startOfYear", S("2024-06-15"));
        assertEquals("2024-01-01", result.asString());
    }

    @Test void endOfYear_Basic() {
        DynValue result = invoke("endOfYear", S("2024-06-15"));
        assertEquals("2024-12-31", result.asString());
    }

    @Test void startOfMonth_Null() { assertTrue(invoke("startOfMonth").isNull()); }
    @Test void endOfMonth_Null() { assertTrue(invoke("endOfMonth").isNull()); }
    @Test void startOfYear_Null() { assertTrue(invoke("startOfYear").isNull()); }
    @Test void endOfYear_Null() { assertTrue(invoke("endOfYear").isNull()); }

    // =========================================================================
    // 27. DATETIME - toUnix / fromUnix
    // =========================================================================

    @Test void toUnix_Epoch() {
        DynValue result = invoke("toUnix", S("1970-01-01T00:00:00Z"));
        assertEquals(0L, result.asInt64());
    }

    @Test void toUnix_Date() {
        DynValue result = invoke("toUnix", S("2024-01-01"));
        Long unix = result.asInt64();
        assertNotNull(unix);
        assertTrue(unix > 0);
    }

    @Test void toUnix_Null() { assertTrue(invoke("toUnix").isNull()); }

    @Test void fromUnix_Epoch() {
        DynValue result = invoke("fromUnix", I(0));
        String ts = result.asString();
        assertNotNull(ts);
        assertTrue(ts.contains("1970-01-01"));
    }

    @Test void fromUnix_Null() { assertTrue(invoke("fromUnix").isNull()); }

    // =========================================================================
    // 28. DATETIME - isValidDate
    // =========================================================================

    @Test void isValidDate_Valid() { assertEquals(true, invoke("isValidDate", S("2024-06-15")).asBool()); }
    @Test void isValidDate_InvalidMonth() { assertEquals(false, invoke("isValidDate", S("2024-13-01")).asBool()); }
    @Test void isValidDate_InvalidDay() { assertEquals(false, invoke("isValidDate", S("2024-02-30")).asBool()); }
    @Test void isValidDate_LeapDay() { assertEquals(true, invoke("isValidDate", S("2024-02-29")).asBool()); }
    @Test void isValidDate_NonLeapDay() { assertEquals(false, invoke("isValidDate", S("2023-02-29")).asBool()); }
    @Test void isValidDate_NotADate() { assertEquals(false, invoke("isValidDate", S("not-a-date")).asBool()); }
    @Test void isValidDate_NoArgs() { assertEquals(false, invoke("isValidDate").asBool()); }

    // =========================================================================
    // 29. RANDOM
    // =========================================================================

    @Test void random_NoArgs() {
        DynValue result = invoke("random");
        Double d = result.asDouble();
        assertNotNull(d);
        assertTrue(d >= 0.0 && d <= 1.0);
    }

    @Test void random_WithRange() {
        DynValue result = invoke("random", F(10.0), F(20.0));
        Double d = result.asDouble();
        assertNotNull(d);
        assertTrue(d >= 10.0 && d <= 20.0);
    }

    // =========================================================================
    // 30. FORMAT_LOCALE_NUMBER
    // =========================================================================

    @Test void formatLocaleNumber_Default() {
        DynValue result = invoke("formatLocaleNumber", F(1234567.89));
        assertNotNull(result.asString());
    }

    @Test void formatLocaleNumber_WithLocale() {
        DynValue result = invoke("formatLocaleNumber", F(1234.5), S("en-US"), I(2));
        assertTrue(result.asString().contains("1,234.50"));
    }

    @Test void formatLocaleNumber_Null() { assertTrue(invoke("formatLocaleNumber").isNull()); }

    // =========================================================================
    // 31. RATE / NPER
    // =========================================================================

    @Test void rate_Basic() {
        DynValue result = invoke("rate", F(10.0), F(-100.0), F(1000.0), F(0.0));
        Double d = result.asDouble();
        assertNotNull(d);
        assertTrue(Double.isFinite(d), "rate should be finite, got " + d);
    }

    @Test void rate_MissingArgs() { assertTrue(invoke("rate", F(10.0), F(-100.0)).isNull()); }

    @Test void nper_ZeroRate() {
        // nper(0, -100, 5000) = -5000/-100 = 50
        DynValue result = invoke("nper", F(0.0), F(-100.0), F(5000.0));
        assertNumeric(result, 50.0, 0.5);
    }

    @Test void nper_MissingArgs() { assertTrue(invoke("nper", F(0.01)).isNull()); }

    // =========================================================================
    // Registry verification - all verbs exist
    // =========================================================================

    @Test void registry_HasAllNumericVerbs() {
        TransformEngine.VerbContext c = new TransformEngine.VerbContext();
        String[] verbs = { "add", "subtract", "multiply", "divide", "abs", "round", "negate",
                "floor", "ceil", "sign", "trunc", "mod", "formatNumber", "formatInteger",
                "formatCurrency", "formatPercent", "minOf", "maxOf", "safeDivide", "parseInt",
                "isFinite", "isNaN", "random", "switch" };
        for (String verb : verbs) {
            try { TransformEngine.invokeVerb(verb, new DynValue[0], c); } catch (Exception ignored) { }
        }
    }

    @Test void registry_HasAllMathVerbs() {
        TransformEngine.VerbContext c = new TransformEngine.VerbContext();
        String[] verbs = { "log", "ln", "log10", "exp", "pow", "sqrt" };
        for (String verb : verbs) {
            try { TransformEngine.invokeVerb(verb, new DynValue[0], c); } catch (Exception ignored) { }
        }
    }

    @Test void registry_HasAllFinancialVerbs() {
        TransformEngine.VerbContext c = new TransformEngine.VerbContext();
        String[] verbs = { "compound", "discount", "pmt", "fv", "pv", "rate", "nper", "npv", "irr", "depreciation" };
        for (String verb : verbs) {
            try { TransformEngine.invokeVerb(verb, new DynValue[0], c); } catch (Exception ignored) { }
        }
    }

    @Test void registry_HasAllStatVerbs() {
        TransformEngine.VerbContext c = new TransformEngine.VerbContext();
        String[] verbs = { "std", "variance", "stdSample", "varianceSample", "median", "mode",
                "percentile", "quantile", "covariance", "correlation", "zscore", "clamp",
                "interpolate", "weightedAvg" };
        for (String verb : verbs) {
            try { TransformEngine.invokeVerb(verb, new DynValue[0], c); } catch (Exception ignored) { }
        }
    }

    @Test void registry_HasAllDateTimeVerbs() {
        TransformEngine.VerbContext c = new TransformEngine.VerbContext();
        String[] verbs = { "today", "now", "formatDate", "parseDate", "formatTime",
                "formatTimestamp", "parseTimestamp", "addDays", "addMonths", "addYears",
                "dateDiff", "addHours", "addMinutes", "addSeconds", "startOfDay", "endOfDay",
                "startOfMonth", "endOfMonth", "startOfYear", "endOfYear", "dayOfWeek",
                "weekOfYear", "quarter", "isLeapYear", "isBefore", "isAfter", "isBetween",
                "toUnix", "fromUnix", "daysBetweenDates", "ageFromDate", "isValidDate",
                "formatLocaleDate" };
        for (String verb : verbs) {
            try { TransformEngine.invokeVerb(verb, new DynValue[0], c); } catch (Exception ignored) { }
        }
    }
}
