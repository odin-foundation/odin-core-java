package foundation.odin.forms;

import foundation.odin.Odin;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Happy-path Forms parse + render behavior. */
class FormsHappyTest {

    private static final String PAGE_TEMPLATE_FORM = """
            {$}
            odin = "1.0.0"
            forms = "1.0.0"
            title = "PA Form"
            id = "pa_form"
            lang = "en"

            {$.page}
            width = #8.5
            height = #11
            unit = "inch"

            {page[0]}
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
    void pageTemplateFormRendersRegionWithBoundRepetition() {
        OdinForm form = FormParser.parseForm(PAGE_TEMPLATE_FORM);

        // Region parsed with binding, max, overflow and one child.
        FormElement el = form.pages().get(0).elements().get(0);
        assertInstanceOf(RegionElement.class, el);
        RegionElement region = (RegionElement) el;
        assertEquals("@policy.vehicles", region.bind());
        assertEquals(3, region.max());
        assertEquals("@tpl_more", region.overflow());
        assertEquals(1, region.children().size());

        // Three bound items render three VIN inputs (within max, no overflow).
        OdinDocument data = Odin.parse("""
                {policy}
                {.vehicles[0]}
                vin = "V0"
                {.vehicles[1]}
                vin = "V1"
                {.vehicles[2]}
                vin = "V2"
                """);
        String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("value=\"V0\""), "V0 rendered");
        assertTrue(html.contains("value=\"V1\""), "V1 rendered");
        assertTrue(html.contains("value=\"V2\""), "V2 rendered");
        // No overflow: single page.
        assertTrue(html.contains("data-page=\"1\""));
        assertFalse(html.contains("data-page=\"2\""));
    }

    @Test
    void inlineValuesTakePrecedenceOverBoundValues() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Inline"
                id = "inline"
                lang = "en"

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
                label = "Name"
                value = "Inline Wins"
                bind = @insured.name
                """;
        OdinForm form = FormParser.parseForm(formText);
        OdinDocument data = Odin.parse("""
                {insured}
                name = "Bound Loses"
                """);
        String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("value=\"Inline Wins\""), "inline value wins");
        assertFalse(html.contains("Bound Loses"), "bound value suppressed");
    }

    @Test
    void selectRendersItsOptions() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Select"
                id = "sel"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.field.state}
                type = "select"
                x = #4
                y = #3
                w = #1.5
                h = #0.3
                label = "State"
                selected = "TX"
                bind = @insured.state

                {.field.state.options[] : ~}
                "AL"
                "CA"
                "NY"
                "TX"
                """;
        OdinForm form = FormParser.parseForm(formText);
        SelectElement select = (SelectElement) form.pages().get(0).elements().get(0);
        assertEquals(java.util.List.of("AL", "CA", "NY", "TX"), select.options());
        assertEquals("TX", select.selected());

        String html = FormRenderer.renderForm(form, null, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("<option value=\"AL\">AL</option>"));
        assertTrue(html.contains("<option value=\"TX\" selected>TX</option>"), "selected option marked");
    }

    @Test
    void radioGroupChecksBoundOption() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Radio"
                id = "rad"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.field.gender_m}
                type = "radio"
                x = #0.6
                y = #2
                w = #0.2
                h = #0.2
                label = "Male"
                group = "gender"
                value = "M"
                bind = @applicant.gender

                {.field.gender_f}
                type = "radio"
                x = #1.6
                y = #2
                w = #0.2
                h = #0.2
                label = "Female"
                group = "gender"
                value = "F"
                bind = @applicant.gender
                """;
        OdinForm form = FormParser.parseForm(formText);
        RadioElement m = (RadioElement) form.pages().get(0).elements().get(0);
        assertEquals("gender", m.group());
        assertEquals("M", m.value());

        OdinDocument data = Odin.parse("""
                {applicant}
                gender = "F"
                """);
        String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("name=\"gender\" value=\"F\" aria-label=\"Female\" checked>"), "bound option checked");
        assertFalse(html.contains("name=\"gender\" value=\"M\" aria-label=\"Male\" checked"), "unbound option unchecked");
    }

    @Test
    void signatureParsesValueAndRendersCaptureArea() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Signature"
                id = "sig"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.field.applicant_sig}
                type = "signature"
                x = #0.6
                y = #8
                w = #3
                h = #0.6
                label = "Applicant Signature"
                required = ?true
                value = ^png:iVBORw0KGgo=
                bind = @applicant.signature
                """;
        OdinForm form = FormParser.parseForm(formText);
        SignatureElement sig = (SignatureElement) form.pages().get(0).elements().get(0);
        assertEquals("^png:iVBORw0KGgo=", sig.value());

        String html = FormRenderer.renderForm(form, null, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("<div class=\"odin-form-signature\" id=\"odin-field-0-applicant_sig\""
                + " aria-label=\"Applicant Signature\" aria-required=\"true\" role=\"img\" tabindex=\"0\""),
                "signature capture div with ARIA");
    }

    @Test
    void geometricElementsRenderAsSvg() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Geometric"
                id = "geo"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.circle.seal}
                cx = #2
                cy = #2
                r = #0.75
                stroke = "#003366"
                stroke-width = #0.02
                fill = "#e6f0ff"

                {.ellipse.stamp}
                cx = #5
                cy = #2
                rx = #1
                ry = #0.5
                stroke = "#660000"
                stroke-width = #0.02
                fill = "none"

                {.polyline.trend}
                points = "3,4 3.5,4.6 4,4.2 4.5,5 5,4.3"
                stroke = "#006600"
                stroke-width = #0.02
                """;
        OdinForm form = FormParser.parseForm(formText);
        assertInstanceOf(CircleElement.class, form.pages().get(0).elements().get(0));
        assertInstanceOf(EllipseElement.class, form.pages().get(0).elements().get(1));
        assertInstanceOf(PolylineElement.class, form.pages().get(0).elements().get(2));

        String html = FormRenderer.renderForm(form, null, RenderFormOptions.of(RenderTarget.HTML));
        assertTrue(html.contains("<circle cx=\"192\" cy=\"192\" r=\"72\" stroke=\"#003366\""), "circle coords");
        assertTrue(html.contains("<ellipse cx=\"480\" cy=\"192\" rx=\"96\" ry=\"48\" stroke=\"#660000\""), "ellipse coords");
        assertTrue(html.contains(
                "<polyline points=\"288,384 336,441.6 384,403.2 432,480 480,412.8\""
                + " stroke=\"#006600\" stroke-width=\"1.92\" fill=\"none\"/>"), "polyline points");
    }
}
