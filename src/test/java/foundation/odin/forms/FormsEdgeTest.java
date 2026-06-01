package foundation.odin.forms;

import foundation.odin.Odin;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Edge-case Forms behavior: overflow, margins, fallbacks, i18n, escaping. */
class FormsEdgeTest {

    private static final String OVERFLOW_FORM = """
            {$}
            odin = "1.0.0"
            forms = "1.0.0"
            title = "Overflow"
            id = "of"
            lang = "en"

            {$.page}
            width = #8.5
            height = #11
            unit = "inch"

            {page[0]}
            {.text.header}
            x = #0.5
            y = #0.5
            content = "Page {@odin.page} of {@odin.total_pages}"

            {.region.vehicles}
            x = #0.5
            y = #1.2
            w = #7.5
            h = #6
            bind = @policy.vehicles
            max = ##3
            overflow = @tpl_more

            {.region.vehicles.field.vin}
            x = #0
            y = #0.15
            y-offset = #1.8
            w = #4
            h = #0.3
            label = "VIN"
            bind = @.vin

            {@tpl_more}
            page-template = ?true
            continues = "region.vehicles"
            form-id = "PA (Cont)"

            {.text.header}
            x = #0.5
            y = #0.5
            content = "More — Page {@odin.page} of {@odin.total_pages}"

            {.region.vehicles}
            x = #0.5
            y = #1
            w = #7.5
            h = #8
            max = ##4
            overflow = @tpl_more

            {.region.vehicles.field.vin}
            x = #0
            y = #0.15
            y-offset = #1.2
            w = #4
            h = #0.3
            label = "VIN"
            bind = @.vin
            """;

    @Test
    void regionOverflowSpillsToSecondPage() {
        OdinForm form = FormParser.parseForm(OVERFLOW_FORM);
        OdinDocument data = Odin.parse("""
                {policy}
                {.vehicles[0]}
                vin = "V0"
                {.vehicles[1]}
                vin = "V1"
                {.vehicles[2]}
                vin = "V2"
                {.vehicles[3]}
                vin = "V3"
                {.vehicles[4]}
                vin = "V4"
                """);
        String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));

        // 5 items, max 3 -> overflow to a second page; total_pages reflects it.
        assertTrue(html.contains("data-page=\"1\""), "page 1 present");
        assertTrue(html.contains("data-page=\"2\""), "page 2 present");
        assertTrue(html.contains("Page 1 of 2"), "interpolated page 1 of 2");
        assertTrue(html.contains("Page 2 of 2"), "interpolated page 2 of 2");
        // First page holds V0..V2; overflow page holds V3..V4.
        assertTrue(html.contains("value=\"V0\""));
        assertTrue(html.contains("value=\"V3\""));
        assertTrue(html.contains("value=\"V4\""));
        // Interpolation tokens fully resolved.
        assertFalse(html.contains("{@odin.page}"));
        assertFalse(html.contains("{@odin.total_pages}"));
    }

    @Test
    void perSideMarginsParse() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Margins"
                id = "m"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"
                margin.top = #0.5
                margin.right = #0.25
                margin.bottom = #0.6
                margin.left = #0.75

                {page[0]}
                {.text.t}
                x = #1
                y = #1
                content = "x"
                """;
        OdinForm form = FormParser.parseForm(formText);
        PageMargins m = form.pageDefaults().margin();
        assertNotNull(m);
        assertEquals(0.5, m.top(), 1e-9);
        assertEquals(0.25, m.right(), 1e-9);
        assertEquals(0.6, m.bottom(), 1e-9);
        assertEquals(0.75, m.left(), 1e-9);
    }

    @Test
    void barcodeTypeFallbackToBarcodeTypeKey() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Barcode"
                id = "bc"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.barcode.doc}
                x = #1
                y = #1
                w = #1
                h = #1
                barcode-type = "pdf417"
                content = "DATA"
                alt = "tracking"
                """;
        OdinForm form = FormParser.parseForm(formText);
        BarcodeElement bc = (BarcodeElement) form.pages().get(0).elements().get(0);
        assertEquals(BarcodeType.PDF417, bc.barcodeType());
    }

    @Test
    void i18nLabelReferenceResolves() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "I18n"
                id = "i18n"
                lang = "en"

                {$.i18n}
                en.field_name = "Full Legal Name"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.field.name}
                type = "text"
                x = #0.6
                y = #1.5
                w = #3.5
                h = #0.3
                label = @$.i18n.en.field_name
                bind = @insured.name
                """;
        OdinForm form = FormParser.parseForm(formText);
        TextFieldElement name = (TextFieldElement) form.pages().get(0).elements().get(0);
        assertEquals("Full Legal Name", name.label());
    }

    @Test
    void attributeEscapingHandlesQuotesAndSpecials() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Escape"
                id = "esc"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.field.weird}
                type = "text"
                x = #1
                y = #1
                w = #2
                h = #0.3
                label = "Label"
                value = "a'b\\"c<d&e"
                bind = @x.y
                """;
        OdinForm form = FormParser.parseForm(formText);
        String html = FormRenderer.renderForm(form, null, RenderFormOptions.of(RenderTarget.HTML));
        // Single quote, double quote, < and & all escaped in the value attribute.
        assertTrue(html.contains("value=\"a&#39;b&quot;c&lt;d&amp;e\""),
                "attribute escaping; got: " + extractValueAttr(html));
    }

    private static String extractValueAttr(String html) {
        int i = html.indexOf("value=\"");
        if (i < 0) return "<none>";
        int j = html.indexOf('"', i + 7);
        return html.substring(i, j + 1);
    }
}
