package foundation.odin.forms;

import foundation.odin.Odin;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ODIN Forms 1.0 — Form Parser
 *
 * Parses a {@code .odin} forms document into a typed {@link OdinForm} structure.
 * Delegates low-level ODIN parsing to {@link Odin#parse(String)}, then maps the
 * resulting flat path space onto the strongly-typed Forms 1.0 schema.
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
     * @return parsed OdinForm with metadata, page defaults, pages, and templates
     * @throws foundation.odin.types.OdinErrors.OdinParseException if the text is not valid ODIN
     */
    public static OdinForm parseForm(String text) {
        // {@tpl_*} template headers are not valid core ODIN sections, so split them
        // out before parsing and parse each template body separately.
        Split split = splitTemplates(text);

        OdinDocument doc = Odin.parse(split.body);

        FormMetadata metadata     = extractMetadata(doc);
        PageDefaults pageDefaults = extractPageDefaults(doc);
        ScreenSettings screen     = extractScreen(doc);
        OdincodeSettings odincode = extractOdincode(doc);
        Map<String, String> i18n  = extractI18n(doc);
        List<FormPage> pages      = extractPages(doc, i18n);
        Map<String, PageTemplate> templates = extractTemplates(split.templateBlocks, i18n);

        return new OdinForm(metadata, pageDefaults, screen, odincode, i18n, pages, templates);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page Template Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private record TemplateBlock(String name, String text) {}

    private static final class Split {
        final String body;
        final List<TemplateBlock> templateBlocks;
        Split(String body, List<TemplateBlock> templateBlocks) {
            this.body = body;
            this.templateBlocks = templateBlocks;
        }
    }

    private static final Pattern TPL_HEADER =
            Pattern.compile("^\\s*\\{\\s*@(tpl_[A-Za-z0-9_]+)\\s*\\}\\s*$");
    private static final Pattern TOP_LEVEL_HEADER =
            Pattern.compile("^\\s*\\{\\s*(\\$|page\\[\\d+\\]|@tpl_)");

    /**
     * Split a forms document into its core-parseable body and the raw text of
     * each {@code {@tpl_*}} block. A template block runs from its header line
     * until the next top-level section.
     */
    private static Split splitTemplates(String text) {
        String[] lines = text.split("\r?\n", -1);
        StringBuilder bodyLines = new StringBuilder();
        List<TemplateBlock> blocks = new ArrayList<>();

        String currentName = null;
        StringBuilder currentText = null;

        for (String line : lines) {
            Matcher tplMatch = TPL_HEADER.matcher(line);
            if (tplMatch.matches()) {
                if (currentName != null) {
                    blocks.add(new TemplateBlock(currentName, currentText.toString()));
                }
                currentName = tplMatch.group(1);
                currentText = new StringBuilder();
                continue;
            }
            if (currentName != null) {
                if (TOP_LEVEL_HEADER.matcher(line).find() && !TPL_HEADER.matcher(line).matches()) {
                    blocks.add(new TemplateBlock(currentName, currentText.toString()));
                    currentName = null;
                    currentText = null;
                    bodyLines.append(line).append('\n');
                } else {
                    currentText.append(line).append('\n');
                }
                continue;
            }
            bodyLines.append(line).append('\n');
        }
        if (currentName != null) {
            blocks.add(new TemplateBlock(currentName, currentText.toString()));
        }

        return new Split(reanchor(bodyLines.toString(), null), blocks);
    }

    private static final Pattern ANCHOR_HEADER =
            Pattern.compile("^\\s*\\{\\s*(page\\[\\d+\\]|tpl\\.[A-Za-z0-9_]+)\\s*\\}\\s*$");
    private static final Pattern RELATIVE_HEADER =
            Pattern.compile("^\\s*\\{\\s*\\.");
    private static final Pattern RELATIVE_TABULAR =
            Pattern.compile("^\\s*\\{\\s*\\.[^}]*\\[\\]\\s*:");

    /**
     * A relative tabular header ({@code {.x[] : ...}}) leaves the core parser's
     * parent context pointing at the field, so a following relative header would
     * nest under it. Re-emit the active top-level anchor after each such block so
     * siblings resolve correctly.
     */
    private static String reanchor(String text, String rootAnchor) {
        String[] lines = text.split("\r?\n", -1);
        StringBuilder out = new StringBuilder();

        String anchor = rootAnchor;
        boolean needsReanchor = false;

        for (String line : lines) {
            Matcher anchorMatch = ANCHOR_HEADER.matcher(line);
            if (anchorMatch.matches()) {
                anchor = "{" + anchorMatch.group(1) + "}";
                needsReanchor = false;
                out.append(line).append('\n');
                continue;
            }

            if (RELATIVE_HEADER.matcher(line).find()) {
                if (needsReanchor && anchor != null) {
                    out.append(anchor).append('\n');
                    needsReanchor = false;
                }
                if (RELATIVE_TABULAR.matcher(line).find()) {
                    needsReanchor = true;
                }
                out.append(line).append('\n');
                continue;
            }

            out.append(line).append('\n');
        }

        return out.toString();
    }

    /**
     * Parse each template block body into a {@link PageTemplate}. The body is
     * reparented under a synthetic {@code {tpl.<name>}} section so it parses as
     * ordinary ODIN.
     */
    private static Map<String, PageTemplate> extractTemplates(
            List<TemplateBlock> blocks, Map<String, String> i18n) {
        if (blocks.isEmpty()) return null;

        Map<String, PageTemplate> templates = new LinkedHashMap<>();
        for (TemplateBlock block : blocks) {
            String root = "tpl." + block.name();
            String synthetic = reanchor("{" + root + "}\n" + block.text(), "{" + root + "}");
            OdinDocument doc = Odin.parse(synthetic);

            String prefix = root + ".";
            Boolean pageTemplate = getBooleanValue(doc, prefix + "page-template");
            String continues = getStringValue(doc, prefix + "continues");
            String formId = getStringValue(doc, prefix + "form-id");
            List<FormElement> elements = extractElements(doc, prefix, i18n);

            templates.put(block.name(), new PageTemplate(
                    block.name(),
                    pageTemplate != null ? pageTemplate : true,
                    continues, formId, elements));
        }
        return Collections.unmodifiableMap(templates);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata and Settings Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private static FormMetadata extractMetadata(OdinDocument doc) {
        String title   = metaString(doc, "title");
        String id      = metaString(doc, "id");
        String lang    = metaString(doc, "lang");
        String version = metaString(doc, "forms");

        return new FormMetadata(
                title   != null ? title   : "",
                id      != null ? id      : "",
                lang    != null ? lang    : "en",
                version
        );
    }

    private static PageDefaults extractPageDefaults(OdinDocument doc) {
        Double width  = metaNumber(doc, "page.width");
        Double height = metaNumber(doc, "page.height");
        String unit   = metaString(doc, "page.unit");
        PageMargins margin = extractMargins(doc);

        if (width == null && height == null && unit == null) {
            return null;
        }

        PageUnit resolvedUnit = PageUnit.fromString(unit != null ? unit : "");

        return new PageDefaults(
                width  != null ? width  : 8.5,
                height != null ? height : 11.0,
                resolvedUnit,
                margin
        );
    }

    /** Read per-side margins from {@code $.page.margin.{top,right,bottom,left}}. */
    private static PageMargins extractMargins(OdinDocument doc) {
        Double top    = metaNumber(doc, "page.margin.top");
        Double right  = metaNumber(doc, "page.margin.right");
        Double bottom = metaNumber(doc, "page.margin.bottom");
        Double left   = metaNumber(doc, "page.margin.left");
        if (top == null && right == null && bottom == null && left == null) {
            return null;
        }
        return new PageMargins(top, right, bottom, left);
    }

    private static ScreenSettings extractScreen(OdinDocument doc) {
        Double scale = metaNumber(doc, "screen.scale");
        if (scale == null) return null;
        return new ScreenSettings(scale);
    }

    private static OdincodeSettings extractOdincode(OdinDocument doc) {
        Boolean enabled = metaBoolean(doc, "odincode.enabled");
        String zone     = metaString(doc, "odincode.zone");
        if (enabled == null && zone == null) return null;

        OdincodeZone resolvedZone = OdincodeZone.fromString(zone != null ? zone : "");
        return new OdincodeSettings(enabled != null ? enabled : false, resolvedZone);
    }

    private static Map<String, String> extractI18n(OdinDocument doc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : doc.getMetadata()) {
            String key = stripLeadingDot(entry.getKey());
            if (key.startsWith("i18n.")) {
                String subKey = key.substring("i18n.".length());
                OdinValue v = entry.getValue();
                String value = v != null && v.isString() ? v.asString() : null;
                if (!subKey.isEmpty() && value != null) {
                    result.put(subKey, value);
                }
            }
        }
        return result.isEmpty() ? null : Collections.unmodifiableMap(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page and Element Extraction
    // ─────────────────────────────────────────────────────────────────────────

    private static List<FormPage> extractPages(OdinDocument doc, Map<String, String> i18n) {
        List<String> allPaths = doc.paths();

        Set<Integer> pageIndices = new TreeSet<>();
        Pattern pagePattern = Pattern.compile("^page\\[(\\d+)\\]\\.");
        for (String path : allPaths) {
            Matcher m = pagePattern.matcher(path);
            if (m.find()) {
                pageIndices.add(Integer.parseInt(m.group(1)));
            }
        }

        if (pageIndices.isEmpty()) return List.of();

        List<FormPage> pages = new ArrayList<>();
        for (int index : pageIndices) {
            pages.add(new FormPage(extractElements(doc, "page[" + index + "].", i18n)));
        }
        return Collections.unmodifiableList(pages);
    }

    /**
     * Collect element keys under {@code prefix} in document order and build each
     * element. An element key is {@code {elementType}.{elementName}}. Region
     * children are absorbed by their region.
     */
    private static List<FormElement> extractElements(
            OdinDocument doc, String prefix, Map<String, String> i18n) {
        Set<String> keysSeen = new LinkedHashSet<>();
        for (String path : doc.paths()) {
            if (!path.startsWith(prefix)) continue;
            String rest = path.substring(prefix.length());
            String[] parts = rest.split("\\.", -1);
            if (parts.length >= 2) {
                keysSeen.add(parts[0] + "." + parts[1]);
            }
        }

        int idCounter = 0;
        List<FormElement> elements = new ArrayList<>();
        for (String elementKey : keysSeen) {
            int dotIdx = elementKey.indexOf('.');
            String elementType = elementKey.substring(0, dotIdx);
            String elementName = elementKey.substring(dotIdx + 1);
            String elementPrefix = prefix + elementKey + ".";
            FormElement element = buildElement(doc, elementType, elementName, elementPrefix, idCounter++, i18n);
            if (element != null) elements.add(element);
        }
        return Collections.unmodifiableList(elements);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Element Builder Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private static FormElement buildElement(OdinDocument doc, String elementType,
                                            String elementName, String prefix,
                                            int idCounter, Map<String, String> i18n) {
        String id = elementType + "_" + elementName + "_" + idCounter;

        return switch (elementType) {
            case "line"     -> buildLineElement(doc, elementName, id, prefix);
            case "rect"     -> buildRectElement(doc, elementName, id, prefix);
            case "circle"   -> buildCircleElement(doc, elementName, id, prefix);
            case "ellipse"  -> buildEllipseElement(doc, elementName, id, prefix);
            case "polygon"  -> buildPolygonElement(doc, elementName, id, prefix);
            case "polyline" -> buildPolylineElement(doc, elementName, id, prefix);
            case "path"     -> buildPathElement(doc, elementName, id, prefix);
            case "text"     -> buildTextElement(doc, elementName, id, prefix, i18n);
            case "img"      -> buildImageElement(doc, elementName, id, prefix, i18n);
            case "barcode"  -> buildBarcodeElement(doc, elementName, id, prefix, i18n);
            case "field"    -> buildFieldElement(doc, elementName, id, prefix, i18n);
            case "region"   -> buildRegionElement(doc, elementName, id, prefix, i18n);
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

    private static TextElement buildTextElement(OdinDocument doc, String name, String id,
                                                String prefix, Map<String, String> i18n) {
        String content = getLabelValue(doc, prefix + "content", i18n);
        return new TextElement(name, id,
                content != null ? content : "",
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                getNumberValue(doc, prefix + "rotate"),
                getStringValue(doc, prefix + "font-family"),
                getNumberValue(doc, prefix + "font-size"),
                getStringValue(doc, prefix + "font-weight"),
                getStringValue(doc, prefix + "font-style"),
                getStringValue(doc, prefix + "text-align"),
                getStringValue(doc, prefix + "color"));
    }

    private static ImageElement buildImageElement(OdinDocument doc, String name, String id,
                                                  String prefix, Map<String, String> i18n) {
        String alt = getLabelValue(doc, prefix + "alt", i18n);
        String src = getBinaryLiteral(doc, prefix + "src");
        return new ImageElement(name, id,
                src != null ? src : "",
                alt != null ? alt : "",
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"),
                getBooleanValue(doc, prefix + "background"));
    }

    private static BarcodeElement buildBarcodeElement(OdinDocument doc, String name, String id,
                                                      String prefix, Map<String, String> i18n) {
        String barcodeTypeStr = getStringValue(doc, prefix + "type");
        if (barcodeTypeStr == null) barcodeTypeStr = getStringValue(doc, prefix + "barcode-type");
        BarcodeType barcodeType = BarcodeType.fromString(barcodeTypeStr != null ? barcodeTypeStr : "");
        String content = getLabelValue(doc, prefix + "content", i18n);
        String alt = getLabelValue(doc, prefix + "alt", i18n);
        return new BarcodeElement(name, id,
                barcodeType,
                content != null ? content : "",
                alt != null ? alt : "",
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Element Builder
    // ─────────────────────────────────────────────────────────────────────────

    private static FormElement buildFieldElement(OdinDocument doc, String name, String id,
                                                 String prefix, Map<String, String> i18n) {
        String fieldType = getStringValue(doc, prefix + "type");
        if (fieldType == null) fieldType = "text";

        BaseFieldParams base = extractBaseField(doc, name, id, prefix, i18n);

        return switch (fieldType) {
            case "checkbox"    -> buildCheckboxField(doc, prefix, base);
            case "radio"       -> buildRadioField(doc, prefix, base);
            case "select"      -> buildSelectField(doc, prefix, base);
            case "multiselect" -> buildMultiselectField(doc, prefix, base);
            case "date"        -> buildDateField(doc, prefix, base);
            case "signature"   -> buildSignatureField(doc, prefix, base);
            default            -> buildTextField(doc, prefix, base); // includes "text" and unknown
        };
    }

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

    private static BaseFieldParams extractBaseField(OdinDocument doc, String name, String id,
                                                    String prefix, Map<String, String> i18n) {
        Boolean required  = getBooleanValue(doc, prefix + "required");
        Double tabindexD  = getNumberValue(doc, prefix + "tabindex");
        Double minLenD    = getNumberValue(doc, prefix + "minLength");
        Double maxLenD    = getNumberValue(doc, prefix + "maxLength");
        Boolean readonly  = getBooleanValue(doc, prefix + "readonly");

        Double minNum = getNumberValue(doc, prefix + "min");
        Double maxNum = getNumberValue(doc, prefix + "max");
        String min = minNum != null ? numToString(minNum) : getScalarString(doc, prefix + "min");
        String max = maxNum != null ? numToString(maxNum) : getScalarString(doc, prefix + "max");

        String label = getLabelValue(doc, prefix + "label", i18n);
        String ariaLabel = getLabelValue(doc, prefix + "aria-label", i18n);

        String bind = getReferenceValue(doc, prefix + "bind");
        String bindStr = bind != null ? "@" + bind : "";

        return new BaseFieldParams(
                name, id,
                label != null ? label : "",
                ariaLabel,
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
                getScalarString(doc, prefix + "value"),
                resolveInputType(getStringValue(doc, prefix + "inputType")),
                getStringValue(doc, prefix + "mask"),
                getStringValue(doc, prefix + "placeholder"),
                getBooleanValue(doc, prefix + "multiline"),
                toIntOrNull(getNumberValue(doc, prefix + "maxLines")));
    }

    private static CheckboxElement buildCheckboxField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new CheckboxElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                getBooleanValue(doc, prefix + "checked"));
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
                getStringValue(doc, prefix + "selected"),
                getStringValue(doc, prefix + "placeholder"));
    }

    private static MultiselectElement buildMultiselectField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new MultiselectElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                extractOptions(doc, prefix),
                extractFieldArray(doc, prefix, "selected"),
                toIntOrNull(getNumberValue(doc, prefix + "minSelect")),
                toIntOrNull(getNumberValue(doc, prefix + "maxSelect")));
    }

    private static DateElement buildDateField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new DateElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                getScalarString(doc, prefix + "value"));
    }

    private static SignatureElement buildSignatureField(OdinDocument doc, String prefix, BaseFieldParams b) {
        return new SignatureElement(b.name(), b.id(),
                b.label(), b.ariaLabel(),
                b.x(), b.y(), b.w(), b.h(), b.bind(),
                b.required(), b.pattern(), b.minLength(), b.maxLength(), b.min(), b.max(),
                b.tabindex(), b.readonly(),
                getBinaryLiteral(doc, prefix + "value"),
                getStringValue(doc, prefix + "date_field"));
    }

    private static String resolveInputType(String inputType) {
        if (inputType == null) return null;
        return switch (inputType) {
            case "text", "email", "tel", "password", "number", "url" -> inputType;
            default -> null;
        };
    }

    /** Extract a field's {@code options} array. */
    private static List<String> extractOptions(OdinDocument doc, String prefix) {
        List<String> arr = extractFieldArray(doc, prefix, "options");
        return arr != null ? arr : List.of();
    }

    /**
     * Extract a field's tabular string array ({@code options} / {@code selected}).
     * Because a relative tabular header resolves against the field's own path, the
     * array may land at {@code <prefix><field>.<name>[n]} rather than
     * {@code <prefix><name>[n]}; search for it regardless of that extra segment.
     */
    private static List<String> extractFieldArray(OdinDocument doc, String prefix, String name) {
        List<String> direct = collectIndexed(doc, prefix + name);
        if (!direct.isEmpty()) return direct;

        Pattern re = Pattern.compile(
                "^" + Pattern.quote(prefix) + "(?:[^.]+\\.)*" + Pattern.quote(name) + "\\[(\\d+)\\]$");
        List<int[]> indices = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        for (String p : doc.paths()) {
            Matcher m = re.matcher(p);
            if (m.matches()) {
                indices.add(new int[]{Integer.parseInt(m.group(1)), paths.size()});
                paths.add(p);
            }
        }
        if (indices.isEmpty()) return null;
        indices.sort(Comparator.comparingInt(a -> a[0]));
        List<String> out = new ArrayList<>();
        for (int[] idx : indices) {
            String v = getStringValue(doc, paths.get(idx[1]));
            if (v != null) out.add(v);
        }
        return out;
    }

    /** Collect a contiguous indexed string array at {@code base[0]}, {@code base[1]}, ... */
    private static List<String> collectIndexed(OdinDocument doc, String base) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (doc.has(base + "[" + i + "]")) {
            String v = getStringValue(doc, base + "[" + i + "]");
            if (v != null) out.add(v);
            i++;
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Region Element Builder
    // ─────────────────────────────────────────────────────────────────────────

    private static RegionElement buildRegionElement(OdinDocument doc, String name, String id,
                                                    String prefix, Map<String, String> i18n) {
        String bind = getReferenceValue(doc, prefix + "bind");
        Integer max = toIntOrNull(getNumberValue(doc, prefix + "max"));
        String overflowRef = getReferenceValue(doc, prefix + "overflow");
        String overflow;
        if (overflowRef != null) {
            overflow = "@" + overflowRef;
        } else {
            overflow = getStringValue(doc, prefix + "overflow");
        }

        List<FormElement> children = extractRegionChildren(doc, prefix, i18n);

        return new RegionElement(name, id,
                num(doc, prefix + "x"), num(doc, prefix + "y"),
                num(doc, prefix + "w"), num(doc, prefix + "h"),
                bind != null ? "@" + bind : null,
                max, overflow,
                children);
    }

    private static final Set<String> REGION_OWN_PROPS =
            Set.of("x", "y", "w", "h", "bind", "max", "overflow");
    private static final Set<String> REGION_CHILD_TYPES =
            Set.of("text", "field", "img", "barcode");

    /**
     * Collect a region's child elements. Children live under
     * {@code <regionPrefix><childType>.<childName>.*}. Region own-properties are
     * skipped. Each child carries its per-item {@code y-offset}/{@code x-offset}.
     */
    private static List<FormElement> extractRegionChildren(
            OdinDocument doc, String prefix, Map<String, String> i18n) {
        Set<String> keysSeen = new LinkedHashSet<>();
        for (String path : doc.paths()) {
            if (!path.startsWith(prefix)) continue;
            String rest = path.substring(prefix.length());
            String[] parts = rest.split("\\.", -1);
            if (parts.length < 2) continue;
            if (REGION_OWN_PROPS.contains(parts[0])) continue;
            if (!REGION_CHILD_TYPES.contains(parts[0])) continue;
            keysSeen.add(parts[0] + "." + parts[1]);
        }

        int idCounter = 0;
        List<FormElement> children = new ArrayList<>();
        for (String key : keysSeen) {
            int dotIdx = key.indexOf('.');
            String childType = key.substring(0, dotIdx);
            String childName = key.substring(dotIdx + 1);
            String childPrefix = prefix + key + ".";
            FormElement built = buildElement(doc, childType, childName, childPrefix, idCounter++, i18n);
            if (built == null) continue;
            built.setOffsets(
                    getNumberValue(doc, childPrefix + "y-offset"),
                    getNumberValue(doc, childPrefix + "x-offset"));
            children.add(built);
        }
        return Collections.unmodifiableList(children);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Value Accessors
    // ─────────────────────────────────────────────────────────────────────────

    private static String getStringValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return val.isString() ? val.asString() : null;
    }

    /**
     * Resolve a string property that may instead be an {@code @$.i18n.*}
     * reference. i18n keys are stored without the {@code $.i18n.} prefix.
     */
    private static String getLabelValue(OdinDocument doc, String path, Map<String, String> i18n) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        if (val.isString()) return val.asString();
        if (val.isReference()) {
            String ref = val.asReference();
            if (ref.startsWith("$.i18n.")) {
                String key = ref.substring("$.i18n.".length());
                if (i18n != null && i18n.containsKey(key)) return i18n.get(key);
                return ref;
            }
            return ref;
        }
        return null;
    }

    /**
     * Read a scalar value as a string, preserving the raw source form for dates
     * and timestamps (e.g. {@code 1900-01-01}).
     */
    private static String getScalarString(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return switch (val) {
            case OdinValue.OdinString s -> s.getValue();
            case OdinValue.OdinDate d -> d.getRaw() != null ? d.getRaw() : d.toString();
            case OdinValue.OdinTimestamp t -> t.getRaw() != null ? t.getRaw() : t.toString();
            default -> null;
        };
    }

    /** Reconstruct an ODIN binary literal ({@code ^algorithm:base64}) for a binary value. */
    private static String getBinaryLiteral(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        if (val instanceof OdinValue.OdinBinary bin) {
            String base64 = Base64.getEncoder().encodeToString(bin.getData());
            return bin.getAlgorithm() != null
                    ? "^" + bin.getAlgorithm() + ":" + base64
                    : "^" + base64;
        }
        if (val.isString()) return val.asString();
        return null;
    }

    private static Double getNumberValue(OdinDocument doc, String path) {
        OdinValue val = doc.get(path);
        if (val == null) return null;
        return val.isNumeric() ? val.asDouble() : null;
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
    // Metadata accessors ({$.section.key} subsections land under metadata)
    // ─────────────────────────────────────────────────────────────────────────

    private static OdinValue getMeta(OdinDocument doc, String key) {
        for (var entry : doc.getMetadata()) {
            if (stripLeadingDot(entry.getKey()).equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String metaString(OdinDocument doc, String key) {
        OdinValue v = getMeta(doc, key);
        return v != null && v.isString() ? v.asString() : null;
    }

    private static Double metaNumber(OdinDocument doc, String key) {
        OdinValue v = getMeta(doc, key);
        return v != null && v.isNumeric() ? v.asDouble() : null;
    }

    private static Boolean metaBoolean(OdinDocument doc, String key) {
        OdinValue v = getMeta(doc, key);
        return v != null && v.isBoolean() ? v.asBool() : null;
    }

    private static String stripLeadingDot(String key) {
        return key.startsWith(".") ? key.substring(1) : key;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static double num(OdinDocument doc, String path) {
        Double d = getNumberValue(doc, path);
        return d != null ? d : 0.0;
    }

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
