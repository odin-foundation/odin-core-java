package foundation.odin.transform;

import foundation.odin.types.DynValue;

import java.util.*;

/** Argument type signatures for built-in verbs, used by strict-type validation. */
final class VerbSignatures {

    private VerbSignatures() {}

    private record Signature(String[] args, String variadic) {}

    private static final Map<String, Signature> SIGS = new HashMap<>();

    private static void sig(String name, String... args) { SIGS.put(name, new Signature(args, null)); }
    private static void variadic(String name, String v, String... args) { SIGS.put(name, new Signature(args, v)); }

    static {
        // String
        for (String s : new String[]{"upper", "lower", "trim", "trimLeft", "trimRight", "capitalize",
                "titleCase", "length", "reverseString", "camelCase", "snakeCase", "kebabCase",
                "pascalCase", "slugify", "normalizeSpace", "stripAccents", "clean", "wordCount", "soundex"})
            sig(s, "string");
        variadic("concat", "any");
        sig("contains", "string", "string");
        sig("startsWith", "string", "string");
        sig("endsWith", "string", "string");
        sig("substring", "string", "integer", "integer");
        sig("replace", "string", "string", "string");
        sig("replaceRegex", "string", "string", "string");
        sig("padLeft", "string", "integer", "string");
        sig("padRight", "string", "integer", "string");
        sig("pad", "string", "integer", "string");
        sig("truncate", "string", "integer");
        sig("split", "string", "string", "integer");
        sig("join", "array", "string");
        sig("mask", "string", "integer");
        sig("repeat", "string", "integer");
        sig("match", "string", "string");
        sig("matches", "string", "string");
        sig("extract", "string", "string", "integer");
        sig("leftOf", "string", "string");
        sig("rightOf", "string", "string");
        sig("wrap", "string", "integer");
        sig("center", "string", "integer", "string");
        sig("tokenize", "string", "string");
        sig("levenshtein", "string", "string");

        // Coercion
        for (String s : new String[]{"coerceString", "coerceNumber", "coerceInteger", "coerceBoolean",
                "coerceDate", "coerceTimestamp", "tryCoerce"})
            sig(s, "any");

        // Numeric
        for (String s : new String[]{"abs", "floor", "ceil", "trunc", "sign", "negate", "sqrt",
                "ln", "log10", "exp", "formatCurrency", "isFinite", "isNaN"})
            sig(s, "number");
        sig("round", "number", "integer");
        for (String s : new String[]{"add", "subtract", "multiply", "divide", "mod", "pow", "log"})
            sig(s, "number", "number");
        sig("formatNumber", "number", "integer");
        sig("formatInteger", "integer");
        sig("formatPercent", "number", "integer");
        sig("clamp", "number", "number", "number");
        sig("interpolate", "number", "number", "number", "number", "number");
        sig("random", "number", "number", "integer");
        sig("parseInt", "string", "integer");
        sig("safeDivide", "number", "number", "number");

        // Comparison (generic)
        for (String s : new String[]{"eq", "ne", "lt", "lte", "gt", "gte"})
            sig(s, "T", "T");
        sig("between", "T", "T", "T");

        // Logic
        for (String s : new String[]{"and", "or", "xor"}) sig(s, "boolean", "boolean");
        sig("not", "boolean");
        sig("ifElse", "boolean", "any", "any");
        sig("ifNull", "any", "any");
        sig("ifEmpty", "any", "any");
        variadic("coalesce", "any");
        variadic("switch", "any");
        variadic("cond", "any");

        // Type checks
        for (String s : new String[]{"isNull", "isString", "isNumber", "isBoolean", "isArray",
                "isObject", "isDate", "typeOf"})
            sig(s, "any");

        // Date/time
        sig("today");
        sig("now");
        sig("formatDate", "date", "string");
        sig("parseDate", "string", "string");
        sig("formatTime", "time", "string");
        sig("formatTimestamp", "timestamp", "string");
        sig("parseTimestamp", "string", "string");
        sig("addDays", "date", "integer");
        sig("addMonths", "date", "integer");
        sig("addYears", "date", "integer");
        sig("addHours", "timestamp", "integer");
        sig("addMinutes", "timestamp", "integer");
        sig("addSeconds", "timestamp", "integer");
        sig("dateDiff", "date", "date", "string");
        sig("daysBetweenDates", "date", "date");
        for (String s : new String[]{"startOfDay", "endOfDay", "startOfMonth", "endOfMonth",
                "startOfYear", "endOfYear", "dayOfWeek", "weekOfYear", "quarter", "isLeapYear"})
            sig(s, "date");
        sig("isBefore", "T", "T");
        sig("isAfter", "T", "T");
        sig("isBetween", "T", "T", "T");
        sig("toUnix", "timestamp");
        sig("fromUnix", "integer");
        sig("ageFromDate", "date", "date");
        sig("isValidDate", "any", "string");

        // Array
        sig("filter", "array", "string", "string", "any");
        sig("flatten", "array");
        sig("distinct", "array", "string");
        sig("sort", "array", "string", "string");
        sig("sortDesc", "array");
        sig("sortBy", "array", "string");
        sig("map", "array", "string");
        sig("indexOf", "array", "any");
        sig("at", "array", "integer");
        sig("slice", "array", "integer", "integer");
        sig("reverse", "array");
        sig("every", "array", "string", "string", "any");
        sig("some", "array", "string", "string", "any");
        sig("find", "array", "string", "string", "any");
        sig("findIndex", "array", "string", "string", "any");
        sig("includes", "array", "any");
        sig("concatArrays", "array", "array");
        sig("zip", "array", "array");
        sig("groupBy", "array", "string");
        sig("partition", "array", "string", "string", "any");
        sig("take", "array", "integer");
        sig("drop", "array", "integer");
        sig("chunk", "array", "integer");
        sig("range", "integer", "integer", "integer");
        sig("compact", "array");
        sig("pluck", "array", "string");
        sig("unique", "array");

        // Aggregation
        for (String s : new String[]{"sum", "count", "min", "max", "avg", "first", "last"})
            sig(s, "array");
        sig("accumulate", "string", "any");
        sig("set", "string", "any");
        variadic("minOf", "number");
        variadic("maxOf", "number");
        sig("weightedAvg", "array", "array");

        // Statistical
        for (String s : new String[]{"std", "stdSample", "variance", "varianceSample", "median",
                "mode", "cumsum", "cumprod"})
            sig(s, "array");
        sig("percentile", "array", "number");
        sig("quantile", "array", "number");
        sig("covariance", "array", "array");
        sig("correlation", "array", "array");
        sig("zscore", "number", "array");
        sig("shift", "array", "integer", "any");
        sig("diff", "array", "integer");
        sig("pctChange", "array", "integer");

        // Financial
        sig("compound", "number", "number", "integer");
        sig("discount", "number", "number", "integer");
        sig("pmt", "number", "number", "integer");
        sig("fv", "number", "number", "integer");
        sig("pv", "number", "number", "integer");
        sig("npv", "number", "array");
        sig("irr", "array", "number");
        sig("rate", "integer", "number", "number", "number");
        sig("nper", "number", "number", "number", "number");
        sig("depreciation", "number", "number", "integer");

        // Object
        sig("keys", "object");
        sig("values", "object");
        sig("entries", "object");
        sig("has", "object", "string");
        sig("get", "object", "string", "any");
        sig("merge", "object", "object");

        // Encoding
        for (String s : new String[]{"base64Encode", "base64Decode", "urlEncode", "urlDecode",
                "jsonDecode", "hexEncode", "hexDecode", "sha256", "sha512", "sha1", "md5", "crc32"})
            sig(s, "string");
        sig("jsonEncode", "any");

        // Generation
        sig("uuid", "string");
        sig("nanoid", "integer", "string");
        sig("sequence", "string");
        sig("resetSequence", "string");

        // Lookup
        variadic("lookup", "any", "string");
        variadic("lookupDefault", "any", "string");

        // Locale
        sig("formatLocaleNumber", "number", "string");
        sig("formatLocaleDate", "date", "string");

        // Dedup
        sig("dedupe", "array", "string");

        // Geo
        sig("distance", "number", "number", "number", "number", "string");
        sig("inBoundingBox", "number", "number", "number", "number", "number", "number");
        sig("toRadians", "number");
        sig("toDegrees", "number");
        sig("bearing", "number", "number", "number", "number");
        sig("midpoint", "number", "number", "number", "number");

        // Window/ranking
        sig("rowNumber", "any");
        sig("rank", "array", "string", "string");
        sig("lag", "array", "integer", "any");
        sig("lead", "array", "integer", "any");
    }

    // Returns a description of the first type violation, or null if valid.
    static String validate(String verb, DynValue[] args) {
        var sig = SIGS.get(verb);
        if (sig == null) return null;

        String generic = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) continue;
            String expected = i < sig.args.length ? sig.args[i]
                    : (sig.variadic != null ? sig.variadic : "any");
            String actual = valueType(args[i]);

            if ("T".equals(expected)) {
                if (generic == null) generic = actual;
                else if (!typesCompatible(generic, actual))
                    return "Arg " + (i + 1) + ": type '" + actual + "' is not compatible with '" + generic + "'";
                continue;
            }
            if ("any".equals(expected)) continue;
            if (!typeMatch(actual, expected))
                return "Arg " + (i + 1) + ": expected " + expected + ", got " + actual;
        }
        return null;
    }

    private static String valueType(DynValue v) {
        return switch (v.getType()) {
            case Null -> "null";
            case Bool -> "boolean";
            case Integer -> "integer";
            case Float, FloatRaw -> "number";
            case Currency, CurrencyRaw -> "currency";
            case Percent -> "number";
            case Date -> "date";
            case Timestamp -> "timestamp";
            case Time -> "time";
            case Duration -> "duration";
            case Reference -> "reference";
            case Binary -> "binary";
            case Array -> "array";
            case Object -> "object";
            case String -> "string";
        };
    }

    private static boolean typeMatch(String actual, String expected) {
        if ("null".equals(actual)) return true;
        if ("number".equals(expected))
            return actual.equals("number") || actual.equals("integer") || actual.equals("currency");
        return actual.equals(expected);
    }

    private static boolean typesCompatible(String a, String b) {
        if (a.equals(b)) return true;
        Set<String> numeric = Set.of("number", "integer", "currency");
        return numeric.contains(a) && numeric.contains(b);
    }
}
