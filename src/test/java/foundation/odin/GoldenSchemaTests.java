package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
public class GoldenSchemaTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> schemaGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path schemaDir = goldenDir.resolve("schema");
        if (!Files.isDirectory(schemaDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(schemaDir)) {
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
                    tests.add(DynamicTest.dynamicTest(displayName, () -> runSchemaTest(test)));
                }
            }
        }
        return tests.stream();
    }

    private void runSchemaTest(JsonObject test) {
        String schemaText = test.has("schema") ? test.get("schema").getAsString() : null;
        JsonObject expected = test.has("expected") ? test.getAsJsonObject("expected") : null;
        JsonObject expectError = test.has("expectError") ? test.getAsJsonObject("expectError") : null;

        if (schemaText == null) return;

        if (expectError != null) {
            try {
                Odin.parseSchema(schemaText);
                fail("Expected schema parse error but parsing succeeded");
            } catch (Exception e) {
                // Expected
            }
            return;
        }

        if (expected == null) return;

        boolean structural = test.has("structural") && test.get("structural").getAsBoolean();

        try {
            OdinSchema.SchemaDefinition schema = Odin.parseSchema(schemaText);
            assertNotNull(schema, "Schema parsed to null");

            if (expected.has("types")) {
                JsonObject expectedTypes = expected.getAsJsonObject("types");
                for (String typeName : expectedTypes.keySet()) {
                    var typeDef = schema.types().get(typeName);
                    assertNotNull(typeDef, "Expected type '" + typeName + "' not found in schema");
                    if (structural) {
                        JsonObject typeSpec = expectedTypes.getAsJsonObject(typeName);
                        if (typeSpec.has("fields")) {
                            var fieldNames = typeDef.fields().stream()
                                    .map(OdinSchema.SchemaField::name).collect(Collectors.toSet());
                            for (String key : typeSpec.getAsJsonObject("fields").keySet()) {
                                assertTrue(fieldNames.contains(key),
                                        "Expected type '" + typeName + "' to contain field '" + key + "', got " + fieldNames);
                            }
                        }
                    }
                }
            }

            if (expected.has("fields")) {
                JsonObject expectedFields = expected.getAsJsonObject("fields");
                for (String fieldName : expectedFields.keySet()) {
                    var fieldDef = schema.fields().get(fieldName);
                    assertNotNull(fieldDef, "Expected field '" + fieldName + "' not found in schema");
                }
            }
        } catch (Exception e) {
            fail("Schema test threw exception: " + e.getMessage());
        }
    }
}
