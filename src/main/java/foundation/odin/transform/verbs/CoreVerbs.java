package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.LookupTable;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.*;
import java.util.function.BiFunction;

public final class CoreVerbs {

    private CoreVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("concat", CoreVerbs::concat);
        reg.put("upper", CoreVerbs::upper);
        reg.put("lower", CoreVerbs::lower);
        reg.put("trim", CoreVerbs::trim);
        reg.put("trimLeft", CoreVerbs::trimLeft);
        reg.put("trimRight", CoreVerbs::trimRight);
        reg.put("coalesce", CoreVerbs::coalesce);
        reg.put("ifNull", CoreVerbs::ifNull);
        reg.put("ifEmpty", CoreVerbs::ifEmpty);
        reg.put("ifElse", CoreVerbs::ifElse);
        reg.put("lookup", CoreVerbs::lookup);
        reg.put("lookupDefault", CoreVerbs::lookupDefault);
    }

    private static DynValue concat(DynValue[] args, VerbContext ctx) {
        var sb = new StringBuilder();
        for (var arg : args) {
            if (!arg.isNull()) sb.append(VerbHelpers.coerceStr(arg));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue upper(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("upper: expected string argument");
        var s = args[0].asString();
        if (args[0].isNull()) return DynValue.ofNull();
        if (s == null)
            throw new IllegalStateException("upper: expected string argument");
        return DynValue.ofString(s.toUpperCase(Locale.ROOT));
    }

    private static DynValue lower(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("lower: expected string argument");
        var s = args[0].asString();
        if (args[0].isNull()) return DynValue.ofNull();
        if (s == null)
            throw new IllegalStateException("lower: expected string argument");
        return DynValue.ofString(s.toLowerCase(Locale.ROOT));
    }

    private static DynValue trim(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("trim: expected string argument");
        var s = args[0].asString();
        if (args[0].isNull()) return DynValue.ofNull();
        if (s == null)
            throw new IllegalStateException("trim: expected string argument");
        return DynValue.ofString(s.trim());
    }

    private static DynValue trimLeft(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("trimLeft: expected string argument");
        var s = args[0].asString();
        if (args[0].isNull()) return DynValue.ofNull();
        if (s == null)
            throw new IllegalStateException("trimLeft: expected string argument");
        return DynValue.ofString(s.stripLeading());
    }

    private static DynValue trimRight(DynValue[] args, VerbContext ctx) {
        if (args.length == 0)
            throw new IllegalStateException("trimRight: expected string argument");
        var s = args[0].asString();
        if (args[0].isNull()) return DynValue.ofNull();
        if (s == null)
            throw new IllegalStateException("trimRight: expected string argument");
        return DynValue.ofString(s.stripTrailing());
    }

    private static DynValue coalesce(DynValue[] args, VerbContext ctx) {
        for (var arg : args) {
            if (arg.isNull()) continue;
            if (arg.getType() == DynValue.Type.String && "".equals(arg.asString())) continue;
            return arg;
        }
        return DynValue.ofNull();
    }

    private static DynValue ifNull(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("ifNull: requires 2 arguments");
        return args[0].isNull() ? args[1] : args[0];
    }

    private static DynValue ifEmpty(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("ifEmpty: requires 2 arguments");
        boolean isEmpty = args[0].isNull()
                || (args[0].getType() == DynValue.Type.String && "".equals(args[0].asString()));
        return isEmpty ? args[1] : args[0];
    }

    private static DynValue ifElse(DynValue[] args, VerbContext ctx) {
        if (args.length < 3)
            throw new IllegalStateException("ifElse: requires 3 arguments");
        return VerbHelpers.isTruthy(args[0]) ? args[1] : args[2];
    }

    private static DynValue lookup(DynValue[] args, VerbContext ctx) {
        if (args.length < 2)
            throw new IllegalStateException("lookup: requires at least 2 arguments (table.column, key...)");
        var tableRef = args[0].asString();
        if (tableRef == null)
            throw new IllegalStateException("lookup: first argument must be a string table reference");

        var keys = Arrays.copyOfRange(args, 1, args.length);
        var result = doTableLookup(tableRef, keys, ctx.getTables());
        if (result != null) return result;

        int dotIdx = tableRef.indexOf('.');
        var tableName = dotIdx >= 0 ? tableRef.substring(0, dotIdx) : tableRef;
        var table = ctx.getTables().get(tableName);
        if (table != null && table.getDefault() != null)
            return table.getDefault();

        return DynValue.ofNull();
    }

    private static DynValue lookupDefault(DynValue[] args, VerbContext ctx) {
        if (args.length < 3)
            throw new IllegalStateException("lookupDefault: requires at least 3 arguments (table.column, key..., default)");
        var tableRef = args[0].asString();
        if (tableRef == null)
            throw new IllegalStateException("lookupDefault: first argument must be a string table reference");

        var defaultVal = args[args.length - 1];
        var keys = Arrays.copyOfRange(args, 1, args.length - 1);
        if (keys.length == 0)
            throw new IllegalStateException("lookupDefault: requires at least one lookup key");

        var result = doTableLookup(tableRef, keys, ctx.getTables());
        return result != null ? result : defaultVal;
    }

    private static DynValue doTableLookup(String tableRef, DynValue[] keys, Map<String, LookupTable> tables) {
        int dotIdx = tableRef.indexOf('.');
        if (dotIdx < 0) return null;

        var tableName = tableRef.substring(0, dotIdx);
        var resultCol = tableRef.substring(dotIdx + 1);

        var table = tables.get(tableName);
        if (table == null) return null;

        int resultIdx = -1;
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (table.getColumns().get(i).equals(resultCol)) {
                resultIdx = i;
                break;
            }
        }
        if (resultIdx < 0) return null;

        var matchColIndices = new ArrayList<Integer>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i != resultIdx) matchColIndices.add(i);
        }

        for (var row : table.getRows()) {
            if (row.size() <= resultIdx) continue;
            boolean allMatch = true;
            for (int k = 0; k < keys.length && k < matchColIndices.size(); k++) {
                int colIdx = matchColIndices.get(k);
                if (colIdx >= row.size()) { allMatch = false; break; }
                if (!dynMatchesKey(row.get(colIdx), keys[k])) { allMatch = false; break; }
            }
            if (allMatch && keys.length > 0)
                return row.get(resultIdx);
        }

        return null;
    }

    private static boolean dynMatchesKey(DynValue cell, DynValue key) {
        if (cell.equals(key)) return true;
        var cellStr = coerceKeyStr(cell);
        var keyStr = coerceKeyStr(key);
        if (cellStr == null || keyStr == null) return false;
        return cellStr.equals(keyStr);
    }

    private static String coerceKeyStr(DynValue val) {
        if (val.isNull()) return null;
        return switch (val.getType()) {
            case String -> val.asString();
            case Integer -> Long.toString(val.asInt64());
            case Float -> Double.toString(val.asDouble());
            case Bool -> val.asBool() ? "true" : "false";
            default -> null;
        };
    }
}
