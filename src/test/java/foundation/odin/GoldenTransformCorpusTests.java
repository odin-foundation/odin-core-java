package foundation.odin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import foundation.odin.parsing.OdinParser;
import foundation.odin.transform.TransformEngine;
import foundation.odin.transform.TransformParser;
import foundation.odin.types.DynValue;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinOptions;
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
public class GoldenTransformCorpusTests {

    private static final Gson GSON = new Gson();

    @TestFactory
    Stream<DynamicTest> transformCorpus() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path corpusDir = goldenDir.resolve("transform-corpus");
        if (!Files.isDirectory(corpusDir)) {
            return Stream.empty();
        }

        var fixtures = loadFixtures(corpusDir);
        var tests = new ArrayList<DynamicTest>();

        for (var lf : fixtures) {
            Fixture f = lf.fixture;
            // Precision/format/RNG-specific fixtures are excluded from cross-language runners.
            if (f.tsOnly) continue;

            tests.add(DynamicTest.dynamicTest(f.family + "/" + f.id, () -> runFixture(f, lf.file)));
        }

        return tests.stream();
    }

    private static void runFixture(Fixture f, Path file) {
        String transformText = headerFor(f) + f.transform;
        var source = parseOdinSource(f.input);

        if ("error".equals(f.family)) {
            // Documented gaps the engine does not yet emit are not asserted.
            if (Boolean.FALSE.equals(f.enforced)) return;
            String code = f.code;
            String surfaced;
            try {
                var transform = TransformParser.parse(transformText);
                var result = TransformEngine.execute(transform, source);
                surfaced = fmtIssues(result.getErrors().stream()
                            .map(e -> e.getCode() + " " + e.getMessage()).toList())
                        + "\n"
                        + fmtIssues(result.getWarnings().stream()
                            .map(w -> w.getCode() + " " + w.getMessage()).toList());
            } catch (Exception e) {
                surfaced = e.getMessage() != null ? e.getMessage() : "";
            }
            assertTrue(surfaced.contains(code), code + " not surfaced in " + file + " — got: " + surfaced);
            return;
        }

        var transform2 = TransformParser.parse(transformText);
        var result = TransformEngine.execute(transform2, source);
        assertTrue(result.getErrors().isEmpty(),
                "errors in " + file + ": " + result.getErrors().stream()
                        .map(e -> "[" + e.getCode() + "] " + e.getMessage())
                        .collect(Collectors.joining(", ")));

        String actual = normalize(result.getFormatted() != null ? result.getFormatted() : "");
        String expected = normalize(f.expectedOutput);
        assertEquals(expected, actual, "Output mismatch for " + f.family + "/" + f.id);
    }

    // Parse an ODIN source and reconstruct it as a nested DynValue, preserving
    // typed leaf values and array/object nesting from the document's flat paths.
    private static DynValue parseOdinSource(String odinText) {
        OdinDocument doc = OdinParser.parse(odinText, OdinOptions.ParseOptions.DEFAULT);
        Node root = new Node();
        for (String path : doc.paths()) {
            if (path.startsWith("$.") || path.equals("$")) continue;
            OdinValue value = doc.get(path);
            if (value == null) continue;
            insert(root, parsePath(path), 0, TransformEngine.odinValueToDyn(value));
        }
        return toDynValue(root);
    }

    // A nesting node: either holds a leaf, an ordered object (named children),
    // or an array (indexed children).
    private static final class Node {
        DynValue leaf;
        final LinkedHashMap<String, Node> fields = new LinkedHashMap<>();
        final TreeMap<Integer, Node> elements = new TreeMap<>();
    }

    private sealed interface Seg permits FieldSeg, IndexSeg {}
    private record FieldSeg(String name) implements Seg {}
    private record IndexSeg(int index) implements Seg {}

    // Split "a.b[0].c" into [field a, field b, index 0, field c].
    private static List<Seg> parsePath(String path) {
        var segs = new ArrayList<Seg>();
        for (String part : path.split("\\.")) {
            int br = part.indexOf('[');
            if (br < 0) {
                segs.add(new FieldSeg(part));
                continue;
            }
            String name = part.substring(0, br);
            if (!name.isEmpty()) segs.add(new FieldSeg(name));
            String rest = part.substring(br);
            for (String idx : rest.replace("]", "").split("\\[")) {
                if (idx.isEmpty()) continue;
                segs.add(new IndexSeg(Integer.parseInt(idx)));
            }
        }
        return segs;
    }

    private static void insert(Node node, List<Seg> segs, int i, DynValue value) {
        if (i == segs.size()) {
            node.leaf = value;
            return;
        }
        Seg seg = segs.get(i);
        Node child;
        if (seg instanceof FieldSeg fs) {
            child = node.fields.computeIfAbsent(fs.name(), k -> new Node());
        } else {
            child = node.elements.computeIfAbsent(((IndexSeg) seg).index(), k -> new Node());
        }
        insert(child, segs, i + 1, value);
    }

    private static DynValue toDynValue(Node node) {
        if (node.leaf != null) return node.leaf;
        if (!node.elements.isEmpty()) {
            var items = new ArrayList<DynValue>();
            int next = 0;
            for (var e : node.elements.entrySet()) {
                while (next < e.getKey()) { items.add(DynValue.ofNull()); next++; }
                items.add(toDynValue(e.getValue()));
                next++;
            }
            return DynValue.ofArray(items);
        }
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : node.fields.entrySet()) entries.add(Map.entry(e.getKey(), toDynValue(e.getValue())));
        return DynValue.ofObject(entries);
    }

    private static String fmtIssues(List<String> issues) {
        return String.join("\n", issues);
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n").stripTrailing();
    }

    // ── Header (mirrors the shared TS builder) ──

    private static String buildHeader(String targetFormat,
            Map<String, String> targetOptions, Map<String, String> headerFields) {
        var meta = new ArrayList<String>();
        meta.add("odin = \"1.0.0\"");
        meta.add("transform = \"1.0.0\"");
        meta.add("direction = \"odin->" + targetFormat + "\"");
        if (headerFields != null) {
            for (var e : headerFields.entrySet()) meta.add(e.getKey() + " = " + e.getValue());
        }
        var target = new ArrayList<String>();
        target.add("format = \"" + targetFormat + "\"");
        if (targetOptions != null) {
            for (var e : targetOptions.entrySet()) target.add(e.getKey() + " = \"" + e.getValue() + "\"");
        }
        return "{$}\n" + String.join("\n", meta) + "\n\n"
                + "{$source}\nformat = \"odin\"\n\n"
                + "{$target}\n" + String.join("\n", target) + "\n\n";
    }

    private static String headerFor(Fixture f) {
        String fmt = f.targetFormat != null ? f.targetFormat : "odin";
        return buildHeader(fmt, f.targetOptions, f.headerFields);
    }

    // ── Discovery (walk <family>/*.json, skip manifest.json) ──

    private record LoadedFixture(Fixture fixture, Path file) {}

    private static List<LoadedFixture> loadFixtures(Path dir) throws IOException {
        var out = new ArrayList<LoadedFixture>();
        try (var families = Files.list(dir)) {
            for (Path familyPath : families.sorted().toList()) {
                if (!Files.isDirectory(familyPath)) continue;
                try (var names = Files.list(familyPath)) {
                    for (Path file : names.sorted().toList()) {
                        String name = file.getFileName().toString();
                        if (!name.endsWith(".json") || name.equals("manifest.json")) continue;
                        String raw = Files.readString(file, StandardCharsets.UTF_8);
                        Fixture fx = parseFixture(raw);
                        out.add(new LoadedFixture(fx, file));
                    }
                }
            }
        }
        out.sort(Comparator.comparing(a -> a.file.toString()));
        return out;
    }

    private static Fixture parseFixture(String raw) {
        JsonObject o = GSON.fromJson(raw, JsonObject.class);
        Fixture f = new Fixture();
        f.id = str(o, "id");
        f.family = str(o, "family");
        f.transform = str(o, "transform");
        f.input = str(o, "input");
        f.expectedOutput = str(o, "expectedOutput");
        f.code = str(o, "code");
        f.targetFormat = str(o, "targetFormat");
        if (o.has("enforced") && !o.get("enforced").isJsonNull()) f.enforced = o.get("enforced").getAsBoolean();
        if (o.has("tsOnly") && !o.get("tsOnly").isJsonNull()) f.tsOnly = o.get("tsOnly").getAsBoolean();
        f.targetOptions = strMap(o, "targetOptions");
        f.headerFields = strMap(o, "headerFields");
        return f;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static Map<String, String> strMap(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        var m = new LinkedHashMap<String, String>();
        for (var e : o.getAsJsonObject(key).entrySet()) m.put(e.getKey(), e.getValue().getAsString());
        return m;
    }

    private static final class Fixture {
        String id;
        String family;
        String transform;
        String input;
        String expectedOutput;
        String code;
        String targetFormat;
        Boolean enforced;
        boolean tsOnly;
        Map<String, String> targetOptions;
        Map<String, String> headerFields;
    }
}
