package foundation.odin.parsing;

import foundation.odin.types.OdinErrors;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinOptions;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValueParserTest {

    private static List<Token> tokensOf(String source) {
        return Tokenizer.tokenize(source, OdinOptions.ParseOptions.DEFAULT);
    }

    private static OdinValue parseValueAt(List<Token> tokens, int pos) {
        return ValueParser.parseValue(tokens, pos).value();
    }

    private static int consumedAt(List<Token> tokens, int pos) {
        return ValueParser.parseValue(tokens, pos).consumed();
    }

    // Find the first token index after "=" in "x = <value>"
    private static int valueStart(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getTokenType() == TokenType.Equals) return i + 1;
        }
        throw new IllegalArgumentException("No = found");
    }

    private static OdinValue parseAssignment(String source) {
        var tokens = tokensOf(source);
        return parseValueAt(tokens, valueStart(tokens));
    }

    // ── Null ──

    @Nested
    class NullValues {
        @Test void nullValue() {
            var v = parseAssignment("x = ~");
            assertTrue(v.isNull());
        }

        @Test void nullConsumesOneToken() {
            var tokens = tokensOf("x = ~");
            assertEquals(1, consumedAt(tokens, valueStart(tokens)));
        }
    }

    // ── Boolean ──

    @Nested
    class BooleanValues {
        @Test void trueLiteral() {
            var v = parseAssignment("x = true");
            assertTrue(v.isBoolean());
            assertEquals(true, v.asBool());
        }

        @Test void falseLiteral() {
            var v = parseAssignment("x = false");
            assertEquals(false, v.asBool());
        }

        @Test void booleanPrefixTrue() {
            var v = parseAssignment("x = ?true");
            assertTrue(v.isBoolean());
            assertEquals(true, v.asBool());
        }

        @Test void booleanPrefixFalse() {
            var v = parseAssignment("x = ?false");
            assertEquals(false, v.asBool());
        }

        @Test void booleanPrefixAloneIsTrue() {
            // Bare ? at end-of-line (next token is Newline/Eof)
            var tokens = tokensOf("x = ?");
            var v = parseValueAt(tokens, valueStart(tokens));
            assertEquals(true, v.asBool());
        }
    }

    // ── String ──

    @Nested
    class StringValues {
        @Test void quotedString() {
            var v = parseAssignment("x = \"hello\"");
            assertTrue(v.isString());
            assertEquals("hello", v.asString());
        }

        @Test void emptyString() {
            var v = parseAssignment("x = \"\"");
            assertEquals("", v.asString());
        }
    }

    // ── Number ──

    @Nested
    class NumberValues {
        @Test void simpleNumber() {
            var v = parseAssignment("x = #3.14");
            assertTrue(v.isNumber());
            assertEquals(3.14, v.asDouble(), 0.001);
        }

        @Test void wholeNumber() {
            var v = parseAssignment("x = #42");
            assertEquals(42.0, v.asDouble(), 0.001);
        }

        @Test void negativeNumber() {
            var v = parseAssignment("x = #-1.5");
            assertEquals(-1.5, v.asDouble(), 0.001);
        }

        @Test void scientificNotation() {
            var v = parseAssignment("x = #1.5e10");
            assertEquals(1.5e10, v.asDouble(), 1.0);
        }

        @Test void emptyNumberThrows() {
            // This would require a token with empty value — construct manually
            var tokens = List.of(
                    new Token(TokenType.NumberPrefix, 0, 1, 1, 1, ""),
                    new Token(TokenType.Eof, 1, 1, 1, 2, ""));
            assertThrows(OdinErrors.OdinParseException.class, () -> ValueParser.parseValue(tokens, 0));
        }
    }

    // ── Integer ──

    @Nested
    class IntegerValues {
        @Test void simpleInteger() {
            var v = parseAssignment("x = ##42");
            assertTrue(v.isInteger());
            assertEquals(42L, v.asInt64());
        }

        @Test void negativeInteger() {
            var v = parseAssignment("x = ##-5");
            assertEquals(-5L, v.asInt64());
        }

        @Test void zeroInteger() {
            var v = parseAssignment("x = ##0");
            assertEquals(0L, v.asInt64());
        }
    }

    // ── Currency ──

    @Nested
    class CurrencyValues {
        @Test void simpleCurrency() {
            var v = parseAssignment("x = #$99.99");
            assertTrue(v.isCurrency());
            assertEquals(99.99, v.asDouble(), 0.001);
        }

        @Test void currencyWithCode() {
            var v = parseAssignment("x = #$100.00:USD");
            assertTrue(v instanceof OdinValue.OdinCurrency);
            assertEquals("USD", ((OdinValue.OdinCurrency) v).getCurrencyCode());
        }

        @Test void currencyDecimalPlaces() {
            var v = parseAssignment("x = #$100.00");
            assertTrue(v instanceof OdinValue.OdinCurrency);
            assertEquals((byte) 2, ((OdinValue.OdinCurrency) v).getDecimalPlaces());
        }
    }

    // ── Percent ──

    @Nested
    class PercentValues {
        @Test void simplePercent() {
            var v = parseAssignment("x = #%0.15");
            assertTrue(v.isPercent());
            assertEquals(0.15, v.asDouble(), 0.001);
        }
    }

    // ── Reference ──

    @Nested
    class ReferenceValues {
        @Test void simpleReference() {
            var v = parseAssignment("x = @target");
            assertTrue(v.isReference());
            assertEquals("target", v.asReference());
        }

        @Test void dottedReference() {
            var v = parseAssignment("x = @person.name");
            assertEquals("person.name", v.asReference());
        }
    }

    // ── Binary ──

    @Nested
    class BinaryValues {
        @Test void base64Binary() {
            var v = parseAssignment("x = ^SGVsbG8=");
            assertTrue(v.isBinary());
        }

        @Test void emptyBinary() {
            var tokens = List.of(
                    new Token(TokenType.BinaryPrefix, 0, 1, 1, 1, ""),
                    new Token(TokenType.Eof, 1, 1, 1, 2, ""));
            var v = ValueParser.parseValue(tokens, 0).value();
            assertTrue(v.isBinary());
        }
    }

    // ── Date ──

    @Nested
    class DateValues {
        @Test void simpleDate() {
            var v = parseAssignment("x = 2024-06-15");
            assertTrue(v.isDate());
        }

        @Test void dateComponents() {
            var v = parseAssignment("x = 2024-12-31");
            assertTrue(v instanceof OdinValue.OdinDate);
            var d = (OdinValue.OdinDate) v;
            assertEquals(2024, d.getYear());
            assertEquals(12, d.getMonth());
            assertEquals(31, d.getDay());
        }

        @Test void invalidMonthThrows() {
            var tokens = List.of(
                    new Token(TokenType.DateLiteral, 0, 10, 1, 1, "2024-13-01"),
                    new Token(TokenType.Eof, 10, 10, 1, 11, ""));
            assertThrows(OdinErrors.OdinParseException.class, () -> ValueParser.parseValue(tokens, 0));
        }

        @Test void invalidDayThrows() {
            var tokens = List.of(
                    new Token(TokenType.DateLiteral, 0, 10, 1, 1, "2024-02-30"),
                    new Token(TokenType.Eof, 10, 10, 1, 11, ""));
            assertThrows(OdinErrors.OdinParseException.class, () -> ValueParser.parseValue(tokens, 0));
        }

        @Test void leapYearFeb29() {
            var tokens = List.of(
                    new Token(TokenType.DateLiteral, 0, 10, 1, 1, "2024-02-29"),
                    new Token(TokenType.Eof, 10, 10, 1, 11, ""));
            var v = ValueParser.parseValue(tokens, 0).value();
            assertTrue(v.isDate());
        }
    }

    // ── Timestamp ──

    @Nested
    class TimestampValues {
        @Test void simpleTimestamp() {
            var v = parseAssignment("x = 2024-06-15T14:30:00Z");
            assertTrue(v.isTimestamp());
        }
    }

    // ── Time ──

    @Nested
    class TimeValues {
        @Test void simpleTime() {
            var v = parseAssignment("x = T14:30:00");
            assertTrue(v.isTime());
        }
    }

    // ── Duration ──

    @Nested
    class DurationValues {
        @Test void simpleDuration() {
            var v = parseAssignment("x = P1Y6M");
            assertTrue(v.isDuration());
        }

        @Test void durationWithTime() {
            var v = parseAssignment("x = PT2H30M");
            assertTrue(v.isDuration());
        }
    }

    // ── Verb ──

    @Nested
    class VerbValues {
        @Test void simpleVerb() {
            var v = parseAssignment("x = %upper");
            assertTrue(v.isVerb());
        }

        @Test void customVerb() {
            var v = parseAssignment("x = %&myverb");
            assertTrue(v.isVerb());
        }
    }

    // ── Modifiers ──

    @Nested
    class ModifierParsing {
        @Test void requiredModifier() {
            var tokens = tokensOf("x = !\"val\"");
            int pos = valueStart(tokens);
            var result = ValueParser.parseModifiers(tokens, pos);
            assertTrue(result.modifiers().isRequired());
            assertFalse(result.modifiers().isConfidential());
            assertEquals(1, result.consumed());
        }

        @Test void confidentialModifier() {
            var tokens = tokensOf("x = *\"val\"");
            int pos = valueStart(tokens);
            var result = ValueParser.parseModifiers(tokens, pos);
            assertTrue(result.modifiers().isConfidential());
        }

        @Test void deprecatedModifier() {
            var tokens = tokensOf("x = -\"val\"");
            int pos = valueStart(tokens);
            var result = ValueParser.parseModifiers(tokens, pos);
            assertTrue(result.modifiers().isDeprecated());
        }

        @Test void allModifiers() {
            var tokens = tokensOf("x = !-*\"val\"");
            int pos = valueStart(tokens);
            var result = ValueParser.parseModifiers(tokens, pos);
            assertTrue(result.modifiers().isRequired());
            assertTrue(result.modifiers().isDeprecated());
            assertTrue(result.modifiers().isConfidential());
            assertEquals(3, result.consumed());
        }

        @Test void noModifiers() {
            var tokens = tokensOf("x = \"val\"");
            int pos = valueStart(tokens);
            var result = ValueParser.parseModifiers(tokens, pos);
            assertEquals(OdinModifiers.EMPTY, result.modifiers());
            assertEquals(0, result.consumed());
        }
    }

    // ── Error Cases ──

    @Nested
    class ErrorCases {
        @Test void positionPastEndThrows() {
            var tokens = tokensOf("x = ##42");
            assertThrows(OdinErrors.OdinParseException.class,
                    () -> ValueParser.parseValue(tokens, tokens.size()));
        }

        @Test void bareWordThrows() {
            var tokens = List.of(
                    new Token(TokenType.BareWord, 0, 5, 1, 1, "hello"),
                    new Token(TokenType.Eof, 5, 5, 1, 6, ""));
            assertThrows(OdinErrors.OdinParseException.class,
                    () -> ValueParser.parseValue(tokens, 0));
        }
    }
}
