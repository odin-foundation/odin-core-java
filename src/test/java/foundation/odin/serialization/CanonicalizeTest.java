package foundation.odin.serialization;

import foundation.odin.parsing.OdinParser;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinValue;
import foundation.odin.types.OrderedMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalizeTest {

    private static OdinDocument parse(String source) {
        return OdinParser.parse(source, ParseOptions.DEFAULT);
    }

    @Nested
    class BasicCanonical {
        @Test void returnsUtf8Bytes() {
            var doc = parse("name = \"Alice\"");
            byte[] result = Canonicalize.serialize(doc);
            assertNotNull(result);
            assertTrue(result.length > 0);
        }

        @Test void utf8Encoded() {
            var doc = parse("name = \"Alice\"");
            byte[] result = Canonicalize.serialize(doc);
            String text = new String(result, StandardCharsets.UTF_8);
            assertTrue(text.contains("name = \"Alice\""));
        }

        @Test void keysSorted() {
            var doc = parse("z = \"last\"\na = \"first\"");
            byte[] result = Canonicalize.serialize(doc);
            String text = new String(result, StandardCharsets.UTF_8);
            int aPos = text.indexOf("a = ");
            int zPos = text.indexOf("z = ");
            assertTrue(aPos < zPos, "canonical form should sort keys");
        }

        @Test void metadataIncluded() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"test\"");
            byte[] result = Canonicalize.serialize(doc);
            String text = new String(result, StandardCharsets.UTF_8);
            // Canonical form uses flat $.key paths, not {$} headers
            assertTrue(text.contains("$.odin = \"1.0.0\""));
            assertTrue(text.contains("name = \"test\""));
        }

        @Test void deterministicOutput() {
            var doc = parse("z = \"last\"\na = \"first\"\nm = \"mid\"");
            byte[] result1 = Canonicalize.serialize(doc);
            byte[] result2 = Canonicalize.serialize(doc);
            assertArrayEquals(result1, result2, "canonical output must be deterministic");
        }

        @Test void emptyDocProducesEmptyBytes() {
            var doc = OdinDocument.empty();
            byte[] result = Canonicalize.serialize(doc);
            assertEquals(0, result.length);
        }
    }
}
