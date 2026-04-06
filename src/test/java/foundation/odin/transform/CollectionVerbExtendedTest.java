package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for collection, array, object, aggregation, and time-series verbs.
 * Ported from .NET CollectionVerbExtendedTests.cs (which was ported from Rust SDK).
 */
class CollectionVerbExtendedTest {

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

    private static void assertArrayLength(DynValue result, int expectedLen) {
        var arr = result.asArray();
        assertNotNull(arr);
        assertEquals(expectedLen, arr.size());
    }

    // =========================================================================
    // flatten -- extended
    // =========================================================================

    @Test
    void flatten_singleLevelOnly() {
        var data = Arr(Arr(Arr(I(1))));
        var result = invoke("flatten", data);
        // Only flattens one level
        assertEquals(Arr(Arr(I(1))), result);
    }

    @Test
    void flatten_allScalars() {
        var data = Arr(I(1), I(2), I(3));
        assertEquals(Arr(I(1), I(2), I(3)), invoke("flatten", data));
    }

    @Test
    void flatten_withNulls() {
        var data = Arr(Null(), Arr(I(1)));
        assertEquals(Arr(Null(), I(1)), invoke("flatten", data));
    }

    @Test
    void flatten_emptyArray() {
        assertEquals(Arr(), invoke("flatten", Arr()));
    }

    @Test
    void flatten_mixedNestedAndScalar() {
        var data = Arr(I(1), Arr(I(2), I(3)), I(4));
        assertEquals(Arr(I(1), I(2), I(3), I(4)), invoke("flatten", data));
    }

    // =========================================================================
    // distinct / unique -- extended
    // =========================================================================

    @Test
    void distinct_singleElement() {
        assertEquals(Arr(I(42)), invoke("distinct", Arr(I(42))));
    }

    @Test
    void distinct_allSame() {
        assertEquals(Arr(S("a")), invoke("distinct", Arr(S("a"), S("a"), S("a"))));
    }

    @Test
    void distinct_mixedTypes() {
        var result = invoke("distinct", Arr(I(1), S("1"), B(true), Null()));
        assertArrayLength(result, 4);
    }

    @Test
    void distinct_preservesOrder() {
        assertEquals(Arr(I(3), I(1), I(2)), invoke("distinct", Arr(I(3), I(1), I(2), I(1), I(3))));
    }

    @Test
    void distinct_withNulls() {
        assertEquals(Arr(Null(), I(1), I(2)), invoke("distinct", Arr(Null(), I(1), Null(), I(2))));
    }

    @Test
    void unique_isAliasForDistinct() {
        assertEquals(Arr(I(1), I(2)), invoke("unique", Arr(I(1), I(2), I(1))));
    }

    // =========================================================================
    // sort -- extended
    // =========================================================================

    @Test
    void sort_mixedIntFloat() {
        var result = invoke("sort", Arr(F(2.5), I(1), I(3)));
        var arr = result.asArray();
        assertEquals(I(1), arr.get(0));
        assertEquals(F(2.5), arr.get(1));
        assertEquals(I(3), arr.get(2));
    }

    @Test
    void sort_alreadySorted() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("sort", Arr(I(1), I(2), I(3))));
    }

    @Test
    void sort_reverseOrder() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("sort", Arr(I(3), I(2), I(1))));
    }

    @Test
    void sort_withDuplicates() {
        assertEquals(Arr(I(1), I(1), I(2), I(2)), invoke("sort", Arr(I(2), I(1), I(2), I(1))));
    }

    @Test
    void sort_stringsCaseSensitive() {
        // Uppercase A comes before lowercase b in ASCII
        assertEquals(Arr(S("Apple"), S("banana"), S("cherry")),
            invoke("sort", Arr(S("banana"), S("Apple"), S("cherry"))));
    }

    // =========================================================================
    // sortDesc -- extended
    // =========================================================================

    @Test
    void sortDesc_strings() {
        assertEquals(Arr(S("c"), S("b"), S("a")), invoke("sortDesc", Arr(S("a"), S("c"), S("b"))));
    }

    @Test
    void sortDesc_floats() {
        assertEquals(Arr(F(3.3), F(2.2), F(1.1)), invoke("sortDesc", Arr(F(1.1), F(3.3), F(2.2))));
    }

    @Test
    void sortDesc_single() {
        assertEquals(Arr(I(5)), invoke("sortDesc", Arr(I(5))));
    }

    @Test
    void sortDesc_preservesAllElements() {
        assertEquals(Arr(I(5), I(4), I(3), I(1), I(1)),
            invoke("sortDesc", Arr(I(3), I(1), I(4), I(1), I(5))));
    }

    // =========================================================================
    // sortBy -- extended
    // =========================================================================

    @Test
    void sortBy_stringField() {
        var data = Arr(
            Obj(e("name", S("Charlie"))),
            Obj(e("name", S("Alice"))),
            Obj(e("name", S("Bob")))
        );
        var result = invoke("sortBy", data, S("name"));
        var arr = result.asArray();
        assertEquals(S("Alice"), arr.get(0).get("name"));
        assertEquals(S("Bob"), arr.get(1).get("name"));
        assertEquals(S("Charlie"), arr.get(2).get("name"));
    }

    @Test
    void sortBy_emptyArray() {
        assertEquals(Arr(), invoke("sortBy", Arr(), S("x")));
    }

    @Test
    void sortBy_missingField() {
        var data = Arr(Obj(e("a", I(2))), Obj(e("b", I(1))));
        var result = invoke("sortBy", data, S("a"));
        assertArrayLength(result, 2);
    }

    // =========================================================================
    // map / pluck -- extended
    // =========================================================================

    @Test
    void map_emptyArray() {
        assertEquals(Arr(), invoke("map", Arr(), S("x")));
    }

    @Test
    void map_allMissingFields() {
        var data = Arr(Obj(e("a", I(1))), Obj(e("a", I(2))));
        assertEquals(Arr(Null(), Null()), invoke("map", data, S("z")));
    }

    @Test
    void pluck_emptyArray() {
        assertEquals(Arr(), invoke("pluck", Arr(), S("x")));
    }

    @Test
    void pluck_extractsField() {
        var data = Arr(
            Obj(e("name", S("A")), e("age", I(10))),
            Obj(e("name", S("B")), e("age", I(20)))
        );
        assertEquals(Arr(I(10), I(20)), invoke("pluck", data, S("age")));
    }

    @Test
    void pluck_missingFieldGivesNull() {
        var data = Arr(Obj(e("x", I(1))));
        assertEquals(Arr(Null()), invoke("pluck", data, S("y")));
    }

    // =========================================================================
    // indexOf -- extended
    // =========================================================================

    @Test
    void indexOf_firstOccurrence() {
        assertEquals(I(0), invoke("indexOf", Arr(I(1), I(2), I(1)), I(1)));
    }

    @Test
    void indexOf_emptyArray() {
        assertEquals(I(-1), invoke("indexOf", Arr(), I(1)));
    }

    @Test
    void indexOf_stringValue() {
        assertEquals(I(1), invoke("indexOf", Arr(S("hello"), S("world")), S("world")));
    }

    // =========================================================================
    // at -- extended
    // =========================================================================

    @Test
    void at_firstElement() {
        assertEquals(S("first"), invoke("at", Arr(S("first"), S("second")), I(0)));
    }

    @Test
    void at_lastElement() {
        assertEquals(I(3), invoke("at", Arr(I(1), I(2), I(3)), I(2)));
    }

    @Test
    void at_emptyArray() {
        assertTrue(invoke("at", Arr(), I(0)).isNull());
    }

    @Test
    void at_negativeIndex() {
        assertEquals(I(3), invoke("at", Arr(I(1), I(2), I(3)), I(-1)));
    }

    // =========================================================================
    // slice -- extended
    // =========================================================================

    @Test
    void slice_fullArray() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("slice", Arr(I(1), I(2), I(3)), I(0), I(3)));
    }

    @Test
    void slice_emptyArray() {
        assertEquals(Arr(), invoke("slice", Arr(), I(0), I(0)));
    }

    @Test
    void slice_singleElement() {
        assertEquals(Arr(I(20)), invoke("slice", Arr(I(10), I(20), I(30)), I(1), I(2)));
    }

    @Test
    void slice_startEqualToEndPastLength() {
        assertEquals(Arr(), invoke("slice", Arr(I(1)), I(5), I(10)));
    }

    @Test
    void slice_negativeStart() {
        var result = invoke("slice", Arr(I(1), I(2), I(3), I(4), I(5)), I(-2));
        assertArrayLength(result, 2);
    }

    // =========================================================================
    // reverse -- extended
    // =========================================================================

    @Test
    void reverse_strings() {
        assertEquals(Arr(S("c"), S("b"), S("a")), invoke("reverse", Arr(S("a"), S("b"), S("c"))));
    }

    @Test
    void reverse_twoElements() {
        assertEquals(Arr(I(2), I(1)), invoke("reverse", Arr(I(1), I(2))));
    }

    @Test
    void reverse_emptyArray() {
        assertEquals(Arr(), invoke("reverse", Arr()));
    }

    // =========================================================================
    // every -- extended
    // =========================================================================

    @Test
    void every_allTruthy() {
        assertEquals(B(true), invoke("every", Arr(I(1), I(2), I(3))));
    }

    @Test
    void every_hasFalse() {
        assertEquals(B(false), invoke("every", Arr(I(1), B(false), I(3))));
    }

    @Test
    void every_emptyIsTrue() {
        assertEquals(B(true), invoke("every", Arr()));
    }

    @Test
    void every_fieldTruthy() {
        var data = Arr(Obj(e("v", I(10))), Obj(e("v", I(20))));
        assertEquals(B(true), invoke("every", data, S("v")));
    }

    @Test
    void every_fieldFalsy() {
        var data = Arr(Obj(e("v", I(10))), Obj(e("v", I(0))));
        assertEquals(B(false), invoke("every", data, S("v")));
    }

    // =========================================================================
    // some -- extended
    // =========================================================================

    @Test
    void some_allTruthy() {
        assertEquals(B(true), invoke("some", Arr(I(1), I(2))));
    }

    @Test
    void some_allFalsy() {
        assertEquals(B(false), invoke("some", Arr(I(0), B(false), Null())));
    }

    @Test
    void some_emptyIsFalse() {
        assertEquals(B(false), invoke("some", Arr()));
    }

    @Test
    void some_fieldTruthy() {
        var data = Arr(Obj(e("v", I(0))), Obj(e("v", I(10))));
        assertEquals(B(true), invoke("some", data, S("v")));
    }

    // =========================================================================
    // find -- extended
    // =========================================================================

    @Test
    void find_emptyArray() {
        assertTrue(invoke("find", Arr()).isNull());
    }

    @Test
    void find_returnsFirstTruthy() {
        assertEquals(I(2), invoke("find", Arr(I(0), I(2), I(3))));
    }

    @Test
    void find_byField() {
        var data = Arr(Obj(e("v", I(0))), Obj(e("v", I(10))));
        var result = invoke("find", data, S("v"));
        assertEquals(I(10), result.get("v"));
    }

    // =========================================================================
    // findIndex -- extended
    // =========================================================================

    @Test
    void findIndex_emptyArray() {
        assertEquals(I(-1), invoke("findIndex", Arr()));
    }

    @Test
    void findIndex_firstTruthy() {
        assertEquals(I(1), invoke("findIndex", Arr(I(0), I(5), I(10))));
    }

    // =========================================================================
    // includes -- extended
    // =========================================================================

    @Test
    void includes_stringValue() {
        assertEquals(B(true), invoke("includes", Arr(S("hello"), S("world")), S("hello")));
    }

    @Test
    void includes_integerPresent() {
        assertEquals(B(true), invoke("includes", Arr(I(1), I(2), I(3)), I(2)));
    }

    @Test
    void includes_floatPresent() {
        assertEquals(B(true), invoke("includes", Arr(F(1.5), F(2.5)), F(2.5)));
    }

    @Test
    void includes_boolAbsent() {
        assertEquals(B(false), invoke("includes", Arr(B(true)), B(false)));
    }

    @Test
    void includes_nullInArray() {
        assertEquals(B(true), invoke("includes", Arr(I(1), Null(), I(3)), Null()));
    }

    // =========================================================================
    // concatArrays -- extended
    // =========================================================================

    @Test
    void concatArrays_bothEmpty() {
        assertEquals(Arr(), invoke("concatArrays", Arr(), Arr()));
    }

    @Test
    void concatArrays_firstEmpty() {
        assertEquals(Arr(I(1)), invoke("concatArrays", Arr(), Arr(I(1))));
    }

    @Test
    void concatArrays_mixedTypes() {
        var result = invoke("concatArrays", Arr(I(1), S("two")), Arr(B(true), Null()));
        assertArrayLength(result, 4);
    }

    @Test
    void concatArrays_nestedArrays() {
        var result = invoke("concatArrays", Arr(Arr(I(1))), Arr(Arr(I(2))));
        assertEquals(Arr(Arr(I(1)), Arr(I(2))), result);
    }

    // =========================================================================
    // zip -- extended
    // =========================================================================

    @Test
    void zip_bothEmpty() {
        assertEquals(Arr(), invoke("zip", Arr(), Arr()));
    }

    @Test
    void zip_singleElements() {
        assertEquals(Arr(Arr(I(1), S("a"))), invoke("zip", Arr(I(1)), Arr(S("a"))));
    }

    @Test
    void zip_mixedTypes() {
        var result = invoke("zip", Arr(I(1), S("two")), Arr(B(true), Null()));
        assertEquals(Arr(Arr(I(1), B(true)), Arr(S("two"), Null())), result);
    }

    @Test
    void zip_unequalLengthPadsWithNull() {
        var result = invoke("zip", Arr(I(1), I(2), I(3)), Arr(S("a")));
        assertArrayLength(result, 3);
        var arr = result.asArray();
        assertEquals(Arr(I(1), S("a")), arr.get(0));
    }

    // =========================================================================
    // groupBy -- extended
    // =========================================================================

    @Test
    void groupBy_emptyArray() {
        var result = invoke("groupBy", Arr(), S("key"));
        var obj = result.asObject();
        assertNotNull(obj);
        assertEquals(0, obj.size());
    }

    @Test
    void groupBy_singleGroup() {
        var data = Arr(
            Obj(e("type", S("A")), e("v", I(1))),
            Obj(e("type", S("A")), e("v", I(2)))
        );
        var result = invoke("groupBy", data, S("type"));
        var obj = result.asObject();
        assertEquals(1, obj.size());
        assertEquals("A", obj.get(0).getKey());
    }

    @Test
    void groupBy_missingFieldUsesNullKey() {
        var data = Arr(
            Obj(e("v", I(1))),
            Obj(e("type", S("A")), e("v", I(2)))
        );
        var result = invoke("groupBy", data, S("type"));
        var obj = result.asObject();
        assertEquals(2, obj.size());
    }

    @Test
    void groupBy_integerField() {
        var data = Arr(
            Obj(e("score", I(10))),
            Obj(e("score", I(20))),
            Obj(e("score", I(10)))
        );
        var result = invoke("groupBy", data, S("score"));
        var obj = result.asObject();
        assertEquals(2, obj.size());
    }

    @Test
    void groupBy_boolField() {
        var data = Arr(
            Obj(e("active", B(true)), e("name", S("A"))),
            Obj(e("active", B(false)), e("name", S("B"))),
            Obj(e("active", B(true)), e("name", S("C")))
        );
        var result = invoke("groupBy", data, S("active"));
        var obj = result.asObject();
        assertEquals(2, obj.size());
    }

    @Test
    void groupBy_allSameKey() {
        var data = Arr(
            Obj(e("k", S("x")), e("v", I(1))),
            Obj(e("k", S("x")), e("v", I(2)))
        );
        var result = invoke("groupBy", data, S("k"));
        var obj = result.asObject();
        assertEquals(1, obj.size());
        assertEquals("x", obj.get(0).getKey());
    }

    // =========================================================================
    // partition -- extended
    // =========================================================================

    @Test
    void partition_allMatch() {
        var data = Arr(I(1), I(2), I(3));
        var result = invoke("partition", data);
        var parts = result.asArray();
        assertEquals(3, parts.get(0).asArray().size());
        assertTrue(parts.get(1).asArray().isEmpty());
    }

    @Test
    void partition_noneMatch() {
        var data = Arr(I(0), B(false), Null());
        var result = invoke("partition", data);
        var parts = result.asArray();
        assertTrue(parts.get(0).asArray().isEmpty());
        assertEquals(3, parts.get(1).asArray().size());
    }

    @Test
    void partition_emptyArray() {
        var result = invoke("partition", Arr());
        var parts = result.asArray();
        assertTrue(parts.get(0).asArray().isEmpty());
        assertTrue(parts.get(1).asArray().isEmpty());
    }

    @Test
    void partition_byField() {
        var data = Arr(Obj(e("v", I(10))), Obj(e("v", I(0))));
        var result = invoke("partition", data, S("v"));
        var parts = result.asArray();
        assertEquals(1, parts.get(0).asArray().size());
        assertEquals(1, parts.get(1).asArray().size());
    }

    // =========================================================================
    // take -- extended
    // =========================================================================

    @Test
    void take_zero() {
        assertEquals(Arr(), invoke("take", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void take_emptyArray() {
        assertEquals(Arr(), invoke("take", Arr(), I(5)));
    }

    @Test
    void take_exactLength() {
        assertEquals(Arr(I(1), I(2)), invoke("take", Arr(I(1), I(2)), I(2)));
    }

    @Test
    void take_moreThanLength() {
        assertEquals(Arr(I(1)), invoke("take", Arr(I(1)), I(10)));
    }

    @Test
    void take_fromMixedTypes() {
        assertEquals(Arr(I(1), S("two"), B(true)),
            invoke("take", Arr(I(1), S("two"), B(true), Null()), I(3)));
    }

    // =========================================================================
    // drop -- extended
    // =========================================================================

    @Test
    void drop_zero() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("drop", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void drop_emptyArray() {
        assertEquals(Arr(), invoke("drop", Arr(), I(5)));
    }

    @Test
    void drop_exactLength() {
        assertEquals(Arr(), invoke("drop", Arr(I(1), I(2)), I(2)));
    }

    @Test
    void drop_moreThanLength() {
        assertEquals(Arr(), invoke("drop", Arr(I(1), I(2)), I(10)));
    }

    @Test
    void drop_fromMixedTypes() {
        assertEquals(Arr(I(1), B(false)), invoke("drop", Arr(S("a"), I(1), B(false)), I(1)));
    }

    // =========================================================================
    // chunk -- extended
    // =========================================================================

    @Test
    void chunk_singleElement() {
        assertEquals(Arr(Arr(I(1))), invoke("chunk", Arr(I(1)), I(1)));
    }

    @Test
    void chunk_sizeLargerThanArray() {
        assertEquals(Arr(Arr(I(1), I(2))), invoke("chunk", Arr(I(1), I(2)), I(10)));
    }

    @Test
    void chunk_emptyArray() {
        assertEquals(Arr(), invoke("chunk", Arr(), I(3)));
    }

    @Test
    void chunk_sizeOne() {
        assertEquals(Arr(Arr(I(1)), Arr(I(2)), Arr(I(3))),
            invoke("chunk", Arr(I(1), I(2), I(3)), I(1)));
    }

    @Test
    void chunk_sizeEqualsLength() {
        assertEquals(Arr(Arr(I(1), I(2), I(3))),
            invoke("chunk", Arr(I(1), I(2), I(3)), I(3)));
    }

    @Test
    void chunk_sizeTwoOddLength() {
        assertEquals(Arr(Arr(I(1), I(2)), Arr(I(3), I(4)), Arr(I(5))),
            invoke("chunk", Arr(I(1), I(2), I(3), I(4), I(5)), I(2)));
    }

    // =========================================================================
    // range -- extended
    // =========================================================================

    @Test
    void range_singleElement() {
        assertEquals(Arr(I(0)), invoke("range", I(0), I(1)));
    }

    @Test
    void range_negativeValues() {
        assertEquals(Arr(I(-3), I(-2), I(-1)), invoke("range", I(-3), I(0)));
    }

    @Test
    void range_sameStartEnd() {
        assertEquals(Arr(), invoke("range", I(5), I(5)));
    }

    @Test
    void range_stepOfTwo() {
        assertEquals(Arr(I(0), I(2), I(4)), invoke("range", I(0), I(6), I(2)));
    }

    @Test
    void range_largeStep() {
        assertEquals(Arr(I(0), I(5)), invoke("range", I(0), I(10), I(5)));
    }

    @Test
    void range_descending() {
        assertEquals(Arr(I(5), I(4), I(3), I(2), I(1)), invoke("range", I(5), I(0), I(-1)));
    }

    @Test
    void range_stepThree() {
        assertEquals(Arr(I(1), I(4), I(7)), invoke("range", I(1), I(10), I(3)));
    }

    // =========================================================================
    // compact -- extended
    // =========================================================================

    @Test
    void compact_noNulls() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("compact", Arr(I(1), I(2), I(3))));
    }

    @Test
    void compact_emptyArray() {
        assertEquals(Arr(), invoke("compact", Arr()));
    }

    @Test
    void compact_onlyEmptyStrings() {
        assertEquals(Arr(), invoke("compact", Arr(S(""), S(""), S(""))));
    }

    @Test
    void compact_keepsNonEmptyStrings() {
        assertEquals(Arr(S("hello"), S("world")),
            invoke("compact", Arr(S(""), S("hello"), Null(), S("world"))));
    }

    @Test
    void compact_keepsZerosAndFalse() {
        var result = invoke("compact", Arr(I(0), B(false), Null(), S("")));
        var arr = result.asArray();
        assertEquals(2, arr.size()); // 0 and false are kept
    }

    @Test
    void compact_keepsFalseAndZero() {
        assertEquals(Arr(B(false), I(0), S("ok")),
            invoke("compact", Arr(B(false), I(0), Null(), S(""), S("ok"))));
    }

    @Test
    void compact_allValid() {
        assertEquals(Arr(I(1), S("a"), B(true)),
            invoke("compact", Arr(I(1), S("a"), B(true))));
    }

    // =========================================================================
    // dedupe -- extended
    // =========================================================================

    @Test
    void dedupe_emptyArray() {
        assertEquals(Arr(), invoke("dedupe", Arr()));
    }

    @Test
    void dedupe_noDuplicates() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("dedupe", Arr(I(1), I(2), I(3))));
    }

    @Test
    void dedupe_consecutiveDuplicates() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("dedupe", Arr(I(1), I(1), I(2), I(2), I(3))));
    }

    @Test
    void dedupe_nonConsecutiveDuplicatesKept() {
        // dedupe only removes consecutive duplicates
        assertEquals(Arr(I(1), I(2), I(1)), invoke("dedupe", Arr(I(1), I(2), I(1))));
    }

    // =========================================================================
    // cumsum -- extended
    // =========================================================================

    @Test
    void cumsum_singleElement() {
        assertEquals(Arr(I(5)), invoke("cumsum", Arr(I(5))));
    }

    @Test
    void cumsum_floats() {
        var result = invoke("cumsum", Arr(F(1.5), F(2.5), F(3.0)));
        var arr = result.asArray();
        assertEquals(F(1.5), arr.get(0));
        // 1.5 + 2.5 = 4.0 -> Integer(4)
        assertEquals(I(4), arr.get(1));
        // 4.0 + 3.0 = 7.0 -> Integer(7)
        assertEquals(I(7), arr.get(2));
    }

    @Test
    void cumsum_negativeNumbers() {
        assertEquals(Arr(I(5), I(2), I(4)), invoke("cumsum", Arr(I(5), I(-3), I(2))));
    }

    @Test
    void cumsum_allNulls() {
        assertEquals(Arr(Null(), Null()), invoke("cumsum", Arr(Null(), Null())));
    }

    @Test
    void cumsum_mixedNullInt() {
        var result = invoke("cumsum", Arr(I(1), Null(), I(3)));
        var arr = result.asArray();
        assertEquals(I(1), arr.get(0));
        assertTrue(arr.get(1).isNull());
        assertEquals(I(4), arr.get(2)); // 1+3=4
    }

    // =========================================================================
    // cumprod -- extended
    // =========================================================================

    @Test
    void cumprod_singleElement() {
        assertEquals(Arr(I(5)), invoke("cumprod", Arr(I(5))));
    }

    @Test
    void cumprod_empty() {
        assertEquals(Arr(), invoke("cumprod", Arr()));
    }

    @Test
    void cumprod_floats() {
        var result = invoke("cumprod", Arr(F(2.0), F(3.0), F(4.0)));
        var arr = result.asArray();
        assertEquals(I(2), arr.get(0));
        assertEquals(I(6), arr.get(1));
        assertEquals(I(24), arr.get(2));
    }

    @Test
    void cumprod_withOnes() {
        assertEquals(Arr(I(1), I(1), I(1)), invoke("cumprod", Arr(I(1), I(1), I(1))));
    }

    @Test
    void cumprod_withNegative() {
        var result = invoke("cumprod", Arr(I(2), I(-3)));
        var arr = result.asArray();
        assertEquals(I(2), arr.get(0));
        assertEquals(I(-6), arr.get(1));
    }

    // =========================================================================
    // diff -- extended
    // =========================================================================

    @Test
    void diff_emptyArray() {
        assertEquals(Arr(), invoke("diff", Arr()));
    }

    @Test
    void diff_singleElement() {
        assertEquals(Arr(Null()), invoke("diff", Arr(I(5))));
    }

    @Test
    void diff_floats() {
        var result = invoke("diff", Arr(F(1.0), F(3.0), F(6.0)));
        var arr = result.asArray();
        assertTrue(arr.get(0).isNull());
        assertEquals(I(2), arr.get(1));
        assertEquals(I(3), arr.get(2));
    }

    @Test
    void diff_integers() {
        var result = invoke("diff", Arr(I(10), I(20), I(50)));
        var arr = result.asArray();
        assertTrue(arr.get(0).isNull());
        assertEquals(I(10), arr.get(1));
        assertEquals(I(30), arr.get(2));
    }

    // =========================================================================
    // pctChange -- extended
    // =========================================================================

    @Test
    void pctChange_empty() {
        assertEquals(Arr(), invoke("pctChange", Arr()));
    }

    @Test
    void pctChange_single() {
        assertEquals(Arr(Null()), invoke("pctChange", Arr(I(100))));
    }

    @Test
    void pctChange_doubling() {
        var result = invoke("pctChange", Arr(I(100), I(200)));
        var arr = result.asArray();
        assertTrue(arr.get(0).isNull());
        assertTrue(Math.abs(arr.get(1).asDouble() - 1.0) < 1e-10);
    }

    @Test
    void pctChange_withZeroPrevious() {
        var result = invoke("pctChange", Arr(I(0), I(100)));
        var arr = result.asArray();
        assertTrue(arr.get(1).isNull()); // Division by zero -> null
    }

    @Test
    void pctChange_decrease() {
        var result = invoke("pctChange", Arr(I(100), I(75)));
        var arr = result.asArray();
        assertTrue(Math.abs(arr.get(1).asDouble() - (-0.25)) < 1e-10);
    }

    // =========================================================================
    // shift -- extended
    // =========================================================================

    @Test
    void shift_zeroNoChange() {
        assertEquals(Arr(I(1), I(2), I(3)), invoke("shift", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void shift_rightByOne() {
        assertEquals(Arr(Null(), I(1), I(2)), invoke("shift", Arr(I(1), I(2), I(3)), I(1)));
    }

    @Test
    void shift_leftByTwo() {
        assertEquals(Arr(I(3), I(4), Null(), Null()),
            invoke("shift", Arr(I(1), I(2), I(3), I(4)), I(-2)));
    }

    // =========================================================================
    // lag -- extended
    // =========================================================================

    @Test
    void lag_defaultPeriodOne() {
        assertEquals(Arr(Null(), I(10), I(20)),
            invoke("lag", Arr(I(10), I(20), I(30))));
    }

    @Test
    void lag_periodTwo() {
        assertEquals(Arr(Null(), Null(), I(10), I(20)),
            invoke("lag", Arr(I(10), I(20), I(30), I(40)), I(2)));
    }

    @Test
    void lag_periodThree() {
        assertEquals(Arr(Null(), Null(), Null(), I(10), I(20)),
            invoke("lag", Arr(I(10), I(20), I(30), I(40), I(50)), I(3)));
    }

    @Test
    void lag_emptyArray() {
        assertEquals(Arr(), invoke("lag", Arr()));
    }

    @Test
    void lag_singleElement() {
        assertEquals(Arr(Null()), invoke("lag", Arr(I(42))));
    }

    // =========================================================================
    // lead -- extended
    // =========================================================================

    @Test
    void lead_defaultPeriodOne() {
        assertEquals(Arr(I(20), I(30), Null()),
            invoke("lead", Arr(I(10), I(20), I(30))));
    }

    @Test
    void lead_periodTwo() {
        assertEquals(Arr(I(30), I(40), Null(), Null()),
            invoke("lead", Arr(I(10), I(20), I(30), I(40)), I(2)));
    }

    @Test
    void lead_emptyArray() {
        assertEquals(Arr(), invoke("lead", Arr()));
    }

    @Test
    void lead_singleElement() {
        assertEquals(Arr(Null()), invoke("lead", Arr(I(42))));
    }

    // =========================================================================
    // rank -- extended
    // =========================================================================

    @Test
    void rank_basicDescending() {
        // rank returns Integer values; highest = rank 1 (descending by default)
        var result = invoke("rank", Arr(I(10), I(30), I(20)));
        var arr = result.asArray();
        assertEquals(3, arr.size());
        // 10 -> rank 3, 30 -> rank 1, 20 -> rank 2
        assertEquals(I(3), arr.get(0));
        assertEquals(I(1), arr.get(1));
        assertEquals(I(2), arr.get(2));
    }

    @Test
    void rank_tiedValues() {
        var result = invoke("rank", Arr(I(10), I(10), I(30)));
        var arr = result.asArray();
        // Both 10s should have same rank
        assertEquals(arr.get(0), arr.get(1));
    }

    @Test
    void rank_singleElement() {
        var result = invoke("rank", Arr(I(42)));
        var arr = result.asArray();
        assertEquals(I(1), arr.get(0));
    }

    @Test
    void rank_allSameValue() {
        var result = invoke("rank", Arr(I(5), I(5), I(5)));
        var arr = result.asArray();
        assertEquals(I(1), arr.get(0));
        assertEquals(I(1), arr.get(1));
        assertEquals(I(1), arr.get(2));
    }

    @Test
    void rank_empty() {
        assertEquals(Arr(), invoke("rank", Arr()));
    }

    // =========================================================================
    // fillMissing -- extended
    // =========================================================================

    @Test
    void fillMissing_noNulls() {
        assertEquals(Arr(I(1), I(2), I(3)),
            invoke("fillMissing", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void fillMissing_allNullsValue() {
        assertEquals(Arr(I(99), I(99)),
            invoke("fillMissing", Arr(Null(), Null()), I(99)));
    }

    @Test
    void fillMissing_forwardStrategy() {
        var result = invoke("fillMissing", Arr(I(1), Null(), Null(), I(4), Null()), S("forward"));
        assertEquals(Arr(I(1), I(1), I(1), I(4), I(4)), result);
    }

    @Test
    void fillMissing_backwardStrategy() {
        var result = invoke("fillMissing", Arr(Null(), Null(), I(3), Null(), I(5)), S("backward"));
        assertEquals(Arr(I(3), I(3), I(3), I(5), I(5)), result);
    }

    @Test
    void fillMissing_emptyArray() {
        assertEquals(Arr(), invoke("fillMissing", Arr(), I(0)));
    }

    // =========================================================================
    // sample -- extended
    // =========================================================================

    @Test
    void sample_zeroCount() {
        assertEquals(Arr(), invoke("sample", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void sample_moreThanLength() {
        var result = invoke("sample", Arr(I(1), I(2)), I(10));
        assertArrayLength(result, 2);
    }

    @Test
    void sample_emptyArray() {
        assertEquals(Arr(), invoke("sample", Arr(), I(5)));
    }

    @Test
    void sample_oneElement() {
        assertEquals(Arr(I(42)), invoke("sample", Arr(I(42)), I(1)));
    }

    // =========================================================================
    // limit -- extended (alias for take)
    // =========================================================================

    @Test
    void limit_zero() {
        assertEquals(Arr(), invoke("limit", Arr(I(1), I(2), I(3)), I(0)));
    }

    @Test
    void limit_exactLength() {
        assertEquals(Arr(I(1), I(2)), invoke("limit", Arr(I(1), I(2)), I(2)));
    }

    @Test
    void limit_basic() {
        assertEquals(Arr(I(1), I(2)), invoke("limit", Arr(I(1), I(2), I(3), I(4)), I(2)));
    }

    @Test
    void limit_moreThanLength() {
        assertEquals(Arr(I(1)), invoke("limit", Arr(I(1)), I(10)));
    }

    // =========================================================================
    // keys -- extended
    // =========================================================================

    @Test
    void keys_emptyObject() {
        assertEquals(Arr(), invoke("keys", Obj()));
    }

    @Test
    void keys_singleKey() {
        assertEquals(Arr(S("only")), invoke("keys", Obj(e("only", I(1)))));
    }

    @Test
    void keys_preservesOrder() {
        assertEquals(Arr(S("z"), S("a"), S("m")),
            invoke("keys", Obj(e("z", I(1)), e("a", I(2)), e("m", I(3)))));
    }

    @Test
    void keys_multipleKeys() {
        assertEquals(Arr(S("a"), S("b"), S("c")),
            invoke("keys", Obj(e("a", I(1)), e("b", I(2)), e("c", I(3)))));
    }

    // =========================================================================
    // values -- extended
    // =========================================================================

    @Test
    void values_emptyObject() {
        assertEquals(Arr(), invoke("values", Obj()));
    }

    @Test
    void values_mixedTypes() {
        var result = invoke("values", Obj(e("a", I(1)), e("b", S("two")), e("c", B(true))));
        assertArrayLength(result, 3);
    }

    @Test
    void values_matchesOrder() {
        assertEquals(Arr(I(1), S("two"), B(true)),
            invoke("values", Obj(e("a", I(1)), e("b", S("two")), e("c", B(true)))));
    }

    // =========================================================================
    // entries -- extended
    // =========================================================================

    @Test
    void entries_emptyObject() {
        assertEquals(Arr(), invoke("entries", Obj()));
    }

    @Test
    void entries_multiplePairs() {
        var result = invoke("entries", Obj(e("a", I(1)), e("b", I(2))));
        var arr = result.asArray();
        assertEquals(2, arr.size());
        assertEquals(Arr(S("a"), I(1)), arr.get(0));
        assertEquals(Arr(S("b"), I(2)), arr.get(1));
    }

    @Test
    void entries_singlePair() {
        assertEquals(Arr(Arr(S("key"), I(42))),
            invoke("entries", Obj(e("key", I(42)))));
    }

    @Test
    void entries_preservesOrder() {
        var result = invoke("entries", Obj(e("z", I(1)), e("a", I(2))));
        var arr = result.asArray();
        assertEquals(Arr(S("z"), I(1)), arr.get(0));
        assertEquals(Arr(S("a"), I(2)), arr.get(1));
    }

    // =========================================================================
    // has -- extended
    // =========================================================================

    @Test
    void has_emptyObject() {
        assertEquals(B(false), invoke("has", Obj(), S("anything")));
    }

    @Test
    void has_withNullValue() {
        assertEquals(B(true), invoke("has", Obj(e("key", Null())), S("key")));
    }

    @Test
    void has_missingKey() {
        assertEquals(B(false), invoke("has", Obj(e("a", I(1))), S("b")));
    }

    @Test
    void has_keyPresent() {
        assertEquals(B(true), invoke("has", Obj(e("x", I(99))), S("x")));
    }

    // =========================================================================
    // get -- extended
    // =========================================================================

    @Test
    void get_topLevel() {
        assertEquals(I(99), invoke("get", Obj(e("x", I(99))), S("x")));
    }

    @Test
    void get_missingKey() {
        assertTrue(invoke("get", Obj(e("a", I(1))), S("b")).isNull());
    }

    @Test
    void get_nullValue() {
        assertTrue(invoke("get", Obj(e("key", Null())), S("key")).isNull());
    }

    // =========================================================================
    // merge -- extended
    // =========================================================================

    @Test
    void merge_bothEmpty() {
        var result = invoke("merge", Obj(), Obj());
        assertEquals(0, result.asObject().size());
    }

    @Test
    void merge_noOverlap() {
        var result = invoke("merge", Obj(e("a", I(1))), Obj(e("b", I(2))));
        assertEquals(2, result.asObject().size());
    }

    @Test
    void merge_completeOverlap() {
        var result = invoke("merge", Obj(e("x", I(1))), Obj(e("x", I(2))));
        var obj = result.asObject();
        assertEquals(1, obj.size());
        assertEquals(I(2), obj.get(0).getValue());
    }

    @Test
    void merge_secondOverwritesFirst() {
        var result = invoke("merge", Obj(e("x", I(1)), e("y", I(2))), Obj(e("y", I(99)), e("z", I(3))));
        var obj = result.asObject();
        assertEquals(3, obj.size());
    }

    @Test
    void merge_disjointObjects() {
        var result = invoke("merge", Obj(e("a", I(1))), Obj(e("b", I(2))));
        assertEquals(2, result.asObject().size());
    }

    // =========================================================================
    // Aggregation verbs -- extended
    // =========================================================================

    @Test
    void sum_singleElement() {
        assertEquals(I(42), invoke("sum", Arr(I(42))));
    }

    @Test
    void sum_negativeNumbers() {
        assertEquals(I(-6), invoke("sum", Arr(I(-1), I(-2), I(-3))));
    }

    @Test
    void sum_withNonNumericIgnored() {
        assertEquals(I(3), invoke("sum", Arr(I(1), S("hello"), I(2), B(true))));
    }

    @Test
    void sum_largeFloats() {
        var result = invoke("sum", Arr(F(1e10), F(2e10)));
        assertTrue(Math.abs(result.asDouble() - 3e10) < 1e5);
    }

    @Test
    void count_withNulls() {
        assertEquals(I(3), invoke("count", Arr(Null(), Null(), I(1))));
    }

    @Test
    void count_singleElement() {
        assertEquals(I(1), invoke("count", Arr(I(42))));
    }

    @Test
    void count_empty() {
        assertEquals(I(0), invoke("count", Arr()));
    }

    @Test
    void min_singleElement() {
        assertEquals(I(42), invoke("min", Arr(I(42))));
    }

    @Test
    void min_negativeNumbers() {
        assertEquals(I(-5), invoke("min", Arr(I(-1), I(-5), I(-2))));
    }

    @Test
    void min_mixedIntFloat() {
        assertEquals(F(2.5), invoke("min", Arr(I(5), F(2.5), I(3))));
    }

    @Test
    void max_singleElement() {
        assertEquals(I(42), invoke("max", Arr(I(42))));
    }

    @Test
    void max_negativeNumbers() {
        assertEquals(I(-1), invoke("max", Arr(I(-1), I(-5), I(-2))));
    }

    @Test
    void max_mixedIntFloat() {
        assertEquals(F(7.5), invoke("max", Arr(I(5), F(7.5), I(3))));
    }

    @Test
    void max_floats() {
        assertEquals(F(9.9), invoke("max", Arr(F(1.1), F(9.9), F(5.5))));
    }

    @Test
    void avg_floats() {
        assertEquals(F(2.0), invoke("avg", Arr(F(1.0), F(2.0), F(3.0))));
    }

    @Test
    void avg_mixedIntFloat() {
        assertEquals(F(2.0), invoke("avg", Arr(I(1), F(2.0), I(3))));
    }

    @Test
    void avg_withNonNumericSkipped() {
        assertEquals(F(15.0), invoke("avg", Arr(I(10), S("abc"), I(20))));
    }

    @Test
    void first_singleElement() {
        assertEquals(I(42), invoke("first", Arr(I(42))));
    }

    @Test
    void first_nullElement() {
        assertTrue(invoke("first", Arr(Null(), I(1))).isNull());
    }

    @Test
    void first_emptyArray() {
        assertTrue(invoke("first", Arr()).isNull());
    }

    @Test
    void last_singleElement() {
        assertEquals(I(42), invoke("last", Arr(I(42))));
    }

    @Test
    void last_nullAtEnd() {
        assertTrue(invoke("last", Arr(I(1), Null())).isNull());
    }

    @Test
    void last_emptyArray() {
        assertTrue(invoke("last", Arr()).isNull());
    }

    // =========================================================================
    // Accumulator verbs -- extended
    // =========================================================================

    @Test
    void accumulate_sumOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("total"), S("sum"), I(10) }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("total"), S("sum"), I(5) }, freshCtx);
        assertEquals(I(15), result);
    }

    @Test
    void accumulate_countOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("cnt"), S("count"), I(0) }, freshCtx);
        reg.invoke("accumulate", new DynValue[]{ S("cnt"), S("count"), I(0) }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("cnt"), S("count"), I(0) }, freshCtx);
        assertEquals(I(3), result);
    }

    @Test
    void accumulate_minOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("m"), S("min"), I(10) }, freshCtx);
        reg.invoke("accumulate", new DynValue[]{ S("m"), S("min"), I(5) }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("m"), S("min"), I(20) }, freshCtx);
        assertEquals(I(5), result);
    }

    @Test
    void accumulate_maxOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("m"), S("max"), I(10) }, freshCtx);
        reg.invoke("accumulate", new DynValue[]{ S("m"), S("max"), I(5) }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("m"), S("max"), I(20) }, freshCtx);
        assertEquals(I(20), result);
    }

    @Test
    void accumulate_concatOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("msg"), S("concat"), S("hello") }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("msg"), S("concat"), S(" world") }, freshCtx);
        assertEquals(S("hello world"), result);
    }

    @Test
    void accumulate_firstOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("f"), S("first"), S("first") }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("f"), S("first"), S("second") }, freshCtx);
        assertEquals(S("first"), result);
    }

    @Test
    void accumulate_lastOp() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        reg.invoke("accumulate", new DynValue[]{ S("l"), S("last"), S("first") }, freshCtx);
        var result = reg.invoke("accumulate", new DynValue[]{ S("l"), S("last"), S("second") }, freshCtx);
        assertEquals(S("second"), result);
    }

    @Test
    void set_stringValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertEquals(S("hello"), reg.invoke("set", new DynValue[]{ S("name"), S("hello") }, freshCtx));
    }

    @Test
    void set_nullValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertTrue(reg.invoke("set", new DynValue[]{ S("name"), Null() }, freshCtx).isNull());
    }

    @Test
    void set_integerValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertEquals(I(42), reg.invoke("set", new DynValue[]{ S("counter"), I(42) }, freshCtx));
    }

    @Test
    void set_boolValue() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertEquals(B(true), reg.invoke("set", new DynValue[]{ S("flag"), B(true) }, freshCtx));
    }

    // =========================================================================
    // Nested array operations (composition)
    // =========================================================================

    @Test
    void nested_sortThenTake() {
        var sorted = invoke("sort", Arr(I(5), I(1), I(3), I(2), I(4)));
        assertEquals(Arr(I(1), I(2), I(3)), invoke("take", sorted, I(3)));
    }

    @Test
    void nested_flattenThenDistinct() {
        var flat = invoke("flatten", Arr(Arr(I(1), I(2)), Arr(I(2), I(3))));
        assertEquals(Arr(I(1), I(2), I(3)), invoke("distinct", flat));
    }

    @Test
    void nested_concatThenSort() {
        var combined = invoke("concatArrays", Arr(I(3), I(1)), Arr(I(4), I(2)));
        assertEquals(Arr(I(1), I(2), I(3), I(4)), invoke("sort", combined));
    }

    @Test
    void nested_reverseThenFirst() {
        var reversed = invoke("reverse", Arr(I(1), I(2), I(3)));
        assertEquals(I(3), invoke("first", reversed));
    }

    @Test
    void nested_chunkThenFirst() {
        var chunks = invoke("chunk", Arr(I(1), I(2), I(3), I(4)), I(2));
        assertEquals(Arr(I(1), I(2)), invoke("first", chunks));
    }

    @Test
    void nested_dropThenCount() {
        var dropped = invoke("drop", Arr(I(1), I(2), I(3), I(4), I(5)), I(2));
        assertEquals(I(3), invoke("count", dropped));
    }

    @Test
    void nested_compactThenSum() {
        var compacted = invoke("compact", Arr(I(1), Null(), I(2), S(""), I(3)));
        assertEquals(I(6), invoke("sum", compacted));
    }

    // =========================================================================
    // rowNumber -- extended
    // =========================================================================

    @Test
    void rowNumber_increments() {
        var freshCtx = new TransformEngine.VerbContext();
        var reg = new VerbRegistry();
        assertEquals(I(1), reg.invoke("rowNumber", new DynValue[]{}, freshCtx));
        assertEquals(I(2), reg.invoke("rowNumber", new DynValue[]{}, freshCtx));
        assertEquals(I(3), reg.invoke("rowNumber", new DynValue[]{}, freshCtx));
    }

    // =========================================================================
    // filter -- basic truthiness (no operator)
    // =========================================================================

    @Test
    void filter_truthyValues() {
        assertEquals(Arr(I(1), I(2), I(3)),
            invoke("filter", Arr(I(0), I(1), Null(), I(2), S(""), I(3))));
    }

    @Test
    void filter_emptyArray() {
        assertEquals(Arr(), invoke("filter", Arr()));
    }

    @Test
    void filter_byFieldTruthiness() {
        var data = Arr(Obj(e("active", B(true))), Obj(e("active", B(false))));
        var result = invoke("filter", data, S("active"));
        assertArrayLength(result, 1);
    }
}
