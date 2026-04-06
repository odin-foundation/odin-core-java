package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinDiff;
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
public class GoldenDiffTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> diffGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path diffDir = goldenDir.resolve("diff");
        if (!Files.isDirectory(diffDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();
        try (var walk = Files.walk(diffDir)) {
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

                    tests.add(DynamicTest.dynamicTest(displayName, () -> runDiffTest(test, testId)));
                }
            }
        }
        return tests.stream();
    }

    private void runDiffTest(JsonObject test, String testId) {
        String doc1Text = test.has("doc1") ? test.get("doc1").getAsString() : null;
        String doc2Text = test.has("doc2") ? test.get("doc2").getAsString() : null;
        JsonObject expected = test.has("expected") ? test.getAsJsonObject("expected") : null;

        if (doc1Text == null || doc2Text == null || expected == null) return;

        try {
            OdinDocument doc1 = Odin.parse(doc1Text);
            OdinDocument doc2 = Odin.parse(doc2Text);
            OdinDiff diff = Odin.diff(doc1, doc2);

            if (expected.has("isEmpty")) {
                assertEquals(expected.get("isEmpty").getAsBoolean(), diff.isEmpty(),
                        testId + ": Diff isEmpty mismatch");
            }

            if (expected.has("modifications") && expected.get("modifications").isJsonArray()) {
                JsonArray expectedMods = expected.getAsJsonArray("modifications");
                for (JsonElement modEl : expectedMods) {
                    JsonObject mod = modEl.getAsJsonObject();
                    if (mod.has("path")) {
                        String path = mod.get("path").getAsString();
                        boolean found = diff.changed().stream()
                                .anyMatch(c -> path.equals(c.path()));
                        assertTrue(found, testId + ": Expected modification at path '" + path + "' not found");
                    }
                }
            }

            if (expected.has("additions") && expected.get("additions").isJsonArray()) {
                JsonArray expectedAdds = expected.getAsJsonArray("additions");
                for (JsonElement addEl : expectedAdds) {
                    JsonObject add = addEl.getAsJsonObject();
                    if (add.has("path")) {
                        String path = add.get("path").getAsString();
                        boolean found = diff.added().stream()
                                .anyMatch(a -> path.equals(a.path()));
                        assertTrue(found, testId + ": Expected addition at path '" + path + "' not found");
                    }
                }
            }

            if (expected.has("deletions") && expected.get("deletions").isJsonArray()) {
                JsonArray expectedDeletions = expected.getAsJsonArray("deletions");
                for (JsonElement delEl : expectedDeletions) {
                    JsonObject del = delEl.getAsJsonObject();
                    if (del.has("path")) {
                        String path = del.get("path").getAsString();
                        boolean found = diff.removed().stream()
                                .anyMatch(r -> path.equals(r.path()));
                        assertTrue(found, testId + ": Expected deletion at path '" + path + "' not found");
                    }
                }
            }

            if (expected.has("removals") && expected.get("removals").isJsonArray()) {
                JsonArray expectedRemovals = expected.getAsJsonArray("removals");
                for (JsonElement remEl : expectedRemovals) {
                    JsonObject rem = remEl.getAsJsonObject();
                    if (rem.has("path")) {
                        String path = rem.get("path").getAsString();
                        boolean found = diff.removed().stream()
                                .anyMatch(r -> path.equals(r.path()));
                        assertTrue(found, testId + ": Expected removal at path '" + path + "' not found");
                    }
                }
            }

            if (expected.has("moves") && expected.get("moves").isJsonArray()) {
                JsonArray expectedMoves = expected.getAsJsonArray("moves");
                for (JsonElement moveEl : expectedMoves) {
                    JsonObject move = moveEl.getAsJsonObject();
                    if (move.has("fromPath") && move.has("toPath")) {
                        String fromPath = move.get("fromPath").getAsString();
                        String toPath = move.get("toPath").getAsString();
                        boolean found = diff.moved().stream()
                                .anyMatch(m -> fromPath.equals(m.fromPath()) && toPath.equals(m.toPath()));
                        assertTrue(found, testId + ": Expected move from '" + fromPath + "' to '" + toPath + "' not found");
                    }
                }
            }
        } catch (Exception e) {
            fail(testId + ": Diff test threw exception: " + e.getMessage());
        }
    }
}
