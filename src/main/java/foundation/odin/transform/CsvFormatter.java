package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.TargetConfig;

import java.util.ArrayList;
import java.util.List;

public final class CsvFormatter {

    private CsvFormatter() {}

    public static String format(DynValue value, TargetConfig config) {
        if (value == null) return "";

        String delimiter = ",";
        boolean includeHeader = true;
        char quoteChar = '"';

        if (config != null) {
            var delim = config.getOptions().get("delimiter");
            if (delim != null && !delim.isEmpty()) delimiter = delim;
            var hdr = config.getOptions().get("header");
            if (hdr == null) hdr = config.getOptions().get("includeHeader");
            if ("false".equals(hdr)) includeHeader = false;
            var qc = config.getOptions().get("quoteChar");
            if (qc != null && !qc.isEmpty()) quoteChar = qc.charAt(0);
        }

        // Unwrap single-key objects containing arrays
        DynValue resolved = value;
        var objEntries = value.asObject();
        if (objEntries != null && objEntries.size() == 1) {
            var inner = objEntries.get(0).getValue();
            if (inner.asArray() != null) resolved = inner;
        }

        var rows = resolved.asArray();
        if (rows == null || rows.isEmpty()) return "";

        var firstObj = rows.get(0).asObject();
        if (firstObj == null) return "";

        var headers = new ArrayList<String>();
        for (var entry : firstObj) headers.add(entry.getKey());

        var sb = new StringBuilder();

        if (includeHeader) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(delimiter);
                sb.append(headers.get(i));
            }
            sb.append('\n');
        }

        for (var row : rows) {
            var rowObj = row.asObject();
            if (rowObj == null) continue;
            for (int c = 0; c < rowObj.size(); c++) {
                if (c > 0) sb.append(delimiter);
                sb.append(formatCsvValue(rowObj.get(c).getValue(), delimiter, quoteChar));
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static String formatCsvValue(DynValue value, String delimiter, char quoteChar) {
        return switch (value.getType()) {
            case Null, Array, Object -> "";
            case Bool -> (value.asBool() != null && value.asBool()) ? "true" : "false";
            case Integer -> String.valueOf(value.asInt64());
            case Float, Percent -> {
                double d = value.asDouble() != null ? value.asDouble() : 0.0;
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case Currency -> {
                double d = value.asDouble() != null ? value.asDouble() : 0.0;
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case FloatRaw, CurrencyRaw -> value.asString() != null ? value.asString() : "";
            default -> {
                String s = value.asString() != null ? value.asString() : "";
                boolean needsQuoting = s.contains(delimiter) || s.indexOf(quoteChar) >= 0 || s.contains("\n");
                if (needsQuoting) {
                    yield String.valueOf(quoteChar) + s.replace(String.valueOf(quoteChar), String.valueOf(quoteChar) + quoteChar) + quoteChar;
                }
                yield s;
            }
        };
    }
}
