package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorEdgeCasesTest {

    private OdinSchema.ValidationResult validate(OdinDocument doc, OdinSchema.SchemaDefinition schema) {
        return Odin.validate(doc, schema);
    }

    // --- Empty and null schemas ---

    @Nested class EmptySchemaTests {
        @Test void emptySchemaAcceptsEmptyDoc() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = OdinDocument.empty();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void emptySchemaAcceptsAnyDoc() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("age", 30L)
                .set("active", true)
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void emptySchemaWithStrictMode() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = new OdinDocumentBuilder().set("x", "value").build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
        }

        @Test void schemaWithNoRequiredFields() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType()),
                "age", new OdinSchema.SchemaField("age", new OdinSchema.SchemaFieldType.IntegerType())
            );
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
            var doc = OdinDocument.empty();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // --- Validation of empty documents ---

    @Nested class EmptyDocumentValidationTests {
        @Test void emptyDocPassesNoRequirements() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("optional", new OdinSchema.SchemaField("optional", new OdinSchema.SchemaFieldType.StringType())),
                Map.of(), Map.of());
            var doc = OdinDocument.empty();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void emptyDocFailsWithRequiredField() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("required", new OdinSchema.SchemaField("required",
                    new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of())),
                Map.of(), Map.of());
            var doc = OdinDocument.empty();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void emptyDocMultipleRequiredFieldsFail() {
            var fields = Map.of(
                "a", new OdinSchema.SchemaField("a", new OdinSchema.SchemaFieldType.StringType(), true, false, false, null, List.of(), null, List.of()),
                "b", new OdinSchema.SchemaField("b", new OdinSchema.SchemaFieldType.IntegerType(), true, false, false, null, List.of(), null, List.of()),
                "c", new OdinSchema.SchemaField("c", new OdinSchema.SchemaFieldType.BooleanType(), true, false, false, null, List.of(), null, List.of())
            );
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().size() >= 3);
        }
    }

    // --- Type mismatch edge cases ---

    @Nested class TypeMismatchEdgeCaseTests {
        @Test void integerWhereStringExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.StringType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", 42L).build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V002".equals(e.code())));
        }

        @Test void stringWhereIntegerExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.IntegerType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", "not a number").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void stringWhereBooleanExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.BooleanType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", "true").build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void booleanWhereStringExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.StringType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", true).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void nullPassesAnyTypeCheck() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.IntegerType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().setNull("x").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void numberWhereIntegerExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.IntegerType())),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", 3.14).build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void integerWhereNumberExpected() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("x", new OdinSchema.SchemaField("x", new OdinSchema.SchemaFieldType.NumberType(null))),
                Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("x", 42L).build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // --- Strict mode edge cases ---

    @Nested class StrictModeEdgeCaseTests {
        @Test void strictModeOnlySchemaFields() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())
            );
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            assertTrue(ValidationEngine.validate(doc, schema, opts).valid());
        }

        @Test void strictModeOneExtraField() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())
            );
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra", "unknown")
                .build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
        }

        @Test void strictModeManyExtraFields() {
            var fields = Map.of(
                "name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())
            );
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), fields, Map.of(), Map.of());
            var doc = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("extra1", "a")
                .set("extra2", "b")
                .set("extra3", "c")
                .build();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            var result = ValidationEngine.validate(doc, schema, opts);
            assertFalse(result.valid());
            assertTrue(result.errors().size() >= 3);
        }

        @Test void strictModeEmptyDocument() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(),
                Map.of("name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())),
                Map.of(), Map.of());
            var doc = OdinDocument.empty();
            var opts = new OdinOptions.ValidateOptions().setStrict(true);
            assertTrue(ValidationEngine.validate(doc, schema, opts).valid());
        }
    }

    // --- Array validation edge cases ---

    @Nested class ArrayValidationEdgeCaseTests {
        @Test void emptyArrayBelowMinimum() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items",
                    new OdinSchema.SchemaFieldType.StringType(), 1L, null)),
                Map.of());
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
        }

        @Test void arrayExactlyAtMinimum() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items",
                    new OdinSchema.SchemaFieldType.StringType(), 2L, null)),
                Map.of());
            var doc = new OdinDocumentBuilder()
                .set("items[0]", "a")
                .set("items[1]", "b")
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void arrayExactlyAtMaximum() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items",
                    new OdinSchema.SchemaFieldType.StringType(), null, 2L)),
                Map.of());
            var doc = new OdinDocumentBuilder()
                .set("items[0]", "a")
                .set("items[1]", "b")
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void arrayOverMaximum() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items",
                    new OdinSchema.SchemaFieldType.StringType(), null, 2L)),
                Map.of());
            var doc = new OdinDocumentBuilder()
                .set("items[0]", "a")
                .set("items[1]", "b")
                .set("items[2]", "c")
                .build();
            assertFalse(validate(doc, schema).valid());
        }

        @Test void arrayMinAndMaxBothSet() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items",
                    new OdinSchema.SchemaFieldType.StringType(), 1L, 3L)),
                Map.of());
            var doc = new OdinDocumentBuilder()
                .set("items[0]", "a")
                .set("items[1]", "b")
                .build();
            assertTrue(validate(doc, schema).valid());
        }
    }

    // --- Cardinality constraints ---

    @Nested class CardinalityConstraintTests {
        @Test void cardinalityMinSatisfied() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(), Map.of(),
                Map.of("", List.of(
                    new OdinSchema.SchemaObjectConstraint.Cardinality(
                        List.of("email", "phone", "fax"), 1L, null)
                )));
            var doc = new OdinDocumentBuilder().set("email", "test@test.com").build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void cardinalityMinNotSatisfied() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(), Map.of(),
                Map.of("", List.of(
                    new OdinSchema.SchemaObjectConstraint.Cardinality(
                        List.of("email", "phone", "fax"), 1L, null)
                )));
            var doc = OdinDocument.empty();
            var result = validate(doc, schema);
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(e -> "V009".equals(e.code())));
        }

        @Test void cardinalityMaxSatisfied() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(), Map.of(),
                Map.of("", List.of(
                    new OdinSchema.SchemaObjectConstraint.Cardinality(
                        List.of("email", "phone", "fax"), null, 2L)
                )));
            var doc = new OdinDocumentBuilder()
                .set("email", "test@test.com")
                .set("phone", "555-1234")
                .build();
            assertTrue(validate(doc, schema).valid());
        }

        @Test void cardinalityMaxExceeded() {
            var schema = new OdinSchema.SchemaDefinition(null, List.of(), Map.of(), Map.of(), Map.of(),
                Map.of("", List.of(
                    new OdinSchema.SchemaObjectConstraint.Cardinality(
                        List.of("email", "phone", "fax"), null, 1L)
                )));
            var doc = new OdinDocumentBuilder()
                .set("email", "test@test.com")
                .set("phone", "555-1234")
                .build();
            var result = validate(doc, schema);
            assertFalse(result.valid());
        }
    }

    // --- Error code constants ---

    @Nested class ErrorCodeConstantsTests {
        @Test void parseErrorCodes() {
            assertEquals("P001", OdinErrors.ParseErrorCodes.UNEXPECTED_CHARACTER);
            assertEquals("P002", OdinErrors.ParseErrorCodes.BARE_STRING_NOT_ALLOWED);
            assertEquals("P003", OdinErrors.ParseErrorCodes.INVALID_ARRAY_INDEX);
            assertEquals("P004", OdinErrors.ParseErrorCodes.UNTERMINATED_STRING);
            assertEquals("P005", OdinErrors.ParseErrorCodes.INVALID_ESCAPE_SEQUENCE);
            assertEquals("P006", OdinErrors.ParseErrorCodes.INVALID_TYPE_PREFIX);
            assertEquals("P007", OdinErrors.ParseErrorCodes.DUPLICATE_PATH_ASSIGNMENT);
            assertEquals("P008", OdinErrors.ParseErrorCodes.INVALID_HEADER_SYNTAX);
            assertEquals("P009", OdinErrors.ParseErrorCodes.INVALID_DIRECTIVE);
            assertEquals("P010", OdinErrors.ParseErrorCodes.MAXIMUM_DEPTH_EXCEEDED);
            assertEquals("P011", OdinErrors.ParseErrorCodes.MAXIMUM_DOCUMENT_SIZE_EXCEEDED);
            assertEquals("P012", OdinErrors.ParseErrorCodes.INVALID_UTF8_SEQUENCE);
            assertEquals("P013", OdinErrors.ParseErrorCodes.NON_CONTIGUOUS_ARRAY_INDICES);
            assertEquals("P014", OdinErrors.ParseErrorCodes.EMPTY_DOCUMENT);
            assertEquals("P015", OdinErrors.ParseErrorCodes.ARRAY_INDEX_OUT_OF_RANGE);
        }

        @Test void validationErrorCodes() {
            assertEquals("V001", OdinErrors.ValidationErrorCodes.REQUIRED_FIELD_MISSING);
            assertEquals("V002", OdinErrors.ValidationErrorCodes.TYPE_MISMATCH);
            assertEquals("V003", OdinErrors.ValidationErrorCodes.VALUE_OUT_OF_BOUNDS);
            assertEquals("V004", OdinErrors.ValidationErrorCodes.PATTERN_MISMATCH);
            assertEquals("V005", OdinErrors.ValidationErrorCodes.INVALID_ENUM_VALUE);
            assertEquals("V006", OdinErrors.ValidationErrorCodes.ARRAY_LENGTH_VIOLATION);
            assertEquals("V007", OdinErrors.ValidationErrorCodes.UNIQUE_CONSTRAINT_VIOLATION);
            assertEquals("V008", OdinErrors.ValidationErrorCodes.INVARIANT_VIOLATION);
            assertEquals("V009", OdinErrors.ValidationErrorCodes.CARDINALITY_CONSTRAINT_VIOLATION);
            assertEquals("V010", OdinErrors.ValidationErrorCodes.CONDITIONAL_REQUIREMENT_NOT_MET);
            assertEquals("V011", OdinErrors.ValidationErrorCodes.UNKNOWN_FIELD);
            assertEquals("V012", OdinErrors.ValidationErrorCodes.CIRCULAR_REFERENCE);
            assertEquals("V013", OdinErrors.ValidationErrorCodes.UNRESOLVED_REFERENCE);
        }

        @Test void parseErrorMessages() {
            assertEquals("Unexpected character", OdinErrors.ParseErrorCodes.message("P001"));
            assertEquals("Unterminated string", OdinErrors.ParseErrorCodes.message("P004"));
            assertEquals("Invalid escape sequence", OdinErrors.ParseErrorCodes.message("P005"));
            assertEquals("Duplicate path assignment", OdinErrors.ParseErrorCodes.message("P007"));
            assertEquals("Empty document", OdinErrors.ParseErrorCodes.message("P014"));
        }

        @Test void validationErrorMessages() {
            assertEquals("Required field missing", OdinErrors.ValidationErrorCodes.message("V001"));
            assertEquals("Type mismatch", OdinErrors.ValidationErrorCodes.message("V002"));
            assertEquals("Value out of bounds", OdinErrors.ValidationErrorCodes.message("V003"));
            assertEquals("Pattern mismatch", OdinErrors.ValidationErrorCodes.message("V004"));
            assertEquals("Invalid enum value", OdinErrors.ValidationErrorCodes.message("V005"));
        }

        @Test void unknownParseErrorCode() {
            assertEquals("Unknown error", OdinErrors.ParseErrorCodes.message("P999"));
        }

        @Test void unknownValidationErrorCode() {
            assertEquals("Unknown error", OdinErrors.ValidationErrorCodes.message("V999"));
        }
    }

    // --- Format validation via schema ---

    @Nested class FormatValidationEdgeCaseTests {
        @Test void emailFormatValid() {
            assertTrue(FormatValidators.validate("user@example.com", "email"));
        }

        @Test void emailFormatInvalid() {
            assertFalse(FormatValidators.validate("not-email", "email"));
        }

        @Test void urlFormatValid() {
            assertTrue(FormatValidators.validate("https://example.com", "url"));
        }

        @Test void urlFormatInvalid() {
            assertFalse(FormatValidators.validate("not-a-url", "url"));
        }

        @Test void uuidFormatValid() {
            assertTrue(FormatValidators.validate("550e8400-e29b-41d4-a716-446655440000", "uuid"));
        }

        @Test void uuidFormatInvalid() {
            assertFalse(FormatValidators.validate("not-a-uuid", "uuid"));
        }

        @Test void unknownFormatAlwaysPasses() {
            assertTrue(FormatValidators.validate("anything", "unknown-format"));
        }

        @Test void emptyValueAlwaysPasses() {
            assertTrue(FormatValidators.validate("", "email"));
            assertTrue(FormatValidators.validate("", "url"));
            assertTrue(FormatValidators.validate("", "uuid"));
        }

        @Test void nullFormatAlwaysPasses() {
            assertTrue(FormatValidators.validate("anything", null));
        }

        @Test void emptyFormatAlwaysPasses() {
            assertTrue(FormatValidators.validate("anything", ""));
        }
    }
}
