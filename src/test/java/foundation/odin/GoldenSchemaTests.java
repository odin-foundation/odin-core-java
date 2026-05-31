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
        JsonObject assertSpec = test.has("assert") ? test.getAsJsonObject("assert") : null;

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

        OdinSchema.SchemaDefinition schema = Odin.parseSchema(schemaText);
        assertNotNull(schema, "Schema parsed to null");

        if (expected != null) {
            boolean structural = test.has("structural") && test.get("structural").getAsBoolean();

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
        }

        // Value-level assertions (constraint values, unions, defaults, flags).
        if (assertSpec != null) {
            if (assertSpec.has("fields")) {
                JsonObject fields = assertSpec.getAsJsonObject("fields");
                for (String fieldPath : fields.keySet()) {
                    var field = schema.fields().get(fieldPath);
                    assertField(field, fields.getAsJsonObject(fieldPath), "field '" + fieldPath + "'");
                }
            }
            if (assertSpec.has("types")) {
                JsonObject types = assertSpec.getAsJsonObject("types");
                for (String typeName : types.keySet()) {
                    var type = schema.types().get(typeName);
                    assertNotNull(type, "type '" + typeName + "' should be defined");
                    JsonObject ta = types.getAsJsonObject(typeName);
                    if (ta.has("fields")) {
                        JsonObject tf = ta.getAsJsonObject("fields");
                        for (String fieldKey : tf.keySet()) {
                            var field = type.fields().stream()
                                    .filter(f -> f.name().equals(fieldKey)).findFirst().orElse(null);
                            assertField(field, tf.getAsJsonObject(fieldKey),
                                    "type '" + typeName + "' field '" + fieldKey + "'");
                        }
                    }
                }
            }
        }
    }

    // Assert a parsed SchemaField against a value-level assertion object.
    private void assertField(OdinSchema.SchemaField field, JsonObject a, String label) {
        assertNotNull(field, label + " should be defined");

        if (a.has("typeKind")) {
            assertEquals(a.get("typeKind").getAsString(), typeKind(field.fieldType()),
                    label + " type kind");
        }
        if (a.has("typeRefName")) {
            assertTrue(field.fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType,
                    label + " should be a typeRef");
            var ref = (OdinSchema.SchemaFieldType.TypeRefType) field.fieldType();
            assertEquals(a.get("typeRefName").getAsString(), ref.name(), label + " typeRef name");
        }
        if (a.has("required")) assertEquals(a.get("required").getAsBoolean(), field.required(), label + " required");
        if (a.has("nullable")) assertEquals(a.get("nullable").getAsBoolean(), field.nullable(), label + " nullable");
        if (a.has("immutable")) assertEquals(a.get("immutable").getAsBoolean(), field.immutable(), label + " immutable");
        if (a.has("computed")) assertEquals(a.get("computed").getAsBoolean(), field.computed(), label + " computed");
        if (a.has("deprecated")) assertEquals(a.get("deprecated").getAsBoolean(), field.deprecated(), label + " deprecated");

        if (a.has("union")) {
            assertTrue(field.fieldType() instanceof OdinSchema.SchemaFieldType.UnionType,
                    label + " should be a union");
            var union = (OdinSchema.SchemaFieldType.UnionType) field.fieldType();
            var actual = union.types().stream().map(this::typeKind).sorted().toList();
            var expected = new ArrayList<String>();
            a.getAsJsonArray("union").forEach(e -> expected.add(e.getAsString()));
            expected.sort(java.util.Comparator.naturalOrder());
            assertEquals(expected, actual, label + " union members");
        }

        if (a.has("default")) {
            JsonObject d = a.getAsJsonObject("default");
            assertNotNull(field.defaultValue(), label + " default value");
            if (d.has("type")) {
                assertEquals(d.get("type").getAsString(), field.defaultValue().type(),
                        label + " default.type");
            }
            if (d.has("value")) {
                assertEquals(d.get("value").getAsDouble(),
                        ((Number) field.defaultValue().value()).doubleValue(), 1e-9,
                        label + " default.value");
            }
        }

        if (a.has("constraints")) {
            for (JsonElement ce : a.getAsJsonArray("constraints")) {
                JsonObject expectedC = ce.getAsJsonObject();
                boolean found = field.constraints().stream().anyMatch(c -> constraintMatches(c, expectedC));
                assertTrue(found, label + " should have constraint " + expectedC
                        + " (got " + field.constraints() + ")");
            }
        }

        if (a.has("conditionals")) {
            for (JsonElement ce : a.getAsJsonArray("conditionals")) {
                JsonObject expectedCond = ce.getAsJsonObject();
                boolean found = field.conditionals().stream().anyMatch(c -> conditionalMatches(c, expectedCond));
                assertTrue(found, label + " should have conditional " + expectedCond
                        + " (got " + field.conditionals() + ")");
            }
        }
    }

    private boolean constraintMatches(OdinSchema.SchemaConstraint c, JsonObject expected) {
        String kind = expected.has("kind") ? expected.get("kind").getAsString() : null;
        if ("bounds".equals(kind) && c instanceof OdinSchema.SchemaConstraint.Bounds b) {
            if (expected.has("min") && !boundEquals(expected.get("min"), b.min())) return false;
            if (expected.has("max") && !boundEquals(expected.get("max"), b.max())) return false;
            return true;
        }
        if ("pattern".equals(kind) && c instanceof OdinSchema.SchemaConstraint.Pattern p) {
            return !expected.has("pattern") || expected.get("pattern").getAsString().equals(p.pattern());
        }
        if ("unique".equals(kind)) return c instanceof OdinSchema.SchemaConstraint.Unique;
        if ("format".equals(kind) && c instanceof OdinSchema.SchemaConstraint.Format f) {
            return !expected.has("format") || expected.get("format").getAsString().equals(f.formatName());
        }
        return false;
    }

    // Compare a JSON bound (number or temporal string) to the parsed bound string.
    private boolean boundEquals(JsonElement expected, String actual) {
        if (actual == null) return false;
        if (expected.getAsJsonPrimitive().isNumber()) {
            try {
                return Double.compare(expected.getAsDouble(), Double.parseDouble(actual)) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return expected.getAsString().equals(actual);
    }

    private boolean conditionalMatches(OdinSchema.SchemaConditional c, JsonObject expected) {
        if (expected.has("field") && !expected.get("field").getAsString().equals(c.field())) return false;
        if (expected.has("operator") && !expected.get("operator").getAsString().equals(operatorSymbol(c.operator()))) return false;
        if (expected.has("value")) {
            JsonElement v = expected.get("value");
            String actual = switch (c.value()) {
                case OdinSchema.ConditionalValue.StringVal s -> s.value();
                case OdinSchema.ConditionalValue.NumberVal n -> String.valueOf(n.value());
                case OdinSchema.ConditionalValue.BoolVal b -> String.valueOf(b.value());
            };
            if (!v.getAsString().equals(actual)) return false;
        }
        return true;
    }

    private String operatorSymbol(OdinSchema.ConditionalOperator op) {
        return switch (op) {
            case EQ -> "=";
            case NOT_EQ -> "!=";
            case GT -> ">";
            case LT -> "<";
            case GTE -> ">=";
            case LTE -> "<=";
        };
    }

    private String typeKind(OdinSchema.SchemaFieldType type) {
        return switch (type) {
            case OdinSchema.SchemaFieldType.StringType t -> "string";
            case OdinSchema.SchemaFieldType.BooleanType t -> "boolean";
            case OdinSchema.SchemaFieldType.NullType t -> "null";
            case OdinSchema.SchemaFieldType.NumberType t -> "number";
            case OdinSchema.SchemaFieldType.IntegerType t -> "integer";
            case OdinSchema.SchemaFieldType.CurrencyType t -> "currency";
            case OdinSchema.SchemaFieldType.PercentType t -> "percent";
            case OdinSchema.SchemaFieldType.DateType t -> "date";
            case OdinSchema.SchemaFieldType.TimestampType t -> "timestamp";
            case OdinSchema.SchemaFieldType.TimeType t -> "time";
            case OdinSchema.SchemaFieldType.DurationType t -> "duration";
            case OdinSchema.SchemaFieldType.EnumType t -> "enum";
            case OdinSchema.SchemaFieldType.UnionType t -> "union";
            case OdinSchema.SchemaFieldType.ReferenceType t -> "reference";
            case OdinSchema.SchemaFieldType.BinaryType t -> "binary";
            case OdinSchema.SchemaFieldType.TypeRefType t -> "typeRef";
        };
    }
}
