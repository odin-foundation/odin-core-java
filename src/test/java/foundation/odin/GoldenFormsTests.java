package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundation.odin.forms.FormParser;
import foundation.odin.forms.FormRenderer;
import foundation.odin.forms.FormTypes.*;
import foundation.odin.types.OdinDocument;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden tests for ODIN Forms.
 *
 * Loads the shared forms manifest and exercises parse + render conformance.
 * This is the cross-language contract for Forms.
 */
@Tag("golden")
public class GoldenFormsTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> formsGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path formsDir = goldenDir.resolve("forms");
        Path manifestPath = formsDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            return Stream.empty();
        }

        String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
        JsonObject suite = GSON.fromJson(content, JsonObject.class);
        JsonArray testArray = suite.getAsJsonArray("tests");
        if (testArray == null) return Stream.empty();

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement testEl : testArray) {
            JsonObject test = testEl.getAsJsonObject();
            String id = test.has("id") ? test.get("id").getAsString() : "unknown";
            String desc = test.has("description") ? test.get("description").getAsString() : id;
            tests.add(DynamicTest.dynamicTest(id + " / " + desc, () -> runFormsTest(formsDir, test)));
        }
        return tests.stream();
    }

    private void runFormsTest(Path formsDir, JsonObject test) throws IOException {
        String formText = Files.readString(formsDir.resolve(test.get("formFile").getAsString()),
                StandardCharsets.UTF_8);
        OdinForm form = FormParser.parseForm(formText);

        if (test.has("expectParse")) {
            checkExpectParse(form, test.getAsJsonObject("expectParse"));
        }

        if (test.has("renderContains") || test.has("renderNotContains")) {
            OdinDocument data = test.has("renderData")
                    ? Odin.parse(test.get("renderData").getAsString()) : null;
            String html = FormRenderer.renderForm(form, data, RenderFormOptions.of(RenderTarget.HTML));

            if (test.has("renderContains")) {
                for (JsonElement needle : test.getAsJsonArray("renderContains")) {
                    String n = needle.getAsString();
                    assertTrue(html.contains(n), "Expected render to contain: " + n);
                }
            }
            if (test.has("renderNotContains")) {
                for (JsonElement needle : test.getAsJsonArray("renderNotContains")) {
                    String n = needle.getAsString();
                    assertFalse(html.contains(n), "Expected render NOT to contain: " + n);
                }
            }
        }
    }

    private void checkExpectParse(OdinForm form, JsonObject ep) {
        if (ep.has("pages")) {
            assertEquals(ep.get("pages").getAsInt(), form.pages().size(), "page count");
        }

        if (ep.has("margins")) {
            JsonObject margins = ep.getAsJsonObject("margins");
            PageMargins m = form.pageDefaults() != null ? form.pageDefaults().margin() : null;
            assertNotNull(m, "margins missing");
            for (String side : margins.keySet()) {
                double expected = margins.get(side).getAsDouble();
                Double actual = switch (side) {
                    case "top" -> m.top();
                    case "right" -> m.right();
                    case "bottom" -> m.bottom();
                    case "left" -> m.left();
                    default -> null;
                };
                assertNotNull(actual, "margin." + side + " missing");
                assertEquals(expected, actual, 1e-9, "margin." + side);
            }
        }

        if (ep.has("templates")) {
            JsonObject templates = ep.getAsJsonObject("templates");
            for (String name : templates.keySet()) {
                JsonObject t = templates.getAsJsonObject(name);
                PageTemplate tpl = form.templates() != null ? form.templates().get(name) : null;
                assertNotNull(tpl, "template " + name + " missing");
                if (t.has("pageTemplate")) {
                    assertEquals(t.get("pageTemplate").getAsBoolean(), tpl.pageTemplate(), "pageTemplate");
                }
                if (t.has("continues")) {
                    assertEquals(t.get("continues").getAsString(), tpl.continues(), "continues");
                }
                if (t.has("formId")) {
                    assertEquals(t.get("formId").getAsString(), tpl.formId(), "formId");
                }
                if (t.has("elementTypes")) {
                    assertEquals(typeNames(t.getAsJsonArray("elementTypes")),
                            tpl.elements().stream().map(e -> e.type().getValue()).toList(),
                            "template elementTypes");
                }
            }
        }

        if (ep.has("page0")) {
            JsonObject page0 = ep.getAsJsonObject("page0");
            FormPage p = form.pages().get(0);
            if (page0.has("elementTypes")) {
                assertEquals(typeNames(page0.getAsJsonArray("elementTypes")),
                        p.elements().stream().map(e -> e.type().getValue()).toList(),
                        "page0 elementTypes");
            }
            if (page0.has("elements")) {
                JsonObject elements = page0.getAsJsonObject("elements");
                for (String elName : elements.keySet()) {
                    FormElement el = findElement(p.elements(), elName);
                    checkElement(el, elements.getAsJsonObject(elName));
                }
            }
        }
    }

    private void checkElement(FormElement el, JsonObject exp) {
        assertNotNull(el, "element not found");
        if (exp.has("type")) {
            assertEquals(exp.get("type").getAsString(), el.type().getValue(), "type");
        }
        if (exp.has("bind")) {
            assertEquals(exp.get("bind").getAsString(), bindOf(el), "bind");
        }
        if (exp.has("max") && el instanceof RegionElement) {
            assertEquals(exp.get("max").getAsInt(), (int) ((RegionElement) el).max(), "max");
        }
        if (exp.has("overflow")) {
            assertEquals(exp.get("overflow").getAsString(), ((RegionElement) el).overflow(), "overflow");
        }
        if (exp.has("childCount")) {
            assertEquals(exp.get("childCount").getAsInt(), ((RegionElement) el).children().size(), "childCount");
        }
        if (exp.has("value")) {
            assertEquals(exp.get("value").getAsString(), valueOf(el), "value");
        }
        if (exp.has("inputType")) {
            assertEquals(exp.get("inputType").getAsString(), ((TextFieldElement) el).inputType(), "inputType");
        }
        if (exp.has("checked")) {
            assertEquals(exp.get("checked").getAsBoolean(), ((CheckboxElement) el).checked(), "checked");
        }
        if (exp.has("selected")) {
            assertEquals(exp.get("selected").getAsString(), ((SelectElement) el).selected(), "selected");
        }
        if (exp.has("options")) {
            assertEquals(stringList(exp.getAsJsonArray("options")),
                    ((SelectElement) el).options(), "options");
        }
        if (exp.has("min")) {
            assertEquals(exp.get("min").getAsString(), ((BaseFieldElement) el).min(), "min");
        }
        if (exp.has("max") && el instanceof BaseFieldElement bf) {
            assertEquals(exp.get("max").getAsString(), bf.max(), "max");
        }
        if (exp.has("label")) {
            assertEquals(exp.get("label").getAsString(), ((BaseFieldElement) el).label(), "label");
        }
        if (exp.has("barcodeType")) {
            assertEquals(exp.get("barcodeType").getAsString(),
                    ((BarcodeElement) el).barcodeType().getValue(), "barcodeType");
        }
        if (exp.has("background")) {
            assertEquals(exp.get("background").getAsBoolean(), ((ImageElement) el).background(), "background");
        }
    }

    private static String bindOf(FormElement el) {
        if (el instanceof RegionElement r) return r.bind();
        if (el instanceof BaseFieldElement f) return f.bind();
        return null;
    }

    private static String valueOf(FormElement el) {
        if (el instanceof TextFieldElement t) return t.value();
        if (el instanceof DateElement d) return d.value();
        if (el instanceof RadioElement r) return r.value();
        if (el instanceof SignatureElement s) return s.value();
        return null;
    }

    private static FormElement findElement(List<FormElement> elements, String name) {
        for (FormElement e : elements) {
            if (e.name().equals(name)) return e;
        }
        return null;
    }

    private static List<String> typeNames(JsonArray arr) {
        return stringList(arr);
    }

    private static List<String> stringList(JsonArray arr) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr) out.add(e.getAsString());
        return out;
    }
}
