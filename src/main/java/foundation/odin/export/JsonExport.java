package foundation.odin.export;

import com.google.gson.stream.JsonWriter;
import foundation.odin.types.OdinArrayItem;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public final class JsonExport {

    private JsonExport() {}

    public static String toJson(OdinDocument doc) {
        return toJson(doc, false, false);
    }

    public static String toJson(OdinDocument doc, boolean preserveTypes, boolean preserveModifiers) {
        try {
            var sw = new StringWriter();
            var writer = new JsonWriter(sw);
            writer.setIndent("  ");
            writer.setHtmlSafe(false);

            writer.beginObject();
            writeAssignments(writer, doc, preserveTypes, preserveModifiers);
            writer.endObject();

            writer.flush();
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException("JSON export failed", e);
        }
    }

    private static void writeAssignments(JsonWriter writer, OdinDocument doc,
            boolean preserveTypes, boolean preserveModifiers) throws IOException {
        var sections = new LinkedHashMap<String, List<Map.Entry<String, OdinValue>>>();
        var arraySections = new LinkedHashMap<String, List<List<Map.Entry<String, OdinValue>>>>();
        var sectionOrder = new ArrayList<String>();
        var topLevelScalars = new ArrayList<Map.Entry<String, OdinValue>>();

        for (var entry : doc.getAssignments()) {
            String key = entry.getKey();
            int dotIndex = key.indexOf('.');
            if (dotIndex <= 0) {
                topLevelScalars.add(Map.entry(key, entry.getValue()));
                continue;
            }

            String section = key.substring(0, dotIndex);
            String field = key.substring(dotIndex + 1);

            // Check for array index: section[N].field
            int bracketPos = section.indexOf('[');
            if (bracketPos >= 0) {
                String arrName = section.substring(0, bracketPos);
                String idxStr = section.substring(bracketPos + 1, section.length() - 1);
                try {
                    int arrIdx = Integer.parseInt(idxStr);
                    arraySections.computeIfAbsent(arrName, k -> {
                        if (!sectionOrder.contains(k)) sectionOrder.add(k);
                        return new ArrayList<>();
                    });
                    var rows = arraySections.get(arrName);
                    while (rows.size() <= arrIdx) rows.add(new ArrayList<>());
                    rows.get(arrIdx).add(Map.entry(field, entry.getValue()));
                    continue;
                } catch (NumberFormatException ignored) {}
            }

            // Handle deeper nested paths: section.subsection.field
            int secondDot = field.indexOf('.');
            if (secondDot > 0) {
                String subsection = section + "." + field.substring(0, secondDot);
                String subfield = field.substring(secondDot + 1);
                sections.computeIfAbsent(subsection, k -> {
                    if (!sectionOrder.contains(k)) sectionOrder.add(k);
                    return new ArrayList<>();
                });
                sections.get(subsection).add(Map.entry(subfield, entry.getValue()));
            } else {
                sections.computeIfAbsent(section, k -> {
                    if (!sectionOrder.contains(k)) sectionOrder.add(k);
                    return new ArrayList<>();
                });
                sections.get(section).add(Map.entry(field, entry.getValue()));
            }
        }

        // Top-level scalars first, then non-$ sections, then $
        for (var entry : topLevelScalars) {
            writer.name(entry.getKey());
            writeValue(writer, entry.getValue(), preserveTypes);
        }
        for (String name : sectionOrder) {
            if ("$".equals(name)) continue;
            writeSectionOrArray(writer, name, sections, arraySections, preserveTypes);
        }
        if (sectionOrder.contains("$")) {
            writeSectionOrArray(writer, "$", sections, arraySections, preserveTypes);
        }
    }

    private static void writeSectionOrArray(JsonWriter writer, String name,
            Map<String, List<Map.Entry<String, OdinValue>>> sections,
            Map<String, List<List<Map.Entry<String, OdinValue>>>> arraySections,
            boolean preserveTypes) throws IOException {
        if (arraySections.containsKey(name)) {
            writer.name(name);
            writer.beginArray();
            for (var record : arraySections.get(name)) {
                writer.beginObject();
                for (var field : record) {
                    writer.name(field.getKey());
                    writeValue(writer, field.getValue(), preserveTypes);
                }
                writer.endObject();
            }
            writer.endArray();
        } else if (sections.containsKey(name)) {
            writer.name(name);
            writer.beginObject();
            for (var field : sections.get(name)) {
                writer.name(field.getKey());
                writeValue(writer, field.getValue(), preserveTypes);
            }
            writer.endObject();
        }
    }

    private static void writeValue(JsonWriter writer, OdinValue value, boolean preserveTypes) throws IOException {
        switch (value) {
            case OdinValue.OdinNull n -> writer.nullValue();
            case OdinValue.OdinBoolean b -> writer.value(b.getValue());
            case OdinValue.OdinString s -> writer.value(s.getValue());
            case OdinValue.OdinInteger i -> writer.value(i.getValue());
            case OdinValue.OdinNumber n -> writeNumber(writer, n.getValue());
            case OdinValue.OdinCurrency c -> writeNumber(writer, c.getValue());
            case OdinValue.OdinPercent p -> writeNumber(writer, p.getValue());
            case OdinValue.OdinDate d -> writer.value(d.getRaw());
            case OdinValue.OdinTimestamp ts -> writer.value(ts.getRaw());
            case OdinValue.OdinTime t -> writer.value(t.getValue());
            case OdinValue.OdinDuration d -> writer.value(d.getValue());
            case OdinValue.OdinReference r -> writer.value("@" + r.getPath());
            case OdinValue.OdinBinary b -> {
                String b64 = Base64.getEncoder().encodeToString(b.getData());
                writer.value(b.getAlgorithm() != null ? "^" + b.getAlgorithm() + ":" + b64 : "^" + b64);
            }
            case OdinValue.OdinArray arr -> {
                writer.beginArray();
                for (var item : arr.getItems()) {
                    if (item instanceof OdinArrayItem.OdinArrayValue av) {
                        writeValue(writer, av.getValue(), preserveTypes);
                    } else if (item instanceof OdinArrayItem.OdinArrayRecord rec) {
                        writer.beginObject();
                        for (var f : rec.getFields()) {
                            writer.name(f.getKey());
                            writeValue(writer, f.getValue(), preserveTypes);
                        }
                        writer.endObject();
                    }
                }
                writer.endArray();
            }
            case OdinValue.OdinObject obj -> {
                writer.beginObject();
                for (var f : obj.getFields()) {
                    writer.name(f.getKey());
                    writeValue(writer, f.getValue(), preserveTypes);
                }
                writer.endObject();
            }
            default -> writer.nullValue();
        }
    }

    private static void writeNumber(JsonWriter writer, double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            writer.nullValue();
            return;
        }
        if (value == Math.floor(value) && !Double.isInfinite(value) && Math.abs(value) < 1e15) {
            writer.value((long) value);
        } else {
            // Normalize scientific notation: 1.0E-18 -> 1e-18
            String s = Double.toString(value);
            int ePos = s.indexOf('E');
            if (ePos >= 0) {
                String mantissa = s.substring(0, ePos);
                // Remove trailing .0 from mantissa: 1.0 -> 1
                if (mantissa.endsWith(".0")) mantissa = mantissa.substring(0, mantissa.length() - 2);
                String exponent = s.substring(ePos + 1);
                // Remove leading + from exponent
                if (exponent.startsWith("+")) exponent = exponent.substring(1);
                writer.jsonValue(mantissa + "e" + exponent);
            } else {
                writer.value(value);
            }
        }
    }
}
