package foundation.odin.serialization;

import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.StringifyOptions;

import java.util.*;
import java.util.Base64;

public final class Stringify {
    private Stringify() {}

    public static String serialize(OdinDocument doc) {
        return serialize(doc, StringifyOptions.DEFAULT);
    }

    public static String serialize(OdinDocument doc, StringifyOptions options) {
        var opts = options != null ? options : StringifyOptions.DEFAULT;
        var sb = new StringBuilder();

        // Metadata section
        if (opts.isIncludeMetadata() && doc.getMetadata().size() > 0) {
            sb.append("{$}\n");
            for (var entry : doc.getMetadata()) {
                sb.append(entry.getKey());
                sb.append(" = ");
                writeValue(sb, entry.getValue());
                sb.append('\n');
            }
            sb.append('\n');
        }

        // Collect entries, optionally sorting
        var entries = new ArrayList<>(doc.getAssignments().entries());
        if (!opts.isPreserveOrder()) {
            entries.sort(Comparator.comparing(Map.Entry::getKey));
        }

        // Group by header sections
        String currentSection = null;
        boolean currentSectionSet = false;

        for (var entry : entries) {
            var path = entry.getKey();
            var value = entry.getValue();

            // Skip metadata duplicate entries ($.xxx)
            if (path.startsWith("$.")) continue;

            // Determine section from path
            String[] sectionField = new String[2];
            splitPath(path, sectionField);
            String section = sectionField[0];
            String field = sectionField[1];

            boolean sectionChanged = !Objects.equals(section, currentSection);
            if (!currentSectionSet) {
                sectionChanged = true;
            }

            if (!currentSectionSet || sectionChanged) {
                if (section != null) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                        sb.append('\n');
                    }
                    sb.append('{');
                    sb.append(section);
                    sb.append("}\n");
                }
                currentSection = section;
                currentSectionSet = true;
            }

            // Write field assignment
            sb.append(field);
            sb.append(" = ");

            // Write modifiers before the value
            if (value.getModifiers() != null) {
                writeModifiers(sb, value.getModifiers());
            }

            writeValue(sb, value);
            sb.append('\n');
        }

        return sb.toString();
    }

    static void splitPath(String path, String[] result) {
        int dotPos = path.indexOf('.');
        if (dotPos >= 0) {
            String candidate = path.substring(0, dotPos);
            if (!candidate.isEmpty() && Character.isUpperCase(candidate.charAt(0))) {
                result[0] = candidate;
                result[1] = path.substring(dotPos + 1);
                return;
            }
        }
        result[0] = null;
        result[1] = path;
    }

    static void writeModifiers(StringBuilder sb, OdinModifiers mods) {
        if (mods.isRequired()) sb.append('!');
        if (mods.isConfidential()) sb.append('*');
        if (mods.isDeprecated()) sb.append('-');
    }

    static void writeValue(StringBuilder sb, OdinValue value) {
        switch (value) {
            case OdinValue.OdinNull n -> sb.append('~');

            case OdinValue.OdinBoolean b -> sb.append(b.getValue() ? "true" : "false");

            case OdinValue.OdinString s -> {
                sb.append('"');
                writeEscapedString(sb, s.getValue());
                sb.append('"');
            }

            case OdinValue.OdinInteger i -> {
                sb.append("##");
                sb.append(i.getRaw() != null ? i.getRaw() : Long.toString(i.getValue()));
            }

            case OdinValue.OdinNumber n -> {
                sb.append('#');
                if (n.getRaw() != null) {
                    sb.append(n.getRaw());
                } else if (n.getDecimalPlaces() != null) {
                    sb.append(String.format("%." + n.getDecimalPlaces() + "f", n.getValue()));
                } else {
                    sb.append(n.getValue());
                }
            }

            case OdinValue.OdinCurrency c -> {
                sb.append("#$");
                if (c.getRaw() != null) {
                    int colonPos = c.getRaw().indexOf(':');
                    if (colonPos >= 0) {
                        sb.append(c.getRaw().substring(0, colonPos));
                        sb.append(':');
                        sb.append(c.getRaw().substring(colonPos + 1).toUpperCase());
                    } else {
                        sb.append(c.getRaw());
                        if (c.getCurrencyCode() != null) {
                            sb.append(':');
                            sb.append(c.getCurrencyCode());
                        }
                    }
                } else {
                    sb.append(String.format("%." + c.getDecimalPlaces() + "f", c.getValue()));
                    if (c.getCurrencyCode() != null) {
                        sb.append(':');
                        sb.append(c.getCurrencyCode());
                    }
                }
            }

            case OdinValue.OdinPercent p -> {
                sb.append("#%");
                sb.append(p.getRaw() != null ? p.getRaw() : Double.toString(p.getValue()));
            }

            case OdinValue.OdinDate d -> sb.append(d.getRaw());

            case OdinValue.OdinTimestamp ts -> sb.append(ts.getRaw());

            case OdinValue.OdinTime t -> sb.append(t.getValue());

            case OdinValue.OdinDuration dur -> sb.append(dur.getValue());

            case OdinValue.OdinReference r -> {
                sb.append('@');
                sb.append(r.getPath());
            }

            case OdinValue.OdinBinary bin -> {
                sb.append('^');
                if (bin.getAlgorithm() != null) {
                    sb.append(bin.getAlgorithm());
                    sb.append(':');
                }
                sb.append(Base64.getEncoder().encodeToString(bin.getData()));
            }

            case OdinValue.OdinVerb v -> {
                sb.append('%');
                if (v.isCustom()) sb.append('&');
                sb.append(v.getName());
                for (var arg : v.getArgs()) {
                    sb.append(' ');
                    writeValue(sb, arg);
                }
            }

            case OdinValue.OdinArray arr -> {
                for (int idx = 0; idx < arr.getItems().size(); idx++) {
                    if (idx > 0) sb.append(", ");
                    var item = arr.getItems().get(idx);
                    var itemValue = item.asValue();
                    if (itemValue != null) {
                        writeValue(sb, itemValue);
                    } else {
                        sb.append("{...}");
                    }
                }
            }

            case OdinValue.OdinObject obj -> sb.append("{...}");

            default -> sb.append(value.toString());
        }

        // Write trailing directives
        for (var directive : value.getDirectives()) {
            writeDirective(sb, directive);
        }
    }

    static void writeDirective(StringBuilder sb, OdinDirective directive) {
        sb.append(" :");
        sb.append(directive.getName());
        if (directive.getValue() != null) {
            sb.append(' ');
            var strVal = directive.getValue().asString();
            if (strVal != null) {
                sb.append(strVal);
            } else {
                var numVal = directive.getValue().asNumber();
                if (numVal != null) {
                    sb.append(numVal);
                }
            }
        }
    }

    static void writeEscapedString(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (Character.isISOControl(ch)) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
    }
}
