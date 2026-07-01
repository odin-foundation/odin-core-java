package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import foundation.odin.utils.SecurityLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transform execution guard: fuel budget (T016), wall-clock timeout (T017), and
 * expression-depth enforcement (T018). Guards charge only when their limit is set
 * (> 0); depth uses its standing default. Aborts are not downgraded by onError and
 * surface as a failed result, never thrown past execute.
 */
class ExecutionGuardTest {

    private static final String HEADER =
            "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n";

    private static String headerWith(String onError) {
        return HEADER + "{$target}\nformat = \"json\"\nonError = \"" + onError + "\"\n\n";
    }

    private final int savedFuel = SecurityLimits.MAX_TRANSFORM_FUEL;
    private final int savedTimeout = SecurityLimits.TRANSFORM_TIMEOUT_MS;
    private final int savedDepth = SecurityLimits.MAX_EXPRESSION_DEPTH;

    @AfterEach
    void restore() {
        SecurityLimits.MAX_TRANSFORM_FUEL = savedFuel;
        SecurityLimits.TRANSFORM_TIMEOUT_MS = savedTimeout;
        SecurityLimits.MAX_EXPRESSION_DEPTH = savedDepth;
        TransformEngine.clock = System::currentTimeMillis;
    }

    private static TransformResult run(String body) {
        return run(body, DynValue.ofNull(), HEADER);
    }

    private static TransformResult run(String body, DynValue source) {
        return run(body, source, HEADER);
    }

    private static TransformResult run(String body, DynValue source, String head) {
        return TransformEngine.execute(TransformParser.parse(head + body), source);
    }

    private static TransformResult run(String body, DynValue source, TransformEngine.TransformOptions opts) {
        return TransformEngine.execute(TransformParser.parse(HEADER + body), source, opts);
    }

    // Object source binding `big` to a descending integer array of the given length.
    private static DynValue bigArray(int length) {
        var items = new ArrayList<DynValue>(length);
        for (int i = 0; i < length; i++) items.add(DynValue.ofInteger(length - i));
        var entries = new ArrayList<java.util.Map.Entry<String, DynValue>>();
        entries.add(java.util.Map.entry("big", DynValue.ofArray(items)));
        return DynValue.ofObject(entries);
    }

    // Chain of n nested unary verbs, evaluated one level per node.
    private static String nestAbs(int n) {
        return "{out}\nr = " + "%abs ".repeat(n) + "##1";
    }

    private static boolean hasErrorCode(TransformResult r, String code) {
        return r.getErrors().stream().anyMatch(e -> code.equals(e.getCode()));
    }

    private static boolean hasWarningCode(TransformResult r, String code) {
        return r.getWarnings().stream().anyMatch(w -> code.equals(w.getCode()));
    }

    @Nested
    class UnboundedWhenUnset {
        @Test
        void runsToCompletionWithAllLimitsOff() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 0;
            SecurityLimits.TRANSFORM_TIMEOUT_MS = 0;
            var r = run("{out}\nr = %upper \"hi\"");
            assertTrue(r.isSuccess(), () -> "errors: " + r.getErrors());
            assertTrue(r.getErrors().isEmpty());
        }

        @Test
        void doesNotChargeFuelWhenCapIsZero() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 0;
            var r = run("{out}\nr = %sort @big", bigArray(500));
            assertTrue(r.isSuccess(), () -> "errors: " + r.getErrors());
        }
    }

    @Nested
    class FuelBudget {
        @Test
        void abortsOverComputingTransformWithFailedResult() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 50;
            var r = run("{out}\nr = %sort @big", bigArray(200));
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T016"));
        }

        @Test
        void chargesArrayVerbsProportionalToWidth() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 50;
            var small = run("{out}\nr = %sort @big", bigArray(3));
            assertTrue(small.isSuccess(), () -> "errors: " + small.getErrors());

            var large = run("{out}\nr = %sort @big", bigArray(200));
            assertFalse(large.isSuccess());
            assertTrue(hasErrorCode(large, "T016"));
        }
    }

    @Nested
    class WallClockTimeout {
        @Test
        void abortsWhenElapsedExceedsTimeout() {
            SecurityLimits.TRANSFORM_TIMEOUT_MS = 100;
            // Each read advances well past the bound, so any read after the engine's
            // start time reports elapsed > timeout regardless of call ordering.
            long[] clock = {0};
            TransformEngine.clock = () -> (clock[0] += 10_000);
            var r = run("{out}\nr = %sort @big", bigArray(2000));
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T017"));
        }
    }

    @Nested
    class ExpressionDepth {
        @Test
        void yieldsCleanDepthErrorUnderLowCap() {
            SecurityLimits.MAX_EXPRESSION_DEPTH = 8;
            var r = run(nestAbs(30));
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T018"));
        }

        @Test
        void convertsDeepNestingIntoTypedErrorNotStackOverflow() {
            // Standing default (32); deep nesting must not throw.
            assertDoesNotThrow(() -> run(nestAbs(200)));
            var r = run(nestAbs(200));
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T018"));
        }
    }

    @Nested
    class PerCallOverrides {
        @Test
        void perCallFuelBudgetAbortsWithNoGlobalLimit() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 0;
            var opts = new TransformEngine.TransformOptions().setMaxTransformFuel(50);
            var r = run("{out}\nr = %sort @big", bigArray(200), opts);
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T016"));
        }

        @Test
        void perCallFuelOverridesGlobalLimit() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 50;
            var opts = new TransformEngine.TransformOptions().setMaxTransformFuel(1_000_000);
            var r = run("{out}\nr = %sort @big", bigArray(200), opts);
            assertTrue(r.isSuccess(), () -> "errors: " + r.getErrors());
        }

        @Test
        void perCallZeroOptsOutOfGlobalFuelCap() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 50;
            var opts = new TransformEngine.TransformOptions().setMaxTransformFuel(0);
            var r = run("{out}\nr = %sort @big", bigArray(200), opts);
            assertTrue(r.isSuccess(), () -> "errors: " + r.getErrors());
        }

        @Test
        void perCallExpressionDepthCapYieldsDepthError() {
            var opts = new TransformEngine.TransformOptions().setMaxExpressionDepth(8);
            var r = run(nestAbs(30), DynValue.ofNull(), opts);
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T018"));
        }

        @Test
        void perCallTimeoutYieldsTimeoutError() {
            long[] clock = {0};
            TransformEngine.clock = () -> (clock[0] += 10_000);
            var opts = new TransformEngine.TransformOptions().setTransformTimeoutMs(100);
            var r = run("{out}\nr = %sort @big", bigArray(2000), opts);
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T017"));
        }
    }

    @Nested
    class NonSwallowableAbort {
        @Test
        void isNotDowngradedToWarningByOnError() {
            SecurityLimits.MAX_TRANSFORM_FUEL = 50;
            var r = run("{out}\nr = %sort @big", bigArray(200), headerWith("warn"));
            assertFalse(r.isSuccess());
            assertTrue(hasErrorCode(r, "T016"));
            assertFalse(hasWarningCode(r, "T016"));
        }
    }
}
