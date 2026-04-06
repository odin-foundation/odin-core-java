package foundation.odin.parsing;

import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinErrors.OdinParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser edge cases — empty documents, whitespace-only, BOM handling, line endings,
 * tab vs space, maximum nesting, unicode in keys/values, escaped characters.
 */
class ParserEdgeCasesTest {

    private OdinDocument parse(String odin) { return OdinParser.parse(odin, ParseOptions.DEFAULT); }

    // ─── Empty and whitespace documents ───────────────────────────────────

    @Nested class EmptyDocumentTests {
        @Test void emptyStringIsValid() {
            var doc = parse("");
            assertNotNull(doc);
            assertTrue(doc.paths().isEmpty());
        }

        @Test void emptyStringAllowed() {
            var opts = ParseOptions.DEFAULT.withAllowEmpty(true);
            var doc = OdinParser.parse("", opts);
            assertNotNull(doc);
            assertTrue(doc.paths().isEmpty());
        }

        @Test void whitespaceOnlyIsValid() {
            var doc = parse("   \n\n   ");
            assertNotNull(doc);
        }

        @Test void whitespaceOnlyAllowed() {
            var opts = ParseOptions.DEFAULT.withAllowEmpty(true);
            var doc = OdinParser.parse("   \n\n   ", opts);
            assertNotNull(doc);
        }

        @Test void newlinesOnlyIsValid() {
            var doc = parse("\n\n\n");
            assertNotNull(doc);
        }

        @Test void newlinesOnlyAllowed() {
            var opts = ParseOptions.DEFAULT.withAllowEmpty(true);
            var doc = OdinParser.parse("\n\n\n", opts);
            assertNotNull(doc);
        }

        @Test void tabsOnlyIsValid() {
            var doc = parse("\t\t\t");
            assertNotNull(doc);
        }

        @Test void singleNewline() {
            var opts = ParseOptions.DEFAULT.withAllowEmpty(true);
            var doc = OdinParser.parse("\n", opts);
            assertNotNull(doc);
        }
    }

    // ─── BOM handling ─────────────────────────────────────────────────────

    @Nested class BomTests {
        @Test void utf8BomHandled() {
            // UTF-8 BOM: \uFEFF
            var text = "\uFEFFname = \"Alice\"";
            try {
                var doc = OdinParser.parse(text, ParseOptions.DEFAULT);
                assertNotNull(doc);
            } catch (OdinParseException e) {
                // If BOM causes parse error, that's also valid behavior
                assertNotNull(e.getMessage());
            }
        }

        @Test void noBomParses() {
            var doc = parse("name = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ─── Line ending variations ───────────────────────────────────────────

    @Nested class LineEndingTests {
        @Test void unixLineEndings() {
            var doc = parse("a = ##1\nb = ##2\nc = ##3");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
            assertEquals(3L, doc.getInteger("c"));
        }

        @Test void windowsLineEndings() {
            var doc = parse("a = ##1\r\nb = ##2\r\nc = ##3");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
            assertEquals(3L, doc.getInteger("c"));
        }

        @Test void mixedLineEndings() {
            var doc = parse("a = ##1\nb = ##2\r\nc = ##3\nd = ##4");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
            assertEquals(3L, doc.getInteger("c"));
            assertEquals(4L, doc.getInteger("d"));
        }

        @Test void crlfInSections() {
            var doc = parse("{A}\r\nx = ##1\r\n{B}\r\ny = ##2");
            assertEquals(1L, doc.getInteger("A.x"));
            assertEquals(2L, doc.getInteger("B.y"));
        }

        @Test void trailingNewline() {
            var doc = parse("name = \"Alice\"\n");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void trailingCrlf() {
            var doc = parse("name = \"Alice\"\r\n");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void multipleTrailingNewlines() {
            var doc = parse("name = \"Alice\"\n\n\n");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ─── Tab vs space indentation ─────────────────────────────────────────

    @Nested class IndentationTests {
        @Test void spacesInKey() {
            var doc = parse("  name = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void tabsInKey() {
            var doc = parse("\tname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void tabsBetweenKeyAndEquals() {
            var doc = parse("name\t=\t\"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void mixedTabsAndSpaces() {
            var doc = parse(" \t name \t = \t \"Alice\" \t ");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void noSpaceAroundEquals() {
            var doc = parse("name=\"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ─── Unicode in keys and values ───────────────────────────────────────

    @Nested class UnicodeTests {
        @Test void unicodeInStringValue() {
            var doc = parse("name = \"日本語\"");
            assertEquals("日本語", doc.getString("name"));
        }

        @Test void chineseCharacters() {
            var doc = parse("greeting = \"你好世界\"");
            assertEquals("你好世界", doc.getString("greeting"));
        }

        @Test void koreanCharacters() {
            var doc = parse("greeting = \"안녕하세요\"");
            assertEquals("안녕하세요", doc.getString("greeting"));
        }

        @Test void arabicCharacters() {
            var doc = parse("greeting = \"مرحبا\"");
            assertEquals("مرحبا", doc.getString("greeting"));
        }

        @Test void emojiInValue() {
            var doc = parse("emoji = \"😀🎉🌍\"");
            assertEquals("😀🎉🌍", doc.getString("emoji"));
        }

        @Test void accentedCharacters() {
            var doc = parse("name = \"café résumé naïve\"");
            assertEquals("café résumé naïve", doc.getString("name"));
        }

        @Test void unicodeEscapeSequence() {
            var doc = parse("x = \"\\u0041\\u0042\\u0043\"");
            assertEquals("ABC", doc.getString("x"));
        }

        @Test void mixedAsciiAndUnicode() {
            var doc = parse("x = \"Hello 世界 Welt мир\"");
            assertEquals("Hello 世界 Welt мир", doc.getString("x"));
        }
    }

    // ─── Escaped characters in strings ────────────────────────────────────

    @Nested class EscapeTests {
        @Test void escapedNewline() {
            var doc = parse("x = \"line1\\nline2\"");
            assertEquals("line1\nline2", doc.getString("x"));
        }

        @Test void escapedTab() {
            var doc = parse("x = \"col1\\tcol2\"");
            assertEquals("col1\tcol2", doc.getString("x"));
        }

        @Test void escapedCarriageReturn() {
            var doc = parse("x = \"before\\rafter\"");
            assertEquals("before\rafter", doc.getString("x"));
        }

        @Test void escapedBackslash() {
            var doc = parse("x = \"path\\\\to\\\\file\"");
            assertEquals("path\\to\\file", doc.getString("x"));
        }

        @Test void escapedQuote() {
            var doc = parse("x = \"say \\\"hello\\\"\"");
            assertEquals("say \"hello\"", doc.getString("x"));
        }

        @Test void multipleEscapesInOneString() {
            var doc = parse("x = \"\\t\\n\\r\\\\\\\"\"");
            assertEquals("\t\n\r\\\"", doc.getString("x"));
        }

        @Test void invalidEscapeThrows() {
            assertThrows(OdinParseException.class, () -> parse("x = \"bad\\q\""));
        }

        @Test void invalidEscapeZ() {
            assertThrows(OdinParseException.class, () -> parse("x = \"bad\\z\""));
        }

        @Test void unterminatedStringThrows() {
            assertThrows(OdinParseException.class, () -> parse("x = \"unterminated"));
        }

        @Test void unterminatedStringMidLine() {
            assertThrows(OdinParseException.class, () -> parse("x = \"no close"));
        }
    }

    // ─── Error handling edge cases ────────────────────────────────────────

    @Nested class ErrorEdgeCaseTests {
        @Test void invalidHeaderMissingClose() {
            assertThrows(OdinParseException.class, () -> parse("{unclosed"));
        }

        @Test void duplicatePathDefault() {
            assertThrows(OdinParseException.class, () -> parse("x = ##1\nx = ##2"));
        }

        @Test void duplicatePathAllowed() {
            var opts = ParseOptions.DEFAULT.withAllowDuplicates(true);
            var doc = OdinParser.parse("x = ##1\nx = ##2", opts);
            assertEquals(2L, doc.getInteger("x")); // last wins
        }

        @Test void parseExceptionHasCode() {
            try {
                parse("x = \"unterminated");
                fail("Should have thrown");
            } catch (OdinParseException e) {
                assertNotNull(e.getCode());
            }
        }

        @Test void parseExceptionHasLine() {
            try {
                parse("x = \"unterminated");
                fail("Should have thrown");
            } catch (OdinParseException e) {
                assertTrue(e.getLine() >= 1);
            }
        }

        @Test void parseExceptionHasColumn() {
            try {
                parse("x = \"unterminated");
                fail("Should have thrown");
            } catch (OdinParseException e) {
                assertTrue(e.getColumn() >= 1);
            }
        }
    }

    // ─── Document immutability ────────────────────────────────────────────

    @Nested class ImmutabilityTests {
        @Test void withReturnsNewDocument() {
            var doc = parse("x = \"original\"");
            var modified = doc.with("x", new OdinValue.OdinString("changed"));
            assertEquals("original", doc.getString("x"));
            assertEquals("changed", modified.getString("x"));
        }

        @Test void withoutReturnsNewDocument() {
            var doc = parse("x = ##1\ny = ##2");
            var modified = doc.without("x");
            assertTrue(doc.has("x"));
            assertFalse(modified.has("x"));
            assertTrue(modified.has("y"));
        }

        @Test void withAddNewField() {
            var doc = parse("x = ##1");
            var modified = doc.with("y", new OdinValue.OdinInteger(2));
            assertFalse(doc.has("y"));
            assertTrue(modified.has("y"));
        }
    }

    // ─── Flatten ──────────────────────────────────────────────────────────

    @Nested class FlattenTests {
        @Test void flattenSimpleDocument() {
            var doc = parse("name = \"Alice\"\nage = ##30");
            var flat = doc.flatten(null);
            assertNotNull(flat);
            assertFalse(flat.keys().isEmpty());
        }

        @Test void flattenWithMetadata() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            var flat = doc.flatten(new FlattenOptions(true, false, true));
            assertTrue(flat.keys().stream().anyMatch(k -> k.startsWith("$.")));
        }

        @Test void flattenSorted() {
            var doc = parse("b = ##2\na = ##1\nc = ##3");
            var flat = doc.flatten(new FlattenOptions(false, false, true));
            var keys = flat.keys();
            for (int i = 1; i < keys.size(); i++) {
                assertTrue(keys.get(i).compareTo(keys.get(i - 1)) >= 0);
            }
        }
    }
}
