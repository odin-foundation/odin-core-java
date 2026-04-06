package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.TargetConfig;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FlatFormatter {

    private FlatFormatter() {}

    public static String format(DynValue value, TargetConfig config) {
        if (value == null) return "";

        String style = "kvp";
        if (config != null) {
            var s = config.getOptions().get("style");
            if (s != null && !s.isEmpty()) style = s;
        }

        return switch (style.toLowerCase()) {
            case "yaml" -> formatYaml(value);
            default -> formatKvp(value);
        };
    }

    // ── KVP style ──

    private static String formatKvp(DynValue value) {
        var pairs = new ArrayList<Map.Entry<String, String>>();
        collectPairs(pairs, value, "");
        pairs.sort(Comparator.comparing(Map.Entry::getKey));

        var sb = new StringBuilder();
        for (var pair : pairs) {
            sb.append(pair.getKey()).append('=').append(pair.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static void collectPairs(List<Map.Entry<String, String>> pairs, DynValue value, String prefix) {
        switch (value.getType()) {
            case Null -> {} // skip nulls
            case Bool -> pairs.add(new AbstractMap.SimpleEntry<>(prefix, value.asBool() ? "true" : "false"));
            case Integer -> pairs.add(new AbstractMap.SimpleEntry<>(prefix, String.valueOf(value.asInt64())));
            case Float, Currency, Percent -> {
                double d = value.asDouble() != null ? value.asDouble() : 0.0;
                String formatted;
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                    formatted = String.valueOf((long) d);
                else
                    formatted = String.valueOf(d);
                pairs.add(new AbstractMap.SimpleEntry<>(prefix, formatted));
            }
            case FloatRaw, CurrencyRaw, String, Reference, Binary, Date, Timestamp, Time, Duration ->
                    pairs.add(new AbstractMap.SimpleEntry<>(prefix, value.asString() != null ? value.asString() : ""));
            case Array -> {
                var items = value.asArray();
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        collectPairs(pairs, items.get(i), prefix + "[" + i + "]");
                    }
                }
            }
            case Object -> {
                var entries = value.asObject();
                if (entries != null) {
                    for (var entry : entries) {
                        String childPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                        collectPairs(pairs, entry.getValue(), childPrefix);
                    }
                }
            }
        }
    }

    // ── YAML style ──

    private static class YamlNode {
        String value;
        List<Map.Entry<String, YamlNode>> children = new ArrayList<>();
        boolean isArrayItem;
    }

    private record PathSegment(String name, boolean isArrayIndex) {}

    private static String formatYaml(DynValue value) {
        var pairs = new ArrayList<Map.Entry<String, String>>();
        collectPairs(pairs, value, "");
        pairs.sort(Comparator.comparing(Map.Entry::getKey));

        var root = new YamlNode();

        for (var pair : pairs) {
            String path = pair.getKey();
            String val = pair.getValue();

            var segments = parsePathSegments(path);

            var node = root;
            for (int i = 0; i < segments.size() - 1; i++) {
                var seg = segments.get(i);
                int idx = findChild(node.children, seg.name);
                if (idx < 0) {
                    var child = new YamlNode();
                    child.isArrayItem = seg.isArrayIndex;
                    node.children.add(new AbstractMap.SimpleEntry<>(seg.name, child));
                    node = child;
                } else {
                    node = node.children.get(idx).getValue();
                }
            }

            var lastSeg = segments.get(segments.size() - 1);
            int leafIdx = findChild(node.children, lastSeg.name);
            if (leafIdx < 0) {
                var leaf = new YamlNode();
                leaf.isArrayItem = lastSeg.isArrayIndex;
                leaf.value = val;
                node.children.add(new AbstractMap.SimpleEntry<>(lastSeg.name, leaf));
            } else {
                node.children.get(leafIdx).getValue().value = val;
            }
        }

        var lines = new ArrayList<String>();
        renderYamlNode(lines, root, 0, false);
        return String.join("\n", lines);
    }

    private static List<PathSegment> parsePathSegments(String path) {
        var segments = new ArrayList<PathSegment>();
        var current = new StringBuilder();
        int i = 0;

        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                if (current.length() > 0) {
                    segments.add(new PathSegment(current.toString(), false));
                    current.setLength(0);
                }
                i++;
            } else if (c == '[') {
                if (current.length() > 0) {
                    segments.add(new PathSegment(current.toString(), false));
                    current.setLength(0);
                }
                i++;
                var indexStr = new StringBuilder();
                while (i < path.length() && path.charAt(i) != ']') {
                    indexStr.append(path.charAt(i));
                    i++;
                }
                segments.add(new PathSegment(indexStr.toString(), true));
                i++; // skip ']'
            } else {
                current.append(c);
                i++;
            }
        }

        if (current.length() > 0)
            segments.add(new PathSegment(current.toString(), false));

        return segments;
    }

    private static int findChild(List<Map.Entry<String, YamlNode>> children, String name) {
        for (int i = 0; i < children.size(); i++)
            if (children.get(i).getKey().equals(name)) return i;
        return -1;
    }

    private static void renderYamlNode(List<String> lines, YamlNode node, int indent, boolean isArrayContext) {
        String pad = " ".repeat(indent * 2);

        var sorted = new ArrayList<>(node.children);
        sorted.sort((a, b) -> {
            boolean aNum = isNumeric(a.getKey());
            boolean bNum = isNumeric(b.getKey());
            if (aNum && bNum) return Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey()));
            return a.getKey().compareTo(b.getKey());
        });

        for (var entry : sorted) {
            String key = entry.getKey();
            var child = entry.getValue();
            boolean isArrayItem = child.isArrayItem;

            if (child.value != null && child.children.isEmpty()) {
                if (isArrayItem && isArrayContext)
                    lines.add(pad + "- " + key + ": " + yamlQuote(child.value));
                else if (isArrayItem)
                    lines.add(pad + "  " + key + ": " + yamlQuote(child.value));
                else
                    lines.add(pad + key + ": " + yamlQuote(child.value));
            } else if (!child.children.isEmpty()) {
                boolean childrenAreArrayItems = !child.children.isEmpty() &&
                        isNumeric(child.children.get(0).getKey());

                if (isArrayItem) {
                    var childEntries = new ArrayList<>(child.children);
                    childEntries.sort((a, b) -> {
                        boolean aNum = isNumeric(a.getKey());
                        boolean bNum = isNumeric(b.getKey());
                        if (aNum && bNum)
                            return Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey()));
                        return a.getKey().compareTo(b.getKey());
                    });

                    boolean first = true;
                    for (var ce : childEntries) {
                        String childKey = ce.getKey();
                        var childNode = ce.getValue();

                        if (first) {
                            if (childNode.value != null)
                                lines.add(pad + "- " + childKey + ": " + yamlQuote(childNode.value));
                            else {
                                lines.add(pad + "- " + childKey + ":");
                                renderYamlNode(lines, childNode, indent + 2, false);
                            }
                            first = false;
                        } else {
                            if (childNode.value != null)
                                lines.add(pad + "  " + childKey + ": " + yamlQuote(childNode.value));
                            else {
                                lines.add(pad + "  " + childKey + ":");
                                renderYamlNode(lines, childNode, indent + 2, false);
                            }
                        }
                    }
                } else {
                    lines.add(pad + key + ":");
                    if (childrenAreArrayItems)
                        renderYamlNode(lines, child, indent + 1, true);
                    else
                        renderYamlNode(lines, child, indent + 1, false);
                }
            }
        }
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    private static String yamlQuote(String value) {
        if (value.isEmpty() ||
                "true".equals(value) || "false".equals(value) ||
                "null".equals(value) ||
                "yes".equals(value) || "no".equals(value) ||
                value.contains(":") || value.contains("#") ||
                value.contains("\n") ||
                value.charAt(0) == ' ' ||
                value.charAt(value.length() - 1) == ' ' ||
                value.charAt(0) == '"' ||
                value.charAt(0) == '\'') {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
        return value;
    }
}
