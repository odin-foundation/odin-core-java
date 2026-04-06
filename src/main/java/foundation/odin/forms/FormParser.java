package foundation.odin.forms;

import foundation.odin.Odin;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.*;

/**
 * ODIN Forms 1.0 — Form Parser
 *
 * Parses a {@code .odin} forms document into a typed {@link OdinForm} structure.
 * Delegates low-level ODIN parsing to {@link Odin#parse(String)}, then maps the
 * resulting flat path space onto the strongly-typed Forms 1.0 schema.
 *
 * Mirrors the TypeScript parser.ts implementation exactly.
 */
public final class FormParser {

    private FormParser() {
        throw new UnsupportedOperationException("FormParser is a static utility class");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse an ODIN forms document text into a typed {@link OdinForm}.
     *
     * @param text raw ODIN text from a {@code .odin} forms file
     * @return parsed OdinForm with metadata, page defaults, and pages
     * @throws foundation.odin.types.OdinErrors.ParseError if the text is not valid ODIN
     */
    public static OdinForm parseForm(String text) {
        OdinDocument doc = Odin.parse(text);

        FormMetadata metadata    = extractMetadata(doc);
        PageDefaults pageDefaults = extractPageDefaults(doc);
        ScreenSettings screen    = extractScreen(doc);
        OdincodeSettings odincode = extractOdincode(doc);
        Map<String, String> i18n = extractI18n(doc);
        List<FormPage> pages     = extractPages(doc);

        return new OdinForm(metadata, pageDefaults, screen, odincode, i18n, pages);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata and Settings Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private static FormMetadata extractMetadata(OdinDocument doc) {
        String title   = getStringValue(doc, "$.title");
        String id      = getStringValue(doc, "$.id");
        String lang    = getStringValue(doc, "$.lang");
        String version = getStringValue(doc, "$.forms");

        return new FormMetadata(
                title   != null ? title   : "",
                id      != null ? id      : "",
                lang    != null ? lang    : "en",
                version
        );
    }

    private static PageDefaults extractPageDefaults(OdinDocument doc) {
        Double width  = getNumberValue(doc, "$.page.width");
        Double height = getNumberValue(doc, "$.page.height");
        String unit   = getStringValue(doc, "$.page.unit");
        Double margin = getNumberValue(doc, "$.page.margin");

        if (width == null && height == null && unit == null) {
            return null;
        }

        PageUnit resolvedUnit = PageUnit.fromString(unit != null ? unit : "");

        return new PageDefaults(
                width  != null ? width  : 8.5,
                height != null ? height : 11.0,
                resolvedUnit,
                margin != null ? margin : Double.NaN
        );
    }

    private static ScreenSettings extractScreen(OdinDocument doc) {
        Double scale = getNumberValue(doc, "$.screen.scale");
        if (scale == null) return null;
        return new ScreenSettings(scale);
    }

    private static OdincodeSettings extractOdincode(OdinDocument doc) {
        Boolean enabled = getBooleanValue(doc, "$.odincode.enabled");
        String zone     = getStringValue(doc, "$.odincode.zone");
        if (enabled == null && zone == null) return null;

        OdincodeZone resolvedZone = OdincodeZone.fromString(zone != null ? zone : "");
        return new OdincodeSettings(enabled != null ? enabled : false, resolvedZone);
    }

    private static Map<String, String> extractI18n(OdinDocument doc) {
        String prefix = "$.i18n.";
        // metadata paths come from a separate map — iterate all metadata keys via doc.get()
        // The document stores metadata under the "$." prefix; we scan paths from assignments
        // but i18n lives in metadata, so we use a known-prefix scan over the metadata map.
        var metaMap = doc.getMetadata();
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : metaMap) {
            if (entry.getKey().startsWith("i18n.")) {
                String key   = entry.getKey().substring("i18n.".length());
                String value = entry.getValue() != null ? entry.getValue().asString() : null;
                if (key != null && !key.isEmpty() && value != null) {
                    result.put(key, value);
                }
            }
        }
        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page and Element Extraction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Walk all paths to find distinct page indices, then for each page
     * collect elements in document order.
     */
    private static List<FormPage> extractPages(OdinDocument doc) {
        List<String> allPaths = doc.paths();

        // Find all page indices referenced in paths like page[N].*
        Set<Integer> pageIndices = new TreeSet<>(); // TreeSet keeps sorted order
        for (String path : allPaths) {
            int bracketOpen  = path.indexOf('[');
            int bracketClose = path.indexOf(']');
            if (path.startsWith("page[") && bracketOpen == 4 && bracketClose > 5) {
                try {
                    int idx = Integer.parseInt(path.substring(5, bracketClose));
                    pageIndices.add(idx);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (pageIndices.isEmpty()) return List.of();

        List<FormPage> pages = new ArrayList<>();
        for (int index : pageIndices) {
            pages.add(extractPage(doc, index));
        }
        return Collections.unmodifiableList(pages);
    }

    /**
     * Extract a single page by collecting all element keys from paths like
     * {@code page[N].{elementType}.{elementName}.{property}}.
     */
    private static FormPage extractPage(OdinDocument doc, int pageIndex) {
        String prefix = "page[" + pageIndex + "].";
        List<String> allPaths = doc.paths();

        // Collect unique element keys in document order (preserve insertion order).
        // An element key is "{elementType}.{elementName}" e.g. "text.title"
        Set<String> elementKeysSeen = new LinkedHashSet<>();

        for (String path : allPaths) {
            if (!path.startsWith(prefix)) continue;
            String rest = path.substring(prefix.length()); // e.g. "text.title.content"
            String[] parts = rest.split("\\.", -1);
            if (parts.length >= 2) {
                String elementKey = parts[0] + "." + parts[1]; // "text.title"
                elementKeysSeen.add(elementKey);
            }
        }

        int idCounter = 0;
        List<FormElement> elements = new ArrayList<>();
        for (String elementKey : elementKeysSeen) {
            int dotIdx = elementKey.indexOf('.');
            String elementType = elementKey.substring(0, dotIdx);
            String elementName = elementKey.substring(dotIdx + 1);
            String elementPrefix = prefix + elementKey + ".";

            FormElement element = buildElement(doc, elementType, elementName, elementPrefix, idCounter++);
            if (element != null) elements.add(element);
        }

        return new FormPage(Collections.unmodifiableList(elements));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Element Builder Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private static FormElement buildElement(OdinDocument doc,
                                            String elementType,
                                            String elementName,
                                            String prefix,
                                            int idCounter) {
        String id = elementType + "_" + elementName + "_" + idCounter;

        return switch (elementType) {
            case "line"     -> buildLineElement(doc, elementName, id, prefix);
            case "rect"     -> buildRectElement(doc, elementName, id, prefix);
            case "circle"   -> buildCircleElement(doc, elementName, id, prefix);
            case "ellipse"  -> buildEllipseElement(doc, elementName, id, prefix);
            case "polygon"  -> buildPolygonElement(doc, elementName, id, prefix);
            case "polyline" -> buildPolylineElement(doc, elementName, id, prefix);
            case "path"     -> buildPathElement(doc, elementName, id, prefix);
            case "text"     -> buildTextElement(doc, elementName, id, prefix);
            case "img"      -> buildImageElement(doc, elementName, id, prefix);
            case "barcode"  -> buildBarcodeElement(doc, elementName, id, prefix);
            case "field"    -> buildFieldElement(doc, elementName, id, prefix);
            default         -> null;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Geometric Element Builders
    // ─────────────────────────────────────────────────────────────────────────

    private static LineElement buildLineElement(OdinDocument doc, String name, String id, String prefix) {
        return new LineElement(name, id,
                num(doc, prefix + "x1"), num(doc, prefix + "y1"),
                num(doc, prefix + "x2"), num(doc, prefix + "y2"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"));
    }

    private static RectElement buildRectElement(OdinDocument doc, String name, String id, String prefix) {
        return new RectElement(name, id,
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"),
                getNumberValue(doc, prefix + "rx"),
                getNumberValue(doc, prefix + "ry"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"),
                getStringValue(doc, prefix + "fill"),
                getNumberValue(doc, prefix + "fill-opacity"));
    }

    private static CircleElement buildCircleElement(OdinDocument doc, String name, String id, String prefix) {
        return new CircleElement(name, id,
                num(doc, prefix + "cx"), num(doc, prefix + "cy"),
                num(doc, prefix + "r"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"),
                getStringValue(doc, prefix + "fill"),
                getNumberValue(doc, prefix + "fill-opacity"));
    }

    private static EllipseElement buildEllipseElement(OdinDocument doc, String name, String id, String prefix) {
        return new EllipseElement(name, id,
                num(doc, prefix + "cx"), num(doc, prefix + "cy"),
                num(doc, prefix + "rx"), num(doc, prefix + "ry"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"),
                getStringValue(doc, prefix + "fill"),
                getNumberValue(doc, prefix + "fill-opacity"));
    }

    private static PolygonElement buildPolygonElement(OdinDocument doc, String name, String id, String prefix) {
        return new PolygonElement(name, id,
                str(doc, prefix + "points"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"),
                getStringValue(doc, prefix + "fill"),
                getNumberValue(doc, prefix + "fill-opacity"));
    }

    private static PolylineElement buildPolylineElement(OdinDocument doc, String name, String id, String prefix) {
        return new PolylineElement(name, id,
                str(doc, prefix + "points"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"));
    }

    private static PathElement buildPathElement(OdinDocument doc, String name, String id, String prefix) {
        return new PathElement(name, id,
                str(doc, prefix + "d"),
                getStringValue(doc, prefix + "stroke"),
                getNumberValue(doc, prefix + "stroke-width"),
                getNumberValue(doc, prefix + "stroke-opacity"),
                getStringValue(doc, prefix + "stroke-dasharray"),
                getStringValue(doc, prefix + "stroke-linecap"),
                getStringValue(doc, prefix + "stroke-linejoin"),
                getStringValue(doc, prefix + "fill"),
                getNumberValue(doc, prefix + "fill-opacity"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content Element Builders
    // ─────────────────────────────────────────────────────────────────────────

    private static TextElement buildTextElement(OdinDocument doc, String name, String id, String prefix) {
        return new TextElement(name, id,
                str(doc, prefix + "content"),
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                getNumberValue(doc, prefix + "rotate"),
                getStringValue(doc, prefix + "font-family"),
                getNumberValue(doc, prefix + "font-size"),
                getStringValue(doc, prefix + "font-weight"),
                getStringValue(doc, prefix + "font-style"),
                getStringValue(doc, prefix + "text-align"),
                getStringValue(doc, prefix + "color"));
    }

    private static ImageElement buildImageElement(OdinDocument doc, String name, String id, String prefix) {
        return new ImageElement(name, id,
                str(doc, prefix + "src"),
                str(doc, prefix + "alt"),
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"));
    }

    private static BarcodeElement buildBarcodeElement(OdinDocument doc, String name, String id, String prefix) {
        String barcodeTypeStr = getStringValue(doc, prefix + "barcode-type");
        BarcodeType barcodeType = BarcodeType.fromString(barcodeTypeStr != null ? barcodeTypeStr : "");
        return new BarcodeElement(name, id,
                barcodeType,
                str(doc, prefix + "content"),
                str(doc, prefix + "alt"),
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Element Builder
    // ─────────────────────────────────────────────────────────────────────────

    private static FormElement buildFieldElement(OdinDocument doc, String name, String id, String prefix) {
        String fieldType = getStringValue(doc, prefix + "type");
        if (fieldType == null) fieldType = "text";

        BaseFieldParams base = extractBaseField(doc, name, id, prefix);

        return switch (fieldType) {
            case "checkbox"   -> buildCheckboxField(base);
            case "radio"      -> buildRadioField(doc, prefix, base);
            case "select"     -> buildSelectField(doc, prefix, base);
            case "multiselect" -> buildMultiselectField(doc, prefix, base);
            case "date"       -> buildDateField(base);
            case "signature"  -> buildSignatureField(doc, prefix, base);
            default           -> buildTextField(doc, prefix, base); // includes "text" and unknown
        };
    }

    /** Internal value holder for shared field properties. */
    private record BaseFieldParams(
            String name, String id,
            String label, String ariaLabel,
            double x, double y, double w, double h,
            String bind,
            Boolean required, String pattern,
            Integer minLength, Integer maxLength,
            String min, String max,
            Integer tabindex, Boolean readonly
    ) {}

    private static BaseFieldParams extractBaseField(OdinDocument doc, String name, String id, String prefix) {
        Boolean required  = getBooleanValue(doc, prefix + "required");
        Double tabindexD  = getNumberValue(doc, prefix + "tabindex");
        Double minLenD    = getNumberValue(doc, prefix + "minLength");
        Double maxLenD    = getNumberValue(doc, prefix + "maxLength");
        Boolean readonly  = getBooleanValue(doc, prefix + "readonly");

        // min/max can be numeric or a date string
        String min = null, max = null;
        Double minNum = getNumberValue(doc, prefix + "min");
        Double maxNum = getNumberValue(doc, prefix + "max");
        if (minNum != null) min = numToString(minNum);
        else min = getStringValue(doc, prefix + "min");
        if (maxNum != null) max = numToString(maxNum);
        else max = getStringValue(doc, prefix + "max");

        // bind: the TypeScript reads it as a reference value and prepends "@"
        String bind = getReferenceValue(doc, prefix + "bind");
        String bindStr = bind != null ? "@" + bind : "";

        return new BaseFieldParams(
                name, id,
                str(doc, prefix + "label"),
                getStringValue(doc, prefix + "aria-label"),
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"),
                bindStr,
                required, getStringValue(doc, prefix + "pattern"),
                minLenD != null ? (int) Math.round(minLenD) : null,
                maxLenD != null ? (int) Math.round(maxLenD) : null,
                min, max,
                tabindexD != null ? (int) Math.round(tabindexD) : null,
                readonly
        );
    }

    private static TextFieldElement buildTextField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new TextFieldElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                getStringValue(doc, prefix + "mask"),
                getStringValue(doc, prefix + "placeholder"),
                getBooleanValue(doc, prefix + "multiline"),
                toIntOrNull(getNumberValue(doc, prefix + "maxLines")));
    }

    private static CheckboxElement buildCheckboxField(BaseFieldParams b) {
        return new CheckboxElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly());
    }

    private static RadioElement buildRadioField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new RadioElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                str(doc, prefix + "group"),
                str(doc, prefix + "value"));
    }

    private static SelectElement buildSelectField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new SelectElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                extractOptions(doc, prefix),
                getStringValue(doc, prefix + "placeholder"));
    }

    private static MultiselectElement buildMultiselectField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new MultiselectElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                extractOptions(doc, prefix),
                toIntOrNull(getNumberValue(doc, prefix + "minSelect")),
                toIntOrNull(getNumberValue(doc, prefix + "maxSelect")));
    }

    private static DateElement buildDateField(BaseFieldParams b) {
        return new DateElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly());
    }

    private static SignatureElement buildSignatureField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new SignatureElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                getStringValue(doc, prefix + "date_field"));
    }

    /**
     * Extract an {@code options} array from indexed paths like
     * {@code prefix + "options[0]"}, {@code "options[1]"}, ...
     */
    private static List<String> extractOptions(OdinDocument doc, String prefix) {
        List<String> options = new ArrayList<>();
        int i = 0;
        while (doc.has(prefix + "options[" + i + "]")) {
            String val = getStringValue(doc, prefix + "options[" + i + "]");
            if (val != null) options.add(val);
            i++;
        }
        return options;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Value Accessors
    // ─────────────────────────────────────────────────────────────────────────

    private static String getStringValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return val.isString() ? val.asString() : null;
    }

    private static Double getNumberValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        if (val.isNumeric()) return val.asDouble();
        return null;
    }

    private static Boolean getBooleanValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return val.isBoolean() ? val.asBool() : null;
    }

    private static String getReferenceValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return val.isReference() ? val.asReference() : null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Get number value defaulting to 0.0 (matches TypeScript's {@code ?? 0} pattern). */
    private static double num(OdinDocument doc, String path) {
        Double d = getNumberValue(doc, path);
        return d != null ? d : 0.0;
    }

    /** Get string value defaulting to empty string (matches TypeScript's {@code ?? ''} pattern). */
    private static String str(OdinDocument doc, String path) {
        String s = getStringValue(doc, path);
        return s != null ? s : "";
    }

    private static Integer toIntOrNull(Double d) {
        return d != null ? (int) Math.round(d) : null;
    }

    private static String numToString(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
