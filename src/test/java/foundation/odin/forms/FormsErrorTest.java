package foundation.odin.forms;

import foundation.odin.Odin;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Error / degraded-input Forms behavior — graceful handling without crashing. */
class FormsErrorTest {

    @Test
    void regionBoundToMissingArrayRendersEmptyPreviewWithoutOverflow() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Missing"
                id = "miss"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.region.vehicles}
                x = #0.5
                y = #1
                w = #7
                h = #6
                bind = @policy.vehicles
                max = ##3
                overflow = @tpl_more

                {.region.vehicles.field.vin}
                x = #0
                y = #0.15
                w = #4
                h = #0.3
                label = "VIN"
                bind = @.vin
                """;
        OdinForm form = FormParser.parseForm(formText);

        // Data has no `policy.vehicles` array at all.
        OdinDocument data = Odin.parse("""
                {policy}
                other = "x"
                """);

        String html = assertDoesNotThrow(
                () -> FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML)));
        // Empty bound array -> single preview instance, no overflow page.
        assertTrue(html.contains("data-page=\"1\""));
        assertFalse(html.contains("data-page=\"2\""), "no overflow for empty array");
        assertTrue(html.contains("odin-form-region"), "region still rendered");
    }

    @Test
    void emptyBoundArrayProducesNoOverflow() {
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "Empty"
                id = "empty"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.region.items}
                x = #0.5
                y = #1
                w = #7
                h = #6
                bind = @data.items
                max = ##2
                overflow = "clone"

                {.region.items.field.v}
                x = #0
                y = #0
                w = #4
                h = #0.3
                label = "V"
                bind = @.v
                """;
        OdinForm form = FormParser.parseForm(formText);
        OdinDocument data = Odin.parse("""
                {data}
                placeholder = "none"
                """);
        String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));
        assertFalse(html.contains("data-page=\"2\""), "empty array: no overflow");
    }

    @Test
    void malformedTemplateReferenceDoesNotCrash() {
        // overflow points at a template that does not exist.
        String formText = """
                {$}
                odin = "1.0.0"
                forms = "1.0.0"
                title = "BadRef"
                id = "bad"
                lang = "en"

                {$.page}
                width = #8.5
                height = #11
                unit = "inch"

                {page[0]}
                {.region.vehicles}
                x = #0.5
                y = #1
                w = #7
                h = #6
                bind = @policy.vehicles
                max = ##2
                overflow = @tpl_does_not_exist

                {.region.vehicles.field.vin}
                x = #0
                y = #0.15
                y-offset = #1
                w = #4
                h = #0.3
                label = "VIN"
                bind = @.vin
                """;
        OdinForm form = FormParser.parseForm(formText);
        // No template named tpl_does_not_exist was defined.
        assertTrue(form.templates() == null || !form.templates().containsKey("tpl_does_not_exist"));

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
                """);
        // Overflow with a missing template falls back to cloning the page; must not throw.
        String html = assertDoesNotThrow(
                () -> FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML)));
        assertTrue(html.contains("data-page=\"2\""), "fallback clone page generated");
        assertTrue(html.contains("value=\"V0\""));
        assertTrue(html.contains("value=\"V3\""));
    }
}
