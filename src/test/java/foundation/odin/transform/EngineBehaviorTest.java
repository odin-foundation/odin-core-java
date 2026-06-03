package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Engine behaviors beyond individual verbs: multi-sink accumulation in one loop
 * pass, lazy control-flow that runs only the selected branch, and month-end
 * clamping in addMonths / addYears.
 */
class EngineBehaviorTest {

    private static final String HEADER =
            "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\ntarget.format = \"json\"\n\n";

    private static TransformResult run(String body, DynValue source) {
        var transform = TransformParser.parse(HEADER + body);
        var result = TransformEngine.execute(transform, source);
        assertTrue(result.isSuccess(), () -> "errors: " + result.getErrors());
        return result;
    }

    private static DynValue obj(java.util.Map<String, DynValue> fields) {
        var entries = new java.util.ArrayList<java.util.Map.Entry<String, DynValue>>();
        for (var e : fields.entrySet()) entries.add(java.util.Map.entry(e.getKey(), e.getValue()));
        return DynValue.ofObject(entries);
    }

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }

    // =========================================================================
    // Multi-sink: two accumulators advance together in one loop pass
    // =========================================================================

    @Nested
    class MultiSink {
        @Test
        void runningTotalAndCountBothAdvance() {
            // Each loop pass updates two underscore-prefixed sink fields.
            var body = "{$accumulator}\n"
                    + "total = ##0\ntotal._persist = true\n"
                    + "count = ##0\ncount._persist = true\n\n"
                    + "{_sink[]}\n:loop items\n"
                    + "_t = \"%accumulate total @.amount\"\n"
                    + "_c = \"%accumulate count ##1\"\n\n"
                    + "{Summary}\n"
                    + "total = \"@$accumulator.total\"\n"
                    + "count = \"@$accumulator.count\"\n";
            var items = DynValue.ofArray(new java.util.ArrayList<>(java.util.List.of(
                    obj(java.util.Map.of("amount", I(10))),
                    obj(java.util.Map.of("amount", I(20))),
                    obj(java.util.Map.of("amount", I(30))))));
            var source = obj(java.util.Map.of("items", items));
            var result = run(body, source);
            var summary = result.getOutput().get("Summary");
            assertEquals(60L, summary.get("total").asInt64());
            assertEquals(3L, summary.get("count").asInt64());
        }
    }

    // =========================================================================
    // Lazy control-flow: unselected branches do not fire side effects
    // =========================================================================

    @Nested
    class LazyControlFlow {
        // Run a verb in a sink, then read back an accumulator it may have touched.
        private long accumAfter(String decl, String sink) {
            var body = "{$accumulator}\n" + decl + "\n" + decl.split(" ")[0] + "._persist = true\n\n"
                    + "{_s}\n" + sink + "\n\n"
                    + "{out}\nx = \"@$accumulator." + decl.split(" ")[0] + "\"\n";
            var result = run(body, DynValue.ofObject(new java.util.ArrayList<>()));
            return result.getOutput().get("out").get("x").asInt64();
        }

        @Test
        void ifElseRunsOnlySelectedBranch() {
            var body = "{$accumulator}\n"
                    + "hit = ##0\nhit._persist = true\n"
                    + "miss = ##0\nmiss._persist = true\n\n"
                    + "{_s}\n_ = \"%ifElse ?true %accumulate hit ##1 %accumulate miss ##1\"\n\n"
                    + "{out}\nhit = \"@$accumulator.hit\"\nmiss = \"@$accumulator.miss\"\n";
            var result = run(body, DynValue.ofObject(new java.util.ArrayList<>()));
            var out = result.getOutput().get("out");
            assertEquals(1L, out.get("hit").asInt64());
            assertEquals(0L, out.get("miss").asInt64());
        }

        @Test
        void andShortCircuitsFalseLeft() {
            assertEquals(0L, accumAfter("x = ##0", "_ = \"%and ?false %accumulate x ##1\""));
        }

        @Test
        void orShortCircuitsTrueLeft() {
            assertEquals(0L, accumAfter("x = ##0", "_ = \"%or ?true %accumulate x ##1\""));
        }

        @Test
        void coalesceStopsAtFirstNonNull() {
            assertEquals(0L, accumAfter("x = ##0", "_ = \"%coalesce \\\"first\\\" %accumulate x ##1\""));
        }

        @Test
        void selectedValuesAreCorrect() {
            var body = "{out}\n"
                    + "a = \"%ifElse %gt ##5 ##3 \\\"big\\\" \\\"small\\\"\"\n"
                    + "b = \"%ifNull ~ \\\"fallback\\\"\"\n"
                    + "c = \"%coalesce ~ ~ \\\"third\\\"\"\n"
                    + "d = \"%switch \\\"b\\\" \\\"a\\\" ##1 \\\"b\\\" ##2 ##99\"\n";
            var out = run(body, DynValue.ofObject(new java.util.ArrayList<>())).getOutput().get("out");
            assertEquals("big", out.get("a").asString());
            assertEquals("fallback", out.get("b").asString());
            assertEquals("third", out.get("c").asString());
            assertEquals(2L, out.get("d").asInt64());
        }
    }

    // =========================================================================
    // addMonths / addYears clamp to the target month end
    // =========================================================================

    @Nested
    class MonthEndClamp {
        @Test
        void addMonthsClampsToFebruaryLeapYear() {
            // Jan 31 + 1 month -> Feb 29 in a leap year.
            assertEquals("2024-02-29", invoke("addMonths", S("2024-01-31"), I(1)).asString());
        }

        @Test
        void addMonthsClampsToFebruaryNonLeapYear() {
            // Jan 31 + 1 month -> Feb 28 in a non-leap year.
            assertEquals("2023-02-28", invoke("addMonths", S("2023-01-31"), I(1)).asString());
        }

        @Test
        void addYearsClampsLeapDayToFebruary28() {
            // Feb 29 + 1 year -> Feb 28 (target year is not a leap year).
            assertEquals("2025-02-28", invoke("addYears", S("2024-02-29"), I(1)).asString());
        }
    }
}
