package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for string manipulation, text analysis, and encoding verbs.
 * Ported from .NET SDK StringVerbTests.cs.
 */
class StringVerbTest {

    private final VerbContext ctx = new VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args, ctx);
    }

    // Shorthand helpers (matching .NET test helpers)
    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }
    private static DynValue Obj(Map.Entry<String, DynValue>... pairs) {
        var list = new ArrayList<Map.Entry<String, DynValue>>();
        for (var p : pairs) list.add(p);
        return DynValue.ofObject(list);
    }
    @SuppressWarnings("unchecked")
    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }

    // =========================================================================
    // capitalize
    // =========================================================================

    @Nested
    class Capitalize {
        @Test
        void basic() {
            assertEquals("Hello", invoke("capitalize", S("hello")).asString());
        }

        @Test
        void emptyString() {
            assertEquals("", invoke("capitalize", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("capitalize", Null()).isNull());
        }

        @Test
        void alreadyCapitalized() {
            assertEquals("Hello", invoke("capitalize", S("Hello")).asString());
        }

        @Test
        void singleChar() {
            assertEquals("H", invoke("capitalize", S("h")).asString());
        }

        @Test
        void allUpper() {
            assertEquals("Hello", invoke("capitalize", S("HELLO")).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("capitalize").isNull());
        }

        @Test
        void withSpaces() {
            assertEquals("Hello world", invoke("capitalize", S("hello world")).asString());
        }
    }

    // =========================================================================
    // titleCase
    // =========================================================================

    @Nested
    class TitleCase {
        @Test
        void basic() {
            assertEquals("Hello World", invoke("titleCase", S("hello world")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("titleCase", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("titleCase", Null()).isNull());
        }

        @Test
        void singleWord() {
            assertEquals("Hello", invoke("titleCase", S("hello")).asString());
        }

        @Test
        void alreadyTitleCase() {
            assertEquals("Hello World", invoke("titleCase", S("Hello World")).asString());
        }

        @Test
        void multipleSpaces() {
            assertEquals("Hello  World", invoke("titleCase", S("hello  world")).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("titleCase").isNull());
        }
    }

    // =========================================================================
    // contains
    // =========================================================================

    @Nested
    class Contains {
        @Test
        void found() {
            assertTrue(invoke("contains", S("hello world"), S("world")).asBool());
        }

        @Test
        void notFound() {
            assertFalse(invoke("contains", S("hello"), S("xyz")).asBool());
        }

        @Test
        void emptySubstring() {
            assertTrue(invoke("contains", S("hello"), S("")).asBool());
        }

        @Test
        void emptyString() {
            assertFalse(invoke("contains", S(""), S("a")).asBool());
        }

        @Test
        void nullInput() {
            assertFalse(invoke("contains", Null(), S("a")).asBool());
        }

        @Test
        void tooFewArgs() {
            assertFalse(invoke("contains", S("hello")).asBool());
        }

        @Test
        void caseSensitive() {
            assertFalse(invoke("contains", S("Hello"), S("hello")).asBool());
        }
    }

    // =========================================================================
    // startsWith
    // =========================================================================

    @Nested
    class StartsWith {
        @Test
        void trueCase() {
            assertTrue(invoke("startsWith", S("hello world"), S("hello")).asBool());
        }

        @Test
        void falseCase() {
            assertFalse(invoke("startsWith", S("hello world"), S("world")).asBool());
        }

        @Test
        void emptyPrefix() {
            assertTrue(invoke("startsWith", S("hello"), S("")).asBool());
        }

        @Test
        void nullInput() {
            assertFalse(invoke("startsWith", Null(), S("a")).asBool());
        }

        @Test
        void exactMatch() {
            assertTrue(invoke("startsWith", S("hello"), S("hello")).asBool());
        }

        @Test
        void tooFewArgs() {
            assertFalse(invoke("startsWith", S("hello")).asBool());
        }
    }

    // =========================================================================
    // endsWith
    // =========================================================================

    @Nested
    class EndsWith {
        @Test
        void trueCase() {
            assertTrue(invoke("endsWith", S("hello world"), S("world")).asBool());
        }

        @Test
        void falseCase() {
            assertFalse(invoke("endsWith", S("hello world"), S("hello")).asBool());
        }

        @Test
        void emptySuffix() {
            assertTrue(invoke("endsWith", S("hello"), S("")).asBool());
        }

        @Test
        void nullInput() {
            assertFalse(invoke("endsWith", Null(), S("a")).asBool());
        }

        @Test
        void exactMatch() {
            assertTrue(invoke("endsWith", S("hello"), S("hello")).asBool());
        }
    }

    // =========================================================================
    // replace
    // =========================================================================

    @Nested
    class Replace {
        @Test
        void basic() {
            assertEquals("hello rust", invoke("replace", S("hello world"), S("world"), S("rust")).asString());
        }

        @Test
        void noMatch() {
            assertEquals("hello", invoke("replace", S("hello"), S("xyz"), S("abc")).asString());
        }

        @Test
        void multiple() {
            assertEquals("bbb", invoke("replace", S("aaa"), S("a"), S("b")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("replace", Null(), S("a"), S("b")).isNull());
        }

        @Test
        void tooFewArgs() {
            var result = invoke("replace", S("hello"), S("h"));
            assertEquals("hello", result.asString());
        }

        @Test
        void emptySearch() {
            // Java and .NET both handle empty search by inserting between chars
            var result = invoke("replace", S("hello"), S(""), S("x"));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // replaceRegex
    // =========================================================================

    @Nested
    class ReplaceRegex {
        @Test
        void basic() {
            assertEquals("hello world", invoke("replaceRegex", S("hello   world"), S("\\s+"), S(" ")).asString());
        }

        @Test
        void digitsRemoved() {
            assertEquals("abc", invoke("replaceRegex", S("a1b2c3"), S("\\d"), S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("replaceRegex", Null(), S("\\d"), S("")).isNull());
        }

        @Test
        void invalidPattern() {
            // Invalid regex yields null
            var result = invoke("replaceRegex", S("hello"), S("[invalid"), S("x"));
            assertTrue(result.isNull());
        }
    }

    // =========================================================================
    // padLeft
    // =========================================================================

    @Nested
    class PadLeft {
        @Test
        void basic() {
            assertEquals("00042", invoke("padLeft", S("42"), I(5), S("0")).asString());
        }

        @Test
        void defaultChar() {
            assertEquals("   42", invoke("padLeft", S("42"), I(5)).asString());
        }

        @Test
        void alreadyLong() {
            assertEquals("hello", invoke("padLeft", S("hello"), I(3), S("0")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("padLeft", Null(), I(5)).isNull());
        }

        @Test
        void exactWidth() {
            assertEquals("hi", invoke("padLeft", S("hi"), I(2)).asString());
        }
    }

    // =========================================================================
    // padRight
    // =========================================================================

    @Nested
    class PadRight {
        @Test
        void basic() {
            assertEquals("hi...", invoke("padRight", S("hi"), I(5), S(".")).asString());
        }

        @Test
        void defaultChar() {
            assertEquals("hi   ", invoke("padRight", S("hi"), I(5)).asString());
        }

        @Test
        void alreadyLong() {
            assertEquals("hello", invoke("padRight", S("hello"), I(3), S(".")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("padRight", Null(), I(5)).isNull());
        }
    }

    // =========================================================================
    // pad (right)
    // =========================================================================

    @Nested
    class Pad {
        @Test
        void right() {
            assertEquals("hi****", invoke("pad", S("hi"), I(6), S("*")).asString());
        }

        @Test
        void oddWidth() {
            var result = invoke("pad", S("hi"), I(5), S("*")).asString();
            assertEquals("hi***", result);
        }

        @Test
        void alreadyLong() {
            assertEquals("hello", invoke("pad", S("hello"), I(3), S("*")).asString());
        }

        @Test
        void nullInput() {
            assertEquals("*****", invoke("pad", Null(), I(5), S("*")).asString());
        }
    }

    // =========================================================================
    // truncate
    // =========================================================================

    @Nested
    class Truncate {
        @Test
        void basic() {
            assertEquals("hello", invoke("truncate", S("hello world"), I(5)).asString());
        }

        @Test
        void withEllipsis() {
            assertEquals("he...", invoke("truncate", S("hello world"), I(5), S("...")).asString());
        }

        @Test
        void shortEnough() {
            assertEquals("hi", invoke("truncate", S("hi"), I(10)).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("truncate", Null(), I(5)).isNull());
        }

        @Test
        void zeroLength() {
            assertEquals("", invoke("truncate", S("hello"), I(0)).asString());
        }
    }

    // =========================================================================
    // split
    // =========================================================================

    @Nested
    class Split {
        @Test
        void basic() {
            var result = invoke("split", S("a,b,c"), S(","));
            var arr = result.asArray();
            assertEquals(3, arr.size());
            assertEquals("a", arr.get(0).asString());
            assertEquals("b", arr.get(1).asString());
            assertEquals("c", arr.get(2).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("split", Null(), S(",")).isNull());
        }

        @Test
        void emptyDelimiter() {
            var result = invoke("split", S("abc"), S(""));
            var arr = result.asArray();
            assertEquals(3, arr.size());
            assertEquals("a", arr.get(0).asString());
        }

        @Test
        void noMatch() {
            var result = invoke("split", S("hello"), S(","));
            var arr = result.asArray();
            assertEquals(1, arr.size());
            assertEquals("hello", arr.get(0).asString());
        }

        @Test
        void tooFewArgs() {
            assertTrue(invoke("split", S("hello")).isNull());
        }
    }

    // =========================================================================
    // join
    // =========================================================================

    @Nested
    class Join {
        @Test
        void basic() {
            assertEquals("a, b, c", invoke("join", Arr(S("a"), S("b"), S("c")), S(", ")).asString());
        }

        @Test
        void emptyDelimiter() {
            assertEquals("abc", invoke("join", Arr(S("a"), S("b"), S("c")), S("")).asString());
        }

        @Test
        void singleItem() {
            assertEquals("hello", invoke("join", Arr(S("hello")), S(",")).asString());
        }

        @Test
        void emptyArray() {
            assertEquals("", invoke("join", Arr(), S(",")).asString());
        }

        @Test
        void tooFewArgs() {
            assertTrue(invoke("join", Arr(S("a"))).isNull());
        }
    }

    // =========================================================================
    // mask
    // =========================================================================

    @Nested
    class Mask {
        @Test
        void basic() {
            assertEquals("123-456-7890", invoke("mask", S("1234567890"), S("###-###-####")).asString());
        }

        @Test
        void customChar() {
            assertEquals("(123) 456-7890", invoke("mask", S("1234567890"), S("(###) ###-####")).asString());
        }

        @Test
        void shortString() {
            // Input shorter than pattern -- only processes as many input chars as available
            var result = invoke("mask", S("abc"), S("##-##")).asString();
            assertEquals("ab-c", result);
        }

        @Test
        void nullInput() {
            assertTrue(invoke("mask", Null(), S("###")).isNull());
        }

        @Test
        void defaultShowLast() {
            // mask requires 2 args (value, pattern) -- 1 arg returns null
            var result = invoke("mask", S("123456789"));
            assertTrue(result.isNull());
        }
    }

    // =========================================================================
    // reverseString
    // =========================================================================

    @Nested
    class ReverseString {
        @Test
        void basic() {
            assertEquals("olleh", invoke("reverseString", S("hello")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("reverseString", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("reverseString", Null()).isNull());
        }

        @Test
        void singleChar() {
            assertEquals("a", invoke("reverseString", S("a")).asString());
        }

        @Test
        void palindrome() {
            assertEquals("racecar", invoke("reverseString", S("racecar")).asString());
        }
    }

    // =========================================================================
    // repeat
    // =========================================================================

    @Nested
    class Repeat {
        @Test
        void basic() {
            assertEquals("ababab", invoke("repeat", S("ab"), I(3)).asString());
        }

        @Test
        void zero() {
            assertEquals("", invoke("repeat", S("ab"), I(0)).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("repeat", Null(), I(3)).isNull());
        }

        @Test
        void one() {
            assertEquals("ab", invoke("repeat", S("ab"), I(1)).asString());
        }

        @Test
        void negativeCount() {
            assertTrue(invoke("repeat", S("ab"), I(-1)).isNull());
        }
    }

    // =========================================================================
    // substring
    // =========================================================================

    @Nested
    class Substring {
        @Test
        void withLength() {
            assertEquals("ell", invoke("substring", S("hello"), I(1), I(3)).asString());
        }

        @Test
        void noLength() {
            assertEquals("llo", invoke("substring", S("hello"), I(2)).asString());
        }

        @Test
        void startBeyond() {
            assertEquals("", invoke("substring", S("hello"), I(10)).asString());
        }

        @Test
        void negativeStart() {
            assertEquals("hello", invoke("substring", S("hello"), I(-1)).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("substring", Null(), I(0), I(3)).isNull());
        }

        @Test
        void zeroLength() {
            assertEquals("", invoke("substring", S("hello"), I(0), I(0)).asString());
        }

        @Test
        void tooFewArgs() {
            assertTrue(invoke("substring", S("hello")).isNull());
        }
    }

    // =========================================================================
    // length
    // =========================================================================

    @Nested
    class Length {
        @Test
        void string() {
            assertEquals(5L, invoke("length", S("hello")).asInt64());
        }

        @Test
        void emptyString() {
            assertEquals(0L, invoke("length", S("")).asInt64());
        }

        @Test
        void array() {
            assertEquals(3L, invoke("length", Arr(I(1), I(2), I(3))).asInt64());
        }

        @Test
        void nullInput() {
            assertEquals(0L, invoke("length", Null()).asInt64());
        }

        @Test
        void noArgs() {
            assertEquals(0L, invoke("length").asInt64());
        }

        @Test
        @SuppressWarnings("unchecked")
        void object() {
            assertEquals(2L, invoke("length", Obj(kv("a", I(1)), kv("b", I(2)))).asInt64());
        }
    }

    // =========================================================================
    // camelCase
    // =========================================================================

    @Nested
    class CamelCase {
        @Test
        void basic() {
            assertEquals("helloWorld", invoke("camelCase", S("hello world")).asString());
        }

        @Test
        void fromSnake() {
            assertEquals("helloWorld", invoke("camelCase", S("hello_world")).asString());
        }

        @Test
        void fromKebab() {
            assertEquals("helloWorld", invoke("camelCase", S("hello-world")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("camelCase", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("camelCase", S("")).asString());
        }

        @Test
        void singleWord() {
            assertEquals("hello", invoke("camelCase", S("hello")).asString());
        }

        @Test
        void threeWords() {
            assertEquals("theQuickBrown", invoke("camelCase", S("the quick brown")).asString());
        }
    }

    // =========================================================================
    // snakeCase
    // =========================================================================

    @Nested
    class SnakeCase {
        @Test
        void fromCamel() {
            assertEquals("hello_world", invoke("snakeCase", S("helloWorld")).asString());
        }

        @Test
        void fromSpaces() {
            assertEquals("hello_world", invoke("snakeCase", S("hello world")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("snakeCase", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("snakeCase", S("")).asString());
        }

        @Test
        void fromKebab() {
            assertEquals("hello_world", invoke("snakeCase", S("hello-world")).asString());
        }

        @Test
        void alreadySnake() {
            assertEquals("hello_world", invoke("snakeCase", S("hello_world")).asString());
        }
    }

    // =========================================================================
    // kebabCase
    // =========================================================================

    @Nested
    class KebabCase {
        @Test
        void fromCamel() {
            assertEquals("hello-world", invoke("kebabCase", S("helloWorld")).asString());
        }

        @Test
        void fromSpaces() {
            assertEquals("hello-world", invoke("kebabCase", S("hello world")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("kebabCase", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("kebabCase", S("")).asString());
        }

        @Test
        void fromSnake() {
            assertEquals("hello-world", invoke("kebabCase", S("hello_world")).asString());
        }
    }

    // =========================================================================
    // pascalCase
    // =========================================================================

    @Nested
    class PascalCase {
        @Test
        void basic() {
            assertEquals("HelloWorld", invoke("pascalCase", S("hello world")).asString());
        }

        @Test
        void fromSnake() {
            assertEquals("HelloWorld", invoke("pascalCase", S("hello_world")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("pascalCase", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("pascalCase", S("")).asString());
        }

        @Test
        void singleWord() {
            assertEquals("Hello", invoke("pascalCase", S("hello")).asString());
        }
    }

    // =========================================================================
    // slugify
    // =========================================================================

    @Nested
    class Slugify {
        @Test
        void basic() {
            assertEquals("hello-world-test", invoke("slugify", S("Hello World! Test")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("slugify", Null()).isNull());
        }

        @Test
        void alreadySlug() {
            assertEquals("hello-world", invoke("slugify", S("hello-world")).asString());
        }

        @Test
        void specialChars() {
            assertEquals("hello-world", invoke("slugify", S("hello & world")).asString());
        }

        @Test
        void accents() {
            assertEquals("caf-nave", invoke("slugify", S("caf\u00e9 na\u00efve")).asString());
        }

        @Test
        void multipleSpaces() {
            assertEquals("hello-world", invoke("slugify", S("hello   world")).asString());
        }
    }

    // =========================================================================
    // match
    // =========================================================================

    @Nested
    class Match {
        @Test
        void found() {
            assertTrue(invoke("match", S("abc123def"), S("\\d+")).asBool());
        }

        @Test
        void notFound() {
            assertFalse(invoke("match", S("abc"), S("\\d+")).asBool());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("match", Null(), S("\\d+")).isNull());
        }

        @Test
        void tooFewArgs() {
            assertTrue(invoke("match", S("hello")).isNull());
        }

        @Test
        void fullMatch() {
            assertTrue(invoke("match", S("hello"), S("^hello$")).asBool());
        }
    }

    // =========================================================================
    // extract
    // =========================================================================

    @Nested
    class Extract {
        @Test
        void withGroupIndex() {
            assertEquals("01", invoke("extract", S("2024-01-15"), S("(\\d{4})-(\\d{2})-(\\d{2})"), I(2)).asString());
        }

        @Test
        void wholeMatchDefault() {
            assertEquals("123", invoke("extract", S("abc123def"), S("\\d+")).asString());
        }

        @Test
        void notFound() {
            assertTrue(invoke("extract", S("abc"), S("\\d+")).isNull());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("extract", Null(), S("\\d+")).isNull());
        }
    }

    // =========================================================================
    // normalizeSpace
    // =========================================================================

    @Nested
    class NormalizeSpace {
        @Test
        void basic() {
            assertEquals("hello world", invoke("normalizeSpace", S("  hello   world  ")).asString());
        }

        @Test
        void tabs() {
            assertEquals("hello world", invoke("normalizeSpace", S("\thello\t\tworld\t")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("normalizeSpace", Null()).isNull());
        }

        @Test
        void noExtraSpace() {
            assertEquals("hello world", invoke("normalizeSpace", S("hello world")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("normalizeSpace", S("")).asString());
        }

        @Test
        void onlySpaces() {
            assertEquals("", invoke("normalizeSpace", S("   ")).asString());
        }
    }

    // =========================================================================
    // leftOf
    // =========================================================================

    @Nested
    class LeftOf {
        @Test
        void basic() {
            assertEquals("hello", invoke("leftOf", S("hello@world.com"), S("@")).asString());
        }

        @Test
        void notFound() {
            assertEquals("hello", invoke("leftOf", S("hello"), S("@")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("leftOf", Null(), S("@")).isNull());
        }

        @Test
        void atStart() {
            assertEquals("", invoke("leftOf", S("@hello"), S("@")).asString());
        }

        @Test
        void multiOccurrence() {
            assertEquals("a", invoke("leftOf", S("a@b@c"), S("@")).asString());
        }
    }

    // =========================================================================
    // rightOf
    // =========================================================================

    @Nested
    class RightOf {
        @Test
        void basic() {
            assertEquals("world.com", invoke("rightOf", S("hello@world.com"), S("@")).asString());
        }

        @Test
        void notFound() {
            assertEquals("", invoke("rightOf", S("hello"), S("@")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("rightOf", Null(), S("@")).isNull());
        }

        @Test
        void atEnd() {
            assertEquals("", invoke("rightOf", S("hello@"), S("@")).asString());
        }

        @Test
        void multiOccurrence() {
            assertEquals("b@c", invoke("rightOf", S("a@b@c"), S("@")).asString());
        }
    }

    // =========================================================================
    // wrap
    // =========================================================================

    @Nested
    class Wrap {
        @Test
        void wordWraps() {
            assertEquals("the quick\nbrown fox", invoke("wrap", S("the quick brown fox"), I(10)).asString());
        }

        @Test
        void shorterThanWidth() {
            assertEquals("hello", invoke("wrap", S("hello"), I(10)).asString());
        }

        @Test
        void nullInput() {
            assertEquals("", invoke("wrap", Null(), I(10)).asString());
        }

        @Test
        void zeroWidth() {
            assertTrue(invoke("wrap", S("abc"), I(0)).isNull());
        }
    }

    // =========================================================================
    // center
    // =========================================================================

    @Nested
    class Center {
        @Test
        void basic() {
            assertEquals("--hi--", invoke("center", S("hi"), I(6), S("-")).asString());
        }

        @Test
        void defaultPad() {
            var result = invoke("center", S("hi"), I(6)).asString();
            assertEquals(6, result.length());
            assertTrue(result.contains("hi"));
        }

        @Test
        void alreadyLong() {
            assertEquals("hello", invoke("center", S("hello"), I(3), S("-")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("center", Null(), I(6)).isNull());
        }

        @Test
        void oddWidth() {
            var result = invoke("center", S("hi"), I(5), S("-")).asString();
            assertEquals(5, result.length());
        }
    }

    // =========================================================================
    // matches (regex boolean)
    // =========================================================================

    @Nested
    class Matches {
        @Test
        void trueCase() {
            assertTrue(invoke("matches", S("abc123"), S("\\d+")).asBool());
        }

        @Test
        void falseCase() {
            assertFalse(invoke("matches", S("abc"), S("\\d+")).asBool());
        }

        @Test
        void nullInput() {
            assertFalse(invoke("matches", Null(), S("\\d+")).asBool());
        }

        @Test
        void tooFewArgs() {
            assertFalse(invoke("matches", S("hello")).asBool());
        }

        @Test
        void invalidRegex() {
            assertFalse(invoke("matches", S("hello"), S("[invalid")).asBool());
        }

        @Test
        void fullPattern() {
            assertTrue(invoke("matches", S("hello"), S("^hello$")).asBool());
        }
    }

    // =========================================================================
    // stripAccents
    // =========================================================================

    @Nested
    class StripAccents {
        @Test
        void basic() {
            assertEquals("cafe naive n", invoke("stripAccents", S("caf\u00e9 na\u00efve \u00f1")).asString());
        }

        @Test
        void noAccents() {
            assertEquals("hello", invoke("stripAccents", S("hello")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("stripAccents", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("stripAccents", S("")).asString());
        }

        @Test
        void umlauts() {
            assertEquals("uber", invoke("stripAccents", S("\u00fcber")).asString());
        }
    }

    // =========================================================================
    // clean
    // =========================================================================

    @Nested
    class Clean {
        @Test
        void removesControlChars() {
            assertEquals("helloworld", invoke("clean", S("hello\u0000\u0001world\n")).asString());
        }

        @Test
        void collapsesWhitespace() {
            assertEquals("hello world", invoke("clean", S("  hello   world  ")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("clean", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("clean", S("")).asString());
        }

        @Test
        void trimsAndCollapsesTabs() {
            assertEquals("hello", invoke("clean", S("\thello\t")).asString());
        }
    }

    // =========================================================================
    // wordCount
    // =========================================================================

    @Nested
    class WordCount {
        @Test
        void basic() {
            assertEquals(3L, invoke("wordCount", S("hello beautiful world")).asInt64());
        }

        @Test
        void empty() {
            assertEquals(0L, invoke("wordCount", S("")).asInt64());
        }

        @Test
        void nullInput() {
            assertEquals(0L, invoke("wordCount", Null()).asInt64());
        }

        @Test
        void singleWord() {
            assertEquals(1L, invoke("wordCount", S("hello")).asInt64());
        }

        @Test
        void extraSpaces() {
            assertEquals(2L, invoke("wordCount", S("  hello   world  ")).asInt64());
        }

        @Test
        void noArgs() {
            assertEquals(0L, invoke("wordCount").asInt64());
        }
    }

    // =========================================================================
    // tokenize
    // =========================================================================

    @Nested
    class Tokenize {
        @Test
        void basic() {
            var result = invoke("tokenize", S("hello beautiful world"));
            var arr = result.asArray();
            assertEquals(3, arr.size());
            assertEquals("hello", arr.get(0).asString());
            assertEquals("beautiful", arr.get(1).asString());
            assertEquals("world", arr.get(2).asString());
        }

        @Test
        void empty() {
            var result = invoke("tokenize", S(""));
            assertTrue(result.asArray().isEmpty());
        }

        @Test
        void nullInput() {
            var result = invoke("tokenize", Null());
            assertTrue(result.asArray().isEmpty());
        }

        @Test
        void extraSpaces() {
            var result = invoke("tokenize", S("  hello   world  "));
            var arr = result.asArray();
            assertEquals(2, arr.size());
        }
    }

    // =========================================================================
    // levenshtein
    // =========================================================================

    @Nested
    class Levenshtein {
        @Test
        void identical() {
            assertEquals(0L, invoke("levenshtein", S("hello"), S("hello")).asInt64());
        }

        @Test
        void oneEdit() {
            assertEquals(1L, invoke("levenshtein", S("hello"), S("hallo")).asInt64());
        }

        @Test
        void completelyDifferent() {
            assertEquals(3L, invoke("levenshtein", S("abc"), S("xyz")).asInt64());
        }

        @Test
        void emptyFirst() {
            assertEquals(5L, invoke("levenshtein", S(""), S("hello")).asInt64());
        }

        @Test
        void emptySecond() {
            assertEquals(5L, invoke("levenshtein", S("hello"), S("")).asInt64());
        }

        @Test
        void bothEmpty() {
            assertEquals(0L, invoke("levenshtein", S(""), S("")).asInt64());
        }

        @Test
        void nullInput() {
            assertEquals(0L, invoke("levenshtein", Null(), S("hello")).asInt64());
        }

        @Test
        void insertion() {
            assertEquals(1L, invoke("levenshtein", S("hello"), S("helloo")).asInt64());
        }

        @Test
        void deletion() {
            assertEquals(1L, invoke("levenshtein", S("hello"), S("hell")).asInt64());
        }
    }

    // =========================================================================
    // soundex
    // =========================================================================

    @Nested
    class Soundex {
        @Test
        void robert() {
            assertEquals("R163", invoke("soundex", S("Robert")).asString());
        }

        @Test
        void rupert() {
            assertEquals("R163", invoke("soundex", S("Rupert")).asString());
        }

        @Test
        void ashcraft() {
            assertEquals("A226", invoke("soundex", S("Ashcraft")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("soundex", Null()).isNull());
        }

        @Test
        void empty() {
            assertEquals("", invoke("soundex", S("")).asString());
        }

        @Test
        void singleChar() {
            assertEquals("A000", invoke("soundex", S("A")).asString());
        }
    }

    // =========================================================================
    // base64Encode / base64Decode
    // =========================================================================

    @Nested
    class Base64Encode {
        @Test
        void basic() {
            assertEquals("SGVsbG8=", invoke("base64Encode", S("Hello")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("base64Encode", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("base64Encode", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("base64Encode").isNull());
        }
    }

    @Nested
    class Base64Decode {
        @Test
        void basic() {
            assertEquals("Hello", invoke("base64Decode", S("SGVsbG8=")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("base64Decode", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("base64Decode", Null()).isNull());
        }

        @Test
        void invalidInput() {
            assertTrue(invoke("base64Decode", S("not-valid-base64!!!")).isNull());
        }
    }

    @Nested
    class Base64Roundtrip {
        @Test
        void roundtrip() {
            var original = "Hello, World! \u00e9";
            var encoded = invoke("base64Encode", S(original));
            var decoded = invoke("base64Decode", encoded);
            assertEquals(original, decoded.asString());
        }
    }

    // =========================================================================
    // urlEncode / urlDecode
    // =========================================================================

    @Nested
    class UrlEncode {
        @Test
        void basic() {
            assertEquals("hello%20world%26foo%3Dbar", invoke("urlEncode", S("hello world&foo=bar")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("urlEncode", Null()).isNull());
        }

        @Test
        void noSpecialChars() {
            assertEquals("hello", invoke("urlEncode", S("hello")).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("urlEncode").isNull());
        }
    }

    @Nested
    class UrlDecode {
        @Test
        void basic() {
            assertEquals("hello world&foo=bar", invoke("urlDecode", S("hello%20world%26foo%3Dbar")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("urlDecode", Null()).isNull());
        }
    }

    @Nested
    class UrlRoundtrip {
        @Test
        void roundtrip() {
            var original = "name=John Doe&age=30";
            var encoded = invoke("urlEncode", S(original));
            var decoded = invoke("urlDecode", encoded);
            assertEquals(original, decoded.asString());
        }
    }

    // =========================================================================
    // hexEncode / hexDecode
    // =========================================================================

    @Nested
    class HexEncode {
        @Test
        void basic() {
            assertEquals("4869", invoke("hexEncode", S("Hi")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("hexEncode", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("hexEncode").isNull());
        }
    }

    @Nested
    class HexDecode {
        @Test
        void basic() {
            assertEquals("Hi", invoke("hexDecode", S("4869")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("hexDecode", Null()).isNull());
        }

        @Test
        void invalidHex() {
            assertTrue(invoke("hexDecode", S("ZZZZ")).isNull());
        }
    }

    @Nested
    class HexRoundtrip {
        @Test
        void roundtrip() {
            var original = "Hello, World!";
            var encoded = invoke("hexEncode", S(original));
            var decoded = invoke("hexDecode", encoded);
            assertEquals(original, decoded.asString());
        }
    }

    // =========================================================================
    // jsonEncode / jsonDecode
    // =========================================================================

    @Nested
    class JsonEncode {
        @Test
        @SuppressWarnings("unchecked")
        void object() {
            var result = invoke("jsonEncode", Obj(kv("a", I(1)), kv("b", S("two"))));
            assertEquals("{\"a\":1,\"b\":\"two\"}", result.asString());
        }

        @Test
        void nullInput() {
            assertEquals("null", invoke("jsonEncode", Null()).asString());
        }

        @Test
        void array() {
            var result = invoke("jsonEncode", Arr(I(1), I(2), I(3)));
            assertEquals("[1,2,3]", result.asString());
        }

        @Test
        void string() {
            assertEquals("hello", invoke("jsonEncode", S("hello")).asString());
        }

        @Test
        void integer() {
            assertEquals("42", invoke("jsonEncode", I(42)).asString());
        }

        @Test
        void bool() {
            assertEquals("true", invoke("jsonEncode", B(true)).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("jsonEncode").isNull());
        }
    }

    @Nested
    class JsonDecode {
        @Test
        void object() {
            var result = invoke("jsonDecode", S("{\"x\":42}"));
            assertEquals(42L, result.get("x").asInt64());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("jsonDecode", Null()).isNull());
        }

        @Test
        void invalidJson() {
            // A bare string round-trips through JSON-string unescaping.
            assertEquals("not json", invoke("jsonDecode", S("not json")).asString());
            // An invalid escape sequence yields null.
            assertTrue(invoke("jsonDecode", S("bad\\xescape")).isNull());
        }
    }

    // =========================================================================
    // sha256
    // =========================================================================

    @Nested
    class Sha256 {
        @Test
        void basic() {
            var result = invoke("sha256", S("hello")).asString();
            assertEquals(64, result.length()); // SHA-256 produces 64 hex chars
            assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result);
        }

        @Test
        void nullInput() {
            assertTrue(invoke("sha256", Null()).isNull());
        }

        @Test
        void empty() {
            var result = invoke("sha256", S("")).asString();
            assertEquals(64, result.length());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("sha256").isNull());
        }
    }

    // =========================================================================
    // sha1
    // =========================================================================

    @Nested
    class Sha1 {
        @Test
        void basic() {
            var result = invoke("sha1", S("hello")).asString();
            assertEquals(40, result.length()); // SHA-1 produces 40 hex chars
        }

        @Test
        void nullInput() {
            assertTrue(invoke("sha1", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("sha1").isNull());
        }
    }

    // =========================================================================
    // sha512
    // =========================================================================

    @Nested
    class Sha512 {
        @Test
        void basic() {
            var result = invoke("sha512", S("hello")).asString();
            assertEquals(128, result.length()); // SHA-512 produces 128 hex chars
        }

        @Test
        void nullInput() {
            assertTrue(invoke("sha512", Null()).isNull());
        }
    }

    // =========================================================================
    // md5
    // =========================================================================

    @Nested
    class Md5 {
        @Test
        void basic() {
            var result = invoke("md5", S("hello")).asString();
            assertEquals(32, result.length()); // MD5 produces 32 hex chars
            assertEquals("5d41402abc4b2a76b9719d911017c592", result);
        }

        @Test
        void nullInput() {
            assertTrue(invoke("md5", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("md5").isNull());
        }
    }

    // =========================================================================
    // crc32
    // =========================================================================

    @Nested
    class Crc32 {
        @Test
        void basic() {
            var result = invoke("crc32", S("hello")).asString();
            assertEquals(8, result.length()); // CRC32 produces 8 hex chars
        }

        @Test
        void nullInput() {
            assertTrue(invoke("crc32", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("crc32").isNull());
        }
    }

    // =========================================================================
    // jsonPath
    // =========================================================================

    @Nested
    class JsonPath {
        @Test
        @SuppressWarnings("unchecked")
        void basicLookup() {
            var obj = Obj(kv("user", Obj(kv("name", S("Alice")))));
            var result = invoke("jsonPath", obj, S("user.name"));
            assertEquals("Alice", result.asString());
        }

        @Test
        @SuppressWarnings("unchecked")
        void arrayIndex() {
            var obj = Obj(kv("items", Arr(S("a"), S("b"), S("c"))));
            var result = invoke("jsonPath", obj, S("items[1]"));
            assertEquals("b", result.asString());
        }

        @Test
        @SuppressWarnings("unchecked")
        void notFound() {
            var obj = Obj(kv("a", I(1)));
            var result = invoke("jsonPath", obj, S("b"));
            assertTrue(result.isNull());
        }

        @Test
        void fromJsonString() {
            var result = invoke("jsonPath", S("{\"a\":42}"), S("a"));
            assertEquals(42L, result.asInt64());
        }

        @Test
        void tooFewArgs() {
            assertTrue(invoke("jsonPath", S("{}")).isNull());
        }

        @Test
        @SuppressWarnings("unchecked")
        void nullPath() {
            assertTrue(invoke("jsonPath", Obj(kv("a", I(1))), Null()).isNull());
        }
    }
}
