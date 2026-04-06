package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.transform.verbs.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

public final class VerbRegistry {

    private final Map<String, BiFunction<DynValue[], TransformEngine.VerbContext, DynValue>> builtins = new LinkedHashMap<>();
    private final Map<String, BiFunction<DynValue[], TransformEngine.VerbContext, DynValue>> custom = new LinkedHashMap<>();

    public VerbRegistry() {
        registerBuiltins();
    }

    public DynValue invoke(String name, DynValue[] args, TransformEngine.VerbContext ctx) {
        var customFn = custom.get(name);
        if (customFn != null) return customFn.apply(args, ctx);

        var builtinFn = builtins.get(name);
        if (builtinFn != null) return builtinFn.apply(args, ctx);

        throw new UnsupportedOperationException("Unknown verb: '" + name + "'");
    }

    public void registerCustom(String name, BiFunction<DynValue[], TransformEngine.VerbContext, DynValue> fn) {
        custom.put(name, fn);
    }

    private void registerBuiltins() {
        CoreVerbs.register(builtins);
        CoercionVerbs.register(builtins);
        LogicVerbs.register(builtins);
        StringVerbs.register(builtins);
        EncodingVerbs.register(builtins);
        GenerationVerbs.register(builtins);
        NumericVerbs.register(builtins);
        CollectionVerbs.register(builtins);
        DateTimeVerbs.register(builtins);
        FinancialVerbs.register(builtins);
        AggregationVerbs.register(builtins);
        ObjectVerbs.register(builtins);
        GeoVerbs.register(builtins);
    }

    public Map<String, BiFunction<DynValue[], TransformEngine.VerbContext, DynValue>> toMap() {
        var map = new LinkedHashMap<>(builtins);
        map.putAll(custom);
        return map;
    }
}
