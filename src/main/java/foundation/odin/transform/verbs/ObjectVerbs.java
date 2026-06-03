package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.*;
import java.util.function.BiFunction;

public final class ObjectVerbs {

    private ObjectVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("keys", ObjectVerbs::keys);
        reg.put("values", ObjectVerbs::values);
        reg.put("entries", ObjectVerbs::entries);
        reg.put("has", ObjectVerbs::has);
        reg.put("get", ObjectVerbs::get);
        reg.put("merge", ObjectVerbs::merge);
        reg.put("pick", ObjectVerbs::pick);
        reg.put("omit", ObjectVerbs::omit);
        reg.put("fromEntries", ObjectVerbs::fromEntries);
        reg.put("invert", ObjectVerbs::invert);
        reg.put("defaults", ObjectVerbs::defaults);
        reg.put("renameKeys", ObjectVerbs::renameKeys);
        reg.put("compactObject", ObjectVerbs::compactObject);
    }

    // ── Helpers ──

    private static List<Map.Entry<String, DynValue>> extractObj(DynValue v) {
        var obj = v.asObject();
        if (obj != null) return obj;
        return v.extractObject();
    }

    private static boolean isSafeKey(String key) {
        String k = key.toLowerCase();
        return !k.equals("__proto__") && !k.equals("constructor") && !k.equals("prototype");
    }

    private static String coerceKey(DynValue v) {
        var s = v.asString();
        if (s != null) return s;
        var i = v.asInt64();
        if (i != null) return Long.toString(i);
        var d = v.asDouble();
        if (d != null) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) return Long.toString((long) (double) d);
            return Double.toString(d);
        }
        var b = v.asBool();
        if (b != null) return b ? "true" : "false";
        if (v.isNull()) return "";
        return "";
    }

    private static boolean isEmptyValue(DynValue v) {
        if (v.isNull()) return true;
        var s = v.asString();
        if (s != null && v.getType() == DynValue.Type.String) return s.isEmpty();
        var arr = v.asArray();
        if (arr != null) return arr.isEmpty();
        var obj = v.asObject();
        if (obj != null) return obj.isEmpty();
        return false;
    }

    // ── Verb Implementations ──

    private static DynValue keys(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var result = new ArrayList<DynValue>();
        for (var entry : obj) result.add(DynValue.ofString(entry.getKey()));
        return DynValue.ofArray(result);
    }

    private static DynValue values(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var result = new ArrayList<DynValue>();
        for (var entry : obj) result.add(entry.getValue());
        return DynValue.ofArray(result);
    }

    private static DynValue entries(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var result = new ArrayList<DynValue>();
        for (var entry : obj) {
            var pair = new ArrayList<DynValue>();
            pair.add(DynValue.ofString(entry.getKey()));
            pair.add(entry.getValue());
            result.add(DynValue.ofArray(pair));
        }
        return DynValue.ofArray(result);
    }

    private static DynValue has(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        String key = args[1].asString();
        if (key == null) return DynValue.ofBool(false);
        return DynValue.ofBool(!resolvePath(args[0], key).isNull());
    }

    private static DynValue get(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String key = args[1].asString();
        if (key == null) return DynValue.ofNull();
        DynValue result = resolvePath(args[0], key);
        if (!result.isNull()) return result;
        if (args.length >= 3) return args[2];
        return DynValue.ofNull();
    }

    // Resolve a dotted path against an object; null when any segment is absent.
    private static DynValue resolvePath(DynValue root, String path) {
        DynValue current = root;
        for (var seg : path.split("\\.")) {
            if (current == null) return DynValue.ofNull();
            var obj = extractObj(current);
            if (obj == null) return DynValue.ofNull();
            DynValue next = null;
            for (var entry : obj) {
                if (seg.equals(entry.getKey())) { next = entry.getValue(); break; }
            }
            if (next == null) return DynValue.ofNull();
            current = next;
        }
        return current != null ? current : DynValue.ofNull();
    }

    private static DynValue merge(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofObject(new ArrayList<>());
        var merged = new LinkedHashMap<String, DynValue>();
        for (DynValue arg : args) {
            var obj = extractObj(arg);
            if (obj == null) continue;
            for (var entry : obj) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : merged.entrySet()) {
            entries.add(Map.entry(e.getKey(), e.getValue()));
        }
        return DynValue.ofObject(entries);
    }

    // Keep only the named keys, in argument order, skipping absent or unsafe ones.
    private static DynValue pick(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var lookup = new LinkedHashMap<String, DynValue>();
        for (var e : obj) lookup.put(e.getKey(), e.getValue());
        var result = new ArrayList<Map.Entry<String, DynValue>>();
        for (int i = 1; i < args.length; i++) {
            String key = coerceKey(args[i]);
            if (isSafeKey(key) && lookup.containsKey(key)) {
                result.add(Map.entry(key, lookup.get(key)));
            }
        }
        return DynValue.ofObject(result);
    }

    // Drop the named keys, preserving source order.
    private static DynValue omit(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var drop = new HashSet<String>();
        for (int i = 1; i < args.length; i++) drop.add(coerceKey(args[i]));
        var result = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : obj) {
            if (!isSafeKey(e.getKey())) continue;
            if (!drop.contains(e.getKey())) result.add(e);
        }
        return DynValue.ofObject(result);
    }

    // Build an object from an array of [key, value] pairs (pair order, last wins).
    private static DynValue fromEntries(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var pairs = args[0].asArray();
        if (pairs == null) pairs = args[0].extractArray();
        if (pairs == null) return DynValue.ofNull();
        var result = new LinkedHashMap<String, DynValue>();
        for (var entry : pairs) {
            var pair = entry.asArray();
            if (pair == null || pair.size() < 2) continue;
            String key = coerceKey(pair.get(0));
            if (isSafeKey(key)) result.put(key, pair.get(1));
        }
        var out = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : result.entrySet()) out.add(Map.entry(e.getKey(), e.getValue()));
        return DynValue.ofObject(out);
    }

    // Swap keys and values; values become string keys (last wins on duplicates).
    private static DynValue invert(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var result = new LinkedHashMap<String, DynValue>();
        for (var e : obj) {
            if (!isSafeKey(e.getKey())) continue;
            String newKey = coerceKey(e.getValue());
            if (isSafeKey(newKey)) result.put(newKey, DynValue.ofString(e.getKey()));
        }
        var out = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : result.entrySet()) out.add(Map.entry(e.getKey(), e.getValue()));
        return DynValue.ofObject(out);
    }

    // Fill keys missing from the object using defaults (object value wins).
    private static DynValue defaults(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var src = extractObj(args[0]);
        var def = extractObj(args[1]);
        if (src == null) return def != null ? args[1] : DynValue.ofNull();
        if (def == null) return args[0];
        var result = new LinkedHashMap<String, DynValue>();
        for (var e : src) if (isSafeKey(e.getKey())) result.put(e.getKey(), e.getValue());
        for (var e : def) {
            if (!isSafeKey(e.getKey())) continue;
            if (!result.containsKey(e.getKey())) result.put(e.getKey(), e.getValue());
        }
        var out = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : result.entrySet()) out.add(Map.entry(e.getKey(), e.getValue()));
        return DynValue.ofObject(out);
    }

    // Rename keys named in the mapping (old -> new), keeping position.
    private static DynValue renameKeys(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var src = extractObj(args[0]);
        if (src == null) return DynValue.ofNull();
        var map = extractObj(args[1]);
        if (map == null) return args[0];
        var rename = new LinkedHashMap<String, DynValue>();
        for (var e : map) rename.put(e.getKey(), e.getValue());
        var result = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : src) {
            if (!isSafeKey(e.getKey())) continue;
            String newKey = rename.containsKey(e.getKey()) ? coerceKey(rename.get(e.getKey())) : e.getKey();
            if (isSafeKey(newKey)) result.add(Map.entry(newKey, e.getValue()));
        }
        return DynValue.ofObject(result);
    }

    // Drop entries whose value is null, empty string, empty array, or empty object.
    private static DynValue compactObject(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        var result = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : obj) {
            if (!isSafeKey(e.getKey())) continue;
            if (isEmptyValue(e.getValue())) continue;
            result.add(e);
        }
        return DynValue.ofObject(result);
    }
}
