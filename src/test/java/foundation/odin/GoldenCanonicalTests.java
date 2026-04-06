package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundation.odin.parsing.OdinParser;
import foundation.odin.serialization.Canonicalize;
import foundation.odin.types.OdinDocument;
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
public class GoldenCanonicalTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> canonicalGoldenTests() throws IOException {
        Path goldenDir = GoldenParseTests.findGoldenDir();
        Path canonicalDir = goldenDir.resolve("canonical");
        if (!Files.isDirectory(canonicalDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(canonicalDir)) {
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

                    tests.add(DynamicTest.dynamicTest(displayName, () -> runCanonicalTest(test)));
                }
            }
        }
        return tests.stream();
    }

    private void runCanonicalTest(JsonObject test) {
        String input = test.has("input") ? test.get("input").getAsString() : null;
        if (input == null) return;

        JsonElement expectedEl = test.get("expected");
        if (expectedEl == null) return;

        try {
            OdinDocument doc = OdinParser.parse(input, ParseOptions.DEFAULT);
            byte[] canonical = Canonicalize.serialize(doc);

            if (expectedEl.isJsonPrimitive() && expectedEl.getAsJsonPrimitive().isString()) {
                // Simple string comparison
                String actual = new String(canonical, StandardCharsets.UTF_8);
                assertEquals(expectedEl.getAsString(), actual, "Canonical output mismatch");
            } else if (expectedEl.isJsonObject()) {
                // Binary output format: { hex, sha256, byteLength }
                JsonObject expectedObj = expectedEl.getAsJsonObject();
                if (expectedObj.has("byteLength")) {
                    int expectedLen = expectedObj.get("byteLength").getAsInt();
                    assertEquals(expectedLen, canonical.length, "Byte length mismatch");
                }
                if (expectedObj.has("hex")) {
                    String expectedHex = expectedObj.get("hex").getAsString();
                    String actualHex = bytesToHex(canonical);
                    assertEquals(expectedHex, actualHex, "Hex output mismatch");
                }
                if (expectedObj.has("sha256")) {
                    String expectedSha = expectedObj.get("sha256").getAsString();
                    try {
                        var digest = java.security.MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(canonical);
                        String actualSha = bytesToHex(hash);
                        assertEquals(expectedSha, actualSha, "SHA-256 mismatch");
                    } catch (java.security.NoSuchAlgorithmException e) {
                        fail("SHA-256 not available");
                    }
                }
            }
        } catch (Exception e) {
            fail("Canonical test threw exception: " + e.getMessage());
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
