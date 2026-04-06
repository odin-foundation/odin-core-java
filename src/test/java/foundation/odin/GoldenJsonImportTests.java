package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import foundation.odin.transform.TransformEngine;
import foundation.odin.transform.TransformParser;
import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON Import Validation Tests
 *
 * Validates that the transform framework correctly parses and handles
 * all JSON input types. Mirrors the TypeScript json-import.test.ts tests.
 */
@Tag("golden")
public class GoldenJsonImportTests {

    // ── Helpers ──

    private static String createFieldTransform(String fieldPath) {
        return "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\nresult = \"@." + fieldPath + "\"\n";
    }

    private static TransformResult exec(String transformText, DynValue input) {
        var transform = TransformParser.parse(transformText);
        return TransformEngine.execute(transform, input);
    }

    private static DynValue jsonToDyn(String json) {
        JsonElement el = JsonParser.parseString(json);
        return DynValue.fromJsonElement(el);
    }

    private static DynValue getOutputValue(TransformResult result) {
        assertNotNull(result.getOutput(), "output should not be null");
        var output = result.getOutput().get("output");
        assertNotNull(output, "output segment should exist");
        return output.get("result");
    }

    private static String getOutputType(TransformResult result) {
        DynValue val = getOutputValue(result);
        assertNotNull(val, "result field should exist");
        return val.getType().name().toLowerCase();
    }

    // ── Primitive Types ──

    @Test void parsesStringValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": \"hello world\"}"));
        assertTrue(result.isSuccess());
        assertEquals("string", getOutputType(result));
        assertEquals("hello world", getOutputValue(result).asString());
    }

    @Test void parsesEmptyString() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": \"\"}"));
        assertTrue(result.isSuccess());
        assertEquals("string", getOutputType(result));
        assertEquals("", getOutputValue(result).asString());
    }

    @Test void parsesIntegerValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": 42}"));
        assertTrue(result.isSuccess());
        assertEquals("integer", getOutputType(result));
        assertEquals(Long.valueOf(42), getOutputValue(result).asInt64());
    }

    @Test void parsesNegativeIntegerValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": -100}"));
        assertTrue(result.isSuccess());
        assertEquals("integer", getOutputType(result));
        assertEquals(Long.valueOf(-100), getOutputValue(result).asInt64());
    }

    @Test void parsesZero() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": 0}"));
        assertTrue(result.isSuccess());
        assertEquals("integer", getOutputType(result));
        assertEquals(Long.valueOf(0), getOutputValue(result).asInt64());
    }

    @Test void parsesFloatingPointValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": 3.14159}"));
        assertTrue(result.isSuccess());
        assertEquals("float", getOutputType(result));
        assertEquals(3.14159, getOutputValue(result).asDouble(), 0.00001);
    }

    @Test void parsesNegativeFloatingPointValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": -99.99}"));
        assertTrue(result.isSuccess());
        assertEquals("float", getOutputType(result));
        assertEquals(-99.99, getOutputValue(result).asDouble(), 0.01);
    }

    @Test void parsesBooleanTrue() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": true}"));
        assertTrue(result.isSuccess());
        assertEquals("bool", getOutputType(result));
        assertEquals(Boolean.TRUE, getOutputValue(result).asBool());
    }

    @Test void parsesBooleanFalse() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": false}"));
        assertTrue(result.isSuccess());
        assertEquals("bool", getOutputType(result));
        assertEquals(Boolean.FALSE, getOutputValue(result).asBool());
    }

    @Test void parsesNullValues() {
        var result = exec(createFieldTransform("value"), jsonToDyn("{\"value\": null}"));
        assertTrue(result.isSuccess());
        assertEquals("null", getOutputType(result));
    }

    // ── Nested Objects ──

    @Test void parsesNestedObjectFields() {
        var result = exec(createFieldTransform("person.name"),
                jsonToDyn("{\"person\": {\"name\": \"John\", \"age\": 30}}"));
        assertTrue(result.isSuccess());
        assertEquals("John", getOutputValue(result).asString());
    }

    @Test void parsesDeeplyNestedObjectFields() {
        var result = exec(createFieldTransform("level1.level2.level3.value"),
                jsonToDyn("{\"level1\": {\"level2\": {\"level3\": {\"value\": \"deep\"}}}}"));
        assertTrue(result.isSuccess());
        assertEquals("deep", getOutputValue(result).asString());
    }

    @Test void handlesEmptyObject() {
        var result = exec(createFieldTransform("obj"), jsonToDyn("{\"obj\": {}}"));
        assertTrue(result.isSuccess());
    }

    // ── Arrays ──

    @Test void parsesArrayOfStrings() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\ncount = \"%count @.items\"\nfirst = \"%first @.items\"\n";
        var result = exec(transform, jsonToDyn("{\"items\": [\"a\", \"b\", \"c\"]}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(Long.valueOf(3), output.get("count").asInt64());
        assertEquals("a", output.get("first").asString());
    }

    @Test void parsesArrayOfNumbers() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\nsum = \"%sum @.numbers\"\navg = \"%avg @.numbers\"\n";
        var result = exec(transform, jsonToDyn("{\"numbers\": [10, 20, 30]}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(60.0, output.get("sum").asDouble(), 0.001);
        assertEquals(20.0, output.get("avg").asDouble(), 0.001);
    }

    @Test void parsesArrayOfObjects() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\ncount = \"%count @.users\"\nfirst = \"%first @.users\"\n";
        var result = exec(transform, jsonToDyn("{\"users\": [{\"name\": \"Alice\", \"age\": 25}, {\"name\": \"Bob\", \"age\": 30}]}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(Long.valueOf(2), output.get("count").asInt64());
        // Verify the first element is an object with expected fields
        var first = output.get("first");
        assertNotNull(first);
        assertEquals("Alice", first.get("name").asString());
    }

    @Test void handlesEmptyArray() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\ncount = \"%count @.items\"\n";
        var result = exec(transform, jsonToDyn("{\"items\": []}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(Long.valueOf(0), output.get("count").asInt64());
    }

    @Test void parsesMixedTypeArray() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\ncount = \"%count @.mixed\"\n";
        var result = exec(transform, jsonToDyn("{\"mixed\": [1, \"two\", true, null]}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(Long.valueOf(4), output.get("count").asInt64());
    }

    // ── Special Characters ──

    @Test void parsesStringsWithUnicode() {
        var result = exec(createFieldTransform("text"),
                jsonToDyn("{\"text\": \"\\u65E5\\u672C\\u8A9E\\u30C6\\u30B9\\u30C8\"}"));
        assertTrue(result.isSuccess());
        assertEquals("\u65E5\u672C\u8A9E\u30C6\u30B9\u30C8", getOutputValue(result).asString());
    }

    @Test void parsesStringsWithEmoji() {
        var result = exec(createFieldTransform("text"),
                jsonToDyn("{\"text\": \"Hello \\uD83D\\uDC4B World \\uD83C\\uDF0D\"}"));
        assertTrue(result.isSuccess());
        assertEquals("Hello \uD83D\uDC4B World \uD83C\uDF0D", getOutputValue(result).asString());
    }

    @Test void parsesStringsWithNewlines() {
        var result = exec(createFieldTransform("text"),
                jsonToDyn("{\"text\": \"line1\\nline2\\nline3\"}"));
        assertTrue(result.isSuccess());
        assertEquals("line1\nline2\nline3", getOutputValue(result).asString());
    }

    @Test void parsesStringsWithSpecialJsonCharacters() {
        var result = exec(createFieldTransform("text"),
                jsonToDyn("{\"text\": \"quotes: \\\"test\\\" and backslash: \\\\\"}"));
        assertTrue(result.isSuccess());
        assertEquals("quotes: \"test\" and backslash: \\", getOutputValue(result).asString());
    }

    // ── Edge Cases ──

    @Test void handlesLargeIntegers() {
        var result = exec(createFieldTransform("value"),
                jsonToDyn("{\"value\": 9007199254740991}"));
        assertTrue(result.isSuccess());
        assertEquals(Long.valueOf(9007199254740991L), getOutputValue(result).asInt64());
    }

    @Test void handlesVerySmallFloatingPoint() {
        var result = exec(createFieldTransform("value"),
                jsonToDyn("{\"value\": 0.000001}"));
        assertTrue(result.isSuccess());
        assertEquals(0.000001, getOutputValue(result).asDouble(), 0.0000001);
    }

    @Test void handlesScientificNotation() {
        var result = exec(createFieldTransform("value"),
                jsonToDyn("{\"value\": 1.5e10}"));
        assertTrue(result.isSuccess());
        assertEquals(1.5e10, getOutputValue(result).asDouble(), 1.0);
    }

    @Test void handlesVeryLongStrings() {
        String longString = "a".repeat(10000);
        var result = exec(createFieldTransform("value"),
                jsonToDyn("{\"value\": \"" + longString + "\"}"));
        assertTrue(result.isSuccess());
        assertEquals(longString, getOutputValue(result).asString());
    }

    @Test void handlesLargeArrays() {
        StringBuilder sb = new StringBuilder("{\"items\": [");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) sb.append(",");
            sb.append(i);
        }
        sb.append("]}");

        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\ncount = \"%count @.items\"\nsum = \"%sum @.items\"\n";
        var result = exec(transform, jsonToDyn(sb.toString()));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals(Long.valueOf(1000), output.get("count").asInt64());
        assertEquals(499500.0, output.get("sum").asDouble(), 0.001);
    }

    // ── Type Preservation ──

    @Test void distinguishesIntegerFromFloat() {
        var resultInt = exec(createFieldTransform("value"), jsonToDyn("{\"value\": 42}"));
        var resultFloat = exec(createFieldTransform("value"), jsonToDyn("{\"value\": 42.5}"));

        assertEquals("integer", getOutputType(resultInt));
        assertEquals("float", getOutputType(resultFloat));
    }

    @Test void preservesTypeThroughTransformations() {
        String transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\n\n{output}\nstr = \"@.strVal\"\nnum = \"@.numVal\"\nbool = \"@.boolVal\"\n";
        var result = exec(transform, jsonToDyn("{\"strVal\": \"test\", \"numVal\": 123, \"boolVal\": true}"));
        assertTrue(result.isSuccess());
        var output = result.getOutput().get("output");
        assertEquals("string", output.get("str").getType().name().toLowerCase());
        assertEquals("integer", output.get("num").getType().name().toLowerCase());
        assertEquals("bool", output.get("bool").getType().name().toLowerCase());
    }

    // ── Golden File–Driven Tests ──

    @TestFactory
    Stream<DynamicTest> goldenJsonImportTests() throws IOException {
        Path goldenDir = GoldenParseTests.findGoldenDir();
        Path importDir = goldenDir.resolve("json-import");
        if (!Files.isDirectory(importDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(importDir)) {
            var jsonFiles = walk
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("manifest.json"))
                    .sorted()
                    .toList();

            for (Path jsonFile : jsonFiles) {
                String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
                JsonObject suite = JsonParser.parseString(content).getAsJsonObject();
                String suiteName = suite.has("suite") ? suite.get("suite").getAsString() : jsonFile.getFileName().toString();
                JsonArray testArray = suite.getAsJsonArray("tests");
                if (testArray == null) continue;

                for (JsonElement testEl : testArray) {
                    JsonObject test = testEl.getAsJsonObject();
                    String testId = test.has("id") ? test.get("id").getAsString() : "unknown";
                    String displayName = suiteName + " / " + testId;

                    tests.add(DynamicTest.dynamicTest(displayName, () -> runGoldenImportTest(test)));
                }
            }
        }
        return tests.stream();
    }

    private void runGoldenImportTest(JsonObject test) {
        String transformText = test.get("transform").getAsString();
        JsonElement inputEl = test.get("input");
        DynValue source = jsonToDyn(inputEl.toString());

        var result = exec(transformText, source);
        assertTrue(result.isSuccess(), "Transform failed for " + test.get("id").getAsString());

        if (test.has("expected")) {
            JsonObject expected = test.getAsJsonObject("expected");
            if (expected.has("output")) {
                var outputSeg = result.getOutput().get("output");
                assertNotNull(outputSeg, "Missing 'output' segment");
                assertGoldenValueMatches(outputSeg, expected.getAsJsonObject("output"), "output");
            }
        }
    }

    private void assertGoldenValueMatches(DynValue actual, JsonObject expected, String path) {
        for (var entry : expected.entrySet()) {
            String key = entry.getKey();
            JsonElement expVal = entry.getValue();
            DynValue actualField = actual.get(key);
            assertNotNull(actualField, "Missing field '" + key + "' at " + path);

            if (expVal.isJsonNull()) {
                assertTrue(actualField.isNull(), "Expected null at " + path + "." + key);
            } else if (expVal.isJsonPrimitive()) {
                var prim = expVal.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    assertEquals(prim.getAsBoolean(), actualField.asBool(), path + "." + key);
                } else if (prim.isNumber()) {
                    double exp = prim.getAsDouble();
                    double act = actualField.getType().name().equalsIgnoreCase("integer") ? actualField.asInt64().doubleValue() : actualField.asDouble();
                    assertEquals(exp, act, 0.00001, path + "." + key);
                } else {
                    assertEquals(prim.getAsString(), actualField.asString(), path + "." + key);
                }
            } else if (expVal.isJsonObject()) {
                assertGoldenValueMatches(actualField, expVal.getAsJsonObject(), path + "." + key);
            }
        }
    }
}
