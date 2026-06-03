package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended string verb tests ported from .NET SDK StringVerbExtendedTests.
 * Adapted from Rust SDK extended_tests and extended_tests_2 modules.
 * Tests verb implementations via TransformEngine.invokeVerb().
 */
class StringVerbExtendedTest {

    // ── Helpers ──

    private static DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }
    private static DynValue Obj(Map.Entry<String, DynValue>... pairs) {
        return DynValue.ofObject(List.of(pairs));
    }
    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }

    // =========================================================================
    // titleCase extended edge cases
    // =========================================================================

    @Nested class TitleCaseTests {

        @Test void titleCase_allUpper() {
            var result = invoke("titleCase", S("HELLO WORLD")).asString();
            assertNotNull(result);
            assertTrue(result.startsWith("H"));
        }

        @Test void titleCase_withNumbers() {
            var result = invoke("titleCase", S("hello 42 world")).asString();
            assertNotNull(result);
            assertTrue(result.startsWith("H"));
        }

        @Test void titleCase_unicode() {
            var result = invoke("titleCase", S("\u00FCber cool")).asString();
            assertNotNull(result);
            assertTrue(result.toLowerCase().contains("cool"));
        }

        @Test void titleCase_integerCoerces() {
            var result = invoke("titleCase", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // contains extended edge cases
    // =========================================================================

    @Nested class ContainsTests {

        @Test void contains_bothEmpty() {
            assertEquals(true, invoke("contains", S(""), S("")).asBool());
        }

        @Test void contains_unicode() {
            assertEquals(true, invoke("contains", S("caf\u00E9"), S("f\u00E9")).asBool());
        }
    }

    // =========================================================================
    // startsWith extended edge cases
    // =========================================================================

    @Nested class StartsWithTests {

        @Test void startsWith_longerPrefix() {
            assertEquals(false, invoke("startsWith", S("hi"), S("hello")).asBool());
        }

        @Test void startsWith_tooFewArgs() {
            var result = invoke("startsWith", S("hello"));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // endsWith extended edge cases
    // =========================================================================

    @Nested class EndsWithTests {

        @Test void endsWith_fullMatch() {
            assertEquals(true, invoke("endsWith", S("hello"), S("hello")).asBool());
        }

        @Test void endsWith_tooFewArgs() {
            var result = invoke("endsWith", S("hello"));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // replaceRegex extended edge cases
    // =========================================================================

    @Nested class ReplaceRegexTests {

        @Test void replaceRegex_noMatch() {
            assertEquals("hello", invoke("replaceRegex", S("hello"), S("xyz"), S("abc")).asString());
        }

        @Test void replaceRegex_emptyReplacement() {
            assertEquals("helloworld", invoke("replaceRegex", S("hello world"), S(" "), S("")).asString());
        }

        @Test void replaceRegex_multipleOccurrences() {
            assertEquals("bbbbbb", invoke("replaceRegex", S("aaa"), S("a"), S("bb")).asString());
        }

        @Test void replaceRegex_allOccurrences() {
            assertEquals("bbb", invoke("replaceRegex", S("aaa"), S("a"), S("b")).asString());
        }

        @Test void replaceRegex_tooFewArgs() {
            var result = invoke("replaceRegex", S("hello"), S("x"));
            assertNotNull(result);
        }

        @Test void replaceRegex_emptyReplacementRemovesWorld() {
            assertEquals("hello", invoke("replaceRegex", S("hello world"), S(" world"), S("")).asString());
        }
    }

    // =========================================================================
    // padLeft extended edge cases
    // =========================================================================

    @Nested class PadLeftTests {

        @Test void padLeft_emptyString() {
            assertEquals("xxx", invoke("padLeft", S(""), I(3), S("x")).asString());
        }

        @Test void padLeft_withSpace() {
            assertEquals("   x", invoke("padLeft", S("x"), I(4), S(" ")).asString());
        }

        @Test void padLeft_tooFewArgs_defaultsPadChar() {
            var result = invoke("padLeft", S("hi"), I(5));
            assertNotNull(result);
            assertEquals(5, result.asString().length());
        }
    }

    // =========================================================================
    // padRight extended edge cases
    // =========================================================================

    @Nested class PadRightTests {

        @Test void padRight_emptyString() {
            assertEquals("----", invoke("padRight", S(""), I(4), S("-")).asString());
        }

        @Test void padRight_spaceChar() {
            assertEquals("hi   ", invoke("padRight", S("hi"), I(5), S(" ")).asString());
        }

        @Test void padRight_singleChar() {
            assertEquals("x....", invoke("padRight", S("x"), I(5), S(".")).asString());
        }

        @Test void padRight_tooFewArgs_defaultsPadChar() {
            var result = invoke("padRight", S("hi"), I(5));
            assertNotNull(result);
            assertEquals(5, result.asString().length());
        }
    }

    // =========================================================================
    // pad (center) extended edge cases
    // =========================================================================

    @Nested class PadCenterTests {

        @Test void pad_alreadyWide() {
            assertEquals("hello", invoke("pad", S("hello"), I(3), S("*")).asString());
        }

        @Test void pad_emptyString() {
            assertEquals("xxxx", invoke("pad", S(""), I(4), S("x")).asString());
        }

        @Test void pad_rightPadding() {
            assertEquals("hi----", invoke("pad", S("hi"), I(6), S("-")).asString());
        }

        @Test void pad_tooFewArgs_returnsNull() {
            assertTrue(invoke("pad", S("hi"), I(6)).isNull());
        }
    }

    // =========================================================================
    // truncate extended edge cases
    // =========================================================================

    @Nested class TruncateTests {

        @Test void truncate_shorterThanLimit() {
            assertEquals("hi", invoke("truncate", S("hi"), I(10)).asString());
        }

        @Test void truncate_exactLength() {
            assertEquals("hello", invoke("truncate", S("hello"), I(5)).asString());
        }

        @Test void truncate_emptyString() {
            assertEquals("", invoke("truncate", S(""), I(5)).asString());
        }

        @Test void truncate_toOne() {
            assertEquals("h", invoke("truncate", S("hello"), I(1)).asString());
        }

        @Test void truncate_tooFewArgs() {
            var result = invoke("truncate", S("hello"));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // split extended edge cases
    // =========================================================================

    @Nested class SplitTests {

        @Test void split_emptyString() {
            var result = invoke("split", S(""), S(","));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(1, arr.size());
            assertEquals("", arr.get(0).asString());
        }

        @Test void split_multiCharDelimiter() {
            var result = invoke("split", S("a::b::c"), S("::"));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(3, arr.size());
            assertEquals("a", arr.get(0).asString());
            assertEquals("b", arr.get(1).asString());
            assertEquals("c", arr.get(2).asString());
        }

        @Test void split_multipleDelimiters() {
            var result = invoke("split", S("a,b,c,d"), S(","));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(4, arr.size());
        }

        @Test void split_tooFewArgs() {
            assertTrue(invoke("split", S("hello")).isNull());
        }
    }

    // =========================================================================
    // join extended edge cases
    // =========================================================================

    @Nested class JoinTests {

        @Test void join_withIntegers() {
            assertEquals("1-2-3", invoke("join", Arr(I(1), I(2), I(3)), S("-")).asString());
        }

        @Test void join_multiCharDelimiter() {
            assertEquals("a -- b", invoke("join", Arr(S("a"), S("b")), S(" -- ")).asString());
        }

        @Test void join_tooFewArgs() {
            assertTrue(invoke("join", Arr(S("a"))).isNull());
        }
    }

    // =========================================================================
    // mask extended edge cases
    // =========================================================================

    @Nested class MaskTests {

        @Test void mask_showAll() {
            assertEquals("abc", invoke("mask", S("abc"), S("***")).asString());
        }

        @Test void mask_showZero() {
            assertEquals("a-b-c", invoke("mask", S("abc"), S("*-*-*")).asString());
        }

        @Test void mask_showExactLength() {
            assertEquals("abc", invoke("mask", S("abc"), S("###")).asString());
        }

        @Test void mask_showLast4() {
            assertEquals("123-456-7890", invoke("mask", S("1234567890"), S("###-###-####")).asString());
        }

        @Test void mask_defaultShowLast() {
            // mask requires 2 args (value, pattern) - 1 arg returns null
            var result = invoke("mask", S("123456789"));
            assertTrue(result.isNull());
        }

        @Test void mask_nullPassthrough() {
            assertTrue(invoke("mask", Null(), S("###")).isNull());
        }
    }

    // =========================================================================
    // reverseString extended edge cases
    // =========================================================================

    @Nested class ReverseStringTests {

        @Test void reverseString_withSpaces() {
            assertEquals("c b a", invoke("reverseString", S("a b c")).asString());
        }

        @Test void reverseString_integerCoerces() {
            var result = invoke("reverseString", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // repeat extended edge cases
    // =========================================================================

    @Nested class RepeatTests {

        @Test void repeat_emptyString() {
            assertEquals("", invoke("repeat", S(""), I(5)).asString());
        }

        @Test void repeat_largeCount() {
            var result = invoke("repeat", S("x"), I(100)).asString();
            assertNotNull(result);
            assertEquals(100, result.length());
        }

        @Test void repeat_tooFewArgs() {
            assertTrue(invoke("repeat", S("abc")).isNull());
        }
    }

    // =========================================================================
    // camelCase extended edge cases
    // =========================================================================

    @Nested class CamelCaseTests {

        @Test void camelCase_fromPascal() {
            assertEquals("helloWorld", invoke("camelCase", S("HelloWorld")).asString());
        }

        @Test void camelCase_multipleWords() {
            assertEquals("theQuickBrownFox", invoke("camelCase", S("the quick brown fox")).asString());
        }

        @Test void camelCase_alreadyCamel() {
            var result = invoke("camelCase", S("helloWorld")).asString();
            assertNotNull(result);
            assertTrue(result.startsWith("h"));
        }

        @Test void camelCase_integerCoerces() {
            var result = invoke("camelCase", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // snakeCase extended edge cases
    // =========================================================================

    @Nested class SnakeCaseTests {

        @Test void snakeCase_singleWord() {
            assertEquals("hello", invoke("snakeCase", S("hello")).asString());
        }

        @Test void snakeCase_fromPascal() {
            assertEquals("hello_world", invoke("snakeCase", S("HelloWorld")).asString());
        }

        @Test void snakeCase_spaces() {
            assertEquals("hello_world_test", invoke("snakeCase", S("hello world test")).asString());
        }
    }

    // =========================================================================
    // kebabCase extended edge cases
    // =========================================================================

    @Nested class KebabCaseTests {

        @Test void kebabCase_singleWord() {
            assertEquals("hello", invoke("kebabCase", S("hello")).asString());
        }

        @Test void kebabCase_fromPascal() {
            assertEquals("hello-world", invoke("kebabCase", S("HelloWorld")).asString());
        }

        @Test void kebabCase_spaces() {
            assertEquals("hello-world-test", invoke("kebabCase", S("hello world test")).asString());
        }
    }

    // =========================================================================
    // pascalCase extended edge cases
    // =========================================================================

    @Nested class PascalCaseTests {

        @Test void pascalCase_fromCamel() {
            assertEquals("HelloWorld", invoke("pascalCase", S("helloWorld")).asString());
        }

        @Test void pascalCase_fromKebab() {
            assertEquals("HelloWorld", invoke("pascalCase", S("hello-world")).asString());
        }

        @Test void pascalCase_multipleWords() {
            assertEquals("TheQuickBrownFox", invoke("pascalCase", S("the quick brown fox")).asString());
        }

        @Test void pascalCase_integerCoerces() {
            var result = invoke("pascalCase", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // slugify extended edge cases
    // =========================================================================

    @Nested class SlugifyTests {

        @Test void slugify_empty() {
            assertEquals("", invoke("slugify", S("")).asString());
        }

        @Test void slugify_leadingTrailingSpecial() {
            assertEquals("hello", invoke("slugify", S("!!hello!!")).asString());
        }

        @Test void slugify_numbers() {
            assertEquals("test-123-stuff", invoke("slugify", S("Test 123 Stuff")).asString());
        }
    }

    // =========================================================================
    // match extended edge cases
    // In Java, match returns the first matched string (not bool)
    // =========================================================================

    @Nested class MatchTests {

        @Test void match_digitPattern() {
            assertTrue(invoke("match", S("abc123def"), S("\\d+")).asBool());
        }

        @Test void match_notFoundReturnsFalse() {
            assertFalse(invoke("match", S("abc"), S("\\d+")).asBool());
        }

        @Test void match_nullReturnsNull() {
            assertTrue(invoke("match", Null(), S("\\d+")).isNull());
        }

        @Test void match_tooFewArgs() {
            assertTrue(invoke("match", S("hello")).isNull());
        }

        @Test void match_fullPattern() {
            assertTrue(invoke("match", S("hello"), S("^hello$")).asBool());
        }

        @Test void match_wordBoundary() {
            assertTrue(invoke("match", S("hello world"), S("world")).asBool());
        }
    }

    // =========================================================================
    // matches extended edge cases
    // =========================================================================

    @Nested class MatchesTests {

        @Test void matches_true() {
            assertEquals(true, invoke("matches", S("hello123"), S("\\d+")).asBool());
        }

        @Test void matches_false() {
            assertEquals(false, invoke("matches", S("hello"), S("\\d+")).asBool());
        }

        @Test void matches_nullInput() {
            assertEquals(false, invoke("matches", Null(), S("\\d+")).asBool());
        }

        @Test void matches_fullPattern() {
            assertEquals(true, invoke("matches", S("hello"), S("^hello$")).asBool());
        }
    }

    // =========================================================================
    // extract extended edge cases
    // In Java, extract uses regex groups (not delimiters)
    // =========================================================================

    @Nested class ExtractTests {

        @Test void extract_groupIndex() {
            assertEquals("01", invoke("extract", S("2024-01-15"), S("(\\d{4})-(\\d{2})-(\\d{2})"), I(2)).asString());
        }

        @Test void extract_wholeMatchDefault() {
            assertEquals("123", invoke("extract", S("abc123def"), S("\\d+")).asString());
        }

        @Test void extract_notFound() {
            assertTrue(invoke("extract", S("abc"), S("\\d+")).isNull());
        }

        @Test void extract_nullInput() {
            assertTrue(invoke("extract", Null(), S("\\d+")).isNull());
        }

        @Test void extract_firstGroup() {
            assertEquals("user", invoke("extract", S("user@domain.com"), S("(.+)@(.+)"), I(1)).asString());
        }
    }

    // =========================================================================
    // normalizeSpace extended edge cases
    // =========================================================================

    @Nested class NormalizeSpaceTests {

        @Test void normalizeSpace_emptyString() {
            assertEquals("", invoke("normalizeSpace", S("")).asString());
        }

        @Test void normalizeSpace_onlyWhitespace() {
            assertEquals("", invoke("normalizeSpace", S("   ")).asString());
        }

        @Test void normalizeSpace_tabsAndNewlines() {
            assertEquals("hello world", invoke("normalizeSpace", S("hello\t\nworld")).asString());
        }

        @Test void normalizeSpace_singleWord() {
            assertEquals("hello", invoke("normalizeSpace", S("hello")).asString());
        }

        @Test void normalizeSpace_integerCoerces() {
            var result = invoke("normalizeSpace", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // leftOf extended edge cases
    // =========================================================================

    @Nested class LeftOfTests {

        @Test void leftOf_noDelimiter() {
            assertEquals("hello", invoke("leftOf", S("hello"), S("@")).asString());
        }

        @Test void leftOf_multipleDelimiters() {
            assertEquals("a", invoke("leftOf", S("a@b@c"), S("@")).asString());
        }

        @Test void leftOf_emptyString() {
            assertEquals("", invoke("leftOf", S(""), S("@")).asString());
        }

        @Test void leftOf_tooFewArgs() {
            assertTrue(invoke("leftOf", S("hello")).isNull());
        }
    }

    // =========================================================================
    // rightOf extended edge cases
    // =========================================================================

    @Nested class RightOfTests {

        @Test void rightOf_noDelimiter() {
            assertEquals("", invoke("rightOf", S("hello"), S("@")).asString());
        }

        @Test void rightOf_atEnd() {
            assertEquals("", invoke("rightOf", S("hello@"), S("@")).asString());
        }

        @Test void rightOf_multipleDelimiters() {
            assertEquals("b@c", invoke("rightOf", S("a@b@c"), S("@")).asString());
        }

        @Test void rightOf_emptyString() {
            assertEquals("", invoke("rightOf", S(""), S("@")).asString());
        }

        @Test void rightOf_tooFewArgs() {
            assertTrue(invoke("rightOf", S("hello")).isNull());
        }
    }

    // =========================================================================
    // wrap extended edge cases
    // In Java, wrap adds prefix/suffix characters (not word-wrapping)
    // =========================================================================

    @Nested class WrapTests {

        @Test void wrap_wordWrap() {
            assertEquals("the quick\nbrown fox", invoke("wrap", S("the quick brown fox"), I(10)).asString());
        }

        @Test void wrap_shorterThanWidth() {
            assertEquals("hello", invoke("wrap", S("hello"), I(20)).asString());
        }

        @Test void wrap_breaksEveryWord() {
            assertEquals("aa\nbb\ncc", invoke("wrap", S("aa bb cc"), I(2)).asString());
        }

        @Test void wrap_zeroWidthNull() {
            assertTrue(invoke("wrap", S("abc"), I(0)).isNull());
        }

        @Test void wrap_nullCoercesEmpty() {
            assertEquals("", invoke("wrap", Null(), I(10)).asString());
        }
    }

    // =========================================================================
    // center extended edge cases
    // =========================================================================

    @Nested class CenterTests {

        @Test void center_alreadyWide() {
            assertEquals("hello", invoke("center", S("hello"), I(3), S("-")).asString());
        }

        @Test void center_emptyString() {
            assertEquals("****", invoke("center", S(""), I(4), S("*")).asString());
        }

        @Test void center_exactWidth() {
            assertEquals("abcd", invoke("center", S("abcd"), I(4), S("-")).asString());
        }

        @Test void center_defaultPadChar() {
            var result = invoke("center", S("hi"), I(6));
            assertNotNull(result);
            assertEquals(6, result.asString().length());
        }
    }

    // =========================================================================
    // stripAccents extended edge cases
    // =========================================================================

    @Nested class StripAccentsTests {

        @Test void stripAccents_various() {
            assertEquals("aaaaaa", invoke("stripAccents", S("\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5")).asString());
        }

        @Test void stripAccents_upper() {
            assertEquals("AAA", invoke("stripAccents", S("\u00C0\u00C1\u00C2")).asString());
        }

        @Test void stripAccents_cedilla() {
            assertEquals("cC", invoke("stripAccents", S("\u00E7\u00C7")).asString());
        }

        @Test void stripAccents_nTilde() {
            assertEquals("nN", invoke("stripAccents", S("\u00F1\u00D1")).asString());
        }
    }

    // =========================================================================
    // clean extended edge cases
    // =========================================================================

    @Nested class CleanTests {

        @Test void clean_emptyString() {
            assertEquals("", invoke("clean", S("")).asString());
        }

        @Test void clean_noControlChars() {
            assertEquals("hello world", invoke("clean", S("hello world")).asString());
        }

        @Test void clean_collapsesInteriorWhitespace() {
            assertEquals("a b c", invoke("clean", S("a\nb\tc\r")).asString());
        }

        @Test void clean_integerCoerces() {
            var result = invoke("clean", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // wordCount extended edge cases
    // =========================================================================

    @Nested class WordCountTests {

        @Test void wordCount_onlyWhitespace() {
            assertEquals(0L, invoke("wordCount", S("   ")).asInt64());
        }

        @Test void wordCount_withTabs() {
            assertEquals(3L, invoke("wordCount", S("a\tb\tc")).asInt64());
        }

        @Test void wordCount_integerCoerces() {
            // Coerces integer to string "42" which is 1 word
            var result = invoke("wordCount", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // tokenize extended edge cases
    // =========================================================================

    @Nested class TokenizeTests {

        @Test void tokenize_whitespaceDefault() {
            var result = invoke("tokenize", S("hello world test"));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(3, arr.size());
            assertEquals("hello", arr.get(0).asString());
            assertEquals("world", arr.get(1).asString());
            assertEquals("test", arr.get(2).asString());
        }

        @Test void tokenize_noArgs() {
            var result = invoke("tokenize");
            var arr = result.asArray();
            assertNotNull(arr);
            assertTrue(arr.isEmpty());
        }
    }

    // =========================================================================
    // levenshtein extended edge cases
    // =========================================================================

    @Nested class LevenshteinTests {

        @Test void levenshtein_singleEdit() {
            assertEquals(1L, invoke("levenshtein", S("kitten"), S("sitten")).asInt64());
        }

        @Test void levenshtein_classic() {
            assertEquals(3L, invoke("levenshtein", S("kitten"), S("sitting")).asInt64());
        }

        @Test void levenshtein_tooFewArgs() {
            var result = invoke("levenshtein", S("hello"));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // soundex extended edge cases
    // =========================================================================

    @Nested class SoundexTests {

        @Test void soundex_empty() {
            assertEquals("", invoke("soundex", S("")).asString());
        }

        @Test void soundex_singleLetter() {
            assertEquals("A000", invoke("soundex", S("A")).asString());
        }

        @Test void soundex_smith() {
            assertEquals("S530", invoke("soundex", S("Smith")).asString());
        }

        @Test void soundex_noArgs() {
            assertTrue(invoke("soundex").isNull());
        }
    }

    // =========================================================================
    // base64 extended edge cases
    // =========================================================================

    @Nested class Base64Tests {

        @Test void base64_roundtripEmpty() {
            var encoded = invoke("base64Encode", S(""));
            var decoded = invoke("base64Decode", encoded);
            assertEquals("", decoded.asString());
        }

        @Test void base64_roundtripSpecialChars() {
            var encoded = invoke("base64Encode", S("a&b=c d+e"));
            var decoded = invoke("base64Decode", encoded);
            assertEquals("a&b=c d+e", decoded.asString());
        }

        @Test void base64_roundtripUnicode() {
            var encoded = invoke("base64Encode", S("caf\u00E9"));
            var decoded = invoke("base64Decode", encoded);
            assertEquals("caf\u00E9", decoded.asString());
        }

        @Test void base64_encodeKnown() {
            assertEquals("TWFu", invoke("base64Encode", S("Man")).asString());
        }

        @Test void base64_roundtripLongString() {
            var longStr = "a".repeat(1000);
            var encoded = invoke("base64Encode", S(longStr));
            var decoded = invoke("base64Decode", encoded);
            assertEquals(longStr, decoded.asString());
        }

        @Test void base64Decode_integerCoerces() {
            var result = invoke("base64Decode", I(42));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // URL encode/decode extended edge cases
    // =========================================================================

    @Nested class UrlEncodeDecodeTests {

        @Test void url_roundtripSimple() {
            var encoded = invoke("urlEncode", S("hello world"));
            var decoded = invoke("urlDecode", encoded);
            assertEquals("hello world", decoded.asString());
        }

        @Test void url_roundtripSpecial() {
            var encoded = invoke("urlEncode", S("a=1&b=2"));
            var decoded = invoke("urlDecode", encoded);
            assertEquals("a=1&b=2", decoded.asString());
        }

        @Test void urlEncode_empty() {
            assertEquals("", invoke("urlEncode", S("")).asString());
        }

        @Test void urlDecode_integerCoerces() {
            var result = invoke("urlDecode", I(42));
            assertNotNull(result);
        }

        @Test void url_roundtripUnicode() {
            var encoded = invoke("urlEncode", S("h\u00E9llo w\u00F6rld"));
            var decoded = invoke("urlDecode", encoded);
            assertEquals("h\u00E9llo w\u00F6rld", decoded.asString());
        }
    }

    // =========================================================================
    // hex encode/decode extended edge cases
    // =========================================================================

    @Nested class HexEncodeDecodeTests {

        @Test void hex_roundtripSimple() {
            var encoded = invoke("hexEncode", S("Hello"));
            var decoded = invoke("hexDecode", encoded);
            assertEquals("Hello", decoded.asString());
        }

        @Test void hex_roundtripEmpty() {
            var encoded = invoke("hexEncode", S(""));
            assertEquals("", encoded.asString());
            var decoded = invoke("hexDecode", encoded);
            assertEquals("", decoded.asString());
        }

        @Test void hex_roundtripSpecial() {
            var encoded = invoke("hexEncode", S("ABC"));
            assertEquals("414243", encoded.asString());
            var decoded = invoke("hexDecode", encoded);
            assertEquals("ABC", decoded.asString());
        }

        @Test void hex_roundtripNumbers() {
            var encoded = invoke("hexEncode", S("0123"));
            assertEquals("30313233", encoded.asString());
            var decoded = invoke("hexDecode", encoded);
            assertEquals("0123", decoded.asString());
        }

        @Test void hexDecode_uppercase() {
            assertEquals("Hi", invoke("hexDecode", S("4869")).asString());
        }
    }

    // =========================================================================
    // JSON encode/decode extended edge cases
    // =========================================================================

    @Nested class JsonEncodeDecodeTests {

        @Test void jsonEncode_roundtripString() {
            assertEquals("hello", invoke("jsonEncode", S("hello")).asString());
        }

        @Test void jsonEncode_roundtripInteger() {
            assertEquals("42", invoke("jsonEncode", I(42)).asString());
        }

        @Test void jsonEncode_roundtripBool() {
            assertEquals("true", invoke("jsonEncode", B(true)).asString());
        }

        @Test void jsonEncode_roundtripNull() {
            assertEquals("null", invoke("jsonEncode", Null()).asString());
        }

        @Test void jsonEncode_roundtripArray() {
            assertEquals("[1,2,3]", invoke("jsonEncode", Arr(I(1), I(2), I(3))).asString());
        }

        @SuppressWarnings("unchecked")
        @Test void jsonDecode_array() {
            var result = invoke("jsonDecode", S("[1,2,3]"));
            var arr = result.asArray();
            assertNotNull(arr);
            assertEquals(3, arr.size());
        }

        @Test void jsonDecode_string() {
            assertEquals("hello", invoke("jsonDecode", S("hello")).asString());
        }

        @Test void jsonDecode_nullInput() {
            assertTrue(invoke("jsonDecode", Null()).isNull());
        }

        @Test void jsonEncode_noArgs() {
            assertTrue(invoke("jsonEncode").isNull());
        }

        @Test void jsonDecode_integerCoerces() {
            var result = invoke("jsonDecode", I(42));
            assertNotNull(result);
        }

        @SuppressWarnings("unchecked")
        @Test void jsonEncode_decodeObjectRoundtrip() {
            var obj = Obj(kv("name", S("Alice")), kv("age", I(30)));
            var encoded = invoke("jsonEncode", obj);
            var decoded = invoke("jsonDecode", encoded);
            var objResult = decoded.asObject();
            assertNotNull(objResult);
        }
    }

    // =========================================================================
    // sha256 extended edge cases
    // =========================================================================

    @Nested class Sha256Tests {

        @Test void sha256_empty() {
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    invoke("sha256", S("")).asString());
        }

        @Test void sha256_hello() {
            assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                    invoke("sha256", S("hello")).asString());
        }

        @Test void sha256_integerCoerces() {
            var result = invoke("sha256", I(42));
            assertNotNull(result);
        }

        @Test void sha256_deterministic() {
            var r1 = invoke("sha256", S("abc")).asString();
            var r2 = invoke("sha256", S("abc")).asString();
            assertEquals(r1, r2);
        }

        @Test void sha256_longerString() {
            assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                    invoke("sha256", S("The quick brown fox jumps over the lazy dog")).asString());
        }
    }

    // =========================================================================
    // md5 extended edge cases
    // =========================================================================

    @Nested class Md5Tests {

        @Test void md5_empty() {
            assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                    invoke("md5", S("")).asString());
        }

        @Test void md5_hello() {
            assertEquals("5d41402abc4b2a76b9719d911017c592",
                    invoke("md5", S("hello")).asString());
        }

        @Test void md5_integerCoerces() {
            var result = invoke("md5", I(42));
            assertNotNull(result);
        }

        @Test void md5_deterministic() {
            var r1 = invoke("md5", S("test123")).asString();
            var r2 = invoke("md5", S("test123")).asString();
            assertEquals(r1, r2);
        }
    }

    // =========================================================================
    // crc32 extended edge cases
    // =========================================================================

    @Nested class Crc32Tests {

        @Test void crc32_empty() {
            assertEquals("00000000", invoke("crc32", S("")).asString());
        }

        @Test void crc32_hello() {
            assertEquals("3610a686", invoke("crc32", S("hello")).asString());
        }

        @Test void crc32_integerCoerces() {
            var result = invoke("crc32", I(42));
            assertNotNull(result);
        }

        @Test void crc32_deterministic() {
            var r1 = invoke("crc32", S("abc")).asString();
            var r2 = invoke("crc32", S("abc")).asString();
            assertEquals(r1, r2);
        }
    }

    // =========================================================================
    // Cross-verb integration tests
    // =========================================================================

    @Nested class CrossVerbIntegrationTests {

        @Test void splitThenJoin_roundtrip() {
            var split = invoke("split", S("a,b,c"), S(","));
            var joined = invoke("join", split, S(","));
            assertEquals("a,b,c", joined.asString());
        }

        @Test void hexEncodeThenDecode_roundtrip() {
            var encoded = invoke("hexEncode", S("test data"));
            var decoded = invoke("hexDecode", encoded);
            assertEquals("test data", decoded.asString());
        }

        @Test void snakeToCamelToPascal() {
            var snake = S("hello_world_test");
            var camel = invoke("camelCase", snake);
            assertEquals("helloWorldTest", camel.asString());
            var pascal = invoke("pascalCase", camel);
            assertEquals("HelloWorldTest", pascal.asString());
        }

        @Test void slugifyWithAccents() {
            var stripped = invoke("stripAccents", S("Caf\u00E9 R\u00E9sum\u00E9"));
            var slugged = invoke("slugify", stripped);
            assertEquals("cafe-resume", slugged.asString());
        }

        @Test void normalizeThenWordCount() {
            var normalized = invoke("normalizeSpace", S("  hello   world   test  "));
            var count = invoke("wordCount", normalized);
            assertEquals(3L, count.asInt64());
        }

        @Test void truncateThenPadRight() {
            var truncated = invoke("truncate", S("hello world"), I(5));
            var padded = invoke("padRight", truncated, I(10), S("."));
            assertEquals("hello.....", padded.asString());
        }

        @Test void maskThenReverse() {
            var masked = invoke("mask", S("1234567890"), S("###-###-####"));
            var reversed = invoke("reverseString", masked);
            assertEquals("0987-654-321", reversed.asString());
        }

        @Test void repeatThenTruncate() {
            var repeated = invoke("repeat", S("ab"), I(10));
            var truncated = invoke("truncate", repeated, I(7));
            assertEquals("abababa", truncated.asString());
        }

        @Test void leftOfThenRightOf_emailSplit() {
            var email = S("user@domain.com");
            var user = invoke("leftOf", email, S("@"));
            var domain = invoke("rightOf", email, S("@"));
            assertEquals("user", user.asString());
            assertEquals("domain.com", domain.asString());
        }

        @Test void base64EncodeThenDecode_longRoundtrip() {
            var longStr = "a".repeat(500);
            var encoded = invoke("base64Encode", S(longStr));
            var decoded = invoke("base64Decode", encoded);
            assertEquals(longStr, decoded.asString());
        }

        @Test void urlEncodeThenDecode_unicodeRoundtrip() {
            var encoded = invoke("urlEncode", S("h\u00E9llo w\u00F6rld"));
            var decoded = invoke("urlDecode", encoded);
            assertEquals("h\u00E9llo w\u00F6rld", decoded.asString());
        }
    }

    // =========================================================================
    // Additional replace edge cases
    // =========================================================================

    @Nested class ReplaceEdgeCaseTests {

        @Test void replace_emptySearch_noThrow() {
            // Java and .NET both handle empty search without throwing
            var result = invoke("replace", S("hello"), S(""), S("x"));
            assertNotNull(result);
        }

        @Test void replace_multipleOccurrences() {
            // replace replaces first occurrence only
            assertEquals("h-llo", invoke("replace", S("hello"), S("e"), S("-")).asString());
        }

        @Test void replace_casePreserving() {
            assertEquals("Hello", invoke("replace", S("hello"), S("h"), S("H")).asString());
        }
    }

    // =========================================================================
    // Additional substring edge cases
    // =========================================================================

    @Nested class SubstringEdgeCaseTests {

        @Test void substring_emptyString() {
            assertEquals("", invoke("substring", S(""), I(0)).asString());
        }

        @Test void substring_fullString() {
            assertEquals("hello", invoke("substring", S("hello"), I(0)).asString());
        }

        @Test void substring_lastChar() {
            assertEquals("o", invoke("substring", S("hello"), I(4)).asString());
        }

        @Test void substring_withExactLength() {
            assertEquals("hel", invoke("substring", S("hello"), I(0), I(3)).asString());
        }
    }

    // =========================================================================
    // Additional length edge cases
    // =========================================================================

    @Nested class LengthEdgeCaseTests {

        @Test void length_integer() {
            // Length of integer - coerces to string
            var result = invoke("length", I(42));
            assertNotNull(result);
        }

        @Test void length_emptyArray() {
            assertEquals(0L, invoke("length", Arr()).asInt64());
        }

        @Test void length_largeString() {
            var longStr = "x".repeat(10000);
            assertEquals(10000L, invoke("length", S(longStr)).asInt64());
        }
    }
}
