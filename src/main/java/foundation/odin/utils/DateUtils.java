package foundation.odin.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public final class DateUtils {
    private DateUtils() {}

    public record DateComponents(int year, int month, int day) {}

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static DateComponents parseDate(String raw) {
        if (raw.length() < 10) return null;
        String[] parts = raw.split("-");
        if (parts.length < 3) return null;

        try {
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            String dayPart = parts[2].length() > 2 ? parts[2].substring(0, 2) : parts[2];
            int day = Integer.parseInt(dayPart);

            if (month < 1 || month > 12 || day < 1 || day > 31)
                return null;

            return new DateComponents(year, month, day);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long parseTimestampToEpochMs(String raw) {
        try {
            Instant instant = Instant.parse(raw);
            return instant.toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String epochMsToTimestamp(long epochMs) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC);
        return dt.format(TIMESTAMP_FORMAT);
    }

    public static String today() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    public static String now() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return now.format(TIMESTAMP_FORMAT);
    }

    public static String addDays(String dateStr, int days) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate result = date.plusDays(days);
            return String.format("%04d-%02d-%02d", result.getYear(), result.getMonthValue(), result.getDayOfMonth());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String addMonths(String dateStr, int months) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalDate result = date.plusMonths(months);
            return String.format("%04d-%02d-%02d", result.getYear(), result.getMonthValue(), result.getDayOfMonth());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Long dateDiffDays(String date1, String date2) {
        try {
            LocalDate d1 = LocalDate.parse(date1);
            LocalDate d2 = LocalDate.parse(date2);
            return ChronoUnit.DAYS.between(d1, d2);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean isValidDate(int year, int month, int day) {
        if (month < 1 || month > 12 || day < 1) return false;
        int maxDay = daysInMonth(year, month);
        return day <= maxDay;
    }

    public static int daysInMonth(int year, int month) {
        if (month == 2)
            return isLeapYear(year) ? 29 : 28;
        return switch (month) {
            case 4, 6, 9, 11 -> 30;
            default -> 31;
        };
    }

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    public static String formatDate(int year, int month, int day, String format) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            return date.format(DateTimeFormatter.ofPattern(format));
        } catch (Exception e) {
            return String.format("%04d-%02d-%02d", year, month, day);
        }
    }
}
