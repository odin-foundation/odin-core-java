package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiFunction;

public final class CoercionVerbs {

    private CoercionVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("coerceString", CoercionVerbs::coerceString);
        reg.put("coerceNumber", CoercionVerbs::coerceNumber);
        reg.put("coerceInteger", CoercionVerbs::coerceInteger);
        reg.put("coerceBoolean", CoercionVerbs::coerceBoolean);
        reg.put("coerceDate", CoercionVerbs::coerceDate);
        reg.put("coerceTimestamp", CoercionVerbs::coerceTimestamp);
        reg.put("tryCoerce", CoercionVerbs::tryCoerce);
        reg.put("toArray", CoercionVerbs::toArray);
        reg.put("toObject", CoercionVerbs::toObject);
    }

    private static DynValue coerceString(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceString: requires 1 argument");
        if (args[0].isNull()) return DynValue.ofNull();
        return DynValue.ofString(VerbHelpers.coerceStr(args[0]));
    }

    private static double toNumber(DynValue v) {
        Double d = VerbHelpers.coerceNum(v);
        return d != null ? d : 0.0;
    }

    private static DynValue numericResult(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return DynValue.ofFloat(v);
        double rounded = Math.rint(v);
        if (Math.abs(v - rounded) < 1e-10 && Math.abs(rounded) < (double) Long.MAX_VALUE)
            return DynValue.ofInteger((long) rounded);
        return DynValue.ofFloat(v);
    }

    private static DynValue coerceNumber(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return numericResult(toNumber(args[0]));
    }

    private static DynValue coerceInteger(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofInteger((long) Math.floor(toNumber(args[0])));
    }

    private static DynValue coerceBoolean(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var val = args[0];
        if (val.isNull()) return DynValue.ofBool(false);

        return switch (val.getType()) {
            case Bool -> val;
            case String -> {
                var s = val.asString().trim().toLowerCase(Locale.ROOT);
                yield DynValue.ofBool(s.equals("true") || s.equals("yes")
                        || s.equals("y") || s.equals("1"));
            }
            case Integer -> DynValue.ofBool(val.asInt64() != 0);
            case Float -> DynValue.ofBool(val.asDouble() != 0.0);
            default -> DynValue.ofBool(VerbHelpers.isTruthy(val));
        };
    }

    private static DynValue coerceDate(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();

        if (val.getType() == DynValue.Type.Date) return val;
        if (val.getType() == DynValue.Type.Timestamp) {
            var s = val.asString();
            int t = s.indexOf('T');
            return DynValue.ofDate(t >= 0 ? s.substring(0, t) : s);
        }
        if (val.getType() == DynValue.Type.Integer) {
            var dt = Instant.ofEpochSecond(val.asInt64()).atOffset(ZoneOffset.UTC);
            return DynValue.ofDate(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        var s = VerbHelpers.coerceStr(val);
        if (s.isEmpty()) return DynValue.ofNull();

        if (s.length() >= 10 && isValidDatePrefix(s)) {
            var datePart = s.substring(0, 10);
            int month = Integer.parseInt(datePart.substring(5, 7));
            int day = Integer.parseInt(datePart.substring(8, 10));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31)
                return DynValue.ofDate(datePart);
            return DynValue.ofNull();
        }

        var compact = s.matches("\\d{8}") ? s : null;
        if (compact != null) {
            int y = Integer.parseInt(s.substring(0, 4));
            int mo = Integer.parseInt(s.substring(4, 6));
            int d = Integer.parseInt(s.substring(6, 8));
            if (validYmd(y, mo, d)) return DynValue.ofDate(String.format("%04d-%02d-%02d", y, mo, d));
            return DynValue.ofNull();
        }

        var slash = java.util.regex.Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matcher(s);
        if (slash.matches()) {
            int first = Integer.parseInt(slash.group(1));
            int second = Integer.parseInt(slash.group(2));
            int y = Integer.parseInt(slash.group(3));
            int mo, d;
            if (first > 12) { d = first; mo = second; } else { mo = first; d = second; }
            if (validYmd(y, mo, d)) return DynValue.ofDate(String.format("%04d-%02d-%02d", y, mo, d));
            return DynValue.ofNull();
        }

        return DynValue.ofNull();
    }

    private static boolean validYmd(int y, int mo, int d) {
        if (mo < 1 || mo > 12 || d < 1) return false;
        int[] days = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int max = days[mo - 1];
        if (mo == 2 && ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0)) max = 29;
        return d <= max;
    }

    private static DynValue coerceTimestamp(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceTimestamp: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();

        var s = val.asString();
        if (s == null)
            throw new IllegalStateException("coerceTimestamp: expected string argument");

        if (s.length() >= 19 && isValidDatePrefix(s)) {
            char sep = s.charAt(10);
            if ((sep == 'T' || sep == ' ')
                    && Character.isDigit(s.charAt(11)) && Character.isDigit(s.charAt(12))
                    && s.charAt(13) == ':'
                    && Character.isDigit(s.charAt(14)) && Character.isDigit(s.charAt(15))
                    && s.charAt(16) == ':'
                    && Character.isDigit(s.charAt(17)) && Character.isDigit(s.charAt(18))) {
                return DynValue.ofTimestamp(s);
            }
        }

        if (s.length() == 10 && isValidDatePrefix(s))
            return DynValue.ofTimestamp(s + "T00:00:00");

        throw new IllegalStateException("coerceTimestamp: '" + s + "' is not a valid timestamp");
    }

    private static DynValue tryCoerce(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var val = args[0];
        if (val.getType() != DynValue.Type.String) return val;

        var s = val.asString();
        if (s == null) return DynValue.ofNull();

        // Try integer
        try { return DynValue.ofInteger(Long.parseLong(s)); }
        catch (NumberFormatException ignored) {}

        // Try float
        try { return DynValue.ofFloat(Double.parseDouble(s)); }
        catch (NumberFormatException ignored) {}

        // Try boolean
        if ("true".equals(s)) return DynValue.ofBool(true);
        if ("false".equals(s)) return DynValue.ofBool(false);

        // Try date
        if (s.length() == 10 && isValidDatePrefix(s))
            return DynValue.ofDate(s);

        return val;
    }

    private static DynValue toArray(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());
        var val = args[0];
        if (val.getType() == DynValue.Type.Array) return val;
        return DynValue.ofArray(new ArrayList<>(List.of(val)));
    }

    private static DynValue toObject(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();
        if (val.getType() == DynValue.Type.Object) return val;

        if (val.getType() == DynValue.Type.Array) {
            var arr = val.asArray();
            var entries = new ArrayList<Map.Entry<String, DynValue>>();
            for (var item : arr) {
                var pair = item.asArray();
                if (pair != null && pair.size() >= 2) {
                    entries.add(Map.entry(VerbHelpers.coerceStr(pair.get(0)), pair.get(1)));
                } else if (item.getType() == DynValue.Type.Object) {
                    var keyV = item.get("key");
                    var valV = item.get("value");
                    if (keyV != null) {
                        entries.add(Map.entry(VerbHelpers.coerceStr(keyV),
                                valV != null ? valV : DynValue.ofNull()));
                    }
                }
            }
            if (!entries.isEmpty()) return DynValue.ofObject(entries);
        }

        return DynValue.ofNull();
    }

    static boolean isValidDatePrefix(String s) {
        if (s.length() < 10) return false;
        return Character.isDigit(s.charAt(0)) && Character.isDigit(s.charAt(1))
                && Character.isDigit(s.charAt(2)) && Character.isDigit(s.charAt(3))
                && s.charAt(4) == '-'
                && Character.isDigit(s.charAt(5)) && Character.isDigit(s.charAt(6))
                && s.charAt(7) == '-'
                && Character.isDigit(s.charAt(8)) && Character.isDigit(s.charAt(9));
    }
}
