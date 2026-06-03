package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HTML/XML markup verbs: escapeHtml, unescapeHtml, escapeXml,
 * stripTags, and template.
 */
class MarkupVerbTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    @SafeVarargs
    private static DynValue Obj(Map.Entry<String, DynValue>... entries) {
        return DynValue.ofObject(new ArrayList<>(List.of(entries)));
    }
    private static Map.Entry<String, DynValue> e(String k, DynValue v) {
        return Map.entry(k, v);
    }

    // =========================================================================
    // escapeHtml
    // =========================================================================

    @Nested
    class EscapeHtml {
        @Test
        void escapesSpecials() {
            assertEquals("&lt;p&gt;1 &amp; 2&lt;/p&gt;", invoke("escapeHtml", S("<p>1 & 2</p>")).asString());
        }

        @Test
        void apostropheBecomesNumericEntity() {
            assertEquals("&#39;", invoke("escapeHtml", S("'")).asString());
        }

        @Test
        void empty() {
            assertEquals("", invoke("escapeHtml", S("")).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("escapeHtml").isNull());
        }
    }

    // =========================================================================
    // unescapeHtml
    // =========================================================================

    @Nested
    class UnescapeHtml {
        @Test
        void decodesNamedEntities() {
            assertEquals("<p>1 & 2</p>", invoke("unescapeHtml", S("&lt;p&gt;1 &amp; 2&lt;/p&gt;")).asString());
        }

        @Test
        void decodesNumericAndHexReferences() {
            assertEquals("AB", invoke("unescapeHtml", S("&#65;&#x42;")).asString());
        }

        @Test
        void roundTripsWithEscapeHtml() {
            var raw = "a < b & c > d \" '";
            var escaped = invoke("escapeHtml", S(raw));
            assertEquals(raw, invoke("unescapeHtml", escaped).asString());
        }
    }

    // =========================================================================
    // escapeXml
    // =========================================================================

    @Nested
    class EscapeXml {
        @Test
        void apostropheBecomesApos() {
            assertEquals("x = &apos;a&apos; &amp; b", invoke("escapeXml", S("x = 'a' & b")).asString());
        }

        @Test
        void escapesAnglesAndQuotes() {
            assertEquals("&lt;a href=&quot;u&quot;&gt;", invoke("escapeXml", S("<a href=\"u\">")).asString());
        }

        @Test
        void plainTextUnchanged() {
            assertEquals("no specials", invoke("escapeXml", S("no specials")).asString());
        }
    }

    // =========================================================================
    // stripTags
    // =========================================================================

    @Nested
    class StripTags {
        @Test
        void removesTags() {
            assertEquals("Hello world", invoke("stripTags", S("<p>Hello <b>world</b></p>")).asString());
        }

        @Test
        void plainTextUnchanged() {
            assertEquals("no tags here", invoke("stripTags", S("no tags here")).asString());
        }

        @Test
        void noArgs() {
            assertTrue(invoke("stripTags").isNull());
        }
    }

    // =========================================================================
    // template
    // =========================================================================

    @Nested
    class Template {
        private final DynValue data = Obj(e("name", S("Ada")), e("age", I(36)));

        @Test
        void fillsPlaceholders() {
            assertEquals("Hi Ada, you are 36",
                    invoke("template", S("Hi {name}, you are {age}"), data).asString());
        }

        @Test
        void missingKeyRendersEmpty() {
            assertEquals("ab", invoke("template", S("a{missing}b"), data).asString());
        }

        @Test
        void trimsWhitespaceInBraces() {
            assertEquals("Ada", invoke("template", S("{ name }"), data).asString());
        }

        @Test
        void tooFewArgsYieldsNull() {
            assertTrue(invoke("template", S("a{name}b")).isNull());
        }
    }
}
