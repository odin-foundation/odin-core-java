package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;

public final class VerbHelpers {

    private VerbHelpers() {}

    public static Double coerceNum(DynValue val) {
        switch (val.getType()) {
            case Integer:
                return (double) val.asInt64();
            case Float:
            case Currency:
            case Percent:
                return val.asDouble();
            case FloatRaw:
            case CurrencyRaw:
                var raw = val.asString();
                if (raw != null) {
                    try { return Double.parseDouble(raw); }
                    catch (NumberFormatException e) { return null; }
                }
                return null;
            case String:
                var s = val.asString();
                if (s != null) {
                    try { return Double.parseDouble(s); }
                    catch (NumberFormatException e) { return null; }
                }
                return null;
            case Bool:
                return val.asBool() ? 1.0 : 0.0;
            default:
                return null;
        }
    }

    public static String coerceStr(DynValue val) {
        switch (val.getType()) {
            case String:
            case Reference:
            case Binary:
            case Date:
            case Timestamp:
            case Time:
            case Duration:
            case FloatRaw:
            case CurrencyRaw:
                var s = val.asString();
                return s != null ? s : "";
            case Integer:
                return Long.toString(val.asInt64());
            case Float:
            case Currency:
            case Percent:
                return formatDouble(val.asDouble());
            case Bool:
                return val.asBool() ? "true" : "false";
            case Null:
                return "";
            case Array:
                var arr = val.asArray();
                return "[" + (arr != null ? arr.size() : 0) + " items]";
            case Object:
                return "[object]";
            default:
                return "";
        }
    }

    public static boolean isTruthy(DynValue val) {
        switch (val.getType()) {
            case Null:
                return false;
            case Bool:
                return val.asBool();
            case Integer:
                return val.asInt64() != 0;
            case Float:
            case Currency:
            case Percent:
                return val.asDouble() != 0.0;
            case String:
            case Reference:
            case Binary:
            case Date:
            case Timestamp:
            case Time:
            case Duration:
                var s = val.asString();
                return s != null && !s.isEmpty();
            case FloatRaw:
            case CurrencyRaw:
                var raw = val.asString();
                return raw != null && !raw.isEmpty() && !"0".equals(raw);
            case Array:
            case Object:
                return true;
            default:
                return false;
        }
    }

    public static boolean dynValuesEqual(DynValue a, DynValue b) {
        if (a.equals(b)) return true;

        // Cross-type numeric comparison (Integer vs Float)
        if ((a.getType() == DynValue.Type.Integer && b.getType() == DynValue.Type.Float)
                || (a.getType() == DynValue.Type.Float && b.getType() == DynValue.Type.Integer)) {
            var da = a.asDouble();
            var db = b.asDouble();
            if (da != null && db != null) return da.equals(db);
        }

        // String-number coercion
        if (a.getType() == DynValue.Type.String
                && (b.getType() == DynValue.Type.Integer || b.getType() == DynValue.Type.Float)) {
            return stringMatchesNumber(a.asString(), b);
        }
        if (b.getType() == DynValue.Type.String
                && (a.getType() == DynValue.Type.Integer || a.getType() == DynValue.Type.Float)) {
            return stringMatchesNumber(b.asString(), a);
        }

        return false;
    }

    private static boolean stringMatchesNumber(String s, DynValue num) {
        if (s == null) return false;

        if (num.getType() == DynValue.Type.Integer) {
            try {
                long parsed = Long.parseLong(s);
                return parsed == num.asInt64();
            } catch (NumberFormatException e) { /* fall through */ }
        }

        if (num.getType() == DynValue.Type.Float) {
            try {
                double parsed = Double.parseDouble(s);
                return parsed == num.asDouble();
            } catch (NumberFormatException e) { /* fall through */ }
        }

        return false;
    }

    private static String formatDouble(double d) {
        if (d == (long) d) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
