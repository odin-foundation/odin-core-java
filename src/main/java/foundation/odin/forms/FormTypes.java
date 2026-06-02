package foundation.odin.forms;

import java.util.List;
import java.util.Map;

/**
 * ODIN Forms 1.0 — Java Type Definitions
 *
 * Declarative form definition types for print and screen rendering.
 *
 * Design: print-first, absolute positioning, bidirectional data binding.
 */
public final class FormTypes {

    private FormTypes() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Root Document
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Root ODIN Forms document.
     *
     * Corresponds to the top-level structure of a {@code .odin} forms file,
     * including the {@code {$}} metadata section, optional settings sections,
     * and {@code page[n]} pages.
     */
    public record OdinForm(
            /** Document-level metadata ({$}). */
            FormMetadata metadata,
            /** Default page dimensions and margins ({$.page}). May be null. */
            PageDefaults pageDefaults,
            /** Screen rendering options ({$.screen}). May be null. */
            ScreenSettings screen,
            /** Multi-language label dictionary ({$.i18n}). May be null. */
            Map<String, String> i18n,
            /** Ordered list of form pages (page[0], page[1], ...). */
            List<FormPage> pages,
            /** Page templates ({@tpl_*}) keyed by template name. May be null. */
            Map<String, PageTemplate> templates
    ) {}

    /**
     * A page template ({@code {@tpl_*}}) — a layout for dynamically generated
     * overflow pages. Not rendered directly; instantiated when a region overflows.
     */
    public record PageTemplate(
            /** Template name (e.g. tpl_vehicles_continued). */
            String name,
            /** Always true — marks this as a template, not a concrete page. */
            boolean pageTemplate,
            /** Names the region this template continues (e.g. region.vehicles). May be null. */
            String continues,
            /** Form identifier for continuation pages. May be null. */
            String formId,
            /** Elements contained in the template, in document order. */
            List<FormElement> elements
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata and Settings
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Document-level metadata from the {@code {$}} header.
     */
    public record FormMetadata(
            /** Human-readable form title. */
            String title,
            /** Unique form identifier. */
            String id,
            /** Primary language code (e.g. "en", "es"). */
            String lang,
            /** ODIN Forms schema version (e.g. "1.0.0"). May be null. */
            String version
    ) {}

    /**
     * Unit for page coordinates and dimensions.
     */
    public enum PageUnit {
        INCH("inch"), CM("cm"), MM("mm"), PT("pt");

        private final String value;

        PageUnit(String value) { this.value = value; }

        public String getValue() { return value; }

        public static PageUnit fromString(String s) {
            for (PageUnit u : values()) {
                if (u.value.equals(s)) return u;
            }
            return INCH; // default
        }
    }

    /**
     * Default page dimensions applied to all pages unless overridden.
     * Corresponds to {@code {$.page}}.
     */
    public record PageDefaults(
            /** Page width in the declared unit. */
            double width,
            /** Page height in the declared unit. */
            double height,
            /** Measurement unit for all coordinates and dimensions on the page. */
            PageUnit unit,
            /** Per-side page margins in the declared unit. May be null. */
            PageMargins margin
    ) {
        public boolean hasMargin() { return margin != null; }
    }

    /**
     * Per-side page margins. Corresponds to {@code margin.top}, {@code margin.right},
     * {@code margin.bottom}, {@code margin.left} under {@code {$.page}}.
     * Each side may be null when absent.
     */
    public record PageMargins(
            Double top,
            Double right,
            Double bottom,
            Double left
    ) {}

    /**
     * Optional settings for screen/web rendering.
     * Corresponds to {@code {$.screen}}.
     */
    public record ScreenSettings(
            /** Default zoom factor. 1.0 = 100% (no scaling). */
            double scale
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Pages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single form page containing an ordered list of elements.
     * Corresponds to {@code {page[n]}}.
     */
    public record FormPage(
            /** All elements on this page, in document order. */
            List<FormElement> elements
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Element Type Enum
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * All valid ODIN Forms element type strings.
     */
    public enum ElementType {
        // Geometric
        LINE("line"), RECT("rect"), CIRCLE("circle"), ELLIPSE("ellipse"),
        POLYGON("polygon"), POLYLINE("polyline"), PATH("path"),
        // Content
        TEXT("text"), IMG("img"), BARCODE("barcode"),
        // Fields
        FIELD_TEXT("field.text"), FIELD_CHECKBOX("field.checkbox"),
        FIELD_RADIO("field.radio"), FIELD_SELECT("field.select"),
        FIELD_MULTISELECT("field.multiselect"), FIELD_DATE("field.date"),
        FIELD_SIGNATURE("field.signature"),
        // Container
        REGION("region");

        private final String value;

        ElementType(String value) { this.value = value; }

        public String getValue() { return value; }

        public boolean isField() { return value.startsWith("field."); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Base Element (sealed class hierarchy)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Base class for all form elements. Use {@link #type()} to dispatch.
     */
    public abstract static sealed class FormElement
            permits LineElement, RectElement, CircleElement, EllipseElement,
                    PolygonElement, PolylineElement, PathElement,
                    TextElement, ImageElement, BarcodeElement,
                    BaseFieldElement, RegionElement {

        private final ElementType type;
        private final String name;
        private final String id;
        /** Per-item vertical offset when this element is a region child. May be null. */
        private Double yOffset;
        /** Per-item horizontal offset when this element is a region child. May be null. */
        private Double xOffset;

        protected FormElement(ElementType type, String name, String id) {
            this.type = type;
            this.name = name;
            this.id   = id;
        }

        /** Element discriminator. */
        public ElementType type() { return type; }

        /** Element name, taken from the path key. Unique within the page. */
        public String name() { return name; }

        /**
         * Optional stable identifier for programmatic access and data binding.
         * May be null.
         */
        public String id() { return id; }

        /** Per-item vertical offset when this element repeats inside a region. May be null. */
        public Double yOffset() { return yOffset; }

        /** Per-item horizontal offset when this element repeats inside a region. May be null. */
        public Double xOffset() { return xOffset; }

        void setOffsets(Double yOffset, Double xOffset) {
            this.yOffset = yOffset;
            this.xOffset = xOffset;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Geometric Elements
    // ─────────────────────────────────────────────────────────────────────────

    /** A line segment between two explicit endpoints. ({.line.*}) */
    public static final class LineElement extends FormElement {
        private final double x1, y1, x2, y2;
        // Stroke
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;

        public LineElement(String name, String id,
                           double x1, double y1, double x2, double y2,
                           String stroke, Double strokeWidth, Double strokeOpacity,
                           String strokeDasharray, String strokeLinecap, String strokeLinejoin) {
            super(ElementType.LINE, name, id);
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity;
            this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap;
            this.strokeLinejoin = strokeLinejoin;
        }

        public double x1() { return x1; }
        public double y1() { return y1; }
        public double x2() { return x2; }
        public double y2() { return y2; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
    }

    /** A rectangle, optionally with rounded corners. ({.rect.*}) */
    public static final class RectElement extends FormElement {
        private final double x, y, w, h;
        private final Double rx, ry;
        // Stroke
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;
        // Fill
        private final String fill;
        private final Double fillOpacity;

        public RectElement(String name, String id,
                           double x, double y, double w, double h,
                           Double rx, Double ry,
                           String stroke, Double strokeWidth, Double strokeOpacity,
                           String strokeDasharray, String strokeLinecap, String strokeLinejoin,
                           String fill, Double fillOpacity) {
            super(ElementType.RECT, name, id);
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.rx = rx; this.ry = ry;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
            this.fill = fill; this.fillOpacity = fillOpacity;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double w() { return w; }
        public double h() { return h; }
        public Double rx() { return rx; }
        public Double ry() { return ry; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
        public String fill() { return fill; }
        public Double fillOpacity() { return fillOpacity; }
    }

    /** A circle defined by a center point and radius. ({.circle.*}) */
    public static final class CircleElement extends FormElement {
        private final double cx, cy, r;
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;
        private final String fill;
        private final Double fillOpacity;

        public CircleElement(String name, String id,
                             double cx, double cy, double r,
                             String stroke, Double strokeWidth, Double strokeOpacity,
                             String strokeDasharray, String strokeLinecap, String strokeLinejoin,
                             String fill, Double fillOpacity) {
            super(ElementType.CIRCLE, name, id);
            this.cx = cx; this.cy = cy; this.r = r;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
            this.fill = fill; this.fillOpacity = fillOpacity;
        }

        public double cx() { return cx; }
        public double cy() { return cy; }
        public double r() { return r; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
        public String fill() { return fill; }
        public Double fillOpacity() { return fillOpacity; }
    }

    /** An ellipse defined by a center point and two radii. ({.ellipse.*}) */
    public static final class EllipseElement extends FormElement {
        private final double cx, cy, rx, ry;
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;
        private final String fill;
        private final Double fillOpacity;

        public EllipseElement(String name, String id,
                              double cx, double cy, double rx, double ry,
                              String stroke, Double strokeWidth, Double strokeOpacity,
                              String strokeDasharray, String strokeLinecap, String strokeLinejoin,
                              String fill, Double fillOpacity) {
            super(ElementType.ELLIPSE, name, id);
            this.cx = cx; this.cy = cy; this.rx = rx; this.ry = ry;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
            this.fill = fill; this.fillOpacity = fillOpacity;
        }

        public double cx() { return cx; }
        public double cy() { return cy; }
        public double rx() { return rx; }
        public double ry() { return ry; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
        public String fill() { return fill; }
        public Double fillOpacity() { return fillOpacity; }
    }

    /** A closed polygon defined by a list of points. ({.polygon.*}) */
    public static final class PolygonElement extends FormElement {
        private final String points;
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;
        private final String fill;
        private final Double fillOpacity;

        public PolygonElement(String name, String id, String points,
                              String stroke, Double strokeWidth, Double strokeOpacity,
                              String strokeDasharray, String strokeLinecap, String strokeLinejoin,
                              String fill, Double fillOpacity) {
            super(ElementType.POLYGON, name, id);
            this.points = points;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
            this.fill = fill; this.fillOpacity = fillOpacity;
        }

        public String points() { return points; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
        public String fill() { return fill; }
        public Double fillOpacity() { return fillOpacity; }
    }

    /** An open polyline defined by a list of points. ({.polyline.*}) */
    public static final class PolylineElement extends FormElement {
        private final String points;
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;

        public PolylineElement(String name, String id, String points,
                               String stroke, Double strokeWidth, Double strokeOpacity,
                               String strokeDasharray, String strokeLinecap, String strokeLinejoin) {
            super(ElementType.POLYLINE, name, id);
            this.points = points;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
        }

        public String points() { return points; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
    }

    /** An SVG-style arbitrary path. ({.path.*}) */
    public static final class PathElement extends FormElement {
        private final String d;
        private final String stroke;
        private final Double strokeWidth;
        private final Double strokeOpacity;
        private final String strokeDasharray;
        private final String strokeLinecap;
        private final String strokeLinejoin;
        private final String fill;
        private final Double fillOpacity;

        public PathElement(String name, String id, String d,
                           String stroke, Double strokeWidth, Double strokeOpacity,
                           String strokeDasharray, String strokeLinecap, String strokeLinejoin,
                           String fill, Double fillOpacity) {
            super(ElementType.PATH, name, id);
            this.d = d;
            this.stroke = stroke; this.strokeWidth = strokeWidth;
            this.strokeOpacity = strokeOpacity; this.strokeDasharray = strokeDasharray;
            this.strokeLinecap = strokeLinecap; this.strokeLinejoin = strokeLinejoin;
            this.fill = fill; this.fillOpacity = fillOpacity;
        }

        public String d() { return d; }
        public String stroke() { return stroke; }
        public Double strokeWidth() { return strokeWidth; }
        public Double strokeOpacity() { return strokeOpacity; }
        public String strokeDasharray() { return strokeDasharray; }
        public String strokeLinecap() { return strokeLinecap; }
        public String strokeLinejoin() { return strokeLinejoin; }
        public String fill() { return fill; }
        public Double fillOpacity() { return fillOpacity; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Content Elements
    // ─────────────────────────────────────────────────────────────────────────

    /** Static text label. ({.text.*}) */
    public static final class TextElement extends FormElement {
        private final String content;
        private final double x, y;
        private final Double rotate;
        // Font
        private final String fontFamily;
        private final Double fontSize;
        private final String fontWeight;
        private final String fontStyle;
        private final String textAlign;
        private final String color;

        public TextElement(String name, String id,
                           String content, double x, double y, Double rotate,
                           String fontFamily, Double fontSize, String fontWeight,
                           String fontStyle, String textAlign, String color) {
            super(ElementType.TEXT, name, id);
            this.content = content; this.x = x; this.y = y; this.rotate = rotate;
            this.fontFamily = fontFamily; this.fontSize = fontSize;
            this.fontWeight = fontWeight; this.fontStyle = fontStyle;
            this.textAlign = textAlign; this.color = color;
        }

        public String content() { return content; }
        public double x() { return x; }
        public double y() { return y; }
        public Double rotate() { return rotate; }
        public String fontFamily() { return fontFamily; }
        public Double fontSize() { return fontSize; }
        public String fontWeight() { return fontWeight; }
        public String fontStyle() { return fontStyle; }
        public String textAlign() { return textAlign; }
        public String color() { return color; }
    }

    /** Embedded image. ({.img.*}) */
    public static final class ImageElement extends FormElement {
        private final String src, alt;
        private final double x, y, w, h;
        private final Boolean background;

        public ImageElement(String name, String id,
                            String src, String alt,
                            double x, double y, double w, double h,
                            Boolean background) {
            super(ElementType.IMG, name, id);
            this.src = src; this.alt = alt;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.background = background;
        }

        public String src() { return src; }
        public String alt() { return alt; }
        public double x() { return x; }
        public double y() { return y; }
        public double w() { return w; }
        public double h() { return h; }
        /** When true, renders behind all other elements at the lowest z-index. May be null. */
        public Boolean background() { return background; }
    }

    /** Barcode symbology enum. */
    public enum BarcodeType {
        CODE39("code39"), CODE128("code128"), QR("qr"),
        DATAMATRIX("datamatrix"), PDF417("pdf417");

        private final String value;

        BarcodeType(String value) { this.value = value; }

        public String getValue() { return value; }

        public static BarcodeType fromString(String s) {
            for (BarcodeType t : values()) {
                if (t.value.equals(s)) return t;
            }
            return CODE128;
        }
    }

    /** 1D or 2D barcode. ({.barcode.*}) */
    public static final class BarcodeElement extends FormElement {
        private final BarcodeType barcodeType;
        private final String content, alt;
        private final double x, y, w, h;

        public BarcodeElement(String name, String id,
                              BarcodeType barcodeType, String content, String alt,
                              double x, double y, double w, double h) {
            super(ElementType.BARCODE, name, id);
            this.barcodeType = barcodeType; this.content = content; this.alt = alt;
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        public BarcodeType barcodeType() { return barcodeType; }
        public String content() { return content; }
        public String alt() { return alt; }
        public double x() { return x; }
        public double y() { return y; }
        public double w() { return w; }
        public double h() { return h; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Field Elements — shared base
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Base class for all field elements. Carries position, size, binding,
     * validation, and accessibility properties.
     */
    public abstract static sealed class BaseFieldElement extends FormElement
            permits TextFieldElement, CheckboxElement, RadioElement,
                    SelectElement, MultiselectElement, DateElement, SignatureElement {

        private final String label;
        private final String ariaLabel;
        private final double x, y, w, h;
        private final String bind;
        // Validation
        private final Boolean required;
        private final String pattern;
        private final Integer minLength;
        private final Integer maxLength;
        private final String min;
        private final String max;
        // Other
        private final Integer tabindex;
        private final Boolean readonly;

        protected BaseFieldElement(ElementType type, String name, String id,
                                   String label, String ariaLabel,
                                   double x, double y, double w, double h,
                                   String bind,
                                   Boolean required, String pattern,
                                   Integer minLength, Integer maxLength,
                                   String min, String max,
                                   Integer tabindex, Boolean readonly) {
            super(type, name, id);
            this.label = label; this.ariaLabel = ariaLabel;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.bind = bind;
            this.required = required; this.pattern = pattern;
            this.minLength = minLength; this.maxLength = maxLength;
            this.min = min; this.max = max;
            this.tabindex = tabindex; this.readonly = readonly;
        }

        public String label() { return label; }
        /** May be null; fall back to {@link #label()} for ARIA. */
        public String ariaLabel() { return ariaLabel; }
        public double x() { return x; }
        public double y() { return y; }
        public double w() { return w; }
        public double h() { return h; }
        public String bind() { return bind; }
        public Boolean required() { return required; }
        public String pattern() { return pattern; }
        public Integer minLength() { return minLength; }
        public Integer maxLength() { return maxLength; }
        public String min() { return min; }
        public String max() { return max; }
        public Integer tabindex() { return tabindex; }
        public Boolean readonly() { return readonly; }

        /** Effective ARIA label — prefers {@link #ariaLabel()} over {@link #label()}. */
        public String effectiveAriaLabel() {
            return ariaLabel != null ? ariaLabel : label;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concrete Field Types
    // ─────────────────────────────────────────────────────────────────────────

    /** Single-line or multi-line text input field. (field.text) */
    public static final class TextFieldElement extends BaseFieldElement {
        private final String value;
        private final String inputType;
        private final String mask;
        private final String placeholder;
        private final Boolean multiline;
        private final Integer maxLines;

        public TextFieldElement(String name, String id,
                                String label, String ariaLabel,
                                double x, double y, double w, double h,
                                String bind,
                                Boolean required, String pattern,
                                Integer minLength, Integer maxLength,
                                String min, String max,
                                Integer tabindex, Boolean readonly,
                                String value, String inputType,
                                String mask, String placeholder,
                                Boolean multiline, Integer maxLines) {
            super(ElementType.FIELD_TEXT, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.value = value; this.inputType = inputType;
            this.mask = mask; this.placeholder = placeholder;
            this.multiline = multiline; this.maxLines = maxLines;
        }

        /** Current inline text value. May be null. */
        public String value() { return value; }
        /** Screen rendering hint for the HTML5 input type. May be null. */
        public String inputType() { return inputType; }
        public String mask() { return mask; }
        public String placeholder() { return placeholder; }
        public Boolean multiline() { return multiline; }
        public Integer maxLines() { return maxLines; }
    }

    /** Boolean checkbox field. (field.checkbox) */
    public static final class CheckboxElement extends BaseFieldElement {
        private final Boolean checked;

        public CheckboxElement(String name, String id,
                               String label, String ariaLabel,
                               double x, double y, double w, double h,
                               String bind,
                               Boolean required, String pattern,
                               Integer minLength, Integer maxLength,
                               String min, String max,
                               Integer tabindex, Boolean readonly,
                               Boolean checked) {
            super(ElementType.FIELD_CHECKBOX, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.checked = checked;
        }

        /** Whether the checkbox is checked. Optional inline value; may be null. */
        public Boolean checked() { return checked; }
    }

    /** Radio button field — part of a mutually exclusive group. (field.radio) */
    public static final class RadioElement extends BaseFieldElement {
        private final String group;
        private final String value;

        public RadioElement(String name, String id,
                            String label, String ariaLabel,
                            double x, double y, double w, double h,
                            String bind,
                            Boolean required, String pattern,
                            Integer minLength, Integer maxLength,
                            String min, String max,
                            Integer tabindex, Boolean readonly,
                            String group, String value) {
            super(ElementType.FIELD_RADIO, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.group = group;
            this.value = value;
        }

        public String group() { return group; }
        public String value() { return value; }
    }

    /** Single-selection dropdown field. (field.select) */
    public static final class SelectElement extends BaseFieldElement {
        private final List<String> options;
        private final String selected;
        private final String placeholder;

        public SelectElement(String name, String id,
                             String label, String ariaLabel,
                             double x, double y, double w, double h,
                             String bind,
                             Boolean required, String pattern,
                             Integer minLength, Integer maxLength,
                             String min, String max,
                             Integer tabindex, Boolean readonly,
                             List<String> options, String selected, String placeholder) {
            super(ElementType.FIELD_SELECT, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.options = options != null ? List.copyOf(options) : List.of();
            this.selected = selected;
            this.placeholder = placeholder;
        }

        public List<String> options() { return options; }
        /** Currently selected option value. Optional inline value; may be null. */
        public String selected() { return selected; }
        public String placeholder() { return placeholder; }
    }

    /** Multiple-selection list field. (field.multiselect) */
    public static final class MultiselectElement extends BaseFieldElement {
        private final List<String> options;
        private final List<String> selected;
        private final Integer minSelect;
        private final Integer maxSelect;

        public MultiselectElement(String name, String id,
                                  String label, String ariaLabel,
                                  double x, double y, double w, double h,
                                  String bind,
                                  Boolean required, String pattern,
                                  Integer minLength, Integer maxLength,
                                  String min, String max,
                                  Integer tabindex, Boolean readonly,
                                  List<String> options, List<String> selected,
                                  Integer minSelect, Integer maxSelect) {
            super(ElementType.FIELD_MULTISELECT, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.options = options != null ? List.copyOf(options) : List.of();
            this.selected = selected != null ? List.copyOf(selected) : null;
            this.minSelect = minSelect;
            this.maxSelect = maxSelect;
        }

        public List<String> options() { return options; }
        /** Currently selected option values. Optional inline value; may be null. */
        public List<String> selected() { return selected; }
        public Integer minSelect() { return minSelect; }
        public Integer maxSelect() { return maxSelect; }
    }

    /** Date input field. (field.date) */
    public static final class DateElement extends BaseFieldElement {
        private final String value;

        public DateElement(String name, String id,
                           String label, String ariaLabel,
                           double x, double y, double w, double h,
                           String bind,
                           Boolean required, String pattern,
                           Integer minLength, Integer maxLength,
                           String min, String max,
                           Integer tabindex, Boolean readonly,
                           String value) {
            super(ElementType.FIELD_DATE, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.value = value;
        }

        /** Current date value as an ISO 8601 date string. Optional inline value; may be null. */
        public String value() { return value; }
    }

    /** Signature capture area. (field.signature) */
    public static final class SignatureElement extends BaseFieldElement {
        private final String value;
        private final String dateField;

        public SignatureElement(String name, String id,
                                String label, String ariaLabel,
                                double x, double y, double w, double h,
                                String bind,
                                Boolean required, String pattern,
                                Integer minLength, Integer maxLength,
                                String min, String max,
                                Integer tabindex, Boolean readonly,
                                String value, String dateField) {
            super(ElementType.FIELD_SIGNATURE, name, id,
                  label, ariaLabel, x, y, w, h, bind,
                  required, pattern, minLength, maxLength, min, max, tabindex, readonly);
            this.value = value;
            this.dateField = dateField;
        }

        /** Captured signature data as an ODIN binary literal. May be null. */
        public String value() { return value; }
        /** ODIN reference to an associated date field. May be null. */
        public String dateField() { return dateField; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Region
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A container grouping repeating content bound to an array. ({.region.*})
     *
     * Child elements repeat for each item in the bound array; when items exceed
     * {@code max}, overflow pages are generated via {@code overflow}.
     */
    public static final class RegionElement extends FormElement {
        private final double x, y, w, h;
        private final String bind;
        private final Integer max;
        private final String overflow;
        private final List<FormElement> children;

        public RegionElement(String name, String id,
                             double x, double y, double w, double h,
                             String bind, Integer max, String overflow,
                             List<FormElement> children) {
            super(ElementType.REGION, name, id);
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.bind = bind; this.max = max; this.overflow = overflow;
            this.children = children != null ? List.copyOf(children) : List.of();
        }

        public double x() { return x; }
        public double y() { return y; }
        public double w() { return w; }
        public double h() { return h; }
        /** ODIN path to the array data source (e.g. @policy.vehicles). May be null. */
        public String bind() { return bind; }
        /** Maximum items before overflow. May be null. */
        public Integer max() { return max; }
        /** {@code clone} or a template reference (e.g. @tpl_vehicles_continued). May be null. */
        public String overflow() { return overflow; }
        /** Child elements, repeated per bound item. */
        public List<FormElement> children() { return children; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Renderer Options
    // ─────────────────────────────────────────────────────────────────────────

    /** Rendering target. */
    public enum RenderTarget {
        HTML("html"), PRINT_CSS("print-css");

        private final String value;

        RenderTarget(String value) { this.value = value; }

        public String getValue() { return value; }
    }

    /**
     * Options passed to {@link FormRenderer#renderForm}.
     */
    public record RenderFormOptions(
            /**
             * Rendering target.
             * {@link RenderTarget#HTML} — interactive HTML form.
             * {@link RenderTarget#PRINT_CSS} — static HTML/CSS optimised for print/PDF.
             */
            RenderTarget target,
            /**
             * Language code for i18n label resolution (e.g. "en", "es").
             * Falls back to {@link FormMetadata#lang()} when null.
             */
            String lang,
            /**
             * Uniform scale factor applied to all page dimensions.
             * Falls back to {@link ScreenSettings#scale()} (or 1.0) when null.
             */
            Double scale,
            /**
             * Additional CSS class name(s) added to the root rendered element.
             * May be null.
             */
            String className
    ) {
        /** Convenience factory with just a target. */
        public static RenderFormOptions of(RenderTarget target) {
            return new RenderFormOptions(target, null, null, null);
        }
    }
}
