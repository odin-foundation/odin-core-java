package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.TargetConfig;

import java.util.Map;

public final class XmlFormatter {

    private XmlFormatter() {}

    public static String formatWithModifiers(DynValue value, TargetConfig config, Map<String, OdinModifiers> modifiers) {
        if (value == null) return "";

        // Read options
        boolean includeDeclaration = true;
        int indent = 2;
        if (config != null) {
            var declOpt = config.getOptions().get("declaration");
            if ("false".equals(declOpt)) includeDeclaration = false;
            var indentOpt = config.getOptions().get("indent");
            if (indentOpt != null) {
                try { indent = Integer.parseInt(indentOpt); } catch (NumberFormatException ignored) {}
            }
        }

        var sb = new StringBuilder();
        if (includeDeclaration) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        }

        var entries = value.asObject();
        if (entries == null) {
            String rootEl = "root";
            if (config != null) {
                var re = config.getOptions().get("rootElement");
                if (re != null && !re.isEmpty()) rootEl = re;
            }
            writeElement(sb, rootEl, value, indent, 0, modifiers, "", false);
            return sb.toString();
        }

        boolean needsNamespace = hasTypedValues(value);

        for (var entry : entries) {
            String key = entry.getKey();
            var child = entry.getValue();

            if (child.getType() == DynValue.Type.Array) {
                var items = child.asArray();
                if (items != null) {
                    for (var item : items) {
                        writeArrayItemElement(sb, key, item, indent, 0, modifiers, key);
                    }
                }
            } else {
                sb.append('<').append(key);
                if (needsNamespace) sb.append(" xmlns:odin=\"https://odin.foundation/ns\"");
                sb.append(">\n");
                writeObjectChildren(sb, child, indent, 1, modifiers, key);
                sb.append("</").append(key).append(">\n");
            }
        }

        return sb.toString();
    }

    public static String format(DynValue value, TargetConfig config) {
        return formatWithModifiers(value, config, Map.of());
    }

    private static void writeArrayItemElement(StringBuilder sb, String tag, DynValue item,
            int indent, int depth, Map<String, OdinModifiers> modifiers, String pathPrefix) {
        var pad = pad(indent, depth);

        if (item.getType() == DynValue.Type.Object) {
            var entries = item.asObject();
            if (entries == null) return;

            // Separate :attr fields from child elements
            var attrFields = new java.util.ArrayList<Map.Entry<String, DynValue>>();
            var childFields = new java.util.ArrayList<Map.Entry<String, DynValue>>();
            for (var entry : entries) {
                String modKey = pathPrefix + "." + entry.getKey();
                var mods = modifiers.get(modKey);
                if (mods != null && mods.isAttr())
                    attrFields.add(entry);
                else
                    childFields.add(entry);
            }

            sb.append(pad).append('<').append(tag);
            for (var attr : attrFields) {
                sb.append(' ').append(attr.getKey()).append("=\"");
                sb.append(xmlEscape(scalarToString(attr.getValue())));
                sb.append('"');
            }
            sb.append(">\n");

            for (var entry : childFields) {
                writeElement(sb, entry.getKey(), entry.getValue(), indent, depth + 1,
                        modifiers, pathPrefix + "." + entry.getKey(), true);
            }
            sb.append(pad).append("</").append(tag).append(">\n");
        } else {
            writeElement(sb, tag, item, indent, depth, modifiers, pathPrefix, true);
        }
    }

    private static void writeObjectChildren(StringBuilder sb, DynValue value, int indent, int depth,
            Map<String, OdinModifiers> modifiers, String pathPrefix) {
        var entries = value.asObject();
        if (entries == null) return;
        for (var entry : entries) {
            String childPath = pathPrefix + "." + entry.getKey();
            writeElement(sb, entry.getKey(), entry.getValue(), indent, depth, modifiers, childPath, true);
        }
    }

    private static void writeElement(StringBuilder sb, String tag, DynValue value, int indent, int depth,
            Map<String, OdinModifiers> modifiers, String modKey, boolean includeTypeAttr) {
        var pad = pad(indent, depth);

        switch (value.getType()) {
            case Null -> sb.append(pad).append('<').append(tag).append(" odin:type=\"null\"></").append(tag).append(">\n");
            case Array -> {
                var items = value.asArray();
                if (items != null) {
                    for (var item : items)
                        writeArrayItemElement(sb, tag, item, indent, depth, modifiers, modKey);
                }
            }
            case Object -> {
                sb.append(pad).append('<').append(tag).append(">\n");
                writeObjectChildren(sb, value, indent, depth + 1, modifiers, modKey);
                sb.append(pad).append("</").append(tag).append(">\n");
            }
            default -> {
                sb.append(pad).append('<').append(tag);

                if (includeTypeAttr) {
                    String typeAttr = getTypeAttribute(value);
                    if (typeAttr != null) sb.append(" odin:type=\"").append(typeAttr).append('"');
                }

                if (modifiers.containsKey(modKey)) {
                    var mods = modifiers.get(modKey);
                    if (mods.isRequired()) sb.append(" odin:required=\"true\"");
                    if (mods.isConfidential()) sb.append(" odin:confidential=\"true\"");
                    if (mods.isDeprecated()) sb.append(" odin:deprecated=\"true\"");
                }

                sb.append('>');
                sb.append(xmlEscape(scalarToString(value)));
                sb.append("</").append(tag).append(">\n");
            }
        }
    }

    private static String getTypeAttribute(DynValue value) {
        return switch (value.getType()) {
            case Bool -> "boolean";
            case Integer -> "integer";
            case Float -> "number";
            case Currency, CurrencyRaw -> {
                double d = value.asDouble() != null ? value.asDouble() : 0.0;
                yield (d == Math.floor(d) && !Double.isInfinite(d)) ? "integer" : "number";
            }
            case Percent, FloatRaw -> "number";
            default -> null;
        };
    }

    private static boolean hasTypedValues(DynValue value) {
        if (value.getType() == DynValue.Type.Object) {
            var entries = value.asObject();
            if (entries != null) {
                for (var entry : entries) if (hasTypedValues(entry.getValue())) return true;
            }
        } else if (value.getType() == DynValue.Type.Array) {
            var items = value.asArray();
            if (items != null) {
                for (var item : items) if (hasTypedValues(item)) return true;
            }
        } else if (value.getType() != DynValue.Type.String) {
            return true;
        }
        return false;
    }

    private static String scalarToString(DynValue value) {
        return switch (value.getType()) {
            case Bool -> (value.asBool() != null && value.asBool()) ? "true" : "false";
            case Integer -> String.valueOf(value.asInt64());
            case Float, Percent -> formatFloat(value.asDouble() != null ? value.asDouble() : 0.0);
            case Currency -> formatFloat(value.asDouble() != null ? value.asDouble() : 0.0);
            case FloatRaw, CurrencyRaw -> value.asString() != null ? value.asString() : "0";
            case String, Reference, Binary, Date, Timestamp, Time, Duration ->
                    value.asString() != null ? value.asString() : "";
            default -> "";
        };
    }

    private static String formatFloat(double n) {
        if (n == Math.floor(n) && !Double.isInfinite(n) && Math.abs(n) < 1e15)
            return String.valueOf((long) n);
        return String.valueOf(n);
    }

    private static String xmlEscape(String s) {
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String pad(int indent, int depth) {
        int count = indent * depth;
        if (count == 0) return "";
        return " ".repeat(count);
    }
}
