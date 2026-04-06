package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import foundation.odin.export.JsonExport;
import foundation.odin.export.XmlExport;
import foundation.odin.transform.*;
import foundation.odin.types.DynValue;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinTransformTypes.OdinTransform;
import foundation.odin.types.OdinValue;
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
public class GoldenEndToEndTests {

    private static final Gson GSON = new Gson();

    // No known gaps — all golden tests must pass

    @TestFactory
    Stream<DynamicTest> endToEndGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path e2eDir = goldenDir.resolve("end-to-end");
        if (!Files.isDirectory(e2eDir)) {
            return Stream.empty();
        }

        Path mainManifestPath = e2eDir.resolve("manifest.json");
        if (!Files.exists(mainManifestPath)) {
            return Stream.empty();
        }

        String mainManifestJson = Files.readString(mainManifestPath, StandardCharsets.UTF_8);
        JsonObject mainManifest = GSON.fromJson(mainManifestJson, JsonObject.class);
        JsonArray categories = mainManifest.getAsJsonArray("categories");
        if (categories == null) return Stream.empty();

        var tests = new ArrayList<DynamicTest>();

        for (var catEl : categories) {
            JsonObject cat = catEl.getAsJsonObject();
            String catId = cat.get("id").getAsString();
            String catPath = cat.get("path").getAsString();

            Path catDir = e2eDir.resolve(catPath);
            Path catManifestPath = catDir.resolve("manifest.json");
            if (!Files.exists(catManifestPath)) continue;

            String catManifestJson = Files.readString(catManifestPath, StandardCharsets.UTF_8);
            JsonObject catManifest = GSON.fromJson(catManifestJson, JsonObject.class);
            JsonArray testDefs = catManifest.getAsJsonArray("tests");
            if (testDefs == null) continue;

            for (var testEl : testDefs) {
                JsonObject test = testEl.getAsJsonObject();
                String testId = test.get("id").getAsString();
                String description = test.has("description") ? test.get("description").getAsString() : testId;

                tests.add(DynamicTest.dynamicTest(catId + " / " + testId + " — " + description,
                        () -> runE2ETest(test, catDir)));
            }
        }

        return tests.stream();
    }

    private void runE2ETest(JsonObject test, Path catDir) throws IOException {
        String testId = test.get("id").getAsString();

        if (test.has("method") && !test.get("method").isJsonNull()) {
            runDirectExportTest(test, catDir);
        } else if (test.has("importTransform") && !test.get("importTransform").isJsonNull()
                && test.has("exportTransform") && !test.get("exportTransform").isJsonNull()) {
            runRoundtripTest(test, catDir);
        } else if (test.has("transform") && !test.get("transform").isJsonNull()) {
            runTransformTest(test, catDir);
        } else {
            fail("[" + testId + "] No transform/method specified");
        }
    }

    private void runTransformTest(JsonObject test, Path catDir) throws IOException {
        String testId = test.get("id").getAsString();
        String inputRaw = readAndNormalize(catDir.resolve(test.get("input").getAsString()));
        String transformText = readAndNormalize(catDir.resolve(test.get("transform").getAsString()));
        String expected = readAndNormalize(catDir.resolve(test.get("expected").getAsString()));

        String direction = test.has("direction") ? test.get("direction").getAsString() : "odin->odin";
        String srcFmt = sourceFormat(direction);

        var transform = TransformParser.parse(transformText);
        var input = parseInput(inputRaw, srcFmt);
        var result = TransformEngine.execute(transform, input);

        if (!result.getErrors().isEmpty()) {
            var errMsgs = result.getErrors().stream()
                    .map(e -> "[" + e.getCode() + "] " + e.getMessage())
                    .collect(Collectors.joining(", "));
            fail("[" + testId + "] Transform failed: " + errMsgs);
        }

        String formatted = normalizeLineEndings(result.getFormatted() != null ? result.getFormatted() : "");
        String expectedNorm = normalizeLineEndings(expected);

        assertEquals(expectedNorm, formatted,
                "[" + testId + "] Formatted output mismatch");
    }

    private void runDirectExportTest(JsonObject test, Path catDir) throws IOException {
        String testId = test.get("id").getAsString();
        String inputText = readAndNormalize(catDir.resolve(test.get("input").getAsString()));
        String expected = readAndNormalize(catDir.resolve(test.get("expected").getAsString()));

        OdinDocument doc = Odin.parse(inputText);
        boolean preserveTypes = true;
        boolean preserveModifiers = true;

        if (test.has("options") && test.get("options").isJsonObject()) {
            JsonObject opts = test.getAsJsonObject("options");
            if (opts.has("preserveTypes")) preserveTypes = opts.get("preserveTypes").getAsBoolean();
            if (opts.has("preserveModifiers")) preserveModifiers = opts.get("preserveModifiers").getAsBoolean();
        }

        String method = test.get("method").getAsString();
        String actual = switch (method) {
            case "toJSON" -> Odin.toJson(doc, preserveTypes, preserveModifiers);
            case "toXML" -> Odin.toXml(doc, preserveTypes, preserveModifiers);
            default -> throw new IllegalArgumentException("Unknown export method: " + method);
        };

        String normExpected = normalizeLineEndings(expected);
        String normActual = normalizeLineEndings(actual);

        assertEquals(normExpected, normActual,
                "[" + testId + "] " + method + " output mismatch");
    }

    private void runRoundtripTest(JsonObject test, Path catDir) throws IOException {
        String testId = test.get("id").getAsString();
        String inputRaw = readAndNormalize(catDir.resolve(test.get("input").getAsString()));
        String importTransformText = readAndNormalize(catDir.resolve(test.get("importTransform").getAsString()));
        String exportTransformText = readAndNormalize(catDir.resolve(test.get("exportTransform").getAsString()));
        String expected = readAndNormalize(catDir.resolve(test.get("expected").getAsString()));

        String direction = test.has("direction") ? test.get("direction").getAsString() : "fixed-width->fixed-width";
        String srcFmt = sourceFormat(direction);

        // Step 1: Import
        var importTransform = TransformParser.parse(importTransformText);
        var input = parseInput(inputRaw, srcFmt);
        var importResult = TransformEngine.execute(importTransform, input);

        if (!importResult.getErrors().isEmpty()) {
            fail("[" + testId + "] Import transform failed: " +
                    importResult.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(", ")));
        }

        var importOutput = importResult.getOutput() != null ? importResult.getOutput() : DynValue.ofNull();

        // Step 2: Export
        var exportTransform = TransformParser.parse(exportTransformText);
        var exportResult = TransformEngine.execute(exportTransform, importOutput);

        if (!exportResult.getErrors().isEmpty()) {
            fail("[" + testId + "] Export transform failed: " +
                    exportResult.getErrors().stream().map(e -> e.getMessage()).collect(Collectors.joining(", ")));
        }

        String formatted = normalizeLineEndings(exportResult.getFormatted() != null ? exportResult.getFormatted() : "");
        String expectedNorm = normalizeLineEndings(expected);

        assertEquals(expectedNorm, formatted,
                "[" + testId + "] Roundtrip output mismatch");
    }

    // ── Helpers ──

    private static String sourceFormat(String direction) {
        String[] parts = direction.split("->");
        return parts.length > 0 ? parts[0] : "odin";
    }

    private static DynValue parseInput(String raw, String format) {
        return switch (format) {
            case "json" -> JsonSourceParser.parse(raw);
            case "xml" -> XmlSourceParser.parse(raw);
            case "yaml" -> YamlSourceParser.parse(raw);
            case "flat", "properties", "flat-kvp" -> FlatSourceParser.parse(raw);
            case "odin" -> odinDocToDyn(Odin.parse(raw));
            case "fixed-width", "csv", "delimited" -> DynValue.ofString(raw);
            default -> DynValue.ofString(raw);
        };
    }

    private static DynValue odinDocToDyn(OdinDocument doc) {
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        for (var kv : doc.getAssignments()) {
            if (kv.getKey().startsWith("$")) continue;
            var dynVal = TransformEngine.odinValueToDyn(kv.getValue());
            setNestedPath(entries, kv.getKey(), dynVal);
        }
        return DynValue.ofObject(entries);
    }

    private static void setNestedPath(List<Map.Entry<String, DynValue>> root, String path, DynValue value) {
        String[] segments = path.split("\\.");
        setNestedRecursive(root, segments, 0, value);
    }

    @SuppressWarnings("unchecked")
    private static void setNestedRecursive(List<Map.Entry<String, DynValue>> entries,
            String[] segments, int idx, DynValue value) {
        if (idx >= segments.length) return;

        String seg = segments[idx];
        boolean isLast = idx == segments.length - 1;

        // Check for array index: key[N]
        int bracketPos = seg.indexOf('[');
        if (bracketPos >= 0 && seg.endsWith("]")) {
            String key = seg.substring(0, bracketPos);
            String idxStr = seg.substring(bracketPos + 1, seg.length() - 1);
            try {
                int arrIdx = Integer.parseInt(idxStr);

                int pos = findEntry(entries, key);
                if (pos < 0) {
                    entries.add(Map.entry(key, DynValue.ofArray(new ArrayList<>())));
                    pos = entries.size() - 1;
                }
                var arr = entries.get(pos).getValue().asArray();
                if (arr == null) {
                    arr = new ArrayList<>();
                    entries.set(pos, Map.entry(key, DynValue.ofArray(arr)));
                }
                while (arr.size() <= arrIdx)
                    arr.add(isLast ? DynValue.ofNull() : DynValue.ofObject(new ArrayList<>()));
                if (isLast) {
                    arr.set(arrIdx, value);
                } else {
                    var inner = arr.get(arrIdx).asObject();
                    if (inner == null) {
                        inner = new ArrayList<>();
                        arr.set(arrIdx, DynValue.ofObject(inner));
                    }
                    setNestedRecursive(inner, segments, idx + 1, value);
                }
                return;
            } catch (NumberFormatException ignored) {}
        }

        if (isLast) {
            entries.add(Map.entry(seg, value));
        } else {
            int pos = findEntry(entries, seg);
            if (pos >= 0) {
                var obj = entries.get(pos).getValue().asObject();
                if (obj != null) {
                    setNestedRecursive(obj, segments, idx + 1, value);
                }
            } else {
                var obj = new ArrayList<Map.Entry<String, DynValue>>();
                entries.add(Map.entry(seg, DynValue.ofObject(obj)));
                setNestedRecursive(obj, segments, idx + 1, value);
            }
        }
    }

    private static int findEntry(List<Map.Entry<String, DynValue>> entries, String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(key)) return i;
        }
        return -1;
    }

    private static String readAndNormalize(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
