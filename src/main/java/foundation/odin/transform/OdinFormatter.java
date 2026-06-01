package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.TargetConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class OdinFormatter {

    private OdinFormatter() {}

    public static String formatWithModifiers(DynValue value, TargetConfig config, Map<String, OdinModifiers> modifiers) {
        return formatImpl(value, config, modifiers);
    }

    public static String format(DynValue value, TargetConfig config) {
        return formatImpl(value, config, null);
    }

    private static String formatImpl(DynValue value, TargetConfig config, Map<String, OdinModifiers> modifiers) {
        if (value == null) return "";

        boolean includeHeader = true;
        if (config != null) {
            var hdr = config.getOptions().get("header");
            if (hdr != null) includeHeader = "true".equals(hdr);
            else {
                var hdr2 = config.getOptions().get("includeHeader");
                if (hdr2 != null) includeHeader = !"false".equals(hdr2);
            }
        }

        var sb = new StringBuilder();
        if (includeHeader) sb.append("{$}\nodin = \"1.0.0\"\n");

        var entries = value.asObject();
        if (entries != null) {
            boolean hasSections = false;
            for (var entry : entries) {
                if (entry.getValue().getType() == DynValue.Type.Object || entry.getValue().getType() == DynValue.Type.Array) {
                    hasSections = true;
                    break;
                }
            }

            if (hasSections && includeHeader) {
                sb.append("{}\n");

                // First pass: flat top-level fields and leaf chains under {}.
                for (var entry : entries) {
                    if (entry.getValue().getType() == DynValue.Type.Object) {
                        collectLeafPaths(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                    } else if (entry.getValue().getType() != DynValue.Type.Array) {
                        writeAssignment(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                    }
                }

                // Second pass: sections and arrays.
                String[] lastCtx = {""};
                for (var entry : entries) {
                    if (entry.getValue().getType() == DynValue.Type.Object && !isPureLeafChain(entry.getValue())) {
                        writeSection(sb, entry.getKey(), entry.getKey(), null, entry.getValue(), modifiers, lastCtx);
                    } else if (entry.getValue().getType() == DynValue.Type.Array) {
                        writeArraySection(sb, entry.getKey(), null, entry.getValue().asArray(), modifiers);
                    }
                }
            } else if (hasSections) {
                boolean deep = hasTopLevelArray(entries);

                // First pass: top-level scalar fields.
                for (var entry : entries) {
                    var t = entry.getValue().getType();
                    if (t != DynValue.Type.Object && t != DynValue.Type.Array) {
                        writeAssignment(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                    }
                }

                // With arrays present, single-leaf object groups flatten ahead of array/header groups.
                if (deep) {
                    for (var entry : entries) {
                        var v = entry.getValue();
                        if (v.getType() == DynValue.Type.Object && isPureLeafChain(v)
                                && countLeaves(v) == 1) {
                            collectLeafPathsInner(sb, entry.getKey(), v, entry.getKey(), modifiers);
                        }
                    }
                }

                // Second pass: object sections and array sections.
                String[] lastCtx = {""};
                for (var entry : entries) {
                    var v = entry.getValue();
                    if (v.getType() == DynValue.Type.Object) {
                        if (deep && isPureLeafChain(v) && countLeaves(v) == 1) continue;
                        // A section holding only one array collapses to an absolute array block.
                        var solo = soleArrayChild(v);
                        if (solo != null) {
                            writeArraySection(sb, entry.getKey() + "." + solo.getKey(), null,
                                    solo.getValue().asArray(), modifiers);
                        } else {
                            writeSection(sb, entry.getKey(), entry.getKey(), null, v, modifiers, lastCtx);
                        }
                    } else if (v.getType() == DynValue.Type.Array) {
                        writeArraySection(sb, entry.getKey(), null, v.asArray(), modifiers);
                    }
                }
            } else {
                for (var entry : entries) {
                    writeAssignment(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                }
            }
        } else if (value.getType() == DynValue.Type.Array) {
            var items = value.asArray();
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append("{item}\n");
                    writeFieldsSimple(sb, items.get(i));
                }
            }
        } else {
            sb.append(valueToOdinString(value)).append('\n');
        }

        return sb.toString();
    }

    // Any top-level entry that is (or contains) an array makes the document "deep".
    private static boolean hasTopLevelArray(List<Map.Entry<String, DynValue>> entries) {
        for (var e : entries) {
            if (containsArray(e.getValue())) return true;
        }
        return false;
    }

    private static boolean containsArray(DynValue v) {
        if (v.getType() == DynValue.Type.Array) return true;
        if (v.getType() == DynValue.Type.Object) {
            var obj = v.asObject();
            if (obj != null) for (var e : obj) if (containsArray(e.getValue())) return true;
        }
        return false;
    }

    // An object whose only field is a single-leaf array; returns that field, else null.
    private static Map.Entry<String, DynValue> soleArrayChild(DynValue val) {
        var entries = val.asObject();
        if (entries == null || entries.size() != 1) return null;
        var e = entries.get(0);
        if (e.getValue().getType() != DynValue.Type.Array) return null;
        return countLeaves(e.getValue()) <= 1 ? e : null;
    }

    private static int countLeaves(DynValue v) {
        if (v.getType() == DynValue.Type.Array) {
            var arr = v.asArray();
            if (arr == null) return 0;
            int n = 0;
            for (var el : arr) n += countLeaves(el);
            return n;
        }
        if (v.getType() != DynValue.Type.Object) return 1;
        var obj = v.asObject();
        if (obj == null) return 1;
        int n = 0;
        for (var e : obj) n += countLeaves(e.getValue());
        return n;
    }

    // ─── Section writing ─────────────────────────────────────────────

    private static void writeSection(StringBuilder sb, String fullPath, String displayPath,
            String parentSection, DynValue val, Map<String, OdinModifiers> modifiers, String[] lastCtx) {
        writeSection(sb, fullPath, displayPath, parentSection, val, modifiers, lastCtx, false);
    }

    private static void writeSection(StringBuilder sb, String fullPath, String displayPath,
            String parentSection, DynValue val, Map<String, OdinModifiers> modifiers, String[] lastCtx,
            boolean insideRelative) {
        var entries = val.asObject();
        if (entries == null) return;

        boolean isRelative = !displayPath.isEmpty() && displayPath.charAt(0) == '.';
        sb.append('{').append(displayPath).append("}\n");
        lastCtx[0] = fullPath;

        // Pass 1: scalar assignments and pure leaf chains
        for (var entry : entries) {
            var child = entry.getValue();
            String childFullPath = fullPath + "." + entry.getKey();
            if (child.getType() == DynValue.Type.Object && isPureLeafChain(child)) {
                collectLeafPathsInner(sb, entry.getKey(), child, childFullPath, modifiers);
            } else if (child.getType() != DynValue.Type.Object && child.getType() != DynValue.Type.Array) {
                writeAssignment(sb, entry.getKey(), child, childFullPath, modifiers);
            }
        }

        // Pass 2: array sections
        for (var entry : entries) {
            if (entry.getValue().getType() == DynValue.Type.Array) {
                String arrParent = lastCtx[0].equals(fullPath) ? fullPath : null;
                writeArraySection(sb, entry.getKey(), arrParent, entry.getValue().asArray(), modifiers);
                lastCtx[0] = fullPath;
            }
        }

        // Pass 3: object subsections (non-leaf-chain)
        for (var entry : entries) {
            var child = entry.getValue();
            if (child.getType() == DynValue.Type.Object && !isPureLeafChain(child)) {
                String childFullPath = fullPath + "." + entry.getKey();
                String childDisplay;
                if (!isRelative && !insideRelative && lastCtx[0].equals(fullPath))
                    childDisplay = "." + entry.getKey();
                else
                    childDisplay = childFullPath;
                writeSection(sb, childFullPath, childDisplay, fullPath, child, modifiers, lastCtx,
                        isRelative || insideRelative);
                lastCtx[0] = fullPath;
            }
        }
    }

    // ─── Array sections ──────────────────────────────────────────────

    private static void writeArraySection(StringBuilder sb, String name, String parentSection,
            List<DynValue> items, Map<String, OdinModifiers> modifiers) {
        if (items.isEmpty()) {
            String prefix = parentSection != null ? "." : "";
            sb.append('{').append(prefix).append(name).append("[] : ~}\n");
            sb.append("~\n");
            return;
        }

        boolean allScalar = true;
        for (var item : items) {
            if (item.getType() == DynValue.Type.Object || item.getType() == DynValue.Type.Array) {
                allScalar = false;
                break;
            }
        }

        if (allScalar) {
            String prefix = parentSection != null ? "." : "";
            sb.append('{').append(prefix).append(name).append("[] : ~}\n");
            for (var item : items) sb.append(valueToOdinString(item)).append('\n');
            return;
        }

        // Array-of-arrays: positional table with [i] / [i].field columns.
        boolean allArrays = true;
        for (var item : items) {
            if (item.getType() != DynValue.Type.Array) { allArrays = false; break; }
        }
        if (allArrays) {
            var cols = positionalColumns(items);
            sb.append('{');
            if (parentSection != null) sb.append('.');
            sb.append(name).append("[] : ").append(formatColumnsWithRelative(cols)).append("}\n");
            for (var item : items) {
                var arr = item.asArray();
                for (int c = 0; c < cols.size(); c++) {
                    if (c > 0) sb.append(", ");
                    DynValue cell = resolvePositional(arr, cols.get(c));
                    if (cell != null) sb.append(valueToOdinString(cell));
                }
                sb.append('\n');
            }
            return;
        }

        // Tabular or fallback
        var columns = getConsistentColumns(items);
        if (columns != null && !columns.isEmpty()) {
            sb.append('{');
            if (parentSection != null) sb.append('.');
            sb.append(name).append("[] : ").append(formatColumnsWithRelative(columns)).append("}\n");
            for (var item : items) {
                var obj = item.asObject();
                if (obj == null) continue;
                for (int c = 0; c < columns.size(); c++) {
                    if (c > 0) sb.append(", ");
                    DynValue fieldVal = findField(obj, columns.get(c));
                    if (fieldVal != null) sb.append(valueToOdinString(fieldVal));
                }
                sb.append('\n');
            }
            return;
        }

        // Fallback: one absolute record block per item, with nested arrays/objects as sub-blocks.
        String fullName;
        if (parentSection == null || parentSection.isEmpty()) fullName = name;
        else fullName = parentSection + "." + name;
        for (int i = 0; i < items.size(); i++) {
            String itemHeader = fullName + "[" + i + "]";
            sb.append('{').append(itemHeader).append("}\n");
            writeFieldsSimple(sb, items.get(i), itemHeader);
        }
    }

    // Columns for an array of arrays: [i] for scalar slots, [i].field for object slots.
    private static List<String> positionalColumns(List<DynValue> items) {
        var cols = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var item : items) {
            var arr = item.asArray();
            if (arr == null) continue;
            for (int i = 0; i < arr.size(); i++) {
                var el = arr.get(i);
                if (el.getType() == DynValue.Type.Object) {
                    var obj = el.asObject();
                    if (obj != null) {
                        for (var f : obj) {
                            String col = "[" + i + "]." + f.getKey();
                            if (seen.add(col)) cols.add(col);
                        }
                        continue;
                    }
                }
                String col = "[" + i + "]";
                if (seen.add(col)) cols.add(col);
            }
        }
        return cols;
    }

    private static DynValue resolvePositional(List<DynValue> arr, String col) {
        if (arr == null) return null;
        int close = col.indexOf(']');
        int idx = Integer.parseInt(col.substring(1, close));
        if (idx < 0 || idx >= arr.size()) return null;
        var el = arr.get(idx);
        if (close + 1 < col.length() && col.charAt(close + 1) == '.') {
            String field = col.substring(close + 2);
            var obj = el.asObject();
            return obj != null ? findField(obj, field) : null;
        }
        return el;
    }

    private static List<String> getConsistentColumns(List<DynValue> items) {
        var allColumns = new ArrayList<String>();
        var columnSet = new HashSet<String>();

        for (var item : items) {
            var obj = item.asObject();
            if (obj == null) return null;
            var itemCols = new ArrayList<String>();
            if (!collectFlatColumns(obj, "", itemCols)) return null;
            for (var col : itemCols) {
                if (columnSet.add(col)) allColumns.add(col);
            }
        }
        if (allColumns.isEmpty()) return null;

        // Reject tabular if any indexed sub-array column (`key[N]`) is sparse —
        // padding shorter rows with empty cells loses to the nested record-block form.
        for (var col : allColumns) {
            if (col.isEmpty() || col.charAt(col.length() - 1) != ']') continue;
            int open = col.lastIndexOf('[');
            if (open <= 0) continue;
            boolean allDigits = true;
            for (int i = open + 1; i < col.length() - 1; i++) {
                char c = col.charAt(i);
                if (c < '0' || c > '9') { allDigits = false; break; }
            }
            if (!allDigits) continue;
            for (var item : items) {
                var obj = item.asObject();
                if (obj == null || findField(obj, col) == null) return null;
            }
        }
        return allColumns;
    }

    private static boolean collectFlatColumns(List<Map.Entry<String, DynValue>> obj, String prefix, List<String> columns) {
        for (var entry : obj) {
            String colName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            var val = entry.getValue();
            if (val.getType() == DynValue.Type.Object) {
                var nested = val.asObject();
                if (nested == null) return false;
                if (!prefix.isEmpty()) return false; // No multi-level nesting in tabular
                if (!collectFlatColumns(nested, entry.getKey(), columns)) return false;
            } else if (val.getType() == DynValue.Type.Array) {
                // Only accept single-level primitive sub-arrays at top level.
                if (!prefix.isEmpty()) return false;
                var arr = val.asArray();
                if (arr == null) return false;
                for (var el : arr) {
                    var t = el.getType();
                    if (t == DynValue.Type.Object || t == DynValue.Type.Array) return false;
                }
                for (int i = 0; i < arr.size(); i++) {
                    columns.add(entry.getKey() + "[" + i + "]");
                }
            } else {
                columns.add(colName);
            }
        }
        return true;
    }

    private static String formatColumnsWithRelative(List<String> columns) {
        var sb = new StringBuilder();
        String currentParent = "";
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            int dotIdx = columns.get(i).indexOf('.');
            if (dotIdx > 0) {
                String parent = columns.get(i).substring(0, dotIdx);
                String field = columns.get(i).substring(dotIdx + 1);
                if (parent.equals(currentParent)) {
                    sb.append('.').append(field);
                } else {
                    sb.append(columns.get(i));
                    currentParent = parent;
                }
            } else {
                sb.append(columns.get(i));
                currentParent = "";
            }
        }
        return sb.toString();
    }

    private static DynValue findField(List<Map.Entry<String, DynValue>> obj, String key) {
        // Support dotted paths for nested object access
        int dotIdx = key.indexOf('.');
        if (dotIdx > 0) {
            String parent = key.substring(0, dotIdx);
            String child = key.substring(dotIdx + 1);
            for (var entry : obj) {
                if (entry.getKey().equals(parent) && entry.getValue().getType() == DynValue.Type.Object) {
                    var nested = entry.getValue().asObject();
                    if (nested != null) return findField(nested, child);
                }
            }
            return null;
        }
        // Support `key[N]` sub-array indexing
        int bracketIdx = key.indexOf('[');
        if (bracketIdx > 0 && key.endsWith("]")) {
            String base = key.substring(0, bracketIdx);
            String idxStr = key.substring(bracketIdx + 1, key.length() - 1);
            int idx;
            try { idx = Integer.parseInt(idxStr); } catch (NumberFormatException e) { return null; }
            for (var entry : obj) {
                if (entry.getKey().equals(base) && entry.getValue().getType() == DynValue.Type.Array) {
                    var arr = entry.getValue().asArray();
                    if (arr != null && idx >= 0 && idx < arr.size()) return arr.get(idx);
                    return null;
                }
            }
            return null;
        }
        for (var entry : obj) {
            if (entry.getKey().equals(key)) return entry.getValue();
        }
        return null;
    }

    // ─── Leaf chain ──────────────────────────────────────────────────

    private static boolean isPureLeafChain(DynValue val) {
        var entries = val.asObject();
        if (entries == null || entries.size() != 1) return false;
        var child = entries.get(0).getValue();
        if (child.getType() == DynValue.Type.Object) return isPureLeafChain(child);
        if (child.getType() == DynValue.Type.Array) return false;
        return true;
    }

    private static void collectLeafPaths(StringBuilder sb, String prefix, DynValue val,
            String modPath, Map<String, OdinModifiers> modifiers) {
        if (!isPureLeafChain(val)) return;
        collectLeafPathsInner(sb, prefix, val, modPath, modifiers);
    }

    private static void collectLeafPathsInner(StringBuilder sb, String prefix, DynValue val,
            String modPath, Map<String, OdinModifiers> modifiers) {
        var entries = val.asObject();
        if (entries != null) {
            for (var entry : entries) {
                String path = prefix + "." + entry.getKey();
                String mp = modPath + "." + entry.getKey();
                if (entry.getValue().getType() == DynValue.Type.Object)
                    collectLeafPathsInner(sb, path, entry.getValue(), mp, modifiers);
                else
                    writeAssignment(sb, path, entry.getValue(), mp, modifiers);
            }
        } else {
            writeAssignment(sb, prefix, val, modPath, modifiers);
        }
    }

    // ─── Assignment writing ──────────────────────────────────────────

    private static String modifierPrefix(String path, Map<String, OdinModifiers> modifiers) {
        if (modifiers == null || !modifiers.containsKey(path)) return "";
        var mods = modifiers.get(path);
        var sb = new StringBuilder();
        if (mods.isRequired()) sb.append('!');
        if (mods.isDeprecated()) sb.append('-');
        if (mods.isConfidential()) sb.append('*');
        return sb.toString();
    }

    private static void writeAssignment(StringBuilder sb, String key, DynValue value,
            String fullPath, Map<String, OdinModifiers> modifiers) {
        sb.append(key).append(" = ");
        sb.append(modifierPrefix(fullPath, modifiers));
        sb.append(valueToOdinString(value)).append('\n');
    }

    private static void writeFieldsSimple(StringBuilder sb, DynValue value) {
        writeFieldsSimple(sb, value, null);
    }

    /**
     * Emit an object's fields under an already-opened header. Nested arrays and
     * objects become sub-blocks rooted at {@code parentHeader} when non-null.
     */
    private static void writeFieldsSimple(StringBuilder sb, DynValue value, String parentHeader) {
        var entries = value.asObject();
        if (entries == null) return;
        // Pass 1: scalar assignments
        for (var entry : entries) {
            var v = entry.getValue();
            var t = v.getType();
            if (t == DynValue.Type.Object || t == DynValue.Type.Array) continue;
            sb.append(entry.getKey()).append(" = ").append(valueToOdinString(v)).append('\n');
        }
        // Pass 2: nested arrays as relative sub-blocks against the open record header.
        for (var entry : entries) {
            var v = entry.getValue();
            if (v.getType() != DynValue.Type.Array) continue;
            var arr = v.asArray();
            if (arr == null) continue;
            if (parentHeader != null) {
                writeArraySection(sb, entry.getKey(), parentHeader, arr, null);
            } else {
                writeArraySection(sb, entry.getKey(), null, arr, null);
            }
        }
        // Pass 3: nested objects as relative sub-sections
        for (var entry : entries) {
            var v = entry.getValue();
            if (v.getType() != DynValue.Type.Object) continue;
            String childHeader = (parentHeader != null ? parentHeader + "." : "") + entry.getKey();
            sb.append('{').append(childHeader).append("}\n");
            writeFieldsSimple(sb, v, childHeader);
        }
    }

    // ─── Value serialization ─────────────────────────────────────────

    private static String valueToOdinString(DynValue value) {
        return switch (value.getType()) {
            case Null -> "~";
            case Bool -> (value.asBool() != null && value.asBool()) ? "?true" : "?false";
            case Integer -> "##" + value.asInt64();
            case Float -> {
                double n = value.asDouble() != null ? value.asDouble() : 0.0;
                if (!Double.isInfinite(n) && !Double.isNaN(n)) {
                    if (n == Math.floor(n) && Math.abs(n) < 1e15)
                        yield "#" + (long) n;
                    yield "#" + formatDouble(n);
                }
                yield "~";
            }
            case FloatRaw -> "#" + (value.asString() != null ? value.asString() : "0");
            case Currency -> {
                double n = value.asDouble() != null ? value.asDouble() : 0.0;
                if (!Double.isInfinite(n) && !Double.isNaN(n)) {
                    int dp = value.getDecimalPlaces();
                    String formatted = String.format("%." + dp + "f", n);
                    String code = value.getCurrencyCode();
                    yield code != null ? "#$" + formatted + ":" + code : "#$" + formatted;
                }
                yield "~";
            }
            case CurrencyRaw -> {
                String raw = value.asString() != null ? value.asString() : "0";
                String code = value.getCurrencyCode();
                yield code != null ? "#$" + raw + ":" + code : "#$" + raw;
            }
            case Percent -> {
                double n = value.asDouble() != null ? value.asDouble() : 0.0;
                if (!Double.isInfinite(n) && !Double.isNaN(n)) yield "#%" + n;
                yield "~";
            }
            case Reference -> "@" + (value.asString() != null ? value.asString() : "");
            case Binary -> "^" + (value.asString() != null ? value.asString() : "");
            case Date, Duration -> value.asString() != null ? value.asString() : "";
            case Timestamp -> value.asString() != null ? value.asString() : "";
            case Time -> {
                String t = value.asString() != null ? value.asString() : "";
                yield !t.isEmpty() && t.charAt(0) != 'T' ? "T" + t : t;
            }
            case String -> "\"" + escapeOdinString(value.asString() != null ? value.asString() : "") + "\"";
            case Array, Object -> "~";
        };
    }

    private static String formatDouble(double n) {
        return JsNumber.toString(n);
    }

    private static String escapeOdinString(String s) {
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
