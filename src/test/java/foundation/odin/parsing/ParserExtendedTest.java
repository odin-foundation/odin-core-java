package foundation.odin.parsing;

import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinErrors.OdinParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended parser tests — deeply nested sections, large documents, all value types,
 * array edge cases, inline arrays, multi-line strings.
 */
class ParserExtendedTest {

    private OdinDocument parse(String odin) { return OdinParser.parse(odin, ParseOptions.DEFAULT); }

    // ─── Deeply nested sections ───────────────────────────────────────────

    @Nested class DeepNestingTests {
        @Test void fiveLevelNesting() {
            var doc = parse("{A.B.C.D.E}\nval = ##1");
            assertEquals(1L, doc.getInteger("A.B.C.D.E.val"));
        }

        @Test void eightLevelNesting() {
            var doc = parse("{A.B.C.D.E.F.G.H}\nval = ##42");
            assertEquals(42L, doc.getInteger("A.B.C.D.E.F.G.H.val"));
        }

        @Test void tenLevelNesting() {
            var doc = parse("{A.B.C.D.E.F.G.H.I.J}\nx = \"deep\"");
            assertEquals("deep", doc.getString("A.B.C.D.E.F.G.H.I.J.x"));
        }

        @Test void nestedSectionsWithMixedContent() {
            var doc = parse(String.join("\n",
                "{Level1}",
                "a = ##1",
                "{Level1.Level2}",
                "b = ##2",
                "{Level1.Level2.Level3}",
                "c = ##3"
            ));
            assertEquals(1L, doc.getInteger("Level1.a"));
            assertEquals(2L, doc.getInteger("Level1.Level2.b"));
            assertEquals(3L, doc.getInteger("Level1.Level2.Level3.c"));
        }

        @Test void twelveLevelNesting() {
            var doc = parse("{A.B.C.D.E.F.G.H.I.J.K.L}\nval = \"very deep\"");
            assertEquals("very deep", doc.getString("A.B.C.D.E.F.G.H.I.J.K.L.val"));
        }

        @Test void dottedPathWithDeepNesting() {
            var doc = parse("a.b.c.d.e.f.g.h = ##99");
            assertEquals(99L, doc.getInteger("a.b.c.d.e.f.g.h"));
        }

        @Test void multipleSectionsAtSameDepth() {
            var doc = parse(String.join("\n",
                "{A.B.C}",
                "x = ##1",
                "{D.E.F}",
                "y = ##2",
                "{G.H.I}",
                "z = ##3"
            ));
            assertEquals(1L, doc.getInteger("A.B.C.x"));
            assertEquals(2L, doc.getInteger("D.E.F.y"));
            assertEquals(3L, doc.getInteger("G.H.I.z"));
        }

        @Test void deepNestingThenShallow() {
            var doc = parse(String.join("\n",
                "{A.B.C.D.E}",
                "deep = ##1",
                "{F}",
                "shallow = ##2"
            ));
            assertEquals(1L, doc.getInteger("A.B.C.D.E.deep"));
            assertEquals(2L, doc.getInteger("F.shallow"));
        }
    }

    // ─── Large documents ──────────────────────────────────────────────────

    @Nested class LargeDocumentTests {
        @Test void hundredAssignments() {
            var sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append(String.format("field%d = ##%d\n", i, i));
            }
            var doc = parse(sb.toString());
            assertEquals(100, doc.paths().size());
            for (int i = 0; i < 100; i++) {
                assertEquals((long) i, doc.getInteger("field" + i));
            }
        }

        @Test void fiveHundredAssignments() {
            var sb = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                sb.append(String.format("f%d = \"value_%d\"\n", i, i));
            }
            var doc = parse(sb.toString());
            assertEquals(500, doc.paths().size());
        }

        @Test void thousandLineDocument() {
            var sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append(String.format("item%d = ##%d\n", i, i));
            }
            var doc = parse(sb.toString());
            assertEquals(1000, doc.paths().size());
            assertEquals(500L, doc.getInteger("item500"));
        }

        @Test void multipleSectionsLargeDoc() {
            var sb = new StringBuilder();
            for (int s = 0; s < 20; s++) {
                sb.append(String.format("{Section%d}\n", s));
                for (int f = 0; f < 10; f++) {
                    sb.append(String.format("field%d = ##%d\n", f, s * 10 + f));
                }
            }
            var doc = parse(sb.toString());
            assertEquals(200, doc.paths().size());
        }

        @Test void largeArrayDocument() {
            var sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append(String.format("items[%d] = \"item_%d\"\n", i, i));
            }
            var doc = parse(sb.toString());
            assertEquals("item_0", doc.getString("items[0]"));
            assertEquals("item_99", doc.getString("items[99]"));
        }
    }

    // ─── All value type prefixes ──────────────────────────────────────────

    @Nested class AllValueTypePrefixTests {
        @Test void numberPrefix() {
            var doc = parse("x = #3.14159");
            assertTrue(doc.get("x").isNumber());
            assertEquals(3.14159, doc.getNumber("x"), 0.00001);
        }

        @Test void integerPrefix() {
            var doc = parse("x = ##12345");
            assertTrue(doc.get("x").isInteger());
            assertEquals(12345L, doc.getInteger("x"));
        }

        @Test void currencyPrefix() {
            var doc = parse("x = #$1234.56");
            assertTrue(doc.get("x").isCurrency());
            assertEquals(1234.56, doc.getNumber("x"), 0.01);
        }

        @Test void percentPrefix() {
            var doc = parse("x = #%0.75");
            assertTrue(doc.get("x").isPercent());
            assertEquals(0.75, doc.getNumber("x"), 0.001);
        }

        @Test void booleanTrueNoPrefix() {
            var doc = parse("x = true");
            assertTrue(doc.get("x").isBoolean());
            assertTrue(doc.getBoolean("x"));
        }

        @Test void booleanFalseNoPrefix() {
            var doc = parse("x = false");
            assertTrue(doc.get("x").isBoolean());
            assertFalse(doc.getBoolean("x"));
        }

        @Test void booleanWithQuestionPrefix() {
            var doc = parse("x = ?true");
            assertTrue(doc.get("x").isBoolean());
            assertTrue(doc.getBoolean("x"));
        }

        @Test void nullTilde() {
            var doc = parse("x = ~");
            assertTrue(doc.get("x").isNull());
        }

        @Test void referencePrefix() {
            var doc = parse("x = @otherField");
            assertTrue(doc.get("x").isReference());
            assertEquals("otherField", doc.get("x").asReference());
        }

        @Test void binaryPrefix() {
            var doc = parse("x = ^SGVsbG8=");
            assertTrue(doc.get("x").isBinary());
        }

        @Test void dateValue() {
            var doc = parse("x = 2024-01-15");
            assertTrue(doc.get("x").isDate());
        }

        @Test void durationValue() {
            var doc = parse("x = P2Y3M");
            assertNotNull(doc.get("x"));
        }

        @Test void negativeInteger() {
            var doc = parse("x = ##-999");
            assertEquals(-999L, doc.getInteger("x"));
        }

        @Test void negativeNumber() {
            var doc = parse("x = #-0.5");
            assertEquals(-0.5, doc.getNumber("x"), 0.001);
        }

        @Test void zeroValues() {
            var doc = parse(String.join("\n",
                "a = ##0",
                "b = #0.0",
                "c = #$0.00",
                "d = #%0.0"
            ));
            assertEquals(0L, doc.getInteger("a"));
            assertEquals(0.0, doc.getNumber("b"), 0.001);
            assertEquals(0.0, doc.getNumber("c"), 0.001);
            assertEquals(0.0, doc.getNumber("d"), 0.001);
        }

        @Test void currencyWithCode() {
            var doc = parse("price = #$100.00:USD");
            var v = (OdinValue.OdinCurrency) doc.get("price");
            assertEquals("USD", v.getCurrencyCode());
            assertEquals(100.0, v.getValue(), 0.01);
        }

        @Test void currencyWithEuroCode() {
            var doc = parse("price = #$50.00:EUR");
            var v = (OdinValue.OdinCurrency) doc.get("price");
            assertEquals("EUR", v.getCurrencyCode());
        }

        @Test void referenceDotted() {
            var doc = parse("ref = @Policy.Insured.name");
            assertEquals("Policy.Insured.name", doc.get("ref").asReference());
        }

        @Test void referenceWithArrayIndex() {
            var doc = parse("ref = @items[3]");
            assertEquals("items[3]", doc.get("ref").asReference());
        }

        @Test void largeInteger() {
            var doc = parse("x = ##2147483647");
            assertEquals(2147483647L, doc.getInteger("x"));
        }

        @Test void veryLargeInteger() {
            var doc = parse("x = ##9999999999");
            assertEquals(9999999999L, doc.getInteger("x"));
        }

        @Test void scientificNotation() {
            var doc = parse("x = #1.5e10");
            assertEquals(1.5e10, doc.getNumber("x"), 1.0);
        }
    }

    // ─── Array parsing edge cases ─────────────────────────────────────────

    @Nested class ArrayEdgeCaseTests {
        @Test void singleElementArray() {
            var doc = parse("items[0] = \"only\"");
            assertEquals("only", doc.getString("items[0]"));
        }

        @Test void arrayWithMixedTypes() {
            var doc = parse(String.join("\n",
                "items[0] = \"string\"",
                "items[1] = ##42",
                "items[2] = true",
                "items[3] = ~",
                "items[4] = #3.14"
            ));
            assertTrue(doc.get("items[0]").isString());
            assertTrue(doc.get("items[1]").isInteger());
            assertTrue(doc.get("items[2]").isBoolean());
            assertTrue(doc.get("items[3]").isNull());
            assertTrue(doc.get("items[4]").isNumber());
        }

        @Test void arrayOfObjects() {
            var doc = parse(String.join("\n",
                "people[0].name = \"Alice\"",
                "people[0].age = ##30",
                "people[1].name = \"Bob\"",
                "people[1].age = ##25"
            ));
            assertEquals("Alice", doc.getString("people[0].name"));
            assertEquals(30L, doc.getInteger("people[0].age"));
            assertEquals("Bob", doc.getString("people[1].name"));
            assertEquals(25L, doc.getInteger("people[1].age"));
        }

        @Test void nestedArraysInSection() {
            var doc = parse(String.join("\n",
                "{Policy}",
                "coverages[0].type = \"GL\"",
                "coverages[0].limit = ##1000000",
                "coverages[1].type = \"WC\"",
                "coverages[1].limit = ##500000"
            ));
            assertEquals("GL", doc.getString("Policy.coverages[0].type"));
            assertEquals(1000000L, doc.getInteger("Policy.coverages[0].limit"));
        }

        @Test void multipleArraysInDocument() {
            var doc = parse(String.join("\n",
                "names[0] = \"Alice\"",
                "names[1] = \"Bob\"",
                "ages[0] = ##30",
                "ages[1] = ##25"
            ));
            assertEquals("Alice", doc.getString("names[0]"));
            assertEquals("Bob", doc.getString("names[1]"));
            assertEquals(30L, doc.getInteger("ages[0]"));
            assertEquals(25L, doc.getInteger("ages[1]"));
        }

        @Test void arrayIndexZero() {
            var doc = parse("arr[0] = \"first\"");
            assertEquals("first", doc.getString("arr[0]"));
        }

        @Test void arrayWithTenElements() {
            var sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(String.format("arr[%d] = ##%d\n", i, i * 10));
            }
            var doc = parse(sb.toString());
            for (int i = 0; i < 10; i++) {
                assertEquals((long) (i * 10), doc.getInteger("arr[" + i + "]"));
            }
        }

        @Test void arrayWithModifiers() {
            var doc = parse("items[0] = !\"important\"");
            assertEquals("important", doc.getString("items[0]"));
            assertTrue(doc.getPathModifiers().get("items[0]").isRequired());
        }

        @Test void deeplyNestedArrayAccess() {
            var doc = parse(String.join("\n",
                "data[0].children[0].name = \"leaf\"",
                "data[0].children[0].value = ##42"
            ));
            assertEquals("leaf", doc.getString("data[0].children[0].name"));
            assertEquals(42L, doc.getInteger("data[0].children[0].value"));
        }

        @Test void arrayInNestedSection() {
            var doc = parse(String.join("\n",
                "{A.B.C}",
                "items[0] = \"x\"",
                "items[1] = \"y\""
            ));
            assertEquals("x", doc.getString("A.B.C.items[0]"));
            assertEquals("y", doc.getString("A.B.C.items[1]"));
        }
    }

    // ─── Mixed content types ──────────────────────────────────────────────

    @Nested class MixedContentTests {
        @Test void sectionsAndRootFields() {
            var doc = parse(String.join("\n",
                "root_field = \"at root\"",
                "{Section}",
                "nested = \"in section\"",
                "{}",
                "back_to_root = \"also root\""
            ));
            assertEquals("at root", doc.getString("root_field"));
            assertEquals("in section", doc.getString("Section.nested"));
            assertEquals("also root", doc.getString("back_to_root"));
        }

        @Test void metadataWithSectionsAndArrays() {
            var doc = parse(String.join("\n",
                "{$}",
                "odin = \"1.0.0\"",
                "",
                "{Policy}",
                "number = \"POL-001\"",
                "coverages[0] = \"GL\"",
                "coverages[1] = \"WC\"",
                "",
                "{Policy.Insured}",
                "name = \"John\""
            ));
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("POL-001", doc.getString("Policy.number"));
            assertEquals("GL", doc.getString("Policy.coverages[0]"));
            assertEquals("John", doc.getString("Policy.Insured.name"));
        }

        @Test void allTypesWithModifiers() {
            var doc = parse(String.join("\n",
                "req_str = !\"required string\"",
                "conf_int = *##42",
                "dep_num = -#3.14",
                "req_conf = !*\"both\"",
                "all_mods = !-*\"all three\""
            ));
            assertTrue(doc.getPathModifiers().get("req_str").isRequired());
            assertTrue(doc.getPathModifiers().get("conf_int").isConfidential());
            assertTrue(doc.getPathModifiers().get("dep_num").isDeprecated());
            assertTrue(doc.getPathModifiers().get("req_conf").isRequired());
            assertTrue(doc.getPathModifiers().get("req_conf").isConfidential());
            assertTrue(doc.getPathModifiers().get("all_mods").isRequired());
            assertTrue(doc.getPathModifiers().get("all_mods").isDeprecated());
            assertTrue(doc.getPathModifiers().get("all_mods").isConfidential());
        }

        @Test void commentsInterspersedWithData() {
            var doc = parse(String.join("\n",
                "; Header comment",
                "name = \"Alice\"",
                "; Another comment",
                "age = ##30",
                "; Final comment"
            ));
            assertEquals("Alice", doc.getString("name"));
            assertEquals(30L, doc.getInteger("age"));
        }

        @Test void emptyLinesBetweenSections() {
            var doc = parse(String.join("\n",
                "{A}",
                "x = ##1",
                "",
                "",
                "{B}",
                "y = ##2",
                "",
                "{C}",
                "z = ##3"
            ));
            assertEquals(1L, doc.getInteger("A.x"));
            assertEquals(2L, doc.getInteger("B.y"));
            assertEquals(3L, doc.getInteger("C.z"));
        }
    }

    // ─── String edge cases ────────────────────────────────────────────────

    @Nested class StringEdgeCaseTests {
        @Test void veryLongString() {
            var longVal = "x".repeat(10000);
            var doc = parse("x = \"" + longVal + "\"");
            assertEquals(longVal, doc.getString("x"));
        }

        @Test void stringWithAllEscapes() {
            var doc = parse("x = \"\\n\\t\\r\\\\\\\"\"");
            assertEquals("\n\t\r\\\"", doc.getString("x"));
        }

        @Test void stringWithUnicodeChars() {
            var doc = parse("x = \"日本語テスト\"");
            assertEquals("日本語テスト", doc.getString("x"));
        }

        @Test void stringWithEmoji() {
            var doc = parse("x = \"Hello 🌍\"");
            assertEquals("Hello 🌍", doc.getString("x"));
        }

        @Test void stringWithUnicodeEscapeSequence() {
            var doc = parse("x = \"\\u0048\\u0065\\u006C\\u006C\\u006F\"");
            assertEquals("Hello", doc.getString("x"));
        }

        @Test void emptyString() {
            var doc = parse("x = \"\"");
            assertEquals("", doc.getString("x"));
        }

        @Test void stringWithOnlySpaces() {
            var doc = parse("x = \"   \"");
            assertEquals("   ", doc.getString("x"));
        }

        @Test void stringWithSpecialChars() {
            var doc = parse("x = \"!@#$%^&*(){}[]|<>\"");
            assertEquals("!@#$%^&*(){}[]|<>", doc.getString("x"));
        }

        @Test void stringWithNewlineEscape() {
            var doc = parse("x = \"line1\\nline2\\nline3\"");
            assertEquals("line1\nline2\nline3", doc.getString("x"));
        }

        @Test void stringWithMixedEscapes() {
            var doc = parse("x = \"tab\\there\\nnew\\\\line\"");
            assertEquals("tab\there\nnew\\line", doc.getString("x"));
        }
    }

    // ─── Whitespace handling ──────────────────────────────────────────────

    @Nested class WhitespaceHandlingTests {
        @Test void leadingSpaces() {
            var doc = parse("   name = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void trailingSpaces() {
            var doc = parse("name = \"Alice\"   ");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void spacesAroundEquals() {
            var doc = parse("name   =   \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void tabsAroundEquals() {
            var doc = parse("name\t=\t\"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void mixedWhitespace() {
            var doc = parse("  \tname \t = \t \"Alice\"  \t");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void multipleBlankLines() {
            var doc = parse("a = ##1\n\n\n\n\nb = ##2");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
        }
    }

    // ─── Multi-document parsing ───────────────────────────────────────────

    @Nested class MultiDocumentExtendedTests {
        @Test void threeDocuments() {
            var docs = OdinParser.parseMulti("a = ##1\n---\nb = ##2\n---\nc = ##3", ParseOptions.DEFAULT);
            assertEquals(3, docs.size());
            assertEquals(1L, docs.get(0).getInteger("a"));
            assertEquals(2L, docs.get(1).getInteger("b"));
            assertEquals(3L, docs.get(2).getInteger("c"));
        }

        @Test void documentsWithSections() {
            var docs = OdinParser.parseMulti(
                "{A}\nx = ##1\n---\n{B}\ny = ##2", ParseOptions.DEFAULT);
            assertEquals(2, docs.size());
            assertEquals(1L, docs.get(0).getInteger("A.x"));
            assertEquals(2L, docs.get(1).getInteger("B.y"));
        }
    }

    // ─── Path resolution ──────────────────────────────────────────────────

    @Nested class PathResolutionTests {
        @Test void resolveSimpleReference() {
            var doc = parse("target = \"hello\"\nref = @target");
            var resolved = doc.resolve("ref");
            assertTrue(resolved.isString());
            assertEquals("hello", resolved.asString());
        }

        @Test void resolveChainedReference() {
            var opts = ParseOptions.DEFAULT.withAllowDuplicates(true);
            var doc = OdinParser.parse("a = \"final\"\nb = @a\nc = @b", opts);
            var resolved = doc.resolve("c");
            assertEquals("final", resolved.asString());
        }

        @Test void circularReferenceThrows() {
            var opts = ParseOptions.DEFAULT.withAllowDuplicates(true);
            var doc = OdinParser.parse("a = @b\nb = @a", opts);
            assertThrows(IllegalStateException.class, () -> doc.resolve("a"));
        }

        @Test void unresolvedReferenceThrows() {
            var doc = parse("ref = @nonexistent");
            assertThrows(IllegalStateException.class, () -> doc.resolve("ref"));
        }

        @Test void resolveNonReference() {
            var doc = parse("x = \"plain\"");
            var resolved = doc.resolve("x");
            assertEquals("plain", resolved.asString());
        }
    }
}
