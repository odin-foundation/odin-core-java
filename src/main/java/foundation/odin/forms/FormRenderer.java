package foundation.odin.forms;

import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ODIN Forms — HTML/CSS Renderer
 *
 * Renders a parsed {@link OdinForm} into a complete, accessible HTML string.
 * Supports absolute-positioned layout matching print coordinates, ARIA
 * attributes, skip navigation, region repetition with overflow, and optional
 * data binding.
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
     * @param data    optional ODIN document for data binding; may be null
     * @param options optional rendering options; may be null
     * @return complete HTML string including {@code <form>}, {@code <style>}, and all elements
     */
    public static String renderForm(OdinForm form, OdinDocument data, RenderFormOptions options) {
        String title     = form.metadata().title() != null && !form.metadata().title().isEmpty()
                           ? form.metadata().title() : "ODIN Form";
        String className = options != null && options.className() != null
                           ? " " + options.className() : "";
        String unit      = form.pageDefaults() != null
                           ? form.pageDefaults().unit().getValue() : "inch";

        // Two-pass: build the concrete render plan (pages + overflow), then render
        // with the final total page count so {@odin.total_pages} resolves.
        List<PlannedPage> plan = buildRenderPlan(form, data);
        int totalPages = plan.size();
        double pageW = Units.toPixels(form.pageDefaults() != null ? form.pageDefaults().width() : 8.5, unit);
        double pageH = Units.toPixels(form.pageDefaults() != null ? form.pageDefaults().height() : 11.0, unit);

        StringBuilder sb = new StringBuilder();
        sb.append("<form role=\"form\" aria-label=\"").append(escapeAttr(title))
          .append("\" class=\"odin-form").append(className).append("\">");
        sb.append(skipLinkHtml(title));
        sb.append("<style>").append(generateFormCSS()).append("\n").append(generatePrintCSS()).append("</style>");

        for (int i = 0; i < plan.size(); i++) {
            RenderContext ctx = new RenderContext(i + 1, totalPages, unit, data, pageW, pageH);
            sb.append(renderPlannedPage(plan.get(i), ctx));
        }

        sb.append("</form>");
        return sb.toString();
    }

    /** Render-time context for a single output page. */
    private record RenderContext(int pageNumber, int totalPages, String unit,
                                 OdinDocument data, double pageWidthPx, double pageHeightPx) {}

    /** A region slice rendered on an overflow page. */
    private record ItemSlice(int start, int count, String bind) {}

    /** A page to render: either a concrete page or a template-generated overflow page. */
    private record PlannedPage(List<FormElement> elements, Map<String, ItemSlice> itemSlices) {}

    /**
     * Build the ordered list of output pages, expanding region overflow when
     * bound array data is present. Without data, concrete pages render as-is.
     */
    private static List<PlannedPage> buildRenderPlan(OdinForm form, OdinDocument data) {
        List<PlannedPage> plan = new ArrayList<>();

        for (FormPage page : form.pages()) {
            plan.add(new PlannedPage(page.elements(), null));

            if (data == null) continue;

            for (FormElement el : page.elements()) {
                if (!(el instanceof RegionElement region)) continue;
                if (region.bind() == null || region.max() == null || region.overflow() == null) continue;
                int regionMax = region.max();
                if (regionMax < 1) continue;
                int count = boundArrayLength(region.bind(), data);
                if (count <= regionMax) continue;

                int consumed = regionMax;
                String templateName = region.overflow().startsWith("@")
                        ? region.overflow().substring(1) : null;
                int guard = 0;
                while (consumed < count && guard++ < 10000) {
                    PageTemplate tpl = templateName != null && form.templates() != null
                            ? form.templates().get(templateName) : null;
                    RegionElement tplRegion = findRegion(tpl, region.name());
                    Integer candidateMax = tplRegion != null ? tplRegion.max() : region.max();
                    int pageMax = candidateMax != null && candidateMax >= 1 ? candidateMax : regionMax;

                    Map<String, ItemSlice> slices = new LinkedHashMap<>();
                    slices.put(region.name(),
                            new ItemSlice(consumed, Math.min(pageMax, count - consumed), region.bind()));
                    List<FormElement> elements = tpl != null ? tpl.elements() : page.elements();
                    plan.add(new PlannedPage(elements, slices));
                    consumed += pageMax;

                    if (tplRegion != null && tplRegion.overflow() != null
                            && tplRegion.overflow().startsWith("@")) {
                        templateName = tplRegion.overflow().substring(1);
                    }
                }
            }
        }

        return plan;
    }

    private static RegionElement findRegion(PageTemplate tpl, String name) {
        if (tpl == null) return null;
        for (FormElement el : tpl.elements()) {
            if (el instanceof RegionElement r && r.name().equals(name)) return r;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderPlannedPage(PlannedPage page, RenderContext ctx) {
        int pageIndex = ctx.pageNumber() - 1;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"odin-form-page\" id=\"odin-form-content\" data-page=\"")
          .append(ctx.pageNumber()).append("\" style=\"width:")
          .append(px(ctx.pageWidthPx())).append(";height:").append(px(ctx.pageHeightPx())).append(";\">");

        // Background images first (lowest z-index), then non-field elements, then fields.
        for (FormElement el : page.elements()) {
            if (el instanceof ImageElement img && Boolean.TRUE.equals(img.background())) {
                sb.append(renderElement(el, pageIndex, ctx, page));
            }
        }
        for (FormElement el : page.elements()) {
            if (el instanceof ImageElement img && Boolean.TRUE.equals(img.background())) continue;
            if (!el.type().isField()) {
                sb.append(renderElement(el, pageIndex, ctx, page));
            }
        }
        for (FormElement el : tabOrderSort(page.elements())) {
            sb.append(renderElement(el, pageIndex, ctx, page));
        }

        sb.append("</div>");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Element Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderElement(FormElement el, int pageIndex, RenderContext ctx, PlannedPage page) {
        String unit = ctx.unit();
        return switch (el.type()) {
            case LINE              -> renderLine((LineElement) el, unit);
            case RECT              -> renderRect((RectElement) el, unit);
            case CIRCLE            -> renderCircle((CircleElement) el, unit);
            case ELLIPSE           -> renderEllipse((EllipseElement) el, unit);
            case POLYGON           -> renderPolygon((PolygonElement) el, unit);
            case POLYLINE          -> renderPolyline((PolylineElement) el, unit);
            case PATH              -> renderPath((PathElement) el, unit);
            case TEXT              -> renderText((TextElement) el, ctx);
            case IMG               -> renderImage((ImageElement) el, ctx);
            case BARCODE           -> renderBarcode((BarcodeElement) el, ctx);
            case FIELD_TEXT        -> renderTextField((TextFieldElement) el, pageIndex, ctx);
            case FIELD_CHECKBOX    -> renderCheckbox((CheckboxElement) el, pageIndex, ctx);
            case FIELD_RADIO       -> renderRadio((RadioElement) el, pageIndex, ctx);
            case FIELD_SELECT      -> renderSelect((SelectElement) el, pageIndex, ctx);
            case FIELD_MULTISELECT -> renderMultiselect((MultiselectElement) el, pageIndex, ctx);
            case FIELD_DATE        -> renderDate((DateElement) el, pageIndex, ctx);
            case FIELD_SIGNATURE   -> renderSignature((SignatureElement) el, pageIndex, ctx);
            case REGION            -> renderRegion((RegionElement) el, ctx, page);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Interpolation
    // ─────────────────────────────────────────────────────────────────────────

    private static final Pattern ODIN_TOKEN = Pattern.compile("\\{@odin\\.([a-z_]+)\\}");

    /** Resolve {@code {@odin.page}} / {@code {@odin.total_pages}} tokens in a string. */
    private static String interpolate(String text, RenderContext ctx) {
        if (text == null) return "";
        Matcher m = ODIN_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String repl = switch (m.group(1)) {
                case "page" -> String.valueOf(ctx.pageNumber());
                case "total_pages" -> String.valueOf(ctx.totalPages());
                default -> m.group(0);
            };
            m.appendReplacement(out, Matcher.quoteReplacement(repl));
        }
        m.appendTail(out);
        return out.toString();
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
               "\" stroke=\"" + stroke + "\" stroke-width=\"" + d(strokeWidth) + "\"/>" +
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
               "\" stroke=\"" + stroke + "\" stroke-width=\"" + d(strokeWidth) +
               "\" fill=\"" + fill + "\"/>" +
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
               "\" stroke=\"" + stroke + "\" stroke-width=\"" + d(strokeWidth) +
               "\" fill=\"" + fill + "\"/>" +
               "</svg>";
    }

    private static String renderPolygon(PolygonElement el, String unit) {
        String points = convertPoints(el.points(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<polygon points=\"" + points + "\" stroke=\"" + stroke +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"" + fill + "\"/>" +
               "</svg>";
    }

    private static String renderPolyline(PolylineElement el, String unit) {
        String points = convertPoints(el.points(), unit);
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<polyline points=\"" + points + "\" stroke=\"" + stroke +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"none\"/>" +
               "</svg>";
    }

    private static String renderPath(PathElement el, String unit) {
        String stroke = el.stroke() != null ? el.stroke() : "#000000";
        double strokeWidth = el.strokeWidth() != null ? Units.toPixels(el.strokeWidth(), unit) : 1;
        String fill = el.fill() != null ? el.fill() : "none";
        return "<svg class=\"odin-form-element\" style=\"position:absolute;left:0;top:0;width:100%;height:100%;overflow:visible;\">" +
               "<path d=\"" + el.d() + "\" stroke=\"" + stroke +
               "\" stroke-width=\"" + d(strokeWidth) + "\" fill=\"" + fill + "\"/>" +
               "</svg>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content Elements
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderText(TextElement el, RenderContext ctx) {
        String unit = ctx.unit();
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
        String content     = interpolate(el.content(), ctx);
        return "<span class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";font-size:" + d(fontSize) + "px;font-weight:" + fontWeight +
               ";color:" + color + ";" + fontFamily + fontStyleCs + textAlign + "\">" +
               escapeHtml(content) + "</span>";
    }

    private static String renderImage(ImageElement el, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String src = imageSrcToDataUri(el.src());
        String alt = interpolate(el.alt(), ctx);
        String zIndex = Boolean.TRUE.equals(el.background()) ? "z-index:0;" : "";
        return "<img class=\"odin-form-element\" src=\"" + escapeAttr(src) +
               "\" alt=\"" + escapeAttr(alt) + "\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";" + zIndex + "\">";
    }

    private static String renderBarcode(BarcodeElement el, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String alt = interpolate(el.alt(), ctx);
        String content = interpolate(el.content(), ctx);
        return "<div class=\"odin-form-element odin-form-barcode\" role=\"img\" aria-label=\"" +
               escapeAttr(alt) + "\" data-barcode-type=\"" + escapeAttr(el.barcodeType().getValue()) +
               "\" data-content=\"" + escapeAttr(content) +
               "\" style=\"position:absolute;left:" + px(x) + ";top:" + px(y) +
               ";width:" + px(w) + ";height:" + px(h) + ";\"></div>";
    }

    /**
     * Convert an ODIN binary literal ({@code ^png:base64}) to a data URI.
     * Passes through values already in data-URI or URL form.
     */
    private static String imageSrcToDataUri(String src) {
        if (src == null || !src.startsWith("^")) return src != null ? src : "";
        String rest = src.substring(1);
        int colon = rest.indexOf(':');
        if (colon < 0) return "data:image/png;base64," + rest;
        String format = rest.substring(0, colon);
        String b64 = rest.substring(colon + 1);
        return "data:image/" + format + ";base64," + b64;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Elements
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderTextField(TextFieldElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId    = generateFieldId(el.name(), pageIndex);
        String ariaLabel  = el.effectiveAriaLabel();
        String value      = el.value() != null ? el.value() : lookupBoundValue(el, ctx.data());
        String valueAttr  = value != null ? " value=\"" + escapeAttr(value) + "\"" : "";
        String requiredAt = Boolean.TRUE.equals(el.required()) ? " required" : "";
        String readonlyAt = Boolean.TRUE.equals(el.readonly()) ? " readonly" : "";
        String placeholder = el.placeholder() != null ? " placeholder=\"" + escapeAttr(el.placeholder()) + "\"" : "";
        String inputType  = el.inputType() != null ? el.inputType() : "text";
        boolean ariaReq   = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<input type=\"" + escapeAttr(inputType) + "\" class=\"odin-form-input\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") +
               valueAttr + requiredAt + readonlyAt + placeholder + ">" +
               "</div>";
    }

    private static String renderCheckbox(CheckboxElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String bound     = lookupBoundValue(el, ctx.data());
        boolean isChecked = el.checked() != null ? el.checked() : "true".equals(bound);
        String checked   = isChecked ? " checked" : "";
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<input type=\"checkbox\" class=\"odin-form-checkbox\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + checked + ">" +
               "</div>";
    }

    private static String renderRadio(RadioElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = lookupBoundValue(el, ctx.data());
        String checked   = el.value().equals(value) ? " checked" : "";
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        String radioHtml = "<input type=\"radio\" class=\"odin-form-radio\" id=\"" + inputId +
                           "\" name=\"" + escapeAttr(el.group()) +
                           "\" value=\"" + escapeAttr(el.value()) +
                           "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
                           (ariaReq ? " aria-required=\"true\"" : "") + checked + ">" +
                           "<label for=\"" + inputId + "\">" + escapeHtml(interpolate(el.label(), ctx)) + "</label>";

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldGroupHtml(el.group(), interpolate(el.label(), ctx), radioHtml) +
               "</div>";
    }

    private static String renderSelect(SelectElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = el.selected() != null ? el.selected() : lookupBoundValue(el, ctx.data());
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
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<select class=\"odin-form-select\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + ">" +
               opts + "</select>" +
               "</div>";
    }

    private static String renderMultiselect(MultiselectElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        List<String> selected;
        if (el.selected() != null) {
            selected = el.selected();
        } else {
            String value = lookupBoundValue(el, ctx.data());
            selected = value != null
                    ? Arrays.stream(value.split(",")).map(String::trim).toList()
                    : List.of();
        }

        StringBuilder opts = new StringBuilder();
        for (String opt : el.options()) {
            String sel = selected.contains(opt) ? " selected" : "";
            opts.append("<option value=\"").append(escapeAttr(opt)).append("\"").append(sel).append(">")
                .append(escapeHtml(opt)).append("</option>");
        }

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<select multiple class=\"odin-form-select\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + ">" +
               opts + "</select>" +
               "</div>";
    }

    private static String renderDate(DateElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        String value     = el.value() != null ? el.value() : lookupBoundValue(el, ctx.data());
        String valueAttr = value != null ? " value=\"" + escapeAttr(value) + "\"" : "";
        String requiredAt = Boolean.TRUE.equals(el.required()) ? " required" : "";
        boolean ariaReq   = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<input type=\"date\" class=\"odin-form-input\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") + valueAttr + requiredAt + ">" +
               "</div>";
    }

    private static String renderSignature(SignatureElement el, int pageIndex, RenderContext ctx) {
        String unit = ctx.unit();
        double x = Units.toPixels(el.x(), unit);
        double y = Units.toPixels(el.y(), unit);
        double w = Units.toPixels(el.w(), unit);
        double h = Units.toPixels(el.h(), unit);
        String inputId   = generateFieldId(el.name(), pageIndex);
        String ariaLabel = el.effectiveAriaLabel();
        boolean ariaReq  = Boolean.TRUE.equals(el.required());

        return "<div class=\"odin-form-element\" style=\"position:absolute;left:" + px(x) +
               ";top:" + px(y) + ";width:" + px(w) + ";height:" + px(h) + ";\">" +
               fieldLabelHtml(interpolate(el.label(), ctx), inputId) +
               "<div class=\"odin-form-signature\" id=\"" + inputId +
               "\" aria-label=\"" + escapeAttr(interpolate(ariaLabel, ctx)) + "\"" +
               (ariaReq ? " aria-required=\"true\"" : "") +
               " role=\"img\" tabindex=\"0\" style=\"width:100%;height:100%;\"></div>" +
               "</div>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Region Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderRegion(RegionElement el, RenderContext ctx, PlannedPage page) {
        String unit = ctx.unit();
        double regionX = Units.toPixels(el.x(), unit);
        double regionY = Units.toPixels(el.y(), unit);
        double regionW = Units.toPixels(el.w(), unit);
        double regionH = Units.toPixels(el.h(), unit);

        ItemSlice slice = page.itemSlices() != null ? page.itemSlices().get(el.name()) : null;
        String bind = el.bind() != null ? el.bind() : (slice != null ? slice.bind() : null);
        int total = bind != null ? boundArrayLength(bind, ctx.data()) : 0;
        int start;
        int count;
        if (slice != null) {
            start = slice.start();
            count = slice.count();
        } else if (total > 0) {
            start = 0;
            count = el.max() != null ? Math.min(el.max(), total) : total;
        } else {
            start = 0;
            count = 1; // empty layout preview
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"odin-form-element odin-form-region\" data-region=\"")
          .append(escapeAttr(el.name())).append("\" style=\"position:absolute;left:")
          .append(px(regionX)).append(";top:").append(px(regionY)).append(";width:")
          .append(px(regionW)).append(";height:").append(px(regionH)).append(";\">");

        for (int i = 0; i < count; i++) {
            int itemIndex = start + i;
            String itemBind = bind != null ? bind + "[" + itemIndex + "]" : null;
            for (FormElement child : el.children()) {
                sb.append(renderRegionChild(child, i, itemBind, ctx));
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    /**
     * Render one region child for repetition index {@code i}. Coordinates are
     * rebased by the per-item offsets; field children get a unique name and have
     * their {@code @.field} relative binding resolved against the current item.
     */
    private static String renderRegionChild(FormElement child, int i, String itemBind, RenderContext ctx) {
        double yOffset = child.yOffset() != null ? child.yOffset() : 0;
        double xOffset = child.xOffset() != null ? child.xOffset() : 0;

        if (child instanceof TextElement t) {
            double dx = t.x() + xOffset * i;
            double dy = t.y() + yOffset * i;
            TextElement rebased = new TextElement(t.name(), t.id(), t.content(), dx, dy, t.rotate(),
                    t.fontFamily(), t.fontSize(), t.fontWeight(), t.fontStyle(), t.textAlign(), t.color());
            return renderText(rebased, ctx);
        }

        if (child instanceof BaseFieldElement f) {
            double dx = f.x() + xOffset * i;
            double dy = f.y() + yOffset * i;
            String resolvedBind = resolveRelativeBind(f.bind(), itemBind);
            if (resolvedBind == null) resolvedBind = f.bind();
            String newName = f.name() + "_" + i;
            FormElement rebased = rebaseField(f, dx, dy, newName, resolvedBind);
            int childPageIndex = -1 - i;
            return renderElement(rebased, childPageIndex, ctx, new PlannedPage(List.of(), null));
        }

        // img / barcode children render in place (no relative binding).
        return renderElement(child, -1 - i, ctx, new PlannedPage(List.of(), null));
    }

    /** Rebuild a field with new coordinates, name, and resolved binding. */
    private static FormElement rebaseField(BaseFieldElement f, double x, double y, String name, String bind) {
        return switch (f) {
            case TextFieldElement t -> new TextFieldElement(name, t.id(), t.label(), t.ariaLabel(),
                    x, y, t.w(), t.h(), bind, t.required(), t.pattern(), t.minLength(), t.maxLength(),
                    t.min(), t.max(), t.tabindex(), t.readonly(), t.value(), t.inputType(), t.mask(),
                    t.placeholder(), t.multiline(), t.maxLines());
            case CheckboxElement c -> new CheckboxElement(name, c.id(), c.label(), c.ariaLabel(),
                    x, y, c.w(), c.h(), bind, c.required(), c.pattern(), c.minLength(), c.maxLength(),
                    c.min(), c.max(), c.tabindex(), c.readonly(), c.checked());
            case RadioElement r -> new RadioElement(name, r.id(), r.label(), r.ariaLabel(),
                    x, y, r.w(), r.h(), bind, r.required(), r.pattern(), r.minLength(), r.maxLength(),
                    r.min(), r.max(), r.tabindex(), r.readonly(), r.group(), r.value());
            case SelectElement s -> new SelectElement(name, s.id(), s.label(), s.ariaLabel(),
                    x, y, s.w(), s.h(), bind, s.required(), s.pattern(), s.minLength(), s.maxLength(),
                    s.min(), s.max(), s.tabindex(), s.readonly(), s.options(), s.selected(), s.placeholder());
            case MultiselectElement m -> new MultiselectElement(name, m.id(), m.label(), m.ariaLabel(),
                    x, y, m.w(), m.h(), bind, m.required(), m.pattern(), m.minLength(), m.maxLength(),
                    m.min(), m.max(), m.tabindex(), m.readonly(), m.options(), m.selected(),
                    m.minSelect(), m.maxSelect());
            case DateElement de -> new DateElement(name, de.id(), de.label(), de.ariaLabel(),
                    x, y, de.w(), de.h(), bind, de.required(), de.pattern(), de.minLength(), de.maxLength(),
                    de.min(), de.max(), de.tabindex(), de.readonly(), de.value());
            case SignatureElement sg -> new SignatureElement(name, sg.id(), sg.label(), sg.ariaLabel(),
                    x, y, sg.w(), sg.h(), bind, sg.required(), sg.pattern(), sg.minLength(), sg.maxLength(),
                    sg.min(), sg.max(), sg.tabindex(), sg.readonly(), sg.value(), sg.dateField());
        };
    }

    /** Resolve a region child's {@code @.field} relative bind against the current item path. */
    private static String resolveRelativeBind(String bind, String itemBind) {
        if (bind == null || bind.isEmpty()) return null;
        if (bind.startsWith("@.")) {
            if (itemBind == null) return null;
            return itemBind + "." + bind.substring(2);
        }
        return bind;
    }

    private static final Pattern ESCAPE_REGEXP = Pattern.compile("[.*+?^${}()|\\[\\]\\\\]");

    /**
     * Number of items in a bound array path. Counts both scalar elements
     * ({@code path[n]}) and object elements ({@code path[n].field}).
     */
    private static int boundArrayLength(String bind, OdinDocument data) {
        if (data == null) return 0;
        String path = bind.startsWith("@") ? bind.substring(1) : bind;
        Pattern re = Pattern.compile("^" + escapeRegexp(path) + "\\[(\\d+)\\](?:\\.|$)");
        int max = -1;
        for (String p : data.paths()) {
            Matcher m = re.matcher(p);
            if (m.find()) {
                int idx = Integer.parseInt(m.group(1));
                if (idx > max) max = idx;
            }
        }
        return max + 1;
    }

    private static String escapeRegexp(String s) {
        return ESCAPE_REGEXP.matcher(s).replaceAll("\\\\$0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Binding
    // ─────────────────────────────────────────────────────────────────────────

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

    /** Sort field elements by tabindex, preserving document order otherwise. */
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
                  .replace("'", "&#39;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;");
    }

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

    private static String px(double v) {
        return d(v) + "px";
    }

    private static String d(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
