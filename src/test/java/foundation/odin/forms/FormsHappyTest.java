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
}
