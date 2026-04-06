package foundation.odin.forms;

import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.Arrays;
import java.util.List;

/**
 * ODIN Forms — HTML/CSS Renderer
 *
 * Renders a parsed {@link OdinForm} into a complete, accessible HTML string.
 * Supports absolute-positioned layout matching print coordinates,
 * ARIA attributes, skip navigation, and optional data binding.
 *
 * Mirrors the TypeScript renderer.ts implementation exactly.
 */
public final class FormRenderer {

    private FormRenderer() {
        throw new UnsupportedOperationException("FormRenderer is a static utility class");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Render an {@link OdinForm} to a complete HTML string.
     *
     * @param form    parsed OdinForm structure
     * @param data    optional ODIN document for data binding (populates field values); may be null
     * @param options optional rendering options (target, scale, className, lang); may be null
     * @return complete HTML string including {@code <form>}, {@code <style>}, and all elements
     */
    public static String renderForm(OdinForm form, OdinDocument data, RenderFormOptions options) {
        String title     = form.metadata().title() != null && !form.metadata().title().isEmpty()
                           ? form.metadata().title() : "ODIN Form";
        String className = options != null && options.className() != null
                           ? " " + options.className() : "";
        String unit      = form.pageDefaults() != null
                           ? form.pageDefaults().unit().getValue() : "inch";

        StringBuilder sb = new StringBuilder();

        // Wrapper
        sb.append("<form role=\"form\" aria-label=\"").append(escapeAttr(title))
          .append("\" class=\"odin-form").append(className).append("\">");

        // Skip link
        sb.append(skipLinkHtml(title));

        // Style tag
        sb.append("<style>").append(generateFormCSS()).append("\n").append(generatePrintCSS()).append("</style>");

        // Pages
        for (int pageIndex = 0; pageIndex < form.pages().size(); pageIndex++) {
            FormPage page = form.pages().get(pageIndex);
            sb.append(renderPage(page, pageIndex, unit, form, data));
        }

        sb.append("</form>");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderPage(FormPage page, int pageIndex, String unit,
                                     OdinForm form, OdinDocument data) {
        double w = Units.toPixels(
                form.pageDefaults() != null ? form.pageDefaults().width() : 8.5, unit);
        double h = Units.toPixels(
                form.pageDefaults() != null ? form.pageDefaults().height() : 11.0, unit);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"odin-form-page\" id=\"odin-form-content\" style=\"width:")
          .append(px(w)).append(";height:").append(px(h)).append(";\">");

        // Render non-field elements in document order
        for (FormElement el : page.elements()) {
            if (!el.type().isField()) {
                sb.append(renderElement(el, pageIndex, unit, data));
            }
        }

        // Render field elements sorted by tab order
        List<FormElement> sortedFields = tabOrderSort(page.elements());
        for (FormElement el : sortedFields) {
            if (el.type().isField()) {
                sb.append(renderElement(el, pageIndex, unit, data));
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Element Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderElement(FormElement el, int pageIndex, String unit, OdinDocument data) {
        return switch (el.type()) {
            case LINE             -> renderLine((LineElement) el, unit);
            case RECT             -> renderRect((RectElement) el, unit);
            case CIRCLE           -> renderCircle((CircleElement) el, unit);
            case ELLIPSE          -> renderEllipse((EllipseElement) el, unit);
            case POLYGON          -> renderPolygon((PolygonElement) el, unit);
            case POLYLINE         -> renderPolyline((PolylineElement) el, unit);
            case PATH             -> renderPath((PathElement) el, unit);
            case TEXT             -> renderText((TextElement) el, unit);
            case IMG              -> renderImage((ImageElement) el, unit);
            case BARCODE          -> ""; // barcode rendering deferred to specialized library
            case FIELD_TEXT       -> renderTextField((TextFieldElement) el, pageIndex, unit, data);
            case FIELD_CHECKBOX   -> renderCheckbox((CheckboxElement) el, pageIndex, unit, data);
            case FIELD_RADIO      -> renderRadio((RadioElement) el, pageIndex, unit, data);
            case FIELD_SELECT     -> renderSelect((SelectElement) el, pageIndex, unit, data);
            case FIELD_MULTISELECT -> renderMultiselect((MultiselectElement) el, pageIndex, unit, data);
            case FIELD_DATE       -> renderDate((DateElement) el, pageIndex, unit, data);
            case FIELD_SIGNATURE  -> renderSignature((SignatureElement) el, pageIndex, unit);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Geometric Elements
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderLine(LineElement el, String unit) {
        double x1 = Units.toPixels(el.x1(), unit);
        double y1 = Units.toPixels(el.y1(), unit);
        double x2 = Units.toPixels(el.x2(), unit);
        double y2 = Units.toPixels(el.y2(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<line x1=\"" + d(x1) + "\" y1=\"" + d(y1) + "\" x2=\"" + d(x2) + "\" y2=\"" + d(y2) +
               "\" stroke=\"" + escapeAttr(stroke) + "\" stroke-width=\"" + d(strokeWidth) + "\"/>" +
               "</svg>";
    }

    private static String renderRect(RectElement el, String unit) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String border = el.stroke() != null
                ? "border:" + d(el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1) + "px solid " + el.stroke() + ";"
                : "";
        String bg = el.fill() != null && !el.fill().equals("none") ? "background:" + el.fill() + ";" : "";
        double rx = el.rx() != null ? Units.toPixels(el.rx(), unit) : 0;
        double ry = el.ry() != null ? Units.toPixels(el.ry(), unit) : 0;
        String radius = (rx > 0 || ry > 0) ? "border-radius:" + d(rx) + "px " + d(ry) + "px;" : "";
        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";" +
               border + bg + radius + "\"></div>";
    }

    private static String renderCircle(CircleElement el, String unit) {
        double cx = Units.toPixels(el.cx(), unit);
        double cy = Units.toPixels(el.cy(), unit);
        double r  = Units.toPixels(el.r(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<circle cx=\"" + d(cx) + "\" cy=\"" + d(cy) + "\" r=\"" + d(r) +
               "\" stroke=\"" + escapeAttr(stroke) + "\" stroke-width=\"" + d(strokeWidth) +
               "\" fill=\"" + escapeAttr(fill) + "\"/>" +
               "</svg>";
    }

    private static String renderEllipse(EllipseElement el, String unit) {
        double cx = Units.toPixels(el.cx(), unit);
        double cy = Units.toPixels(el.cy(), unit);
        double rx = Units.toPixels(el.rx(), unit);
        double ry = Units.toPixels(el.ry(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<ellipse cx=\"" + d(cx) + "\" cy=\"" + d(cy) + "\" rx=\"" + d(rx) + "\" ry=\"" + d(ry) +
               "\" stroke=\"" + escapeAttr(stroke) + "\" stroke-width=\"" + d(strokeWidth) +
               "\" fill=\"" + escapeAttr(fill) + "\"/>" +
               "</svg>";
    }

    private static String renderPolygon(PolygonElement el, String unit) {
        String points = convertPoints(el.points(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<polygon points=\"" + escapeAttr(points) + "\" stroke=\"" + escapeAttr(stroke) +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"" + escapeAttr(fill) + "\"/>" +
               "</svg>";
    }

    private static String renderPolyline(PolylineElement el, String unit) {
        String points = convertPoints(el.points(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<polyline points=\"" + escapeAttr(points) + "\" stroke=\"" + escapeAttr(stroke) +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"none\"/>" +
               "</svg>";
    }

    private static String renderPath(PathElement el, String unit) {
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<path d=\"" + escapeAttr(el.d()) + "\" stroke=\"" + escapeAttr(stroke) +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"" + escapeAttr(fill) + "\"/>" +
               "</svg>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content Elements
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderText(TextElement el, String unit) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double fontSize = el.fontSize() != null
                ? Units.toPixels(el.fontSize(), "pt")
                : Units.toPixels(12, "pt");
        String fontWeight  = el.fontWeight()  != null ? el.fontWeight()  : "normal";
        String color       = el.color()       != null ? el.color()       : "#000000";
        String fontFamily  = el.fontFamily()  != null ? "font-family:" + el.fontFamily() + ";" : "";
        String fontStyleCs = "italic".equals(el.fontStyle()) ? "font-style:italic;" : "";
        String textAlign   = el.textAlign()   != null ? "text-align:" + el.textAlign() + ";" : "";
        return "<span class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";font-size:" + d(fontSize) + "px;font-weight:" + fontWeight +
               ";color:" + escapeAttr(color) + ";" + fontFamily + fontStyleCs + textAlign + "\">" +
               escapeHtml(el.content()) + "</span>";
    }

    private static String renderImage(ImageElement el, String unit) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        return "<img class=\"odin-form-element\" src=\"" + escapeAttr(el.src()) +
               "\" alt=\"" + escapeAttr(el.alt()) + "\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Elements
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderTextField(TextFieldElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId    = generateFieldId(el.name(), pageIndex);
        String ariaLabel  = el.effectiveAriaLabel();
        String value      = lookupBoundValue(el, data);
        String valueAttr  = value != null ? " value=\"" + escapeAttr(value) + "\"" : "";
        String requiredAt = Boolean.TRUE.equals(el.required()) ? " required" : "";
        String readonlyAt = Boolean.TRUE.equals(el.readonly()) ? " readonly" : "";
        String placeholder = el.placeholder() != null ? " placeholder=\"" + escapeAttr(el.placeholder()) + "\"" : "";
        boolean ariaReq   = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<input type=\"text\" class=\"odin-form-input\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") +
               valueAttr + requiredAt + readonlyAt + placeholder + ">" +
               "</div>";
    }

    private static String renderCheckbox(CheckboxElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, data);
        String checked   = "true".equals(value) ? " checked" : "";
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<input type=\"checkbox\" class=\"odin-form-checkbox\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + checked + ">" +
               "</div>";
    }

    private static String renderRadio(RadioElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, data);
        String checked   = el.value().equals(value) ? " checked" : "";
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        String radioHtml = "<input type=\"radio\" class=\"odin-form-radio\" id=\"" + inputId +
                           "\" name=\"" + escapeAttr(el.group()) +
                           "\" value=\"" + escapeAttr(el.value()) +
                           "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
                           (ariaReq ? " aria-required=\"true\"" : "") + checked + ">" +
                           "<label for=\"" + inputId + "\">" + escapeHtml(el.label()) + "</label>";

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldGroupHtml(el.group(), el.label(), radioHtml) +
               "</div>";
    }

    private static String renderSelect(SelectElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, data);
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        StringBuilder opts = new StringBuilder();
        if (el.placeholder() != null) {
            opts.append("<option value=\"\">").append(escapeHtml(el.placeholder())).append("</option>");
        }
        for (String opt : el.options()) {
            String selected = opt.equals(value) ? " selected" : "";
            opts.append("<option value=\"").append(escapeAttr(opt)).append("\"").append(selected).append(">")
                .append(escapeHtml(opt)).append("</option>");
        }

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<select class=\"odin-form-select\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + ">" +
               opts + "</select>" +
               "</div>";
    }

    private static String renderMultiselect(MultiselectElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, data);
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        List<String> selected = value != null
                ? Arrays.stream(value.split(",")).map(String::trim).toList()
                : List.of();

        StringBuilder opts = new StringBuilder();
        for (String opt : el.options()) {
            String sel = selected.contains(opt) ? " selected" : "";
            opts.append("<option value=\"").append(escapeAttr(opt)).append("\"").append(sel).append(">")
                .append(escapeHtml(opt)).append("</option>");
        }

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<select multiple class=\"odin-form-select\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + ">" +
               opts + "</select>" +
               "</div>";
    }

    private static String renderDate(DateElement el, int pageIndex, String unit, OdinDocument data) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, data);
        String valueAttr = value != null ? " value=\"" + escapeAttr(value) + "\"" : "";
        String requiredAt = Boolean.TRUE.equals(el.required()) ? " required" : "";
        boolean ariaReq   = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<input type=\"date\" class=\"odin-form-input\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + valueAttr + requiredAt + ">" +
               "</div>";
    }

    private static String renderSignature(SignatureElement el, int pageIndex, String unit) {
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(el.label(), inputId) +
               "<div class=\"odin-form-signature\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(ariaLabel) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") +
               " role=\"img\" tabindex=\"0\" style=\"width:100%;height:100%;\"></div>" +
               "</div>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Binding
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Looks up a field's bound value in the data document.
     *
     * The {@code bind} property uses {@code @path.to.value} syntax. We strip the
     * leading {@code @} and resolve the path against the data document.
     */
    private static String lookupBoundValue(BaseFieldElement el, OdinDocument data) {
        if (data == null || el.bind() == null || el.bind().isEmpty()) return null;

        String path = el.bind().startsWith("@") ? el.bind().substring(1) : el.bind();
        if (path.isEmpty()) return null;

        OdinValue val = data.get(path);
        if (val == null) return null;

        if (val.isString())  return val.asString();
        if (val.isNumeric()) return val.asDouble() != null ? String.valueOf(val.asDouble()) : null;
        if (val.isBoolean()) return String.valueOf(val.asBool());
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessibility Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String generateFieldId(String name, int pageIndex) {
        return "odin-field-" + pageIndex + "-" + name;
    }

    private static String fieldLabelHtml(String label, String inputId) {
        return "<label class=\"odin-form-label\" for=\"" + inputId + "\">" +
               escapeHtml(label) + "</label>";
    }

    private static String fieldGroupHtml(String group, String label, String innerHtml) {
        return "<fieldset class=\"odin-form-group\">" +
               "<legend class=\"odin-form-legend\">" + escapeHtml(label) + "</legend>" +
               innerHtml +
               "</fieldset>";
    }

    private static String skipLinkHtml(String title) {
        return "<a class=\"odin-skip-link\" href=\"#odin-form-content\">Skip to form: " +
               escapeHtml(title) + "</a>";
    }

    /**
     * Sort field elements by their {@code tabindex} property, preserving document
     * order for elements without a tabindex (mirrors TypeScript's tabOrderSort).
     */
    private static List<FormElement> tabOrderSort(List<FormElement> elements) {
        return elements.stream()
                .filter(el -> el instanceof BaseFieldElement)
                .sorted((a, b) -> {
                    Integer ta = ((BaseFieldElement) a).tabindex();
                    Integer tb = ((BaseFieldElement) b).tabindex();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return Integer.compare(ta, tb);
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CSS Generation
    // ─────────────────────────────────────────────────────────────────────────

    private static String generateFormCSS() {
        return """
                .odin-form { position: relative; font-family: Helvetica, Arial, sans-serif; }
                .odin-form-page { position: relative; overflow: hidden; box-sizing: border-box; background: white; }
                .odin-form-element { box-sizing: border-box; }
                .odin-form-input, .odin-form-select { width: 100%; box-sizing: border-box; }
                .odin-form-checkbox, .odin-form-radio { cursor: pointer; }
                .odin-form-signature { border: 1px dashed #aaa; background: #fafafa; cursor: crosshair; }
                .odin-form-label { display: block; font-size: 11px; color: #555; margin-bottom: 2px; }
                .odin-form-legend { font-size: 11px; color: #555; }
                .odin-form-group { border: none; padding: 0; margin: 0; }
                .odin-skip-link { position: absolute; left: -9999px; top: auto; width: 1px; height: 1px; overflow: hidden; }
                .odin-skip-link:focus { position: static; width: auto; height: auto; }""";
    }

    private static String generatePrintCSS() {
        return """
                @media print {
                  .odin-skip-link { display: none; }
                  .odin-form-page { page-break-after: always; }
                }""";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility Functions
    // ─────────────────────────────────────────────────────────────────────────

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;");
    }

    private static String escapeAttr(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("\"", "&quot;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;");
    }

    /**
     * Converts an SVG {@code points} string (space-separated x,y pairs in page units)
     * to pixel values.
     */
    private static String convertPoints(String points, String unit) {
        if (points == null || points.isBlank()) return "";
        String[] pairs = points.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) sb.append(' ');
            String pair = pairs[i];
            int comma = pair.indexOf(',');
            if (comma < 0) {
                sb.append(pair);
            } else {
                double x = parseDouble(pair.substring(0, comma));
                double y = parseDouble(pair.substring(comma + 1));
                sb.append(d(Units.toPixels(x, unit))).append(',').append(d(Units.toPixels(y, unit)));
            }
        }
        return sb.toString();
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** Format a double as a pixel string (e.g. "96.0px"). */
    private static String px(double v) {
        return d(v) + "px";
    }

    /** Format a double value compactly (strip unnecessary trailing .0). */
    private static String d(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
