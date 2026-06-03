package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for set-algebra, grouping, and reshaping array verbs: intersection,
 * union, difference, symmetricDifference, countBy, keyBy, explode, and window.
 */
class CollectionSetVerbTest {

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

    private static List<Long> ints(DynValue arr) {
        var out = new ArrayList<Long>();
        for (var v : arr.asArray()) out.add(v.asInt64());
        return out;
    }

    private static List<String> keys(DynValue obj) {
        var ks = new ArrayList<String>();
        for (var entry : obj.asObject()) ks.add(entry.getKey());
        return ks;
    }

    // =========================================================================
    // intersection
    // =========================================================================

    @Nested
    class Intersection {
        @Test
        void distinctInBothFirstOrder() {
            var a = Arr(I(1), I(2), I(2), I(3));
            var b = Arr(I(2), I(3), I(4));
            assertEquals(List.of(2L, 3L), ints(invoke("intersection", a, b)));
        }

        @Test
        void disjointYieldsEmpty() {
            assertEquals(List.of(), ints(invoke("intersection", Arr(I(1), I(2)), Arr(I(9)))));
        }

        @Test
        void tooFewArgsYieldsEmptyArray() {
            assertEquals(List.of(), ints(invoke("intersection", Arr(I(1)))));
        }
    }

    // =========================================================================
    // union
    // =========================================================================

    @Nested
    class Union {
        @Test
        void distinctFirstOrderThenNew() {
            var a = Arr(I(1), I(2), I(2));
            var b = Arr(I(2), I(3));
            assertEquals(List.of(1L, 2L, 3L), ints(invoke("union", a, b)));
        }

        @Test
        void disjointConcatenatesDistinct() {
            assertEquals(List.of(1L, 2L, 3L, 4L),
                    ints(invoke("union", Arr(I(1), I(2)), Arr(I(3), I(4)))));
        }

        @Test
        void emptyFirstYieldsDistinctSecond() {
            assertEquals(List.of(2L, 3L), ints(invoke("union", Arr(), Arr(I(2), I(3)))));
        }
    }

    // =========================================================================
    // difference
    // =========================================================================

    @Nested
    class Difference {
        @Test
        void distinctOnlyInFirst() {
            var a = Arr(I(1), I(1), I(2), I(3));
            var b = Arr(I(2), I(3), I(4));
            assertEquals(List.of(1L), ints(invoke("difference", a, b)));
        }

        @Test
        void noOverlapReturnsDistinctFirst() {
            var a = Arr(I(1), I(1), I(2), I(3));
            var c = Arr(I(9), I(8));
            assertEquals(List.of(1L, 2L, 3L), ints(invoke("difference", a, c)));
        }
    }

    // =========================================================================
    // symmetricDifference
    // =========================================================================

    @Nested
    class SymmetricDifference {
        @Test
        void exclusivesFirstThenSecond() {
            var a = Arr(I(1), I(2), I(3));
            var b = Arr(I(2), I(3), I(4));
            assertEquals(List.of(1L, 4L), ints(invoke("symmetricDifference", a, b)));
        }

        @Test
        void disjointReturnsEveryElement() {
            assertEquals(List.of(1L, 2L, 3L, 4L),
                    ints(invoke("symmetricDifference", Arr(I(1), I(2)), Arr(I(3), I(4)))));
        }

        @Test
        void collapsesDuplicatesWithinInput() {
            var e1 = Arr(I(1), I(1), I(2));
            var f = Arr(I(2), I(3));
            assertEquals(List.of(1L, 3L), ints(invoke("symmetricDifference", e1, f)));
        }
    }

    // =========================================================================
    // countBy
    // =========================================================================

    @Nested
    class CountBy {
        @Test
        void countsPerFieldValueSortedKeys() {
            var items = Arr(
                    Obj(e("region", S("east"))),
                    Obj(e("region", S("west"))),
                    Obj(e("region", S("east"))));
            var result = invoke("countBy", items, S("region"));
            assertEquals(List.of("east", "west"), keys(result));
            assertEquals(2L, result.get("east").asInt64());
            assertEquals(1L, result.get("west").asInt64());
        }

        @Test
        void withoutFieldCountsItems() {
            var tags = Arr(S("a"), S("b"), S("a"), S("a"));
            var result = invoke("countBy", tags);
            assertEquals(3L, result.get("a").asInt64());
            assertEquals(1L, result.get("b").asInt64());
        }

        @Test
        void nonArrayYieldsNull() {
            assertTrue(invoke("countBy", S("x")).isNull());
        }
    }

    // =========================================================================
    // keyBy
    // =========================================================================

    @Nested
    class KeyBy {
        @Test
        void indexesByFieldLastWins() {
            var users = Arr(
                    Obj(e("id", S("u1")), e("name", S("Ada"))),
                    Obj(e("id", S("u2")), e("name", S("Bo"))),
                    Obj(e("id", S("u1")), e("name", S("Ada2"))));
            var result = invoke("keyBy", users, S("id"));
            assertEquals(List.of("u1", "u2"), keys(result));
            assertEquals("Ada2", result.get("u1").get("name").asString());
            assertEquals("Bo", result.get("u2").get("name").asString());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("keyBy", Arr()).isNull());
        }
    }

    // =========================================================================
    // explode
    // =========================================================================

    @Nested
    class Explode {
        @Test
        void oneRowPerElement() {
            var orders = Arr(
                    Obj(e("id", S("o1")), e("tags", Arr(S("red"), S("blue")))),
                    Obj(e("id", S("o2")), e("tags", Arr())));
            var result = invoke("explode", orders, S("tags")).asArray();
            assertEquals(3, result.size());
            assertEquals("o1", result.get(0).get("id").asString());
            assertEquals("red", result.get(0).get("tags").asString());
            assertEquals("o1", result.get(1).get("id").asString());
            assertEquals("blue", result.get(1).get("tags").asString());
            // Empty array field emits the row once, unchanged.
            assertEquals("o2", result.get(2).get("id").asString());
        }

        @Test
        void missingFieldEmitsRowOnce() {
            var plain = Arr(Obj(e("id", S("p1"))), Obj(e("id", S("p2"))));
            var result = invoke("explode", plain, S("tags")).asArray();
            assertEquals(2, result.size());
            assertEquals("p1", result.get(0).get("id").asString());
            assertEquals("p2", result.get(1).get("id").asString());
        }
    }

    // =========================================================================
    // window
    // =========================================================================

    @Nested
    class Window {
        @Test
        void slidingPairs() {
            var nums = Arr(I(1), I(2), I(3));
            var result = invoke("window", nums, I(2)).asArray();
            assertEquals(2, result.size());
            assertEquals(List.of(1L, 2L), ints(result.get(0)));
            assertEquals(List.of(2L, 3L), ints(result.get(1)));
        }

        @Test
        void singleElementWindows() {
            var nums = Arr(I(1), I(2), I(3));
            var result = invoke("window", nums, I(1)).asArray();
            assertEquals(3, result.size());
            assertEquals(List.of(1L), ints(result.get(0)));
        }

        @Test
        void sizeBeyondLengthYieldsEmpty() {
            assertEquals(0, invoke("window", Arr(I(1), I(2)), I(5)).asArray().size());
        }
    }
}
