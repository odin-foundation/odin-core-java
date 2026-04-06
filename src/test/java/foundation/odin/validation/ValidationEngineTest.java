package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidationEngineTest {

    private OdinSchema.ValidationResult validate(OdinDocument doc, OdinSchema.SchemaDefinition schema) {
        return Odin.validate(doc, schema);
    }

    private OdinSchema.SchemaDefinition simpleSchema(String fieldName, OdinSchema.SchemaFieldType fieldType, boolean required) {
        var field = required
            ? new OdinSchema.SchemaField(fieldName, fieldType, true, false, false, null, List.of(), null, List.of())
            : new OdinSchema.SchemaField(fieldName, fieldType);
        return new OdinSchema.SchemaDefinition(null, List.of(),
            Map.of(), Map.of(fieldName, field), Map.of(), Map.of());
    }

    private OdinSchema.SchemaDefinition schemaWithConstraints(String fieldName, OdinSchema.SchemaFieldType fieldType,
            List<OdinSchema.SchemaConstraint> constraints) {
        var field = new OdinSchema.SchemaField(fieldName, fieldType, false, false, false, null, constraints, null, List.of());
        return new OdinSchema.SchemaDefinition(null, List.of(),
            Map.of(), Map.of(fieldName, field), Map.of(), Map.of());
    }

    // ── Required field validation ──

    @Nested class RequiredFieldTests {
        @Test void requiredFieldPresent() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void requiredFieldMissing() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertFalse(result.errors().isEmpty());
        }

        @Test void requiredFieldMissingCode() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), true);
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertTrue(result.errors().stream().anyMatch(e -> "V001".equals(e.code())));
        }

        @Test void optionalFieldMissing() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = OdinDocument.empty();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Type validation ──

    @Nested class TypeValidationTests {
        @Test void stringTypeValid() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void stringTypeMismatch() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder().set("name", 42L).build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V002".equals(e.code())));
        }

        @Test void integerTypeValid() {
            var schema = simpleSchema("count", new OdinSchema.SchemaFieldType.IntegerType(), false);
            var doc = new OdinDocumentBuilder().set("count", 42L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void integerTypeMismatch() {
            var schema = simpleSchema("count", new OdinSchema.SchemaFieldType.IntegerType(), false);
            var doc = new OdinDocumentBuilder().set("count", "hello").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void booleanTypeValid() {
            var schema = simpleSchema("active", new OdinSchema.SchemaFieldType.BooleanType(), false);
            var doc = new OdinDocumentBuilder().set("active", true).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void booleanTypeMismatch() {
            var schema = simpleSchema("active", new OdinSchema.SchemaFieldType.BooleanType(), false);
            var doc = new OdinDocumentBuilder().set("active", "yes").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void numberTypeValid() {
            var schema = simpleSchema("rate", new OdinSchema.SchemaFieldType.NumberType(null), false);
            var doc = new OdinDocumentBuilder().set("rate", 3.14).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void nullTypeValid() {
            var schema = simpleSchema("empty", new OdinSchema.SchemaFieldType.NullType(), false);
            var doc = new OdinDocumentBuilder().setNull("empty").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void dateTypeValid() {
            var schema = simpleSchema("dob", new OdinSchema.SchemaFieldType.DateType(), false);
            var doc = new OdinDocumentBuilder()
                .set("dob", new OdinValue.OdinDate(2024, (byte) 6, (byte) 15))
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void timestampTypeValid() {
            var schema = simpleSchema("ts", new OdinSchema.SchemaFieldType.TimestampType(), false);
            var doc = new OdinDocumentBuilder()
                .set("ts", new OdinValue.OdinTimestamp(0L, "2024-06-15T10:30:00Z"))
                .build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── Constraint validation ──

    @Nested class ConstraintTests {
        @Test void boundsValidInRange() {
            var schema = schemaWithConstraints("age", new OdinSchema.SchemaFieldType.IntegerType(),
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "150", false, false)));
            var doc = new OdinDocumentBuilder().set("age", 30L).build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void boundsViolationTooHigh() {
            var schema = schemaWithConstraints("age", new OdinSchema.SchemaFieldType.IntegerType(),
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "150", false, false)));
            var doc = new OdinDocumentBuilder().set("age", 200L).build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V003".equals(e.code())));
        }

        @Test void boundsViolationTooLow() {
            var schema = schemaWithConstraints("age", new OdinSchema.SchemaFieldType.IntegerType(),
                List.of(new OdinSchema.SchemaConstraint.Bounds("0", "150", false, false)));
            var doc = new OdinDocumentBuilder().set("age", -1L).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void patternValid() {
            var schema = schemaWithConstraints("code", new OdinSchema.SchemaFieldType.StringType(),
                List.of(new OdinSchema.SchemaConstraint.Pattern("^[A-Z]{3}$")));
            var doc = new OdinDocumentBuilder().set("code", "ABC").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void patternInvalid() {
            var schema = schemaWithConstraints("code", new OdinSchema.SchemaFieldType.StringType(),
                List.of(new OdinSchema.SchemaConstraint.Pattern("^[A-Z]{3}$")));
            var doc = new OdinDocumentBuilder().set("code", "abc").build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V004".equals(e.code())));
        }

        @Test void enumValid() {
            var schema = schemaWithConstraints("status", new OdinSchema.SchemaFieldType.StringType(),
                List.of(new OdinSchema.SchemaConstraint.Enum(List.of("active", "inactive", "pending"))));
            var doc = new OdinDocumentBuilder().set("status", "active").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void enumInvalid() {
            var schema = schemaWithConstraints("status", new OdinSchema.SchemaFieldType.StringType(),
                List.of(new OdinSchema.SchemaConstraint.Enum(List.of("active", "inactive"))));
            var doc = new OdinDocumentBuilder().set("status", "unknown").build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V005".equals(e.code())));
        }
    }

    // ── Strict mode ──

    @Nested class StrictModeTests {
        @Test void strictModeRejectsUnknownFields() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra", "unknown")
                .build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V011".equals(e.code())));
        }

        @Test void nonStrictAllowsUnknownFields() {
            var schema = simpleSchema("name", new OdinSchema.SchemaFieldType.StringType(), false);
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra", "unknown")
                .build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // ── ValidationResult ──

    @Nested class ResultTests {
        @Test void validResultDefault() {
            var result = new OdinSchema.ValidationResult();
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test void invalidResultHasErrors() {
            var errors = List.of(new OdinSchema.ValidationError("path", "V001", "Required field missing"));
            var result = new OdinSchema.ValidationResult(false, errors);
            assertFalse(result.valid());
            assertEquals(1, result.errors().size());
        }

        @Test void validationErrorFields() {
            var err = new OdinSchema.ValidationError("name", "V001", "Required field missing");
            assertEquals("name", err.path());
            assertEquals("V001", err.code());
            assertEquals("Required field missing", err.message());
        }
    }

    // ── Empty schema ──

    @Nested class EmptySchemaTests {
        @Test void emptySchemaAllowsAnything() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = new OdinDocumentBuilder().set("anything", "goes").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void emptyDocAgainstEmptySchema() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = OdinDocument.empty();
            assertTrue(validate(doc, schema).valid());
        }
    }
}
