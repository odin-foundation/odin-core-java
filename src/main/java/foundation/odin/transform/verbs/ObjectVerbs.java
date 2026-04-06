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
    }

    // ── Helpers ──

    private static List<Map.Entry<String, DynValue>> extractObj(DynValue v) {
        var obj = v.asObject();
        if (obj != null) return obj;
        return v.extractObject();
    }

    // ── Verb Implementations ──

    private static DynValue keys(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofArray(new ArrayList<>());
        var result = new ArrayList<DynValue>();
        for (var entry : obj) result.add(DynValue.ofString(entry.getKey()));
        return DynValue.ofArray(result);
    }

    private static DynValue values(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofArray(new ArrayList<>());
        var result = new ArrayList<DynValue>();
        for (var entry : obj) result.add(entry.getValue());
        return DynValue.ofArray(result);
    }

    private static DynValue entries(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofArray(new ArrayList<>());
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofArray(new ArrayList<>());
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
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofBool(false);
        String key = args[1].asString();
        if (key == null) return DynValue.ofBool(false);
        for (var entry : obj) {
            if (key.equals(entry.getKey())) return DynValue.ofBool(true);
        }
        return DynValue.ofBool(false);
    }

    private static DynValue get(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String key = args[1].asString();
        if (key == null) return DynValue.ofNull();
        DynValue result = args[0].get(key);
        if (result != null) return result;
        var obj = extractObj(args[0]);
        if (obj == null) return DynValue.ofNull();
        for (var entry : obj) {
            if (key.equals(entry.getKey())) return entry.getValue();
        }
        return DynValue.ofNull();
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
}
