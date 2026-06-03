package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended numeric, financial, statistics, and datetime verb tests ported from
 * .NET SDK NumericVerbExtendedTests.
 */
class NumericVerbExtendedTest {

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
    // 1. PARSE_INT edge cases
    // =========================================================================

    @Test void parseInt_Basic() { assertNumeric(invoke("parseInt", S("42")), 42.0, 1e-10); }
    @Test void parseInt_Negative() { assertNumeric(invoke("parseInt", S("-99")), -99.0, 1e-10); }

    @Test void parseInt_FromFloatString_Truncates() {
        // parseInt parses "3.14" as double then truncates to 3
        DynValue result = invoke("parseInt", S("3.14"));
        assertNumeric(result, 3.0, 1e-10);
    }

    @Test void parseInt_NonNumeric_ReturnsNull() {
        DynValue result = invoke("parseInt", S("abc"));
        assertTrue(result.isNull());
    }

    // =========================================================================
    // 2. IS_FINITE / IS_NAN extended
    // =========================================================================

    @Test void isFinite_NegativeInfinity() {
        assertEquals(false, invoke("isFinite", F(Double.NEGATIVE_INFINITY)).asBool());
    }

    // =========================================================================
    // 3. MIN_OF / MAX_OF edge cases
    // =========================================================================

    @Test void minOf_Basic() { assertNumeric(invoke("minOf", I(3), I(1), I(2)), 1.0, 1e-10); }
    @Test void minOf_Negative() { assertNumeric(invoke("minOf", F(-5.0), F(-3.0), F(-10.0)), -10.0, 1e-10); }
    @Test void minOf_Single() { assertNumeric(invoke("minOf", I(42)), 42.0, 1e-10); }
    @Test void minOf_MixedTypes() { assertNumeric(invoke("minOf", I(5), F(3.5), S("2")), 2.0, 1e-10); }

    @Test void maxOf_Basic() { assertNumeric(invoke("maxOf", I(3), I(1), I(2)), 3.0, 1e-10); }
    @Test void maxOf_Negative() { assertNumeric(invoke("maxOf", F(-5.0), F(-3.0), F(-10.0)), -3.0, 1e-10); }
    @Test void maxOf_Single() { assertNumeric(invoke("maxOf", I(42)), 42.0, 1e-10); }
    @Test void maxOf_LargeNumbers() { assertNumeric(invoke("maxOf", F(1e15), F(1e14), F(1e16)), 1e16, 1e6); }

    // =========================================================================
    // 4. SAFE_DIVIDE edge cases
    // =========================================================================

    @Test void safeDivide_Normal() { assertNumeric(invoke("safeDivide", F(10.0), F(3.0), F(0.0)), 10.0 / 3.0, 1e-10); }
    @Test void safeDivide_ByZero() { assertNumeric(invoke("safeDivide", F(10.0), F(0.0), F(-1.0)), -1.0, 1e-10); }
    @Test void safeDivide_ZeroNumerator() { assertNumeric(invoke("safeDivide", F(0.0), F(5.0), F(0.0)), 0.0, 1e-10); }
    @Test void safeDivide_MissingArgs() { assertTrue(invoke("safeDivide", F(10.0)).isNull()); }

    // =========================================================================
    // 5. MATH VERBS: LOG, LN, LOG10, EXP, POW, SQRT
    // =========================================================================

    @Test void log_Base2() { assertNumeric(invoke("log", F(8.0), F(2.0)), 3.0, 1e-10); }
    @Test void log_Base10() { assertNumeric(invoke("log", F(1000.0), F(10.0)), 3.0, 1e-10); }

    @Test void ln_E() { assertNumeric(invoke("ln", F(Math.E)), 1.0, 1e-10); }
    @Test void ln_One() { assertNumeric(invoke("ln", F(1.0)), 0.0, 1e-10); }

    @Test void log10_Hundred() { assertNumeric(invoke("log10", F(100.0)), 2.0, 1e-10); }

    @Test void exp_Zero() { assertNumeric(invoke("exp", F(0.0)), 1.0, 1e-10); }
    @Test void exp_One() { assertNumeric(invoke("exp", F(1.0)), Math.E, 1e-10); }

    @Test void pow_Basic() { assertNumeric(invoke("pow", F(2.0), F(10.0)), 1024.0, 1e-10); }
    @Test void pow_Fractional() { assertNumeric(invoke("pow", F(4.0), F(0.5)), 2.0, 1e-10); }
    @Test void pow_ZeroExponent() { assertNumeric(invoke("pow", F(99.0), F(0.0)), 1.0, 1e-10); }

    @Test void sqrt_Perfect() { assertNumeric(invoke("sqrt", F(144.0)), 12.0, 1e-10); }
    @Test void sqrt_Zero() { assertNumeric(invoke("sqrt", F(0.0)), 0.0, 1e-10); }
    @Test void sqrt_NonPerfect() { assertNumeric(invoke("sqrt", F(2.0)), Math.sqrt(2.0), 1e-10); }

    // =========================================================================
    // 6. FINANCIAL: COMPOUND / DISCOUNT
    // =========================================================================

    @Test void compound_Basic() {
        assertNumeric(invoke("compound", F(1000.0), F(0.05), I(10)),
                1000.0 * Math.pow(1.05, 10), 0.01);
    }

    @Test void compound_ZeroRate() {
        assertNumeric(invoke("compound", F(1000.0), F(0.0), I(10)), 1000.0, 0.01);
    }

    @Test void compound_OnePeriod() {
        assertNumeric(invoke("compound", F(500.0), F(0.10), I(1)), 550.0, 0.01);
    }

    @Test void compound_LargePeriods() {
        assertNumeric(invoke("compound", F(100.0), F(0.07), I(30)),
                100.0 * Math.pow(1.07, 30), 0.01);
    }

    @Test void compound_NegativeRate() {
        assertNumeric(invoke("compound", F(1000.0), F(-0.02), I(5)),
                1000.0 * Math.pow(0.98, 5), 0.01);
    }

    @Test void compound_MissingArgs() { assertTrue(invoke("compound", F(100.0), F(0.05)).isNull()); }
    @Test void compound_NullRate() { assertTrue(invoke("compound", F(1000.0), Null(), I(10)).isNull()); }

    @Test void discount_Basic() {
        assertNumeric(invoke("discount", F(1000.0), F(0.05), I(10)),
                1000.0 / Math.pow(1.05, 10), 0.01);
    }

    @Test void discount_ZeroRate() {
        assertNumeric(invoke("discount", F(1000.0), F(0.0), I(5)), 1000.0, 0.01);
    }

    @Test void discount_OnePeriod() {
        assertNumeric(invoke("discount", F(1100.0), F(0.10), I(1)), 1000.0, 0.01);
    }

    @Test void discount_LargePeriods() {
        assertNumeric(invoke("discount", F(1000000.0), F(0.08), I(50)),
                1000000.0 / Math.pow(1.08, 50), 1.0);
    }

    @Test void discount_MissingArgs() { assertTrue(invoke("discount", F(100.0)).isNull()); }

    // =========================================================================
    // 7. PMT / FV / PV
    // =========================================================================

    @Test void pmt_Basic() {
        double rate = 0.05 / 12.0;
        double nper = 360.0;
        double pv = 200000.0;
        double factor = Math.pow(1.0 + rate, nper);
        double expected = pv * rate * factor / (factor - 1.0);
        assertNumeric(invoke("pmt", F(pv), F(rate), F(nper)), expected, 1.0);
    }

    @Test void pmt_ZeroRate() {
        assertNumeric(invoke("pmt", F(12000.0), F(0.0), F(12.0)), 1000.0, 0.01);
    }

    @Test void pmt_OnePeriod() {
        assertNumeric(invoke("pmt", F(1000.0), F(0.1), F(1.0)), 1100.0, 0.01);
    }

    @Test void pmt_HighRate() {
        double rate = 0.02;
        double n = 60.0;
        double pv = 100000.0;
        double factor = Math.pow(1.0 + rate, n);
        double expected = pv * rate * factor / (factor - 1.0);
        assertNumeric(invoke("pmt", F(pv), F(rate), F(n)), expected, 0.01);
    }

    @Test void pmt_MissingArgs() { assertTrue(invoke("pmt", F(0.05), F(360.0)).isNull()); }
    @Test void pmt_NullPrincipal() { assertTrue(invoke("pmt", Null(), F(0.05), F(360.0)).isNull()); }

    @Test void fv_Basic() {
        double expected = 100.0 * (Math.pow(1.005, 120.0) - 1.0) / 0.005;
        assertNumeric(invoke("fv", F(100.0), F(0.005), F(120.0)), expected, 0.01);
    }

    @Test void fv_ZeroRate() {
        assertNumeric(invoke("fv", F(100.0), F(0.0), F(12.0)), 1200.0, 0.01);
    }

    @Test void fv_LargeN() {
        double expected = 50.0 * (Math.pow(1.005, 480.0) - 1.0) / 0.005;
        assertNumeric(invoke("fv", F(50.0), F(0.005), F(480.0)), expected, 1.0);
    }

    @Test void fv_MissingArgs() { assertTrue(invoke("fv", F(0.05), F(12.0)).isNull()); }

    @Test void pv_Basic() {
        double expected = 100.0 * (1.0 - Math.pow(1.005, -120.0)) / 0.005;
        assertNumeric(invoke("pv", F(100.0), F(0.005), F(120.0)), expected, 0.01);
    }

    @Test void pv_ZeroRate() {
        assertNumeric(invoke("pv", F(1200.0), F(0.0), F(12.0)), 1200.0 * 12.0, 0.01);
    }

    @Test void pv_MissingArgs() { assertTrue(invoke("pv", F(0.05)).isNull()); }

    // =========================================================================
    // 8. NPV
    // =========================================================================

    @Test void npv_Basic() {
        DynValue flows = Arr(F(-1000.0), F(300.0), F(400.0), F(500.0));
        double expected = -1000.0 + 300.0 / 1.1 + 400.0 / Math.pow(1.1, 2) + 500.0 / Math.pow(1.1, 3);
        assertNumeric(invoke("npv", F(0.1), flows), expected, 1.0);
    }

    @Test void npv_ZeroRate() {
        DynValue flows = Arr(F(-1000.0), F(500.0), F(500.0));
        assertNumeric(invoke("npv", F(0.0), flows), 0.0, 0.01);
    }

    @Test void npv_SingleFlow() {
        DynValue flows = Arr(F(1000.0));
        assertNumeric(invoke("npv", F(0.1), flows), 1000.0, 0.01);
    }

    @Test void npv_HighRate() {
        DynValue flows = Arr(F(-100.0), F(50.0), F(50.0), F(50.0));
        double expected = -100.0 + 50.0 / 1.5 + 50.0 / Math.pow(1.5, 2) + 50.0 / Math.pow(1.5, 3);
        assertNumeric(invoke("npv", F(0.5), flows), expected, 0.01);
    }

    @Test void npv_NegativeFlows() {
        DynValue flows = Arr(F(1000.0), F(-300.0), F(-300.0), F(-300.0));
        double expected = 1000.0 - 300.0 / 1.05 - 300.0 / Math.pow(1.05, 2) - 300.0 / Math.pow(1.05, 3);
        assertNumeric(invoke("npv", F(0.05), flows), expected, 1.0);
    }

    @Test void npv_ManyCashFlows() {
        List<DynValue> items = new ArrayList<>();
        items.add(F(-10000.0));
        for (int j = 0; j < 20; j++) items.add(F(1000.0));
        DynValue flows = DynValue.ofArray(items);
        DynValue result = invoke("npv", F(0.08), flows);
        assertTrue(result.asDouble() != null || result.asInt64() != null,
                "NPV should return numeric");
    }

    @Test void npv_MissingArgs() { assertTrue(invoke("npv", F(0.1)).isNull()); }
    @Test void npv_NullRate() { assertTrue(invoke("npv", Null(), Arr(F(-1000.0), F(500.0))).isNull()); }

    // =========================================================================
    // 9. IRR
    // =========================================================================

    @Test void irr_Basic() {
        DynValue flows = Arr(F(-1000.0), F(300.0), F(400.0), F(500.0));
        DynValue result = invoke("irr", flows);
        Double v = result.asDouble();
        assertTrue(v != null, "IRR should return a float");
        assertTrue(v > 0.0 && v < 0.5, "IRR=" + v + " out of reasonable range");
    }

    @Test void irr_Simple() {
        DynValue flows = Arr(F(-100.0), F(110.0));
        assertNumeric(invoke("irr", flows), 0.10, 0.001);
    }

    @Test void irr_EvenCashFlows() {
        DynValue flows = Arr(F(-1000.0), F(400.0), F(400.0), F(400.0));
        DynValue result = invoke("irr", flows);
        Double v = result.asDouble();
        assertTrue(v != null, "IRR should return a float");
        assertTrue(v > 0.05 && v < 0.15, "IRR=" + v);
    }

    @Test void irr_TooFewFlows() {
        DynValue flows = Arr(F(-100.0));
        DynValue result = invoke("irr", flows);
        assertTrue(result.asDouble() != null || result.asInt64() != null,
                "IRR with single flow should still return a numeric value");
    }

    // =========================================================================
    // 10. RATE / NPER / DEPRECIATION
    // =========================================================================

    @Test void rate_Basic() {
        DynValue result = invoke("rate", F(10.0), F(-100.0), F(1000.0), F(0.0));
        assertTrue(result.asDouble() != null, "rate should return a float");
        assertTrue(Double.isFinite(result.asDouble()), "rate should be finite");
    }

    @Test void rate_ZeroPmtGrowth() {
        assertNumeric(invoke("rate", F(10.0), F(0.0), F(-1000.0), F(2000.0)), 0.0718, 0.01);
    }

    @Test void rate_MissingArgs() { assertTrue(invoke("rate", F(10.0), F(-100.0)).isNull()); }

    @Test void nper_Basic() {
        DynValue result = invoke("nper", F(0.01), F(-100.0), F(5000.0));
        Double v = result.asDouble();
        assertTrue(v != null, "nper should return float");
        assertTrue(v > 0.0 && v < 200.0, "nper=" + v);
    }

    @Test void nper_ZeroRate() {
        assertNumeric(invoke("nper", F(0.0), F(-100.0), F(5000.0)), 50.0, 0.01);
    }

    @Test void nper_MissingArgs() { assertTrue(invoke("nper", F(0.01), F(-100.0)).isNull()); }

    @Test void depreciation_Basic() {
        assertNumeric(invoke("depreciation", F(10000.0), F(1000.0), F(5.0)), 1800.0, 0.01);
    }

    @Test void depreciation_NoSalvage() {
        assertNumeric(invoke("depreciation", F(5000.0), F(0.0), F(10.0)), 500.0, 0.01);
    }

    @Test void depreciation_ZeroLife() {
        assertTrue(invoke("depreciation", F(5000.0), F(0.0), F(0.0)).isNull());
    }

    @Test void depreciation_EqualCostSalvage() {
        assertNumeric(invoke("depreciation", F(5000.0), F(5000.0), F(10.0)), 0.0, 1e-10);
    }

    @Test void depreciation_MissingArgs() {
        assertTrue(invoke("depreciation", F(5000.0), F(0.0)).isNull());
    }

    // =========================================================================
    // 11. STATISTICS: STD, VARIANCE, MEDIAN, MODE
    // =========================================================================

    @Test void std_Basic() {
        assertNumeric(invoke("std", Arr(F(2), F(4), F(4), F(4), F(5), F(5), F(7), F(9))), 2.0, 0.01);
    }

    @Test void std_Uniform() {
        assertNumeric(invoke("std", Arr(F(5), F(5), F(5))), 0.0, 1e-10);
    }

    @Test void variance_Basic() {
        assertNumeric(invoke("variance", Arr(F(2), F(4), F(4), F(4), F(5), F(5), F(7), F(9))), 4.0, 0.01);
    }

    @Test void median_Odd() {
        assertNumeric(invoke("median", Arr(F(3), F(1), F(2))), 2.0, 1e-10);
    }

    @Test void median_Even() {
        assertNumeric(invoke("median", Arr(F(1), F(2), F(3), F(4))), 2.5, 1e-10);
    }

    @Test void median_Single() {
        assertNumeric(invoke("median", Arr(F(42))), 42.0, 1e-10);
    }

    @Test void mode_Basic() {
        assertNumeric(invoke("mode", Arr(F(1), F(2), F(2), F(3))), 2.0, 1e-10);
    }

    @Test void mode_AllSame() {
        assertNumeric(invoke("mode", Arr(F(7), F(7), F(7))), 7.0, 1e-10);
    }

    // =========================================================================
    // 12. CLAMP / INTERPOLATE / WEIGHTED_AVG
    // =========================================================================

    @Test void clamp_WithinRange() { assertNumeric(invoke("clamp", F(5.0), F(1.0), F(10.0)), 5.0, 1e-10); }
    @Test void clamp_BelowMin() { assertNumeric(invoke("clamp", F(-5.0), F(0.0), F(10.0)), 0.0, 1e-10); }
    @Test void clamp_AboveMax() { assertNumeric(invoke("clamp", F(15.0), F(0.0), F(10.0)), 10.0, 1e-10); }

    @Test void interpolate_Midpoint() {
        assertNumeric(invoke("interpolate", F(5.0), F(0.0), F(100.0), F(10.0), F(200.0)), 150.0, 1e-10);
    }

    @Test void interpolate_AtStart() {
        assertNumeric(invoke("interpolate", F(0.0), F(0.0), F(100.0), F(10.0), F(200.0)), 100.0, 1e-10);
    }

    @Test void interpolate_AtEnd() {
        assertNumeric(invoke("interpolate", F(10.0), F(0.0), F(100.0), F(10.0), F(200.0)), 200.0, 1e-10);
    }

    @Test void interpolate_MissingArgs() { assertTrue(invoke("interpolate", F(0.0), F(100.0)).isNull()); }

    @Test void weightedAvg_Basic() {
        DynValue vals = Arr(F(80.0), F(90.0), F(100.0));
        DynValue wts = Arr(F(1.0), F(2.0), F(1.0));
        assertNumeric(invoke("weightedAvg", vals, wts), 90.0, 1e-10);
    }

    @Test void weightedAvg_EqualWeights() {
        DynValue vals = Arr(F(10.0), F(20.0), F(30.0));
        DynValue wts = Arr(F(1.0), F(1.0), F(1.0));
        assertNumeric(invoke("weightedAvg", vals, wts), 20.0, 1e-10);
    }

    // =========================================================================
    // 13. MOD
    // =========================================================================

    @Test void mod_Basic() { assertNumeric(invoke("mod", I(10), I(3)), 1.0, 1e-10); }
    @Test void mod_NoRemainder() { assertNumeric(invoke("mod", I(9), I(3)), 0.0, 1e-10); }
    @Test void mod_Float() { assertNumeric(invoke("mod", F(10.5), F(3.0)), 1.5, 1e-10); }
    @Test void mod_ByZero() { assertTrue(invoke("mod", I(10), I(0)).isNull()); }

    // =========================================================================
    // 14. PERCENTILE / QUANTILE
    // =========================================================================

    @Test void percentile_50th() {
        assertNumeric(invoke("percentile", Arr(F(1), F(2), F(3), F(4), F(5)), F(50.0)), 3.0, 1e-10);
    }

    @Test void percentile_0th() {
        assertNumeric(invoke("percentile", Arr(F(1), F(2), F(3)), F(0.0)), 1.0, 1e-10);
    }

    @Test void percentile_100th() {
        assertNumeric(invoke("percentile", Arr(F(1), F(2), F(3)), F(100.0)), 3.0, 1e-10);
    }

    @Test void quantile_Half() {
        assertNumeric(invoke("quantile", Arr(F(10), F(20), F(30)), F(0.5)), 20.0, 1e-10);
    }

    // =========================================================================
    // 15. COVARIANCE / CORRELATION
    // =========================================================================

    @Test void covariance_PerfectPositive() {
        assertNumeric(invoke("covariance",
                Arr(F(1), F(2), F(3)),
                Arr(F(2), F(4), F(6))),
                4.0 / 3.0, 1e-10);
    }

    @Test void correlation_PerfectPositive() {
        assertNumeric(invoke("correlation",
                Arr(F(1), F(2), F(3)),
                Arr(F(2), F(4), F(6))),
                1.0, 1e-10);
    }

    @Test void correlation_PerfectNegative() {
        assertNumeric(invoke("correlation",
                Arr(F(1), F(2), F(3)),
                Arr(F(6), F(4), F(2))),
                -1.0, 1e-10);
    }

    // =========================================================================
    // 16. STD_SAMPLE / VARIANCE_SAMPLE
    // =========================================================================

    @Test void stdSample_Basic() {
        assertNumeric(invoke("stdSample", Arr(F(2), F(4), F(4), F(4), F(5), F(5), F(7), F(9))),
                Math.sqrt(32.0 / 7.0), 0.01);
    }

    @Test void stdSample_TooFew() {
        assertTrue(invoke("stdSample", Arr(F(5))).isNull());
    }

    @Test void varianceSample_Basic() {
        assertNumeric(invoke("varianceSample", Arr(F(2), F(4), F(4), F(4), F(5), F(5), F(7), F(9))),
                32.0 / 7.0, 0.01);
    }

    // =========================================================================
    // 17. ZSCORE
    // =========================================================================

    @Test void zscore_AtMean() {
        assertNumeric(invoke("zscore", F(3.0), Arr(F(1.0), F(3.0), F(5.0))), 0.0, 1e-10);
    }

    @Test void zscore_AboveMean() {
        // zscore(value=5, dataset=[1,3,5])
        DynValue result = invoke("zscore", F(5.0), Arr(F(1.0), F(3.0), F(5.0)));
        assertNumeric(result, 2.0 / Math.sqrt(8.0 / 3.0), 1e-10);
    }

    @Test void zscore_AllSame() {
        assertTrue(invoke("zscore", F(5.0), Arr(F(5.0), F(5.0), F(5.0))).isNull());
    }

    // =========================================================================
    // 18. NUMERIC EDGE CASES
    // =========================================================================

    @Test void formatNumber_VerySmall() {
        assertEquals("0.000001", invoke("formatNumber", F(0.000001), I(6)).asString());
    }

    @Test void formatNumber_VeryLarge() {
        String result = invoke("formatNumber", F(1e15), I(0)).asString();
        assertFalse(result == null || result.isEmpty(), "Expected non-empty string");
    }

    @Test void sign_VeryLarge() { assertNumeric(invoke("sign", F(1e300)), 1.0, 1e-10); }
    @Test void sign_VerySmallPositive() { assertNumeric(invoke("sign", F(1e-300)), 1.0, 1e-10); }
    @Test void trunc_VeryLarge() { assertNumeric(invoke("trunc", F(1e15 + 0.5)), 1e15, 1.0); }
    @Test void floor_VerySmallNegative() { assertNumeric(invoke("floor", F(-0.0001)), -1.0, 1e-10); }
    @Test void ceil_VerySmallPositive() { assertNumeric(invoke("ceil", F(0.0001)), 1.0, 1e-10); }

    // =========================================================================
    // 19. NULL / ERROR INPUT HANDLING
    // =========================================================================

    @Test void formatNumber_NullInput() { assertTrue(invoke("formatNumber", Null(), I(2)).isNull()); }
    @Test void floor_NullInput() { assertNumeric(invoke("floor", Null()), 0.0, 1e-10); }
    @Test void ceil_NullInput() { assertNumeric(invoke("ceil", Null()), 0.0, 1e-10); }

    // =========================================================================
    // 20. DATETIME: FORMAT_DATE / FORMAT_TIME
    // =========================================================================

    @Test void formatDate_YyyyMmDd() {
        assertEquals("2024-06-15", invoke("formatDate", S("2024-06-15"), S("YYYY-MM-DD")).asString());
    }

    @Test void formatDate_MmDdYyyy() {
        assertEquals("06/15/2024", invoke("formatDate", S("2024-06-15"), S("MM/DD/YYYY")).asString());
    }

    @Test void formatDate_DdMmYyyy() {
        assertEquals("15-06-2024", invoke("formatDate", S("2024-06-15"), S("DD-MM-YYYY")).asString());
    }

    @Test void formatDate_FromTimestamp() {
        assertEquals("2024-06-15", invoke("formatDate", S("2024-06-15T14:30:00"), S("YYYY-MM-DD")).asString());
    }

    @Test void formatTime_HhMmSs() {
        assertEquals("14:30:45", invoke("formatTime", S("2024-06-15T14:30:45"), S("HH:mm:ss")).asString());
    }

    @Test void formatTime_HhMm() {
        assertEquals("09:05", invoke("formatTime", S("2024-06-15T09:05:00"), S("HH:mm")).asString());
    }

    @Test void formatTime_Midnight() {
        assertEquals("00:00:00", invoke("formatTime", S("2024-06-15T00:00:00"), S("HH:mm:ss")).asString());
    }

    @Test void formatTime_EndOfDay() {
        assertEquals("23:59:59", invoke("formatTime", S("2024-06-15T23:59:59"), S("HH:mm:ss")).asString());
    }

    // =========================================================================
    // 21. DATETIME: PARSE_DATE
    // =========================================================================

    @Test void parseDate_Basic() {
        assertEquals("2024-06-15", invoke("parseDate", S("06/15/2024"), S("MM/DD/YYYY")).asString());
    }

    @Test void parseDate_European() {
        assertEquals("2024-06-15", invoke("parseDate", S("15-06-2024"), S("DD-MM-YYYY")).asString());
    }

    // =========================================================================
    // 22. DATETIME: ADD MONTHS / YEARS
    // =========================================================================

    @Test void addMonths_Basic() {
        assertEquals("2024-04-15", invoke("addMonths", S("2024-01-15"), I(3)).asString());
    }

    @Test void addMonths_YearBoundary() {
        assertEquals("2025-02-15", invoke("addMonths", S("2024-11-15"), I(3)).asString());
    }

    @Test void addMonths_Negative() {
        assertEquals("2023-12-15", invoke("addMonths", S("2024-03-15"), I(-3)).asString());
    }

    @Test void addMonths_JanToFebClamp() {
        assertEquals("2024-02-29", invoke("addMonths", S("2024-01-31"), I(1)).asString());
    }

    @Test void addMonths_JanToFebNonLeap() {
        assertEquals("2023-02-28", invoke("addMonths", S("2023-01-31"), I(1)).asString());
    }

    @Test void addMonths_Twelve() {
        assertEquals("2025-06-15", invoke("addMonths", S("2024-06-15"), I(12)).asString());
    }

    @Test void addMonths_Zero() {
        assertEquals("2024-06-15", invoke("addMonths", S("2024-06-15"), I(0)).asString());
    }

    @Test void addMonths_LargeNegative() {
        assertEquals("2022-06-15", invoke("addMonths", S("2024-06-15"), I(-24)).asString());
    }

    @Test void addMonths_InvalidDate() {
        assertTrue(invoke("addMonths", S("not-a-date"), I(1)).isNull());
    }

    @Test void addYears_Basic() {
        assertEquals("2029-06-15", invoke("addYears", S("2024-06-15"), I(5)).asString());
    }

    @Test void addYears_Negative() {
        assertEquals("2021-06-15", invoke("addYears", S("2024-06-15"), I(-3)).asString());
    }

    @Test void addYears_LeapDay() {
        assertEquals("2025-02-28", invoke("addYears", S("2024-02-29"), I(1)).asString());
    }

    @Test void addYears_LeapDayToLeap() {
        assertEquals("2028-02-29", invoke("addYears", S("2024-02-29"), I(4)).asString());
    }

    @Test void addYears_Zero() {
        assertEquals("2024-06-15", invoke("addYears", S("2024-06-15"), I(0)).asString());
    }

    @Test void addYears_InvalidDate() {
        assertTrue(invoke("addYears", S("not-a-date"), I(1)).isNull());
    }

    // =========================================================================
    // 23. DATETIME: START/END OF DAY/MONTH/YEAR
    // =========================================================================

    @Test void startOfDay_Basic() {
        String result = invoke("startOfDay", S("2024-06-15T14:30:45")).asString();
        assertNotNull(result);
        assertTrue(result.contains("2024-06-15"));
        assertTrue(result.contains("00:00:00"));
    }

    @Test void endOfDay_Basic() {
        String result = invoke("endOfDay", S("2024-06-15T14:30:45")).asString();
        assertNotNull(result);
        assertTrue(result.contains("2024-06-15"));
        assertTrue(result.contains("23:59:59"));
    }

    @Test void startOfDay_FromDateOnly() {
        String result = invoke("startOfDay", S("2024-06-15")).asString();
        assertNotNull(result);
        assertTrue(result.contains("2024-06-15"));
        assertTrue(result.contains("00:00:00"));
    }

    @Test void endOfDay_FromDateOnly() {
        String result = invoke("endOfDay", S("2024-06-15")).asString();
        assertNotNull(result);
        assertTrue(result.contains("2024-06-15"));
        assertTrue(result.contains("23:59:59"));
    }

    @Test void startOfMonth_Basic() {
        assertEquals("2024-06-01", invoke("startOfMonth", S("2024-06-15")).asString());
    }

    @Test void endOfMonth_June() {
        assertEquals("2024-06-30", invoke("endOfMonth", S("2024-06-15")).asString());
    }

    @Test void endOfMonth_FebLeap() {
        assertEquals("2024-02-29", invoke("endOfMonth", S("2024-02-10")).asString());
    }

    @Test void endOfMonth_FebNonLeap() {
        assertEquals("2023-02-28", invoke("endOfMonth", S("2023-02-10")).asString());
    }

    @Test void endOfMonth_December() {
        assertEquals("2024-12-31", invoke("endOfMonth", S("2024-12-05")).asString());
    }

    @Test void endOfMonth_January() {
        assertEquals("2024-01-31", invoke("endOfMonth", S("2024-01-15")).asString());
    }

    @Test void endOfMonth_March() {
        assertEquals("2024-03-31", invoke("endOfMonth", S("2024-03-01")).asString());
    }

    @Test void endOfMonth_April() {
        assertEquals("2024-04-30", invoke("endOfMonth", S("2024-04-01")).asString());
    }

    @Test void startOfYear_Basic() {
        assertEquals("2024-01-01", invoke("startOfYear", S("2024-06-15")).asString());
    }

    @Test void endOfYear_Basic() {
        assertEquals("2024-12-31", invoke("endOfYear", S("2024-06-15")).asString());
    }

    // =========================================================================
    // 24. DATETIME: DAY_OF_WEEK / WEEK_OF_YEAR / QUARTER
    // =========================================================================

    @Test void dayOfWeek_Monday() {
        // 2024-01-01 is a Monday = 1
        assertNumeric(invoke("dayOfWeek", S("2024-01-01")), 1.0, 1e-10);
    }

    @Test void dayOfWeek_Sunday() {
        assertNumeric(invoke("dayOfWeek", S("2024-01-07")), 7.0, 1e-10);
    }

    @Test void dayOfWeek_Saturday() {
        assertNumeric(invoke("dayOfWeek", S("2024-01-06")), 6.0, 1e-10);
    }

    @Test void dayOfWeek_InvalidDate() {
        assertTrue(invoke("dayOfWeek", S("invalid")).isNull());
    }

    @Test void weekOfYear_Jan1() {
        assertNumeric(invoke("weekOfYear", S("2024-01-01")), 1.0, 1e-10);
    }

    @Test void weekOfYear_Dec31() {
        DynValue result = invoke("weekOfYear", S("2024-12-31"));
        Double d = result.asDouble();
        Long i = result.asInt64();
        double v = d != null ? d : (i != null ? (double) i : -1);
        assertEquals(1.0, v, "week=" + v);
    }

    @Test void quarter_Q1() {
        assertNumeric(invoke("quarter", S("2024-02-15")), 1.0, 1e-10);
    }

    @Test void quarter_Q2() {
        assertNumeric(invoke("quarter", S("2024-06-15")), 2.0, 1e-10);
    }

    @Test void quarter_Q3() {
        assertNumeric(invoke("quarter", S("2024-09-15")), 3.0, 1e-10);
    }

    @Test void quarter_Q4() {
        assertNumeric(invoke("quarter", S("2024-12-15")), 4.0, 1e-10);
    }

    @Test void quarter_InvalidDate() {
        assertTrue(invoke("quarter", S("bad-date")).isNull());
    }

    // =========================================================================
    // 25. IS_LEAP_YEAR
    // =========================================================================

    @Test void isLeapYear_2024() { assertEquals(true, invoke("isLeapYear", S("2024-01-01")).asBool()); }
    @Test void isLeapYear_2023() { assertEquals(false, invoke("isLeapYear", S("2023-06-01")).asBool()); }
    @Test void isLeapYear_2000() { assertEquals(true, invoke("isLeapYear", S("2000-01-01")).asBool()); }
    @Test void isLeapYear_1900() { assertEquals(false, invoke("isLeapYear", S("1900-01-01")).asBool()); }

    // =========================================================================
    // 26. DATE COMPARISON: IS_BEFORE / IS_AFTER / IS_BETWEEN
    // =========================================================================

    @Test void isBefore_True() { assertEquals(true, invoke("isBefore", S("2024-01-01"), S("2024-12-31")).asBool()); }
    @Test void isBefore_False() { assertEquals(false, invoke("isBefore", S("2024-12-31"), S("2024-01-01")).asBool()); }
    @Test void isBefore_Equal() { assertEquals(false, invoke("isBefore", S("2024-06-15"), S("2024-06-15")).asBool()); }
    @Test void isBefore_Timestamps() { assertEquals(true, invoke("isBefore", S("2024-06-15T10:00:00"), S("2024-06-15T14:00:00")).asBool()); }

    @Test void isAfter_True() { assertEquals(true, invoke("isAfter", S("2024-12-31"), S("2024-01-01")).asBool()); }
    @Test void isAfter_False() { assertEquals(false, invoke("isAfter", S("2024-01-01"), S("2024-12-31")).asBool()); }
    @Test void isAfter_Equal() { assertEquals(false, invoke("isAfter", S("2024-06-15"), S("2024-06-15")).asBool()); }
    @Test void isAfter_Timestamps() { assertEquals(true, invoke("isAfter", S("2024-06-15T14:00:00"), S("2024-06-15T10:00:00")).asBool()); }

    @Test void isBetween_True() { assertEquals(true, invoke("isBetween", S("2024-06-15"), S("2024-01-01"), S("2024-12-31")).asBool()); }
    @Test void isBetween_FalseBefore() { assertEquals(false, invoke("isBetween", S("2023-06-15"), S("2024-01-01"), S("2024-12-31")).asBool()); }
    @Test void isBetween_FalseAfter() { assertEquals(false, invoke("isBetween", S("2025-06-15"), S("2024-01-01"), S("2024-12-31")).asBool()); }
    @Test void isBetween_Timestamps() {
        assertEquals(true, invoke("isBetween",
                S("2024-06-15T12:00:00"),
                S("2024-06-15T00:00:00"),
                S("2024-06-15T23:59:59")).asBool());
    }

    // =========================================================================
    // 27. DAYS_BETWEEN_DATES / DATE_DIFF
    // =========================================================================

    @Test void daysBetween_SameDate() {
        assertNumeric(invoke("daysBetweenDates", S("2024-06-15"), S("2024-06-15")), 0.0, 1e-10);
    }

    @Test void daysBetween_OneDay() {
        assertNumeric(invoke("daysBetweenDates", S("2024-06-15"), S("2024-06-16")), 1.0, 1e-10);
    }

    @Test void daysBetween_ReversedOrder() {
        assertNumeric(invoke("daysBetweenDates", S("2024-06-16"), S("2024-06-15")), -1.0, 1e-10);
    }

    @Test void daysBetween_LeapYear() {
        assertNumeric(invoke("daysBetweenDates", S("2024-02-28"), S("2024-03-01")), 2.0, 1e-10);
    }

    @Test void daysBetween_NonLeapYear() {
        assertNumeric(invoke("daysBetweenDates", S("2023-02-28"), S("2023-03-01")), 1.0, 1e-10);
    }

    @Test void daysBetween_YearBoundary() {
        assertNumeric(invoke("daysBetweenDates", S("2023-12-31"), S("2024-01-01")), 1.0, 1e-10);
    }

    @Test void daysBetween_FullLeapYear() {
        assertNumeric(invoke("daysBetweenDates", S("2024-01-01"), S("2025-01-01")), 366.0, 1e-10);
    }

    @Test void daysBetween_FullNonLeapYear() {
        assertNumeric(invoke("daysBetweenDates", S("2023-01-01"), S("2024-01-01")), 365.0, 1e-10);
    }

    @Test void dateDiff_Days() {
        DynValue result = invoke("dateDiff", S("2024-01-01"), S("2024-01-31"));
        assertEquals(30L, result.asInt64());
    }

    @Test void dateDiff_Months() {
        DynValue result = invoke("dateDiff", S("2024-01-15"), S("2024-04-15"));
        Long v = result.asInt64();
        assertNotNull(v);
        assertTrue(v > 80 && v < 100, "dateDiff=" + v);
    }

    @Test void dateDiff_Years() {
        DynValue result = invoke("dateDiff", S("2020-06-15"), S("2024-06-15"));
        Long v = result.asInt64();
        assertNotNull(v);
        assertTrue(v > 1400 && v < 1470, "dateDiff=" + v);
    }

    // =========================================================================
    // 28. RANDOM
    // =========================================================================

    @Test void random_NoArgs() {
        DynValue result = invoke("random");
        Double v = result.asDouble();
        assertTrue(v != null, "random should return float");
        assertTrue(v >= 0.0 && v < 1.0, "random=" + v);
    }

    @Test void random_WithMax() {
        DynValue result = invoke("random", I(100));
        Double d = result.asDouble();
        Long i = result.asInt64();
        // random(N) returns int in [0, N] inclusive — match implementation in NumericVerbs.
        if (d != null)
            assertTrue(d >= 0.0 && d <= 100.0, "random=" + d);
        else if (i != null)
            assertTrue(i >= 0 && i <= 100, "random=" + i);
        else
            fail("Expected numeric result");
    }
}
