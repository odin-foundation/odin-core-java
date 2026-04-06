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

            if (hasSections) {
                if (includeHeader) sb.append("{}\n");

                // First pass: flat top-level fields and leaf chains
                for (var entry : entries) {
                    if (entry.getValue().getType() == DynValue.Type.Object) {
                        collectLeafPaths(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                    } else if (entry.getValue().getType() != DynValue.Type.Array) {
                        writeAssignment(sb, entry.getKey(), entry.getValue(), entry.getKey(), modifiers);
                    }
                }

                // Second pass: sections and arrays
                String[] lastCtx = {""};
                for (var entry : entries) {
                    if (entry.getValue().getType() == DynValue.Type.Object && !isPureLeafChain(entry.getValue())) {
                        writeSection(sb, entry.getKey(), entry.getKey(), null, entry.getValue(), modifiers, lastCtx);
                    } else if (entry.getValue().getType() == DynValue.Type.Array) {
                        writeArraySection(sb, entry.getKey(), null, entry.getValue().asArray(), modifiers);
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

        sb.append('{').append(name).append("[]}\n");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append("{---}\n");
            writeFieldsSimple(sb, items.get(i));
        }
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
        return allColumns.isEmpty() ? null : allColumns;
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
                return false;
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
        var entries = value.asObject();
        if (entries == null) return;
        for (var entry : entries) {
            sb.append(entry.getKey()).append(" = ").append(valueToOdinString(entry.getValue())).append('\n');
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
        String s = Double.toString(n);
        // Normalize: 6.022E23 -> 6.022e+23, -2.73E-2 -> -2.73e-2
        int ePos = s.indexOf('E');
        if (ePos >= 0) {
            String mantissa = s.substring(0, ePos);
            String exponent = s.substring(ePos + 1);
            if (!exponent.startsWith("-") && !exponent.startsWith("+"))
                exponent = "+" + exponent;
            return mantissa + "e" + exponent;
        }
        return s;
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
