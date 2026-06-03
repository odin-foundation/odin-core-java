package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for URL-safe Base64, HMAC, URL/query parsing and building, and
 * stable serialization/hashing verbs.
 */
class EncodingVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
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

    private static List<String> keys(DynValue obj) {
        var ks = new ArrayList<String>();
        for (var entry : obj.asObject()) ks.add(entry.getKey());
        return ks;
    }

    // =========================================================================
    // base64urlEncode / base64urlDecode
    // =========================================================================

    @Nested
    class Base64UrlEncode {
        @Test
        void urlSafeNoPadding() {
            assertEquals("aGVsbG8gd29ybGQ_Pj4", invoke("base64urlEncode", S("hello world?>>")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("base64urlEncode", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("base64urlEncode", Null()).isNull());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("base64urlEncode").isNull());
        }
    }

    @Nested
    class Base64UrlDecode {
        @Test
        void roundTrips() {
            var encoded = invoke("base64urlEncode", S("hello world?>>"));
            assertEquals("hello world?>>", invoke("base64urlDecode", encoded).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("base64urlDecode", S("")).asString());
        }

        @Test
        void nullInput() {
            assertTrue(invoke("base64urlDecode", Null()).isNull());
        }
    }

    // =========================================================================
    // hmac
    // =========================================================================

    @Nested
    class Hmac {
        @Test
        void sha256Default() {
            assertEquals("8b5f48702995c1598c573db1e21866a9b825d4a794d169d7060a03605796360b",
                    invoke("hmac", S("message"), S("secret")).asString());
        }

        @Test
        void sha1Explicit() {
            assertEquals("0caf649feee4953d87bf903ac1176c45e028df16",
                    invoke("hmac", S("message"), S("secret"), S("sha1")).asString());
        }

        @Test
        void deterministic() {
            var a = invoke("hmac", S("message"), S("secret")).asString();
            var b = invoke("hmac", S("message"), S("secret")).asString();
            assertEquals(a, b);
        }

        @Test
        void missingKeyYieldsNull() {
            assertTrue(invoke("hmac", S("message")).isNull());
        }

        @Test
        void unknownAlgorithmYieldsNull() {
            assertTrue(invoke("hmac", S("message"), S("secret"), S("sha3")).isNull());
        }
    }

    // =========================================================================
    // parseUrl
    // =========================================================================

    @Nested
    class ParseUrl {
        @Test
        void fullUrlSplitsParts() {
            var result = invoke("parseUrl", S("https://example.com:8080/a/b?z=1&a=2#frag"));
            assertEquals("https", result.get("scheme").asString());
            assertEquals("example.com", result.get("host").asString());
            assertEquals(8080L, result.get("port").asInt64());
            assertEquals("/a/b", result.get("path").asString());
            assertEquals("frag", result.get("fragment").asString());
            // Query keys are sorted for canonical output.
            var query = result.get("query");
            assertEquals(List.of("a", "z"), keys(query));
            assertEquals("2", query.get("a").asString());
            assertEquals("1", query.get("z").asString());
        }

        @Test
        void absentPortIsNull() {
            var result = invoke("parseUrl", S("https://example.com/x"));
            assertTrue(result.get("port").isNull());
            assertEquals("/x", result.get("path").asString());
            assertEquals("", result.get("fragment").asString());
        }

        @Test
        void invalidUrlYieldsNull() {
            assertTrue(invoke("parseUrl", S("not a url")).isNull());
        }
    }

    // =========================================================================
    // buildUrl
    // =========================================================================

    @Nested
    class BuildUrl {
        @Test
        void assemblesWithSortedQuery() {
            var parts = Obj(
                    e("scheme", S("https")),
                    e("host", S("example.com")),
                    e("port", I(8080)),
                    e("path", S("/a/b")),
                    e("query", Obj(e("z", I(1)), e("a", I(2)))),
                    e("fragment", S("frag")));
            assertEquals("https://example.com:8080/a/b?a=2&z=1#frag", invoke("buildUrl", parts).asString());
        }

        @Test
        void missingSchemeYieldsNull() {
            var bad = Obj(e("host", S("example.com")));
            assertTrue(invoke("buildUrl", bad).isNull());
        }

        @Test
        void roundTripsWithParseUrl() {
            var parts = invoke("parseUrl", S("https://example.com:8080/a/b?a=2&z=1#frag"));
            assertEquals("https://example.com:8080/a/b?a=2&z=1#frag", invoke("buildUrl", parts).asString());
        }
    }

    // =========================================================================
    // parseQuery
    // =========================================================================

    @Nested
    class ParseQuery {
        @Test
        void parsesSortedKeys() {
            var result = invoke("parseQuery", S("z=1&a=2"));
            assertEquals(List.of("a", "z"), keys(result));
            assertEquals("2", result.get("a").asString());
            assertEquals("1", result.get("z").asString());
        }

        @Test
        void toleratesLeadingQuestionMark() {
            var result = invoke("parseQuery", S("?a=2"));
            assertEquals("2", result.get("a").asString());
        }
    }

    // =========================================================================
    // buildQuery
    // =========================================================================

    @Nested
    class BuildQuery {
        @Test
        void serializesSortedKeys() {
            assertEquals("a=2&z=1", invoke("buildQuery", Obj(e("z", I(1)), e("a", I(2)))).asString());
        }

        @Test
        void skipsNullValues() {
            assertEquals("a=1", invoke("buildQuery", Obj(e("a", I(1)), e("b", Null()))).asString());
        }
    }

    // =========================================================================
    // stableStringify
    // =========================================================================

    @Nested
    class StableStringify {
        @Test
        void sortsKeysRecursively() {
            var doc = Obj(e("b", I(2)), e("a", I(1)), e("nested", Obj(e("y", I(2)), e("x", I(1)))));
            assertEquals("{\"a\":1,\"b\":2,\"nested\":{\"x\":1,\"y\":2}}",
                    invoke("stableStringify", doc).asString());
        }

        @Test
        void arrayKeepsOrder() {
            assertEquals("[3,1,2]", invoke("stableStringify", Arr(I(3), I(1), I(2))).asString());
        }

        @Test
        void scalar() {
            assertEquals("42", invoke("stableStringify", I(42)).asString());
        }
    }

    // =========================================================================
    // canonicalHash
    // =========================================================================

    @Nested
    class CanonicalHash {
        @Test
        void hashesCanonicalForm() {
            assertEquals("43258cff783fe7036d8a43033f830adfc60ec037382473548ac742b888292777",
                    invoke("canonicalHash", Obj(e("b", I(2)), e("a", I(1)))).asString());
        }

        @Test
        void independentOfKeyOrder() {
            var a = invoke("canonicalHash", Obj(e("b", I(2)), e("a", I(1)))).asString();
            var b = invoke("canonicalHash", Obj(e("a", I(1)), e("b", I(2)))).asString();
            assertEquals(a, b);
        }
    }
}
