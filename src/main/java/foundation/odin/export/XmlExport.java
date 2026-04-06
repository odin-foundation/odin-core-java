package foundation.odin.export;

import foundation.odin.types.OdinArrayItem;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinValue;

import java.util.*;

public final class XmlExport {

    private XmlExport() {}

    public static String toXml(OdinDocument doc) {
        return toXml(doc, false, false, "root");
    }

    public static String toXml(OdinDocument doc, boolean preserveTypes, boolean preserveModifiers) {
        return toXml(doc, preserveTypes, preserveModifiers, "root");
    }

    public static String toXml(OdinDocument doc, boolean preserveTypes, boolean preserveModifiers, String rootElement) {
        if (preserveModifiers) preserveTypes = true;

        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append('<').append(rootElement);
        if (preserveTypes || preserveModifiers)
            sb.append(" xmlns:odin=\"https://odin.foundation/ns\"");
        sb.append(">\n");

        var sections = new LinkedHashMap<String, List<Map.Entry<String, OdinValue>>>();
        var sectionOrder = new ArrayList<String>();

        // Build path-to-full-key map for modifier lookups
        var pathToFullKey = new LinkedHashMap<String, String>();
        for (var entry : doc.getAssignments()) {
            if (entry.getKey().startsWith("$")) continue;

            int dotIndex = entry.getKey().indexOf('.');
            if (dotIndex > 0) {
                String section = entry.getKey().substring(0, dotIndex);
                String field = entry.getKey().substring(dotIndex + 1);
                sections.computeIfAbsent(section, k -> {
                    sectionOrder.add(k);
                    return new ArrayList<>();
                });
                sections.get(section).add(Map.entry(field, entry.getValue()));
                pathToFullKey.put(section + "." + field, entry.getKey());
            } else {
                writeElement(sb, entry.getKey(), entry.getValue(), "  ", preserveTypes, preserveModifiers, doc, entry.getKey());
            }
        }

        for (String sectionName : sectionOrder) {
            sb.append("  <").append(sectionName).append(">\n");
            for (var field : sections.get(sectionName)) {
                String fullKey = sectionName + "." + field.getKey();
                writeElement(sb, field.getKey(), field.getValue(), "    ", preserveTypes, preserveModifiers, doc, fullKey);
            }
            sb.append("  </").append(sectionName).append(">\n");
        }

        sb.append("</").append(rootElement).append(">\n");
        return sb.toString();
    }

    private static void writeElement(StringBuilder sb, String name, OdinValue value, String indent,
            boolean preserveTypes, boolean preserveModifiers, OdinDocument doc, String fullPath) {
        if (value instanceof OdinValue.OdinNull) return;

        String safeName = escapeXmlName(name);
        sb.append(indent).append('<').append(safeName);

        if (preserveTypes && !(value instanceof OdinValue.OdinString) && !(value instanceof OdinValue.OdinNull)) {
            String typeName = getOdinTypeName(value);
            if (typeName != null)
                sb.append(" odin:type=\"").append(typeName).append('"');
            if (value instanceof OdinValue.OdinCurrency c && c.getCurrencyCode() != null)
                sb.append(" odin:currencyCode=\"").append(c.getCurrencyCode()).append('"');
        }

        if (preserveModifiers) {
            // Check value-level modifiers first, then document pathModifiers
            OdinModifiers mods = value.getModifiers();
            if ((mods == null || !mods.hasAny()) && fullPath != null) {
                var docMods = doc.getPathModifiers();
                if (docMods != null) {
                    var docMod = docMods.tryGet(fullPath);
                    if (docMod != null) mods = docMod;
                }
            }
            if (mods != null && mods.hasAny()) {
                if (mods.isRequired()) sb.append(" odin:required=\"true\"");
                if (mods.isConfidential()) sb.append(" odin:confidential=\"true\"");
                if (mods.isDeprecated()) sb.append(" odin:deprecated=\"true\"");
            }
        }

        switch (value) {
            case OdinValue.OdinArray arr -> {
                sb.append(">\n");
                for (var item : arr.getItems()) {
                    if (item instanceof OdinArrayItem.OdinArrayValue av) {
                        writeElement(sb, "item", av.getValue(), indent + "  ", preserveTypes, preserveModifiers, doc, null);
                    } else if (item instanceof OdinArrayItem.OdinArrayRecord rec) {
                        sb.append(indent).append("  <item>\n");
                        for (var f : rec.getFields())
                            writeElement(sb, f.getKey(), f.getValue(), indent + "    ", preserveTypes, preserveModifiers, doc, null);
                        sb.append(indent).append("  </item>\n");
                    }
                }
                sb.append(indent).append("</").append(safeName).append(">\n");
            }
            case OdinValue.OdinObject obj -> {
                sb.append(">\n");
                for (var f : obj.getFields())
                    writeElement(sb, f.getKey(), f.getValue(), indent + "  ", preserveTypes, preserveModifiers, doc, null);
                sb.append(indent).append("</").append(safeName).append(">\n");
            }
            default -> {
                sb.append('>');
                sb.append(escapeXmlContent(formatValueText(value)));
                sb.append("</").append(safeName).append(">\n");
            }
        }
    }

    private static String getOdinTypeName(OdinValue value) {
        return switch (value) {
            case OdinValue.OdinInteger i -> "integer";
            case OdinValue.OdinNumber n -> "number";
            case OdinValue.OdinCurrency c -> "currency";
            case OdinValue.OdinPercent p -> "percent";
            case OdinValue.OdinBoolean b -> "boolean";
            case OdinValue.OdinDate d -> "date";
            case OdinValue.OdinTimestamp ts -> "timestamp";
            case OdinValue.OdinTime t -> "time";
            case OdinValue.OdinDuration d -> "duration";
            case OdinValue.OdinReference r -> "reference";
            case OdinValue.OdinBinary b -> "binary";
            default -> null;
        };
    }

    private static String formatValueText(OdinValue value) {
        return switch (value) {
            case OdinValue.OdinBoolean b -> b.getValue() ? "true" : "false";
            case OdinValue.OdinString s -> s.getValue();
            case OdinValue.OdinInteger i -> i.getRaw() != null ? i.getRaw() : Long.toString(i.getValue());
            case OdinValue.OdinNumber n -> n.getRaw() != null ? n.getRaw() : formatNumber(n.getValue());
            case OdinValue.OdinCurrency c -> formatCurrencyText(c);
            case OdinValue.OdinPercent p -> p.getRaw() != null ? p.getRaw() : formatNumber(p.getValue());
            case OdinValue.OdinDate d -> d.getRaw();
            case OdinValue.OdinTimestamp ts -> ts.getRaw();
            case OdinValue.OdinTime t -> t.getValue();
            case OdinValue.OdinDuration d -> d.getValue();
            case OdinValue.OdinReference r -> "@" + r.getPath();
            case OdinValue.OdinBinary b -> {
                String b64 = Base64.getEncoder().encodeToString(b.getData());
                yield b.getAlgorithm() != null ? "^" + b.getAlgorithm() + ":" + b64 : "^" + b64;
            }
            default -> value.toString();
        };
    }

    private static String formatCurrencyText(OdinValue.OdinCurrency c) {
        String raw = c.getRaw() != null ? c.getRaw() : formatNumber(c.getValue());
        int colonIdx = raw.indexOf(':');
        if (colonIdx >= 0) raw = raw.substring(0, colonIdx);
        return raw;
    }

    private static String formatNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
            return Long.toString((long) d);
        return Double.toString(d);
    }

    private static String escapeXmlContent(String text) {
        var sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
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

    private static String escapeXmlName(String name) {
        if (!name.isEmpty() && Character.isDigit(name.charAt(0)))
            return "_" + name;
        return name.replace(' ', '_').replace('[', '_').replace(']', '_');
    }
}
