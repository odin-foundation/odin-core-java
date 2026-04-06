package foundation.odin.transform;

import foundation.odin.types.DynValue;

import javax.xml.stream.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class XmlSourceParser {

    private XmlSourceParser() {}

    public static DynValue parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("XML input is null or empty.");
        }

        XNode root;
        try {
            root = readXml(input);
        } catch (XMLStreamException ex) {
            throw new FormatException("Invalid XML: " + ex.getMessage(), ex);
        }

        if (root == null) throw new FormatException("Empty XML document.");

        var rootValue = nodeToValue(root, 0);
        return DynValue.ofObject(List.of(Map.entry(root.name, rootValue)));
    }

    // ─── Internal node ───────────────────────────────────────────────

    private static final class XNode {
        String name = "";
        List<Map.Entry<String, String>> attributes = new ArrayList<>();
        List<XNode> children = new ArrayList<>();
        String text = "";
        boolean selfClosing;
    }

    // ─── XML Reading ─────────────────────────────────────────────────

    private static XNode readXml(String input) throws XMLStreamException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var reader = factory.createXMLStreamReader(new StringReader(input));

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                return readElement(reader, input);
            }
        }

        throw new FormatException("No root element found.");
    }

    /**
     * Detect if the element at the reader's current position is self-closing (&lt;tag/&gt;)
     * by checking the raw XML input. StAX doesn't provide isEmptyElement like .NET XmlReader.
     */
    private static boolean detectSelfClosing(XMLStreamReader reader, String input) {
        int offset = reader.getLocation().getCharacterOffset();
        // Scan backwards from offset to find the '>' that ends this opening tag
        for (int i = Math.min(offset, input.length()) - 1; i >= 0; i--) {
            char c = input.charAt(i);
            if (c == '>') {
                return i > 0 && input.charAt(i - 1) == '/';
            }
            if (c == '<') return false;
        }
        return false;
    }

    private static XNode readElement(XMLStreamReader reader, String input) throws XMLStreamException {
        var node = new XNode();
        node.name = reader.getLocalName();
        if (reader.getPrefix() != null && !reader.getPrefix().isEmpty()) {
            node.name = reader.getPrefix() + ":" + reader.getLocalName();
        }

        // Detect self-closing via raw input before reading attributes/children
        boolean isSelfClosing = detectSelfClosing(reader, input);

        // Read attributes
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String prefix = reader.getAttributePrefix(i);
            String localName = reader.getAttributeLocalName(i);
            // Skip xmlns declarations
            if ("xmlns".equals(prefix) || ("".equals(prefix) && "xmlns".equals(localName))) continue;
            node.attributes.add(Map.entry(localName, reader.getAttributeValue(i)));
        }

        var textParts = new ArrayList<String>();

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    node.children.add(readElement(reader, input));
                    break;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    textParts.add(reader.getText());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (!textParts.isEmpty()) {
                        node.text = String.join("", textParts).trim();
                    }
                    if (isSelfClosing && textParts.isEmpty() && node.children.isEmpty()) {
                        node.selfClosing = true;
                    }
                    return node;
            }
        }

        if (!textParts.isEmpty()) {
            node.text = String.join("", textParts).trim();
        }
        return node;
    }

    // ─── Value conversion ────────────────────────────────────────────

    private static DynValue nodeToValue(XNode node, int depth) {
        if (depth > 100) throw new FormatException("XML nesting depth limit (100) exceeded.");

        // Check for xsi:nil="true"
        for (var attr : node.attributes) {
            if ("nil".equals(attr.getKey()) || "nillable".equals(attr.getKey())) {
                if ("true".equals(attr.getValue()) || "1".equals(attr.getValue())) {
                    return DynValue.ofNull();
                }
            }
        }

        // Filter out nil/nillable from attributes
        var attrs = new ArrayList<Map.Entry<String, String>>();
        for (var attr : node.attributes) {
            if (!"nil".equals(attr.getKey()) && !"nillable".equals(attr.getKey())) {
                attrs.add(attr);
            }
        }

        boolean hasAttrs = !attrs.isEmpty();
        boolean hasChildren = !node.children.isEmpty();
        boolean hasText = !node.text.isEmpty();

        // Leaf element with no attributes
        if (!hasAttrs && !hasChildren) {
            // Self-closing <tag/> → null; empty <tag></tag> → empty string
            if (node.selfClosing)
                return DynValue.ofNull();
            return DynValue.ofString(node.text);
        }

        // Build object
        var entries = new ArrayList<Map.Entry<String, DynValue>>();

        // Attributes first (prefixed with @)
        for (var attr : attrs) {
            entries.add(Map.entry("@" + attr.getKey(), DynValue.ofString(attr.getValue())));
        }

        // Text content
        if (hasText && hasChildren) {
            entries.add(Map.entry("_text", DynValue.ofString(node.text)));
        } else if (hasText && hasAttrs) {
            entries.add(Map.entry("_text", DynValue.ofString(node.text)));
        }

        // Child elements — group repeated names into arrays
        if (hasChildren) {
            var childGroups = new ArrayList<Map.Entry<String, List<DynValue>>>();
            var seen = new ArrayList<String>();

            for (var child : node.children) {
                DynValue childValue = nodeToValue(child, depth + 1);
                int idx = seen.indexOf(child.name);
                if (idx >= 0) {
                    childGroups.get(idx).getValue().add(childValue);
                } else {
                    seen.add(child.name);
                    var list = new ArrayList<DynValue>();
                    list.add(childValue);
                    childGroups.add(Map.entry(child.name, list));
                }
            }

            for (var group : childGroups) {
                if (group.getValue().size() == 1) {
                    entries.add(Map.entry(group.getKey(), group.getValue().get(0)));
                } else {
                    entries.add(Map.entry(group.getKey(), DynValue.ofArray(group.getValue())));
                }
            }
        }

        if (entries.isEmpty()) return DynValue.ofNull();
        return DynValue.ofObject(entries);
    }
}
