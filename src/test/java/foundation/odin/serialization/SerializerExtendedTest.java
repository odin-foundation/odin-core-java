package foundation.odin.serialization;

import foundation.odin.parsing.OdinParser;
import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinOptions.StringifyOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended serialization tests — round-trip fidelity, canonical form output,
 * formatting options, modifier serialization, escaping.
 */
class SerializerExtendedTest {

    private static OdinDocument parse(String odin) {
        return OdinParser.parse(odin, ParseOptions.DEFAULT);
    }

    private String roundtrip(String odin) {
        return Stringify.serialize(parse(odin));
    }

    // ─── Round-Trip Fidelity ────────────────────────────────────────────────

    @Nested class RoundTripFidelityTests {
        @Test void parseSerializeParseString() {
            var original = parse("name = \"Alice\"");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertEquals("Alice", reparsed.getString("name"));
        }

        @Test void parseSerializeParseInteger() {
            var original = parse("count = ##42");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertEquals(42L, reparsed.getInteger("count"));
        }

        @Test void parseSerializeParseBoolean() {
            var original = parse("active = true\ninactive = false");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertTrue(reparsed.getBoolean("active"));
            assertFalse(reparsed.getBoolean("inactive"));
        }

        @Test void parseSerializeParseNull() {
            var original = parse("x = ~");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertTrue(reparsed.get("x").isNull());
        }

        @Test void parseSerializeParseReference() {
            var original = parse("ref = @target.path");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertTrue(reparsed.get("ref").isReference());
        }

        @Test void parseSerializeParseDate() {
            var original = parse("d = 2024-06-15");
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertTrue(reparsed.get("d").isDate());
        }

        @Test void parseSerializeParseBinary() {
            var original = parse("b = ^SGVsbG8=");
            var serialized = Stringify.serialize(original);
            assertTrue(serialized.contains("SGVsbG8="));
        }

        @Test void parseSerializeParseMetadata() {
            var input = "{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"";
            var original = parse(input);
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertEquals("1.0.0", reparsed.getString("$.odin"));
            assertEquals("Alice", reparsed.getString("name"));
        }

        @Test void parseSerializeParseMultipleFields() {
            var input = "a = \"alpha\"\nb = ##2\nc = #3.14\nd = true\ne = ~";
            var original = parse(input);
            var serialized = Stringify.serialize(original);
            var reparsed = parse(serialized);
            assertEquals("alpha", reparsed.getString("a"));
            assertEquals(2L, reparsed.getInteger("b"));
            assertTrue(reparsed.getBoolean("d"));
            assertTrue(reparsed.get("e").isNull());
        }
    }

    // ─── Canonical Form Tests ───────────────────────────────────────────────

    @Nested class CanonicalFormTests {
        @Test void canonicalSortsKeys() {
            var doc = new OdinDocumentBuilder()
                .setString("z", "last").setString("a", "first").setString("m", "middle").build();
            var canonical = new String(Canonicalize.serialize(doc), StandardCharsets.UTF_8);
            int aPos = canonical.indexOf("a =");
            int mPos = canonical.indexOf("m =");
            int zPos = canonical.indexOf("z =");
            assertTrue(aPos < mPos && mPos < zPos);
        }

        @Test void canonicalDeterministic() {
            var doc = new OdinDocumentBuilder().setInteger("x", 42L).setString("y", "hello").build();
            var bytes1 = Canonicalize.serialize(doc);
            var bytes2 = Canonicalize.serialize(doc);
            assertArrayEquals(bytes1, bytes2);
        }

        @Test void canonicalDifferentOrderSameOutput() {
            var doc1 = new OdinDocumentBuilder().setInteger("b", 2L).setInteger("a", 1L).build();
            var doc2 = new OdinDocumentBuilder().setInteger("a", 1L).setInteger("b", 2L).build();
            assertArrayEquals(Canonicalize.serialize(doc1), Canonicalize.serialize(doc2));
        }

        @Test void canonicalIsUtf8() {
            var doc = new OdinDocumentBuilder().setString("x", "café").build();
            var bytes = Canonicalize.serialize(doc);
            var str = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(str.contains("café"));
        }

        @Test void canonicalIncludesAllValues() {
            var doc = new OdinDocumentBuilder()
                .setString("str", "text")
                .setInteger("int", 42L)
                .setBoolean("bool", true)
                .setNull("nul")
                .build();
            var str = new String(Canonicalize.serialize(doc), StandardCharsets.UTF_8);
            assertTrue(str.contains("text"));
            assertTrue(str.contains("42"));
            assertTrue(str.contains("true"));
            assertTrue(str.contains("~"));
        }
    }

    // ─── Formatting Options Tests ───────────────────────────────────────────

    @Nested class FormattingOptionsTests {
        @Test void defaultOptionsIncludeMetadata() {
            var doc = new OdinDocumentBuilder()
                .metadata("odin", OdinValue.ofString("1.0.0"))
                .setString("x", "y")
                .build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("{$}"));
        }

        @Test void excludeMetadataOption() {
            var doc = new OdinDocumentBuilder()
                .metadata("odin", OdinValue.ofString("1.0.0"))
                .setString("x", "y")
                .build();
            var opts = new StringifyOptions(false, true, "");
            var result = Stringify.serialize(doc, opts);
            assertFalse(result.contains("{$}"));
        }

        @Test void stringifyOptionsDefaults() {
            assertTrue(StringifyOptions.DEFAULT.isIncludeMetadata());
            assertTrue(StringifyOptions.DEFAULT.isPreserveOrder());
            assertEquals("", StringifyOptions.DEFAULT.getIndent());
        }

        @Test void stringifyOptionsWithMethods() {
            var opts = StringifyOptions.DEFAULT
                .withIncludeMetadata(false)
                .withPreserveOrder(false)
                .withIndent("  ");
            assertFalse(opts.isIncludeMetadata());
            assertFalse(opts.isPreserveOrder());
            assertEquals("  ", opts.getIndent());
        }
    }

    // ─── Modifier Serialization ─────────────────────────────────────────────

    @Nested class ModifierSerializationTests {
        @Test void requiredModifierRoundtrip() {
            var result = roundtrip("x = !\"required\"");
            assertTrue(result.contains("!"));
        }

        @Test void confidentialModifierRoundtrip() {
            var result = roundtrip("x = *\"secret\"");
            assertTrue(result.contains("*"));
        }

        @Test void deprecatedModifierRoundtrip() {
            var result = roundtrip("x = -\"old\"");
            assertTrue(result.contains("-"));
        }

        @Test void combinedModifiersRoundtrip() {
            var result = roundtrip("x = !*\"both\"");
            assertTrue(result.contains("!"));
            assertTrue(result.contains("*"));
        }

        @Test void allThreeModifiersRoundtrip() {
            var result = roundtrip("x = !-*\"all\"");
            assertTrue(result.contains("!"));
            assertTrue(result.contains("-"));
            assertTrue(result.contains("*"));
        }
    }

    // ─── Special Character Escaping ─────────────────────────────────────────

    @Nested class EscapingTests {
        @Test void newlinePreserved() {
            var doc = new OdinDocumentBuilder().setString("x", "line1\nline2").build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\n"));
        }

        @Test void tabPreserved() {
            var doc = new OdinDocumentBuilder().setString("x", "col1\tcol2").build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\t"));
        }

        @Test void quotePreserved() {
            var doc = new OdinDocumentBuilder().setString("x", "say \"hi\"").build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\\""));
        }

        @Test void backslashPreserved() {
            var doc = new OdinDocumentBuilder().setString("x", "path\\file").build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("\\\\"));
        }

        @Test void escapedRoundtrip() {
            var doc = new OdinDocumentBuilder().setString("x", "a\nb\tc\\d\"e").build();
            var serialized = Stringify.serialize(doc);
            var reparsed = parse(serialized);
            assertEquals("a\nb\tc\\d\"e", reparsed.getString("x"));
        }
    }

    // ─── Section Serialization ──────────────────────────────────────────────

    @Nested class SectionSerializationTests {
        @Test void sectionHeaderPresent() {
            var result = roundtrip("{Policy}\nname = \"Test\"");
            assertTrue(result.contains("{Policy}"));
        }

        @Test void multipleSectionsPresent() {
            var result = roundtrip("{A}\nx = ##1\n{B}\ny = ##2");
            assertTrue(result.contains("{A}"));
            assertTrue(result.contains("{B}"));
        }

        @Test void metadataSectionPresent() {
            var result = roundtrip("{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertTrue(result.contains("{$}"));
            assertTrue(result.contains("odin"));
        }
    }

    // ─── Builder to Serialize ───────────────────────────────────────────────

    @Nested class BuilderSerializeTests {
        @Test void serializeBuiltWithAllTypes() {
            var doc = new OdinDocumentBuilder()
                .setString("str", "hello")
                .setInteger("int", 42L)
                .setNumber("num", 3.14)
                .setBoolean("bool", true)
                .setNull("nul")
                .set("ref", OdinValue.ofReference("target"))
                .set("date", OdinValue.ofDate(2024, (byte) 6, (byte) 15))
                .build();
            var result = Stringify.serialize(doc);
            assertTrue(result.contains("hello"));
            assertTrue(result.contains("42"));
            assertTrue(result.contains("3.14"));
            assertTrue(result.contains("true"));
            assertTrue(result.contains("~"));
            assertTrue(result.contains("@target"));
            assertTrue(result.contains("2024-06-15"));
        }

        @Test void serializeEmptyDoc() {
            var result = Stringify.serialize(OdinDocument.empty());
            assertNotNull(result);
        }

        @Test void serializePreservesOrder() {
            var doc = new OdinDocumentBuilder()
                .setString("z", "last")
                .setString("a", "first")
                .build();
            var result = Stringify.serialize(doc);
            assertTrue(result.indexOf("z =") < result.indexOf("a ="));
        }
    }
}
