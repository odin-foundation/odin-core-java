package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.*;
import java.util.function.BiFunction;

public final class CollectionVerbs {

    private CollectionVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("filter", CollectionVerbs::filter);
        reg.put("flatten", CollectionVerbs::flatten);
        reg.put("distinct", CollectionVerbs::distinct);
        reg.put("unique", CollectionVerbs::unique);
        reg.put("sort", CollectionVerbs::sort);
        reg.put("sortDesc", CollectionVerbs::sortDesc);
        reg.put("sortBy", CollectionVerbs::sortBy);
        reg.put("map", CollectionVerbs::map);
        reg.put("indexOf", CollectionVerbs::indexOf);
        reg.put("at", CollectionVerbs::at);
        reg.put("slice", CollectionVerbs::slice);
        reg.put("reverse", CollectionVerbs::reverse);
        reg.put("every", CollectionVerbs::every);
        reg.put("some", CollectionVerbs::some);
        reg.put("find", CollectionVerbs::find);
        reg.put("findIndex", CollectionVerbs::findIndex);
        reg.put("includes", CollectionVerbs::includes);
        reg.put("concatArrays", CollectionVerbs::concatArrays);
        reg.put("zip", CollectionVerbs::zip);
        reg.put("groupBy", CollectionVerbs::groupBy);
        reg.put("partition", CollectionVerbs::partition);
        reg.put("take", CollectionVerbs::take);
        reg.put("drop", CollectionVerbs::drop);
        reg.put("chunk", CollectionVerbs::chunk);
        reg.put("range", CollectionVerbs::range);
        reg.put("compact", CollectionVerbs::compact);
        reg.put("pluck", CollectionVerbs::pluck);
        reg.put("rowNumber", CollectionVerbs::rowNumber);
        reg.put("sample", CollectionVerbs::sample);
        reg.put("limit", CollectionVerbs::limit);
        reg.put("dedupe", CollectionVerbs::dedupe);
        reg.put("cumsum", CollectionVerbs::cumsum);
        reg.put("cumprod", CollectionVerbs::cumprod);
        reg.put("diff", CollectionVerbs::diff);
        reg.put("pctChange", CollectionVerbs::pctChange);
        reg.put("shift", CollectionVerbs::shift);
        reg.put("lag", CollectionVerbs::lag);
        reg.put("lead", CollectionVerbs::lead);
        reg.put("rank", CollectionVerbs::rank);
        reg.put("fillMissing", CollectionVerbs::fillMissing);
        reg.put("reduce", CollectionVerbs::reduce);
        reg.put("pivot", CollectionVerbs::pivot);
        reg.put("unpivot", CollectionVerbs::unpivot);
    }

    // ── Helpers ──

    private static List<DynValue> extractArray(DynValue v) {
        var arr = v.asArray();
        if (arr != null) return arr;
        var extracted = v.extractArray();
        if (extracted != null) return extracted;
        return new ArrayList<>();
    }

    private static boolean isTruthy(DynValue v) {
        if (v.isNull()) return false;
        Boolean b = v.asBool();
        if (b != null) return b;
        String s = v.asString();
        if (s != null) return !s.isEmpty();
        Long l = v.asInt64();
        if (l != null) return l != 0;
        Double d = v.asDouble();
        if (d != null) return d != 0.0;
        return true;
    }

    private static Double toDouble(DynValue v) {
        return v.asDouble();
    }

    private static Integer toInt(DynValue v) {
        Long l = v.asInt64();
        if (l != null) return (int) l.longValue();
        Double d = v.asDouble();
        if (d != null) return (int) d.doubleValue();
        String s = v.asString();
        if (s != null) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static int compareDynValues(DynValue a, DynValue b) {
        Double na = toDouble(a);
        Double nb = toDouble(b);
        if (na != null && nb != null) return Double.compare(na, nb);
        String sa = a.asString() != null ? a.asString() : a.toString();
        String sb = b.asString() != null ? b.asString() : b.toString();
        return sa.compareTo(sb);
    }

    private static boolean areEqual(DynValue a, DynValue b) {
        if (a.equals(b)) return true;
        Double na = toDouble(a);
        Double nb = toDouble(b);
        if (na != null && nb != null) return na.doubleValue() == nb.doubleValue();
        String sa = a.asString();
        String sb = b.asString();
        if (sa != null && sb != null) return sa.equals(sb);
        return false;
    }

    private static DynValue getField(DynValue obj, String fieldName) {
        DynValue result = obj.get(fieldName);
        return result != null ? result : DynValue.ofNull();
    }

    private static DynValue numericResult(double v) {
        if (v == Math.floor(v) && Math.abs(v) < (double) Long.MAX_VALUE)
            return DynValue.ofInteger((long) v);
        return DynValue.ofFloat(v);
    }

    // ── Verb Implementations ──

    private static DynValue filter(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();

        if (args.length >= 4) {
            String fieldName = args[1].asString();
            String op = args[2].asString();
            DynValue compareValue = args[3];

            if (fieldName == null || op == null)
                return DynValue.ofArray(new ArrayList<>());

            for (DynValue item : arr) {
                DynValue fieldVal = getField(item, fieldName);
                String fieldStr = fieldVal.asString() != null ? fieldVal.asString() : fieldVal.toString();
                String cmpStr = compareValue.asString() != null ? compareValue.asString() : compareValue.toString();

                boolean match = switch (op) {
                    case "=", "==" -> fieldStr.equals(cmpStr);
                    case "!=", "<>" -> !fieldStr.equals(cmpStr);
                    case "<" -> {
                        Double a = toDouble(fieldVal), b = toDouble(compareValue);
                        yield a != null && b != null && a < b;
                    }
                    case "<=" -> {
                        Double a = toDouble(fieldVal), b = toDouble(compareValue);
                        yield a != null && b != null && a <= b;
                    }
                    case ">" -> {
                        Double a = toDouble(fieldVal), b = toDouble(compareValue);
                        yield a != null && b != null && a > b;
                    }
                    case ">=" -> {
                        Double a = toDouble(fieldVal), b = toDouble(compareValue);
                        yield a != null && b != null && a >= b;
                    }
                    case "contains" -> fieldStr.contains(cmpStr);
                    case "startsWith" -> fieldStr.startsWith(cmpStr);
                    case "endsWith" -> fieldStr.endsWith(cmpStr);
                    default -> false;
                };

                if (match) result.add(item);
            }
            return DynValue.ofArray(result);
        }

        String fName = args.length >= 2 ? args[1].asString() : null;

        for (DynValue item : arr) {
            DynValue testVal = fName != null ? getField(item, fName) : item;
            if (isTruthy(testVal)) result.add(item);
        }

        return DynValue.ofArray(result);
    }

    private static DynValue flatten(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();

        for (DynValue item : arr) {
            var inner = item.asArray();
            if (inner != null) {
                result.addAll(inner);
            } else {
                result.add(item);
            }
        }

        return DynValue.ofArray(result);
    }

    private static DynValue distinct(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();
        var seen = new HashSet<String>();

        for (DynValue item : arr) {
            String key = item.toString();
            if (seen.add(key)) result.add(item);
        }

        return DynValue.ofArray(result);
    }

    private static DynValue unique(DynValue[] args, VerbContext ctx) {
        return distinct(args, ctx);
    }

    private static DynValue sort(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<>(arr);
        result.sort(CollectionVerbs::compareDynValues);
        return DynValue.ofArray(result);
    }

    private static DynValue sortDesc(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<>(arr);
        result.sort((a, b) -> compareDynValues(b, a));
        return DynValue.ofArray(result);
    }

    private static DynValue sortBy(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        if (fieldName == null) return DynValue.ofArray(new ArrayList<>(arr));

        var result = new ArrayList<>(arr);
        result.sort((a, b) -> compareDynValues(getField(a, fieldName), getField(b, fieldName)));
        return DynValue.ofArray(result);
    }

    private static DynValue map(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);

        if (args.length < 2) return DynValue.ofArray(new ArrayList<>(arr));

        String fieldName = args[1].asString();
        if (fieldName == null) return DynValue.ofArray(new ArrayList<>(arr));

        var result = new ArrayList<DynValue>();
        for (DynValue item : arr) result.add(getField(item, fieldName));
        return DynValue.ofArray(result);
    }

    private static DynValue indexOf(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofInteger(-1);
        var arr = extractArray(args[0]);

        for (int i = 0; i < arr.size(); i++) {
            if (areEqual(arr.get(i), args[1])) return DynValue.ofInteger(i);
        }

        return DynValue.ofInteger(-1);
    }

    private static DynValue at(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer idx = toInt(args[1]);
        if (idx == null) return DynValue.ofNull();

        int index = idx;
        if (index < 0) index = arr.size() + index;
        if (index < 0 || index >= arr.size()) return DynValue.ofNull();

        return arr.get(index);
    }

    private static DynValue slice(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer startIdx = toInt(args[1]);
        if (startIdx == null) return DynValue.ofNull();

        int start = startIdx;
        if (start < 0) start = Math.max(0, arr.size() + start);
        if (start > arr.size()) start = arr.size();

        int end = arr.size();
        if (args.length >= 3) {
            Integer endIdx = toInt(args[2]);
            if (endIdx != null) {
                end = endIdx;
                if (end < 0) end = Math.max(0, arr.size() + end);
                if (end > arr.size()) end = arr.size();
            }
        }

        if (end <= start) return DynValue.ofArray(new ArrayList<>());

        var result = new ArrayList<DynValue>();
        for (int i = start; i < end; i++) result.add(arr.get(i));
        return DynValue.ofArray(result);
    }

    private static DynValue reverse(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<>(arr);
        Collections.reverse(result);
        return DynValue.ofArray(result);
    }

    private static DynValue every(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(true);
        var arr = extractArray(args[0]);
        String fieldName = args.length >= 2 ? args[1].asString() : null;

        for (DynValue item : arr) {
            DynValue testVal = fieldName != null ? getField(item, fieldName) : item;
            if (!isTruthy(testVal)) return DynValue.ofBool(false);
        }

        return DynValue.ofBool(true);
    }

    private static DynValue some(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofBool(false);
        var arr = extractArray(args[0]);
        String fieldName = args.length >= 2 ? args[1].asString() : null;

        for (DynValue item : arr) {
            DynValue testVal = fieldName != null ? getField(item, fieldName) : item;
            if (isTruthy(testVal)) return DynValue.ofBool(true);
        }

        return DynValue.ofBool(false);
    }

    private static DynValue find(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        String op = args[2].asString();
        DynValue compareValue = args[3];

        for (DynValue item : arr) {
            if (matchesCondition(item, fieldName, op, compareValue)) return item;
        }
        return DynValue.ofNull();
    }

    private static DynValue findIndex(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofInteger(-1);
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        String op = args[2].asString();
        DynValue compareValue = args[3];

        for (int i = 0; i < arr.size(); i++) {
            if (matchesCondition(arr.get(i), fieldName, op, compareValue))
                return DynValue.ofInteger(i);
        }
        return DynValue.ofInteger(-1);
    }

    // Field-comparison predicate shared by filter/find/findIndex/partition/some/every.
    private static boolean matchesCondition(DynValue item, String fieldName, String op, DynValue compareValue) {
        if (fieldName == null || op == null) return false;
        DynValue fieldVal = getField(item, fieldName);
        String fieldStr = fieldVal.asString() != null ? fieldVal.asString() : fieldVal.toString();
        String cmpStr = compareValue.asString() != null ? compareValue.asString() : compareValue.toString();
        return switch (op) {
            case "=", "==" -> fieldStr.equals(cmpStr);
            case "!=", "<>" -> !fieldStr.equals(cmpStr);
            case "<" -> {
                Double a = toDouble(fieldVal), b = toDouble(compareValue);
                yield a != null && b != null && a < b;
            }
            case "<=" -> {
                Double a = toDouble(fieldVal), b = toDouble(compareValue);
                yield a != null && b != null && a <= b;
            }
            case ">" -> {
                Double a = toDouble(fieldVal), b = toDouble(compareValue);
                yield a != null && b != null && a > b;
            }
            case ">=" -> {
                Double a = toDouble(fieldVal), b = toDouble(compareValue);
                yield a != null && b != null && a >= b;
            }
            case "contains" -> fieldStr.contains(cmpStr);
            case "startsWith" -> fieldStr.startsWith(cmpStr);
            case "endsWith" -> fieldStr.endsWith(cmpStr);
            default -> false;
        };
    }

    private static DynValue includes(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        var arr = extractArray(args[0]);

        for (DynValue item : arr) {
            if (areEqual(item, args[1])) return DynValue.ofBool(true);
        }

        return DynValue.ofBool(false);
    }

    private static DynValue concatArrays(DynValue[] args, VerbContext ctx) {
        var result = new ArrayList<DynValue>();
        for (DynValue arg : args) {
            var arr = extractArray(arg);
            result.addAll(arr);
        }
        return DynValue.ofArray(result);
    }

    private static DynValue zip(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());

        var arrays = new ArrayList<List<DynValue>>();
        int maxLen = 0;
        for (DynValue arg : args) {
            var arr = extractArray(arg);
            arrays.add(arr);
            if (arr.size() > maxLen) maxLen = arr.size();
        }

        var result = new ArrayList<DynValue>();
        for (int i = 0; i < maxLen; i++) {
            var tuple = new ArrayList<DynValue>();
            for (List<DynValue> array : arrays) {
                tuple.add(i < array.size() ? array.get(i) : DynValue.ofNull());
            }
            result.add(DynValue.ofArray(tuple));
        }

        return DynValue.ofArray(result);
    }

    private static DynValue groupBy(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        if (fieldName == null) return DynValue.ofNull();

        var groups = new LinkedHashMap<String, List<DynValue>>();

        for (DynValue item : arr) {
            DynValue keyVal = getField(item, fieldName);
            String key = keyVal.isNull() ? "null" : (keyVal.asString() != null ? keyVal.asString() : keyVal.toString());

            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        var result = new ArrayList<DynValue>();
        for (var e : groups.entrySet()) {
            var entries = new ArrayList<Map.Entry<String, DynValue>>();
            entries.add(Map.entry("key", DynValue.ofString(e.getKey())));
            entries.add(Map.entry("items", DynValue.ofArray(e.getValue())));
            result.add(DynValue.ofObject(entries));
        }

        return DynValue.ofArray(result);
    }

    private static DynValue partition(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        String op = args[2].asString();
        DynValue compareValue = args[3];

        var pass = new ArrayList<DynValue>();
        var fail = new ArrayList<DynValue>();

        for (DynValue item : arr) {
            if (matchesCondition(item, fieldName, op, compareValue)) pass.add(item);
            else fail.add(item);
        }

        var result = new ArrayList<DynValue>();
        result.add(DynValue.ofArray(pass));
        result.add(DynValue.ofArray(fail));
        return DynValue.ofArray(result);
    }

    private static DynValue take(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer count = toInt(args[1]);
        if (count == null) return DynValue.ofNull();

        int n = Math.min(Math.max(0, count), arr.size());
        var result = new ArrayList<DynValue>();
        for (int i = 0; i < n; i++) result.add(arr.get(i));
        return DynValue.ofArray(result);
    }

    private static DynValue drop(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer count = toInt(args[1]);
        if (count == null) return DynValue.ofNull();

        int skip = Math.min(Math.max(0, count), arr.size());
        var result = new ArrayList<DynValue>();
        for (int i = skip; i < arr.size(); i++) result.add(arr.get(i));
        return DynValue.ofArray(result);
    }

    private static DynValue chunk(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer size = toInt(args[1]);
        if (size == null || size <= 0) return DynValue.ofNull();

        var result = new ArrayList<DynValue>();
        for (int i = 0; i < arr.size(); i += size) {
            var chunkList = new ArrayList<DynValue>();
            int end = Math.min(i + size, arr.size());
            for (int j = i; j < end; j++) chunkList.add(arr.get(j));
            result.add(DynValue.ofArray(chunkList));
        }

        return DynValue.ofArray(result);
    }

    private static DynValue range(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());

        int start, end, step;

        if (args.length == 1) {
            start = 0;
            Integer e = toInt(args[0]);
            if (e == null) return DynValue.ofArray(new ArrayList<>());
            end = e;
            step = 1;
        } else {
            Integer s = toInt(args[0]);
            Integer e = toInt(args[1]);
            if (s == null || e == null) return DynValue.ofArray(new ArrayList<>());
            start = s;
            end = e;
            step = 1;
            if (args.length >= 3) {
                Integer st = toInt(args[2]);
                if (st != null && st != 0) step = st;
            }
        }

        var result = new ArrayList<DynValue>();
        int maxItems = 10000;

        if (step > 0) {
            for (int i = start; i < end && result.size() < maxItems; i += step)
                result.add(DynValue.ofInteger(i));
        } else if (step < 0) {
            for (int i = start; i > end && result.size() < maxItems; i += step)
                result.add(DynValue.ofInteger(i));
        }

        return DynValue.ofArray(result);
    }

    private static DynValue compact(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();

        for (DynValue item : arr) {
            if (item.isNull()) continue;
            String s = item.asString();
            if (s != null && s.isEmpty()) continue;
            result.add(item);
        }

        return DynValue.ofArray(result);
    }

    private static DynValue pluck(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args[1].asString();
        if (fieldName == null) return DynValue.ofNull();

        var result = new ArrayList<DynValue>();
        for (DynValue item : arr) result.add(getField(item, fieldName));
        return DynValue.ofArray(result);
    }

    private static DynValue rowNumber(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();
        for (int i = 0; i < arr.size(); i++) {
            result.add(withLeadingField("_rowNum", DynValue.ofInteger(i + 1), arr.get(i)));
        }
        return DynValue.ofArray(result);
    }

    // Build {field: value, ...item} when item is an object, else {field: value, value: item}.
    private static DynValue withLeadingField(String field, DynValue value, DynValue item) {
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        entries.add(Map.entry(field, value));
        if (item.getType() == DynValue.Type.Object) {
            var obj = item.asObject();
            if (obj != null) for (var e : obj) entries.add(Map.entry(e.getKey(), e.getValue()));
        } else {
            entries.add(Map.entry("value", item));
        }
        return DynValue.ofObject(entries);
    }

    private static DynValue sample(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer count = toInt(args[1]);
        if (count == null || count <= 0) return DynValue.ofArray(new ArrayList<>());

        int n = Math.min(count, arr.size());
        var copy = new ArrayList<>(arr);

        // Check for optional 3rd arg as integer seed
        Integer seedVal = args.length >= 3 ? toInt(args[2]) : null;

        if (seedVal != null) {
            // Seeded Fisher-Yates using Mulberry32 (matches TypeScript)
            int[] state = { seedVal };
            for (int i = 0; i < n; i++) {
                int j = i + (int) Math.floor(NumericVerbs.mulberry32Next(state) * (copy.size() - i));
                var temp = copy.get(i);
                copy.set(i, copy.get(j));
                copy.set(j, temp);
            }
        } else {
            // Unseeded Fisher-Yates
            var rng = new Random();
            for (int i = copy.size() - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                var temp = copy.get(i);
                copy.set(i, copy.get(j));
                copy.set(j, temp);
            }
        }

        var result = new ArrayList<DynValue>();
        for (int i = 0; i < n; i++) result.add(copy.get(i));
        return DynValue.ofArray(result);
    }

    private static DynValue limit(DynValue[] args, VerbContext ctx) {
        return take(args, ctx);
    }

    private static DynValue dedupe(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String keyField = args[1].asString();

        var seen = new HashSet<String>();
        var result = new ArrayList<DynValue>();
        for (DynValue item : arr) {
            String keyValue;
            if (item.getType() == DynValue.Type.Object) {
                DynValue fieldValue = getField(item, keyField);
                if (fieldValue.isNull()) { result.add(item); continue; }
                keyValue = fieldValue.asString() != null ? fieldValue.asString() : fieldValue.toString();
            } else {
                keyValue = item.asString() != null ? item.asString() : item.toString();
            }
            if (seen.add(keyValue)) result.add(item);
        }
        return DynValue.ofArray(result);
    }

    private static DynValue cumsum(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();
        double running = 0;

        for (DynValue item : arr) {
            Double v = toDouble(item);
            if (v != null) {
                running += v;
                result.add(numericResult(running));
            } else {
                result.add(DynValue.ofNull());
            }
        }

        return DynValue.ofArray(result);
    }

    private static DynValue cumprod(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();
        double running = 1;

        for (DynValue item : arr) {
            Double v = toDouble(item);
            if (v != null) {
                running *= v;
                result.add(numericResult(running));
            } else {
                result.add(DynValue.ofNull());
            }
        }

        return DynValue.ofArray(result);
    }

    private static DynValue diff(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();

        if (!arr.isEmpty()) result.add(DynValue.ofNull());

        for (int i = 1; i < arr.size(); i++) {
            Double curr = toDouble(arr.get(i));
            Double prev = toDouble(arr.get(i - 1));
            if (curr != null && prev != null)
                result.add(numericResult(curr - prev));
            else
                result.add(DynValue.ofNull());
        }

        return DynValue.ofArray(result);
    }

    private static DynValue pctChange(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        var result = new ArrayList<DynValue>();

        if (!arr.isEmpty()) result.add(DynValue.ofNull());

        for (int i = 1; i < arr.size(); i++) {
            Double curr = toDouble(arr.get(i));
            Double prev = toDouble(arr.get(i - 1));
            if (curr != null && prev != null && prev != 0.0)
                result.add(DynValue.ofFloat((curr - prev) / prev));
            else
                result.add(DynValue.ofNull());
        }

        return DynValue.ofArray(result);
    }

    private static DynValue shift(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        Integer n = toInt(args[1]);
        if (n == null) return DynValue.ofArray(new ArrayList<>(arr));
        return shiftArray(arr, n);
    }

    private static DynValue lag(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        int n = 1;
        if (args.length >= 2) {
            Integer nVal = toInt(args[1]);
            if (nVal != null) n = nVal;
        }
        return shiftArray(arr, n);
    }

    private static DynValue lead(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        int n = 1;
        if (args.length >= 2) {
            Integer nVal = toInt(args[1]);
            if (nVal != null) n = nVal;
        }
        return shiftArray(arr, -n);
    }

    private static DynValue shiftArray(List<DynValue> arr, int n) {
        var result = new ArrayList<DynValue>(arr.size());
        int absN = Math.abs(n);

        if (n > 0) {
            for (int i = 0; i < Math.min(absN, arr.size()); i++)
                result.add(DynValue.ofNull());
            for (int i = 0; i < arr.size() - absN; i++)
                result.add(arr.get(i));
        } else if (n < 0) {
            for (int i = absN; i < arr.size(); i++)
                result.add(arr.get(i));
            for (int i = 0; i < Math.min(absN, arr.size()); i++)
                result.add(DynValue.ofNull());
        } else {
            result.addAll(arr);
        }

        return DynValue.ofArray(result);
    }

    private static DynValue rank(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String fieldName = args.length > 1 ? args[1].asString() : null;
        String direction = args.length > 2 && args[2].asString() != null
                ? args[2].asString().toLowerCase() : "desc";
        int mult = "asc".equals(direction) ? 1 : -1;

        // Comparable value per element.
        var vals = new ArrayList<DynValue>();
        for (DynValue item : arr) {
            vals.add(fieldName != null && item.getType() == DynValue.Type.Object
                    ? getField(item, fieldName) : item);
        }

        var order = new ArrayList<Integer>();
        for (int i = 0; i < arr.size(); i++) order.add(i);
        final int m = mult;
        order.sort((a, b) -> {
            Double av = toDouble(vals.get(a)), bv = toDouble(vals.get(b));
            if (av != null && bv != null) return Double.compare(av, bv) * m;
            String as = vals.get(a).asString(), bs = vals.get(b).asString();
            return (as != null ? as : "").compareTo(bs != null ? bs : "") * m;
        });

        var ranks = new int[arr.size()];
        int currentRank = 1;
        for (int i = 0; i < order.size(); i++) {
            if (i > 0 && !sameRankValue(vals.get(order.get(i)), vals.get(order.get(i - 1))))
                currentRank = i + 1;
            ranks[order.get(i)] = currentRank;
        }

        var result = new ArrayList<DynValue>();
        for (int i = 0; i < arr.size(); i++) {
            result.add(withLeadingField("_rank", DynValue.ofInteger(ranks[i]), arr.get(i)));
        }
        return DynValue.ofArray(result);
    }

    private static boolean sameRankValue(DynValue a, DynValue b) {
        Double ad = toDouble(a), bd = toDouble(b);
        if (ad != null && bd != null) return ad.doubleValue() == bd.doubleValue();
        return Objects.equals(a.asString(), b.asString());
    }

    private static DynValue fillMissing(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = extractArray(args[0]);
        String strategyStr = args[1].asString();

        var result = new ArrayList<>(arr);

        if ("forward".equals(strategyStr)) {
            DynValue last = null;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).isNull() && last != null)
                    result.set(i, last);
                else if (!result.get(i).isNull())
                    last = result.get(i);
            }
        } else if ("backward".equals(strategyStr)) {
            DynValue next = null;
            for (int i = result.size() - 1; i >= 0; i--) {
                if (result.get(i).isNull() && next != null)
                    result.set(i, next);
                else if (!result.get(i).isNull())
                    next = result.get(i);
            }
        } else {
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).isNull())
                    result.set(i, args[1]);
            }
        }

        return DynValue.ofArray(result);
    }

    // ── reduce ──

    private static DynValue reduce(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();

        var arr = extractArray(args[0]);
        if (arr.isEmpty()) return args[2]; // empty array → return initial value

        String verbName = args[1].asString();
        if (verbName == null) return DynValue.ofNull();

        DynValue accumulator = args[2];

        for (var item : arr) {
            accumulator = foundation.odin.transform.TransformEngine.invokeVerb(
                    verbName, new DynValue[]{accumulator, item}, ctx);
        }

        return accumulator;
    }

    // ── pivot ──

    private static DynValue pivot(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();

        var arr = extractArray(args[0]);
        String keyField = args[1].asString();
        String valueField = args[2].asString();
        if (keyField == null || valueField == null) return DynValue.ofNull();

        var entries = new ArrayList<Map.Entry<String, DynValue>>();

        for (var item : arr) {
            // Get the object entries from this item
            var objEntries = item.asObject();
            if (objEntries == null) continue;

            DynValue keyVal = item.get(keyField);
            if (keyVal == null || keyVal.isNull()) continue;

            DynValue valueVal = item.get(valueField);
            if (valueVal == null) continue;

            String key;
            if (keyVal.asString() != null) key = keyVal.asString();
            else if (keyVal.asInt64() != null) key = Long.toString(keyVal.asInt64());
            else if (keyVal.asDouble() != null) key = String.valueOf(keyVal.asDouble());
            else key = keyVal.toString();

            // Remove existing entry with same key (last value wins)
            entries.removeIf(e -> e.getKey().equals(key));
            entries.add(Map.entry(key, valueVal));
        }

        return DynValue.ofObject(entries);
    }

    // ── unpivot ──

    private static DynValue unpivot(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();

        var objEntries = args[0].asObject();
        if (objEntries == null) {
            objEntries = args[0].extractObject();
            if (objEntries == null) return DynValue.ofNull();
        }

        String keyName = args[1].asString();
        String valueName = args[2].asString();
        if (keyName == null || valueName == null) return DynValue.ofNull();

        var result = new ArrayList<DynValue>();

        for (var entry : objEntries) {
            var row = new ArrayList<Map.Entry<String, DynValue>>();
            row.add(Map.entry(keyName, DynValue.ofString(entry.getKey())));
            row.add(Map.entry(valueName, entry.getValue()));
            result.add(DynValue.ofObject(row));
        }

        return DynValue.ofArray(result);
    }
}
