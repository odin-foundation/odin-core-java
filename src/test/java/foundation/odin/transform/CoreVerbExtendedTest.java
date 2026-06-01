package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended core verb tests ported from the .NET SDK for cross-language parity.
 * Covers logic, coercion, string, comparison, type checking, and conditional verbs.
 */
class CoreVerbExtendedTest {

    // ── Verb invocation helper ──

    private static DynValue invoke(String verb, DynValue... args) {
        try {
            return TransformEngine.invokeVerb(verb, args);
        } catch (UnsupportedOperationException | IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            if (e.getClass() == RuntimeException.class) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            throw e;
        }
    }

    // ── Shorthand factory methods ──

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }

    @SafeVarargs
    private static DynValue Obj(Map.Entry<String, DynValue>... pairs) {
        var list = new ArrayList<Map.Entry<String, DynValue>>();
        for (var pair : pairs) list.add(pair);
        return DynValue.ofObject(list);
    }

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) {
        return Map.entry(k, v);
    }

    // =========================================================================
    // Logic verbs -- additional edge cases (from Rust extended_tests_2)
    // =========================================================================

    @Nested class LogicVerbTests {

        @Test void and_StringArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("and", S("true"), S("true")));
        }

        @Test void and_NullArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("and", Null(), B(true)));
        }

        @Test void and_EmptyArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("and"));
        }

        @Test void and_FalseTrue() {
            assertEquals(false, invoke("and", B(false), B(true)).asBool());
        }

        @Test void or_NonBool_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("or", I(1), I(0)));
        }

        @Test void or_NullArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("or", Null(), Null()));
        }

        @Test void or_EmptyArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("or"));
        }

        @Test void or_TrueTrue() {
            assertEquals(true, invoke("or", B(true), B(true)).asBool());
        }

        @Test void xor_NullArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("xor", Null(), B(true)));
        }

        @Test void xor_EmptyArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("xor"));
        }

        @Test void xor_FalseTrue() {
            assertEquals(true, invoke("xor", B(false), B(true)).asBool());
        }

        @Test void not_Object() {
            assertEquals(false, invoke("not", Obj()).asBool());
        }

        @Test void not_NonzeroFloat() {
            assertEquals(false, invoke("not", F(1.5)).asBool());
        }

        @Test void not_FalseString() {
            assertEquals(true, invoke("not", S("false")).asBool());
        }
    }

    // =========================================================================
    // Equality -- additional edge cases
    // =========================================================================

    @Nested class EqualityTests {

        @Test void eq_ArraysEqual() {
            assertEquals(true, invoke("eq", Arr(I(1), I(2)), Arr(I(1), I(2))).asBool());
        }

        @Test void eq_ArraysNotEqual() {
            assertEquals(false, invoke("eq", Arr(I(1)), Arr(I(2))).asBool());
        }

        @Test void eq_NullNull_Extended() {
            assertEquals(true, invoke("eq", Null(), Null()).asBool());
        }

        @Test void eq_IntStringNoMatch() {
            assertEquals(false, invoke("eq", I(42), S("abc")).asBool());
        }

        @Test void ne_StringsEqual() {
            assertEquals(false, invoke("ne", S("a"), S("a")).asBool());
        }

        @Test void ne_StringsDiffer() {
            assertEquals(true, invoke("ne", S("a"), S("b")).asBool());
        }

        @Test void ne_NullNull_Extended() {
            assertEquals(false, invoke("ne", Null(), Null()).asBool());
        }

        @Test void ne_NullString() {
            assertEquals(true, invoke("ne", Null(), S("x")).asBool());
        }
    }

    // =========================================================================
    // Comparison -- mixed types and edge cases
    // =========================================================================

    @Nested class ComparisonTests {

        @Test void lt_IntFloat() {
            assertEquals(true, invoke("lt", I(3), F(3.5)).asBool());
        }

        @Test void lt_FloatInt() {
            assertEquals(false, invoke("lt", F(3.5), I(3)).asBool());
        }

        @Test void lt_Negative() {
            assertEquals(true, invoke("lt", I(-5), I(-3)).asBool());
        }

        @Test void lte_FloatsEqual() {
            assertEquals(true, invoke("lte", F(3.14), F(3.14)).asBool());
        }

        @Test void lte_FloatsLess() {
            assertEquals(true, invoke("lte", F(1.0), F(2.0)).asBool());
        }

        @Test void lte_TooFew_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("lte", I(1)));
        }

        @Test void gt_Floats() {
            assertEquals(true, invoke("gt", F(5.5), F(3.3)).asBool());
        }

        @Test void gt_NegativeInts() {
            assertEquals(true, invoke("gt", I(-1), I(-5)).asBool());
        }

        @Test void gt_TooFew_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("gt", I(1)));
        }

        @Test void gte_Floats() {
            assertEquals(true, invoke("gte", F(5.0), F(5.0)).asBool());
        }

        @Test void gte_Strings() {
            assertEquals(true, invoke("gte", S("b"), S("a")).asBool());
        }

        @Test void gte_StringsEqual() {
            assertEquals(true, invoke("gte", S("a"), S("a")).asBool());
        }

        @Test void gte_TooFew_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("gte", I(1)));
        }

        @Test void between_NegativeRange() {
            assertEquals(true, invoke("between", I(-5), I(-10), I(0)).asBool());
        }

        @Test void between_FloatOutside() {
            assertEquals(false, invoke("between", F(0.5), F(1.0), F(10.0)).asBool());
        }
    }

    // =========================================================================
    // Conditional -- extended
    // =========================================================================

    @Nested class ConditionalTests {

        @Test void cond_MultiplePairsThirdTrue() {
            assertEquals("c", invoke("cond", B(false), S("a"), B(false), S("b"), B(true), S("c")).asString());
        }

        @Test void cond_TruthyInteger() {
            assertEquals("yes", invoke("cond", I(1), S("yes"), S("no")).asString());
        }

        @Test void cond_AllFalseWithDefault() {
            assertEquals("default", invoke("cond", B(false), S("a"), B(false), S("b"), S("default")).asString());
        }

        @Test void ifElse_TruthyString() {
            assertEquals("yes", invoke("ifElse", S("x"), S("yes"), S("no")).asString());
        }

        @Test void ifElse_FalsyEmptyStr() {
            assertEquals("no", invoke("ifElse", S(""), S("yes"), S("no")).asString());
        }

        @Test void ifNull_IntValue() {
            assertEquals(0L, invoke("ifNull", I(0), S("default")).asInt64());
        }

        @Test void ifNull_EmptyStringNotNull() {
            assertEquals("", invoke("ifNull", S(""), S("default")).asString());
        }

        @Test void ifEmpty_BoolNotEmpty() {
            assertEquals(false, invoke("ifEmpty", B(false), S("default")).asBool());
        }

        @Test void ifEmpty_FloatNotEmpty() {
            var result = invoke("ifEmpty", F(0.0), S("default"));
            assertEquals(0.0, result.asDouble());
        }
    }

    // =========================================================================
    // Switch -- additional
    // =========================================================================

    @Nested class SwitchTests {

        @Test void switch_IntMatch() {
            assertEquals("two", invoke("switch", I(2), I(1), S("one"), I(2), S("two")).asString());
        }

        @Test void switch_IntDefault() {
            assertEquals("other", invoke("switch", I(3), I(1), S("one"), S("other")).asString());
        }

        @Test void switch_SingleValueNoPairs() {
            assertTrue(invoke("switch", S("x")).isNull());
        }
    }

    // =========================================================================
    // Type checking -- additional edge cases
    // =========================================================================

    @Nested class TypeCheckingTests {

        @Test void isNull_Bool() { assertEquals(false, invoke("isNull", B(false)).asBool()); }
        @Test void isNull_Float() { assertEquals(false, invoke("isNull", F(0.0)).asBool()); }
        @Test void isNull_Array() { assertEquals(false, invoke("isNull", Arr()).asBool()); }
        @Test void isNull_Object_Extended() { assertEquals(false, invoke("isNull", Obj()).asBool()); }

        @Test void isString_Float() { assertEquals(false, invoke("isString", F(3.14)).asBool()); }
        @Test void isString_Bool() { assertEquals(false, invoke("isString", B(true)).asBool()); }
        @Test void isString_Array() { assertEquals(false, invoke("isString", Arr()).asBool()); }

        @Test void isNumber_Object() { assertEquals(false, invoke("isNumber", Obj()).asBool()); }
        @Test void isNumber_Array() { assertEquals(false, invoke("isNumber", Arr()).asBool()); }

        @Test void isBoolean_Int() { assertEquals(false, invoke("isBoolean", I(1)).asBool()); }
        @Test void isBoolean_Null() { assertEquals(false, invoke("isBoolean", Null()).asBool()); }
        @Test void isBoolean_Float() { assertEquals(false, invoke("isBoolean", F(1.0)).asBool()); }

        @Test void isArray_PlainString() { assertEquals(false, invoke("isArray", S("hello")).asBool()); }
        @Test void isArray_Null() { assertEquals(false, invoke("isArray", Null()).asBool()); }

        @Test void isObject_PlainString() { assertEquals(false, invoke("isObject", S("hello")).asBool()); }
        @Test void isObject_Null() { assertEquals(false, invoke("isObject", Null()).asBool()); }
        @Test void isObject_Int() { assertEquals(false, invoke("isObject", I(42)).asBool()); }
        @Test void isObject_Empty() { assertEquals(true, invoke("isObject", Obj()).asBool()); }

        @Test void isDate_ShortString() { assertEquals(false, invoke("isDate", S("2024")).asBool()); }
        @Test void isDate_Empty() { assertEquals(false, invoke("isDate", S("")).asBool()); }
        @Test void isDate_Bool() { assertEquals(false, invoke("isDate", B(true)).asBool()); }
        @Test void isDate_NoArgs() { assertEquals(false, invoke("isDate").asBool()); }
        @Test void isDate_ValidDate() { assertEquals(true, invoke("isDate", S("2024-01-15")).asBool()); }
        @Test void isDate_DateType() { assertEquals(true, invoke("isDate", DynValue.ofDate("2024-06-15")).asBool()); }

        @Test void typeOf_Reference() { assertEquals("reference", invoke("typeOf", DynValue.ofReference("ref")).asString()); }
        @Test void typeOf_Binary() { assertEquals("binary", invoke("typeOf", DynValue.ofBinary("data")).asString()); }
        @Test void typeOf_DateValue() { assertEquals("date", invoke("typeOf", DynValue.ofDate("2024-01-01")).asString()); }

        @Test void isFinite_Zero() { assertEquals(true, invoke("isFinite", F(0.0)).asBool()); }
        @Test void isFinite_Negative() { assertEquals(true, invoke("isFinite", F(-999.0)).asBool()); }
        @Test void isFinite_NegInfinity() { assertEquals(false, invoke("isFinite", F(Double.NEGATIVE_INFINITY)).asBool()); }

        @Test void isNaN_Infinity() { assertEquals(false, invoke("isNaN", F(Double.POSITIVE_INFINITY)).asBool()); }
        @Test void isNaN_NegInfinity() { assertEquals(false, invoke("isNaN", F(Double.NEGATIVE_INFINITY)).asBool()); }
    }

    // =========================================================================
    // Coercion -- additional edge cases
    // =========================================================================

    @Nested class CoercionTests {

        @Test void coerceString_BoolFalse() { assertEquals("false", invoke("coerceString", B(false)).asString()); }
        @Test void coerceString_ZeroInt() { assertEquals("0", invoke("coerceString", I(0)).asString()); }
        @Test void coerceString_NegativeInt() { assertEquals("-5", invoke("coerceString", I(-5)).asString()); }

        @Test void coerceNumber_IntString() { assertEquals(42.0, invoke("coerceNumber", S("42")).asDouble()); }
        @Test void coerceNumber_NegativeString() { assertEquals(-3.14, invoke("coerceNumber", S("-3.14")).asDouble()); }
        @Test void coerceNumber_ZeroString() { assertEquals(0.0, invoke("coerceNumber", S("0")).asDouble()); }

        @Test void coerceBoolean_N() { assertEquals(false, invoke("coerceBoolean", S("n")).asBool()); }
        @Test void coerceBoolean_Off() { assertEquals(false, invoke("coerceBoolean", S("off")).asBool()); }
        @Test void coerceBoolean_On() { assertEquals(true, invoke("coerceBoolean", S("on")).asBool()); }
        @Test void coerceBoolean_1String() { assertEquals(true, invoke("coerceBoolean", S("1")).asBool()); }
        @Test void coerceBoolean_FloatNonzero() { assertEquals(true, invoke("coerceBoolean", F(0.5)).asBool()); }
        @Test void coerceBoolean_FloatZero() { assertEquals(false, invoke("coerceBoolean", F(0.0)).asBool()); }

        @Test void coerceInteger_NegativeFloat() { assertEquals(-3L, invoke("coerceInteger", F(-3.9)).asInt64()); }
        @Test void coerceInteger_BoolFalse() { assertEquals(0L, invoke("coerceInteger", B(false)).asInt64()); }
        @Test void coerceInteger_LargeFloat() { assertEquals(10_000_000_000L, invoke("coerceInteger", F(1e10)).asInt64()); }
    }

    // =========================================================================
    // Core string verbs -- additional edge cases
    // =========================================================================

    @Nested class CoreStringTests {

        @Test void concat_Float() { assertEquals("pi=3.14", invoke("concat", S("pi="), F(3.14)).asString()); }
        @Test void concat_Single() { assertEquals("only", invoke("concat", S("only")).asString()); }
        @Test void concat_AllNulls() { assertEquals("", invoke("concat", Null(), Null()).asString()); }

        @Test void upper_IntError_Extended() { assertThrows(RuntimeException.class, () -> invoke("upper", I(42))); }
        @Test void lower_IntError_Extended() { assertThrows(RuntimeException.class, () -> invoke("lower", I(42))); }

        @Test void trim_AllWhitespace() { assertEquals("", invoke("trim", S("   ")).asString()); }
        @Test void trim_IntError() { assertThrows(RuntimeException.class, () -> invoke("trim", I(5))); }

        @Test void trimLeft_NoLeading() { assertEquals("hello", invoke("trimLeft", S("hello")).asString()); }
        @Test void trimLeft_IntError() { assertThrows(RuntimeException.class, () -> invoke("trimLeft", I(5))); }
        @Test void trimRight_NoTrailing() { assertEquals("hello", invoke("trimRight", S("hello")).asString()); }
        @Test void trimRight_IntError() { assertThrows(RuntimeException.class, () -> invoke("trimRight", I(5))); }

        @Test void coalesce_EmptyThenInt() { assertEquals(5L, invoke("coalesce", S(""), I(5)).asInt64()); }
        @Test void coalesce_NullThenBool() { assertEquals(true, invoke("coalesce", Null(), B(true)).asBool()); }
        @Test void coalesce_Empty() { assertTrue(invoke("coalesce").isNull()); }
    }

    // =========================================================================
    // String verbs -- titleCase
    // =========================================================================

    @Nested class TitleCaseTests {

        @Test void titleCase_EmptyString() { assertEquals("", invoke("titleCase", S("")).asString()); }
        @Test void titleCase_SingleWord() { assertEquals("Hello", invoke("titleCase", S("hello")).asString()); }
        @Test void titleCase_AlreadyTitled() { assertEquals("Hello World", invoke("titleCase", S("Hello World")).asString()); }
        @Test void titleCase_AllUpper() { assertEquals("HELLO WORLD", invoke("titleCase", S("HELLO WORLD")).asString()); }
        @Test void titleCase_WithNumbers() { assertEquals("Hello 42 World", invoke("titleCase", S("hello 42 world")).asString()); }
        @Test void titleCase_Null() { assertTrue(invoke("titleCase", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- contains
    // =========================================================================

    @Nested class ContainsTests {

        @Test void contains_EmptySubstring() { assertEquals(true, invoke("contains", S("hello"), S("")).asBool()); }
        @Test void contains_EmptyString() { assertEquals(false, invoke("contains", S(""), S("a")).asBool()); }
        @Test void contains_BothEmpty() { assertEquals(true, invoke("contains", S(""), S("")).asBool()); }
        @Test void contains_CaseSensitive() { assertEquals(false, invoke("contains", S("Hello"), S("hello")).asBool()); }
        @Test void contains_Unicode() { assertEquals(true, invoke("contains", S("caf\u00e9"), S("f\u00e9")).asBool()); }
        @Test void contains_NullFirstArg() { assertEquals(false, invoke("contains", Null(), S("x")).asBool()); }
        @Test void contains_HappyPath() { assertEquals(true, invoke("contains", S("hello world"), S("world")).asBool()); }
    }

    // =========================================================================
    // String verbs -- startsWith / endsWith
    // =========================================================================

    @Nested class StartsWithEndsWithTests {

        @Test void startsWith_EmptyPrefix() { assertEquals(true, invoke("startsWith", S("hello"), S("")).asBool()); }
        @Test void startsWith_FullMatch() { assertEquals(true, invoke("startsWith", S("hello"), S("hello")).asBool()); }
        @Test void startsWith_LongerPrefix() { assertEquals(false, invoke("startsWith", S("hi"), S("hello")).asBool()); }
        @Test void startsWith_Null() { assertEquals(false, invoke("startsWith", Null(), S("x")).asBool()); }
        @Test void startsWith_HappyPath() { assertEquals(true, invoke("startsWith", S("hello"), S("hel")).asBool()); }

        @Test void endsWith_EmptySuffix() { assertEquals(true, invoke("endsWith", S("hello"), S("")).asBool()); }
        @Test void endsWith_FullMatch() { assertEquals(true, invoke("endsWith", S("hello"), S("hello")).asBool()); }
        @Test void endsWith_NoMatch() { assertEquals(false, invoke("endsWith", S("hello"), S("xyz")).asBool()); }
        @Test void endsWith_Null() { assertEquals(false, invoke("endsWith", Null(), S("x")).asBool()); }
        @Test void endsWith_HappyPath() { assertEquals(true, invoke("endsWith", S("hello"), S("llo")).asBool()); }
    }

    // =========================================================================
    // String verbs -- replace / replaceRegex
    // =========================================================================

    @Nested class ReplaceTests {

        @Test void replace_HappyPath() { assertEquals("hero world", invoke("replace", S("hello world"), S("hello"), S("hero")).asString()); }
        @Test void replace_NoMatch() { assertEquals("hello", invoke("replace", S("hello"), S("xyz"), S("abc")).asString()); }
        @Test void replace_Null() { assertTrue(invoke("replace", Null(), S("a"), S("b")).isNull()); }

        @Test void replaceRegex_NoMatch() { assertEquals("hello", invoke("replaceRegex", S("hello"), S("xyz"), S("abc")).asString()); }
        @Test void replaceRegex_EmptyReplacement() { assertEquals("helloworld", invoke("replaceRegex", S("hello world"), S(" "), S("")).asString()); }
        @Test void replaceRegex_MultipleOccurrences() { assertEquals("bbbbbb", invoke("replaceRegex", S("aaa"), S("a"), S("bb")).asString()); }
    }

    // =========================================================================
    // String verbs -- padding
    // =========================================================================

    @Nested class PaddingTests {

        @Test void padLeft_AlreadyWide() { assertEquals("hello", invoke("padLeft", S("hello"), I(3), S("0")).asString()); }
        @Test void padLeft_ExactWidth() { assertEquals("hi", invoke("padLeft", S("hi"), I(2), S("0")).asString()); }
        @Test void padLeft_EmptyString() { assertEquals("xxx", invoke("padLeft", S(""), I(3), S("x")).asString()); }
        @Test void padLeft_DefaultChar() { assertEquals("   hi", invoke("padLeft", S("hi"), I(5)).asString()); }

        @Test void padRight_AlreadyWide() { assertEquals("hello", invoke("padRight", S("hello"), I(3), S(".")).asString()); }
        @Test void padRight_EmptyString() { assertEquals("----", invoke("padRight", S(""), I(4), S("-")).asString()); }
        @Test void padRight_SpaceChar() { assertEquals("hi   ", invoke("padRight", S("hi"), I(5), S(" ")).asString()); }

        @Test void pad_AlreadyWide() { assertEquals("hello", invoke("pad", S("hello"), I(3), S("*")).asString()); }
        @Test void pad_OddPadding() { assertEquals("ab---", invoke("pad", S("ab"), I(5), S("-")).asString()); }
        @Test void pad_EmptyString() { assertEquals("xxxx", invoke("pad", S(""), I(4), S("x")).asString()); }
    }

    // =========================================================================
    // String verbs -- truncate
    // =========================================================================

    @Nested class TruncateTests {

        @Test void truncate_ShorterThanLimit() { assertEquals("hi", invoke("truncate", S("hi"), I(10)).asString()); }
        @Test void truncate_ExactLength() { assertEquals("hello", invoke("truncate", S("hello"), I(5)).asString()); }
        @Test void truncate_ToZero() { assertEquals("", invoke("truncate", S("hello"), I(0)).asString()); }
        @Test void truncate_EmptyString() { assertEquals("", invoke("truncate", S(""), I(5)).asString()); }
        @Test void truncate_Null() { assertTrue(invoke("truncate", Null(), I(5)).isNull()); }
    }

    // =========================================================================
    // String verbs -- split / join
    // =========================================================================

    @Nested class SplitJoinTests {

        @Test void split_EmptyString() {
            var result = invoke("split", S(""), S(","));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(1, arr.size());
            assertEquals("", arr.get(0).asString());
        }

        @Test void split_NoDelimiterFound() {
            var result = invoke("split", S("hello"), S(","));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(1, arr.size());
            assertEquals("hello", arr.get(0).asString());
        }

        @Test void split_MultiCharDelimiter() {
            var result = invoke("split", S("a::b::c"), S("::"));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(3, arr.size());
            assertEquals("a", arr.get(0).asString());
            assertEquals("b", arr.get(1).asString());
            assertEquals("c", arr.get(2).asString());
        }

        @Test void join_EmptyArray() {
            assertEquals("", invoke("join", Arr(), S(",")).asString());
        }

        @Test void join_SingleElement() {
            assertEquals("a", invoke("join", Arr(S("a")), S(",")).asString());
        }

        @Test void join_EmptyDelimiter() {
            assertEquals("abc", invoke("join", Arr(S("a"), S("b"), S("c")), S("")).asString());
        }

        @Test void join_WithIntegers() {
            assertEquals("1-2-3", invoke("join", Arr(I(1), I(2), I(3)), S("-")).asString());
        }
    }

    // =========================================================================
    // String verbs -- mask
    // =========================================================================

    @Nested class MaskTests {

        @Test void mask_ShowAll() { assertEquals("abc", invoke("mask", S("abc"), S("***")).asString()); }
        @Test void mask_ShowZero() { assertEquals("a-b-c", invoke("mask", S("abc"), S("*-*-*")).asString()); }
        @Test void mask_ShowExactLength() { assertEquals("abc", invoke("mask", S("abc"), S("###")).asString()); }
        @Test void mask_Null() { assertTrue(invoke("mask", Null(), S("####")).isNull()); }
    }

    // =========================================================================
    // String verbs -- reverseString
    // =========================================================================

    @Nested class ReverseStringTests {

        @Test void reverse_Empty() { assertEquals("", invoke("reverseString", S("")).asString()); }
        @Test void reverse_SingleChar() { assertEquals("a", invoke("reverseString", S("a")).asString()); }
        @Test void reverse_Palindrome() { assertEquals("racecar", invoke("reverseString", S("racecar")).asString()); }
        @Test void reverse_Regular() { assertEquals("cba", invoke("reverseString", S("abc")).asString()); }
        @Test void reverse_WithSpaces() { assertEquals("c b a", invoke("reverseString", S("a b c")).asString()); }
        @Test void reverse_Null() { assertTrue(invoke("reverseString", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- repeat
    // =========================================================================

    @Nested class RepeatTests {

        @Test void repeat_ZeroTimes() { assertEquals("", invoke("repeat", S("abc"), I(0)).asString()); }
        @Test void repeat_OneTime() { assertEquals("abc", invoke("repeat", S("abc"), I(1)).asString()); }
        @Test void repeat_EmptyString() { assertEquals("", invoke("repeat", S(""), I(5)).asString()); }

        @Test void repeat_LargeCount() {
            var result = invoke("repeat", S("x"), I(100)).asString();
            assertNotNull(result);
            assertEquals(100, result.length());
        }
    }

    // =========================================================================
    // String verbs -- case conversion
    // =========================================================================

    @Nested class CaseConversionTests {

        @Test void camelCase_Empty() { assertEquals("", invoke("camelCase", S("")).asString()); }
        @Test void camelCase_SingleWord() { assertEquals("hello", invoke("camelCase", S("hello")).asString()); }
        @Test void camelCase_FromSnake() { assertEquals("helloWorld", invoke("camelCase", S("hello_world")).asString()); }
        @Test void camelCase_FromKebab() { assertEquals("helloWorld", invoke("camelCase", S("hello-world")).asString()); }
        @Test void camelCase_FromPascal() { assertEquals("helloWorld", invoke("camelCase", S("HelloWorld")).asString()); }
        @Test void camelCase_MultipleWords() { assertEquals("theQuickBrownFox", invoke("camelCase", S("the quick brown fox")).asString()); }
        @Test void camelCase_Null() { assertTrue(invoke("camelCase", Null()).isNull()); }

        @Test void snakeCase_Empty() { assertEquals("", invoke("snakeCase", S("")).asString()); }
        @Test void snakeCase_SingleWord() { assertEquals("hello", invoke("snakeCase", S("hello")).asString()); }
        @Test void snakeCase_FromCamel() { assertEquals("hello_world", invoke("snakeCase", S("helloWorld")).asString()); }
        @Test void snakeCase_FromPascal() { assertEquals("hello_world", invoke("snakeCase", S("HelloWorld")).asString()); }
        @Test void snakeCase_FromKebab() { assertEquals("hello_world", invoke("snakeCase", S("hello-world")).asString()); }
        @Test void snakeCase_Spaces() { assertEquals("hello_world_test", invoke("snakeCase", S("hello world test")).asString()); }
        @Test void snakeCase_Null() { assertTrue(invoke("snakeCase", Null()).isNull()); }

        @Test void kebabCase_Empty() { assertEquals("", invoke("kebabCase", S("")).asString()); }
        @Test void kebabCase_SingleWord() { assertEquals("hello", invoke("kebabCase", S("hello")).asString()); }
        @Test void kebabCase_FromCamel() { assertEquals("hello-world", invoke("kebabCase", S("helloWorld")).asString()); }
        @Test void kebabCase_FromSnake() { assertEquals("hello-world", invoke("kebabCase", S("hello_world")).asString()); }
        @Test void kebabCase_FromPascal() { assertEquals("hello-world", invoke("kebabCase", S("HelloWorld")).asString()); }
        @Test void kebabCase_Spaces() { assertEquals("hello-world-test", invoke("kebabCase", S("hello world test")).asString()); }
        @Test void kebabCase_Null() { assertTrue(invoke("kebabCase", Null()).isNull()); }

        @Test void pascalCase_Empty() { assertEquals("", invoke("pascalCase", S("")).asString()); }
        @Test void pascalCase_SingleWord() { assertEquals("Hello", invoke("pascalCase", S("hello")).asString()); }
        @Test void pascalCase_FromCamel() { assertEquals("HelloWorld", invoke("pascalCase", S("helloWorld")).asString()); }
        @Test void pascalCase_FromSnake() { assertEquals("HelloWorld", invoke("pascalCase", S("hello_world")).asString()); }
        @Test void pascalCase_FromKebab() { assertEquals("HelloWorld", invoke("pascalCase", S("hello-world")).asString()); }
        @Test void pascalCase_MultipleWords() { assertEquals("TheQuickBrownFox", invoke("pascalCase", S("the quick brown fox")).asString()); }
        @Test void pascalCase_Null() { assertTrue(invoke("pascalCase", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- slugify
    // =========================================================================

    @Nested class SlugifyTests {

        @Test void slugify_Empty() { assertEquals("", invoke("slugify", S("")).asString()); }
        @Test void slugify_AlreadySlug() { assertEquals("hello-world", invoke("slugify", S("hello-world")).asString()); }
        @Test void slugify_SpecialChars() { assertEquals("hello-world-1", invoke("slugify", S("Hello, World! #1")).asString()); }
        @Test void slugify_MultipleSpaces() { assertEquals("hello-world", invoke("slugify", S("hello   world")).asString()); }
        @Test void slugify_LeadingTrailingSpecial() { assertEquals("hello", invoke("slugify", S("!!hello!!")).asString()); }
        @Test void slugify_Numbers() { assertEquals("test-123-stuff", invoke("slugify", S("Test 123 Stuff")).asString()); }
        @Test void slugify_Null() { assertTrue(invoke("slugify", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- matches / match
    // =========================================================================

    @Nested class MatchesTests {

        @Test void matches_HappyPath() { assertEquals(true, invoke("matches", S("hello"), S("ell")).asBool()); }
        @Test void matches_NoMatch() { assertEquals(false, invoke("matches", S("hello"), S("xyz")).asBool()); }
        @Test void matches_Null() { assertEquals(false, invoke("matches", Null(), S("x")).asBool()); }

        @Test void match_HappyPath() {
            assertTrue(invoke("match", S("hello world"), S("w\\w+")).asBool());
        }

        @Test void match_NoMatch() { assertFalse(invoke("match", S("hello"), S("xyz")).asBool()); }
        @Test void match_Null() { assertTrue(invoke("match", Null(), S("x")).isNull()); }
    }

    // =========================================================================
    // String verbs -- normalizeSpace
    // =========================================================================

    @Nested class NormalizeSpaceTests {

        @Test void normalizeSpace_Empty() { assertEquals("", invoke("normalizeSpace", S("")).asString()); }
        @Test void normalizeSpace_OnlyWhitespace() { assertEquals("", invoke("normalizeSpace", S("   ")).asString()); }
        @Test void normalizeSpace_TabsAndNewlines() { assertEquals("hello world", invoke("normalizeSpace", S("hello\t\nworld")).asString()); }
        @Test void normalizeSpace_SingleWord() { assertEquals("hello", invoke("normalizeSpace", S("hello")).asString()); }
        @Test void normalizeSpace_Null() { assertTrue(invoke("normalizeSpace", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- leftOf / rightOf
    // =========================================================================

    @Nested class LeftOfRightOfTests {

        @Test void leftOf_NoDelimiter() { assertEquals("hello", invoke("leftOf", S("hello"), S("@")).asString()); }
        @Test void leftOf_AtStart() { assertEquals("", invoke("leftOf", S("@hello"), S("@")).asString()); }
        @Test void leftOf_Multiple() { assertEquals("a", invoke("leftOf", S("a@b@c"), S("@")).asString()); }
        @Test void leftOf_EmptyString() { assertEquals("", invoke("leftOf", S(""), S("@")).asString()); }
        @Test void leftOf_HappyPath() { assertEquals("user", invoke("leftOf", S("user@example.com"), S("@")).asString()); }

        @Test void rightOf_NoDelimiter() { assertEquals("hello", invoke("rightOf", S("hello"), S("@")).asString()); }
        @Test void rightOf_AtEnd() { assertEquals("", invoke("rightOf", S("hello@"), S("@")).asString()); }
        @Test void rightOf_Multiple() { assertEquals("b@c", invoke("rightOf", S("a@b@c"), S("@")).asString()); }
        @Test void rightOf_EmptyString() { assertEquals("", invoke("rightOf", S(""), S("@")).asString()); }
        @Test void rightOf_HappyPath() { assertEquals("example.com", invoke("rightOf", S("user@example.com"), S("@")).asString()); }
    }

    // =========================================================================
    // String verbs -- wrap / center
    // =========================================================================

    @Nested class WrapCenterTests {

        @Test void wrap_WordWraps() { assertEquals("the quick\nbrown fox", invoke("wrap", S("the quick brown fox"), I(10)).asString()); }
        @Test void wrap_ShorterThanWidth() { assertEquals("hello", invoke("wrap", S("hello"), I(20)).asString()); }
        @Test void wrap_Null() { assertEquals("", invoke("wrap", Null(), I(10)).asString()); }

        @Test void center_AlreadyWide() { assertEquals("hello", invoke("center", S("hello"), I(3), S("-")).asString()); }
        @Test void center_OddPadding() { assertEquals("-ab--", invoke("center", S("ab"), I(5), S("-")).asString()); }
        @Test void center_EmptyString() { assertEquals("****", invoke("center", S(""), I(4), S("*")).asString()); }
    }

    // =========================================================================
    // String verbs -- stripAccents
    // =========================================================================

    @Nested class StripAccentsTests {

        @Test void stripAccents_Empty() { assertEquals("", invoke("stripAccents", S("")).asString()); }
        @Test void stripAccents_NoAccents() { assertEquals("hello", invoke("stripAccents", S("hello")).asString()); }
        @Test void stripAccents_Cafe() { assertEquals("cafe", invoke("stripAccents", S("caf\u00e9")).asString()); }
        @Test void stripAccents_Upper() { assertEquals("AAA", invoke("stripAccents", S("\u00c0\u00c1\u00c2")).asString()); }
        @Test void stripAccents_NTilde() { assertEquals("nN", invoke("stripAccents", S("\u00f1\u00d1")).asString()); }
        @Test void stripAccents_Null() { assertTrue(invoke("stripAccents", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- clean
    // =========================================================================

    @Nested class CleanTests {

        @Test void clean_Empty() { assertEquals("", invoke("clean", S("")).asString()); }
        @Test void clean_NoControlChars() { assertEquals("hello world", invoke("clean", S("hello world")).asString()); }
        @Test void clean_CollapsesWhitespace() { assertEquals("a b c", invoke("clean", S("a\nb\tc\r")).asString()); }
        @Test void clean_RemovesNullBytes() { assertEquals("abcd", invoke("clean", S("a\0b\u0001c\u0002d")).asString()); }
        @Test void clean_Null() { assertTrue(invoke("clean", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- wordCount / tokenize
    // =========================================================================

    @Nested class WordCountTokenizeTests {

        @Test void wordCount_Empty() { assertEquals(0L, invoke("wordCount", S("")).asInt64()); }
        @Test void wordCount_OnlyWhitespace() { assertEquals(0L, invoke("wordCount", S("   ")).asInt64()); }
        @Test void wordCount_SingleWord() { assertEquals(1L, invoke("wordCount", S("hello")).asInt64()); }
        @Test void wordCount_MultipleSpaces() { assertEquals(2L, invoke("wordCount", S("  hello   world  ")).asInt64()); }
        @Test void wordCount_WithTabs() { assertEquals(3L, invoke("wordCount", S("a\tb\tc")).asInt64()); }
        @Test void wordCount_Null() { assertEquals(0L, invoke("wordCount", Null()).asInt64()); }

        @Test void tokenize_Empty() {
            var result = invoke("tokenize", S(""));
            var arr = result.asArray();
            assertNotNull(arr);
            assertTrue(arr.isEmpty());
        }

        @Test void tokenize_Whitespace() {
            var result = invoke("tokenize", S("hello world test"));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(3, arr.size());
            assertEquals("hello", arr.get(0).asString());
            assertEquals("world", arr.get(1).asString());
            assertEquals("test", arr.get(2).asString());
        }
    }

    // =========================================================================
    // String verbs -- levenshtein
    // =========================================================================

    @Nested class LevenshteinTests {

        @Test void levenshtein_Identical() { assertEquals(0L, invoke("levenshtein", S("hello"), S("hello")).asInt64()); }
        @Test void levenshtein_EmptyFirst() { assertEquals(5L, invoke("levenshtein", S(""), S("hello")).asInt64()); }
        @Test void levenshtein_EmptySecond() { assertEquals(5L, invoke("levenshtein", S("hello"), S("")).asInt64()); }
        @Test void levenshtein_BothEmpty() { assertEquals(0L, invoke("levenshtein", S(""), S("")).asInt64()); }
        @Test void levenshtein_SingleEdit() { assertEquals(1L, invoke("levenshtein", S("kitten"), S("sitten")).asInt64()); }
        @Test void levenshtein_Classic() { assertEquals(3L, invoke("levenshtein", S("kitten"), S("sitting")).asInt64()); }
    }

    // =========================================================================
    // String verbs -- soundex
    // =========================================================================

    @Nested class SoundexTests {

        @Test void soundex_Robert() { assertEquals("R163", invoke("soundex", S("Robert")).asString()); }
        @Test void soundex_Rupert() { assertEquals("R163", invoke("soundex", S("Rupert")).asString()); }
        @Test void soundex_Ashcraft() { assertEquals("A226", invoke("soundex", S("Ashcraft")).asString()); }
        @Test void soundex_Empty() { assertEquals("", invoke("soundex", S("")).asString()); }
        @Test void soundex_SingleLetter() { assertEquals("A000", invoke("soundex", S("A")).asString()); }
        @Test void soundex_Null() { assertTrue(invoke("soundex", Null()).isNull()); }
    }

    // =========================================================================
    // String verbs -- substring / length
    // =========================================================================

    @Nested class SubstringLengthTests {

        @Test void substring_HappyPath() { assertEquals("llo", invoke("substring", S("hello"), I(2)).asString()); }
        @Test void substring_WithLength() { assertEquals("ell", invoke("substring", S("hello"), I(1), I(3)).asString()); }
        @Test void substring_BeyondEnd() { assertEquals("lo", invoke("substring", S("hello"), I(3), I(100)).asString()); }
        @Test void substring_Null() { assertTrue(invoke("substring", Null(), I(0)).isNull()); }

        @Test void length_String() { assertEquals(5L, invoke("length", S("hello")).asInt64()); }
        @Test void length_EmptyString() { assertEquals(0L, invoke("length", S("")).asInt64()); }
        @Test void length_EmptyArray() { assertEquals(0L, invoke("length", Arr()).asInt64()); }
        @Test void length_Array() { assertEquals(3L, invoke("length", Arr(I(1), I(2), I(3))).asInt64()); }
        @Test void length_Null() { assertEquals(0L, invoke("length", Null()).asInt64()); }
    }

    // =========================================================================
    // String verbs -- capitalize
    // =========================================================================

    @Nested class CapitalizeTests {

        @Test void capitalize_HappyPath() { assertEquals("Hello", invoke("capitalize", S("hello")).asString()); }
        @Test void capitalize_Empty() { assertEquals("", invoke("capitalize", S("")).asString()); }
        @Test void capitalize_AlreadyCapitalized() { assertEquals("Hello", invoke("capitalize", S("Hello")).asString()); }
        @Test void capitalize_AllUpper() { assertEquals("Hello", invoke("capitalize", S("HELLO")).asString()); }
        @Test void capitalize_Null() { assertTrue(invoke("capitalize", Null()).isNull()); }
    }

    // =========================================================================
    // Assert verb
    // =========================================================================

    @Nested class AssertVerbTests {

        @Test void assert_TruthyInt() { assertEquals(1L, invoke("assert", I(1)).asInt64()); }
        @Test void assert_FalsyZero_Throws() { assertThrows(RuntimeException.class, () -> invoke("assert", I(0))); }
        @Test void assert_TruthyString() { assertEquals("yes", invoke("assert", S("yes")).asString()); }
        @Test void assert_FalsyNull_Throws() { assertThrows(RuntimeException.class, () -> invoke("assert", Null())); }
    }

    // =========================================================================
    // Coercion -- tryCoerce / toArray / toObject / coerceDate / coerceTimestamp
    // =========================================================================

    @Nested class ExtendedCoercionTests {

        @Test void tryCoerce_Integer() { assertEquals(42L, invoke("tryCoerce", S("42")).asInt64()); }
        @Test void tryCoerce_Float() { assertEquals(3.14, invoke("tryCoerce", S("3.14")).asDouble()); }
        @Test void tryCoerce_BoolTrue() { assertEquals(true, invoke("tryCoerce", S("true")).asBool()); }
        @Test void tryCoerce_BoolFalse() { assertEquals(false, invoke("tryCoerce", S("false")).asBool()); }
        @Test void tryCoerce_Date() { assertEquals("2024-01-15", invoke("tryCoerce", S("2024-01-15")).asString()); }
        @Test void tryCoerce_PlainString() { assertEquals("hello", invoke("tryCoerce", S("hello")).asString()); }
        @Test void tryCoerce_NonString() { assertEquals(42L, invoke("tryCoerce", I(42)).asInt64()); }

        @Test void toArray_Single() {
            var result = invoke("toArray", I(42));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(1, arr.size());
            assertEquals(42L, arr.get(0).asInt64());
        }

        @Test void toArray_AlreadyArray() {
            var input = Arr(I(1), I(2));
            var result = invoke("toArray", input);
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(2, arr.size());
        }

        @Test void toArray_NoArgs() {
            var result = invoke("toArray");
            var arr = result.asArray();
            assertNotNull(arr);
            assertTrue(arr.isEmpty());
        }

        @Test void coerceDate_ValidDate() {
            var result = invoke("coerceDate", S("2024-06-15"));
            assertEquals("2024-06-15", result.asString());
        }

        @Test void coerceDate_Null() {
            var result = invoke("coerceDate", Null());
            assertTrue(result.isNull());
        }

        @Test void coerceDate_InvalidString_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("coerceDate", S("not-a-date")));
        }

        @Test void coerceTimestamp_ValidTimestamp() {
            var result = invoke("coerceTimestamp", S("2024-06-15T14:30:00"));
            assertNotNull(result.asString());
            assertTrue(result.asString().contains("2024-06-15"));
        }

        @Test void coerceTimestamp_BareDate() {
            var result = invoke("coerceTimestamp", S("2024-06-15"));
            assertTrue(result.asString().contains("2024-06-15"));
            assertTrue(result.asString().contains("00:00:00"));
        }

        @Test void coerceTimestamp_Null() {
            var result = invoke("coerceTimestamp", Null());
            assertTrue(result.isNull());
        }
    }
}
