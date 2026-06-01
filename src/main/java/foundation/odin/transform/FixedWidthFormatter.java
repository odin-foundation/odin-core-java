package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.FieldMapping;
import foundation.odin.types.OdinTransformTypes.TransformSegment;
import foundation.odin.types.TargetConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class FixedWidthFormatter {

    private FixedWidthFormatter() {}

    // ── Segment-driven mode ──

    public static String formatFromSegments(DynValue value, List<TransformSegment> segments, TargetConfig config) {
        if (value == null || segments == null || segments.isEmpty()) return "";

        // Literal-block segments emit pre-rendered interpolated lines verbatim.
        boolean hasLiteralLines = false;
        var outerObj = value.asObject();
        if (outerObj != null) {
            for (var entry : outerObj) {
                if (isLiteralLines(entry.getValue())) { hasLiteralLines = true; break; }
            }
        }

        // Check if any segment has :pos/:len directives
        boolean hasPositionalFields = false;
        for (var seg : segments) {
            if (hasPositionalDirectives(seg)) {
                hasPositionalFields = true;
                break;
            }
        }

        if (hasLiteralLines) return formatWithLiteralLines(value, segments, config);

        if (!hasPositionalFields) return format(value, config);

        var outputObj = value.asObject();
        if (outputObj == null) return "";

        // Read fixed-width record options.
        int lineWidth = -1;
        char padChar = ' ';
        boolean truncate = true;
        if (config != null) {
            var lw = config.getOptions().get("lineWidth");
            if (lw != null) {
                try { lineWidth = Integer.parseInt(lw.trim()); } catch (NumberFormatException ignored) {}
            }
            var pc = config.getOptions().get("padChar");
            if (pc != null && !pc.isEmpty()) padChar = pc.charAt(0);
            if ("false".equals(config.getOptions().get("truncate"))) truncate = false;
        }

        var sb = new StringBuilder();

        for (var seg : segments) {
            var segName = seg.getName();
            if (segName == null || segName.isEmpty() || segName.equals("$") || segName.equals("_root"))
                continue;

            boolean isArray = segName.endsWith("[]");
            var cleanName = isArray ? segName.substring(0, segName.length() - 2) : segName;

            // Find the data for this segment
            DynValue segData = null;
            for (var entry : outputObj) {
                if (entry.getKey().equals(cleanName)) {
                    segData = entry.getValue();
                    break;
                }
            }
            if (segData == null) continue;

            var fieldDefs = collectFieldDefs(seg);
            if (fieldDefs.isEmpty()) continue;

            if (isArray && segData.getType() == DynValue.Type.Array) {
                var items = segData.asArray();
                if (items != null) {
                    for (var item : items) {
                        var itemObj = item.asObject();
                        if (itemObj == null) continue;
                        sb.append(padToWidth(buildFixedWidthLine(fieldDefs, itemObj), lineWidth, padChar, truncate));
                        sb.append('\n');
                    }
                }
            } else if (segData.getType() == DynValue.Type.Object) {
                var obj = segData.asObject();
                if (obj != null) {
                    sb.append(padToWidth(buildFixedWidthLine(fieldDefs, obj), lineWidth, padChar, truncate));
                    sb.append('\n');
                }
            }
        }

        return sb.toString();
    }

    // Pre-rendered literal-block lines emitted by the engine.
    private static boolean isLiteralLines(DynValue data) {
        if (data == null) return false;
        var obj = data.asObject();
        if (obj == null) return false;
        for (var entry : obj) {
            if ("__literalLines".equals(entry.getKey()) && entry.getValue().asArray() != null) return true;
        }
        return false;
    }

    private static List<DynValue> literalLines(DynValue data) {
        var obj = data.asObject();
        if (obj == null) return List.of();
        for (var entry : obj) {
            if ("__literalLines".equals(entry.getKey())) {
                var arr = entry.getValue().asArray();
                if (arr != null) return arr;
            }
        }
        return List.of();
    }

    // Emit segments in order; literal segments contribute their rendered lines verbatim,
    // positional segments are formatted as fixed-width records.
    private static String formatWithLiteralLines(DynValue value, List<TransformSegment> segments, TargetConfig config) {
        var outputObj = value.asObject();
        if (outputObj == null) return "";

        int lineWidth = -1;
        char padChar = ' ';
        boolean truncate = true;
        if (config != null) {
            var lw = config.getOptions().get("lineWidth");
            if (lw != null) { try { lineWidth = Integer.parseInt(lw.trim()); } catch (NumberFormatException ignored) {} }
            var pc = config.getOptions().get("padChar");
            if (pc != null && !pc.isEmpty()) padChar = pc.charAt(0);
            if ("false".equals(config.getOptions().get("truncate"))) truncate = false;
        }

        var sb = new StringBuilder();
        for (var seg : segments) {
            var segName = seg.getName();
            if (segName == null || segName.isEmpty() || segName.equals("$") || segName.equals("_root")) continue;
            boolean isArray = segName.endsWith("[]");
            var cleanName = isArray ? segName.substring(0, segName.length() - 2) : segName;

            DynValue segData = null;
            for (var entry : outputObj) {
                if (entry.getKey().equals(cleanName)) { segData = entry.getValue(); break; }
            }
            if (segData == null) continue;

            if (isLiteralLines(segData)) {
                for (var line : literalLines(segData)) {
                    sb.append(line.asString() != null ? line.asString() : "");
                    sb.append('\n');
                }
                continue;
            }

            var fieldDefs = collectFieldDefs(seg);
            if (fieldDefs.isEmpty()) continue;
            if (isArray && segData.getType() == DynValue.Type.Array) {
                var items = segData.asArray();
                if (items != null) {
                    for (var item : items) {
                        var itemObj = item.asObject();
                        if (itemObj == null) continue;
                        sb.append(padToWidth(buildFixedWidthLine(fieldDefs, itemObj), lineWidth, padChar, truncate));
                        sb.append('\n');
                    }
                }
            } else if (segData.getType() == DynValue.Type.Object) {
                var obj = segData.asObject();
                if (obj != null) {
                    sb.append(padToWidth(buildFixedWidthLine(fieldDefs, obj), lineWidth, padChar, truncate));
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    // Pad a record to the configured line width using padChar; truncate when longer.
    private static String padToWidth(String line, int lineWidth, char padChar, boolean truncate) {
        if (lineWidth < 0) return line;
        if (line.length() < lineWidth) {
            var sb = new StringBuilder(line);
            while (sb.length() < lineWidth) sb.append(padChar);
            return sb.toString();
        }
        if (line.length() > lineWidth && truncate) return line.substring(0, lineWidth);
        return line;
    }

    private static boolean hasPositionalDirectives(TransformSegment segment) {
        for (var mapping : segment.getMappings()) {
            for (var d : mapping.getDirectives()) {
                if ("pos".equals(d.getName()) || "len".equals(d.getName())) return true;
            }
        }
        for (var item : segment.getItems()) {
            var m = item.asMapping();
            if (m != null) {
                for (var d : m.getDirectives()) {
                    if ("pos".equals(d.getName()) || "len".equals(d.getName())) return true;
                }
            }
        }
        return false;
    }

    private record FieldDef(String name, int pos, int len, String leftPad, String rightPad) {}

    private static List<FieldDef> collectFieldDefs(TransformSegment segment) {
        var defs = new ArrayList<FieldDef>();

        // Collect from Items list
        for (var item : segment.getItems()) {
            var m = item.asMapping();
            if (m != null) {
                var def = extractFieldDef(m);
                if (def != null) defs.add(def);
            }
        }

        // Also collect from Mappings if Items empty
        if (defs.isEmpty()) {
            for (var mapping : segment.getMappings()) {
                var def = extractFieldDef(mapping);
                if (def != null) defs.add(def);
            }
        }

        defs.sort((a, b) -> Integer.compare(a.pos, b.pos));
        return defs;
    }

    private static FieldDef extractFieldDef(FieldMapping mapping) {
        if (mapping.getTarget().startsWith("_")) return null;

        int pos = -1, len = -1;
        String leftPad = null, rightPad = null;

        for (var dir : mapping.getDirectives()) {
            switch (dir.getName()) {
                case "pos" -> {
                    var numVal = dir.getValue() != null ? dir.getValue().asNumber() : null;
                    if (numVal != null) pos = numVal.intValue();
                }
                case "len" -> {
                    var numVal = dir.getValue() != null ? dir.getValue().asNumber() : null;
                    if (numVal != null) len = numVal.intValue();
                }
                case "leftPad" -> leftPad = dir.getValue() != null ? dir.getValue().asString() : null;
                case "rightPad" -> rightPad = dir.getValue() != null ? dir.getValue().asString() : null;
            }
        }

        if (pos < 0 || len <= 0) return null;
        return new FieldDef(mapping.getTarget(), pos, len, leftPad, rightPad);
    }

    private static String buildFixedWidthLine(List<FieldDef> fieldDefs, List<Map.Entry<String, DynValue>> data) {
        int lineWidth = 0;
        for (var def : fieldDefs) {
            int end = def.pos + def.len;
            if (end > lineWidth) lineWidth = end;
        }

        char[] line = new char[lineWidth];
        Arrays.fill(line, ' ');

        for (var def : fieldDefs) {
            DynValue fieldVal = findField(data, def.name);
            String text = fieldToString(fieldVal);

            if (text.length() > def.len) text = text.substring(0, def.len);

            if (def.leftPad != null && !def.leftPad.isEmpty()) {
                char padChar = def.leftPad.charAt(0);
                int padding = def.len - text.length();
                for (int p = 0; p < padding; p++) line[def.pos + p] = padChar;
                for (int c = 0; c < text.length(); c++) line[def.pos + padding + c] = text.charAt(c);
            } else if (def.rightPad != null && !def.rightPad.isEmpty()) {
                char padChar = def.rightPad.charAt(0);
                for (int c = 0; c < text.length(); c++) line[def.pos + c] = text.charAt(c);
                for (int p = text.length(); p < def.len; p++) line[def.pos + p] = padChar;
            } else {
                for (int c = 0; c < text.length(); c++) line[def.pos + c] = text.charAt(c);
            }
        }

        return new String(line);
    }

    // ── Config-driven mode ──

    public static String format(DynValue value, TargetConfig config) {
        if (value == null) return "";

        var columns = parseColumns(config);
        if (columns.isEmpty()) return "";

        // Unwrap single-key objects containing arrays
        DynValue resolved = value;
        var objEntries = value.asObject();
        if (objEntries != null && objEntries.size() == 1) {
            var inner = objEntries.get(0).getValue();
            if (inner.asArray() != null) resolved = inner;
        }

        // Collect records
        var records = new ArrayList<List<Map.Entry<String, DynValue>>>();
        var arr = resolved.asArray();
        if (arr != null) {
            for (var item : arr) {
                var obj = item.asObject();
                if (obj != null) records.add(obj);
            }
        } else {
            var singleObj = resolved.asObject();
            if (singleObj != null) records.add(singleObj);
        }

        if (records.isEmpty()) return "";

        var sb = new StringBuilder();
        for (var fields : records) {
            for (int c = 0; c < columns.size(); c++) {
                var col = columns.get(c);
                DynValue fieldVal = findField(fields, col.name);
                String text = fieldToString(fieldVal);
                boolean isNumeric = fieldVal != null && isNumericType(fieldVal);
                boolean rightAlign = "right".equals(col.alignment) || (col.alignment == null && isNumeric);

                if (text.length() > col.width) text = text.substring(0, col.width);

                if (rightAlign) {
                    int padding = col.width - text.length();
                    sb.append(" ".repeat(Math.max(0, padding)));
                    sb.append(text);
                } else {
                    sb.append(text);
                    int padding = col.width - text.length();
                    sb.append(" ".repeat(Math.max(0, padding)));
                }
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static DynValue findField(List<Map.Entry<String, DynValue>> obj, String key) {
        for (var entry : obj) {
            if (entry.getKey().equals(key)) return entry.getValue();
        }
        return null;
    }

    private static boolean isNumericType(DynValue value) {
        return switch (value.getType()) {
            case Integer, Float, Currency, Percent, FloatRaw, CurrencyRaw -> true;
            default -> false;
        };
    }

    private static String fieldToString(DynValue value) {
        if (value == null) return "";
        return switch (value.getType()) {
            case Null, Array, Object -> "";
            case Bool -> (value.asBool() != null && value.asBool()) ? "true" : "false";
            case Integer -> String.valueOf(value.asInt64());
            case Float, Currency, Percent -> {
                double d = value.asDouble() != null ? value.asDouble() : 0.0;
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                    yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case FloatRaw, CurrencyRaw -> value.asString() != null ? value.asString() : "";
            default -> value.asString() != null ? value.asString() : "";
        };
    }

    private static List<ColumnDef> parseColumns(TargetConfig config) {
        var columns = new ArrayList<ColumnDef>();
        if (config == null) return columns;

        var columnsStr = config.getOptions().get("columns");
        if (columnsStr == null || columnsStr.isEmpty()) return columns;

        for (var part : columnsStr.split(";")) {
            var trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            var segs = trimmed.split(":");
            if (segs.length < 2) continue;
            String name = segs[0].trim();
            try {
                int width = Integer.parseInt(segs[1].trim());
                String alignment = segs.length >= 3 ? segs[2].trim() : null;
                columns.add(new ColumnDef(name, width, alignment));
            } catch (NumberFormatException ignored) {}
        }
        return columns;
    }

    private record ColumnDef(String name, int width, String alignment) {}
}
