package foundation.odin;

import foundation.odin.transform.JsonSourceParser;
import foundation.odin.transform.TransformEngine;
import foundation.odin.transform.TransformParser;
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
public class GoldenTryItVerbTests {

    @TestFactory
    Stream<DynamicTest> tryItVerbGoldenTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path tryItDir = goldenDir.resolve("transform/verbs/try-it");
        if (!Files.isDirectory(tryItDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();

        // Discover all verbs by finding *.expected.odin files
        try (var walk = Files.list(tryItDir)) {
            var expectedFiles = walk
                    .filter(p -> p.getFileName().toString().endsWith(".expected.odin"))
                    .sorted()
                    .toList();

            for (Path expectedFile : expectedFiles) {
                String fileName = expectedFile.getFileName().toString();
                String verb = fileName.replace(".expected.odin", "");

                Path inputFile = tryItDir.resolve(verb + ".input.json");
                Path transformFile = tryItDir.resolve(verb + ".transform.odin");

                if (!Files.exists(inputFile) || !Files.exists(transformFile)) {
                    continue;
                }

                tests.add(DynamicTest.dynamicTest("try-it / " + verb, () -> {
                    String inputRaw = normalize(Files.readString(inputFile, StandardCharsets.UTF_8));
                    String transformText = normalize(Files.readString(transformFile, StandardCharsets.UTF_8));
                    String expectedText = normalize(Files.readString(expectedFile, StandardCharsets.UTF_8));

                    var transform = TransformParser.parse(transformText);
                    transform.getTarget().setFormat("odin");
                    var input = JsonSourceParser.parse(inputRaw);
                    var result = TransformEngine.execute(transform, input);

                    if (!result.getErrors().isEmpty()) {
                        var errMsgs = result.getErrors().stream()
                                .map(e -> "[" + e.getCode() + "] " + e.getMessage())
                                .collect(Collectors.joining(", "));
                        fail("Transform failed: " + errMsgs);
                    }

                    String actual = normalize(result.getFormatted() != null ? result.getFormatted() : "");
                    String expected = normalize(expectedText);

                    assertEquals(expected, actual,
                            "Output mismatch for verb '" + verb + "'");
                }));
            }
        }

        return tests.stream();
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }
}
