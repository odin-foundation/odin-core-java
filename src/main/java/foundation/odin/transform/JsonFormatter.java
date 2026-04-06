package foundation.odin.transform;

import com.google.gson.stream.JsonWriter;
import foundation.odin.types.DynValue;
import foundation.odin.types.TargetConfig;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

public final class JsonFormatter {

    private JsonFormatter() {}

    public static String format(DynValue value, TargetConfig config) {
        if (value == null) return "null";

        boolean pretty = true;
        String indentStr = "  ";
        boolean omitNulls = false;
        boolean omitEmptyArrays = false;

        if (config != null) {
            var indentVal = config.getOptions().get("indent");
            if (indentVal != null) {
                pretty = !"false".equals(indentVal) && !"0".equals(indentVal);
                if (pretty) {
                    try {
                        int n = Integer.parseInt(indentVal);
                        indentStr = " ".repeat(n);
                    } catch (NumberFormatException ignored) {}
                }
            }
            omitNulls = "omit".equals(config.getOptions().get("nulls"));
            omitEmptyArrays = "omit".equals(config.getOptions().get("emptyArrays"));
        }

        // Apply filtering before serialization
        DynValue filtered = (omitNulls || omitEmptyArrays)
                ? filterValue(value, omitNulls, omitEmptyArrays)
                : value;
        if (filtered == null) return "null";

        try {
            var sw = new StringWriter();
            var writer = new JsonWriter(sw);
            if (pretty) writer.setIndent(indentStr);
            writeValue(writer, filtered);
            writer.close();
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException("JSON formatting failed", e);
        }
    }

    /**
     * Recursively filter out null values and/or empty arrays from a DynValue tree.
     */
    private static DynValue filterValue(DynValue value, boolean omitNulls, boolean omitEmptyArrays) {
        if (value == null) return null;
        switch (value.getType()) {
            case Null -> {
                return omitNulls ? null : value;
            }
            case Array -> {
                var items = value.asArray();
                if (items == null) return value;
                if (items.isEmpty() && omitEmptyArrays) return null;
                var filtered = new ArrayList<DynValue>();
                for (var item : items) {
                    var f = filterValue(item, omitNulls, omitEmptyArrays);
                    if (f != null) filtered.add(f);
                }
                if (filtered.isEmpty() && omitEmptyArrays) return null;
                return DynValue.ofArray(filtered);
            }
            case Object -> {
                var entries = value.asObject();
                if (entries == null) return value;
                var filteredEntries = new ArrayList<java.util.Map.Entry<String, DynValue>>();
                for (var entry : entries) {
                    var f = filterValue(entry.getValue(), omitNulls, omitEmptyArrays);
                    if (f != null) {
                        filteredEntries.add(java.util.Map.entry(entry.getKey(), f));
                    }
                }
                return DynValue.ofObject(filteredEntries);
            }
            default -> {
                return value;
            }
        }
    }

    private static void writeValue(JsonWriter writer, DynValue value) throws IOException {
        switch (value.getType()) {
            case Null -> writer.nullValue();
            case Bool -> writer.value(value.asBool());
            case Integer -> writer.value(value.asInt64());
            case Float, Currency, Percent -> {
                Double d = value.asDouble();
                if (d != null && !Double.isInfinite(d) && !Double.isNaN(d)) {
                    writeDouble(writer, d);
                } else {
                    writer.nullValue();
                }
            }
            case FloatRaw, CurrencyRaw -> {
                String raw = value.asString();
                if (raw != null) {
                    try {
                        writeDouble(writer, Double.parseDouble(raw));
                    } catch (NumberFormatException e) {
                        writer.value(raw);
                    }
                } else {
                    writer.nullValue();
                }
            }
            case String, Reference, Binary, Date, Timestamp, Time, Duration ->
                    writer.value(value.asString());
            case Array -> {
                writer.beginArray();
                var items = value.asArray();
                if (items != null) {
                    for (var item : items) writeValue(writer, item);
                }
                writer.endArray();
            }
            case Object -> {
                writer.beginObject();
                var entries = value.asObject();
                if (entries != null) {
                    for (var entry : entries) {
                        writer.name(entry.getKey());
                        writeValue(writer, entry.getValue());
                    }
                }
                writer.endObject();
            }
        }
    }

    private static void writeDouble(JsonWriter writer, double d) throws IOException {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            writer.value((long) d);
        } else {
            String s = Double.toString(d);
            int ePos = s.indexOf('E');
            if (ePos >= 0) {
                String mantissa = s.substring(0, ePos);
                if (mantissa.endsWith(".0")) mantissa = mantissa.substring(0, mantissa.length() - 2);
                String exponent = s.substring(ePos + 1);
                if (exponent.startsWith("+")) exponent = exponent.substring(1);
                writer.jsonValue(mantissa + "e" + exponent);
            } else {
                writer.value(d);
            }
        }
    }
}
