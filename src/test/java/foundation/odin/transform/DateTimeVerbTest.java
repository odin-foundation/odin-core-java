package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for datetime, generation, and geo verbs.
 * Ported from .NET DateTimeVerbTests.cs.
 */
class DateTimeVerbTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DynValue invoke(String verb, DynValue... args) {
        return TransformEngine.invokeVerb(verb, args, null);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v)   { return DynValue.ofInteger(v); }
    private static DynValue F(double v) { return DynValue.ofFloat(v); }
    private static DynValue B(boolean v){ return DynValue.ofBool(v); }
    private static DynValue Null()      { return DynValue.ofNull(); }
    private static DynValue D(String v) { return DynValue.ofDate(v); }
    private static DynValue TS(String v){ return DynValue.ofTimestamp(v); }
    private static DynValue Arr(DynValue... items) {
        return DynValue.ofArray(new ArrayList<>(List.of(items)));
    }

    // =========================================================================
    // today / now
    // =========================================================================

    @Test
    void today_ReturnsDateString() {
        DynValue result = invoke("today");
        String s = result.asString();
        assertNotNull(s);
        assertTrue(s.matches("^\\d{4}-\\d{2}-\\d{2}$"));
    }

    @Test
    void now_ReturnsTimestampString() {
        DynValue result = invoke("now");
        String s = result.asString();
        assertNotNull(s);
        assertTrue(s.contains("T"));
    }

    // =========================================================================
    // formatDate
    // =========================================================================

    @Test
    void formatDate_BasicPattern() {
        DynValue result = invoke("formatDate", D("2024-03-15"), S("YYYY/MM/DD"));
        assertEquals("2024/03/15", result.asString());
    }

    @Test
    void formatDate_YearOnly() {
        DynValue result = invoke("formatDate", D("2024-06-15"), S("YYYY"));
        assertEquals("2024", result.asString());
    }

    @Test
    void formatDate_MonthDay() {
        DynValue result = invoke("formatDate", D("2024-01-05"), S("MM-DD"));
        assertEquals("01-05", result.asString());
    }

    @Test
    void formatDate_NullInput() {
        assertTrue(invoke("formatDate", Null(), S("YYYY-MM-DD")).isNull());
    }

    @Test
    void formatDate_NullPattern() {
        assertTrue(invoke("formatDate", D("2024-01-01"), Null()).isNull());
    }

    // =========================================================================
    // parseDate
    // =========================================================================

    @Test
    void parseDate_BasicPattern() {
        DynValue result = invoke("parseDate", S("2024-03-15"), S("YYYY-MM-DD"));
        assertEquals("2024-03-15", result.asString());
    }

    @Test
    void parseDate_SlashPattern() {
        DynValue result = invoke("parseDate", S("15/03/2024"), S("DD/MM/YYYY"));
        assertEquals("2024-03-15", result.asString());
    }

    @Test
    void parseDate_NullInput() {
        assertTrue(invoke("parseDate", Null(), S("YYYY-MM-DD")).isNull());
    }

    // =========================================================================
    // formatTime
    // =========================================================================

    @Test
    void formatTime_FromTimestamp() {
        DynValue result = invoke("formatTime", TS("2024-03-15T14:30:00.000Z"));
        assertEquals("14:30:00", result.asString());
    }

    @Test
    void formatTime_NullInput() {
        assertTrue(invoke("formatTime", Null()).isNull());
    }

    // =========================================================================
    // formatTimestamp
    // =========================================================================

    @Test
    void formatTimestamp_BasicPattern() {
        DynValue result = invoke("formatTimestamp", TS("2024-03-15T14:30:00.000Z"), S("YYYY-MM-DD HH:mm:ss"));
        assertEquals("2024-03-15 14:30:00", result.asString());
    }

    @Test
    void formatTimestamp_DefaultIso() {
        DynValue result = invoke("formatTimestamp", TS("2024-03-15T14:30:00.000Z"));
        assertTrue(result.asString().contains("2024-03-15"));
        assertTrue(result.asString().contains("T"));
    }

    @Test
    void formatTimestamp_NullInput() {
        assertTrue(invoke("formatTimestamp", Null()).isNull());
    }

    // =========================================================================
    // parseTimestamp
    // =========================================================================

    @Test
    void parseTimestamp_IsoFormat() {
        DynValue result = invoke("parseTimestamp", S("2024-03-15T14:30:00Z"));
        String s = result.asString();
        assertNotNull(s);
        assertTrue(s.contains("2024-03-15"));
    }

    @Test
    void parseTimestamp_NullInput() {
        assertTrue(invoke("parseTimestamp", Null()).isNull());
    }

    // =========================================================================
    // addDays
    // =========================================================================

    @Test
    void addDays_Positive() {
        DynValue result = invoke("addDays", D("2024-01-01"), I(10));
        assertEquals("2024-01-11", result.asString());
    }

    @Test
    void addDays_Negative() {
        DynValue result = invoke("addDays", D("2024-01-11"), I(-10));
        assertEquals("2024-01-01", result.asString());
    }

    @Test
    void addDays_CrossMonth() {
        DynValue result = invoke("addDays", D("2024-01-30"), I(5));
        assertEquals("2024-02-04", result.asString());
    }

    @Test
    void addDays_Zero() {
        DynValue result = invoke("addDays", D("2024-03-15"), I(0));
        assertEquals("2024-03-15", result.asString());
    }

    @Test
    void addDays_NullDate() {
        assertTrue(invoke("addDays", Null(), I(5)).isNull());
    }

    @Test
    void addDays_CrossYear() {
        DynValue result = invoke("addDays", D("2024-12-30"), I(5));
        assertEquals("2025-01-04", result.asString());
    }

    // =========================================================================
    // addMonths
    // =========================================================================

    @Test
    void addMonths_Positive() {
        DynValue result = invoke("addMonths", D("2024-01-15"), I(3));
        assertEquals("2024-04-15", result.asString());
    }

    @Test
    void addMonths_Negative() {
        DynValue result = invoke("addMonths", D("2024-04-15"), I(-3));
        assertEquals("2024-01-15", result.asString());
    }

    @Test
    void addMonths_CrossYear() {
        DynValue result = invoke("addMonths", D("2024-11-15"), I(3));
        assertEquals("2025-02-15", result.asString());
    }

    @Test
    void addMonths_NullDate() {
        assertTrue(invoke("addMonths", Null(), I(1)).isNull());
    }

    // =========================================================================
    // addYears
    // =========================================================================

    @Test
    void addYears_Positive() {
        DynValue result = invoke("addYears", D("2024-03-15"), I(2));
        assertEquals("2026-03-15", result.asString());
    }

    @Test
    void addYears_Negative() {
        DynValue result = invoke("addYears", D("2024-03-15"), I(-1));
        assertEquals("2023-03-15", result.asString());
    }

    @Test
    void addYears_NullDate() {
        assertTrue(invoke("addYears", Null(), I(1)).isNull());
    }

    // =========================================================================
    // dateDiff
    // =========================================================================

    @Test
    void dateDiff_SameDate() {
        assertEquals(0L, invoke("dateDiff", D("2024-01-01"), D("2024-01-01")).asInt64());
    }

    @Test
    void dateDiff_PositiveDiff() {
        assertEquals(10L, invoke("dateDiff", D("2024-01-01"), D("2024-01-11")).asInt64());
    }

    @Test
    void dateDiff_NegativeDiff() {
        assertEquals(-10L, invoke("dateDiff", D("2024-01-11"), D("2024-01-01")).asInt64());
    }

    @Test
    void dateDiff_CrossYear() {
        DynValue result = invoke("dateDiff", D("2023-12-31"), D("2024-01-01"));
        assertEquals(1L, result.asInt64());
    }

    @Test
    void dateDiff_NullInput() {
        assertTrue(invoke("dateDiff", Null(), D("2024-01-01")).isNull());
    }

    // =========================================================================
    // addHours / addMinutes / addSeconds
    // =========================================================================

    @Test
    void addHours_Positive() {
        DynValue result = invoke("addHours", TS("2024-01-01T00:00:00.000Z"), I(5));
        assertTrue(result.asString().contains("05:00:00"));
    }

    @Test
    void addHours_NullInput() {
        assertTrue(invoke("addHours", Null(), I(1)).isNull());
    }

    @Test
    void addMinutes_Positive() {
        DynValue result = invoke("addMinutes", TS("2024-01-01T00:00:00.000Z"), I(90));
        assertTrue(result.asString().contains("01:30:00"));
    }

    @Test
    void addMinutes_NullInput() {
        assertTrue(invoke("addMinutes", Null(), I(30)).isNull());
    }

    @Test
    void addSeconds_Positive() {
        DynValue result = invoke("addSeconds", TS("2024-01-01T00:00:00.000Z"), I(3661));
        assertTrue(result.asString().contains("01:01:01"));
    }

    @Test
    void addSeconds_NullInput() {
        assertTrue(invoke("addSeconds", Null(), I(10)).isNull());
    }

    // =========================================================================
    // startOfDay / endOfDay
    // =========================================================================

    @Test
    void startOfDay_ReturnsTimestamp() {
        DynValue result = invoke("startOfDay", D("2024-03-15"));
        assertTrue(result.asString().contains("00:00:00"));
    }

    @Test
    void startOfDay_NullInput() {
        assertTrue(invoke("startOfDay", Null()).isNull());
    }

    @Test
    void endOfDay_ReturnsTimestamp() {
        DynValue result = invoke("endOfDay", D("2024-03-15"));
        assertTrue(result.asString().contains("23:59:59"));
    }

    @Test
    void endOfDay_NullInput() {
        assertTrue(invoke("endOfDay", Null()).isNull());
    }

    // =========================================================================
    // startOfMonth / endOfMonth
    // =========================================================================

    @Test
    void startOfMonth_ReturnsFirstDay() {
        DynValue result = invoke("startOfMonth", D("2024-03-15"));
        assertEquals("2024-03-01", result.asString());
    }

    @Test
    void startOfMonth_AlreadyFirstDay() {
        assertEquals("2024-01-01", invoke("startOfMonth", D("2024-01-01")).asString());
    }

    @Test
    void startOfMonth_NullInput() {
        assertTrue(invoke("startOfMonth", Null()).isNull());
    }

    @Test
    void endOfMonth_March() {
        assertEquals("2024-03-31", invoke("endOfMonth", D("2024-03-15")).asString());
    }

    @Test
    void endOfMonth_February_LeapYear() {
        assertEquals("2024-02-29", invoke("endOfMonth", D("2024-02-10")).asString());
    }

    @Test
    void endOfMonth_February_NonLeapYear() {
        assertEquals("2023-02-28", invoke("endOfMonth", D("2023-02-10")).asString());
    }

    @Test
    void endOfMonth_NullInput() {
        assertTrue(invoke("endOfMonth", Null()).isNull());
    }

    // =========================================================================
    // startOfYear / endOfYear
    // =========================================================================

    @Test
    void startOfYear_ReturnsJanFirst() {
        assertEquals("2024-01-01", invoke("startOfYear", D("2024-06-15")).asString());
    }

    @Test
    void startOfYear_NullInput() {
        assertTrue(invoke("startOfYear", Null()).isNull());
    }

    @Test
    void endOfYear_ReturnsDecThirtyFirst() {
        assertEquals("2024-12-31", invoke("endOfYear", D("2024-06-15")).asString());
    }

    @Test
    void endOfYear_NullInput() {
        assertTrue(invoke("endOfYear", Null()).isNull());
    }

    // =========================================================================
    // dayOfWeek
    // =========================================================================

    @Test
    void dayOfWeek_Monday() {
        // 2024-01-01 is Monday
        DynValue result = invoke("dayOfWeek", D("2024-01-01"));
        assertEquals(1L, result.asInt64()); // Monday = 1
    }

    @Test
    void dayOfWeek_Sunday() {
        // 2024-01-07 is Sunday
        assertEquals(0L, invoke("dayOfWeek", D("2024-01-07")).asInt64()); // Sunday = 0
    }

    @Test
    void dayOfWeek_NullInput() {
        assertTrue(invoke("dayOfWeek", Null()).isNull());
    }

    // =========================================================================
    // weekOfYear
    // =========================================================================

    @Test
    void weekOfYear_JanFirst() {
        DynValue result = invoke("weekOfYear", D("2024-01-01"));
        assertTrue(result.asInt64() >= 1);
    }

    @Test
    void weekOfYear_MidYear() {
        DynValue result = invoke("weekOfYear", D("2024-06-15"));
        assertTrue(result.asInt64() > 20 && result.asInt64() < 30);
    }

    @Test
    void weekOfYear_NullInput() {
        assertTrue(invoke("weekOfYear", Null()).isNull());
    }

    // =========================================================================
    // quarter
    // =========================================================================

    @Test
    void quarter_Q1() {
        assertEquals(1L, invoke("quarter", D("2024-01-15")).asInt64());
        assertEquals(1L, invoke("quarter", D("2024-03-31")).asInt64());
    }

    @Test
    void quarter_Q2() {
        assertEquals(2L, invoke("quarter", D("2024-04-01")).asInt64());
        assertEquals(2L, invoke("quarter", D("2024-06-30")).asInt64());
    }

    @Test
    void quarter_Q3() {
        assertEquals(3L, invoke("quarter", D("2024-07-01")).asInt64());
    }

    @Test
    void quarter_Q4() {
        assertEquals(4L, invoke("quarter", D("2024-12-31")).asInt64());
    }

    @Test
    void quarter_NullInput() {
        assertTrue(invoke("quarter", Null()).isNull());
    }

    // =========================================================================
    // isLeapYear
    // =========================================================================

    @Test
    void isLeapYear_2024() {
        assertTrue(invoke("isLeapYear", I(2024)).asBool());
    }

    @Test
    void isLeapYear_2023() {
        assertFalse(invoke("isLeapYear", I(2023)).asBool());
    }

    @Test
    void isLeapYear_2000() {
        assertTrue(invoke("isLeapYear", I(2000)).asBool());
    }

    @Test
    void isLeapYear_1900() {
        assertFalse(invoke("isLeapYear", I(1900)).asBool());
    }

    @Test
    void isLeapYear_FromDate() {
        assertTrue(invoke("isLeapYear", D("2024-06-15")).asBool());
    }

    // =========================================================================
    // isBefore / isAfter / isBetween
    // =========================================================================

    @Test
    void isBefore_True() {
        assertTrue(invoke("isBefore", D("2024-01-01"), D("2024-01-02")).asBool());
    }

    @Test
    void isBefore_False() {
        assertFalse(invoke("isBefore", D("2024-01-02"), D("2024-01-01")).asBool());
    }

    @Test
    void isBefore_Equal() {
        assertFalse(invoke("isBefore", D("2024-01-01"), D("2024-01-01")).asBool());
    }

    @Test
    void isBefore_NullInput() {
        assertTrue(invoke("isBefore", Null(), D("2024-01-01")).isNull());
    }

    @Test
    void isAfter_True() {
        assertTrue(invoke("isAfter", D("2024-01-02"), D("2024-01-01")).asBool());
    }

    @Test
    void isAfter_False() {
        assertFalse(invoke("isAfter", D("2024-01-01"), D("2024-01-02")).asBool());
    }

    @Test
    void isAfter_Equal() {
        assertFalse(invoke("isAfter", D("2024-01-01"), D("2024-01-01")).asBool());
    }

    @Test
    void isAfter_NullInput() {
        assertTrue(invoke("isAfter", Null(), D("2024-01-01")).isNull());
    }

    @Test
    void isBetween_Inside() {
        assertTrue(invoke("isBetween", D("2024-06-15"), D("2024-01-01"), D("2024-12-31")).asBool());
    }

    @Test
    void isBetween_OnStart() {
        assertTrue(invoke("isBetween", D("2024-01-01"), D("2024-01-01"), D("2024-12-31")).asBool());
    }

    @Test
    void isBetween_OnEnd() {
        assertTrue(invoke("isBetween", D("2024-12-31"), D("2024-01-01"), D("2024-12-31")).asBool());
    }

    @Test
    void isBetween_Outside() {
        assertFalse(invoke("isBetween", D("2025-01-01"), D("2024-01-01"), D("2024-12-31")).asBool());
    }

    @Test
    void isBetween_NullInput() {
        assertTrue(invoke("isBetween", Null(), D("2024-01-01"), D("2024-12-31")).isNull());
    }

    // =========================================================================
    // toUnix / fromUnix
    // =========================================================================

    @Test
    void toUnix_Epoch() {
        assertEquals(0L, invoke("toUnix", TS("1970-01-01T00:00:00.000Z")).asInt64());
    }

    @Test
    void toUnix_KnownDate() {
        DynValue result = invoke("toUnix", D("2024-01-01"));
        assertTrue(result.asInt64() > 0);
    }

    @Test
    void toUnix_NullInput() {
        assertTrue(invoke("toUnix", Null()).isNull());
    }

    @Test
    void fromUnix_Epoch() {
        DynValue result = invoke("fromUnix", I(0));
        assertTrue(result.asString().contains("1970-01-01"));
    }

    @Test
    void fromUnix_KnownTimestamp() {
        // 1704067200 = 2024-01-01T00:00:00Z
        DynValue result = invoke("fromUnix", I(1704067200));
        assertTrue(result.asString().contains("2024-01-01"));
    }

    @Test
    void toUnix_FromUnix_Roundtrip() {
        DynValue ts = TS("2024-06-15T12:30:00.000Z");
        DynValue unix = invoke("toUnix", ts);
        DynValue back = invoke("fromUnix", unix);
        assertTrue(back.asString().contains("2024-06-15"));
    }

    // =========================================================================
    // daysBetweenDates
    // =========================================================================

    @Test
    void daysBetweenDates_SameDate() {
        assertEquals(0L, invoke("daysBetweenDates", D("2024-01-01"), D("2024-01-01")).asInt64());
    }

    @Test
    void daysBetweenDates_Positive() {
        assertEquals(10L, invoke("daysBetweenDates", D("2024-01-01"), D("2024-01-11")).asInt64());
    }

    @Test
    void daysBetweenDates_ReversedIsAbsolute() {
        assertEquals(10L, invoke("daysBetweenDates", D("2024-01-11"), D("2024-01-01")).asInt64());
    }

    @Test
    void daysBetweenDates_NullInput() {
        assertTrue(invoke("daysBetweenDates", Null(), D("2024-01-01")).isNull());
    }

    // =========================================================================
    // isValidDate
    // =========================================================================

    @Test
    void isValidDate_Valid() {
        assertTrue(invoke("isValidDate", S("2024-03-15")).asBool());
    }

    @Test
    void isValidDate_Invalid() {
        assertFalse(invoke("isValidDate", S("not-a-date")).asBool());
    }

    @Test
    void isValidDate_Feb29_LeapYear() {
        assertTrue(invoke("isValidDate", S("2024-02-29")).asBool());
    }

    @Test
    void isValidDate_Feb29_NonLeapYear() {
        assertFalse(invoke("isValidDate", S("2023-02-29")).asBool());
    }

    @Test
    void isValidDate_InvalidMonth() {
        assertFalse(invoke("isValidDate", S("2024-13-01")).asBool());
    }

    @Test
    void isValidDate_InvalidDay() {
        assertFalse(invoke("isValidDate", S("2024-01-32")).asBool());
    }

    @Test
    void isValidDate_NullInput() {
        assertFalse(invoke("isValidDate", Null()).asBool());
    }

    // =========================================================================
    // formatLocaleDate
    // =========================================================================

    @Test
    void formatLocaleDate_EnUs() {
        DynValue result = invoke("formatLocaleDate", D("2024-03-15"), S("en-US"));
        assertNotNull(result.asString());
        assertFalse(result.asString().isEmpty());
    }

    @Test
    void formatLocaleDate_NullDate() {
        assertTrue(invoke("formatLocaleDate", Null(), S("en-US")).isNull());
    }

    @Test
    void formatLocaleDate_NullLocale() {
        assertTrue(invoke("formatLocaleDate", D("2024-03-15"), Null()).isNull());
    }

    // =========================================================================
    // uuid
    // =========================================================================

    @Test
    void uuid_ReturnsString() {
        DynValue result = invoke("uuid");
        String s = result.asString();
        assertNotNull(s);
        assertEquals(36, s.length()); // UUID format: 8-4-4-4-12
    }

    @Test
    void uuid_HasHyphens() {
        String result = invoke("uuid").asString();
        assertTrue(result.contains("-"));
        String[] parts = result.split("-");
        assertEquals(5, parts.length);
    }

    @Test
    void uuid_TwoCallsAreDifferent() {
        String r1 = invoke("uuid").asString();
        String r2 = invoke("uuid").asString();
        assertNotEquals(r1, r2);
    }

    // =========================================================================
    // sequence / resetSequence
    // =========================================================================

    @Test
    void sequence_DefaultZero() {
        DynValue result = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("counter") }, new TransformEngine.VerbContext());
        assertEquals(0L, result.asInt64());
    }

    @Test
    void sequence_Increments() {
        var ctx = new TransformEngine.VerbContext();
        DynValue r1 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("inc_counter") }, ctx);
        DynValue r2 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("inc_counter") }, ctx);
        DynValue r3 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("inc_counter") }, ctx);
        assertEquals(0L, r1.asInt64());
        assertEquals(1L, r2.asInt64());
        assertEquals(2L, r3.asInt64());
    }

    @Test
    void sequence_NamedCounters() {
        var ctx = new TransformEngine.VerbContext();
        DynValue a1 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("seq_a") }, ctx);
        DynValue b1 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("seq_b") }, ctx);
        DynValue a2 = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("seq_a") }, ctx);
        assertEquals(0L, a1.asInt64());
        assertEquals(0L, b1.asInt64());
        assertEquals(1L, a2.asInt64());
    }

    @Test
    void resetSequence_ResetsToZero() {
        var ctx = new TransformEngine.VerbContext();
        TransformEngine.invokeVerb("sequence", new DynValue[]{ S("reset_counter") }, ctx);
        TransformEngine.invokeVerb("sequence", new DynValue[]{ S("reset_counter") }, ctx);
        TransformEngine.invokeVerb("resetSequence", new DynValue[]{ S("reset_counter") }, ctx);
        DynValue result = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("reset_counter") }, ctx);
        assertEquals(0L, result.asInt64());
    }

    @Test
    void resetSequence_ResetsToCustomValue() {
        var ctx = new TransformEngine.VerbContext();
        TransformEngine.invokeVerb("resetSequence", new DynValue[]{ S("custom_counter"), I(10) }, ctx);
        DynValue result = TransformEngine.invokeVerb("sequence", new DynValue[]{ S("custom_counter") }, ctx);
        assertEquals(10L, result.asInt64());
    }

    // =========================================================================
    // nanoid
    // =========================================================================

    @Test
    void nanoid_DefaultLength() {
        DynValue result = invoke("nanoid");
        assertEquals(21, result.asString().length());
    }

    @Test
    void nanoid_CustomLength() {
        DynValue result = invoke("nanoid", I(10));
        assertEquals(10, result.asString().length());
    }

    @Test
    void nanoid_TwoCallsAreDifferent() {
        String r1 = invoke("nanoid").asString();
        String r2 = invoke("nanoid").asString();
        assertNotEquals(r1, r2);
    }

    // =========================================================================
    // distance (Haversine)
    // =========================================================================

    @Test
    void distance_SamePointIsZero() {
        DynValue result = invoke("distance", F(40.0), F(-74.0), F(40.0), F(-74.0));
        assertTrue(Math.abs(result.asDouble()) < 0.001);
    }

    @Test
    void distance_NewYorkToLondon() {
        DynValue result = invoke("distance", F(40.7128), F(-74.0060), F(51.5074), F(-0.1278));
        double km = result.asDouble();
        assertTrue(km > 5000.0 && km < 6000.0); // ~5570 km
    }

    @Test
    void distance_NullInput() {
        assertTrue(invoke("distance", Null(), F(0.0), F(0.0), F(0.0)).isNull());
    }

    // =========================================================================
    // inBoundingBox
    // =========================================================================

    @Test
    void inBoundingBox_Inside() {
        assertTrue(invoke("inBoundingBox", F(5.0), F(5.0), F(0.0), F(0.0), F(10.0), F(10.0)).asBool());
    }

    @Test
    void inBoundingBox_Outside() {
        assertFalse(invoke("inBoundingBox", F(15.0), F(5.0), F(0.0), F(0.0), F(10.0), F(10.0)).asBool());
    }

    @Test
    void inBoundingBox_OnEdge() {
        assertTrue(invoke("inBoundingBox", F(0.0), F(0.0), F(0.0), F(0.0), F(10.0), F(10.0)).asBool());
    }

    @Test
    void inBoundingBox_NullInput() {
        assertTrue(invoke("inBoundingBox", Null(), F(5.0), F(0.0), F(0.0), F(10.0), F(10.0)).isNull());
    }

    // =========================================================================
    // toRadians / toDegrees
    // =========================================================================

    @Test
    void toRadians_180() {
        DynValue result = invoke("toRadians", F(180.0));
        assertTrue(Math.abs(result.asDouble() - Math.PI) < 1e-10);
    }

    @Test
    void toRadians_90() {
        DynValue result = invoke("toRadians", F(90.0));
        assertTrue(Math.abs(result.asDouble() - Math.PI / 2) < 1e-10);
    }

    @Test
    void toRadians_Zero() {
        DynValue result = invoke("toRadians", F(0.0));
        assertTrue(Math.abs(result.asDouble()) < 1e-10);
    }

    @Test
    void toRadians_IntegerInput() {
        DynValue result = invoke("toRadians", I(180));
        assertTrue(Math.abs(result.asDouble() - Math.PI) < 1e-10);
    }

    @Test
    void toDegrees_Pi() {
        DynValue result = invoke("toDegrees", F(Math.PI));
        assertTrue(Math.abs(result.asDouble() - 180.0) < 1e-10);
    }

    @Test
    void toDegrees_Zero() {
        DynValue result = invoke("toDegrees", F(0.0));
        assertTrue(Math.abs(result.asDouble()) < 1e-10);
    }

    // =========================================================================
    // bearing
    // =========================================================================

    @Test
    void bearing_North() {
        DynValue result = invoke("bearing", F(0.0), F(0.0), F(10.0), F(0.0));
        double deg = result.asDouble();
        assertTrue(deg < 1.0 || (deg - 360.0) > -1.0); // ~0 degrees
    }

    @Test
    void bearing_East() {
        DynValue result = invoke("bearing", F(0.0), F(0.0), F(0.0), F(10.0));
        assertTrue(Math.abs(result.asDouble() - 90.0) < 1.0);
    }

    @Test
    void bearing_NullInput() {
        assertTrue(invoke("bearing", Null(), F(0.0), F(0.0), F(0.0)).isNull());
    }

    // =========================================================================
    // midpoint
    // =========================================================================

    @Test
    void midpoint_SamePoint() {
        DynValue result = invoke("midpoint", F(40.0), F(-74.0), F(40.0), F(-74.0));
        var arr = result.asArray();
        assertTrue(Math.abs(arr.get(0).asDouble() - 40.0) < 0.001);
        assertTrue(Math.abs(arr.get(1).asDouble() - (-74.0)) < 0.001);
    }

    @Test
    void midpoint_Equator() {
        DynValue result = invoke("midpoint", F(0.0), F(0.0), F(0.0), F(10.0));
        var arr = result.asArray();
        assertTrue(Math.abs(arr.get(0).asDouble()) < 0.01); // lat ~0
        assertTrue(Math.abs(arr.get(1).asDouble() - 5.0) < 0.01); // lon ~5
    }

    @Test
    void midpoint_NullInput() {
        assertTrue(invoke("midpoint", Null(), F(0.0), F(0.0), F(0.0)).isNull());
    }

    // =========================================================================
    // ageFromDate
    // =========================================================================

    @Test
    void ageFromDate_ReturnsPositiveAge() {
        // Someone born 30 years ago
        int birthYear = Year.now(ZoneOffset.UTC).getValue() - 30;
        String birthDate = birthYear + "-01-01";
        DynValue result = invoke("ageFromDate", D(birthDate));
        assertTrue(result.asInt64() >= 29 && result.asInt64() <= 31);
    }

    @Test
    void ageFromDate_NullInput() {
        assertTrue(invoke("ageFromDate", Null()).isNull());
    }
}
