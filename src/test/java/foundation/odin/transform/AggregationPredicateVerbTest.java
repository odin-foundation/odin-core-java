package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for conditional aggregation verbs: countIf, sumIf, and avgIf.
 */
class AggregationPredicateVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue Arr(DynValue... items) {
        return DynValue.ofArray(new ArrayList<>(List.of(items)));
    }
    @SafeVarargs
    private static DynValue Obj(Map.Entry<String, DynValue>... entries) {
        return DynValue.ofObject(new ArrayList<>(List.of(entries)));
    }
    private static Map.Entry<String, DynValue> e(String k, DynValue v) {
        return Map.entry(k, v);
    }

    private DynValue orders() {
        return Arr(
                Obj(e("status", S("paid")), e("amount", I(100))),
                Obj(e("status", S("open")), e("amount", I(200))),
                Obj(e("status", S("paid")), e("amount", I(300))));
    }

    // =========================================================================
    // countIf
    // =========================================================================

    @Nested
    class CountIf {
        @Test
        void countsMatches() {
            assertEquals(2L, invoke("countIf", orders(), S("status"), S("="), S("paid")).asInt64());
        }

        @Test
        void noMatchYieldsZero() {
            assertEquals(0L, invoke("countIf", orders(), S("status"), S("="), S("void")).asInt64());
        }

        @Test
        void comparisonOperator() {
            assertEquals(2L, invoke("countIf", orders(), S("amount"), S(">="), I(200)).asInt64());
        }
    }

    // =========================================================================
    // sumIf
    // =========================================================================

    @Nested
    class SumIf {
        @Test
        void sumsExplicitField() {
            assertEquals(400L, invoke("sumIf", orders(), S("status"), S("="), S("paid"), S("amount")).asInt64());
        }

        @Test
        void noMatchYieldsZero() {
            assertEquals(0L, invoke("sumIf", orders(), S("status"), S("="), S("void"), S("amount")).asInt64());
        }

        @Test
        void defaultsToPredicateField() {
            assertEquals(500L, invoke("sumIf", orders(), S("amount"), S(">="), I(200)).asInt64());
        }
    }

    // =========================================================================
    // avgIf
    // =========================================================================

    @Nested
    class AvgIf {
        @Test
        void averagesExplicitField() {
            assertEquals(200L, invoke("avgIf", orders(), S("status"), S("="), S("paid"), S("amount")).asInt64());
        }

        @Test
        void noMatchYieldsNull() {
            assertTrue(invoke("avgIf", orders(), S("status"), S("="), S("void"), S("amount")).isNull());
        }
    }
}
