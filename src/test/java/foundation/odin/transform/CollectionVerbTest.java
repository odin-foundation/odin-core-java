package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for collection, array, object, aggregation, and statistical verbs.
 * Ported from .NET CollectionVerbTests.cs (which was ported from Rust SDK).
 */
class CollectionVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    // Shorthand helpers
    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
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

    // =========================================================================
    // flatten
    // =========================================================================

    @Test
    void flatten_nested() {
        var data = Arr(Arr(I(1), I(2)), Arr(I(3)));
        var result = invoke("flatten", data);
        assertEquals(3, result.asArray().size());
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    @Test
    void flatten_mixed() {
        var data = Arr(I(1), Arr(I(2), I(3)), I(4));
        var result = invoke("flatten", data);
        assertEquals(4, result.asArray().size());
    }

    @Test
    void flatten_empty() {
        var result = invoke("flatten", Arr());
        assertTrue(result.asArray().isEmpty());
    }

    @Test
    void flatten_singleLevelOnly() {
        var data = Arr(Arr(Arr(I(1))));
        var result = invoke("flatten", data);
        // Only flattens one level
        assertEquals(1, result.asArray().size());
        assertNotNull(result.asArray().get(0).asArray());
    }

    @Test
    void flatten_allScalars() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("flatten", data);
        assertEquals(3, result.asArray().size());
    }

    @Test
    void flatten_withNulls() {
        var data = Arr(Null(), Arr(I(1)));
        var result = invoke("flatten", data);
        assertEquals(2, result.asArray().size());
        assertTrue(result.asArray().get(0).isNull());
    }

    // =========================================================================
    // distinct / unique
    // =========================================================================

    @Test
    void distinct_removesDuplicates() {
        var data = Arr(I(1), I(2), I(1), I(3), I(2));
        var result = invoke("distinct", data);
        assertEquals(3, result.asArray().size());
    }

    @Test
    void distinct_empty() {
        assertTrue(invoke("distinct", Arr()).asArray().isEmpty());
    }

    @Test
    void unique_isDistinctAlias() {
        var data = Arr(S("a"), S("b"), S("a"));
        var r1 = invoke("unique", data);
        assertEquals(2, r1.asArray().size());
    }

    @Test
    void distinct_singleElement() {
        assertEquals(1, invoke("distinct", Arr(I(42))).asArray().size());
    }

    @Test
    void distinct_allSame() {
        var data = Arr(S("a"), S("a"), S("a"));
        assertEquals(1, invoke("distinct", data).asArray().size());
    }

    @Test
    void distinct_preservesOrder() {
        var data = Arr(I(3), I(1), I(2), I(1), I(3));
        var result = invoke("distinct", data);
        assertEquals(3, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(1).asInt64());
        assertEquals(2, result.asArray().get(2).asInt64());
    }

    @Test
    void distinct_withNulls() {
        var data = Arr(Null(), I(1), Null(), I(2));
        var result = invoke("distinct", data);
        assertEquals(3, result.asArray().size());
    }

    // =========================================================================
    // sort
    // =========================================================================

    @Test
    void sort_integers() {
        var data = Arr(I(3), I(1), I(2));
        var result = invoke("sort", data);
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    @Test
    void sort_strings() {
        var data = Arr(S("banana"), S("apple"), S("cherry"));
        var result = invoke("sort", data);
        assertEquals("apple", result.asArray().get(0).asString());
        assertEquals("banana", result.asArray().get(1).asString());
        assertEquals("cherry", result.asArray().get(2).asString());
    }

    @Test
    void sort_empty() {
        assertTrue(invoke("sort", Arr()).asArray().isEmpty());
    }

    @Test
    void sort_single() {
        var result = invoke("sort", Arr(I(42)));
        assertEquals(42, result.asArray().get(0).asInt64());
    }

    @Test
    void sort_floats() {
        var data = Arr(F(3.1), F(1.5), F(2.7));
        var result = invoke("sort", data);
        assertEquals(1.5, result.asArray().get(0).asDouble());
        assertEquals(2.7, result.asArray().get(1).asDouble());
        assertEquals(3.1, result.asArray().get(2).asDouble());
    }

    @Test
    void sort_alreadySorted() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("sort", data);
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    @Test
    void sort_reverseOrder() {
        var data = Arr(I(3), I(2), I(1));
        var result = invoke("sort", data);
        assertEquals(1, result.asArray().get(0).asInt64());
    }

    @Test
    void sort_withDuplicates() {
        var data = Arr(I(2), I(1), I(2), I(1));
        var result = invoke("sort", data);
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(1).asInt64());
        assertEquals(2, result.asArray().get(2).asInt64());
        assertEquals(2, result.asArray().get(3).asInt64());
    }

    // =========================================================================
    // sortDesc
    // =========================================================================

    @Test
    void sortDesc_integers() {
        var data = Arr(I(1), I(3), I(2));
        var result = invoke("sortDesc", data);
        assertEquals(3, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
        assertEquals(1, result.asArray().get(2).asInt64());
    }

    @Test
    void sortDesc_empty() {
        assertTrue(invoke("sortDesc", Arr()).asArray().isEmpty());
    }

    @Test
    void sortDesc_strings() {
        var data = Arr(S("a"), S("c"), S("b"));
        var result = invoke("sortDesc", data);
        assertEquals("c", result.asArray().get(0).asString());
        assertEquals("b", result.asArray().get(1).asString());
        assertEquals("a", result.asArray().get(2).asString());
    }

    @Test
    void sortDesc_floats() {
        var data = Arr(F(1.1), F(3.3), F(2.2));
        var result = invoke("sortDesc", data);
        assertEquals(3.3, result.asArray().get(0).asDouble());
        assertEquals(2.2, result.asArray().get(1).asDouble());
        assertEquals(1.1, result.asArray().get(2).asDouble());
    }

    @Test
    void sortDesc_single() {
        var result = invoke("sortDesc", Arr(I(5)));
        assertEquals(5, result.asArray().get(0).asInt64());
    }

    // =========================================================================
    // sortBy
    // =========================================================================

    @Test
    void sortBy_field() {
        var data = Arr(
            Obj(e("n", I(3))),
            Obj(e("n", I(1))),
            Obj(e("n", I(2)))
        );
        var result = invoke("sortBy", data, S("n"));
        assertEquals(1, result.asArray().get(0).get("n").asInt64());
        assertEquals(3, result.asArray().get(2).get("n").asInt64());
    }

    @Test
    void sortBy_stringField() {
        var data = Arr(
            Obj(e("name", S("Charlie"))),
            Obj(e("name", S("Alice"))),
            Obj(e("name", S("Bob")))
        );
        var result = invoke("sortBy", data, S("name"));
        assertEquals("Alice", result.asArray().get(0).get("name").asString());
        assertEquals("Bob", result.asArray().get(1).get("name").asString());
        assertEquals("Charlie", result.asArray().get(2).get("name").asString());
    }

    @Test
    void sortBy_emptyArray() {
        var result = invoke("sortBy", Arr(), S("x"));
        assertTrue(result.asArray().isEmpty());
    }

    @Test
    void sortBy_missingField() {
        var data = Arr(Obj(e("a", I(2))), Obj(e("b", I(1))));
        var result = invoke("sortBy", data, S("a"));
        assertEquals(2, result.asArray().size());
    }

    // =========================================================================
    // map / pluck
    // =========================================================================

    @Test
    void map_extractsField() {
        var data = Arr(Obj(e("name", S("Alice"))), Obj(e("name", S("Bob"))));
        var result = invoke("map", data, S("name"));
        assertEquals("Alice", result.asArray().get(0).asString());
        assertEquals("Bob", result.asArray().get(1).asString());
    }

    @Test
    void map_missingFieldGivesNull() {
        var data = Arr(Obj(e("a", I(1))));
        var result = invoke("map", data, S("b"));
        assertTrue(result.asArray().get(0).isNull());
    }

    @Test
    void pluck_isMapAlias() {
        var data = Arr(Obj(e("x", I(1))), Obj(e("x", I(2))));
        var result = invoke("pluck", data, S("x"));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
    }

    @Test
    void map_emptyArray() {
        assertTrue(invoke("map", Arr(), S("x")).asArray().isEmpty());
    }

    @Test
    void map_allMissingFields() {
        var data = Arr(Obj(e("a", I(1))), Obj(e("a", I(2))));
        var result = invoke("map", data, S("z"));
        assertTrue(result.asArray().get(0).isNull());
        assertTrue(result.asArray().get(1).isNull());
    }

    @Test
    void pluck_emptyArray() {
        assertTrue(invoke("pluck", Arr(), S("x")).asArray().isEmpty());
    }

    // =========================================================================
    // indexOf
    // =========================================================================

    @Test
    void indexOf_found() {
        var data = Arr(S("a"), S("b"), S("c"));
        assertEquals(1, invoke("indexOf", data, S("b")).asInt64());
    }

    @Test
    void indexOf_notFound() {
        var data = Arr(I(1), I(2));
        assertEquals(-1, invoke("indexOf", data, I(99)).asInt64());
    }

    @Test
    void indexOf_firstOccurrence() {
        var data = Arr(I(1), I(2), I(1));
        assertEquals(0, invoke("indexOf", data, I(1)).asInt64());
    }

    @Test
    void indexOf_emptyArray() {
        assertEquals(-1, invoke("indexOf", Arr(), I(1)).asInt64());
    }

    @Test
    void indexOf_stringValue() {
        var data = Arr(S("hello"), S("world"));
        assertEquals(1, invoke("indexOf", data, S("world")).asInt64());
    }

    // =========================================================================
    // at
    // =========================================================================

    @Test
    void at_validIndex() {
        var data = Arr(S("a"), S("b"), S("c"));
        assertEquals("b", invoke("at", data, I(1)).asString());
    }

    @Test
    void at_outOfBounds() {
        var data = Arr(I(1));
        assertTrue(invoke("at", data, I(5)).isNull());
    }

    @Test
    void at_firstElement() {
        var data = Arr(S("first"), S("second"));
        assertEquals("first", invoke("at", data, I(0)).asString());
    }

    @Test
    void at_lastElement() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(3, invoke("at", data, I(2)).asInt64());
    }

    @Test
    void at_emptyArray() {
        assertTrue(invoke("at", Arr(), I(0)).isNull());
    }

    @Test
    void at_negativeIndex() {
        var data = Arr(I(10), I(20), I(30));
        assertEquals(30, invoke("at", data, I(-1)).asInt64());
    }

    // =========================================================================
    // slice
    // =========================================================================

    @Test
    void slice_middle() {
        var data = Arr(I(10), I(20), I(30), I(40), I(50));
        var result = invoke("slice", data, I(1), I(4));
        assertEquals(3, result.asArray().size());
        assertEquals(20, result.asArray().get(0).asInt64());
        assertEquals(40, result.asArray().get(2).asInt64());
    }

    @Test
    void slice_emptyRange() {
        var data = Arr(I(1), I(2));
        assertTrue(invoke("slice", data, I(1), I(1)).asArray().isEmpty());
    }

    @Test
    void slice_clampsEnd() {
        var data = Arr(I(1), I(2));
        var result = invoke("slice", data, I(0), I(100));
        assertEquals(2, result.asArray().size());
    }

    @Test
    void slice_fullArray() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("slice", data, I(0), I(3));
        assertEquals(3, result.asArray().size());
    }

    @Test
    void slice_emptyArray() {
        assertTrue(invoke("slice", Arr(), I(0), I(0)).asArray().isEmpty());
    }

    @Test
    void slice_singleElement() {
        var data = Arr(I(10), I(20), I(30));
        var result = invoke("slice", data, I(1), I(2));
        assertEquals(1, result.asArray().size());
        assertEquals(20, result.asArray().get(0).asInt64());
    }

    @Test
    void slice_startPastLength() {
        var data = Arr(I(1));
        assertTrue(invoke("slice", data, I(5), I(10)).asArray().isEmpty());
    }

    // =========================================================================
    // reverse
    // =========================================================================

    @Test
    void reverse_array() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("reverse", data);
        assertEquals(3, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(2).asInt64());
    }

    @Test
    void reverse_empty() {
        assertTrue(invoke("reverse", Arr()).asArray().isEmpty());
    }

    @Test
    void reverse_single() {
        var result = invoke("reverse", Arr(I(1)));
        assertEquals(1, result.asArray().get(0).asInt64());
    }

    @Test
    void reverse_strings() {
        var data = Arr(S("a"), S("b"), S("c"));
        var result = invoke("reverse", data);
        assertEquals("c", result.asArray().get(0).asString());
        assertEquals("a", result.asArray().get(2).asString());
    }

    @Test
    void reverse_twoElements() {
        var data = Arr(I(1), I(2));
        var result = invoke("reverse", data);
        assertEquals(2, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(1).asInt64());
    }

    // =========================================================================
    // every
    // =========================================================================

    @Test
    void every_allTruthy() {
        var data = Arr(I(1), I(2), I(3));
        assertTrue(invoke("every", data).asBool());
    }

    @Test
    void every_notAll() {
        var data = Arr(I(1), I(0), I(3));
        assertFalse(invoke("every", data).asBool());
    }

    @Test
    void every_emptyIsTrue() {
        assertTrue(invoke("every", Arr()).asBool());
    }

    @Test
    void every_withFieldName() {
        var data = Arr(Obj(e("v", I(10))), Obj(e("v", I(20))));
        assertTrue(invoke("every", data, S("v")).asBool());
    }

    @Test
    void every_withFieldName_notAll() {
        var data = Arr(Obj(e("v", I(0))), Obj(e("v", I(10))));
        assertFalse(invoke("every", data, S("v")).asBool());
    }

    // =========================================================================
    // some
    // =========================================================================

    @Test
    void some_oneMatches() {
        var data = Arr(I(0), I(0), I(1));
        assertTrue(invoke("some", data).asBool());
    }

    @Test
    void some_noneMatch() {
        var data = Arr(I(0), Null(), S(""));
        assertFalse(invoke("some", data).asBool());
    }

    @Test
    void some_emptyIsFalse() {
        assertFalse(invoke("some", Arr()).asBool());
    }

    @Test
    void some_withFieldName() {
        var data = Arr(Obj(e("v", I(0))), Obj(e("v", I(10))));
        assertTrue(invoke("some", data, S("v")).asBool());
    }

    // =========================================================================
    // find
    // =========================================================================

    @Test
    void find_firstTruthy() {
        var data = Arr(Null(), I(0), S("found"), I(99));
        var result = invoke("find", data);
        assertEquals("found", result.asString());
    }

    @Test
    void find_noMatchReturnsNull() {
        var data = Arr(Null(), I(0), S(""));
        assertTrue(invoke("find", data).isNull());
    }

    @Test
    void find_withFieldName() {
        var data = Arr(
            Obj(e("n", S("a")), e("v", I(0))),
            Obj(e("n", S("b")), e("v", I(1)))
        );
        var result = invoke("find", data, S("v"));
        assertEquals("b", result.get("n").asString());
    }

    @Test
    void find_emptyArray() {
        assertTrue(invoke("find", Arr()).isNull());
    }

    // =========================================================================
    // findIndex
    // =========================================================================

    @Test
    void findIndex_found() {
        var data = Arr(Null(), I(0), I(1));
        assertEquals(2, invoke("findIndex", data).asInt64());
    }

    @Test
    void findIndex_notFound() {
        var data = Arr(Null(), I(0));
        assertEquals(-1, invoke("findIndex", data).asInt64());
    }

    @Test
    void findIndex_emptyArray() {
        assertEquals(-1, invoke("findIndex", Arr()).asInt64());
    }

    // =========================================================================
    // includes
    // =========================================================================

    @Test
    void includes_present() {
        var data = Arr(I(1), I(2), I(3));
        assertTrue(invoke("includes", data, I(2)).asBool());
    }

    @Test
    void includes_absent() {
        var data = Arr(I(1), I(2));
        assertFalse(invoke("includes", data, I(99)).asBool());
    }

    @Test
    void includes_empty() {
        assertFalse(invoke("includes", Arr(), I(1)).asBool());
    }

    @Test
    void includes_stringValue() {
        var data = Arr(S("hello"), S("world"));
        assertTrue(invoke("includes", data, S("hello")).asBool());
    }

    @Test
    void includes_boolValue() {
        var data = Arr(B(true), B(false));
        assertTrue(invoke("includes", data, B(true)).asBool());
    }

    // =========================================================================
    // concatArrays
    // =========================================================================

    @Test
    void concatArrays_twoArrays() {
        var a = Arr(I(1), I(2));
        var b = Arr(I(3), I(4));
        var result = invoke("concatArrays", a, b);
        assertEquals(4, result.asArray().size());
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(4, result.asArray().get(3).asInt64());
    }

    @Test
    void concatArrays_withEmpty() {
        var a = Arr(I(1));
        var result = invoke("concatArrays", a, Arr());
        assertEquals(1, result.asArray().size());
    }

    @Test
    void concatArrays_bothEmpty() {
        assertTrue(invoke("concatArrays", Arr(), Arr()).asArray().isEmpty());
    }

    @Test
    void concatArrays_firstEmpty() {
        var result = invoke("concatArrays", Arr(), Arr(I(1)));
        assertEquals(1, result.asArray().size());
    }

    @Test
    void concatArrays_mixedTypes() {
        var a = Arr(I(1), S("two"));
        var b = Arr(B(true), Null());
        assertEquals(4, invoke("concatArrays", a, b).asArray().size());
    }

    // =========================================================================
    // zip
    // =========================================================================

    @Test
    void zip_equalLength() {
        var a = Arr(I(1), I(2));
        var b = Arr(S("a"), S("b"));
        var result = invoke("zip", a, b);
        assertEquals(2, result.asArray().size());
        var first = result.asArray().get(0).asArray();
        assertEquals(1, first.get(0).asInt64());
        assertEquals("a", first.get(1).asString());
    }

    @Test
    void zip_unequalPadsWithNull() {
        var a = Arr(I(1), I(2), I(3));
        var b = Arr(S("a"));
        var result = invoke("zip", a, b);
        // .NET implementation pads with null for shorter arrays
        assertEquals(3, result.asArray().size());
    }

    @Test
    void zip_bothEmpty() {
        assertTrue(invoke("zip", Arr(), Arr()).asArray().isEmpty());
    }

    @Test
    void zip_singleElements() {
        var result = invoke("zip", Arr(I(1)), Arr(S("a")));
        assertEquals(1, result.asArray().size());
        var pair = result.asArray().get(0).asArray();
        assertEquals(1, pair.get(0).asInt64());
        assertEquals("a", pair.get(1).asString());
    }

    // =========================================================================
    // groupBy
    // =========================================================================

    @Test
    void groupBy_field() {
        var data = Arr(
            Obj(e("color", S("red")), e("n", I(1))),
            Obj(e("color", S("blue")), e("n", I(2))),
            Obj(e("color", S("red")), e("n", I(3)))
        );
        var result = invoke("groupBy", data, S("color"));
        var obj = result.asObject();
        assertEquals(2, obj.size());
        assertEquals("red", obj.get(0).getKey());
        assertEquals("blue", obj.get(1).getKey());
    }

    @Test
    void groupBy_emptyArray() {
        var result = invoke("groupBy", Arr(), S("key"));
        assertTrue(result.asObject().isEmpty());
    }

    @Test
    void groupBy_singleGroup() {
        var data = Arr(
            Obj(e("type", S("A")), e("v", I(1))),
            Obj(e("type", S("A")), e("v", I(2)))
        );
        var result = invoke("groupBy", data, S("type"));
        assertEquals(1, result.asObject().size());
    }

    @Test
    void groupBy_missingFieldUsesNullKey() {
        var data = Arr(
            Obj(e("v", I(1))),
            Obj(e("type", S("A")), e("v", I(2)))
        );
        var result = invoke("groupBy", data, S("type"));
        assertEquals(2, result.asObject().size());
    }

    @Test
    void groupBy_integerField() {
        var data = Arr(
            Obj(e("score", I(10))),
            Obj(e("score", I(20))),
            Obj(e("score", I(10)))
        );
        var result = invoke("groupBy", data, S("score"));
        assertEquals(2, result.asObject().size());
    }

    // =========================================================================
    // partition
    // =========================================================================

    @Test
    void partition_splits() {
        var data = Arr(I(1), I(0), I(3), Null());
        var result = invoke("partition", data);
        var parts = result.asArray();
        assertEquals(2, parts.size());
        assertEquals(2, parts.get(0).asArray().size()); // truthy: 1, 3
        assertEquals(2, parts.get(1).asArray().size()); // falsy: 0, null
    }

    @Test
    void partition_allTruthy() {
        var data = Arr(I(1), I(2));
        var result = invoke("partition", data);
        assertEquals(2, result.asArray().get(0).asArray().size());
        assertTrue(result.asArray().get(1).asArray().isEmpty());
    }

    @Test
    void partition_allFalsy() {
        var data = Arr(I(0), Null());
        var result = invoke("partition", data);
        assertTrue(result.asArray().get(0).asArray().isEmpty());
        assertEquals(2, result.asArray().get(1).asArray().size());
    }

    @Test
    void partition_empty() {
        var result = invoke("partition", Arr());
        assertTrue(result.asArray().get(0).asArray().isEmpty());
        assertTrue(result.asArray().get(1).asArray().isEmpty());
    }

    // =========================================================================
    // take
    // =========================================================================

    @Test
    void take_firstN() {
        var data = Arr(I(1), I(2), I(3), I(4));
        var result = invoke("take", data, I(2));
        assertEquals(2, result.asArray().size());
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
    }

    @Test
    void take_moreThanLength() {
        var data = Arr(I(1));
        var result = invoke("take", data, I(100));
        assertEquals(1, result.asArray().size());
    }

    @Test
    void take_zero() {
        assertTrue(invoke("take", Arr(I(1), I(2), I(3)), I(0)).asArray().isEmpty());
    }

    @Test
    void take_emptyArray() {
        assertTrue(invoke("take", Arr(), I(5)).asArray().isEmpty());
    }

    @Test
    void take_exactLength() {
        var data = Arr(I(1), I(2));
        assertEquals(2, invoke("take", data, I(2)).asArray().size());
    }

    // =========================================================================
    // drop
    // =========================================================================

    @Test
    void drop_firstN() {
        var data = Arr(I(1), I(2), I(3), I(4));
        var result = invoke("drop", data, I(2));
        assertEquals(2, result.asArray().size());
        assertEquals(3, result.asArray().get(0).asInt64());
    }

    @Test
    void drop_all() {
        var data = Arr(I(1), I(2));
        assertTrue(invoke("drop", data, I(10)).asArray().isEmpty());
    }

    @Test
    void drop_zero() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(3, invoke("drop", data, I(0)).asArray().size());
    }

    @Test
    void drop_emptyArray() {
        assertTrue(invoke("drop", Arr(), I(5)).asArray().isEmpty());
    }

    @Test
    void drop_exactLength() {
        assertTrue(invoke("drop", Arr(I(1), I(2)), I(2)).asArray().isEmpty());
    }

    // =========================================================================
    // chunk
    // =========================================================================

    @Test
    void chunk_even() {
        var data = Arr(I(1), I(2), I(3), I(4));
        var result = invoke("chunk", data, I(2));
        assertEquals(2, result.asArray().size());
        assertEquals(2, result.asArray().get(0).asArray().size());
        assertEquals(2, result.asArray().get(1).asArray().size());
    }

    @Test
    void chunk_uneven() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("chunk", data, I(2));
        assertEquals(2, result.asArray().size());
        assertEquals(2, result.asArray().get(0).asArray().size());
        assertEquals(1, result.asArray().get(1).asArray().size());
    }

    @Test
    void chunk_singleElement() {
        var result = invoke("chunk", Arr(I(1)), I(1));
        assertEquals(1, result.asArray().size());
        assertEquals(1, result.asArray().get(0).asArray().size());
    }

    @Test
    void chunk_sizeLargerThanArray() {
        var data = Arr(I(1), I(2));
        var result = invoke("chunk", data, I(10));
        assertEquals(1, result.asArray().size());
        assertEquals(2, result.asArray().get(0).asArray().size());
    }

    @Test
    void chunk_emptyArray() {
        assertTrue(invoke("chunk", Arr(), I(3)).asArray().isEmpty());
    }

    @Test
    void chunk_sizeOne() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("chunk", data, I(1));
        assertEquals(3, result.asArray().size());
    }

    // =========================================================================
    // range
    // =========================================================================

    @Test
    void range_basic() {
        var result = invoke("range", I(0), I(5));
        assertEquals(5, result.asArray().size());
        assertEquals(0, result.asArray().get(0).asInt64());
        assertEquals(4, result.asArray().get(4).asInt64());
    }

    @Test
    void range_withStep() {
        var result = invoke("range", I(0), I(10), I(3));
        assertEquals(4, result.asArray().size());
        assertEquals(0, result.asArray().get(0).asInt64());
        assertEquals(9, result.asArray().get(3).asInt64());
    }

    @Test
    void range_negativeStep() {
        var result = invoke("range", I(5), I(0), I(-2));
        assertEquals(3, result.asArray().size());
        assertEquals(5, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(2).asInt64());
    }

    @Test
    void range_emptyWhenStartGeEnd() {
        assertTrue(invoke("range", I(5), I(3)).asArray().isEmpty());
    }

    @Test
    void range_singleElement() {
        var result = invoke("range", I(0), I(1));
        assertEquals(1, result.asArray().size());
        assertEquals(0, result.asArray().get(0).asInt64());
    }

    @Test
    void range_negativeValues() {
        var result = invoke("range", I(-3), I(0));
        assertEquals(3, result.asArray().size());
        assertEquals(-3, result.asArray().get(0).asInt64());
    }

    @Test
    void range_sameStartEnd() {
        assertTrue(invoke("range", I(5), I(5)).asArray().isEmpty());
    }

    @Test
    void range_stepOfTwo() {
        var result = invoke("range", I(0), I(6), I(2));
        assertEquals(3, result.asArray().size());
        assertEquals(0, result.asArray().get(0).asInt64());
        assertEquals(4, result.asArray().get(2).asInt64());
    }

    // =========================================================================
    // compact
    // =========================================================================

    @Test
    void compact_removesNullsAndEmpty() {
        var data = Arr(I(1), Null(), S(""), I(2), S("ok"));
        var result = invoke("compact", data);
        assertEquals(3, result.asArray().size());
    }

    @Test
    void compact_allNull() {
        assertTrue(invoke("compact", Arr(Null(), Null())).asArray().isEmpty());
    }

    @Test
    void compact_noNulls() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(3, invoke("compact", data).asArray().size());
    }

    @Test
    void compact_emptyArray() {
        assertTrue(invoke("compact", Arr()).asArray().isEmpty());
    }

    @Test
    void compact_onlyEmptyStrings() {
        assertTrue(invoke("compact", Arr(S(""), S(""), S(""))).asArray().isEmpty());
    }

    @Test
    void compact_keepsNonEmptyStrings() {
        var data = Arr(S(""), S("hello"), Null(), S("world"));
        var result = invoke("compact", data);
        assertEquals(2, result.asArray().size());
    }

    // =========================================================================
    // dedupe
    // =========================================================================

    @Test
    void dedupe_consecutiveDuplicates() {
        var data = Arr(I(1), I(1), I(2), I(2), I(3));
        var result = invoke("dedupe", data);
        assertEquals(3, result.asArray().size());
    }

    @Test
    void dedupe_noDuplicates() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(3, invoke("dedupe", data).asArray().size());
    }

    @Test
    void dedupe_nonConsecutiveDuplicatesKept() {
        var data = Arr(I(1), I(2), I(1));
        assertEquals(3, invoke("dedupe", data).asArray().size());
    }

    @Test
    void dedupe_emptyArray() {
        assertTrue(invoke("dedupe", Arr()).asArray().isEmpty());
    }

    // =========================================================================
    // filter (truthiness-based)
    // =========================================================================

    @Test
    void filter_truthyElements() {
        var data = Arr(I(1), Null(), I(0), S("hello"), S(""), B(true), B(false));
        var result = invoke("filter", data);
        // Truthy: 1, "hello", true
        assertEquals(3, result.asArray().size());
    }

    @Test
    void filter_emptyArray() {
        assertTrue(invoke("filter", Arr()).asArray().isEmpty());
    }

    @Test
    void filter_allTruthy() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(3, invoke("filter", data).asArray().size());
    }

    @Test
    void filter_allFalsy() {
        var data = Arr(Null(), I(0), S(""));
        assertTrue(invoke("filter", data).asArray().isEmpty());
    }

    @Test
    void filter_byFieldName() {
        var data = Arr(
            Obj(e("active", B(true)), e("name", S("A"))),
            Obj(e("active", B(false)), e("name", S("B"))),
            Obj(e("active", B(true)), e("name", S("C")))
        );
        var result = invoke("filter", data, S("active"));
        assertEquals(2, result.asArray().size());
    }

    // =========================================================================
    // keys
    // =========================================================================

    @Test
    void keys_ofObject() {
        var data = Obj(e("a", I(1)), e("b", I(2)));
        var result = invoke("keys", data);
        assertEquals(2, result.asArray().size());
        assertEquals("a", result.asArray().get(0).asString());
        assertEquals("b", result.asArray().get(1).asString());
    }

    @Test
    void keys_emptyObject() {
        assertTrue(invoke("keys", Obj()).asArray().isEmpty());
    }

    @Test
    void keys_singleKey() {
        var result = invoke("keys", Obj(e("only", I(1))));
        assertEquals(1, result.asArray().size());
        assertEquals("only", result.asArray().get(0).asString());
    }

    @Test
    void keys_preservesOrder() {
        var data = Obj(e("z", I(1)), e("a", I(2)), e("m", I(3)));
        var result = invoke("keys", data);
        assertEquals("z", result.asArray().get(0).asString());
        assertEquals("a", result.asArray().get(1).asString());
        assertEquals("m", result.asArray().get(2).asString());
    }

    // =========================================================================
    // values
    // =========================================================================

    @Test
    void values_ofObject() {
        var data = Obj(e("a", I(1)), e("b", I(2)));
        var result = invoke("values", data);
        assertEquals(2, result.asArray().size());
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
    }

    @Test
    void values_emptyObject() {
        assertTrue(invoke("values", Obj()).asArray().isEmpty());
    }

    @Test
    void values_mixedTypes() {
        var data = Obj(e("a", I(1)), e("b", S("two")), e("c", B(true)));
        assertEquals(3, invoke("values", data).asArray().size());
    }

    // =========================================================================
    // entries
    // =========================================================================

    @Test
    void entries_ofObject() {
        var data = Obj(e("x", I(1)));
        var result = invoke("entries", data);
        assertEquals(1, result.asArray().size());
        var pair = result.asArray().get(0).asArray();
        assertEquals("x", pair.get(0).asString());
        assertEquals(1, pair.get(1).asInt64());
    }

    @Test
    void entries_emptyObject() {
        assertTrue(invoke("entries", Obj()).asArray().isEmpty());
    }

    @Test
    void entries_multiplePairs() {
        var data = Obj(e("a", I(1)), e("b", I(2)));
        var result = invoke("entries", data);
        assertEquals(2, result.asArray().size());
        assertEquals("a", result.asArray().get(0).asArray().get(0).asString());
        assertEquals("b", result.asArray().get(1).asArray().get(0).asString());
    }

    // =========================================================================
    // has
    // =========================================================================

    @Test
    void has_existingKey() {
        assertTrue(invoke("has", Obj(e("a", I(1))), S("a")).asBool());
    }

    @Test
    void has_missingKey() {
        assertFalse(invoke("has", Obj(e("a", I(1))), S("z")).asBool());
    }

    @Test
    void has_emptyObject() {
        assertFalse(invoke("has", Obj(), S("anything")).asBool());
    }

    @Test
    void has_withNullValue() {
        assertTrue(invoke("has", Obj(e("key", Null())), S("key")).asBool());
    }

    // =========================================================================
    // get
    // =========================================================================

    @Test
    void get_simpleKey() {
        var data = Obj(e("a", I(42)));
        assertEquals(42, invoke("get", data, S("a")).asInt64());
    }

    @Test
    void get_missingReturnsNull() {
        assertTrue(invoke("get", Obj(e("a", I(1))), S("z")).isNull());
    }

    // =========================================================================
    // merge
    // =========================================================================

    @Test
    void merge_objects() {
        var a = Obj(e("a", I(1)), e("b", I(2)));
        var b = Obj(e("b", I(99)), e("c", I(3)));
        var result = invoke("merge", a, b);
        assertEquals(3, result.asObject().size());
    }

    @Test
    void merge_bothEmpty() {
        assertTrue(invoke("merge", Obj(), Obj()).asObject().isEmpty());
    }

    @Test
    void merge_noOverlap() {
        var result = invoke("merge", Obj(e("a", I(1))), Obj(e("b", I(2))));
        assertEquals(2, result.asObject().size());
    }

    @Test
    void merge_completeOverlap() {
        var result = invoke("merge", Obj(e("x", I(1))), Obj(e("x", I(2))));
        assertEquals(1, result.asObject().size());
        assertEquals(2, result.asObject().get(0).getValue().asInt64());
    }

    // =========================================================================
    // sum
    // =========================================================================

    @Test
    void sum_integers() {
        assertEquals(6, invoke("sum", Arr(I(1), I(2), I(3))).asInt64());
    }

    @Test
    void sum_floats() {
        assertEquals(4.0, invoke("sum", Arr(F(1.5), F(2.5))).asDouble());
    }

    @Test
    void sum_empty() {
        assertEquals(0, invoke("sum", Arr()).asInt64());
    }

    @Test
    void sum_singleElement() {
        assertEquals(42, invoke("sum", Arr(I(42))).asInt64());
    }

    @Test
    void sum_negativeNumbers() {
        assertEquals(-6, invoke("sum", Arr(I(-1), I(-2), I(-3))).asInt64());
    }

    @Test
    void sum_largeFloats() {
        assertEquals(3e10, invoke("sum", Arr(F(1e10), F(2e10))).asDouble());
    }

    // =========================================================================
    // count
    // =========================================================================

    @Test
    void count_elements() {
        assertEquals(3, invoke("count", Arr(I(1), I(2), I(3))).asInt64());
    }

    @Test
    void count_empty() {
        assertEquals(0, invoke("count", Arr()).asInt64());
    }

    @Test
    void count_withNulls() {
        assertEquals(3, invoke("count", Arr(Null(), Null(), I(1))).asInt64());
    }

    @Test
    void count_singleElement() {
        assertEquals(1, invoke("count", Arr(I(42))).asInt64());
    }

    // =========================================================================
    // min
    // =========================================================================

    @Test
    void min_integers() {
        assertEquals(1, invoke("min", Arr(I(3), I(1), I(2))).asInt64());
    }

    @Test
    void min_emptyIsNull() {
        assertTrue(invoke("min", Arr()).isNull());
    }

    @Test
    void min_floats() {
        assertEquals(1.2, invoke("min", Arr(F(3.5), F(1.2), F(2.8))).asDouble());
    }

    @Test
    void min_singleElement() {
        assertEquals(42, invoke("min", Arr(I(42))).asInt64());
    }

    @Test
    void min_negativeNumbers() {
        assertEquals(-5, invoke("min", Arr(I(-1), I(-5), I(-2))).asInt64());
    }

    // =========================================================================
    // max
    // =========================================================================

    @Test
    void max_integers() {
        assertEquals(3, invoke("max", Arr(I(3), I(1), I(2))).asInt64());
    }

    @Test
    void max_emptyIsNull() {
        assertTrue(invoke("max", Arr()).isNull());
    }

    @Test
    void max_singleElement() {
        assertEquals(42, invoke("max", Arr(I(42))).asInt64());
    }

    @Test
    void max_negativeNumbers() {
        assertEquals(-1, invoke("max", Arr(I(-1), I(-5), I(-2))).asInt64());
    }

    @Test
    void max_floats() {
        assertEquals(9.9, invoke("max", Arr(F(1.1), F(9.9), F(5.5))).asDouble());
    }

    // =========================================================================
    // avg
    // =========================================================================

    @Test
    void avg_basic() {
        assertEquals(20.0, invoke("avg", Arr(I(10), I(20), I(30))).asDouble());
    }

    @Test
    void avg_emptyIsNull() {
        assertTrue(invoke("avg", Arr()).isNull());
    }

    @Test
    void avg_single() {
        assertEquals(7.0, invoke("avg", Arr(I(7))).asDouble());
    }

    @Test
    void avg_floats() {
        assertEquals(2.0, invoke("avg", Arr(F(1.0), F(2.0), F(3.0))).asDouble());
    }

    // =========================================================================
    // first
    // =========================================================================

    @Test
    void first_ofArray() {
        assertEquals("a", invoke("first", Arr(S("a"), S("b"))).asString());
    }

    @Test
    void first_emptyIsNull() {
        assertTrue(invoke("first", Arr()).isNull());
    }

    @Test
    void first_singleElement() {
        assertEquals(42, invoke("first", Arr(I(42))).asInt64());
    }

    @Test
    void first_nullElement() {
        assertTrue(invoke("first", Arr(Null(), I(1))).isNull());
    }

    // =========================================================================
    // last
    // =========================================================================

    @Test
    void last_ofArray() {
        assertEquals("c", invoke("last", Arr(S("a"), S("b"), S("c"))).asString());
    }

    @Test
    void last_emptyIsNull() {
        assertTrue(invoke("last", Arr()).isNull());
    }

    @Test
    void last_singleElement() {
        assertEquals(42, invoke("last", Arr(I(42))).asInt64());
    }

    @Test
    void last_nullAtEnd() {
        assertTrue(invoke("last", Arr(I(1), Null())).isNull());
    }

    // =========================================================================
    // cumsum
    // =========================================================================

    @Test
    void cumsum_basic() {
        var result = invoke("cumsum", Arr(I(1), I(2), I(3)));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(1).asInt64());
        assertEquals(6, result.asArray().get(2).asInt64());
    }

    @Test
    void cumsum_withNull() {
        var result = invoke("cumsum", Arr(I(1), Null(), I(3)));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertTrue(result.asArray().get(1).isNull());
        assertEquals(4, result.asArray().get(2).asInt64());
    }

    @Test
    void cumsum_empty() {
        assertTrue(invoke("cumsum", Arr()).asArray().isEmpty());
    }

    @Test
    void cumsum_singleElement() {
        assertEquals(5, invoke("cumsum", Arr(I(5))).asArray().get(0).asInt64());
    }

    @Test
    void cumsum_negativeNumbers() {
        var result = invoke("cumsum", Arr(I(5), I(-3), I(2)));
        assertEquals(5, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
        assertEquals(4, result.asArray().get(2).asInt64());
    }

    // =========================================================================
    // cumprod
    // =========================================================================

    @Test
    void cumprod_basic() {
        var result = invoke("cumprod", Arr(I(1), I(2), I(3)));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(2, result.asArray().get(1).asInt64());
        assertEquals(6, result.asArray().get(2).asInt64());
    }

    @Test
    void cumprod_withZero() {
        var result = invoke("cumprod", Arr(I(5), I(0), I(3)));
        assertEquals(5, result.asArray().get(0).asInt64());
        assertEquals(0, result.asArray().get(1).asInt64());
        assertEquals(0, result.asArray().get(2).asInt64());
    }

    @Test
    void cumprod_singleElement() {
        assertEquals(5, invoke("cumprod", Arr(I(5))).asArray().get(0).asInt64());
    }

    @Test
    void cumprod_empty() {
        assertTrue(invoke("cumprod", Arr()).asArray().isEmpty());
    }

    // =========================================================================
    // diff
    // =========================================================================

    @Test
    void diff_basic() {
        var result = invoke("diff", Arr(I(10), I(20), I(15)));
        assertTrue(result.asArray().get(0).isNull());
        assertEquals(10, result.asArray().get(1).asInt64());
        assertEquals(-5, result.asArray().get(2).asInt64());
    }

    @Test
    void diff_empty() {
        assertTrue(invoke("diff", Arr()).asArray().isEmpty());
    }

    @Test
    void diff_singleElement() {
        var result = invoke("diff", Arr(I(5)));
        assertEquals(1, result.asArray().size());
        assertTrue(result.asArray().get(0).isNull());
    }

    @Test
    void diff_floats() {
        var result = invoke("diff", Arr(F(1.0), F(3.0), F(6.0)));
        assertTrue(result.asArray().get(0).isNull());
        assertEquals(2, result.asArray().get(1).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    // =========================================================================
    // pctChange
    // =========================================================================

    @Test
    void pctChange_basic() {
        var result = invoke("pctChange", Arr(I(100), I(110)));
        assertTrue(result.asArray().get(0).isNull());
        assertTrue(Math.abs(result.asArray().get(1).asDouble() - 0.1) < 1e-10);
    }

    @Test
    void pctChange_empty() {
        assertTrue(invoke("pctChange", Arr()).asArray().isEmpty());
    }

    @Test
    void pctChange_single() {
        var result = invoke("pctChange", Arr(I(100)));
        assertTrue(result.asArray().get(0).isNull());
    }

    @Test
    void pctChange_doubling() {
        var result = invoke("pctChange", Arr(I(100), I(200)));
        assertTrue(Math.abs(result.asArray().get(1).asDouble() - 1.0) < 1e-10);
    }

    @Test
    void pctChange_withZeroPrevious() {
        var result = invoke("pctChange", Arr(I(0), I(100)));
        assertTrue(result.asArray().get(1).isNull()); // Division by zero -> null
    }

    // =========================================================================
    // shift
    // =========================================================================

    @Test
    void shift_forward() {
        var result = invoke("shift", Arr(I(1), I(2), I(3)), I(1));
        assertTrue(result.asArray().get(0).isNull());
        assertEquals(1, result.asArray().get(1).asInt64());
        assertEquals(2, result.asArray().get(2).asInt64());
    }

    @Test
    void shift_backward() {
        var result = invoke("shift", Arr(I(1), I(2), I(3)), I(-1));
        assertEquals(2, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(1).asInt64());
        assertTrue(result.asArray().get(2).isNull());
    }

    @Test
    void shift_zeroNoChange() {
        var result = invoke("shift", Arr(I(1), I(2), I(3)), I(0));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    // =========================================================================
    // lag
    // =========================================================================

    @Test
    void lag_defaultPeriod() {
        var result = invoke("lag", Arr(I(10), I(20), I(30)));
        assertTrue(result.asArray().get(0).isNull());
        assertEquals(10, result.asArray().get(1).asInt64());
        assertEquals(20, result.asArray().get(2).asInt64());
    }

    @Test
    void lag_periodTwo() {
        var result = invoke("lag", Arr(I(10), I(20), I(30), I(40)), I(2));
        assertTrue(result.asArray().get(0).isNull());
        assertTrue(result.asArray().get(1).isNull());
        assertEquals(10, result.asArray().get(2).asInt64());
        assertEquals(20, result.asArray().get(3).asInt64());
    }

    // =========================================================================
    // lead
    // =========================================================================

    @Test
    void lead_defaultPeriod() {
        var result = invoke("lead", Arr(I(10), I(20), I(30)));
        assertEquals(20, result.asArray().get(0).asInt64());
        assertEquals(30, result.asArray().get(1).asInt64());
        assertTrue(result.asArray().get(2).isNull());
    }

    @Test
    void lead_periodTwo() {
        var result = invoke("lead", Arr(I(10), I(20), I(30), I(40)), I(2));
        assertEquals(30, result.asArray().get(0).asInt64());
        assertEquals(40, result.asArray().get(1).asInt64());
        assertTrue(result.asArray().get(2).isNull());
        assertTrue(result.asArray().get(3).isNull());
    }

    // =========================================================================
    // rank
    // =========================================================================

    @Test
    void rank_basic() {
        var result = invoke("rank", Arr(I(10), I(30), I(20)));
        // Highest=rank 1 (descending). 30->1, 20->2, 10->3
        assertEquals(3, result.asArray().get(0).asInt64()); // 10 -> rank 3
        assertEquals(1, result.asArray().get(1).asInt64()); // 30 -> rank 1
        assertEquals(2, result.asArray().get(2).asInt64()); // 20 -> rank 2
    }

    @Test
    void rank_tiedValues() {
        var result = invoke("rank", Arr(I(10), I(10), I(30)));
        // Both 10s get same rank
        assertEquals(result.asArray().get(0).asInt64(), result.asArray().get(1).asInt64());
    }

    @Test
    void rank_singleElement() {
        var result = invoke("rank", Arr(I(42)));
        assertEquals(1, result.asArray().get(0).asInt64());
    }

    // =========================================================================
    // fillMissing
    // =========================================================================

    @Test
    void fillMissing_valueStrategy() {
        var result = invoke("fillMissing", Arr(I(1), Null(), I(3)), I(0));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(0, result.asArray().get(1).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    @Test
    void fillMissing_forward() {
        var result = invoke("fillMissing", Arr(I(1), Null(), Null(), I(4)), S("forward"));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(1, result.asArray().get(1).asInt64());
        assertEquals(1, result.asArray().get(2).asInt64());
        assertEquals(4, result.asArray().get(3).asInt64());
    }

    @Test
    void fillMissing_backward() {
        var result = invoke("fillMissing", Arr(Null(), Null(), I(3), I(4)), S("backward"));
        assertEquals(3, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(1).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
        assertEquals(4, result.asArray().get(3).asInt64());
    }

    @Test
    void fillMissing_noNulls() {
        var result = invoke("fillMissing", Arr(I(1), I(2), I(3)), I(0));
        assertEquals(3, result.asArray().size());
    }

    @Test
    void fillMissing_allNulls() {
        var result = invoke("fillMissing", Arr(Null(), Null()), I(99));
        assertEquals(99, result.asArray().get(0).asInt64());
        assertEquals(99, result.asArray().get(1).asInt64());
    }

    @Test
    void fillMissing_emptyArray() {
        assertTrue(invoke("fillMissing", Arr(), I(0)).asArray().isEmpty());
    }

    // =========================================================================
    // sample
    // =========================================================================

    @Test
    void sample_takesNElements() {
        var data = Arr(I(1), I(2), I(3), I(4));
        var result = invoke("sample", data, I(2));
        assertEquals(2, result.asArray().size());
    }

    @Test
    void sample_zeroCount() {
        assertTrue(invoke("sample", Arr(I(1), I(2)), I(0)).asArray().isEmpty());
    }

    @Test
    void sample_moreThanLength() {
        var data = Arr(I(1), I(2));
        assertEquals(2, invoke("sample", data, I(10)).asArray().size());
    }

    @Test
    void sample_emptyArray() {
        assertTrue(invoke("sample", Arr(), I(5)).asArray().isEmpty());
    }

    // =========================================================================
    // limit (alias for take)
    // =========================================================================

    @Test
    void limit_basicN() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("limit", data, I(2));
        assertEquals(2, result.asArray().size());
    }

    @Test
    void limit_zero() {
        assertTrue(invoke("limit", Arr(I(1), I(2)), I(0)).asArray().isEmpty());
    }

    @Test
    void limit_exactLength() {
        var data = Arr(I(1), I(2));
        assertEquals(2, invoke("limit", data, I(2)).asArray().size());
    }

    // =========================================================================
    // rowNumber
    // =========================================================================

    @Test
    void rowNumber_increments() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        var r1 = reg.invoke("rowNumber", new DynValue[]{}, freshCtx);
        var r2 = reg.invoke("rowNumber", new DynValue[]{}, freshCtx);
        assertEquals(1, r1.asInt64());
        assertEquals(2, r2.asInt64());
    }

    // =========================================================================
    // accumulate
    // =========================================================================

    @Test
    void accumulate_sumOp() {
        var freshCtx = new TransformEngine.VerbContext();
        freshCtx.getAccumulators().put("total", I(10));
        var reg = new VerbRegistry();
        var result = reg.invoke("accumulate", new DynValue[]{ S("total"), S("sum"), I(5) }, freshCtx);
        assertEquals(15, result.asInt64());
    }

    @Test
    void accumulate_countOp() {
        var freshCtx = new TransformEngine.VerbContext();
        freshCtx.getAccumulators().put("cnt", I(0));
        var reg = new VerbRegistry();
        var result = reg.invoke("accumulate", new DynValue[]{ S("cnt"), S("count"), I(0) }, freshCtx);
        assertEquals(1, result.asInt64());
    }

    @Test
    void accumulate_firstOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        var r1 = reg.invoke("accumulate", new DynValue[]{ S("f"), S("first"), S("hello") }, freshCtx);
        var r2 = reg.invoke("accumulate", new DynValue[]{ S("f"), S("first"), S("world") }, freshCtx);
        assertEquals("hello", r1.asString());
        assertEquals("hello", r2.asString()); // first stays
    }

    @Test
    void accumulate_lastOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("l"), S("last"), S("hello") }, freshCtx);
        var r2 = reg.invoke("accumulate", new DynValue[]{ S("l"), S("last"), S("world") }, freshCtx);
        assertEquals("world", r2.asString());
    }

    // =========================================================================
    // set
    // =========================================================================

    @Test
    void set_returnsValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        var result = reg.invoke("set", new DynValue[]{ S("counter"), I(42) }, freshCtx);
        assertEquals(42, result.asInt64());
    }

    @Test
    void set_stringValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertEquals("hello", reg.invoke("set", new DynValue[]{ S("name"), S("hello") }, freshCtx).asString());
    }

    // =========================================================================
    // Nested/chained operations
    // =========================================================================

    @Test
    void nested_sortThenTake() {
        var data = Arr(I(5), I(1), I(3), I(2), I(4));
        var sorted = invoke("sort", data);
        var result = invoke("take", sorted, I(3));
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(3, result.asArray().get(2).asInt64());
    }

    @Test
    void nested_flattenThenDistinct() {
        var data = Arr(Arr(I(1), I(2)), Arr(I(2), I(3)));
        var flat = invoke("flatten", data);
        var result = invoke("distinct", flat);
        assertEquals(3, result.asArray().size());
    }

    @Test
    void nested_concatThenSort() {
        var a = Arr(I(3), I(1));
        var b = Arr(I(4), I(2));
        var combined = invoke("concatArrays", a, b);
        var result = invoke("sort", combined);
        assertEquals(1, result.asArray().get(0).asInt64());
        assertEquals(4, result.asArray().get(3).asInt64());
    }

    @Test
    void nested_reverseThenFirst() {
        var data = Arr(I(1), I(2), I(3));
        var reversed = invoke("reverse", data);
        assertEquals(3, invoke("first", reversed).asInt64());
    }

    @Test
    void nested_chunkThenFirst() {
        var data = Arr(I(1), I(2), I(3), I(4));
        var chunks = invoke("chunk", data, I(2));
        var first = invoke("first", chunks);
        assertEquals(2, first.asArray().size());
    }

    @Test
    void nested_dropThenCount() {
        var data = Arr(I(1), I(2), I(3), I(4), I(5));
        var dropped = invoke("drop", data, I(2));
        assertEquals(3, invoke("count", dropped).asInt64());
    }

    @Test
    void nested_compactThenSum() {
        var data = Arr(I(1), Null(), I(2), S(""), I(3));
        var compacted = invoke("compact", data);
        assertEquals(6, invoke("sum", compacted).asInt64());
    }
}
