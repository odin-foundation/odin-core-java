package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for logic, type-checking, comparison, conditional, and coercion verbs.
 * Ported from .NET SDK LogicVerbTests.cs.
 */
class LogicVerbTest {

    // ── Invoke helper ──

    private DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args);
    }

    // ── Shorthand helpers (matching .NET test helpers) ──

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }

    @SafeVarargs
    private static DynValue Obj(Map.Entry<String, DynValue>... pairs) {
        var list = new ArrayList<Map.Entry<String, DynValue>>();
        for (var p : pairs) list.add(p);
        return DynValue.ofObject(list);
    }

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) {
        return Map.entry(k, v);
    }

    // =========================================================================
    // and
    // =========================================================================

    @Test void and_TrueTrue() { assertTrue(invoke("and", B(true), B(true)).asBool()); }
    @Test void and_TrueFalse() { assertFalse(invoke("and", B(true), B(false)).asBool()); }
    @Test void and_FalseTrue() { assertFalse(invoke("and", B(false), B(true)).asBool()); }
    @Test void and_FalseFalse() { assertFalse(invoke("and", B(false), B(false)).asBool()); }
    @Test void and_TooFewArgs() { assertThrows(Exception.class, () -> invoke("and", B(true))); }
    @Test void and_NonBool() { assertThrows(Exception.class, () -> invoke("and", I(1), I(0))); }

    // =========================================================================
    // or
    // =========================================================================

    @Test void or_TrueTrue() { assertTrue(invoke("or", B(true), B(true)).asBool()); }
    @Test void or_TrueFalse() { assertTrue(invoke("or", B(true), B(false)).asBool()); }
    @Test void or_FalseTrue() { assertTrue(invoke("or", B(false), B(true)).asBool()); }
    @Test void or_FalseFalse() { assertFalse(invoke("or", B(false), B(false)).asBool()); }
    @Test void or_TooFewArgs() { assertThrows(Exception.class, () -> invoke("or", B(true))); }

    // =========================================================================
    // not
    // =========================================================================

    @Test void not_True() { assertFalse(invoke("not", B(true)).asBool()); }
    @Test void not_False() { assertTrue(invoke("not", B(false)).asBool()); }
    @Test void not_Null() { assertTrue(invoke("not", Null()).asBool()); }
    @Test void not_ZeroInt() { assertTrue(invoke("not", I(0)).asBool()); }
    @Test void not_NonzeroInt() { assertFalse(invoke("not", I(5)).asBool()); }
    @Test void not_ZeroFloat() { assertTrue(invoke("not", F(0.0)).asBool()); }
    @Test void not_EmptyString() { assertTrue(invoke("not", S("")).asBool()); }
    @Test void not_FalseString() { assertTrue(invoke("not", S("false")).asBool()); }
    @Test void not_NonemptyString() { assertFalse(invoke("not", S("hello")).asBool()); }
    @Test void not_EmptyArray() { assertTrue(invoke("not", Arr()).asBool()); }
    @Test void not_NonemptyArray() { assertFalse(invoke("not", Arr(I(1))).asBool()); }
    @Test void not_NoArgs() { assertFalse(invoke("not").asBool()); }

    // =========================================================================
    // xor
    // =========================================================================

    @Test void xor_TrueTrue() { assertFalse(invoke("xor", B(true), B(true)).asBool()); }
    @Test void xor_TrueFalse() { assertTrue(invoke("xor", B(true), B(false)).asBool()); }
    @Test void xor_FalseTrue() { assertTrue(invoke("xor", B(false), B(true)).asBool()); }
    @Test void xor_FalseFalse() { assertFalse(invoke("xor", B(false), B(false)).asBool()); }
    @Test void xor_TooFewArgs() { assertThrows(Exception.class, () -> invoke("xor", B(true))); }
    @Test void xor_NonBool() { assertThrows(Exception.class, () -> invoke("xor", I(1), I(0))); }

    // =========================================================================
    // eq
    // =========================================================================

    @Test void eq_IntsEqual() { assertTrue(invoke("eq", I(5), I(5)).asBool()); }
    @Test void eq_IntsNotEqual() { assertFalse(invoke("eq", I(5), I(6)).asBool()); }
    @Test void eq_StringsEqual() { assertTrue(invoke("eq", S("abc"), S("abc")).asBool()); }
    @Test void eq_StringsNotEqual() { assertFalse(invoke("eq", S("abc"), S("xyz")).asBool()); }
    @Test void eq_IntFloatCross() { assertTrue(invoke("eq", I(5), F(5.0)).asBool()); }
    @Test void eq_StringIntCoercion() { assertTrue(invoke("eq", S("42"), I(42)).asBool()); }
    @Test void eq_Nulls() { assertTrue(invoke("eq", Null(), Null()).asBool()); }
    @Test void eq_NullVsString() { assertFalse(invoke("eq", Null(), S("")).asBool()); }
    @Test void eq_Bools() { assertTrue(invoke("eq", B(true), B(true)).asBool()); }
    @Test void eq_BoolsDifferent() { assertFalse(invoke("eq", B(true), B(false)).asBool()); }
    @Test void eq_TooFew() { assertThrows(Exception.class, () -> invoke("eq", I(1))); }
    @Test void eq_FloatsEqual() { assertTrue(invoke("eq", F(3.14), F(3.14)).asBool()); }
    @Test void eq_FloatsNotEqual() { assertFalse(invoke("eq", F(3.14), F(2.71)).asBool()); }

    // =========================================================================
    // ne
    // =========================================================================

    @Test void ne_Equal() { assertFalse(invoke("ne", I(5), I(5)).asBool()); }
    @Test void ne_NotEqual() { assertTrue(invoke("ne", I(5), I(6)).asBool()); }
    @Test void ne_CrossType() { assertFalse(invoke("ne", I(5), F(5.0)).asBool()); }
    @Test void ne_TooFew() { assertThrows(Exception.class, () -> invoke("ne", I(1))); }
    @Test void ne_Strings() { assertTrue(invoke("ne", S("abc"), S("xyz")).asBool()); }
    @Test void ne_Nulls() { assertFalse(invoke("ne", Null(), Null()).asBool()); }

    // =========================================================================
    // lt
    // =========================================================================

    @Test void lt_IntsLess() { assertTrue(invoke("lt", I(3), I(5)).asBool()); }
    @Test void lt_IntsEqual() { assertFalse(invoke("lt", I(5), I(5)).asBool()); }
    @Test void lt_IntsGreater() { assertFalse(invoke("lt", I(7), I(5)).asBool()); }
    @Test void lt_Floats() { assertTrue(invoke("lt", F(1.5), F(2.5)).asBool()); }
    @Test void lt_Strings() { assertTrue(invoke("lt", S("abc"), S("xyz")).asBool()); }
    @Test void lt_TooFew() { assertThrows(Exception.class, () -> invoke("lt", I(1))); }

    // =========================================================================
    // lte
    // =========================================================================

    @Test void lte_Less() { assertTrue(invoke("lte", I(3), I(5)).asBool()); }
    @Test void lte_Equal() { assertTrue(invoke("lte", I(5), I(5)).asBool()); }
    @Test void lte_Greater() { assertFalse(invoke("lte", I(7), I(5)).asBool()); }
    @Test void lte_Strings() { assertTrue(invoke("lte", S("abc"), S("abc")).asBool()); }
    @Test void lte_FloatsLess() { assertTrue(invoke("lte", F(1.0), F(2.0)).asBool()); }
    @Test void lte_FloatsEqual() { assertTrue(invoke("lte", F(2.0), F(2.0)).asBool()); }

    // =========================================================================
    // gt
    // =========================================================================

    @Test void gt_Greater() { assertTrue(invoke("gt", I(7), I(5)).asBool()); }
    @Test void gt_Equal() { assertFalse(invoke("gt", I(5), I(5)).asBool()); }
    @Test void gt_Less() { assertFalse(invoke("gt", I(3), I(5)).asBool()); }
    @Test void gt_Strings() { assertTrue(invoke("gt", S("xyz"), S("abc")).asBool()); }
    @Test void gt_Floats() { assertTrue(invoke("gt", F(3.0), F(2.0)).asBool()); }

    // =========================================================================
    // gte
    // =========================================================================

    @Test void gte_Greater() { assertTrue(invoke("gte", I(7), I(5)).asBool()); }
    @Test void gte_Equal() { assertTrue(invoke("gte", I(5), I(5)).asBool()); }
    @Test void gte_Less() { assertFalse(invoke("gte", I(3), I(5)).asBool()); }
    @Test void gte_Floats() { assertTrue(invoke("gte", F(5.0), F(5.0)).asBool()); }
    @Test void gte_Strings() { assertTrue(invoke("gte", S("xyz"), S("abc")).asBool()); }

    // =========================================================================
    // between
    // =========================================================================

    @Test void between_InRange() { assertTrue(invoke("between", I(5), I(1), I(10)).asBool()); }
    @Test void between_AtMin() { assertTrue(invoke("between", I(1), I(1), I(10)).asBool()); }
    @Test void between_AtMax() { assertTrue(invoke("between", I(10), I(1), I(10)).asBool()); }
    @Test void between_Below() { assertFalse(invoke("between", I(0), I(1), I(10)).asBool()); }
    @Test void between_Above() { assertFalse(invoke("between", I(11), I(1), I(10)).asBool()); }
    @Test void between_Floats() { assertTrue(invoke("between", F(5.5), F(1.0), F(10.0)).asBool()); }
    @Test void between_Strings() { assertTrue(invoke("between", S("dog"), S("cat"), S("fox")).asBool()); }
    @Test void between_TooFew() { assertThrows(Exception.class, () -> invoke("between", I(5), I(1))); }

    // =========================================================================
    // isNull
    // =========================================================================

    @Test void isNull_Null() { assertTrue(invoke("isNull", Null()).asBool()); }
    @Test void isNull_String() { assertFalse(invoke("isNull", S("hi")).asBool()); }
    @Test void isNull_Int() { assertFalse(invoke("isNull", I(0)).asBool()); }
    @Test void isNull_EmptyArgs() { assertTrue(invoke("isNull").asBool()); }
    @Test void isNull_Bool() { assertFalse(invoke("isNull", B(false)).asBool()); }
    @Test void isNull_EmptyString() { assertFalse(invoke("isNull", S("")).asBool()); }

    // =========================================================================
    // isString
    // =========================================================================

    @Test void isString_String() { assertTrue(invoke("isString", S("hello")).asBool()); }
    @Test void isString_Empty() { assertTrue(invoke("isString", S("")).asBool()); }
    @Test void isString_Int() { assertFalse(invoke("isString", I(5)).asBool()); }
    @Test void isString_Null() { assertFalse(invoke("isString", Null()).asBool()); }
    @Test void isString_NoArgs() { assertFalse(invoke("isString").asBool()); }
    @Test void isString_Bool() { assertFalse(invoke("isString", B(true)).asBool()); }
    @Test void isString_Float() { assertFalse(invoke("isString", F(3.14)).asBool()); }

    // =========================================================================
    // isNumber
    // =========================================================================

    @Test void isNumber_Int() { assertTrue(invoke("isNumber", I(42)).asBool()); }
    @Test void isNumber_Float() { assertTrue(invoke("isNumber", F(3.14)).asBool()); }
    @Test void isNumber_String() { assertFalse(invoke("isNumber", S("42")).asBool()); }
    @Test void isNumber_Null() { assertFalse(invoke("isNumber", Null()).asBool()); }
    @Test void isNumber_Bool() { assertFalse(invoke("isNumber", B(true)).asBool()); }
    @Test void isNumber_NoArgs() { assertFalse(invoke("isNumber").asBool()); }

    // =========================================================================
    // isBoolean
    // =========================================================================

    @Test void isBoolean_True() { assertTrue(invoke("isBoolean", B(true)).asBool()); }
    @Test void isBoolean_False() { assertTrue(invoke("isBoolean", B(false)).asBool()); }
    @Test void isBoolean_String() { assertFalse(invoke("isBoolean", S("true")).asBool()); }
    @Test void isBoolean_NoArgs() { assertFalse(invoke("isBoolean").asBool()); }
    @Test void isBoolean_Int() { assertFalse(invoke("isBoolean", I(1)).asBool()); }
    @Test void isBoolean_Null() { assertFalse(invoke("isBoolean", Null()).asBool()); }

    // =========================================================================
    // isArray
    // =========================================================================

    @Test void isArray_Array() { assertTrue(invoke("isArray", Arr(I(1))).asBool()); }
    @Test void isArray_EmptyArray() { assertTrue(invoke("isArray", Arr()).asBool()); }
    @Test void isArray_StringLike() { assertTrue(invoke("isArray", S("[1,2]")).asBool()); }
    @Test void isArray_Int() { assertFalse(invoke("isArray", I(5)).asBool()); }
    @Test void isArray_NoArgs() { assertFalse(invoke("isArray").asBool()); }
    @Test void isArray_Object() { assertFalse(invoke("isArray", Obj(kv("a", I(1)))).asBool()); }
    @Test void isArray_Null() { assertFalse(invoke("isArray", Null()).asBool()); }

    // =========================================================================
    // isObject
    // =========================================================================

    @Test void isObject_Object() { assertTrue(invoke("isObject", Obj(kv("a", I(1)))).asBool()); }
    @Test void isObject_StringLike() { assertTrue(invoke("isObject", S("{}")).asBool()); }
    @Test void isObject_Array() { assertFalse(invoke("isObject", Arr()).asBool()); }
    @Test void isObject_NoArgs() { assertFalse(invoke("isObject").asBool()); }
    @Test void isObject_Null() { assertFalse(invoke("isObject", Null()).asBool()); }
    @Test void isObject_String() { assertFalse(invoke("isObject", S("hello")).asBool()); }

    // =========================================================================
    // isDate
    // =========================================================================

    @Test void isDate_Valid() { assertFalse(invoke("isDate", S("2024-01-15")).asBool()); }
    @Test void isDate_Timestamp() { assertTrue(invoke("isDate", DynValue.ofTimestamp("2024-01-15T10:30:00")).asBool()); }
    @Test void isDate_Invalid() { assertFalse(invoke("isDate", S("not-a-date")).asBool()); }
    @Test void isDate_Int() { assertFalse(invoke("isDate", I(20240115)).asBool()); }
    @Test void isDate_Null() { assertFalse(invoke("isDate", Null()).asBool()); }
    @Test void isDate_NoArgs() { assertFalse(invoke("isDate").asBool()); }
    @Test void isDate_ShortString() { assertFalse(invoke("isDate", S("2024")).asBool()); }

    // =========================================================================
    // typeOf
    // =========================================================================

    @Test void typeOf_Null() { assertEquals("null", invoke("typeOf", Null()).asString()); }
    @Test void typeOf_Bool() { assertEquals("boolean", invoke("typeOf", B(true)).asString()); }
    @Test void typeOf_String() { assertEquals("string", invoke("typeOf", S("hi")).asString()); }
    @Test void typeOf_Integer() { assertEquals("integer", invoke("typeOf", I(42)).asString()); }
    @Test void typeOf_Float() { assertEquals("number", invoke("typeOf", F(3.14)).asString()); }
    @Test void typeOf_Array() { assertEquals("array", invoke("typeOf", Arr()).asString()); }
    @Test void typeOf_Object() { assertEquals("object", invoke("typeOf", Obj()).asString()); }
    @Test void typeOf_NoArgs() { assertEquals("null", invoke("typeOf").asString()); }

    // =========================================================================
    // cond
    // =========================================================================

    @Test void cond_FirstTrue() {
        assertEquals("yes", invoke("cond", B(true), S("yes"), B(false), S("no")).asString());
    }

    @Test void cond_SecondTrue() {
        assertEquals("no", invoke("cond", B(false), S("yes"), B(true), S("no")).asString());
    }

    @Test void cond_Default() {
        assertEquals("default", invoke("cond", B(false), S("yes"), S("default")).asString());
    }

    @Test void cond_NoMatch() {
        assertTrue(invoke("cond", B(false), S("yes"), B(false), S("no")).isNull());
    }

    @Test void cond_Empty() {
        assertTrue(invoke("cond").isNull());
    }

    @Test void cond_TruthyInt() {
        assertEquals("yes", invoke("cond", I(1), S("yes"), S("no")).asString());
    }

    @Test void cond_FalsyZero() {
        assertEquals("no", invoke("cond", I(0), S("yes"), S("no")).asString());
    }

    // =========================================================================
    // assert
    // =========================================================================

    @Test void assert_Truthy() {
        var result = invoke("assert", B(true));
        assertTrue(result.asBool());
    }

    @Test void assert_Falsy() {
        assertTrue(invoke("assert", B(false)).isNull());
    }

    @Test void assert_FalsyWithMessage() {
        // The message argument is diagnostic only; a failing assertion yields null.
        assertTrue(invoke("assert", B(false), S("custom message")).isNull());
    }

    @Test void assert_NoArgs() {
        assertTrue(invoke("assert").isNull());
    }

    @Test void assert_TruthyString() {
        var result = invoke("assert", S("hello"));
        assertEquals("hello", result.asString());
    }

    @Test void assert_FalsyNull() {
        assertTrue(invoke("assert", Null()).isNull());
    }

    @Test void assert_FalsyEmptyString() {
        assertTrue(invoke("assert", S("")).isNull());
    }

    // =========================================================================
    // ifElse
    // =========================================================================

    @Test void ifElse_True() {
        assertEquals("yes", invoke("ifElse", B(true), S("yes"), S("no")).asString());
    }

    @Test void ifElse_False() {
        assertEquals("no", invoke("ifElse", B(false), S("yes"), S("no")).asString());
    }

    @Test void ifElse_TruthyInt() {
        assertEquals("yes", invoke("ifElse", I(1), S("yes"), S("no")).asString());
    }

    @Test void ifElse_FalsyZero() {
        assertEquals("no", invoke("ifElse", I(0), S("yes"), S("no")).asString());
    }

    @Test void ifElse_NullIsFalsy() {
        assertEquals("no", invoke("ifElse", Null(), S("yes"), S("no")).asString());
    }

    @Test void ifElse_TooFew() {
        assertThrows(Exception.class, () -> invoke("ifElse", B(true), S("yes")));
    }

    @Test void ifElse_TruthyString() {
        assertEquals("yes", invoke("ifElse", S("hello"), S("yes"), S("no")).asString());
    }

    @Test void ifElse_FalsyEmptyString() {
        assertEquals("no", invoke("ifElse", S(""), S("yes"), S("no")).asString());
    }

    // =========================================================================
    // ifNull
    // =========================================================================

    @Test void ifNull_NotNull() {
        assertEquals("val", invoke("ifNull", S("val"), S("default")).asString());
    }

    @Test void ifNull_IsNull() {
        assertEquals("default", invoke("ifNull", Null(), S("default")).asString());
    }

    @Test void ifNull_TooFew() {
        assertThrows(Exception.class, () -> invoke("ifNull", Null()));
    }

    @Test void ifNull_EmptyStringNotNull() {
        assertEquals("", invoke("ifNull", S(""), S("default")).asString());
    }

    @Test void ifNull_ZeroNotNull() {
        assertEquals(Long.valueOf(0), invoke("ifNull", I(0), S("default")).asInt64());
    }

    @Test void ifNull_FalseNotNull() {
        assertFalse(invoke("ifNull", B(false), S("default")).asBool());
    }

    // =========================================================================
    // ifEmpty
    // =========================================================================

    @Test void ifEmpty_NotEmpty() {
        assertEquals("val", invoke("ifEmpty", S("val"), S("default")).asString());
    }

    @Test void ifEmpty_EmptyString() {
        assertEquals("default", invoke("ifEmpty", S(""), S("default")).asString());
    }

    @Test void ifEmpty_Null() {
        assertEquals("default", invoke("ifEmpty", Null(), S("default")).asString());
    }

    @Test void ifEmpty_IntNotEmpty() {
        assertEquals(Long.valueOf(0), invoke("ifEmpty", I(0), S("default")).asInt64());
    }

    @Test void ifEmpty_TooFew() {
        assertThrows(Exception.class, () -> invoke("ifEmpty", S("")));
    }

    @Test void ifEmpty_BoolNotEmpty() {
        assertFalse(invoke("ifEmpty", B(false), S("default")).asBool());
    }

    // =========================================================================
    // coerceString
    // =========================================================================

    @Test void coerceString_FromString() {
        assertEquals("hi", invoke("coerceString", S("hi")).asString());
    }

    @Test void coerceString_FromInt() {
        assertEquals("42", invoke("coerceString", I(42)).asString());
    }

    @Test void coerceString_FromFloat() {
        assertEquals("3.14", invoke("coerceString", F(3.14)).asString());
    }

    @Test void coerceString_FromBool() {
        assertEquals("true", invoke("coerceString", B(true)).asString());
    }

    @Test void coerceString_FromNull() {
        assertTrue(invoke("coerceString", Null()).isNull());
    }

    @Test void coerceString_NoArgs() {
        assertThrows(Exception.class, () -> invoke("coerceString"));
    }

    @Test void coerceString_FromBoolFalse() {
        assertEquals("false", invoke("coerceString", B(false)).asString());
    }

    // =========================================================================
    // coerceNumber
    // =========================================================================

    @Test void coerceNumber_FromInt() {
        assertEquals(42.0, invoke("coerceNumber", I(42)).asDouble());
    }

    @Test void coerceNumber_FromFloat() {
        assertEquals(3.14, invoke("coerceNumber", F(3.14)).asDouble());
    }

    @Test void coerceNumber_FromString() {
        assertEquals(3.14, invoke("coerceNumber", S("3.14")).asDouble());
    }

    @Test void coerceNumber_FromBoolTrue() {
        assertEquals(1.0, invoke("coerceNumber", B(true)).asDouble());
    }

    @Test void coerceNumber_FromBoolFalse() {
        assertEquals(0.0, invoke("coerceNumber", B(false)).asDouble());
    }

    @Test void coerceNumber_FromNull() {
        assertEquals(Long.valueOf(0), invoke("coerceNumber", Null()).asInt64());
    }

    @Test void coerceNumber_InvalidString() {
        assertEquals(Long.valueOf(0), invoke("coerceNumber", S("abc")).asInt64());
    }

    @Test void coerceNumber_NoArgs() {
        assertTrue(invoke("coerceNumber").isNull());
    }

    // =========================================================================
    // coerceInteger
    // =========================================================================

    @Test void coerceInteger_FromInt() {
        assertEquals(Long.valueOf(42), invoke("coerceInteger", I(42)).asInt64());
    }

    @Test void coerceInteger_FromFloat() {
        assertEquals(Long.valueOf(3), invoke("coerceInteger", F(3.7)).asInt64());
    }

    @Test void coerceInteger_FromString() {
        assertEquals(Long.valueOf(42), invoke("coerceInteger", S("42")).asInt64());
    }

    @Test void coerceInteger_FromStringFloat() {
        assertEquals(Long.valueOf(3), invoke("coerceInteger", S("3.9")).asInt64());
    }

    @Test void coerceInteger_FromBool() {
        assertEquals(Long.valueOf(1), invoke("coerceInteger", B(true)).asInt64());
    }

    @Test void coerceInteger_FromBoolFalse() {
        assertEquals(Long.valueOf(0), invoke("coerceInteger", B(false)).asInt64());
    }

    @Test void coerceInteger_FromNull() {
        assertEquals(Long.valueOf(0), invoke("coerceInteger", Null()).asInt64());
    }

    @Test void coerceInteger_InvalidString() {
        assertEquals(Long.valueOf(0), invoke("coerceInteger", S("abc")).asInt64());
    }

    @Test void coerceInteger_NoArgs() {
        assertTrue(invoke("coerceInteger").isNull());
    }

    // =========================================================================
    // coerceBoolean
    // =========================================================================

    @Test void coerceBoolean_FromTrue() { assertTrue(invoke("coerceBoolean", B(true)).asBool()); }
    @Test void coerceBoolean_FromFalse() { assertFalse(invoke("coerceBoolean", B(false)).asBool()); }
    @Test void coerceBoolean_FromStringTrue() { assertTrue(invoke("coerceBoolean", S("true")).asBool()); }
    @Test void coerceBoolean_FromStringFalse() { assertFalse(invoke("coerceBoolean", S("false")).asBool()); }
    @Test void coerceBoolean_FromStringZero() { assertFalse(invoke("coerceBoolean", S("0")).asBool()); }
    @Test void coerceBoolean_FromStringEmpty() { assertFalse(invoke("coerceBoolean", S("")).asBool()); }
    @Test void coerceBoolean_FromStringNo() { assertFalse(invoke("coerceBoolean", S("no")).asBool()); }
    @Test void coerceBoolean_FromStringYes() { assertTrue(invoke("coerceBoolean", S("yes")).asBool()); }
    @Test void coerceBoolean_FromIntNonzero() { assertTrue(invoke("coerceBoolean", I(1)).asBool()); }
    @Test void coerceBoolean_FromIntZero() { assertFalse(invoke("coerceBoolean", I(0)).asBool()); }
    @Test void coerceBoolean_FromNull() { assertFalse(invoke("coerceBoolean", Null()).asBool()); }
    @Test void coerceBoolean_FromStringN() { assertFalse(invoke("coerceBoolean", S("n")).asBool()); }
    @Test void coerceBoolean_FromStringOff() { assertFalse(invoke("coerceBoolean", S("off")).asBool()); }
    @Test void coerceBoolean_NoArgs() { assertTrue(invoke("coerceBoolean").isNull()); }

    // =========================================================================
    // coerceDate
    // =========================================================================

    @Test void coerceDate_Valid() {
        assertEquals("2024-01-15", invoke("coerceDate", S("2024-01-15")).asString());
    }

    @Test void coerceDate_Timestamp() {
        assertEquals("2024-01-15", invoke("coerceDate", S("2024-01-15T10:30:00")).asString());
    }

    @Test void coerceDate_Invalid() {
        assertTrue(invoke("coerceDate", S("not-a-date")).isNull());
    }

    @Test void coerceDate_Null() {
        assertTrue(invoke("coerceDate", Null()).isNull());
    }

    @Test void coerceDate_NoArgs() {
        assertTrue(invoke("coerceDate").isNull());
    }

    // =========================================================================
    // coerceTimestamp
    // =========================================================================

    @Test void coerceTimestamp_Valid() {
        assertEquals("2024-01-15T10:30:00", invoke("coerceTimestamp", S("2024-01-15T10:30:00")).asString());
    }

    @Test void coerceTimestamp_DateOnly() {
        assertEquals("2024-01-15T00:00:00", invoke("coerceTimestamp", S("2024-01-15")).asString());
    }

    @Test void coerceTimestamp_Invalid() {
        assertThrows(Exception.class, () -> invoke("coerceTimestamp", S("not-valid")));
    }

    @Test void coerceTimestamp_Null() {
        assertTrue(invoke("coerceTimestamp", Null()).isNull());
    }

    @Test void coerceTimestamp_NoArgs() {
        assertThrows(Exception.class, () -> invoke("coerceTimestamp"));
    }

    // =========================================================================
    // tryCoerce
    // =========================================================================

    @Test void tryCoerce_Integer() {
        assertEquals(Long.valueOf(42), invoke("tryCoerce", S("42")).asInt64());
    }

    @Test void tryCoerce_Float() {
        assertEquals(3.14, invoke("tryCoerce", S("3.14")).asDouble());
    }

    @Test void tryCoerce_BoolTrue() {
        assertTrue(invoke("tryCoerce", S("true")).asBool());
    }

    @Test void tryCoerce_BoolFalse() {
        assertFalse(invoke("tryCoerce", S("false")).asBool());
    }

    @Test void tryCoerce_Date() {
        var result = invoke("tryCoerce", S("2024-01-15"));
        assertEquals("2024-01-15", result.asString());
    }

    @Test void tryCoerce_PlainString() {
        assertEquals("hello", invoke("tryCoerce", S("hello")).asString());
    }

    @Test void tryCoerce_NoArgs() {
        assertTrue(invoke("tryCoerce").isNull());
    }

    @Test void tryCoerce_PassthroughInt() {
        assertEquals(Long.valueOf(99), invoke("tryCoerce", I(99)).asInt64());
    }

    @Test void tryCoerce_PassthroughBool() {
        assertTrue(invoke("tryCoerce", B(true)).asBool());
    }

    // =========================================================================
    // toArray
    // =========================================================================

    @Test void toArray_FromArray() {
        var result = invoke("toArray", Arr(I(1), I(2)));
        assertEquals(2, result.asArray().size());
    }

    @Test void toArray_FromScalar() {
        var result = invoke("toArray", I(42));
        var arr = result.asArray();
        assertEquals(1, arr.size());
        assertEquals(Long.valueOf(42), arr.get(0).asInt64());
    }

    @Test void toArray_NoArgs() {
        var result = invoke("toArray");
        assertTrue(result.asArray().isEmpty());
    }

    @Test void toArray_FromString() {
        var result = invoke("toArray", S("hello"));
        var arr = result.asArray();
        assertEquals(1, arr.size());
        assertEquals("hello", arr.get(0).asString());
    }

    @Test void toArray_FromNull() {
        var result = invoke("toArray", Null());
        var arr = result.asArray();
        assertEquals(1, arr.size());
        assertTrue(arr.get(0).isNull());
    }

    // =========================================================================
    // toObject
    // =========================================================================

    @Test void toObject_FromPairs() {
        var input = Arr(Arr(S("a"), I(1)), Arr(S("b"), I(2)));
        var result = invoke("toObject", input);
        var obj = result.asObject();
        assertEquals(2, obj.size());
        assertEquals("a", obj.get(0).getKey());
        assertEquals(Long.valueOf(1), obj.get(0).getValue().asInt64());
        assertEquals("b", obj.get(1).getKey());
        assertEquals(Long.valueOf(2), obj.get(1).getValue().asInt64());
    }

    @Test void toObject_Null() {
        assertTrue(invoke("toObject", Null()).isNull());
    }

    @Test void toObject_AlreadyObject() {
        var input = Obj(kv("x", I(1)));
        var result = invoke("toObject", input);
        assertEquals(Long.valueOf(1), result.asObject().get(0).getValue().asInt64());
    }

    @Test void toObject_NoArgs() {
        assertTrue(invoke("toObject").isNull());
    }

    @Test void toObject_InvalidPairs() {
        assertTrue(invoke("toObject", Arr(I(1), I(2))).isNull());
    }

    // =========================================================================
    // coalesce
    // =========================================================================

    @Test void coalesce_FirstNonNull() {
        assertEquals("val", invoke("coalesce", Null(), S(""), S("val")).asString());
    }

    @Test void coalesce_FirstIsGood() {
        assertEquals("val", invoke("coalesce", S("val"), Null()).asString());
    }

    @Test void coalesce_AllNull() {
        assertTrue(invoke("coalesce", Null(), Null()).isNull());
    }

    @Test void coalesce_AllEmpty() {
        assertTrue(invoke("coalesce", S(""), S("")).isNull());
    }

    @Test void coalesce_IntFirst() {
        assertEquals(Long.valueOf(0), invoke("coalesce", I(0), S("fallback")).asInt64());
    }

    @Test void coalesce_SkipsNullAndEmpty() {
        assertEquals("c", invoke("coalesce", Null(), S(""), S("c")).asString());
    }

    @Test void coalesce_BoolFirst() {
        assertFalse(invoke("coalesce", B(false), S("fallback")).asBool());
    }
}
