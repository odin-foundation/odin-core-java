package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.Map;
import java.util.function.BiFunction;

public final class LogicVerbs {

    private LogicVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("and", LogicVerbs::and);
        reg.put("or", LogicVerbs::or);
        reg.put("not", LogicVerbs::not);
        reg.put("xor", LogicVerbs::xor);
        reg.put("eq", LogicVerbs::eq);
        reg.put("ne", LogicVerbs::ne);
        reg.put("lt", LogicVerbs::lt);
        reg.put("lte", LogicVerbs::lte);
        reg.put("gt", LogicVerbs::gt);
        reg.put("gte", LogicVerbs::gte);
        reg.put("between", LogicVerbs::between);
        reg.put("isNull", LogicVerbs::isNull);
        reg.put("isString", LogicVerbs::isString);
        reg.put("isNumber", LogicVerbs::isNumber);
        reg.put("isBoolean", LogicVerbs::isBoolean);
        reg.put("isArray", LogicVerbs::isArray);
        reg.put("isObject", LogicVerbs::isObject);
        reg.put("isDate", LogicVerbs::isDate);
        reg.put("typeOf", LogicVerbs::typeOf);
        reg.put("cond", LogicVerbs::cond);
        reg.put("assert", LogicVerbs::doAssert);
        reg.put("switch", LogicVerbs::doSwitch);
        reg.put("isFinite", LogicVerbs::isFinite);
        reg.put("isNaN", LogicVerbs::isNaN);
    }

    // ── Boolean Logic ──

    private static DynValue and(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("and: requires 2 arguments");
        var a = args[0].asBool();
        var b = args[1].asBool();
        if (a == null || b == null)
            throw new IllegalStateException("and: expected boolean arguments");
        return DynValue.ofBool(a && b);
    }

    private static DynValue or(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("or: requires 2 arguments");
        var a = args[0].asBool();
        var b = args[1].asBool();
        if (a == null || b == null)
            throw new IllegalStateException("or: expected boolean arguments");
        return DynValue.ofBool(a || b);
    }

    private static DynValue not(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        var val = args[0];
        return switch (val.getType()) {
            case Bool -> DynValue.ofBool(!val.asBool());
            case Null -> DynValue.ofBool(true);
            case Integer -> DynValue.ofBool(val.asInt64() == 0);
            case Float -> DynValue.ofBool(val.asDouble() == 0.0);
            case String -> {
                var s = val.asString();
                yield DynValue.ofBool(s == null || s.isEmpty() || "false".equals(s));
            }
            case Array -> {
                var arr = val.asArray();
                yield DynValue.ofBool(arr == null || arr.isEmpty());
            }
            default -> DynValue.ofBool(false);
        };
    }

    private static DynValue xor(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("xor: requires 2 arguments");
        var a = args[0].asBool();
        var b = args[1].asBool();
        if (a == null || b == null)
            throw new IllegalStateException("xor: expected boolean arguments");
        return DynValue.ofBool(a ^ b);
    }

    // ── Equality ──

    private static DynValue eq(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("eq: requires 2 arguments");
        return DynValue.ofBool(VerbHelpers.dynValuesEqual(args[0], args[1]));
    }

    private static DynValue ne(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("ne: requires 2 arguments");
        return DynValue.ofBool(!VerbHelpers.dynValuesEqual(args[0], args[1]));
    }

    // ── Ordering Comparisons ──

    private static DynValue lt(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("lt: requires 2 arguments");
        var a = toF64ForCmp(args[0]);
        var b = toF64ForCmp(args[1]);
        if (a != null && b != null) return DynValue.ofBool(a < b);
        var sa = args[0].asString();
        var sb = args[1].asString();
        if (sa != null && sb != null) return DynValue.ofBool(sa.compareTo(sb) < 0);
        return DynValue.ofBool(false);
    }

    private static DynValue lte(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("lte: requires 2 arguments");
        var a = toF64ForCmp(args[0]);
        var b = toF64ForCmp(args[1]);
        if (a != null && b != null) return DynValue.ofBool(a <= b);
        var sa = args[0].asString();
        var sb = args[1].asString();
        if (sa != null && sb != null) return DynValue.ofBool(sa.compareTo(sb) <= 0);
        return DynValue.ofBool(false);
    }

    private static DynValue gt(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("gt: requires 2 arguments");
        var a = toF64ForCmp(args[0]);
        var b = toF64ForCmp(args[1]);
        if (a != null && b != null) return DynValue.ofBool(a > b);
        var sa = args[0].asString();
        var sb = args[1].asString();
        if (sa != null && sb != null) return DynValue.ofBool(sa.compareTo(sb) > 0);
        return DynValue.ofBool(false);
    }

    private static DynValue gte(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("gte: requires 2 arguments");
        var a = toF64ForCmp(args[0]);
        var b = toF64ForCmp(args[1]);
        if (a != null && b != null) return DynValue.ofBool(a >= b);
        var sa = args[0].asString();
        var sb = args[1].asString();
        if (sa != null && sb != null) return DynValue.ofBool(sa.compareTo(sb) >= 0);
        return DynValue.ofBool(false);
    }

    private static DynValue between(DynValue[] args, VerbContext ctx) {
        if (args.length < 3)
            throw new IllegalStateException("between: requires 3 arguments (value, min, max)");
        var val = toF64ForCmp(args[0]);
        var min = toF64ForCmp(args[1]);
        var max = toF64ForCmp(args[2]);
        if (val != null && min != null && max != null)
            return DynValue.ofBool(val >= min && val <= max);

        var sVal = args[0].asString();
        var sMin = args[1].asString();
        var sMax = args[2].asString();
        if (sVal != null && sMin != null && sMax != null)
            return DynValue.ofBool(sVal.compareTo(sMin) >= 0 && sVal.compareTo(sMax) <= 0);

        throw new IllegalStateException("between: expected numeric or string arguments");
    }

    // ── Type Checks ──

    private static DynValue isNull(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(true);
        return DynValue.ofBool(args[0].isNull());
    }

    private static DynValue isString(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        return DynValue.ofBool(args[0].getType() == DynValue.Type.String);
    }

    private static DynValue isNumber(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        return DynValue.ofBool(args[0].getType() == DynValue.Type.Integer || args[0].getType() == DynValue.Type.Float);
    }

    private static DynValue isBoolean(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        return DynValue.ofBool(args[0].getType() == DynValue.Type.Bool);
    }

    private static DynValue isArray(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        if (args[0].getType() == DynValue.Type.Array) return DynValue.ofBool(true);
        if (args[0].getType() == DynValue.Type.String) {
            var s = args[0].asString().trim();
            return DynValue.ofBool(s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']');
        }
        return DynValue.ofBool(false);
    }

    private static DynValue isObject(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        if (args[0].getType() == DynValue.Type.Object) return DynValue.ofBool(true);
        if (args[0].getType() == DynValue.Type.String) {
            var s = args[0].asString().trim();
            return DynValue.ofBool(s.length() >= 2 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}');
        }
        return DynValue.ofBool(false);
    }

    private static DynValue isDate(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        if (args[0].getType() == DynValue.Type.Date) return DynValue.ofBool(true);
        if (args[0].getType() == DynValue.Type.String) {
            var s = args[0].asString();
            return DynValue.ofBool(s.length() >= 10 && CoercionVerbs.isValidDatePrefix(s));
        }
        return DynValue.ofBool(false);
    }

    // ── Type Inspection ──

    private static DynValue typeOf(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofString("null");
        String typeName = switch (args[0].getType()) {
            case Null -> "null";
            case Bool -> "boolean";
            case String -> "string";
            case Integer -> "integer";
            case Float, FloatRaw -> "number";
            case Currency, CurrencyRaw -> "currency";
            case Percent -> "percent";
            case Reference -> "reference";
            case Binary -> "binary";
            case Date -> "date";
            case Timestamp -> "timestamp";
            case Time -> "time";
            case Duration -> "duration";
            case Array -> "array";
            case Object -> "object";
        };
        return DynValue.ofString(typeName);
    }

    // ── Conditional ──

    private static DynValue cond(DynValue[] args, VerbContext ctx) {
        int i = 0;
        while (i + 1 < args.length) {
            if (VerbHelpers.isTruthy(args[i])) return args[i + 1];
            i += 2;
        }
        if (i < args.length) return args[i];
        return DynValue.ofNull();
    }

    private static DynValue doAssert(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        // Pass the condition through when truthy; a failing assertion yields null
        // and the transform continues. A second string argument is diagnostic only.
        if (VerbHelpers.isTruthy(args[0])) return args[0];
        return DynValue.ofNull();
    }

    private static DynValue doSwitch(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        var value = args[0];
        int i = 1;
        while (i + 1 < args.length) {
            if (VerbHelpers.dynValuesEqual(value, args[i])) return args[i + 1];
            i += 2;
        }
        // If remaining arg, it's the default
        if (i < args.length) return args[i];
        return DynValue.ofNull();
    }

    private static DynValue isFinite(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        var val = args[0];
        if (val.getType() == DynValue.Type.Integer) return DynValue.ofBool(true);
        if (val.getType() == DynValue.Type.Float) {
            double d = val.asDouble();
            return DynValue.ofBool(Double.isFinite(d));
        }
        return DynValue.ofBool(false);
    }

    private static DynValue isNaN(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(true);
        var val = args[0];
        if (val.getType() == DynValue.Type.Float) {
            return DynValue.ofBool(Double.isNaN(val.asDouble()));
        }
        if (val.getType() == DynValue.Type.Integer) return DynValue.ofBool(false);
        if (val.getType() == DynValue.Type.String) {
            try {
                Double.parseDouble(val.asString());
                return DynValue.ofBool(false);
            } catch (NumberFormatException e) {
                return DynValue.ofBool(true);
            }
        }
        return DynValue.ofBool(true);
    }

    // ── Helpers ──

    private static Double toF64ForCmp(DynValue val) {
        return switch (val.getType()) {
            case Integer -> (double) val.asInt64();
            case Float -> val.asDouble();
            case Currency, Percent -> val.asDouble();
            case FloatRaw, CurrencyRaw -> {
                var s = val.asString();
                if (s != null) {
                    try { yield Double.parseDouble(s); }
                    catch (NumberFormatException e) { yield null; }
                }
                yield null;
            }
            case Bool -> (val.asBool() != null && val.asBool()) ? 1.0 : 0.0;
            case Null -> 0.0;
            case String -> {
                var s = val.asString();
                if (s != null) {
                    try { yield Double.parseDouble(s); }
                    catch (NumberFormatException e) { yield null; }
                }
                yield null;
            }
            default -> null;
        };
    }
}
