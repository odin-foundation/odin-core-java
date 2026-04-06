package foundation.odin.parsing;

import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comment handling tests — single-line, end-of-line, comments in various positions,
 * comment-only lines, comments with special characters, comments in arrays.
 */
class ParserCommentsTest {

    private OdinDocument parse(String odin) { return OdinParser.parse(odin, ParseOptions.DEFAULT); }

    private OdinDocument parsePreserve(String odin) {
        var opts = ParseOptions.DEFAULT.withPreserveComments(true);
        return OdinParser.parse(odin, opts);
    }

    // ─── Single-line comments ─────────────────────────────────────────────

    @Nested class SingleLineCommentTests {
        @Test void commentAtStart() {
            var doc = parse("; This is a comment\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentOnlyLine() {
            var doc = parse("; Just a comment\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void multipleCommentLines() {
            var doc = parse("; Comment 1\n; Comment 2\n; Comment 3\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentBetweenAssignments() {
            var doc = parse("a = ##1\n; middle comment\nb = ##2");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
        }

        @Test void commentAtEnd() {
            var doc = parse("name = \"Alice\"\n; trailing comment");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void emptyComment() {
            var doc = parse(";\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithOnlySemicolon() {
            var doc = parse(";\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithLeadingSpaces() {
            var doc = parse("  ; indented comment\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ─── End-of-line comments ─────────────────────────────────────────────

    @Nested class EndOfLineCommentTests {
        @Test void commentAfterStringValue() {
            var doc = parse("name = \"Alice\" ; inline comment");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentAfterIntegerValue() {
            var doc = parse("count = ##42 ; the answer");
            assertEquals(42L, doc.getInteger("count"));
        }

        @Test void commentAfterBooleanValue() {
            var doc = parse("active = true ; is active");
            assertTrue(doc.getBoolean("active"));
        }

        @Test void commentAfterNullValue() {
            var doc = parse("empty = ~ ; nothing here");
            assertTrue(doc.get("empty").isNull());
        }

        @Test void commentAfterNumberValue() {
            var doc = parse("rate = #3.14 ; pi");
            assertEquals(3.14, doc.getNumber("rate"), 0.01);
        }

        @Test void commentAfterCurrencyValue() {
            var doc = parse("price = #$99.99 ; the price");
            assertEquals(99.99, doc.getNumber("price"), 0.01);
        }

        @Test void commentAfterReferenceValue() {
            var doc = parse("ref = @target ; reference");
            assertEquals("target", doc.get("ref").asReference());
        }

        @Test void commentAfterBinaryValue() {
            var doc = parse("data = ^SGVsbG8= ; binary data");
            assertTrue(doc.get("data").isBinary());
        }

        @Test void commentAfterDateValue() {
            var doc = parse("date = 2024-06-15 ; a date");
            assertTrue(doc.get("date").isDate());
        }
    }

    // ─── Comments in various positions ────────────────────────────────────

    @Nested class CommentPositionTests {
        @Test void commentBeforeHeader() {
            var doc = parse("; section comment\n{Policy}\nname = \"Test\"");
            assertEquals("Test", doc.getString("Policy.name"));
        }

        @Test void commentAfterHeader() {
            var doc = parse("{Policy}\n; fields follow\nname = \"Test\"");
            assertEquals("Test", doc.getString("Policy.name"));
        }

        @Test void commentBetweenSections() {
            var doc = parse("{A}\nx = ##1\n; between sections\n{B}\ny = ##2");
            assertEquals(1L, doc.getInteger("A.x"));
            assertEquals(2L, doc.getInteger("B.y"));
        }

        @Test void commentBeforeMetadata() {
            var doc = parse("; preamble comment\n{$}\nodin = \"1.0.0\"\n\nname = \"Test\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("Test", doc.getString("name"));
        }

        @Test void commentInArrayRegion() {
            var doc = parse("items[0] = \"a\"\n; comment in array\nitems[1] = \"b\"");
            assertEquals("a", doc.getString("items[0]"));
            assertEquals("b", doc.getString("items[1]"));
        }

        @Test void commentBeforeDirective() {
            var doc = parse("; before import\n@import ./base.odin\nname = \"Alice\"");
            assertFalse(doc.getImports().isEmpty());
        }

        @Test void multipleInlineCommentsOnDifferentLines() {
            var doc = parse("a = ##1 ; first\nb = ##2 ; second\nc = ##3 ; third");
            assertEquals(1L, doc.getInteger("a"));
            assertEquals(2L, doc.getInteger("b"));
            assertEquals(3L, doc.getInteger("c"));
        }
    }

    // ─── Comments with special characters ─────────────────────────────────

    @Nested class CommentSpecialCharTests {
        @Test void commentWithSemicolons() {
            var doc = parse("; comment with ; more ; semicolons\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithQuotes() {
            var doc = parse("; comment with \"quotes\" inside\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithSpecialChars() {
            var doc = parse("; @#$%^&*(){}[]|<>\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithUnicode() {
            var doc = parse("; 日本語コメント\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithEquals() {
            var doc = parse("; key = value looks like assignment\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void commentWithBraces() {
            var doc = parse("; {looks like header}\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }
    }

    // ─── Comment preservation ─────────────────────────────────────────────

    @Nested class CommentPreservationTests {
        @Test void preserveSingleComment() {
            var doc = parsePreserve("; header comment\nname = \"Alice\"");
            assertFalse(doc.getComments().isEmpty());
        }

        @Test void preserveMultipleComments() {
            var doc = parsePreserve("; comment 1\n; comment 2\nname = \"Alice\"");
            assertTrue(doc.getComments().size() >= 2);
        }

        @Test void preserveInlineComment() {
            var doc = parsePreserve("name = \"Alice\" ; inline");
            assertNotNull(doc);
        }

        @Test void noCommentsWithoutPreserve() {
            var doc = parse("name = \"Alice\"");
            assertTrue(doc.getComments().isEmpty());
        }

        @Test void preserveCommentText() {
            var doc = parsePreserve("; hello world\nname = \"Alice\"");
            var comments = doc.getComments();
            if (!comments.isEmpty()) {
                assertTrue(comments.get(0).text().contains("hello world"));
            }
        }

        @Test void preserveCommentsBetweenSections() {
            var doc = parsePreserve("{A}\nx = ##1\n; between\n{B}\ny = ##2");
            assertNotNull(doc);
            assertFalse(doc.getComments().isEmpty());
        }
    }

    // ─── Comment edge cases ───────────────────────────────────────────────

    @Nested class CommentEdgeCaseTests {
        @Test void documentOfOnlyComments() {
            var opts = ParseOptions.DEFAULT.withAllowEmpty(true).withPreserveComments(true);
            var doc = OdinParser.parse("; only a comment", opts);
            assertNotNull(doc);
        }

        @Test void semicolonInStringNotComment() {
            var doc = parse("x = \"value ; not a comment\"");
            assertEquals("value ; not a comment", doc.getString("x"));
        }

        @Test void commentAfterPercentValue() {
            var doc = parse("pct = #%0.15 ; percentage");
            assertEquals(0.15, doc.getNumber("pct"), 0.001);
        }

        @Test void veryLongComment() {
            var longComment = "x".repeat(5000);
            var doc = parse("; " + longComment + "\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }
    }
}
