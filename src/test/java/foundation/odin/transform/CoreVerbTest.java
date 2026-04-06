package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Port of .NET CoreVerbTests — tests all core verbs via VerbRegistry.
 * <p>
 * Expects a {@code VerbRegistry} class with:
 * <pre>
 *   var registry = new VerbRegistry();
 *   DynValue result = registry.invoke("verbName", args, ctx);
 * </pre>
 */
class CoreVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    // ─────────────────────────────────────────────────────────────────
    // concat
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class ConcatTests {

        @Test
        void concat_TwoStrings() {
            assertEquals("helloworld", invoke("concat", DynValue.ofString("hello"), DynValue.ofString("world")).asString());
        }

        @Test
        void concat_MultipleArgs() {
            assertEquals("abc", invoke("concat", DynValue.ofString("a"), DynValue.ofString("b"), DynValue.ofString("c")).asString());
        }

        @Test
        void concat_SkipsNull() {
            assertEquals("hello", invoke("concat", DynValue.ofString("hello"), DynValue.ofNull()).asString());
        }

        @Test
        void concat_AllNull() {
            assertEquals("", invoke("concat", DynValue.ofNull(), DynValue.ofNull()).asString());
        }

        @Test
        void concat_NumbersCoerced() {
            assertEquals("42", invoke("concat", DynValue.ofInteger(42)).asString());
        }

        @Test
        void concat_EmptyArgs() {
            assertEquals("", invoke("concat").asString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // upper
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class UpperTests {

        @Test
        void upper_HappyPath() {
            assertEquals("HELLO", invoke("upper", DynValue.ofString("hello")).asString());
        }

        @Test
        void upper_NullPassthrough() {
            assertTrue(invoke("upper", DynValue.ofNull()).isNull());
        }

        @Test
        void upper_NoArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("upper"));
        }

        @Test
        void upper_IntegerThrows() {
            assertThrows(RuntimeException.class, () -> invoke("upper", DynValue.ofInteger(42)));
        }

        @Test
        void upper_EmptyString() {
            assertEquals("", invoke("upper", DynValue.ofString("")).asString());
        }

        @Test
        void upper_MixedCase() {
            assertEquals("HELLO WORLD", invoke("upper", DynValue.ofString("Hello World")).asString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // lower
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class LowerTests {

        @Test
        void lower_HappyPath() {
            assertEquals("hello", invoke("lower", DynValue.ofString("HELLO")).asString());
        }

        @Test
        void lower_NullPassthrough() {
            assertTrue(invoke("lower", DynValue.ofNull()).isNull());
        }

        @Test
        void lower_NoArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("lower"));
        }

        @Test
        void lower_IntegerThrows() {
            assertThrows(RuntimeException.class, () -> invoke("lower", DynValue.ofInteger(42)));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // trim, trimLeft, trimRight
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class TrimTests {

        @Test
        void trim_HappyPath() {
            assertEquals("hello", invoke("trim", DynValue.ofString("  hello  ")).asString());
        }

        @Test
        void trim_NullPassthrough() {
            assertTrue(invoke("trim", DynValue.ofNull()).isNull());
        }

        @Test
        void trim_NoArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("trim"));
        }

        @Test
        void trimLeft_HappyPath() {
            assertEquals("hello  ", invoke("trimLeft", DynValue.ofString("  hello  ")).asString());
        }

        @Test
        void trimLeft_NullPassthrough() {
            assertTrue(invoke("trimLeft", DynValue.ofNull()).isNull());
        }

        @Test
        void trimRight_HappyPath() {
            assertEquals("  hello", invoke("trimRight", DynValue.ofString("  hello  ")).asString());
        }

        @Test
        void trimRight_NullPassthrough() {
            assertTrue(invoke("trimRight", DynValue.ofNull()).isNull());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // coalesce
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CoalesceTests {

        @Test
        void coalesce_ReturnsFirstNonNull() {
            assertEquals("hello", invoke("coalesce", DynValue.ofNull(), DynValue.ofString("hello")).asString());
        }

        @Test
        void coalesce_SkipsEmptyString() {
            assertEquals("hello", invoke("coalesce", DynValue.ofString(""), DynValue.ofString("hello")).asString());
        }

        @Test
        void coalesce_AllNull() {
            assertTrue(invoke("coalesce", DynValue.ofNull(), DynValue.ofNull()).isNull());
        }

        @Test
        void coalesce_ReturnsInteger() {
            assertEquals(42L, (long) invoke("coalesce", DynValue.ofNull(), DynValue.ofInteger(42)).asInt64());
        }

        @Test
        void coalesce_ReturnsFirstValue() {
            assertEquals("first", invoke("coalesce", DynValue.ofString("first"), DynValue.ofString("second")).asString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ifNull
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class IfNullTests {

        @Test
        void ifNull_NullReturnsDefault() {
            assertEquals("default", invoke("ifNull", DynValue.ofNull(), DynValue.ofString("default")).asString());
        }

        @Test
        void ifNull_NonNullReturnsFirst() {
            assertEquals("value", invoke("ifNull", DynValue.ofString("value"), DynValue.ofString("default")).asString());
        }

        @Test
        void ifNull_TooFewArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("ifNull", DynValue.ofNull()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ifEmpty
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class IfEmptyTests {

        @Test
        void ifEmpty_NullReturnsDefault() {
            assertEquals("default", invoke("ifEmpty", DynValue.ofNull(), DynValue.ofString("default")).asString());
        }

        @Test
        void ifEmpty_EmptyStringReturnsDefault() {
            assertEquals("default", invoke("ifEmpty", DynValue.ofString(""), DynValue.ofString("default")).asString());
        }

        @Test
        void ifEmpty_NonEmptyReturnsFirst() {
            assertEquals("value", invoke("ifEmpty", DynValue.ofString("value"), DynValue.ofString("default")).asString());
        }

        @Test
        void ifEmpty_TooFewArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("ifEmpty", DynValue.ofNull()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // ifElse
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class IfElseTests {

        @Test
        void ifElse_TruthyReturnsThen() {
            assertEquals("yes", invoke("ifElse", DynValue.ofBool(true), DynValue.ofString("yes"), DynValue.ofString("no")).asString());
        }

        @Test
        void ifElse_FalsyReturnsElse() {
            assertEquals("no", invoke("ifElse", DynValue.ofBool(false), DynValue.ofString("yes"), DynValue.ofString("no")).asString());
        }

        @Test
        void ifElse_NullIsFalsy() {
            assertEquals("no", invoke("ifElse", DynValue.ofNull(), DynValue.ofString("yes"), DynValue.ofString("no")).asString());
        }

        @Test
        void ifElse_NonZeroIsTruthy() {
            assertEquals("yes", invoke("ifElse", DynValue.ofInteger(1), DynValue.ofString("yes"), DynValue.ofString("no")).asString());
        }

        @Test
        void ifElse_ZeroIsFalsy() {
            assertEquals("no", invoke("ifElse", DynValue.ofInteger(0), DynValue.ofString("yes"), DynValue.ofString("no")).asString());
        }

        @Test
        void ifElse_TooFewArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("ifElse", DynValue.ofBool(true), DynValue.ofString("yes")));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // and, or, not, xor
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class LogicTests {

        @Test
        void and_TrueTrue() {
            assertEquals(true, invoke("and", DynValue.ofBool(true), DynValue.ofBool(true)).asBool());
        }

        @Test
        void and_TrueFalse() {
            assertEquals(false, invoke("and", DynValue.ofBool(true), DynValue.ofBool(false)).asBool());
        }

        @Test
        void and_FalseFalse() {
            assertEquals(false, invoke("and", DynValue.ofBool(false), DynValue.ofBool(false)).asBool());
        }

        @Test
        void and_NonBoolThrows() {
            assertThrows(RuntimeException.class, () -> invoke("and", DynValue.ofString("true"), DynValue.ofBool(true)));
        }

        @Test
        void and_TooFewArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("and", DynValue.ofBool(true)));
        }

        @Test
        void or_TrueFalse() {
            assertEquals(true, invoke("or", DynValue.ofBool(true), DynValue.ofBool(false)).asBool());
        }

        @Test
        void or_FalseFalse() {
            assertEquals(false, invoke("or", DynValue.ofBool(false), DynValue.ofBool(false)).asBool());
        }

        @Test
        void or_TooFewArgs_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("or", DynValue.ofBool(true)));
        }

        @Test
        void not_True() {
            assertEquals(false, invoke("not", DynValue.ofBool(true)).asBool());
        }

        @Test
        void not_False() {
            assertEquals(true, invoke("not", DynValue.ofBool(false)).asBool());
        }

        @Test
        void not_Null() {
            assertEquals(true, invoke("not", DynValue.ofNull()).asBool());
        }

        @Test
        void not_Zero() {
            assertEquals(true, invoke("not", DynValue.ofInteger(0)).asBool());
        }

        @Test
        void not_NonZero() {
            assertEquals(false, invoke("not", DynValue.ofInteger(5)).asBool());
        }

        @Test
        void not_EmptyString() {
            assertEquals(true, invoke("not", DynValue.ofString("")).asBool());
        }

        @Test
        void not_NonEmptyString() {
            assertEquals(false, invoke("not", DynValue.ofString("hello")).asBool());
        }

        @Test
        void not_EmptyArray() {
            assertEquals(true, invoke("not", DynValue.ofArray(new ArrayList<>())).asBool());
        }

        @Test
        void not_NoArgs() {
            assertEquals(false, invoke("not").asBool());
        }

        @Test
        void xor_TrueFalse() {
            assertEquals(true, invoke("xor", DynValue.ofBool(true), DynValue.ofBool(false)).asBool());
        }

        @Test
        void xor_TrueTrue() {
            assertEquals(false, invoke("xor", DynValue.ofBool(true), DynValue.ofBool(true)).asBool());
        }

        @Test
        void xor_FalseFalse() {
            assertEquals(false, invoke("xor", DynValue.ofBool(false), DynValue.ofBool(false)).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // eq, ne
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class EqualityTests {

        @Test
        void eq_SameIntegers() {
            assertEquals(true, invoke("eq", DynValue.ofInteger(42), DynValue.ofInteger(42)).asBool());
        }

        @Test
        void eq_DifferentIntegers() {
            assertEquals(false, invoke("eq", DynValue.ofInteger(42), DynValue.ofInteger(43)).asBool());
        }

        @Test
        void eq_SameStrings() {
            assertEquals(true, invoke("eq", DynValue.ofString("hello"), DynValue.ofString("hello")).asBool());
        }

        @Test
        void eq_DifferentStrings() {
            assertEquals(false, invoke("eq", DynValue.ofString("hello"), DynValue.ofString("world")).asBool());
        }

        @Test
        void eq_NullNull() {
            assertEquals(true, invoke("eq", DynValue.ofNull(), DynValue.ofNull()).asBool());
        }

        @Test
        void eq_CrossTypeIntegerFloat() {
            assertEquals(true, invoke("eq", DynValue.ofInteger(42), DynValue.ofFloat(42.0)).asBool());
        }

        @Test
        void eq_StringNumber() {
            assertEquals(true, invoke("eq", DynValue.ofString("42"), DynValue.ofInteger(42)).asBool());
        }

        @Test
        void ne_DifferentValues() {
            assertEquals(true, invoke("ne", DynValue.ofInteger(1), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void ne_SameValues() {
            assertEquals(false, invoke("ne", DynValue.ofInteger(1), DynValue.ofInteger(1)).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // lt, lte, gt, gte
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class ComparisonTests {

        @Test
        void lt_IntLess() {
            assertEquals(true, invoke("lt", DynValue.ofInteger(1), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void lt_IntEqual() {
            assertEquals(false, invoke("lt", DynValue.ofInteger(2), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void lt_IntGreater() {
            assertEquals(false, invoke("lt", DynValue.ofInteger(3), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void lt_Strings() {
            assertEquals(true, invoke("lt", DynValue.ofString("abc"), DynValue.ofString("def")).asBool());
        }

        @Test
        void lte_IntEqual() {
            assertEquals(true, invoke("lte", DynValue.ofInteger(2), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void lte_IntLess() {
            assertEquals(true, invoke("lte", DynValue.ofInteger(1), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void gt_IntGreater() {
            assertEquals(true, invoke("gt", DynValue.ofInteger(3), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void gt_IntEqual() {
            assertEquals(false, invoke("gt", DynValue.ofInteger(2), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void gte_IntEqual() {
            assertEquals(true, invoke("gte", DynValue.ofInteger(2), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void gte_IntGreater() {
            assertEquals(true, invoke("gte", DynValue.ofInteger(3), DynValue.ofInteger(2)).asBool());
        }

        @Test
        void gte_IntLess() {
            assertEquals(false, invoke("gte", DynValue.ofInteger(1), DynValue.ofInteger(2)).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // between
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class BetweenTests {

        @Test
        void between_InRange() {
            assertEquals(true, invoke("between", DynValue.ofInteger(5), DynValue.ofInteger(1), DynValue.ofInteger(10)).asBool());
        }

        @Test
        void between_AtMin() {
            assertEquals(true, invoke("between", DynValue.ofInteger(1), DynValue.ofInteger(1), DynValue.ofInteger(10)).asBool());
        }

        @Test
        void between_AtMax() {
            assertEquals(true, invoke("between", DynValue.ofInteger(10), DynValue.ofInteger(1), DynValue.ofInteger(10)).asBool());
        }

        @Test
        void between_OutOfRange() {
            assertEquals(false, invoke("between", DynValue.ofInteger(11), DynValue.ofInteger(1), DynValue.ofInteger(10)).asBool());
        }

        @Test
        void between_Strings() {
            assertEquals(true, invoke("between", DynValue.ofString("c"), DynValue.ofString("a"), DynValue.ofString("z")).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // isNull, isString, isNumber, isBoolean, isArray, isObject
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class TypeCheckTests {

        @Test
        void isNull_Null() {
            assertEquals(true, invoke("isNull", DynValue.ofNull()).asBool());
        }

        @Test
        void isNull_NonNull() {
            assertEquals(false, invoke("isNull", DynValue.ofString("hi")).asBool());
        }

        @Test
        void isNull_NoArgs() {
            assertEquals(true, invoke("isNull").asBool());
        }

        @Test
        void isString_String() {
            assertEquals(true, invoke("isString", DynValue.ofString("hi")).asBool());
        }

        @Test
        void isString_Integer() {
            assertEquals(false, invoke("isString", DynValue.ofInteger(42)).asBool());
        }

        @Test
        void isString_NoArgs() {
            assertEquals(false, invoke("isString").asBool());
        }

        @Test
        void isNumber_Integer() {
            assertEquals(true, invoke("isNumber", DynValue.ofInteger(42)).asBool());
        }

        @Test
        void isNumber_Float() {
            assertEquals(true, invoke("isNumber", DynValue.ofFloat(3.14)).asBool());
        }

        @Test
        void isNumber_String() {
            assertEquals(false, invoke("isNumber", DynValue.ofString("42")).asBool());
        }

        @Test
        void isBoolean_Bool() {
            assertEquals(true, invoke("isBoolean", DynValue.ofBool(true)).asBool());
        }

        @Test
        void isBoolean_String() {
            assertEquals(false, invoke("isBoolean", DynValue.ofString("true")).asBool());
        }

        @Test
        void isArray_Array() {
            assertEquals(true, invoke("isArray", DynValue.ofArray(new ArrayList<>())).asBool());
        }

        @Test
        void isArray_StringArray() {
            assertEquals(true, invoke("isArray", DynValue.ofString("[1,2,3]")).asBool());
        }

        @Test
        void isArray_NonArray() {
            assertEquals(false, invoke("isArray", DynValue.ofInteger(42)).asBool());
        }

        @Test
        void isObject_Object() {
            var obj = DynValue.ofObject(List.of(Map.entry("k", DynValue.ofString("v"))));
            assertEquals(true, invoke("isObject", obj).asBool());
        }

        @Test
        void isObject_StringObject() {
            assertEquals(true, invoke("isObject", DynValue.ofString("{\"k\":\"v\"}")).asBool());
        }

        @Test
        void isObject_NonObject() {
            assertEquals(false, invoke("isObject", DynValue.ofInteger(42)).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // typeOf
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class TypeOfTests {

        @Test
        void typeOf_Null() {
            assertEquals("null", invoke("typeOf", DynValue.ofNull()).asString());
        }

        @Test
        void typeOf_Bool() {
            assertEquals("boolean", invoke("typeOf", DynValue.ofBool(true)).asString());
        }

        @Test
        void typeOf_String() {
            assertEquals("string", invoke("typeOf", DynValue.ofString("hi")).asString());
        }

        @Test
        void typeOf_Integer() {
            assertEquals("integer", invoke("typeOf", DynValue.ofInteger(1)).asString());
        }

        @Test
        void typeOf_Float() {
            assertEquals("number", invoke("typeOf", DynValue.ofFloat(1.5)).asString());
        }

        @Test
        void typeOf_Array() {
            assertEquals("array", invoke("typeOf", DynValue.ofArray(new ArrayList<>())).asString());
        }

        @Test
        void typeOf_Object() {
            var obj = DynValue.ofObject(new ArrayList<>());
            assertEquals("object", invoke("typeOf", obj).asString());
        }

        @Test
        void typeOf_NoArgs() {
            assertEquals("null", invoke("typeOf").asString());
        }

        @Test
        void typeOf_Currency() {
            assertEquals("currency", invoke("typeOf", DynValue.ofCurrency(99.99)).asString());
        }

        @Test
        void typeOf_Date() {
            assertEquals("date", invoke("typeOf", DynValue.ofDate("2024-01-01")).asString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // cond
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CondTests {

        @Test
        void cond_FirstTruthy() {
            assertEquals("a", invoke("cond",
                    DynValue.ofBool(true), DynValue.ofString("a"),
                    DynValue.ofBool(true), DynValue.ofString("b")).asString());
        }

        @Test
        void cond_SecondTruthy() {
            assertEquals("b", invoke("cond",
                    DynValue.ofBool(false), DynValue.ofString("a"),
                    DynValue.ofBool(true), DynValue.ofString("b")).asString());
        }

        @Test
        void cond_DefaultValue() {
            assertEquals("default", invoke("cond",
                    DynValue.ofBool(false), DynValue.ofString("a"),
                    DynValue.ofString("default")).asString());
        }

        @Test
        void cond_NoMatch() {
            assertTrue(invoke("cond", DynValue.ofBool(false), DynValue.ofString("a")).isNull());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // coerceString
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CoerceStringTests {

        @Test
        void coerceString_Integer() {
            assertEquals("42", invoke("coerceString", DynValue.ofInteger(42)).asString());
        }

        @Test
        void coerceString_Float() {
            assertEquals("3.14", invoke("coerceString", DynValue.ofFloat(3.14)).asString());
        }

        @Test
        void coerceString_Bool() {
            assertEquals("true", invoke("coerceString", DynValue.ofBool(true)).asString());
        }

        @Test
        void coerceString_Null() {
            assertTrue(invoke("coerceString", DynValue.ofNull()).isNull());
        }

        @Test
        void coerceString_String() {
            assertEquals("hello", invoke("coerceString", DynValue.ofString("hello")).asString());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // coerceNumber
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CoerceNumberTests {

        @Test
        void coerceNumber_String() {
            assertEquals(42.0, invoke("coerceNumber", DynValue.ofString("42")).asDouble());
        }

        @Test
        void coerceNumber_IntegerToFloat() {
            assertEquals(42.0, invoke("coerceNumber", DynValue.ofInteger(42)).asDouble());
        }

        @Test
        void coerceNumber_FloatPassthrough() {
            assertEquals(3.14, invoke("coerceNumber", DynValue.ofFloat(3.14)).asDouble());
        }

        @Test
        void coerceNumber_BoolTrue() {
            assertEquals(1.0, invoke("coerceNumber", DynValue.ofBool(true)).asDouble());
        }

        @Test
        void coerceNumber_BoolFalse() {
            assertEquals(0.0, invoke("coerceNumber", DynValue.ofBool(false)).asDouble());
        }

        @Test
        void coerceNumber_Null() {
            assertTrue(invoke("coerceNumber", DynValue.ofNull()).isNull());
        }

        @Test
        void coerceNumber_InvalidString_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("coerceNumber", DynValue.ofString("abc")));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // coerceInteger
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CoerceIntegerTests {

        @Test
        void coerceInteger_String() {
            assertEquals(42L, (long) invoke("coerceInteger", DynValue.ofString("42")).asInt64());
        }

        @Test
        void coerceInteger_FloatTruncates() {
            assertEquals(3L, (long) invoke("coerceInteger", DynValue.ofFloat(3.7)).asInt64());
        }

        @Test
        void coerceInteger_IntegerPassthrough() {
            assertEquals(42L, (long) invoke("coerceInteger", DynValue.ofInteger(42)).asInt64());
        }

        @Test
        void coerceInteger_BoolTrue() {
            assertEquals(1L, (long) invoke("coerceInteger", DynValue.ofBool(true)).asInt64());
        }

        @Test
        void coerceInteger_Null() {
            assertTrue(invoke("coerceInteger", DynValue.ofNull()).isNull());
        }

        @Test
        void coerceInteger_InvalidString_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("coerceInteger", DynValue.ofString("abc")));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // coerceBoolean
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class CoerceBooleanTests {

        @Test
        void coerceBoolean_TrueString() {
            assertEquals(true, invoke("coerceBoolean", DynValue.ofString("true")).asBool());
        }

        @Test
        void coerceBoolean_FalseString() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofString("false")).asBool());
        }

        @Test
        void coerceBoolean_ZeroString() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofString("0")).asBool());
        }

        @Test
        void coerceBoolean_EmptyString() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofString("")).asBool());
        }

        @Test
        void coerceBoolean_NoString() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofString("no")).asBool());
        }

        @Test
        void coerceBoolean_YesString() {
            assertEquals(true, invoke("coerceBoolean", DynValue.ofString("yes")).asBool());
        }

        @Test
        void coerceBoolean_NonZeroInteger() {
            assertEquals(true, invoke("coerceBoolean", DynValue.ofInteger(5)).asBool());
        }

        @Test
        void coerceBoolean_ZeroInteger() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofInteger(0)).asBool());
        }

        @Test
        void coerceBoolean_Null() {
            assertEquals(false, invoke("coerceBoolean", DynValue.ofNull()).asBool());
        }

        @Test
        void coerceBoolean_BoolPassthrough() {
            assertEquals(true, invoke("coerceBoolean", DynValue.ofBool(true)).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // switch
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class SwitchTests {

        @Test
        void switch_MatchesFirst() {
            assertEquals("one", invoke("switch",
                    DynValue.ofInteger(1),
                    DynValue.ofInteger(1), DynValue.ofString("one"),
                    DynValue.ofInteger(2), DynValue.ofString("two")).asString());
        }

        @Test
        void switch_MatchesSecond() {
            assertEquals("two", invoke("switch",
                    DynValue.ofInteger(2),
                    DynValue.ofInteger(1), DynValue.ofString("one"),
                    DynValue.ofInteger(2), DynValue.ofString("two")).asString());
        }

        @Test
        void switch_DefaultValue() {
            assertEquals("default", invoke("switch",
                    DynValue.ofInteger(3),
                    DynValue.ofInteger(1), DynValue.ofString("one"),
                    DynValue.ofString("default")).asString());
        }

        @Test
        void switch_NoMatchNoDefault() {
            assertTrue(invoke("switch",
                    DynValue.ofInteger(3),
                    DynValue.ofInteger(1), DynValue.ofString("one")).isNull());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // isFinite, isNaN
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class NumericCheckTests {

        @Test
        void isFinite_Integer() {
            assertEquals(true, invoke("isFinite", DynValue.ofInteger(42)).asBool());
        }

        @Test
        void isFinite_Float() {
            assertEquals(true, invoke("isFinite", DynValue.ofFloat(3.14)).asBool());
        }

        @Test
        void isFinite_Infinity() {
            assertEquals(false, invoke("isFinite", DynValue.ofFloat(Double.POSITIVE_INFINITY)).asBool());
        }

        @Test
        void isFinite_NaN() {
            assertEquals(false, invoke("isFinite", DynValue.ofFloat(Double.NaN)).asBool());
        }

        @Test
        void isFinite_NoArgs() {
            assertEquals(false, invoke("isFinite").asBool());
        }

        @Test
        void isNaN_NaN() {
            assertEquals(true, invoke("isNaN", DynValue.ofFloat(Double.NaN)).asBool());
        }

        @Test
        void isNaN_Number() {
            assertEquals(false, invoke("isNaN", DynValue.ofFloat(42.0)).asBool());
        }

        @Test
        void isNaN_String() {
            assertEquals(true, invoke("isNaN", DynValue.ofString("abc")).asBool());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Unknown verb throws
    // ─────────────────────────────────────────────────────────────────

    @Nested
    class UnknownVerbTests {

        @Test
        void unknownVerb_Throws() {
            assertThrows(RuntimeException.class, () -> invoke("nonExistentVerb", DynValue.ofNull()));
        }
    }
}
