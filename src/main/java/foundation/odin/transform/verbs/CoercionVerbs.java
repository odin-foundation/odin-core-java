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

    private static DynValue coerceNumber(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceNumber: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();

        return switch (val.getType()) {
            case Integer -> DynValue.ofFloat((double) val.asInt64());
            case Float -> val;
            case String -> {
                var s = val.asString();
                try {
                    yield DynValue.ofFloat(Double.parseDouble(s));
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("coerceNumber: cannot parse '" + s + "' as number");
                }
            }
            case Bool -> DynValue.ofFloat(val.asBool() ? 1.0 : 0.0);
            default -> throw new IllegalStateException("coerceNumber: unsupported type");
        };
    }

    private static DynValue coerceInteger(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceInteger: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();

        return switch (val.getType()) {
            case Integer -> val;
            case Float -> DynValue.ofInteger((long) val.asDouble().doubleValue());
            case String -> {
                var s = val.asString();
                try {
                    yield DynValue.ofInteger(Long.parseLong(s));
                } catch (NumberFormatException e) {
                    try {
                        yield DynValue.ofInteger((long) Double.parseDouble(s));
                    } catch (NumberFormatException e2) {
                        throw new IllegalStateException("coerceInteger: cannot parse '" + s + "' as integer");
                    }
                }
            }
            case Bool -> DynValue.ofInteger(val.asBool() ? 1L : 0L);
            default -> throw new IllegalStateException("coerceInteger: unsupported type");
        };
    }

    private static DynValue coerceBoolean(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceBoolean: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofBool(false);

        return switch (val.getType()) {
            case Bool -> val;
            case String -> {
                var s = val.asString().trim().toLowerCase(Locale.ROOT);
                boolean isFalsy = s.isEmpty() || "false".equals(s) || "0".equals(s) || "no".equals(s) || "n".equals(s) || "off".equals(s);
                yield DynValue.ofBool(!isFalsy);
            }
            case Integer -> DynValue.ofBool(val.asInt64() != 0);
            case Float -> DynValue.ofBool(val.asDouble() != 0.0);
            default -> throw new IllegalStateException("coerceBoolean: unsupported type");
        };
    }

    private static DynValue coerceDate(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("coerceDate: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();

        return switch (val.getType()) {
            case String, Date, Timestamp -> {
                var s = val.asString();
                if (s.length() >= 10 && isValidDatePrefix(s)) {
                    var datePart = s.substring(0, 10);
                    try {
                        int month = Integer.parseInt(datePart.substring(5, 7));
                        int day = Integer.parseInt(datePart.substring(8, 10));
                        if (month >= 1 && month <= 12 && day >= 1 && day <= 31)
                            yield DynValue.ofDate(datePart);
                    } catch (NumberFormatException ignored) {}
                }
                throw new IllegalStateException("coerceDate: '" + s + "' is not a valid date");
            }
            case Integer -> {
                var secs = val.asInt64();
                var dt = Instant.ofEpochSecond(secs).atOffset(ZoneOffset.UTC);
                yield DynValue.ofDate(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            }
            default -> throw new IllegalStateException("coerceDate: expected string argument");
        };
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
        if (args.length == 0)
            throw new IllegalStateException("toObject: requires 1 argument");
        var val = args[0];
        if (val.isNull()) return DynValue.ofNull();
        if (val.getType() == DynValue.Type.Object) return val;

        if (val.getType() == DynValue.Type.Array) {
            var arr = val.asArray();
            var entries = new ArrayList<Map.Entry<String, DynValue>>();
            for (var item : arr) {
                var pair = item.asArray();
                if (pair == null || pair.size() < 2)
                    throw new IllegalStateException("toObject: array elements must be [key, value] pairs");
                String key;
                if (pair.get(0).getType() == DynValue.Type.String)
                    key = pair.get(0).asString();
                else if (pair.get(0).getType() == DynValue.Type.Integer)
                    key = Long.toString(pair.get(0).asInt64());
                else
                    key = VerbHelpers.coerceStr(pair.get(0));
                entries.add(Map.entry(key, pair.get(1)));
            }
            return DynValue.ofObject(entries);
        }

        throw new IllegalStateException("toObject: expected array of [key, value] pairs");
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
