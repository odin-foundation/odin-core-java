package foundation.odin.types;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OdinDocumentBuilderTest {

    @Test void buildEmptyDocument() {
        var doc = new OdinDocumentBuilder().build();
        assertEquals(0, doc.getAssignments().size());
        assertEquals(0, doc.getMetadata().size());
    }

    // ── Metadata ──

    @Nested
    class MetadataTests {
        @Test void buildWithStringMetadata() {
            var doc = new OdinDocumentBuilder()
                    .metadata("odin", "1.0.0")
                    .build();
            assertEquals("1.0.0", doc.get("$.odin").asString());
        }

        @Test void buildWithOdinValueMetadata() {
            var doc = new OdinDocumentBuilder()
                    .metadata("version", OdinValue.ofInteger(1))
                    .build();
            assertEquals(1L, doc.get("$.version").asInt64());
        }
    }

    // ── Set Various Types ──

    @Nested
    class SetTests {
        @Test void buildSetString() {
            var doc = new OdinDocumentBuilder().setString("name", "Alice").build();
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void buildSetInteger() {
            var doc = new OdinDocumentBuilder().setInteger("age", 30).build();
            assertEquals(30L, doc.getInteger("age"));
        }

        @Test void buildSetNumber() {
            var doc = new OdinDocumentBuilder().setNumber("pi", 3.14).build();
            assertEquals(3.14, doc.getNumber("pi"));
        }

        @Test void buildSetBoolean() {
            var doc = new OdinDocumentBuilder().setBoolean("active", true).build();
            assertEquals(true, doc.getBoolean("active"));
        }

        @Test void buildSetNull() {
            var doc = new OdinDocumentBuilder().setNull("empty").build();
            assertTrue(doc.get("empty").isNull());
        }

        @Test void buildSetCurrency() {
            var doc = new OdinDocumentBuilder().setCurrency("price", 99.99, (byte) 2).build();
            assertEquals(99.99, doc.getNumber("price"));
        }

        @Test void buildSetCurrencyWithCode() {
            var doc = new OdinDocumentBuilder().setCurrency("amount", 100.0, (byte) 2, "EUR").build();
            var val = (OdinValue.OdinCurrency) doc.get("amount");
            assertEquals("EUR", val.getCurrencyCode());
        }

        @Test void buildSetOdinValueDirectly() {
            var doc = new OdinDocumentBuilder()
                    .set("ref", OdinValue.ofReference("other.field"))
                    .build();
            assertEquals("other.field", doc.get("ref").asReference());
        }

        @Test void buildWithModifiers() {
            var mods = new OdinModifiers(true, false, false, false);
            var doc = new OdinDocumentBuilder()
                    .setString("name", "test")
                    .withModifiers("name", mods)
                    .build();
            assertTrue(doc.getPathModifiers().containsKey("name"));
            assertTrue(doc.getPathModifiers().get("name").isRequired());
        }

        @Test void buildWithMultipleModifiers() {
            var mods = new OdinModifiers(true, true, true, false);
            var doc = new OdinDocumentBuilder()
                    .withModifiers("field", mods)
                    .build();
            var m = doc.getPathModifiers().get("field");
            assertTrue(m.isRequired());
            assertTrue(m.isConfidential());
            assertTrue(m.isDeprecated());
        }
    }

    // ── Imports, Schemas, Comments ──

    @Nested
    class DirectiveTests {
        @Test void buildWithImport() {
            var doc = new OdinDocumentBuilder()
                    .addImport("./base.odin", "base")
                    .build();
            assertEquals(1, doc.getImports().size());
            assertEquals("./base.odin", doc.getImports().get(0).path());
            assertEquals("base", doc.getImports().get(0).alias());
        }

        @Test void buildWithSchema() {
            var doc = new OdinDocumentBuilder()
                    .addSchema("policy.schema.odin")
                    .build();
            assertEquals(1, doc.getSchemas().size());
            assertEquals("policy.schema.odin", doc.getSchemas().get(0).url());
        }

        @Test void buildWithComment() {
            var doc = new OdinDocumentBuilder()
                    .addComment("this is a comment", "field.path")
                    .build();
            assertEquals(1, doc.getComments().size());
            assertEquals("this is a comment", doc.getComments().get(0).text());
        }
    }

    // ── Fluent Chaining ──

    @Test void buildFluentChaining() {
        var doc = new OdinDocumentBuilder()
                .metadata("odin", "1.0.0")
                .setString("Policy.Name", "Test")
                .setInteger("Policy.Number", 12345)
                .setBoolean("Policy.Active", true)
                .build();

        assertEquals("1.0.0", doc.get("$.odin").asString());
        assertEquals("Test", doc.getString("Policy.Name"));
        assertEquals(12345L, doc.getInteger("Policy.Number"));
        assertEquals(true, doc.getBoolean("Policy.Active"));
    }

    // ── Document Operations ──

    @Nested
    class DocumentOperations {
        @Test void pathsReturnsAllKeys() {
            var doc = new OdinDocumentBuilder()
                    .setString("a", "1")
                    .setString("b", "2")
                    .build();
            var paths = doc.paths();
            assertEquals(2, paths.size());
            assertTrue(paths.contains("a"));
            assertTrue(paths.contains("b"));
        }

        @Test void flattenDocument() {
            var doc = new OdinDocumentBuilder()
                    .setString("name", "test")
                    .setNull("empty")
                    .build();
            var flat = doc.flatten();
            assertEquals("test", flat.tryGet("name"));
            // Null is excluded by default
            assertNull(flat.tryGet("empty"));
        }

        @Test void emptyHasNoAssignments() {
            var doc = OdinDocument.empty();
            assertEquals(0, doc.getAssignments().size());
        }

        @Test void documentWith() {
            var doc = new OdinDocumentBuilder().setString("a", "1").build();
            var doc2 = doc.with("b", OdinValue.ofString("2"));
            assertTrue(doc2.has("a"));
            assertTrue(doc2.has("b"));
            assertFalse(doc.has("b")); // original unchanged
        }

        @Test void documentWithout() {
            var doc = new OdinDocumentBuilder()
                    .setString("a", "1")
                    .setString("b", "2")
                    .build();
            var doc2 = doc.without("a");
            assertFalse(doc2.has("a"));
            assertTrue(doc2.has("b"));
        }

        @Test void documentResolveFollowsReferences() {
            var doc = new OdinDocumentBuilder()
                    .setString("target", "hello")
                    .set("ref", OdinValue.ofReference("target"))
                    .build();
            var resolved = doc.resolve("ref");
            assertEquals("hello", resolved.asString());
        }

        @Test void documentResolveThrowsOnCircular() {
            var doc = new OdinDocumentBuilder()
                    .set("a", OdinValue.ofReference("b"))
                    .set("b", OdinValue.ofReference("a"))
                    .build();
            assertThrows(IllegalStateException.class, () -> doc.resolve("a"));
        }
    }
}
