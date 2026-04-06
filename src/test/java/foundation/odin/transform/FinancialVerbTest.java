package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for financial, statistical, and numeric edge case verbs.
 * Ported from .NET FinancialVerbTests.cs.
 */
class FinancialVerbTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args, null);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v)   { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v){ return DynValue.ofBool(v); }
    private static DynValue Null()      { return DynValue.ofNull(); }
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
    // formatNumber — extended
    // =========================================================================

    @Test
    void formatNumber_ZeroDecimals() {
        assertEquals(S("3"), invoke("formatNumber", F(3.14159), I(0)));
    }

    @Test
    void formatNumber_TwoDecimals() {
        assertEquals(S("3.14"), invoke("formatNumber", F(3.14159), I(2)));
    }

    @Test
    void formatNumber_ManyDecimals() {
        assertEquals(S("1.00000"), invoke("formatNumber", F(1.0), I(5)));
    }

    @Test
    void formatNumber_Negative() {
        assertEquals(S("-42.6"), invoke("formatNumber", F(-42.567), I(1)));
    }

    @Test
    void formatNumber_FromInteger() {
        assertEquals(S("100.00"), invoke("formatNumber", I(100), I(2)));
    }

    @Test
    void formatNumber_FromString() {
        assertEquals(S("99.900"), invoke("formatNumber", S("99.9"), I(3)));
    }

    // =========================================================================
    // formatInteger — extended
    // =========================================================================

    @Test
    void formatInteger_Basic() {
        assertEquals(S("3"), invoke("formatInteger", F(3.7)));
    }

    @Test
    void formatInteger_Negative() {
        assertEquals(S("-2"), invoke("formatInteger", F(-2.3)));
    }

    @Test
    void formatInteger_FromInt() {
        assertEquals(S("42"), invoke("formatInteger", I(42)));
    }

    // =========================================================================
    // formatCurrency — extended
    // =========================================================================

    @Test
    void formatCurrency_Basic() {
        assertEquals(S("1234.50"), invoke("formatCurrency", F(1234.5)));
    }

    @Test
    void formatCurrency_Negative() {
        assertEquals(S("-100.00"), invoke("formatCurrency", F(-99.999)));
    }

    @Test
    void formatCurrency_Zero() {
        assertEquals(S("0.00"), invoke("formatCurrency", F(0.0)));
    }

    // =========================================================================
    // formatPercent — extended
    // =========================================================================

    @Test
    void formatPercent_Basic() {
        assertEquals(S("85%"), invoke("formatPercent", F(0.85), I(0)));
    }

    @Test
    void formatPercent_WithDecimals() {
        assertEquals(S("85.7%"), invoke("formatPercent", F(0.8567), I(1)));
    }

    @Test
    void formatPercent_Zero() {
        assertEquals(S("0%"), invoke("formatPercent", F(0.0), I(0)));
    }

    @Test
    void formatPercent_OverOne() {
        assertEquals(S("150%"), invoke("formatPercent", F(1.5), I(0)));
    }

    // =========================================================================
    // floor / ceil / negate / sign / trunc — extended
    // =========================================================================

    @Test
    void floor_Positive() {
        assertNumeric(invoke("floor", F(3.7)), 3.0, 1e-10);
    }

    @Test
    void floor_Negative() {
        assertNumeric(invoke("floor", F(-3.2)), -4.0, 1e-10);
    }

    @Test
    void floor_Exact() {
        assertNumeric(invoke("floor", F(5.0)), 5.0, 1e-10);
    }

    @Test
    void ceil_Positive() {
        assertNumeric(invoke("ceil", F(3.1)), 4.0, 1e-10);
    }

    @Test
    void ceil_Negative() {
        assertNumeric(invoke("ceil", F(-3.7)), -3.0, 1e-10);
    }

    @Test
    void ceil_Exact() {
        assertNumeric(invoke("ceil", F(5.0)), 5.0, 1e-10);
    }

    @Test
    void negate_Positive() {
        assertNumeric(invoke("negate", I(42)), -42.0, 1e-10);
    }

    @Test
    void negate_Negative() {
        assertNumeric(invoke("negate", F(-3.14)), 3.14, 1e-10);
    }

    @Test
    void negate_Zero() {
        assertNumeric(invoke("negate", I(0)), 0.0, 1e-10);
    }

    @Test
    void sign_Positive() {
        assertNumeric(invoke("sign", F(42.0)), 1.0, 1e-10);
    }

    @Test
    void sign_Negative() {
        assertNumeric(invoke("sign", F(-5.0)), -1.0, 1e-10);
    }

    @Test
    void sign_Zero() {
        assertNumeric(invoke("sign", F(0.0)), 0.0, 1e-10);
    }

    @Test
    void trunc_Positive() {
        assertNumeric(invoke("trunc", F(3.9)), 3.0, 1e-10);
    }

    @Test
    void trunc_Negative() {
        assertNumeric(invoke("trunc", F(-3.9)), -3.0, 1e-10);
    }

    // =========================================================================
    // parseInt — extended
    // =========================================================================

    @Test
    void parseInt_Basic() {
        assertNumeric(invoke("parseInt", S("42")), 42.0, 1e-10);
    }

    @Test
    void parseInt_Negative() {
        assertNumeric(invoke("parseInt", S("-99")), -99.0, 1e-10);
    }

    @Test
    void parseInt_FromInteger() {
        assertNumeric(invoke("parseInt", I(77)), 77.0, 1e-10);
    }

    @Test
    void parseInt_FromFloat() {
        assertNumeric(invoke("parseInt", F(3.7)), 3.0, 1e-10);
    }

    // =========================================================================
    // isFinite / isNaN — extended
    // =========================================================================

    @Test
    void isFinite_Normal() {
        assertEquals(B(true), invoke("isFinite", F(42.0)));
    }

    @Test
    void isFinite_Infinity() {
        assertEquals(B(false), invoke("isFinite", F(Double.POSITIVE_INFINITY)));
    }

    @Test
    void isFinite_NegInfinity() {
        assertEquals(B(false), invoke("isFinite", F(Double.NEGATIVE_INFINITY)));
    }

    @Test
    void isFinite_NaN() {
        assertEquals(B(false), invoke("isFinite", F(Double.NaN)));
    }

    @Test
    void isNaN_Normal() {
        assertEquals(B(false), invoke("isNaN", F(42.0)));
    }

    @Test
    void isNaN_NaN() {
        assertEquals(B(true), invoke("isNaN", F(Double.NaN)));
    }

    // =========================================================================
    // minOf / maxOf — extended
    // =========================================================================

    @Test
    void minOf_Basic() {
        assertNumeric(invoke("minOf", I(3), I(1), I(2)), 1.0, 1e-10);
    }

    @Test
    void minOf_Negative() {
        assertNumeric(invoke("minOf", F(-5.0), F(-3.0), F(-10.0)), -10.0, 1e-10);
    }

    @Test
    void minOf_Single() {
        assertNumeric(invoke("minOf", I(42)), 42.0, 1e-10);
    }

    @Test
    void maxOf_Basic() {
        assertNumeric(invoke("maxOf", I(3), I(1), I(2)), 3.0, 1e-10);
    }

    @Test
    void maxOf_Negative() {
        assertNumeric(invoke("maxOf", F(-5.0), F(-3.0), F(-10.0)), -3.0, 1e-10);
    }

    @Test
    void maxOf_Single() {
        assertNumeric(invoke("maxOf", I(42)), 42.0, 1e-10);
    }

    @Test
    void maxOf_LargeNumbers() {
        assertNumeric(invoke("maxOf", F(1e15), F(1e14), F(1e16)), 1e16, 1e6);
    }

    // =========================================================================
    // safeDivide — extended
    // =========================================================================

    @Test
    void safeDivide_Normal() {
        assertNumeric(invoke("safeDivide", F(10.0), F(3.0), F(0.0)), 10.0 / 3.0, 1e-10);
    }

    @Test
    void safeDivide_ByZero() {
        // Returns the fallback (3rd arg)
        assertNumeric(invoke("safeDivide", F(10.0), F(0.0), F(-1.0)), -1.0, 1e-10);
    }

    @Test
    void safeDivide_ZeroNumerator() {
        assertNumeric(invoke("safeDivide", F(0.0), F(5.0), F(0.0)), 0.0, 1e-10);
    }

    // =========================================================================
    // Math: log, ln, log10, exp, pow, sqrt — extended
    // =========================================================================

    @Test
    void log_Base2() {
        assertNumeric(invoke("log", F(8.0), F(2.0)), 3.0, 1e-10);
    }

    @Test
    void log_Base10() {
        assertNumeric(invoke("log", F(1000.0), F(10.0)), 3.0, 1e-10);
    }

    @Test
    void ln_E() {
        assertNumeric(invoke("ln", F(Math.E)), 1.0, 1e-10);
    }

    @Test
    void ln_One() {
        assertNumeric(invoke("ln", F(1.0)), 0.0, 1e-10);
    }

    @Test
    void log10_Hundred() {
        assertNumeric(invoke("log10", F(100.0)), 2.0, 1e-10);
    }

    @Test
    void exp_Zero() {
        assertNumeric(invoke("exp", F(0.0)), 1.0, 1e-10);
    }

    @Test
    void exp_One() {
        assertNumeric(invoke("exp", F(1.0)), Math.E, 1e-10);
    }

    @Test
    void pow_Basic() {
        assertNumeric(invoke("pow", F(2.0), F(10.0)), 1024.0, 1e-10);
    }

    @Test
    void pow_Fractional() {
        assertNumeric(invoke("pow", F(4.0), F(0.5)), 2.0, 1e-10);
    }

    @Test
    void pow_ZeroExponent() {
        assertNumeric(invoke("pow", F(99.0), F(0.0)), 1.0, 1e-10);
    }

    @Test
    void sqrt_Perfect() {
        assertNumeric(invoke("sqrt", F(144.0)), 12.0, 1e-10);
    }

    @Test
    void sqrt_Zero() {
        assertNumeric(invoke("sqrt", F(0.0)), 0.0, 1e-10);
    }

    @Test
    void sqrt_NonPerfect() {
        assertNumeric(invoke("sqrt", F(2.0)), Math.sqrt(2.0), 1e-10);
    }

    // =========================================================================
    // Compound / Discount — TVM
    // =========================================================================

    @Test
    void compound_Basic() {
        // 1000 * (1 + 0.05)^10
        assertNumeric(invoke("compound", F(1000.0), F(0.05), I(10)),
                1000.0 * Math.pow(1.05, 10), 0.01);
    }

    @Test
    void compound_ZeroRate() {
        assertNumeric(invoke("compound", F(1000.0), F(0.0), I(10)), 1000.0, 0.01);
    }

    @Test
    void compound_OnePeriod() {
        assertNumeric(invoke("compound", F(500.0), F(0.10), I(1)), 550.0, 0.01);
    }

    @Test
    void compound_LargePeriods() {
        assertNumeric(invoke("compound", F(100.0), F(0.07), I(30)),
                100.0 * Math.pow(1.07, 30), 0.01);
    }

    @Test
    void discount_Basic() {
        assertNumeric(invoke("discount", F(1000.0), F(0.05), I(10)),
                1000.0 / Math.pow(1.05, 10), 0.01);
    }

    @Test
    void discount_ZeroRate() {
        assertNumeric(invoke("discount", F(1000.0), F(0.0), I(5)), 1000.0, 0.01);
    }

    @Test
    void discount_OnePeriod() {
        assertNumeric(invoke("discount", F(1100.0), F(0.10), I(1)), 1000.0, 0.01);
    }

    // =========================================================================
    // pmt / fv / pv — TVM
    // =========================================================================

    @Test
    void pmt_Basic() {
        // pmt(principal=200000, rate=0.05/12, periods=360)
        double rate = 0.05 / 12.0;
        double n = 360.0;
        double p = 200000.0;
        double expected = p * rate * Math.pow(1.0 + rate, n) / (Math.pow(1.0 + rate, n) - 1.0);
        DynValue result = invoke("pmt", F(p), F(rate), F(n));
        assertNumeric(result, expected, 1.0);
    }

    @Test
    void pmt_ZeroRate() {
        // pmt(principal=12000, rate=0, periods=12) = 12000 / 12 = 1000
        assertNumeric(invoke("pmt", F(12000.0), F(0.0), F(12.0)), 1000.0, 0.01);
    }

    @Test
    void pmt_OnePeriod() {
        // pmt(principal=1000, rate=0.1, periods=1) = 1000 * 0.1 * 1.1 / (1.1 - 1) = 1100
        assertNumeric(invoke("pmt", F(1000.0), F(0.1), F(1.0)), 1100.0, 0.01);
    }

    @Test
    void fv_ZeroRate() {
        // fv(payment=100, rate=0, periods=12) = 100 * 12 = 1200
        assertNumeric(invoke("fv", F(100.0), F(0.0), F(12.0)), 1200.0, 0.01);
    }

    @Test
    void fv_ZeroRate_WithPmt() {
        // fv(payment=100, rate=0, periods=12) = 100 * 12 = 1200
        assertNumeric(invoke("fv", F(100.0), F(0.0), F(12.0)), 1200.0, 0.01);
    }

    @Test
    void pv_Basic() {
        // pv(payment=1000, rate=0.05, periods=10) = 1000 * (1 - (1.05)^-10) / 0.05
        double expected = 1000.0 * (1.0 - Math.pow(1.05, -10.0)) / 0.05;
        assertNumeric(invoke("pv", F(1000.0), F(0.05), F(10.0)),
                expected, 0.01);
    }

    // =========================================================================
    // rate / nper — TVM
    // =========================================================================

    @Test
    void rate_Basic() {
        DynValue result = invoke("rate", F(10.0), F(-100.0), F(1000.0), F(0.0));
        assertNotNull(result.asDouble());
        assertTrue(Double.isFinite(result.asDouble()));
    }

    @Test
    void nper_Basic() {
        DynValue result = invoke("nper", F(0.01), F(-100.0), F(5000.0));
        assertTrue(result.asDouble() != null || result.asInt64() != null);
    }

    @Test
    void nper_ZeroRate() {
        // nper(0, -100, 5000) = -5000 / -100 = 50
        assertNumeric(invoke("nper", F(0.0), F(-100.0), F(5000.0)), 50.0, 0.01);
    }

    // =========================================================================
    // NPV — extended
    // =========================================================================

    @Test
    void npv_Basic() {
        DynValue flows = Arr(F(-1000.0), F(300.0), F(400.0), F(500.0));
        DynValue result = invoke("npv", F(0.1), flows);
        // t=0 indexing: sum of cf[t] / (1+r)^t
        double expected = -1000.0 + 300.0 / 1.1 + 400.0 / Math.pow(1.1, 2) + 500.0 / Math.pow(1.1, 3);
        assertNumeric(result, expected, 0.01);
    }

    @Test
    void npv_ZeroRate() {
        DynValue flows = Arr(F(-1000.0), F(500.0), F(500.0));
        DynValue result = invoke("npv", F(0.0), flows);
        assertNumeric(result, 0.0, 0.01);
    }

    @Test
    void npv_SingleFlow() {
        DynValue flows = Arr(F(1000.0));
        DynValue result = invoke("npv", F(0.1), flows);
        // t=0: 1000 / (1.1)^0 = 1000
        assertNumeric(result, 1000.0, 0.01);
    }

    @Test
    void npv_HighRate() {
        DynValue flows = Arr(F(-100.0), F(50.0), F(50.0), F(50.0));
        DynValue result = invoke("npv", F(0.5), flows);
        // t=0 indexing
        double e = -100.0 + 50.0 / 1.5 + 50.0 / Math.pow(1.5, 2) + 50.0 / Math.pow(1.5, 3);
        assertNumeric(result, e, 0.01);
    }

    // =========================================================================
    // IRR — extended
    // =========================================================================

    @Test
    void irr_Simple() {
        // -100, +110 => IRR = 0.10
        DynValue flows = Arr(F(-100.0), F(110.0));
        assertNumeric(invoke("irr", flows), 0.10, 0.001);
    }

    @Test
    void irr_Basic() {
        DynValue flows = Arr(F(-1000.0), F(300.0), F(400.0), F(500.0));
        DynValue result = invoke("irr", flows);
        double v = result.asDouble();
        assertTrue(v > 0.0 && v < 0.5, "IRR=" + v + " out of range");
    }

    @Test
    void irr_EvenCashFlows() {
        DynValue flows = Arr(F(-1000.0), F(400.0), F(400.0), F(400.0));
        DynValue result = invoke("irr", flows);
        double v = result.asDouble();
        assertTrue(v > 0.05 && v < 0.15, "IRR=" + v);
    }

    // =========================================================================
    // Depreciation — extended
    // =========================================================================

    @Test
    void depreciation_Basic() {
        // (10000 - 1000) / 5 = 1800
        assertNumeric(invoke("depreciation", F(10000.0), F(1000.0), F(5.0)), 1800.0, 0.01);
    }

    @Test
    void depreciation_NoSalvage() {
        assertNumeric(invoke("depreciation", F(5000.0), F(0.0), F(10.0)), 500.0, 0.01);
    }

    @Test
    void depreciation_ZeroLife() {
        assertTrue(invoke("depreciation", F(5000.0), F(0.0), F(0.0)).isNull());
    }

    // =========================================================================
    // Statistics: std, variance, median, mode — extended
    // =========================================================================

    @Test
    void std_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        assertNumeric(invoke("std", a), 2.0, 0.01);
    }

    @Test
    void std_Uniform() {
        assertNumeric(invoke("std", Arr(F(5.0), F(5.0), F(5.0))), 0.0, 1e-10);
    }

    @Test
    void variance_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        assertNumeric(invoke("variance", a), 4.0, 0.01);
    }

    @Test
    void median_Odd() {
        assertNumeric(invoke("median", Arr(F(3.0), F(1.0), F(2.0))), 2.0, 1e-10);
    }

    @Test
    void median_Even() {
        assertNumeric(invoke("median", Arr(F(1.0), F(2.0), F(3.0), F(4.0))), 2.5, 1e-10);
    }

    @Test
    void median_Single() {
        assertNumeric(invoke("median", Arr(F(42.0))), 42.0, 1e-10);
    }

    @Test
    void mode_Basic() {
        assertNumeric(invoke("mode", Arr(F(1.0), F(2.0), F(2.0), F(3.0))), 2.0, 1e-10);
    }

    @Test
    void mode_AllSame() {
        assertNumeric(invoke("mode", Arr(F(7.0), F(7.0), F(7.0))), 7.0, 1e-10);
    }

    // =========================================================================
    // stdSample / varianceSample — extended
    // =========================================================================

    @Test
    void stdSample_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        assertNumeric(invoke("stdSample", a), Math.sqrt(32.0 / 7.0), 0.01);
    }

    @Test
    void stdSample_TooFew() {
        assertTrue(invoke("stdSample", Arr(F(5.0))).isNull());
    }

    @Test
    void varianceSample_Basic() {
        DynValue a = Arr(F(2.0), F(4.0), F(4.0), F(4.0), F(5.0), F(5.0), F(7.0), F(9.0));
        assertNumeric(invoke("varianceSample", a), 32.0 / 7.0, 0.01);
    }

    // =========================================================================
    // percentile / quantile — extended
    // =========================================================================

    @Test
    void percentile_50th() {
        assertNumeric(invoke("percentile", Arr(F(1.0), F(2.0), F(3.0), F(4.0), F(5.0)), F(50.0)),
                3.0, 1e-10);
    }

    @Test
    void percentile_0th() {
        assertNumeric(invoke("percentile", Arr(F(1.0), F(2.0), F(3.0)), F(0.0)), 1.0, 1e-10);
    }

    @Test
    void percentile_100th() {
        assertNumeric(invoke("percentile", Arr(F(1.0), F(2.0), F(3.0)), F(100.0)), 3.0, 1e-10);
    }

    @Test
    void quantile_Half() {
        assertNumeric(invoke("quantile", Arr(F(10.0), F(20.0), F(30.0)), F(0.5)), 20.0, 1e-10);
    }

    // =========================================================================
    // covariance / correlation — extended
    // =========================================================================

    @Test
    void covariance_PerfectPositive() {
        assertNumeric(invoke("covariance",
                Arr(F(1.0), F(2.0), F(3.0)),
                Arr(F(2.0), F(4.0), F(6.0))), 4.0 / 3.0, 1e-10);
    }

    @Test
    void correlation_PerfectPositive() {
        assertNumeric(invoke("correlation",
                Arr(F(1.0), F(2.0), F(3.0)),
                Arr(F(2.0), F(4.0), F(6.0))), 1.0, 1e-10);
    }

    @Test
    void correlation_PerfectNegative() {
        assertNumeric(invoke("correlation",
                Arr(F(1.0), F(2.0), F(3.0)),
                Arr(F(6.0), F(4.0), F(2.0))), -1.0, 1e-10);
    }

    // =========================================================================
    // zscore — extended
    // =========================================================================

    @Test
    void zscore_AtMean() {
        // zscore(value, mean, stddev)
        assertNumeric(invoke("zscore", F(5.0), F(5.0), F(2.0)), 0.0, 1e-10);
    }

    @Test
    void zscore_AboveMean() {
        DynValue result = invoke("zscore", F(7.0), F(5.0), F(2.0));
        assertNumeric(result, 1.0, 1e-10);
    }

    @Test
    void zscore_BelowMean() {
        DynValue result = invoke("zscore", F(3.0), F(5.0), F(2.0));
        assertNumeric(result, -1.0, 1e-10);
    }

    @Test
    void zscore_ZeroStddev() {
        assertTrue(invoke("zscore", F(5.0), F(5.0), F(0.0)).isNull());
    }

    // =========================================================================
    // clamp / interpolate / weightedAvg — extended
    // =========================================================================

    @Test
    void clamp_WithinRange() {
        assertNumeric(invoke("clamp", F(5.0), F(1.0), F(10.0)), 5.0, 1e-10);
    }

    @Test
    void clamp_BelowMin() {
        assertNumeric(invoke("clamp", F(-5.0), F(0.0), F(10.0)), 0.0, 1e-10);
    }

    @Test
    void clamp_AboveMax() {
        assertNumeric(invoke("clamp", F(15.0), F(0.0), F(10.0)), 10.0, 1e-10);
    }

    @Test
    void interpolate_Midpoint() {
        // interpolate(a, b, t) = a + (b - a) * t
        assertNumeric(invoke("interpolate", F(0.0), F(100.0), F(0.5)), 50.0, 1e-10);
    }

    @Test
    void interpolate_AtStart() {
        assertNumeric(invoke("interpolate", F(0.0), F(100.0), F(0.0)), 0.0, 1e-10);
    }

    @Test
    void interpolate_AtEnd() {
        assertNumeric(invoke("interpolate", F(0.0), F(100.0), F(1.0)), 100.0, 1e-10);
    }

    @Test
    void weightedAvg_Basic() {
        // (80*1 + 90*2 + 100*1) / (1+2+1) = 360/4 = 90
        assertNumeric(invoke("weightedAvg",
                Arr(F(80.0), F(90.0), F(100.0)),
                Arr(F(1.0), F(2.0), F(1.0))), 90.0, 1e-10);
    }

    @Test
    void weightedAvg_EqualWeights() {
        assertNumeric(invoke("weightedAvg",
                Arr(F(10.0), F(20.0), F(30.0)),
                Arr(F(1.0), F(1.0), F(1.0))), 20.0, 1e-10);
    }

    // =========================================================================
    // mod — extended
    // =========================================================================

    @Test
    void mod_Basic() {
        assertNumeric(invoke("mod", I(10), I(3)), 1.0, 1e-10);
    }

    @Test
    void mod_NoRemainder() {
        assertNumeric(invoke("mod", I(9), I(3)), 0.0, 1e-10);
    }

    @Test
    void mod_Float() {
        assertNumeric(invoke("mod", F(10.5), F(3.0)), 1.5, 1e-10);
    }

    @Test
    void mod_ByZero() {
        assertTrue(invoke("mod", I(10), I(0)).isNull());
    }

    // =========================================================================
    // add / subtract / multiply / divide / abs / round — extended
    // =========================================================================

    @Test
    void add_Integers() {
        assertNumeric(invoke("add", I(3), I(4)), 7.0, 1e-10);
    }

    @Test
    void add_Floats() {
        assertNumeric(invoke("add", F(1.5), F(2.5)), 4.0, 1e-10);
    }

    @Test
    void add_Negative() {
        assertNumeric(invoke("add", I(5), I(-3)), 2.0, 1e-10);
    }

    @Test
    void subtract_Basic() {
        assertNumeric(invoke("subtract", I(10), I(3)), 7.0, 1e-10);
    }

    @Test
    void subtract_Negative() {
        assertNumeric(invoke("subtract", I(5), I(10)), -5.0, 1e-10);
    }

    @Test
    void multiply_Basic() {
        assertNumeric(invoke("multiply", I(3), I(4)), 12.0, 1e-10);
    }

    @Test
    void multiply_Float() {
        assertNumeric(invoke("multiply", F(2.5), F(4.0)), 10.0, 1e-10);
    }

    @Test
    void multiply_ByZero() {
        assertNumeric(invoke("multiply", I(999), I(0)), 0.0, 1e-10);
    }

    @Test
    void divide_Basic() {
        assertNumeric(invoke("divide", I(10), I(2)), 5.0, 1e-10);
    }

    @Test
    void divide_Float() {
        assertNumeric(invoke("divide", F(7.0), F(2.0)), 3.5, 1e-10);
    }

    @Test
    void divide_ByZero() {
        assertTrue(invoke("divide", I(10), I(0)).isNull());
    }

    @Test
    void abs_Positive() {
        assertNumeric(invoke("abs", I(42)), 42.0, 1e-10);
    }

    @Test
    void abs_Negative() {
        assertNumeric(invoke("abs", I(-42)), 42.0, 1e-10);
    }

    @Test
    void abs_Zero() {
        assertNumeric(invoke("abs", I(0)), 0.0, 1e-10);
    }

    @Test
    void abs_Float() {
        assertNumeric(invoke("abs", F(-3.14)), 3.14, 1e-10);
    }

    @Test
    void round_Basic() {
        assertNumeric(invoke("round", F(3.7)), 4.0, 1e-10);
    }

    @Test
    void round_Down() {
        assertNumeric(invoke("round", F(3.2)), 3.0, 1e-10);
    }

    @Test
    void round_WithDecimals() {
        assertNumeric(invoke("round", F(3.14159), I(2)), 3.14, 1e-10);
    }

    @Test
    void round_Negative() {
        assertNumeric(invoke("round", F(-2.5)), -3.0, 1e-10);
    }

    // =========================================================================
    // switch — extended
    // =========================================================================

    @Test
    void switch_MatchFirst() {
        assertEquals(S("one"), invoke("switch", I(1), I(1), S("one"), I(2), S("two")));
    }

    @Test
    void switch_MatchSecond() {
        assertEquals(S("two"), invoke("switch", I(2), I(1), S("one"), I(2), S("two")));
    }

    @Test
    void switch_Default() {
        assertEquals(S("other"), invoke("switch", I(99), I(1), S("one"), S("other")));
    }

    @Test
    void switch_NoMatchNoDefault() {
        assertTrue(invoke("switch", I(99), I(1), S("one"), I(2), S("two")).isNull());
    }

    @Test
    void switch_StringMatch() {
        assertEquals(I(1), invoke("switch", S("a"), S("a"), I(1), S("b"), I(2)));
    }

    // =========================================================================
    // random — extended
    // =========================================================================

    @Test
    void random_NoArgs_Between0And1() {
        DynValue result = invoke("random");
        double v = result.asDouble();
        assertTrue(v >= 0.0 && v <= 1.0, "random=" + v + " not in [0,1]");
    }

    @Test
    void random_WithRange() {
        DynValue result = invoke("random", I(10), I(20));
        double v = result.asDouble();
        assertTrue(v >= 10.0 && v <= 20.0, "random=" + v + " not in [10,20]");
    }

    // =========================================================================
    // Null handling edge cases
    // =========================================================================

    @Test
    void add_NullArg() {
        assertTrue(invoke("add", Null(), I(5)).isNull());
    }

    @Test
    void subtract_NullArg() {
        assertTrue(invoke("subtract", I(5), Null()).isNull());
    }

    @Test
    void multiply_NullArg() {
        assertTrue(invoke("multiply", Null(), Null()).isNull());
    }

    @Test
    void divide_NullArg() {
        assertTrue(invoke("divide", Null(), I(5)).isNull());
    }

    @Test
    void floor_NullArg() {
        assertTrue(invoke("floor", Null()).isNull());
    }

    @Test
    void ceil_NullArg() {
        assertTrue(invoke("ceil", Null()).isNull());
    }

    @Test
    void sqrt_NullArg() {
        assertTrue(invoke("sqrt", Null()).isNull());
    }

    @Test
    void log_NullArg() {
        assertTrue(invoke("log", Null(), F(2.0)).isNull());
    }

    @Test
    void ln_NullArg() {
        assertTrue(invoke("ln", Null()).isNull());
    }

    @Test
    void exp_NullArg() {
        assertTrue(invoke("exp", Null()).isNull());
    }

    @Test
    void pow_NullArg() {
        assertTrue(invoke("pow", Null(), F(2.0)).isNull());
    }

    @Test
    void negate_NullArg() {
        assertTrue(invoke("negate", Null()).isNull());
    }

    @Test
    void sign_NullArg() {
        assertTrue(invoke("sign", Null()).isNull());
    }

    @Test
    void trunc_NullArg() {
        assertTrue(invoke("trunc", Null()).isNull());
    }

    @Test
    void abs_NullArg() {
        assertTrue(invoke("abs", Null()).isNull());
    }

    @Test
    void round_NullArg() {
        assertTrue(invoke("round", Null()).isNull());
    }

    @Test
    void compound_NullArg() {
        assertTrue(invoke("compound", Null(), F(0.05), I(10)).isNull());
    }

    @Test
    void discount_NullArg() {
        assertTrue(invoke("discount", Null(), F(0.05), I(10)).isNull());
    }

    @Test
    void pmt_NullArg() {
        assertTrue(invoke("pmt", Null(), F(360.0), F(200000.0)).isNull());
    }

    @Test
    void clamp_NullArg() {
        assertTrue(invoke("clamp", Null(), F(0.0), F(10.0)).isNull());
    }

    @Test
    void interpolate_NullArg() {
        assertTrue(invoke("interpolate", Null(), F(100.0), F(0.5)).isNull());
    }

    @Test
    void depreciation_NullArg() {
        assertTrue(invoke("depreciation", Null(), F(0.0), F(5.0)).isNull());
    }
}
