package foundation.odin.parsing;

import foundation.odin.Odin;
import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemporalValidationTest {

    private static OdinValue parseValue(String odin) {
        return Odin.parse(odin).get("x");
    }

    private static OdinParseException expectError(String odin) {
        return assertThrows(OdinParseException.class, () -> Odin.parse(odin));
    }

    // ── Timestamp: happy ──

    @Nested
    class TimestampHappy {
        @Test void validComponents() {
            var v = parseValue("x = 2024-06-15T23:59:59Z");
            assertTrue(v.isTimestamp());
        }

        @Test void leapSecondAccepted() {
            var v = parseValue("x = 2016-12-31T23:59:60Z");
            assertTrue(v.isTimestamp());
        }

        @Test void offsetAccepted() {
            var v = parseValue("x = 2024-06-15T10:30:00+05:30");
            assertTrue(v.isTimestamp());
        }

        @Test void noSecondsAccepted() {
            var v = parseValue("x = 2024-06-15T10:30Z");
            assertTrue(v.isTimestamp());
        }

        @Test void fractionalSecondsStrippedBeforeBounds() {
            var v = parseValue("x = 2024-06-15T10:30:59.250Z");
            assertTrue(v.isTimestamp());
        }
    }

    // ── Timestamp: error (all P001) ──

    @Nested
    class TimestampError {
        @Test void badDatePortion() {
            assertEquals("P001", expectError("x = 2024-13-40T10:30:00Z").getCode());
        }

        @Test void badHour() {
            assertEquals("P001", expectError("x = 2024-06-15T25:30:00Z").getCode());
        }

        @Test void badMinute() {
            assertEquals("P001", expectError("x = 2024-06-15T10:61:00Z").getCode());
        }

        @Test void badSecond() {
            assertEquals("P001", expectError("x = 2024-06-15T10:30:61Z").getCode());
        }

        @Test void badOffsetHour() {
            assertEquals("P001", expectError("x = 2024-06-15T10:30:00+25:00").getCode());
        }

        @Test void badOffsetMinute() {
            assertEquals("P001", expectError("x = 2024-06-15T10:30:00+05:99").getCode());
        }

        @Test void fullyMalformed() {
            assertEquals("P001", expectError("x = 2024-13-40T99:99:99Z").getCode());
        }
    }

    // ── Time: happy ──

    @Nested
    class TimeHappy {
        @Test void valid() {
            var v = parseValue("x = T14:30:00");
            assertTrue(v.isTime());
        }

        @Test void noSeconds() {
            var v = parseValue("x = T14:30");
            assertTrue(v.isTime());
        }

        @Test void endOfDayMidnight() {
            var v = parseValue("x = T24:00:00");
            assertTrue(v.isTime());
        }

        @Test void leapSecond() {
            var v = parseValue("x = T23:59:60");
            assertTrue(v.isTime());
        }

        @Test void fractionalSecondsStripped() {
            var v = parseValue("x = T23:59:59.999");
            assertTrue(v.isTime());
        }
    }

    // ── Time: error (all P001) ──

    @Nested
    class TimeError {
        @Test void badHour() {
            assertEquals("P001", expectError("x = T25:00:00").getCode());
        }

        @Test void hour24NonZeroMinute() {
            assertEquals("P001", expectError("x = T24:30:00").getCode());
        }

        @Test void hour24NonZeroSecond() {
            assertEquals("P001", expectError("x = T24:00:30").getCode());
        }

        @Test void badMinute() {
            assertEquals("P001", expectError("x = T14:61:00").getCode());
        }

        @Test void badSecond() {
            assertEquals("P001", expectError("x = T14:30:61").getCode());
        }
    }

    // ── Edge: boundaries that must stay valid ──

    @Nested
    class Boundaries {
        @Test void hour23MinuteSecond59Valid() {
            assertTrue(parseValue("x = T23:59:59").isTime());
        }

        @Test void offsetZeroValid() {
            assertTrue(parseValue("x = 2024-06-15T10:30:00+00:00").isTimestamp());
        }

        @Test void offsetMax2359Valid() {
            assertTrue(parseValue("x = 2024-06-15T10:30:00-23:59").isTimestamp());
        }
    }
}
