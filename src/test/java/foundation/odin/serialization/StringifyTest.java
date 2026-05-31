package foundation.odin.serialization;

import foundation.odin.parsing.OdinParser;
import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinOptions.StringifyOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unused")

class StringifyTest {

    private static OdinDocument parse(String source) {
        return OdinParser.parse(source, ParseOptions.DEFAULT);
    }

    private static String roundTrip(String source) {
        return Stringify.serialize(parse(source));
    }

    private static OdinDocument docWith(String key, OdinValue value) {
        var assignments = new OrderedMap<String, OdinValue>();
        assignments.set(key, value);
        return new OdinDocument(null, assignments, null, null, null, null, null);
    }

    // ── Basic Value Roundtrip ──

    @Nested
    class BasicRoundtrip {
        @Test void stringValue() {
            var result = roundTrip("name = \"Alice\"");
            assertTrue(result.contains("name = \"Alice\""));
        }

        @Test void integerValue() {
            var result = roundTrip("age = ##42");
            assertTrue(result.contains("age = ##42"));
        }

        @Test void numberValue() {
            var result = roundTrip("pi = #3.14");
            assertTrue(result.contains("pi = #3.14"));
        }

        @Test void booleanTrue() {
            var result = roundTrip("active = true");
            assertTrue(result.contains("active = true"));
        }

        @Test void booleanFalse() {
            var result = roundTrip("active = false");
            assertTrue(result.contains("active = false"));
        }

        @Test void nullValue() {
            var result = roundTrip("value = ~");
            assertTrue(result.contains("value = ~"));
        }

        @Test void referenceValue() {
            var result = roundTrip("ref = @target");
            assertTrue(result.contains("ref = @target"));
        }

        @Test void dateValue() {
            var result = roundTrip("start = 2024-06-15");
            assertTrue(result.contains("start = 2024-06-15"));
        }

        @Test void currencyValue() {
            var result = roundTrip("price = #$99.99");
            assertTrue(result.contains("price = #$99.99"));
        }

        @Test void percentValue() {
            var result = roundTrip("rate = #%0.15");
            assertTrue(result.contains("rate = #%0.15"));
        }
    }

    // ── Headers / Sections ──

    @Nested
    class SectionOutput {
        @Test void uppercaseSectionHeader() {
            var result = roundTrip("{Customer}\nName = \"Alice\"");
            assertTrue(result.contains("{Customer}"));
            assertTrue(result.contains("Name = \"Alice\""));
        }

        @Test void lowercaseNoSection() {
            var result = roundTrip("customer.name = \"Alice\"");
            // lowercase first segment should NOT produce a section header
            assertFalse(result.contains("{customer}"));
            assertTrue(result.contains("customer.name = \"Alice\""));
        }

        @Test void multipleSections() {
            var result = roundTrip("{Customer}\nName = \"Alice\"\n\n{Order}\nId = ##1");
            assertTrue(result.contains("{Customer}"));
            assertTrue(result.contains("{Order}"));
        }
    }

    // ── Metadata ──

    @Nested
    class MetadataOutput {
        @Test void metadataIncluded() {
            var opts = new StringifyOptions(true, true, "");
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"test\"");
            var result = Stringify.serialize(doc, opts);
            assertTrue(result.contains("{$}"));
            assertTrue(result.contains("odin = \"1.0.0\""));
        }

        @Test void metadataExcluded() {
            var opts = new StringifyOptions(false, true, "");
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"test\"");
            var result = Stringify.serialize(doc, opts);
            assertFalse(result.contains("{$}"));
        }
    }

    // ── Modifiers ──

    @Nested
    class ModifierOutput {
        @Test void requiredModifier() {
            var result = roundTrip("name = !\"Alice\"");
            assertTrue(result.contains("!\"Alice\""));
        }

        @Test void confidentialModifier() {
            var result = roundTrip("ssn = *\"123\"");
            assertTrue(result.contains("*\"123\""));
        }

        @Test void deprecatedModifier() {
            var result = roundTrip("old = -\"legacy\"");
            assertTrue(result.contains("-\"legacy\""));
        }

        @Test void modifierOrder() {
            var result = roundTrip("field = !*-\"value\"");
            // canonical order: ! - *
            assertTrue(result.contains("!-*\"value\""));
        }
    }

    // ── Directives ──

    @Nested
    class DirectiveOutput {
        @Test void simpleDirective() {
            var result = roundTrip("name = \"Alice\" :required");
            assertTrue(result.contains(":required"));
        }

        @Test void directiveWithValue() {
            var result = roundTrip("width = ##100 :unit px");
            assertTrue(result.contains(":unit px"));
        }
    }

    // ── Escape Sequences ──

    @Nested
    class EscapeOutput {
        @Test void escapedQuote() {
            var doc = docWith("msg", OdinValue.ofString("say \"hi\""));
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\\""));
        }

        @Test void escapedNewline() {
            var doc = docWith("msg", OdinValue.ofString("line1\nline2"));
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\n"));
        }

        @Test void escapedBackslash() {
            var doc = docWith("path", OdinValue.ofString("C:\\Users"));
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\\\"));
        }
    }

    // ── Sorted Output ──

    @Nested
    class SortedOutput {
        @Test void sortedKeys() {
            var opts = new StringifyOptions(true, false, "");
            var doc = parse("z = \"last\"\na = \"first\"");
            var result = Stringify.serialize(doc, opts);
            int aPos = result.indexOf("a = ");
            int zPos = result.indexOf("z = ");
            assertTrue(aPos < zPos, "sorted output should have 'a' before 'z'");
        }
    }

    // ── SplitPath ──

    @Nested
    class SplitPathTests {
        @Test void uppercaseProducesSection() {
            String[] result = new String[2];
            Stringify.splitPath("Customer.Name", result);
            assertEquals("Customer", result[0]);
            assertEquals("Name", result[1]);
        }

        @Test void lowercaseNoSection() {
            String[] result = new String[2];
            Stringify.splitPath("customer.name", result);
            assertNull(result[0]);
            assertEquals("customer.name", result[1]);
        }

        @Test void noDot() {
            String[] result = new String[2];
            Stringify.splitPath("name", result);
            assertNull(result[0]);
            assertEquals("name", result[1]);
        }
    }

    // ── Binary ──

    @Nested
    class BinaryOutput {
        @Test void binaryWithAlgorithm() {
            var doc = docWith("hash", OdinValue.ofBinary(new byte[]{1,2,3}, "sha256"));
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("^sha256:"));
        }

        @Test void binaryNoAlgorithm() {
            var doc = docWith("data", OdinValue.ofBinary(new byte[]{72,101,108,108,111}));
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("^SGVsbG8="));
        }
    }

    // ── Empty Document ──

    @Nested
    class EmptyDocument {
        @Test void emptyDocProducesEmpty() {
            var doc = OdinDocument.empty();
            var result = Stringify.serialize(doc);
            assertTrue(result.isEmpty());
        }
    }
}
