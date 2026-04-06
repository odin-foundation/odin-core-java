package foundation.odin.parsing;

import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OdinParserTest {

    private static foundation.odin.types.OdinDocument parse(String source) {
        return OdinParser.parse(source, ParseOptions.DEFAULT);
    }

    private static foundation.odin.types.OdinDocument parse(String source, ParseOptions opts) {
        return OdinParser.parse(source, opts);
    }

    // ── Basic Assignments ──

    @Nested
    class BasicAssignments {
        @Test void stringAssignment() {
            var doc = parse("name = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void integerAssignment() {
            var doc = parse("age = ##42");
            assertEquals(42L, doc.getInteger("age"));
        }

        @Test void numberAssignment() {
            var doc = parse("pi = #3.14");
            assertEquals(3.14, doc.getNumber("pi"), 0.001);
        }

        @Test void booleanAssignment() {
            var doc = parse("active = true");
            assertEquals(true, doc.getBoolean("active"));
        }

        @Test void nullAssignment() {
            var doc = parse("value = ~");
            assertTrue(doc.get("value").isNull());
        }

        @Test void referenceAssignment() {
            var doc = parse("ref = @target");
            assertTrue(doc.get("ref").isReference());
            assertEquals("target", doc.get("ref").asReference());
        }

        @Test void dateAssignment() {
            var doc = parse("start = 2024-06-15");
            assertTrue(doc.get("start").isDate());
        }

        @Test void multipleAssignments() {
            var doc = parse("name = \"Alice\"\nage = ##30\nactive = true");
            assertEquals("Alice", doc.getString("name"));
            assertEquals(30L, doc.getInteger("age"));
            assertEquals(true, doc.getBoolean("active"));
        }

        @Test void currencyAssignment() {
            var doc = parse("price = #$99.99");
            assertTrue(doc.get("price").isCurrency());
            assertEquals(99.99, doc.get("price").asDouble(), 0.001);
        }

        @Test void percentAssignment() {
            var doc = parse("rate = #%0.15");
            assertTrue(doc.get("rate").isPercent());
        }
    }

    // ── Headers / Sections ──

    @Nested
    class Headers {
        @Test void simpleHeader() {
            var doc = parse("{Customer}\nName = \"Alice\"\nAge = ##30");
            assertEquals("Alice", doc.getString("Customer.Name"));
            assertEquals(30L, doc.getInteger("Customer.Age"));
        }

        @Test void multipleHeaders() {
            var doc = parse("{Customer}\nName = \"Alice\"\n\n{Order}\nId = ##1");
            assertEquals("Alice", doc.getString("Customer.Name"));
            assertEquals(1L, doc.getInteger("Order.Id"));
        }

        @Test void nestedPath() {
            var doc = parse("{Customer}\nAddress.City = \"NYC\"");
            assertEquals("NYC", doc.getString("Customer.Address.City"));
        }

        @Test void emptyHeader() {
            var doc = parse("{}\nname = \"root\"");
            assertEquals("root", doc.getString("name"));
        }
    }

    // ── Metadata ──

    @Nested
    class MetadataTests {
        @Test void metadataSection() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\n{Customer}\nName = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("Alice", doc.getString("Customer.Name"));
        }

        @Test void metadataInMetadataMap() {
            var doc = parse("{$}\nodin = \"1.0.0\"");
            assertNotNull(doc.getMetadata().tryGet("odin"));
            assertEquals("1.0.0", doc.getMetadata().tryGet("odin").asString());
        }

        @Test void metadataAlsoInAssignments() {
            var doc = parse("{$}\nodin = \"1.0.0\"");
            assertNotNull(doc.get("$.odin"));
        }
    }

    // ── Modifiers ──

    @Nested
    class ModifierTests {
        @Test void requiredModifier() {
            var doc = parse("name = !\"Alice\"");
            assertTrue(doc.get("name").isRequired());
        }

        @Test void confidentialModifier() {
            var doc = parse("ssn = *\"123-45-6789\"");
            assertTrue(doc.get("ssn").isConfidential());
        }

        @Test void deprecatedModifier() {
            var doc = parse("old = -\"legacy\"");
            assertTrue(doc.get("old").isDeprecated());
        }

        @Test void combinedModifiers() {
            var doc = parse("field = !*-\"value\"");
            var v = doc.get("field");
            assertTrue(v.isRequired());
            assertTrue(v.isConfidential());
            assertTrue(v.isDeprecated());
        }

        @Test void modifiersInPathModifiers() {
            var doc = parse("name = !\"Alice\"");
            var mods = doc.getPathModifiers().tryGet("name");
            assertNotNull(mods);
            assertTrue(mods.isRequired());
        }
    }

    // ── Directives ──

    @Nested
    class DirectiveTests {
        @Test void simpleDirective() {
            var doc = parse("name = \"Alice\" :required");
            var v = doc.get("name");
            assertFalse(v.getDirectives().isEmpty());
            assertEquals("required", v.getDirectives().get(0).getName());
        }

        @Test void directiveWithValue() {
            var doc = parse("width = ##100 :unit px");
            var v = doc.get("width");
            assertFalse(v.getDirectives().isEmpty());
            assertEquals("unit", v.getDirectives().get(0).getName());
            assertEquals("px", v.getDirectives().get(0).getValue().asString());
        }

        @Test void multipleDirectives() {
            var doc = parse("field = \"val\" :min 1 :max 100");
            var v = doc.get("field");
            assertEquals(2, v.getDirectives().size());
        }
    }

    // ── Arrays ──

    @Nested
    class ArrayTests {
        @Test void simpleArray() {
            var doc = parse("items[0] = \"a\"\nitems[1] = \"b\"\nitems[2] = \"c\"");
            assertEquals("a", doc.getString("items[0]"));
            assertEquals("b", doc.getString("items[1]"));
            assertEquals("c", doc.getString("items[2]"));
        }

        @Test void arrayWithHeader() {
            var doc = parse("{Order}\nitems[0].name = \"Widget\"\nitems[0].qty = ##5");
            assertEquals("Widget", doc.getString("Order.items[0].name"));
            assertEquals(5L, doc.getInteger("Order.items[0].qty"));
        }

        @Test void nonContiguousArrayThrows() {
            assertThrows(OdinParseException.class, () ->
                    parse("items[0] = \"a\"\nitems[2] = \"c\""));
        }

        @Test void negativeIndexThrows() {
            assertThrows(OdinParseException.class, () ->
                    parse("items[-1] = \"a\""));
        }
    }

    // ── Depth Validation ──

    @Nested
    class DepthValidation {
        @Test void maxDepthExceeded() {
            var opts = ParseOptions.DEFAULT.withMaxDepth(3);
            assertThrows(OdinParseException.class, () ->
                    parse("a.b.c.d = \"deep\"", opts));
        }

        @Test void withinMaxDepth() {
            var opts = ParseOptions.DEFAULT.withMaxDepth(5);
            var doc = parse("a.b.c = \"ok\"", opts);
            assertEquals("ok", doc.getString("a.b.c"));
        }
    }

    // ── Duplicate Detection ──

    @Nested
    class DuplicateDetection {
        @Test void duplicatePathThrows() {
            assertThrows(OdinParseException.class, () ->
                    parse("name = \"Alice\"\nname = \"Bob\""));
        }

        @Test void allowDuplicatesOption() {
            var opts = ParseOptions.DEFAULT.withAllowDuplicates(true);
            var doc = parse("name = \"Alice\"\nname = \"Bob\"", opts);
            assertEquals("Bob", doc.getString("name"));
        }
    }

    // ── Index Range ──

    @Nested
    class IndexRange {
        @Test void indexExceedsMaxThrows() {
            var opts = ParseOptions.DEFAULT.withMaxArrayIndex(5);
            assertThrows(OdinParseException.class, () ->
                    parse("items[10] = \"x\"", opts));
        }
    }

    // ── Multi-Document ──

    @Nested
    class MultiDocument {
        @Test void twoDocuments() {
            var docs = OdinParser.parseMulti("name = \"v1\"\n---\nname = \"v2\"", ParseOptions.DEFAULT);
            assertEquals(2, docs.size());
            assertEquals("v1", docs.get(0).getString("name"));
            assertEquals("v2", docs.get(1).getString("name"));
        }

        @Test void singleDocumentReturnsLast() {
            var doc = parse("name = \"v1\"\n---\nname = \"v2\"");
            assertEquals("v2", doc.getString("name"));
        }
    }

    // ── Empty Input ──

    @Nested
    class EmptyInput {
        @Test void emptyStringReturnsEmptyDoc() {
            var doc = parse("");
            assertTrue(doc.paths().isEmpty());
        }

        @Test void whitespaceOnlyReturnsEmpty() {
            var doc = parse("   \n\n  ");
            assertTrue(doc.paths().isEmpty());
        }

        @Test void commentsOnlyReturnsEmpty() {
            var doc = parse("; comment line\n; another");
            assertTrue(doc.paths().isEmpty());
        }
    }

    // ── Import ──

    @Nested
    class ImportTests {
        @Test void simpleImport() {
            var doc = parse("@import \"base.odin\"");
            assertEquals(1, doc.getImports().size());
            assertEquals("base.odin", doc.getImports().get(0).path());
        }

        @Test void importWithAlias() {
            var doc = parse("@import \"base.odin\" as base");
            assertEquals(1, doc.getImports().size());
            assertEquals("base", doc.getImports().get(0).alias());
        }
    }

    // ── Schema ──

    @Nested
    class SchemaTests {
        @Test void schemaDirective() {
            var doc = parse("@schema \"customer.odin\"");
            assertEquals(1, doc.getSchemas().size());
            assertEquals("customer.odin", doc.getSchemas().get(0).url());
        }
    }

    // ── Verb Values ──

    @Nested
    class VerbTests {
        @Test void verbValue() {
            var doc = parse("transform = %upper");
            assertTrue(doc.get("transform").isVerb());
        }
    }

    // ── Comments ──

    @Nested
    class CommentTests {
        @Test void commentsSkipped() {
            var doc = parse("; this is a comment\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void inlineCommentsHandled() {
            var doc = parse("name = \"Alice\" ; a comment");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ── Boolean Literals in Path Position ──

    @Nested
    class BooleanLiteralPath {
        @Test void trueAsPathName() {
            var doc = parse("true = \"yes\"");
            assertEquals("yes", doc.getString("true"));
        }

        @Test void falseAsPathName() {
            var doc = parse("false = \"no\"");
            assertEquals("no", doc.getString("false"));
        }
    }

    // ── Array Header Bracket Path ──

    @Nested
    class ArrayHeaderBracketPath {
        @Test void bracketPathContinuesHeader() {
            var doc = parse("{items}\n[0].name = \"first\"\n[1].name = \"second\"");
            assertEquals("first", doc.getString("items[0].name"));
            assertEquals("second", doc.getString("items[1].name"));
        }
    }
}
