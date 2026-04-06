package foundation.odin.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    // ── ParseDate ──

    @Nested
    class ParseDateTests {
        @Test void validDate() {
            var result = DateUtils.parseDate("2024-06-15");
            assertNotNull(result);
            assertEquals(2024, result.year());
            assertEquals(6, result.month());
            assertEquals(15, result.day());
        }

        @Test void invalidFormatReturnsNull() {
            assertNull(DateUtils.parseDate("2024"));
            assertNull(DateUtils.parseDate("not-date"));
        }

        @Test void tooShortReturnsNull() {
            assertNull(DateUtils.parseDate("24-06-15"));
        }

        @Test void invalidMonthReturnsNull() {
            assertNull(DateUtils.parseDate("2024-13-01"));
            assertNull(DateUtils.parseDate("2024-00-01"));
        }

        @Test void invalidDayReturnsNull() {
            assertNull(DateUtils.parseDate("2024-01-00"));
        }
    }

    // ── ParseTimestampToEpochMs ──

    @Nested
    class ParseTimestampTests {
        @Test void unixEpoch() {
            var result = DateUtils.parseTimestampToEpochMs("1970-01-01T00:00:00Z");
            assertNotNull(result);
            assertEquals(0L, result);
        }

        @Test void knownTimestamp() {
            var result = DateUtils.parseTimestampToEpochMs("2024-01-01T00:00:00Z");
            assertNotNull(result);
            assertTrue(result > 0);
        }

        @Test void invalidReturnsNull() {
            assertNull(DateUtils.parseTimestampToEpochMs("not-a-timestamp"));
        }
    }

    // ── EpochMsToTimestamp ──

    @Nested
    class EpochMsToTimestampTests {
        @Test void zero() {
            var result = DateUtils.epochMsToTimestamp(0);
            assertEquals("1970-01-01T00:00:00.000Z", result);
        }

        @Test void roundTrip() {
            var epochMs = DateUtils.parseTimestampToEpochMs("2024-06-15T14:30:00Z");
            assertNotNull(epochMs);
            var back = DateUtils.epochMsToTimestamp(epochMs);
            assertTrue(back.contains("2024-06-15"));
            assertTrue(back.contains("14:30:00"));
        }
    }

    // ── AddDays ──

    @Nested
    class AddDaysTests {
        @Test void positive() {
            assertEquals("2024-01-11", DateUtils.addDays("2024-01-01", 10));
        }

        @Test void negative() {
            assertEquals("2024-01-01", DateUtils.addDays("2024-01-11", -10));
        }

        @Test void crossMonth() {
            assertEquals("2024-02-04", DateUtils.addDays("2024-01-30", 5));
        }

        @Test void invalidDateReturnsNull() {
            assertNull(DateUtils.addDays("not-a-date", 1));
        }
    }

    // ── AddMonths ──

    @Nested
    class AddMonthsTests {
        @Test void positive() {
            assertEquals("2024-04-15", DateUtils.addMonths("2024-01-15", 3));
        }

        @Test void crossYear() {
            assertEquals("2025-02-01", DateUtils.addMonths("2024-11-01", 3));
        }

        @Test void invalidDateReturnsNull() {
            assertNull(DateUtils.addMonths("invalid", 1));
        }
    }

    // ── DateDiffDays ──

    @Nested
    class DateDiffDaysTests {
        @Test void sameDate() {
            assertEquals(0L, DateUtils.dateDiffDays("2024-06-15", "2024-06-15"));
        }

        @Test void positiveDifference() {
            assertEquals(10L, DateUtils.dateDiffDays("2024-01-01", "2024-01-11"));
        }

        @Test void negativeDifference() {
            assertEquals(-10L, DateUtils.dateDiffDays("2024-01-11", "2024-01-01"));
        }

        @Test void invalidDatesReturnsNull() {
            assertNull(DateUtils.dateDiffDays("bad", "2024-01-01"));
            assertNull(DateUtils.dateDiffDays("2024-01-01", "bad"));
        }
    }

    // ── IsValidDate ──

    @ParameterizedTest
    @CsvSource({
            "2024, 1, 1, true",
            "2024, 2, 29, true",
            "2023, 2, 29, false",
            "2024, 12, 31, true",
            "2024, 4, 30, true",
            "2024, 4, 31, false",
            "2024, 0, 1, false",
            "2024, 13, 1, false",
            "2024, 1, 0, false"
    })
    void isValidDate(int year, int month, int day, boolean expected) {
        assertEquals(expected, DateUtils.isValidDate(year, month, day));
    }

    // ── DaysInMonth ──

    @ParameterizedTest
    @CsvSource({
            "2024, 1, 31",
            "2024, 2, 29",
            "2023, 2, 28",
            "2024, 4, 30",
            "2024, 6, 30",
            "2024, 9, 30",
            "2024, 11, 30",
            "2024, 7, 31",
            "2024, 12, 31"
    })
    void daysInMonth(int year, int month, int expected) {
        assertEquals(expected, DateUtils.daysInMonth(year, month));
    }

    // ── IsLeapYear ──

    @ParameterizedTest
    @CsvSource({
            "2024, true",
            "2000, true",
            "1900, false",
            "2023, false",
            "2020, true"
    })
    void isLeapYear(int year, boolean expected) {
        assertEquals(expected, DateUtils.isLeapYear(year));
    }
}
