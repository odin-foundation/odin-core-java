package foundation.odin.parsing;

import foundation.odin.types.OdinErrors;
import foundation.odin.types.OdinOptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    private static List<Token> tokenize(String source) {
        return Tokenizer.tokenize(source, OdinOptions.ParseOptions.DEFAULT);
    }

    private static List<Token> nonTrivialTokens(String source) {
        return tokenize(source).stream()
                .filter(t -> t.getTokenType() != TokenType.Newline &&
                             t.getTokenType() != TokenType.Comment &&
                             t.getTokenType() != TokenType.Eof)
                .collect(Collectors.toList());
    }

    // ── Basic Token Types ──

    @Nested
    class BasicTokenTypes {
        @Test void emptyStringReturnsOnlyEof() {
            var tokens = tokenize("");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Eof, tokens.get(0).getTokenType());
        }

        @Test void simpleAssignmentProducesPathEqualsString() {
            var tokens = nonTrivialTokens("name = \"Alice\"");
            assertEquals(3, tokens.size());
            assertEquals(TokenType.Path, tokens.get(0).getTokenType());
            assertEquals("name", tokens.get(0).getValue());
            assertEquals(TokenType.Equals, tokens.get(1).getTokenType());
            assertEquals(TokenType.QuotedString, tokens.get(2).getTokenType());
            assertEquals("Alice", tokens.get(2).getValue());
        }

        @Test void integerPrefixProducesIntegerToken() {
            var tokens = nonTrivialTokens("x = ##42");
            assertEquals(TokenType.IntegerPrefix, tokens.get(2).getTokenType());
            assertEquals("42", tokens.get(2).getValue());
        }

        @Test void numberPrefixProducesNumberToken() {
            var tokens = nonTrivialTokens("x = #3.14");
            assertEquals(TokenType.NumberPrefix, tokens.get(2).getTokenType());
            assertEquals("3.14", tokens.get(2).getValue());
        }

        @Test void currencyPrefixProducesCurrencyToken() {
            var tokens = nonTrivialTokens("x = #$99.99");
            assertEquals(TokenType.CurrencyPrefix, tokens.get(2).getTokenType());
            assertEquals("99.99", tokens.get(2).getValue());
        }

        @Test void currencyWithCodeIncludesCode() {
            var tokens = nonTrivialTokens("x = #$99.99:USD");
            assertEquals(TokenType.CurrencyPrefix, tokens.get(2).getTokenType());
            assertEquals("99.99:USD", tokens.get(2).getValue());
        }

        @Test void percentPrefixProducesPercentToken() {
            var tokens = nonTrivialTokens("x = #%50");
            assertEquals(TokenType.PercentPrefix, tokens.get(2).getTokenType());
            assertEquals("50", tokens.get(2).getValue());
        }

        @Test void booleanLiteralTrue() {
            var tokens = nonTrivialTokens("x = true");
            assertEquals(TokenType.BooleanLiteral, tokens.get(2).getTokenType());
            assertEquals("true", tokens.get(2).getValue());
        }

        @Test void booleanLiteralFalse() {
            var tokens = nonTrivialTokens("x = false");
            assertEquals(TokenType.BooleanLiteral, tokens.get(2).getTokenType());
            assertEquals("false", tokens.get(2).getValue());
        }

        @Test void nullValue() {
            var tokens = nonTrivialTokens("x = ~");
            assertEquals(TokenType.Null, tokens.get(2).getTokenType());
        }

        @Test void referenceValue() {
            var tokens = nonTrivialTokens("x = @other.path");
            assertEquals(TokenType.ReferencePrefix, tokens.get(2).getTokenType());
            assertEquals("other.path", tokens.get(2).getValue());
        }

        @Test void binaryPrefix() {
            var tokens = nonTrivialTokens("x = ^SGVsbG8=");
            assertEquals(TokenType.BinaryPrefix, tokens.get(2).getTokenType());
            assertEquals("SGVsbG8=", tokens.get(2).getValue());
        }
    }

    // ── Modifiers ──

    @Nested
    class Modifiers {
        @Test void requiredModifier() {
            var tokens = nonTrivialTokens("x = !\"val\"");
            assertEquals(TokenType.Modifier, tokens.get(2).getTokenType());
            assertEquals("!", tokens.get(2).getValue());
        }

        @Test void confidentialModifier() {
            var tokens = nonTrivialTokens("x = *\"val\"");
            assertEquals(TokenType.Modifier, tokens.get(2).getTokenType());
            assertEquals("*", tokens.get(2).getValue());
        }

        @Test void deprecatedModifier() {
            var tokens = nonTrivialTokens("x = -\"val\"");
            assertEquals(TokenType.Modifier, tokens.get(2).getTokenType());
            assertEquals("-", tokens.get(2).getValue());
        }

        @Test void allModifiersInOrder() {
            var tokens = nonTrivialTokens("x = !-*\"val\"");
            assertEquals(TokenType.Modifier, tokens.get(2).getTokenType());
            assertEquals("!", tokens.get(2).getValue());
            assertEquals(TokenType.Modifier, tokens.get(3).getTokenType());
            assertEquals("-", tokens.get(3).getValue());
            assertEquals(TokenType.Modifier, tokens.get(4).getTokenType());
            assertEquals("*", tokens.get(4).getValue());
            assertEquals(TokenType.QuotedString, tokens.get(5).getTokenType());
        }
    }

    // ── Headers ──

    @Nested
    class Headers {
        @Test void metadataHeader() {
            var tokens = nonTrivialTokens("{$}");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Header, tokens.get(0).getTokenType());
            assertEquals("$", tokens.get(0).getValue());
        }

        @Test void sectionHeader() {
            var tokens = nonTrivialTokens("{Customer}");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Header, tokens.get(0).getTokenType());
            assertEquals("Customer", tokens.get(0).getValue());
        }

        @Test void typeDefinitionHeader() {
            var tokens = nonTrivialTokens("{@Person}");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Header, tokens.get(0).getTokenType());
            assertEquals("@Person", tokens.get(0).getValue());
        }

        @Test void emptyHeader() {
            var tokens = nonTrivialTokens("{}");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Header, tokens.get(0).getTokenType());
            assertEquals("", tokens.get(0).getValue());
        }

        @Test void unterminatedHeaderThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("{Customer"));
            assertEquals(OdinErrors.ParseErrorCode.InvalidHeaderSyntax, ex.getErrorCode());
        }

        @Test void headerWithNewlineThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("{Cus\ntomer}"));
            assertEquals(OdinErrors.ParseErrorCode.InvalidHeaderSyntax, ex.getErrorCode());
        }
    }

    // ── Comments ──

    @Nested
    class Comments {
        @Test void comment() {
            var tokens = tokenize("; this is a comment");
            assertEquals(TokenType.Comment, tokens.get(0).getTokenType());
            assertTrue(tokens.get(0).getValue().contains("this is a comment"));
        }

        @Test void inlineCommentSeparateToken() {
            var tokens = tokenize("x = ##42 ; comment");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.Comment));
        }
    }

    // ── Strings ──

    @Nested
    class Strings {
        @Test void emptyString() {
            var tokens = nonTrivialTokens("x = \"\"");
            assertEquals(TokenType.QuotedString, tokens.get(2).getTokenType());
            assertEquals("", tokens.get(2).getValue());
        }

        @Test void escapedQuotes() {
            var tokens = nonTrivialTokens("x = \"say \\\"hello\\\"\"");
            assertEquals("say \"hello\"", tokens.get(2).getValue());
        }

        @Test void escapedBackslash() {
            var tokens = nonTrivialTokens("x = \"C:\\\\path\"");
            assertEquals("C:\\path", tokens.get(2).getValue());
        }

        @Test void escapedNewline() {
            var tokens = nonTrivialTokens("x = \"a\\nb\"");
            assertEquals("a\nb", tokens.get(2).getValue());
        }

        @Test void escapedTab() {
            var tokens = nonTrivialTokens("x = \"a\\tb\"");
            assertEquals("a\tb", tokens.get(2).getValue());
        }

        @Test void escapedCarriageReturn() {
            var tokens = nonTrivialTokens("x = \"a\\rb\"");
            assertEquals("a\rb", tokens.get(2).getValue());
        }

        @Test void escapedForwardSlash() {
            var tokens = nonTrivialTokens("x = \"a\\/b\"");
            assertEquals("a/b", tokens.get(2).getValue());
        }

        @Test void escapedNull() {
            var tokens = nonTrivialTokens("x = \"a\\0b\"");
            assertEquals("a\0b", tokens.get(2).getValue());
        }

        @Test void unicodeEscape4Digit() {
            var tokens = nonTrivialTokens("x = \"\\u0041\"");
            assertEquals("A", tokens.get(2).getValue());
        }

        @Test void unicodeEscape8Digit() {
            var tokens = nonTrivialTokens("x = \"\\U0001F600\"");
            assertEquals("\uD83D\uDE00", tokens.get(2).getValue()); // grinning face emoji
        }

        @Test void unterminatedStringThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = \"unterminated"));
            assertEquals(OdinErrors.ParseErrorCode.UnterminatedString, ex.getErrorCode());
        }

        @Test void stringWithNewlineThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = \"line1\nline2\""));
            assertEquals(OdinErrors.ParseErrorCode.UnterminatedString, ex.getErrorCode());
        }

        @Test void invalidEscapeSequenceThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = \"\\q\""));
            assertEquals(OdinErrors.ParseErrorCode.InvalidEscapeSequence, ex.getErrorCode());
        }

        @Test void incompleteUnicodeEscapeThrows() {
            assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = \"\\u00\""));
        }

        @Test void surrogatePairEscape() {
            var tokens = nonTrivialTokens("x = \"\\uD83C\\uDF0D\"");
            assertEquals("\uD83C\uDF0D", tokens.get(2).getValue()); // Earth Globe
        }
    }

    // ── Dates and Timestamps ──

    @Nested
    class DatesAndTimestamps {
        @Test void dateLiteral() {
            var tokens = nonTrivialTokens("x = 2024-06-15");
            assertEquals(TokenType.DateLiteral, tokens.get(2).getTokenType());
            assertEquals("2024-06-15", tokens.get(2).getValue());
        }

        @Test void timestampLiteral() {
            var tokens = nonTrivialTokens("x = 2024-06-15T14:30:00Z");
            assertEquals(TokenType.TimestampLiteral, tokens.get(2).getTokenType());
            assertEquals("2024-06-15T14:30:00Z", tokens.get(2).getValue());
        }

        @Test void timeLiteral() {
            var tokens = nonTrivialTokens("x = T14:30:00");
            assertEquals(TokenType.TimeLiteral, tokens.get(2).getTokenType());
        }

        @Test void durationLiteral() {
            var tokens = nonTrivialTokens("x = P1Y6M");
            assertEquals(TokenType.DurationLiteral, tokens.get(2).getTokenType());
            assertEquals("P1Y6M", tokens.get(2).getValue());
        }

        @Test void durationWithTime() {
            var tokens = nonTrivialTokens("x = PT2H30M");
            assertEquals(TokenType.DurationLiteral, tokens.get(2).getTokenType());
            assertEquals("PT2H30M", tokens.get(2).getValue());
        }
    }

    // ── Document Separator ──

    @Nested
    class DocumentSeparator {
        @Test void documentSeparator() {
            var tokens = nonTrivialTokens("---");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.DocumentSeparator, tokens.get(0).getTokenType());
            assertEquals("---", tokens.get(0).getValue());
        }

        @Test void documentSeparatorBetween() {
            var tokens = tokenize("a = ##1\n---\nb = ##2");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.DocumentSeparator));
        }
    }

    // ── Directives ──

    @Nested
    class Directives {
        @Test void directive() {
            var tokens = nonTrivialTokens("x = ##42 :required");
            assertTrue(tokens.stream().anyMatch(t ->
                    t.getTokenType() == TokenType.Directive && t.getValue().equals("required")));
        }

        @Test void multipleDirectives() {
            var tokens = nonTrivialTokens("x = \"val\" :required :confidential");
            long count = tokens.stream().filter(t -> t.getTokenType() == TokenType.Directive).count();
            assertEquals(2, count);
        }
    }

    // ── Verb Prefix ──

    @Nested
    class VerbPrefixTests {
        @Test void verbPrefix() {
            var tokens = nonTrivialTokens("x = %upper");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.VerbPrefix));
        }

        @Test void customVerbPrefix() {
            var tokens = nonTrivialTokens("x = %&myverb");
            var verb = tokens.stream().filter(t -> t.getTokenType() == TokenType.VerbPrefix).findFirst().orElseThrow();
            assertTrue(verb.getValue().startsWith("&"));
        }
    }

    // ── Imports and Schema Directives ──

    @Nested
    class ImportSchemaDirectives {
        @Test void importDirective() {
            var tokens = tokenize("@import \"types.odin\"");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.Import));
        }

        @Test void schemaDirective() {
            var tokens = tokenize("@schema \"schema.odin\"");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.Schema));
        }

        @Test void conditionalDirective() {
            var tokens = tokenize("@if status == \"active\"");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.Conditional));
        }
    }

    // ── Token Position Tracking ──

    @Nested
    class PositionTracking {
        @Test void hasCorrectLineNumber() {
            var tokens = tokenize("a = ##1\nb = ##2");
            var bToken = tokens.stream()
                    .filter(t -> t.getTokenType() == TokenType.Path && t.getValue().equals("b"))
                    .findFirst().orElseThrow();
            assertEquals(2, bToken.getLine());
        }

        @Test void hasCorrectColumn() {
            var tokens = tokenize("name = \"Alice\"");
            assertEquals(1, tokens.get(0).getColumn());
        }

        @Test void startEndOffsets() {
            var tokens = tokenize("x = ##42");
            var path = tokens.stream().filter(t -> t.getTokenType() == TokenType.Path).findFirst().orElseThrow();
            assertEquals(0, path.getStart());
            assertTrue(path.getEnd() > path.getStart());
        }
    }

    // ── Hash Error Handling ──

    @Nested
    class HashErrors {
        @Test void bareHashThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = #"));
            assertEquals(OdinErrors.ParseErrorCode.InvalidTypePrefix, ex.getErrorCode());
        }

        @Test void hashWithInvalidFollowerThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("x = #abc"));
            assertEquals(OdinErrors.ParseErrorCode.InvalidTypePrefix, ex.getErrorCode());
        }
    }

    // ── Whitespace Handling ──

    @Nested
    class WhitespaceHandling {
        @Test void tabsSkipped() {
            var tokens = nonTrivialTokens("\tx = ##42");
            assertEquals(TokenType.Path, tokens.get(0).getTokenType());
        }

        @Test void spacesSkipped() {
            var tokens = nonTrivialTokens("   x = ##42");
            assertEquals(TokenType.Path, tokens.get(0).getTokenType());
        }

        @Test void crLfProducesNewline() {
            var tokens = tokenize("x = ##1\r\ny = ##2");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.Newline));
        }
    }

    // ── Negative Numbers ──

    @Nested
    class NegativeNumbers {
        @Test void negativeInteger() {
            var tokens = nonTrivialTokens("x = ##-42");
            assertEquals(TokenType.IntegerPrefix, tokens.get(2).getTokenType());
            assertEquals("-42", tokens.get(2).getValue());
        }

        @Test void negativeNumber() {
            var tokens = nonTrivialTokens("x = #-3.14");
            assertEquals(TokenType.NumberPrefix, tokens.get(2).getTokenType());
            assertEquals("-3.14", tokens.get(2).getValue());
        }
    }

    // ── Size Limits ──

    @Nested
    class SizeLimits {
        @Test void exceedsMaxDocumentSizeThrows() {
            var opts = OdinOptions.ParseOptions.DEFAULT.withMaxDocumentSize(10);
            var ex = assertThrows(OdinErrors.OdinParseException.class,
                    () -> Tokenizer.tokenize("x = \"a very long value\"", opts));
            assertEquals(OdinErrors.ParseErrorCode.MaximumDocumentSizeExceeded, ex.getErrorCode());
        }

        @Test void nullSourceThrows() {
            assertThrows(NullPointerException.class,
                    () -> Tokenizer.tokenize(null, OdinOptions.ParseOptions.DEFAULT));
        }

        @Test void nullOptionsThrows() {
            assertThrows(NullPointerException.class,
                    () -> Tokenizer.tokenize("x = ##1", null));
        }
    }

    // ── Pipe and Comma ──

    @Nested
    class PipeAndComma {
        @Test void pipe() {
            var tokens = nonTrivialTokens("|");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Pipe, tokens.get(0).getTokenType());
        }

        @Test void comma() {
            var tokens = nonTrivialTokens(",");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Comma, tokens.get(0).getTokenType());
        }
    }

    // ── Boolean Prefix ──

    @Nested
    class BooleanPrefixTests {
        @Test void booleanPrefix() {
            var tokens = nonTrivialTokens("x = ?true");
            assertTrue(tokens.stream().anyMatch(t -> t.getTokenType() == TokenType.BooleanPrefix));
        }
    }

    // ── Path with Array Index ──

    @Nested
    class PathArrayIndex {
        @Test void pathWithArrayIndex() {
            var tokens = nonTrivialTokens("items[0] = \"a\"");
            assertEquals(TokenType.Path, tokens.get(0).getTokenType());
            assertEquals("items[0]", tokens.get(0).getValue());
        }

        @Test void pathWithDot() {
            var tokens = nonTrivialTokens("section.field = \"val\"");
            assertEquals(TokenType.Path, tokens.get(0).getTokenType());
            assertEquals("section.field", tokens.get(0).getValue());
        }

        @Test void negativeArrayIndexThrows() {
            var ex = assertThrows(OdinErrors.OdinParseException.class, () -> tokenize("items[-1] = \"a\""));
            assertEquals(OdinErrors.ParseErrorCode.InvalidArrayIndex, ex.getErrorCode());
        }
    }

    // ── Multiple Lines ──

    @Nested
    class MultipleLines {
        @Test void multipleLinesAllTokenized() {
            var tokens = tokenize("a = ##1\nb = ##2\nc = ##3");
            long pathCount = tokens.stream().filter(t -> t.getTokenType() == TokenType.Path).count();
            assertEquals(3, pathCount);
        }

        @Test void blankLinesProduceNewlineTokens() {
            var tokens = tokenize("a = ##1\n\n\nb = ##2");
            long newlines = tokens.stream().filter(t -> t.getTokenType() == TokenType.Newline).count();
            assertTrue(newlines >= 2);
        }
    }

    // ── Edge Cases ──

    @Nested
    class EdgeCases {
        @Test void onlyWhitespaceReturnsEof() {
            var tokens = tokenize("   \t  ");
            assertEquals(1, tokens.size());
            assertEquals(TokenType.Eof, tokens.get(0).getTokenType());
        }

        @Test void onlyNewlines() {
            var tokens = tokenize("\n\n\n");
            assertTrue(tokens.size() >= 2);
            assertEquals(TokenType.Eof, tokens.get(tokens.size() - 1).getTokenType());
        }

        @Test void trailingWhitespaceAfterValue() {
            var tokens = nonTrivialTokens("x = ##42   ");
            assertEquals(3, tokens.size());
        }

        @Test void bareAtIsReferencePrefix() {
            var tokens = tokenize("x = @");
            var refTok = tokens.stream()
                    .filter(t -> t.getTokenType() == TokenType.ReferencePrefix)
                    .findFirst().orElse(null);
            assertNotNull(refTok);
            assertEquals("", refTok.getValue());
        }

        @Test void headerWithArrayBrackets() {
            var tokens = nonTrivialTokens("{$table.data[code, name]}");
            assertEquals(TokenType.Header, tokens.get(0).getTokenType());
        }

        @Test void numberWithScientificNotation() {
            var tokens = nonTrivialTokens("x = #1.5e10");
            assertEquals(TokenType.NumberPrefix, tokens.get(2).getTokenType());
            assertEquals("1.5e10", tokens.get(2).getValue());
        }

        @Test void negativeCurrency() {
            var tokens = nonTrivialTokens("x = #$-100.00");
            assertEquals(TokenType.CurrencyPrefix, tokens.get(2).getTokenType());
        }

        @Test void negativePercent() {
            var tokens = nonTrivialTokens("x = #%-5.5");
            assertEquals(TokenType.PercentPrefix, tokens.get(2).getTokenType());
        }

        @Test void longStringNoTruncation() {
            var longStr = "a".repeat(5000);
            var tokens = nonTrivialTokens("x = \"" + longStr + "\"");
            assertEquals(longStr, tokens.get(2).getValue());
        }

        @Test void tokenToStringIncludesTypeAndValue() {
            var token = new Token(TokenType.Path, 0, 4, 1, 1, "name");
            var str = token.toString();
            assertTrue(str.contains("Path"));
            assertTrue(str.contains("name"));
        }

        @Test void tokenToStringIncludesPosition() {
            var token = new Token(TokenType.Path, 0, 4, 3, 5, "x");
            var str = token.toString();
            assertTrue(str.contains("3"));
            assertTrue(str.contains("5"));
        }
    }
}
