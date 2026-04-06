package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidationExtendedTest {

    private OdinSchema.ValidationResult validate(OdinDocument doc, OdinSchema.SchemaDefinition schema) {
        return Odin.validate(doc, schema);
    }

    private OdinSchema.SchemaDefinition schemaWithField(String name, OdinSchema.SchemaFieldType type, boolean required) {
        var field = new OdinSchema.SchemaField(name, type, required, false, false, null, List.of(), null, List.of());
        return new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(name, field), Map.of(), Map.of());
    }

    private OdinSchema.SchemaDefinition schemaWithFields(Map<String, OdinSchema.SchemaField> fields) {
        return new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
    }

    // ── Multiple required fields ──

    @Nested class MultipleRequiredFieldTests {
        @Test void allRequiredPresent() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "age", new OdinSchema.SchemaField("age", new OdinSchema.SchemaFieldType.IntegerType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = schemaWithFields(fields);
            var doc = new OdinDocumentBuilder().set("name", "Alice").set("age", 30L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void oneRequiredMissing() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "age", new OdinSchema.SchemaField("age", new OdinSchema.SchemaFieldType.IntegerType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = schemaWithFields(fields);
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void allRequiredMissing() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "age", new OdinSchema.SchemaField("age", new OdinSchema.SchemaFieldType.IntegerType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = schemaWithFields(fields);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().size() >= 2);
        }

        @Test void requiredAndOptionalMix() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "nickname", new OdinSchema.SchemaField("nickname", new OdinSchema.SchemaFieldType.StringType())
            );
            var schema = schemaWithFields(fields);
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Type validation comprehensive ──

    @Nested class TypeValidationComprehensiveTests {
        @Test void stringTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("x", "hello").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void stringTypeMismatchWithInteger() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("x", 42L).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void integerTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.IntegerType(), false);
            var doc = new OdinDocumentBuilder().set("x", 42L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void numberTypeAcceptsFloat() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.NumberType(null), false);
            var doc = new OdinDocumentBuilder().set("x", 3.14).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void numberTypeAcceptsInteger() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.NumberType(null), false);
            var doc = new OdinDocumentBuilder().set("x", 42L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void booleanTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.BooleanType(), false);
            var doc = new OdinDocumentBuilder().set("x", true).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void booleanTypeMismatchWithString() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.BooleanType(), false);
            var doc = new OdinDocumentBuilder().set("x", "true").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void nullTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.NullType(), false);
            var doc = new OdinDocumentBuilder().setNull("x").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void nullPassesAnyTypeCheck() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().setNull("x").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void dateTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.DateType(), false);
            var doc = new OdinDocumentBuilder()
                .set("x", new OdinValue.OdinDate(2024, (byte) 6, (byte) 15))
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void timestampTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.TimestampType(), false);
            var doc = new OdinDocumentBuilder()
                .set("x", new OdinValue.OdinTimestamp(0L, "2024-06-15T10:30:00Z"))
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void binaryTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.BinaryType(), false);
            var doc = new OdinDocumentBuilder()
                .set("x", new OdinValue.OdinBinary("hello".getBytes()))
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void currencyTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.CurrencyType(null), false);
            var doc = new OdinDocumentBuilder()
                .set("x", new OdinValue.OdinCurrency(99.99))
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void percentTypeValid() {
            var schema = schemaWithField("x", new OdinSchema.SchemaFieldType.PercentType(), false);
            var doc = new OdinDocumentBuilder()
                .set("x", new OdinValue.OdinPercent(0.15))
                .build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Constraint validation extended ──

    @Nested class ConstraintValidationExtendedTests {
        @Test void boundsWithinRange() {
            var field = new OdinSchema.SchemaField("score", new OdinSchema.SchemaFieldType.IntegerType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", false, false)),
                null, List.of());
            var schema = schemaWithFields(Map.of("score", field));
            var doc = new OdinDocumentBuilder().set("score", 50L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void boundsAtMinimum() {
            var field = new OdinSchema.SchemaField("score", new OdinSchema.SchemaFieldType.IntegerType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", false, false)),
                null, List.of());
            var schema = schemaWithFields(Map.of("score", field));
            var doc = new OdinDocumentBuilder().set("score", 0L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void boundsAtMaximum() {
            var field = new OdinSchema.SchemaField("score", new OdinSchema.SchemaFieldType.IntegerType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", false, false)),
                null, List.of());
            var schema = schemaWithFields(Map.of("score", field));
            var doc = new OdinDocumentBuilder().set("score", 100L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void boundsBelowMinimum() {
            var field = new OdinSchema.SchemaField("score", new OdinSchema.SchemaFieldType.IntegerType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", false, false)),
                null, List.of());
            var schema = schemaWithFields(Map.of("score", field));
            var doc = new OdinDocumentBuilder().set("score", -1L).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void boundsExclusiveMinimum() {
            var field = new OdinSchema.SchemaField("temp", new OdinSchema.SchemaFieldType.NumberType(null),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", true, false)),
                null, List.of());
            var schema = schemaWithFields(Map.of("temp", field));
            var doc = new OdinDocumentBuilder().set("temp", 0.0).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void boundsExclusiveMaximum() {
            var field = new OdinSchema.SchemaField("temp", new OdinSchema.SchemaFieldType.NumberType(null),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "100", false, true)),
                null, List.of());
            var schema = schemaWithFields(Map.of("temp", field));
            var doc = new OdinDocumentBuilder().set("temp", 100.0).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void patternConstraintValid() {
            var field = new OdinSchema.SchemaField("code", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Pattern("^[A-Z]{3}$")),
                null, List.of());
            var schema = schemaWithFields(Map.of("code", field));
            var doc = new OdinDocumentBuilder().set("code", "ABC").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void patternConstraintInvalid() {
            var field = new OdinSchema.SchemaField("code", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Pattern("^[A-Z]{3}$")),
                null, List.of());
            var schema = schemaWithFields(Map.of("code", field));
            var doc = new OdinDocumentBuilder().set("code", "abc").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void enumConstraintValid() {
            var field = new OdinSchema.SchemaField("status", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Enum(List.of("active", "inactive", "pending"))),
                null, List.of());
            var schema = schemaWithFields(Map.of("status", field));
            var doc = new OdinDocumentBuilder().set("status", "active").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void enumConstraintInvalid() {
            var field = new OdinSchema.SchemaField("status", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Enum(List.of("active", "inactive"))),
                null, List.of());
            var schema = schemaWithFields(Map.of("status", field));
            var doc = new OdinDocumentBuilder().set("status", "unknown").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void formatConstraintEmail() {
            var field = new OdinSchema.SchemaField("email", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Format("email")),
                null, List.of());
            var schema = schemaWithFields(Map.of("email", field));
            var doc = new OdinDocumentBuilder().set("email", "user@example.com").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void formatConstraintInvalidEmail() {
            var field = new OdinSchema.SchemaField("email", new OdinSchema.SchemaFieldType.StringType(),
                false, false, false, null,
                List.of(new OdinSchema.SchemaConstraint.Format("email")),
                null, List.of());
            var schema = schemaWithFields(Map.of("email", field));
            var doc = new OdinDocumentBuilder().set("email", "not-an-email").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void multipleConstraints() {
            var field = new OdinSchema.SchemaField("code", new OdinSchema.SchemaFieldType.StringType(),
                true, false, false, null,
                List.of(
                    new OdinSchema.SchemaConstraint.Pattern("^[A-Z]+$"),
                    new OdinSchema.SchemaConstraint.Enum(List.of("ABC", "DEF", "GHI"))
                ),
                null, List.of());
            var schema = schemaWithFields(Map.of("code", field));
            var doc = new OdinDocumentBuilder().set("code", "ABC").build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Validation error messages ──

    @Nested class ValidationErrorMessageTests {
        @Test void requiredFieldErrorCode() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertTrue(result.errors().stream().anyMatch(e -> "V001".equals(e.code())));
        }

        @Test void typeMismatchErrorCode() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("name", 42L).build();
            var result = validate(doc, schema);
            assertTrue(result.errors().stream().anyMatch(e -> "V002".equals(e.code())));
        }

        @Test void errorHasPath() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.errors().isEmpty());
            assertEquals("name", result.errors().get(0).path());
        }

        @Test void errorHasMessage() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.errors().isEmpty());
            assertNotNull(result.errors().get(0).message());
            assertFalse(result.errors().get(0).message().isEmpty());
        }
    }

    // ── Strict mode extended ──

    @Nested class StrictModeExtendedTests {
        @Test void strictModeRejectsUnknownField() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra", "unknown")
                .build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V011".equals(e.code())));
        }

        @Test void strictModeAllowsKnownFields() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertTrue(result.valid());
        }

        @Test void nonStrictAllowsUnknown() {
            var schema = schemaWithField("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra", "unknown")
                .build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Fail-fast mode ──

    @Nested class FailFastTests {
        @Test void failFastStopsAtFirstError() {
            var fields = Map.of(
                "a", new OdinSchema.SchemaField("a", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "b", new OdinSchema.SchemaField("b", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "c", new OdinSchema.SchemaField("c", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = schemaWithFields(fields);
            var doc = OdinDocument.empty();
            var opts = new OdinOptions.ValidateOptions().setFailFast(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
            assertEquals(1, result.errors().size());
        }

        @Test void nonFailFastCollectsAllErrors() {
            var fields = Map.of(
                "a", new OdinSchema.SchemaField("a", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "b", new OdinSchema.SchemaField("b", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "c", new OdinSchema.SchemaField("c", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = schemaWithFields(fields);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().size() >= 3);
        }
    }

    // ── Reference validation ──

    @Nested class ReferenceValidationTests {
        @Test void validReference() {
            var doc = new OdinDocumentBuilder()
                .set("target", "value")
                .set("ref", new OdinValue.OdinReference("target"))
                .build();
            var schema = new OdinSchema.SchemaDefinition();
            var opts = new OdinOptions.ValidateOptions().setValidateReferences(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertTrue(result.valid());
        }

        @Test void unresolvedReferenceReported() {
            var doc = new OdinDocumentBuilder()
                .set("ref", new OdinValue.OdinReference("nonexistent"))
                .build();
            var schema = new OdinSchema.SchemaDefinition();
            var opts = new OdinOptions.ValidateOptions().setValidateReferences(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V013".equals(e.code())));
        }

        @Test void referenceValidationDisabled() {
            var doc = new OdinDocumentBuilder()
                .set("ref", new OdinValue.OdinReference("nonexistent"))
                .build();
            var schema = new OdinSchema.SchemaDefinition();
            var opts = new OdinOptions.ValidateOptions().setValidateReferences(false);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertTrue(result.valid());
        }
    }
}
