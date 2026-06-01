package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class AggregationVerbs {

    private AggregationVerbs() {}

    private static final double MAX_SAFE_INTEGER = 9007199254740991.0; // 2^53 - 1

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("accumulate", AggregationVerbs::accumulate);
        reg.put("set", AggregationVerbs::set);
        reg.put("sum", AggregationVerbs::sum);
        reg.put("count", AggregationVerbs::count);
        reg.put("min", AggregationVerbs::min);
        reg.put("max", AggregationVerbs::max);
        reg.put("avg", AggregationVerbs::avg);
        reg.put("first", AggregationVerbs::first);
        reg.put("last", AggregationVerbs::last);
    }

    // ── Helpers ──

    private static Double toDouble(DynValue v) {
        if (v.isNull()) return null;
        Double d = v.asDouble();
        if (d != null) return d;
        String s = v.asString();
        if (s != null) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static DynValue numericResult(double v) {
        if (v == Math.floor(v) && Math.abs(v) < (double) Long.MAX_VALUE)
            return DynValue.ofInteger((long) v);
        return DynValue.ofFloat(v);
    }

    private static List<DynValue> extractItems(DynValue arg) {
        List<DynValue> arr = arg.asArray();
        if (arr != null) return arr;
        return arg.extractArray();
    }

    // ── Verb Implementations ──

    private static DynValue accumulate(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String accName = args[0].asString();
        if (accName == null) return DynValue.ofNull();

        DynValue current = ctx != null && ctx.getAccumulators().containsKey(accName)
                ? ctx.getAccumulators().get(accName) : DynValue.ofNull();

        if (args.length == 2) {
            double cv = toDouble(current) != null ? toDouble(current) : 0.0;
            double vv = toDouble(args[1]) != null ? toDouble(args[1]) : 0.0;
            double sum = cv + vv;

            // T008: the running sum is no longer exactly representable (non-finite, or
            // an integer accumulator beyond the safe-integer magnitude). Retain the
            // last valid value.
            boolean integerAccumulator = current.getType() == DynValue.Type.Integer;
            boolean overflowed = !Double.isFinite(sum)
                    || (integerAccumulator && Math.abs(sum) > MAX_SAFE_INTEGER);
            if (overflowed) {
                if (ctx != null) ctx.reportAccumulatorOverflow(accName, sum);
                return current;
            }

            current = numericResult(sum);
            if (ctx != null) ctx.getAccumulators().put(accName, current);
            return current;
        }

        String verbName = args[1].asString();
        if (verbName == null) {
            double cv = toDouble(current) != null ? toDouble(current) : 0.0;
            double vv = toDouble(args[1]) != null ? toDouble(args[1]) : 0.0;
            current = numericResult(cv + vv);
            if (ctx != null) ctx.getAccumulators().put(accName, current);
            return current;
        }

        DynValue value = args[2];

        switch (verbName) {
            case "sum", "add" -> {
                double cv = toDouble(current) != null ? toDouble(current) : 0.0;
                double vv = toDouble(value) != null ? toDouble(value) : 0.0;
                current = numericResult(cv + vv);
            }
            case "count" -> {
                double cv = toDouble(current) != null ? toDouble(current) : 0.0;
                current = numericResult(cv + 1.0);
            }
            case "min" -> {
                Double vv = toDouble(value);
                if (vv != null) {
                    Double cv = toDouble(current);
                    current = (cv == null || vv < cv) ? numericResult(vv) : current;
                }
            }
            case "max" -> {
                Double vv = toDouble(value);
                if (vv != null) {
                    Double cv = toDouble(current);
                    current = (cv == null || vv > cv) ? numericResult(vv) : current;
                }
            }
            case "concat" -> {
                String cs = current.isNull() ? "" : (current.asString() != null ? current.asString() : current.toString());
                String vs = value.isNull() ? "" : (value.asString() != null ? value.asString() : value.toString());
                current = DynValue.ofString(cs + vs);
            }
            case "first" -> {
                if (current.isNull()) current = value;
            }
            case "last" -> current = value;
            default -> current = value;
        }

        if (ctx != null) ctx.getAccumulators().put(accName, current);
        return current;
    }

    private static DynValue set(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String accName = args[0].asString();
        if (accName == null) return DynValue.ofNull();
        if (ctx != null) ctx.getAccumulators().put(accName, args[1]);
        return args[1];
    }

    private static DynValue sum(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofInteger(0);
        List<DynValue> items = extractItems(args[0]);
        if (items == null) {
            double total = 0;
            for (DynValue arg : args) {
                Double v = toDouble(arg);
                if (v != null) total += v;
            }
            return numericResult(total);
        }
        double s = 0;
        for (DynValue item : items) {
            Double v = toDouble(item);
            if (v != null) s += v;
        }
        return numericResult(s);
    }

    private static DynValue count(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofInteger(0);
        List<DynValue> items = extractItems(args[0]);
        if (items == null) return DynValue.ofInteger(args.length);
        return DynValue.ofInteger(items.size());
    }

    private static DynValue min(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<DynValue> items = extractItems(args[0]);
        if (items == null) items = new ArrayList<>(List.of(args));
        Double result = null;
        for (DynValue item : items) {
            Double v = toDouble(item);
            if (v != null && (result == null || v < result)) result = v;
        }
        return result != null ? numericResult(result) : DynValue.ofNull();
    }

    private static DynValue max(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<DynValue> items = extractItems(args[0]);
        if (items == null) items = new ArrayList<>(List.of(args));
        Double result = null;
        for (DynValue item : items) {
            Double v = toDouble(item);
            if (v != null && (result == null || v > result)) result = v;
        }
        return result != null ? numericResult(result) : DynValue.ofNull();
    }

    private static DynValue avg(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<DynValue> items = extractItems(args[0]);
        if (items == null) items = new ArrayList<>(List.of(args));
        double s = 0;
        int c = 0;
        for (DynValue item : items) {
            Double v = toDouble(item);
            if (v != null) { s += v; c++; }
        }
        if (c == 0) return DynValue.ofNull();
        return numericResult(s / c);
    }

    private static DynValue first(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<DynValue> items = extractItems(args[0]);
        if (items == null) return args[0];
        return items.isEmpty() ? DynValue.ofNull() : items.get(0);
    }

    private static DynValue last(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<DynValue> items = extractItems(args[0]);
        if (items == null) return args[args.length - 1];
        return items.isEmpty() ? DynValue.ofNull() : items.get(items.size() - 1);
    }
}
