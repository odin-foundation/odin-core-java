package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundation.odin.parsing.OdinParser;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;
import foundation.odin.types.OdinErrors;
import foundation.odin.types.OdinOptions.ParseOptions;
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
public class GoldenParseTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> parseGoldenTests() throws IOException {
        Path goldenDir = findGoldenDir();
        Path parseDir = goldenDir.resolve("parse");
        if (!Files.isDirectory(parseDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(parseDir)) {
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

                    tests.add(DynamicTest.dynamicTest(displayName, () -> runParseTest(test)));
                }
            }
        }
        return tests.stream();
    }

    private void runParseTest(JsonObject test) {
        String input = test.has("input") ? test.get("input").getAsString() : null;
        JsonObject expected = test.has("expected") ? test.getAsJsonObject("expected") : null;
        JsonObject expectError = test.has("expectError") ? test.getAsJsonObject("expectError") : null;

        if (expectError != null) {
            String expectedCode = expectError.has("code") ? expectError.get("code").getAsString() : null;
            try {
                OdinParser.parse(input, ParseOptions.DEFAULT);
                fail("Expected parse error with code " + expectedCode + " but parsing succeeded");
            } catch (OdinErrors.OdinParseException e) {
                if (expectedCode != null) {
                    assertEquals(expectedCode, e.getCode(),
                            "Expected error code " + expectedCode + " but got " + e.getCode());
                }
            } catch (Exception e) {
                if (expectedCode != null) {
                    assertTrue(e.getMessage() != null,
                            "Expected error code " + expectedCode + " but got: " + e.getClass().getSimpleName());
                }
            }
            return;
        }

        if (expected == null) return;

        assertNotNull(input, "Test input is null");
        OdinDocument doc = OdinParser.parse(input, ParseOptions.DEFAULT);
        assertNotNull(doc, "Parsed document is null");

        if (expected.has("assignments")) {
            JsonObject assignments = expected.getAsJsonObject("assignments");
            for (String path : assignments.keySet()) {
                JsonObject expectedVal = assignments.getAsJsonObject(path);
                OdinValue actual = doc.get(path);
                assertNotNull(actual, "Expected path '" + path + "' not found in document");

                if (expectedVal.has("type")) {
                    String expectedType = expectedVal.get("type").getAsString();
                    assertTypeMatch(actual, expectedType, path);
                }

                if (expectedVal.has("value")) {
                    assertValueMatch(actual, expectedVal, path);
                }
            }
        }

        if (expected.has("metadata")) {
            JsonObject meta = expected.getAsJsonObject("metadata");
            for (String key : meta.keySet()) {
                OdinValue metaVal = doc.getMetadata().tryGet(key);
                assertNotNull(metaVal, "Expected metadata key '" + key + "' not found");
            }
        }
    }

    private void assertTypeMatch(OdinValue actual, String expectedType, String path) {
        String actualType = switch (actual) {
            case OdinValue.OdinNull n -> "null";
            case OdinValue.OdinBoolean b -> "boolean";
            case OdinValue.OdinString s -> "string";
            case OdinValue.OdinNumber n -> "number";
            case OdinValue.OdinInteger i -> "integer";
            case OdinValue.OdinCurrency c -> "currency";
            case OdinValue.OdinPercent p -> "percent";
            case OdinValue.OdinDate d -> "date";
            case OdinValue.OdinTimestamp t -> "timestamp";
            case OdinValue.OdinTime t -> "time";
            case OdinValue.OdinDuration d -> "duration";
            case OdinValue.OdinReference r -> "reference";
            case OdinValue.OdinBinary b -> "binary";
            case OdinValue.OdinVerb v -> "verb";
            case OdinValue.OdinArray a -> "array";
            case OdinValue.OdinObject o -> "object";
        };

        assertEquals(expectedType, actualType,
                "Type mismatch at path '" + path + "': expected " + expectedType + " but got " + actualType);
    }

    private void assertValueMatch(OdinValue actual, JsonObject expectedVal, String path) {
        JsonElement valueEl = expectedVal.get("value");

        switch (actual) {
            case OdinValue.OdinString s ->
                assertEquals(valueEl.getAsString(), s.getValue(), "String mismatch at '" + path + "'");
            case OdinValue.OdinBoolean b ->
                assertEquals(valueEl.getAsBoolean(), b.getValue(), "Boolean mismatch at '" + path + "'");
            case OdinValue.OdinInteger i ->
                assertEquals(valueEl.getAsLong(), i.getValue(), "Integer mismatch at '" + path + "'");
            case OdinValue.OdinNumber n ->
                assertEquals(valueEl.getAsDouble(), n.getValue(), 1e-10, "Number mismatch at '" + path + "'");
            case OdinValue.OdinCurrency c ->
                assertEquals(valueEl.getAsDouble(), c.getValue(), 1e-10, "Currency mismatch at '" + path + "'");
            case OdinValue.OdinPercent p ->
                assertEquals(valueEl.getAsDouble(), p.getValue(), 1e-10, "Percent mismatch at '" + path + "'");
            case OdinValue.OdinReference r ->
                assertEquals(valueEl.getAsString(), r.getPath(), "Reference mismatch at '" + path + "'");
            case OdinValue.OdinNull n ->
                assertTrue(valueEl.isJsonNull() || "null".equals(valueEl.getAsString()),
                        "Expected null at '" + path + "'");
            case OdinValue.OdinDate d -> {
                if (expectedVal.has("raw")) assertEquals(expectedVal.get("raw").getAsString(), d.getRaw());
            }
            case OdinValue.OdinTimestamp t -> {
                if (expectedVal.has("raw")) assertEquals(expectedVal.get("raw").getAsString(), t.getRaw());
            }
            case OdinValue.OdinBinary b -> {
                if (valueEl.isJsonPrimitive()) {
                    assertEquals(valueEl.getAsString(),
                            java.util.Base64.getEncoder().encodeToString(b.getData()));
                }
            }
            default -> { /* other types not checked for value */ }
        }

        if (expectedVal.has("modifiers")) {
            JsonObject mods = expectedVal.getAsJsonObject("modifiers");
            var actualMods = actual.getModifiers();
            if (mods.has("required") && mods.get("required").getAsBoolean()) {
                assertNotNull(actualMods, "Expected required modifier at '" + path + "'");
                assertTrue(actualMods.isRequired(), "Expected required modifier at '" + path + "'");
            }
            if (mods.has("confidential") && mods.get("confidential").getAsBoolean()) {
                assertNotNull(actualMods, "Expected confidential modifier at '" + path + "'");
                assertTrue(actualMods.isConfidential(), "Expected confidential modifier at '" + path + "'");
            }
            if (mods.has("deprecated") && mods.get("deprecated").getAsBoolean()) {
                assertNotNull(actualMods, "Expected deprecated modifier at '" + path + "'");
                assertTrue(actualMods.isDeprecated(), "Expected deprecated modifier at '" + path + "'");
            }
        }
    }

    static Path findGoldenDir() {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        Path golden = cwd.resolve("../golden").normalize();
        if (Files.isDirectory(golden)) return golden;
        golden = cwd.resolve("../../golden").normalize();
        if (Files.isDirectory(golden)) return golden;
        golden = Paths.get("C:/dev/odin/sdk/golden");
        if (Files.isDirectory(golden)) return golden;
        throw new RuntimeException("Cannot find sdk/golden/ directory from " + cwd);
    }
}
