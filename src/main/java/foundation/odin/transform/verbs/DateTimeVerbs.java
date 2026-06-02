package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;
import foundation.odin.utils.DateUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.BiFunction;

public final class DateTimeVerbs {

    private DateTimeVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("today", DateTimeVerbs::today);
        reg.put("now", DateTimeVerbs::now);
        reg.put("formatDate", DateTimeVerbs::formatDate);
        reg.put("parseDate", DateTimeVerbs::parseDate);
        reg.put("formatTime", DateTimeVerbs::formatTime);
        reg.put("formatTimestamp", DateTimeVerbs::formatTimestamp);
        reg.put("parseTimestamp", DateTimeVerbs::parseTimestamp);
        reg.put("addDays", DateTimeVerbs::addDays);
        reg.put("addMonths", DateTimeVerbs::addMonths);
        reg.put("addYears", DateTimeVerbs::addYears);
        reg.put("dateDiff", DateTimeVerbs::dateDiff);
        reg.put("addHours", DateTimeVerbs::addHours);
        reg.put("addMinutes", DateTimeVerbs::addMinutes);
        reg.put("addSeconds", DateTimeVerbs::addSeconds);
        reg.put("startOfDay", DateTimeVerbs::startOfDay);
        reg.put("endOfDay", DateTimeVerbs::endOfDay);
        reg.put("startOfMonth", DateTimeVerbs::startOfMonth);
        reg.put("endOfMonth", DateTimeVerbs::endOfMonth);
        reg.put("startOfYear", DateTimeVerbs::startOfYear);
        reg.put("endOfYear", DateTimeVerbs::endOfYear);
        reg.put("dayOfWeek", DateTimeVerbs::dayOfWeek);
        reg.put("weekOfYear", DateTimeVerbs::weekOfYear);
        reg.put("quarter", DateTimeVerbs::quarter);
        reg.put("isLeapYear", DateTimeVerbs::isLeapYear);
        reg.put("isBefore", DateTimeVerbs::isBefore);
        reg.put("isAfter", DateTimeVerbs::isAfter);
        reg.put("isBetween", DateTimeVerbs::isBetween);
        reg.put("toUnix", DateTimeVerbs::toUnix);
        reg.put("fromUnix", DateTimeVerbs::fromUnix);
        reg.put("daysBetweenDates", DateTimeVerbs::daysBetweenDates);
        reg.put("ageFromDate", DateTimeVerbs::ageFromDate);
        reg.put("isValidDate", DateTimeVerbs::isValidDate);
        reg.put("formatLocaleDate", DateTimeVerbs::formatLocaleDate);
        reg.put("businessDays", DateTimeVerbs::businessDays);
        reg.put("nextBusinessDay", DateTimeVerbs::nextBusinessDay);
        reg.put("formatDuration", DateTimeVerbs::formatDuration);
    }

    // ── Helpers ──

    private static final Instant UNIX_EPOCH = Instant.EPOCH;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private static String extractDateStr(DynValue v) {
        if (v.isNull()) return null;
        return v.asString();
    }

    private static LocalDateTime parseDt(String s) {
        if (s == null) return null;
        // Try ISO timestamp with Z
        try {
            Instant instant = Instant.parse(s);
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {}
        // Try LocalDateTime (no Z)
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignored) {}
        // Try date-only
        try {
            LocalDate date = LocalDate.parse(s);
            return date.atStartOfDay();
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    private static String formatAsDate(LocalDateTime dt) {
        return String.format("%04d-%02d-%02d", dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth());
    }

    private static String formatAsTimestamp(LocalDateTime dt) {
        return dt.format(TIMESTAMP_FORMAT);
    }

    private static Long extractLong(DynValue v) {
        Long i = v.asInt64();
        if (i != null) return i;
        Double d = v.asDouble();
        if (d != null) return (long) d.doubleValue();
        String s = v.asString();
        if (s != null) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Integer extractInt(DynValue v) {
        Long l = extractLong(v);
        if (l != null) return (int) l.longValue();
        return null;
    }

    private static String applySimpleDateFormat(LocalDateTime dt, String pattern) {
        String result = pattern;
        result = result.replace("YYYY", String.format("%04d", dt.getYear()));
        result = result.replace("YY", String.format("%02d", dt.getYear() % 100));
        result = result.replace("MM", String.format("%02d", dt.getMonthValue()));
        result = result.replace("DD", String.format("%02d", dt.getDayOfMonth()));
        result = result.replace("HH", String.format("%02d", dt.getHour()));
        result = result.replace("mm", String.format("%02d", dt.getMinute()));
        result = result.replace("ss", String.format("%02d", dt.getSecond()));
        return result;
    }

    // ── Verb Implementations ──

    private static DynValue today(DynValue[] args, VerbContext ctx) {
        return DynValue.ofDate(DateUtils.today());
    }

    private static DynValue now(DynValue[] args, VerbContext ctx) {
        return DynValue.ofTimestamp(DateUtils.now());
    }

    private static DynValue formatDate(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String dateStr = extractDateStr(args[0]);
        String pattern = args[1].asString();
        if (dateStr == null || pattern == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(dateStr);
        if (dt == null) return DynValue.ofNull();

        return DynValue.ofString(applySimpleDateFormat(dt, pattern));
    }

    private static DynValue parseDate(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        String pattern = args[1].asString();
        if (s == null || pattern == null) return DynValue.ofNull();

        // Try pattern-based parsing first
        LocalDateTime dt = parseWithFormat(s, pattern);
        if (dt == null) dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        return DynValue.ofString(formatAsDate(dt));
    }

    private static LocalDateTime parseWithFormat(String s, String pattern) {
        String javaFmt = pattern
                .replace("YYYY", "yyyy")
                .replace("DD", "dd");
        // MM, HH, mm, ss are same in Java DateTimeFormatter
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(javaFmt);
            LocalDate date = LocalDate.parse(s, formatter);
            return date.atStartOfDay();
        } catch (Exception e) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(javaFmt);
                return LocalDateTime.parse(s, formatter);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static DynValue formatTime(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        if (args.length >= 2) {
            String pattern = args[1].asString();
            if (pattern != null)
                return DynValue.ofString(applySimpleDateFormat(dt, pattern));
        }

        return DynValue.ofTime(String.format("%02d:%02d:%02d", dt.getHour(), dt.getMinute(), dt.getSecond()));
    }

    private static DynValue formatTimestamp(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        if (args.length >= 2) {
            String pattern = args[1].asString();
            if (pattern != null)
                return DynValue.ofString(applySimpleDateFormat(dt, pattern));
        }

        return DynValue.ofTimestamp(formatAsTimestamp(dt));
    }

    private static DynValue parseTimestamp(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s = args[0].asString();
        String pattern = args[1].asString();
        if (s == null || pattern == null) return DynValue.ofNull();

        String javaFmt = pattern.replace("YYYY", "yyyy").replace("DD", "dd");
        LocalDateTime dt;
        try {
            dt = LocalDateTime.parse(s, DateTimeFormatter.ofPattern(javaFmt));
        } catch (Exception e) {
            dt = parseWithFormat(s, pattern);
        }
        if (dt == null) return DynValue.ofNull();

        return DynValue.ofString(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
    }

    private static DynValue addDays(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String dateStr = extractDateStr(args[0]);
        Integer days = extractInt(args[1]);
        if (dateStr == null || days == null) return DynValue.ofNull();

        String result = DateUtils.addDays(dateStr, days);
        return result != null ? DynValue.ofString(result) : DynValue.ofNull();
    }

    private static DynValue addMonths(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String dateStr = extractDateStr(args[0]);
        Integer months = extractInt(args[1]);
        if (dateStr == null || months == null) return DynValue.ofNull();

        String result = DateUtils.addMonths(dateStr, months);
        return result != null ? DynValue.ofString(result) : DynValue.ofNull();
    }

    private static DynValue addYears(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String dateStr = extractDateStr(args[0]);
        Integer years = extractInt(args[1]);
        if (dateStr == null || years == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(dateStr);
        if (dt == null) return DynValue.ofNull();

        try {
            LocalDateTime result = dt.plusYears(years);
            return DynValue.ofString(formatAsDate(result));
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue dateDiff(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s1 = extractDateStr(args[0]);
        String s2 = extractDateStr(args[1]);
        String unit = args.length >= 3 ? args[2].asString() : "days";
        if (s1 == null || s2 == null || unit == null) return DynValue.ofNull();

        LocalDateTime dt1 = parseDt(s1);
        LocalDateTime dt2 = parseDt(s2);
        if (dt1 == null || dt2 == null) return DynValue.ofNull();

        switch (unit) {
            case "days": {
                Long diff = DateUtils.dateDiffDays(s1, s2);
                return diff != null ? DynValue.ofInteger(diff) : DynValue.ofNull();
            }
            case "months": {
                int months = (dt2.getYear() - dt1.getYear()) * 12
                        + (dt2.getMonthValue() - dt1.getMonthValue());
                return DynValue.ofInteger(months);
            }
            case "years":
                return DynValue.ofInteger(dt2.getYear() - dt1.getYear());
            default:
                // T011_INCOMPATIBLE_CONVERSION - unknown unit
                return DynValue.ofNull();
        }
    }

    private static DynValue addHours(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        Integer hours = extractInt(args[1]);
        if (s == null || hours == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = dt.plusHours(hours);
        return DynValue.ofTimestamp(formatAsTimestamp(result));
    }

    private static DynValue addMinutes(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        Integer minutes = extractInt(args[1]);
        if (s == null || minutes == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = dt.plusMinutes(minutes);
        return DynValue.ofTimestamp(formatAsTimestamp(result));
    }

    private static DynValue addSeconds(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        Integer seconds = extractInt(args[1]);
        if (s == null || seconds == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = dt.plusSeconds(seconds);
        return DynValue.ofTimestamp(formatAsTimestamp(result));
    }

    private static DynValue startOfDay(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = LocalDateTime.of(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), 0, 0, 0);
        return DynValue.ofTimestamp(formatAsTimestamp(result));
    }

    private static DynValue endOfDay(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = LocalDateTime.of(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), 23, 59, 59, 999_000_000);
        return DynValue.ofTimestamp(formatAsTimestamp(result));
    }

    private static DynValue startOfMonth(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = LocalDateTime.of(dt.getYear(), dt.getMonthValue(), 1, 0, 0, 0);
        return DynValue.ofString(formatAsDate(result));
    }

    private static DynValue endOfMonth(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        int lastDay = DateUtils.daysInMonth(dt.getYear(), dt.getMonthValue());
        LocalDateTime result = LocalDateTime.of(dt.getYear(), dt.getMonthValue(), lastDay, 0, 0, 0);
        return DynValue.ofString(formatAsDate(result));
    }

    private static DynValue startOfYear(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = LocalDateTime.of(dt.getYear(), 1, 1, 0, 0, 0);
        return DynValue.ofString(formatAsDate(result));
    }

    private static DynValue endOfYear(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        LocalDateTime result = LocalDateTime.of(dt.getYear(), 12, 31, 0, 0, 0);
        return DynValue.ofString(formatAsDate(result));
    }

    private static DynValue dayOfWeek(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        // Output numbering: Sunday=0, Monday=1, ..., Saturday=6
        int dow = dt.getDayOfWeek().getValue(); // 1=Mon..7=Sun
        int dotNetDow = dow == 7 ? 0 : dow; // Convert to 0=Sun, 1=Mon, ..., 6=Sat
        return DynValue.ofInteger(dotNetDow);
    }

    private static DynValue weekOfYear(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        // Week of year (first four-day week, Monday start):
        // count weeks within the calendar year (never week 1 of next year),
        // so use weekOfYear() which caps at 52/53 rather than weekOfWeekBasedYear()
        // which can return 1 for late-December dates belonging to next year's ISO week 1.
        WeekFields wf = WeekFields.of(DayOfWeek.MONDAY, 4);
        int week = dt.get(wf.weekOfYear());
        return DynValue.ofInteger(week);
    }

    private static DynValue quarter(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        int q = (dt.getMonthValue() - 1) / 3 + 1;
        return DynValue.ofInteger(q);
    }

    private static DynValue isLeapYear(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();

        Integer yearNum = extractInt(args[0]);
        if (yearNum != null) return DynValue.ofBool(DateUtils.isLeapYear(yearNum));

        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        return DynValue.ofBool(DateUtils.isLeapYear(dt.getYear()));
    }

    private static DynValue isBefore(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s1 = extractDateStr(args[0]);
        String s2 = extractDateStr(args[1]);
        if (s1 == null || s2 == null) return DynValue.ofNull();

        LocalDateTime dt1 = parseDt(s1);
        LocalDateTime dt2 = parseDt(s2);
        if (dt1 == null || dt2 == null) return DynValue.ofNull();

        return DynValue.ofBool(dt1.isBefore(dt2));
    }

    private static DynValue isAfter(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s1 = extractDateStr(args[0]);
        String s2 = extractDateStr(args[1]);
        if (s1 == null || s2 == null) return DynValue.ofNull();

        LocalDateTime dt1 = parseDt(s1);
        LocalDateTime dt2 = parseDt(s2);
        if (dt1 == null || dt2 == null) return DynValue.ofNull();

        return DynValue.ofBool(dt1.isAfter(dt2));
    }

    private static DynValue isBetween(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        String sStart = extractDateStr(args[1]);
        String sEnd = extractDateStr(args[2]);
        if (s == null || sStart == null || sEnd == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        LocalDateTime dtStart = parseDt(sStart);
        LocalDateTime dtEnd = parseDt(sEnd);
        if (dt == null || dtStart == null || dtEnd == null) return DynValue.ofNull();

        return DynValue.ofBool(!dt.isBefore(dtStart) && !dt.isAfter(dtEnd));
    }

    private static DynValue toUnix(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(s);
        if (dt == null) return DynValue.ofNull();

        long epochSeconds = dt.toEpochSecond(ZoneOffset.UTC);
        return DynValue.ofInteger(epochSeconds);
    }

    private static DynValue fromUnix(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Long seconds = extractLong(args[0]);
        if (seconds == null) return DynValue.ofNull();

        LocalDateTime dt = LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
        return DynValue.ofTimestamp(formatAsTimestamp(dt));
    }

    private static DynValue daysBetweenDates(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String s1 = extractDateStr(args[0]);
        String s2 = extractDateStr(args[1]);
        if (s1 == null || s2 == null) return DynValue.ofNull();

        Long diff = DateUtils.dateDiffDays(s1, s2);
        return diff != null ? DynValue.ofInteger(Math.abs(diff)) : DynValue.ofNull();
    }

    private static DynValue ageFromDate(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofNull();

        LocalDateTime birthDt = parseDt(s);
        if (birthDt == null) return DynValue.ofNull();

        LocalDate asOf;
        if (args.length >= 2) {
            String asOfStr = extractDateStr(args[1]);
            LocalDateTime asOfDt = asOfStr != null ? parseDt(asOfStr) : null;
            if (asOfDt == null) return DynValue.ofNull();
            asOf = asOfDt.toLocalDate();
        } else {
            asOf = LocalDate.now(ZoneOffset.UTC);
        }

        if (birthDt.toLocalDate().isAfter(asOf)) return DynValue.ofNull();

        int age = asOf.getYear() - birthDt.getYear();
        if (asOf.getMonthValue() < birthDt.getMonthValue() ||
                (asOf.getMonthValue() == birthDt.getMonthValue() && asOf.getDayOfMonth() < birthDt.getDayOfMonth())) {
            age--;
        }
        return DynValue.ofInteger(age);
    }

    private static DynValue isValidDate(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        String s = extractDateStr(args[0]);
        if (s == null) return DynValue.ofBool(false);

        var parsed = DateUtils.parseDate(s);
        if (parsed == null) return DynValue.ofBool(false);

        return DynValue.ofBool(DateUtils.isValidDate(parsed.year(), parsed.month(), parsed.day()));
    }

    private static DynValue formatLocaleDate(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String dateStr = extractDateStr(args[0]);
        String locale = args[1].asString();
        if (dateStr == null || locale == null) return DynValue.ofNull();

        LocalDateTime dt = parseDt(dateStr);
        if (dt == null) return DynValue.ofNull();

        Locale loc;
        try {
            loc = Locale.forLanguageTag(locale.replace("_", "-"));
        } catch (Exception e) {
            loc = Locale.US;
        }

        String format = "d"; // default short
        if (args.length >= 3) {
            String fmtArg = args[2].asString();
            if (fmtArg != null) {
                if ("short".equals(fmtArg)) format = "d";
                else if ("long".equals(fmtArg)) format = "D";
                else format = fmtArg;
            }
        }

        try {
            java.time.format.DateTimeFormatter formatter;
            if ("d".equals(format)) {
                formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(loc);
            } else if ("D".equals(format)) {
                formatter = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(loc);
            } else {
                String javaFmt = format.replace("YYYY", "yyyy").replace("DD", "dd");
                formatter = java.time.format.DateTimeFormatter.ofPattern(javaFmt, loc);
            }
            return DynValue.ofString(dt.toLocalDate().format(formatter));
        } catch (Exception e) {
            return DynValue.ofString(formatAsDate(dt));
        }
    }

    // ── businessDays ──

    private static DynValue businessDays(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();

        String dateStr = extractDateStr(args[0]);
        var dt = parseDt(dateStr);
        if (dt == null) return DynValue.ofNull();

        Long countLong = extractLong(args[1]);
        if (countLong == null) return DynValue.ofNull();
        int count = countLong.intValue();

        LocalDate date = dt.toLocalDate();
        int direction = count >= 0 ? 1 : -1;
        int absCount = Math.abs(count);
        int fullWeeks = absCount / 5;
        int remaining = absCount % 5;

        // O(1): 5 business days == 7 calendar days
        date = date.plusDays((long) direction * fullWeeks * 7);

        while (remaining > 0) {
            date = date.plusDays(direction);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }

        return DynValue.ofString(formatAsDate(date.atStartOfDay()));
    }

    // ── nextBusinessDay ──

    private static DynValue nextBusinessDay(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();

        String dateStr = extractDateStr(args[0]);
        var dt = parseDt(dateStr);
        if (dt == null) return DynValue.ofNull();

        LocalDate date = dt.toLocalDate();
        // Advance at least one day, then skip weekends.
        do {
            date = date.plusDays(1);
        } while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY);

        return DynValue.ofString(formatAsDate(date.atStartOfDay()));
    }

    // ── formatDuration ──

    private static final java.util.regex.Pattern DURATION_PATTERN =
            java.util.regex.Pattern.compile("^P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$");

    private static final java.util.regex.Pattern NUMERIC_SECONDS_PATTERN =
            java.util.regex.Pattern.compile("^\\d+(?:\\.\\d+)?$");

    private static boolean isNumeric(DynValue v) {
        switch (v.getType()) {
            case Integer:
            case Float:
            case FloatRaw:
            case Currency:
            case CurrencyRaw:
            case Percent:
                return true;
            default:
                return false;
        }
    }

    private static DynValue formatDuration(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();

        var arg = args[0];

        int years = 0, months = 0, days = 0, hours = 0, minutes = 0;
        double seconds = 0.0;

        if (isNumeric(arg)) {
            // Numeric seconds: expand into days/hours/minutes/seconds.
            Double total = VerbHelpers.coerceNum(arg);
            if (total == null || !Double.isFinite(total) || total < 0) return DynValue.ofNull();
            days = (int) Math.floor(total / 86400);
            total -= days * 86400.0;
            hours = (int) Math.floor(total / 3600);
            total -= hours * 3600.0;
            minutes = (int) Math.floor(total / 60);
            seconds = total - minutes * 60.0;
        } else {
            String input = arg.asString();
            if (input == null || input.isEmpty()) return DynValue.ofNull();

            if (NUMERIC_SECONDS_PATTERN.matcher(input).matches()) {
                // Numeric seconds passed as a string.
                double total = Double.parseDouble(input);
                days = (int) Math.floor(total / 86400);
                total -= days * 86400.0;
                hours = (int) Math.floor(total / 3600);
                total -= hours * 3600.0;
                minutes = (int) Math.floor(total / 60);
                seconds = total - minutes * 60.0;
            } else {
                var matcher = DURATION_PATTERN.matcher(input);
                if (!matcher.matches()) return DynValue.ofNull();
                years = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
                months = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
                days = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
                hours = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
                minutes = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
                seconds = matcher.group(6) != null ? Double.parseDouble(matcher.group(6)) : 0.0;
            }
        }

        var parts = new ArrayList<String>();
        if (years > 0) parts.add(years + " " + (years == 1 ? "year" : "years"));
        if (months > 0) parts.add(months + " " + (months == 1 ? "month" : "months"));
        if (days > 0) parts.add(days + " " + (days == 1 ? "day" : "days"));
        if (hours > 0) parts.add(hours + " " + (hours == 1 ? "hour" : "hours"));
        if (minutes > 0) parts.add(minutes + " " + (minutes == 1 ? "minute" : "minutes"));
        if (seconds > 0) {
            String secStr = (seconds == Math.floor(seconds))
                    ? String.valueOf((int) seconds)
                    : String.format("%.1f", seconds);
            parts.add(secStr + " " + (seconds == 1.0 ? "second" : "seconds"));
        }

        if (parts.isEmpty()) return DynValue.ofString("0 seconds");

        return DynValue.ofString(String.join(", ", parts));
    }
}
