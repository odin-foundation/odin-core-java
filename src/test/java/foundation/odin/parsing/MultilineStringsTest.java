package foundation.odin.parsing;

import foundation.odin.Odin;
import foundation.odin.types.OdinErrors.OdinParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Triple-quoted (""" ... """) multiline string literals: content is captured
// verbatim and may span newlines; an unterminated block is a P004 parse error.
class MultilineStringsTest {

    // ── Happy path ──

    @Nested
    class HappyPathTests {
        @Test
        void parsesBlockSpanningNewlines() {
            var doc = Odin.parse("field = \"\"\"hello\nworld\"\"\"");
            assertEquals("hello\nworld", doc.getString("field"));
        }

        @Test
        void parsesSingleLineTripleQuotedString() {
            var doc = Odin.parse("field = \"\"\"one line\"\"\"");
            assertEquals("one line", doc.getString("field"));
        }

        @Test
        void parsesEmptyTripleQuotedString() {
            var doc = Odin.parse("field = \"\"\"\"\"\"");
            assertEquals("", doc.getString("field"));
        }
    }

    // ── Edge cases ──

    @Nested
    class EdgeCaseTests {
        @Test
        void retainsLeadingAndTrailingNewlinesVerbatim() {
            var doc = Odin.parse("field = \"\"\"\ninner\n\"\"\"");
            assertEquals("\ninner\n", doc.getString("field"));
        }

        @Test
        void preservesInteriorBlankLines() {
            var doc = Odin.parse("field = \"\"\"a\n\nb\"\"\"");
            assertEquals("a\n\nb", doc.getString("field"));
        }

        @Test
        void keepsBackslashesAndEmbeddedQuotesVerbatim() {
            var doc = Odin.parse("field = \"\"\"C:\\path say \"hi\" done\"\"\"");
            assertEquals("C:\\path say \"hi\" done", doc.getString("field"));
        }

        @Test
        void keepsInterpolationMarkersVerbatimAtCoreLayer() {
            var doc = Odin.parse("field = \"\"\"value=${@x}\"\"\"");
            assertEquals("value=${@x}", doc.getString("field"));
        }

        @Test
        void doesNotTreatNormalQuotedStringAsMultiline() {
            var doc = Odin.parse("a = \"\"\nb = \"x\"");
            assertEquals("", doc.getString("a"));
            assertEquals("x", doc.getString("b"));
        }
    }

    // ── Error ──

    @Nested
    class ErrorTests {
        @Test
        void unterminatedBlockThrowsP004() {
            var ex = assertThrows(OdinParseException.class,
                    () -> Odin.parse("field = \"\"\"never closed\n"));
            assertEquals("P004", ex.getCode());
        }
    }
}
