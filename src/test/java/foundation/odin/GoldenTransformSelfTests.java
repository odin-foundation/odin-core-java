package foundation.odin;

import foundation.odin.transform.TransformEngine;
import foundation.odin.transform.TransformParser;
import foundation.odin.types.DynValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("golden")
public class GoldenTransformSelfTests {

    // No known gaps — all golden tests must pass

    @TestFactory
    Stream<DynamicTest> transformSelfTests() throws IOException {
        Path goldenDir = GoldenTestHelper.findGoldenDir();
        Path verbsDir = goldenDir.resolve("transform/verbs");
        if (!Files.isDirectory(verbsDir)) {
            return Stream.empty();
        }

        var tests = new ArrayList<DynamicTest>();

        // Discover self-testing transforms (files ending with .test.odin)
        try (var walk = Files.list(verbsDir)) {
            var testFiles = walk
                    .filter(p -> p.getFileName().toString().endsWith(".test.odin"))
                    .sorted()
                    .toList();

            for (Path testFile : testFiles) {
                String verbName = testFile.getFileName().toString().replace(".test.odin", "");

                tests.add(DynamicTest.dynamicTest("self-test / " + verbName, () -> {
                    String transformText = Files.readString(testFile, StandardCharsets.UTF_8)
                            .replace("\r\n", "\n");

                    // Self-tests run with empty JSON object as input
                    var transform = TransformParser.parse(transformText);
                    var input = DynValue.ofObject(new ArrayList<>());
                    var result = TransformEngine.execute(transform, input);

                    assertTrue(result.isSuccess(),
                            "Transform execution failed for " + verbName + ": " +
                            result.getErrors().stream()
                                    .map(e -> e.getMessage())
                                    .collect(Collectors.joining(", ")));

                    // Check the TestResult segment
                    var output = result.getOutput();
                    assertNotNull(output, "No output for " + verbName);

                    var testResult = output.get("TestResult");
                    assertNotNull(testResult, "No TestResult segment in output for " + verbName);

                    // Check success field
                    var successField = testResult.get("success");
                    assertNotNull(successField, "No success field in TestResult for " + verbName);

                    var passedField = testResult.get("passed");
                    var failedField = testResult.get("failed");
                    var totalField = testResult.get("total");

                    String detail = String.format("[%s] passed=%s, failed=%s, total=%s",
                            verbName,
                            passedField != null ? passedField.asString() != null ? passedField.asString() : String.valueOf(passedField.asInt64()) : "?",
                            failedField != null ? failedField.asString() != null ? failedField.asString() : String.valueOf(failedField.asInt64()) : "?",
                            totalField != null ? totalField.asString() != null ? totalField.asString() : String.valueOf(totalField.asInt64()) : "?");

                    // success should be true (boolean) or truthy
                    Boolean successBool = successField.asBool();
                    assertTrue(successBool != null && successBool,
                            "Self-test FAILED: " + detail);
                }));
            }
        }

        return tests.stream();
    }
}
