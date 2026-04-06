package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinSchema;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("golden")
public class GoldenValidateTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> validateGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path validateDir = goldenDir.resolve("validate");
        if (!Files.isDirectory(validateDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(validateDir)) {
            var jsonFiles = walk
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                    .sorted()
                    .toList();

            for (Path jsonFile : jsonFiles) {
                String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
                JsonObject suite = GSON.fromJson(content, JsonObject.class);
                String suiteName = suite.has("suite") ? suite.get("suite").getAsString() : jsonFile.getFileName().toString();
                JsonArray testArray = suite.getAsJsonArray("tests");
                if (testArray == null) continue;

                for (JsonElement testEl : testArray) {
                    JsonObject test = testEl.getAsJsonObject();
                    String testId = test.has("id") ? test.get("id").getAsString() : "unknown";
                    String displayName = suiteName + " / " + testId;
                    tests.add(DynamicTest.dynamicTest(displayName, () -> runValidateTest(test)));
                }
            }
        }
        return tests.stream();
    }

    private void runValidateTest(JsonObject test) {
        String schemaText = test.has("schema") ? test.get("schema").getAsString() : null;
        String inputText = test.has("input") ? test.get("input").getAsString() : null;
        JsonObject expected = test.has("expected") ? test.getAsJsonObject("expected") : null;

        if (schemaText == null || inputText == null || expected == null) return;

        try {
            OdinSchema.SchemaDefinition schema = Odin.parseSchema(schemaText);
            OdinDocument doc;
            if (inputText.trim().isEmpty()) {
                doc = OdinDocument.empty();
            } else {
                doc = Odin.parse(inputText);
            }
            var opts = new foundation.odin.types.OdinOptions.ValidateOptions();
            if (test.has("options")) {
                JsonObject optObj = test.getAsJsonObject("options");
                if (optObj.has("strict")) opts = opts.setStrict(optObj.get("strict").getAsBoolean());
            }
            OdinSchema.ValidationResult result = Odin.validate(doc, schema, opts);

            boolean expectedValid = expected.has("valid") && expected.get("valid").getAsBoolean();
            String testId = test.has("id") ? test.get("id").getAsString() : "unknown";
            assertEquals(expectedValid, result.valid(),
                    "Validation result mismatch for '" + testId + "': expected valid=" + expectedValid
                    + (result.errors().isEmpty() ? "" : ", errors=" + result.errors()));

            if (expected.has("errors") && expected.get("errors").isJsonArray()) {
                JsonArray expectedErrors = expected.getAsJsonArray("errors");
                assertNotNull(result.errors(), "Expected errors but got none");
                assertEquals(expectedErrors.size(), result.errors().size(),
                        "Error count mismatch");

                for (int i = 0; i < expectedErrors.size(); i++) {
                    JsonObject expectedErr = expectedErrors.get(i).getAsJsonObject();
                    var actualErr = result.errors().get(i);

                    if (expectedErr.has("code")) {
                        assertEquals(expectedErr.get("code").getAsString(), actualErr.code(),
                                "Error code mismatch at index " + i);
                    }
                    if (expectedErr.has("path")) {
                        assertEquals(expectedErr.get("path").getAsString(), actualErr.path(),
                                "Error path mismatch at index " + i);
                    }
                }
            }
        } catch (Exception e) {
            fail("Validate test threw exception: " + e.getMessage());
        }
    }
}
