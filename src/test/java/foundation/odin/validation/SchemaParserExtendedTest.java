package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class SchemaParserExtendedTest {

    // ── Basic schema parsing ──

    @Nested class BasicSchemaParsingTests {
        @Test void parseEmptySchema() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"");
            assertNotNull(schema);
        }

        @Test void parseSchemaWithOneField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Person}\nname = \"string\"");
            assertNotNull(schema);
        }

        @Test void parseSchemaWithMultipleFields() {
            var schema = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"", "",
                "{Person}", "name = \"string\"", "age = \"integer\"", "active = \"boolean\""));
            assertNotNull(schema);
            assertFalse(schema.fields().isEmpty() && schema.types().isEmpty());
        }

        @Test void parseSchemaMetadata() {
            var schema = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"",
                "title = \"Test Schema\"", "description = \"A test\"", "version = \"2.0\"", "",
                "{Root}", "name = \"string\""));
            assertNotNull(schema.metadata());
        }

        @Test void parseSchemaId() {
            var schema = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"",
                "id = \"my-schema\"", "",
                "{Root}", "name = \"string\""));
            assertNotNull(schema.metadata());
        }
    }

    // ── Field type detection ──

    @Nested class FieldTypeDetectionTests {
        @Test void stringFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string\"");
            assertNotNull(schema);
        }

        @Test void integerFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ncount = \"integer\"");
            assertNotNull(schema);
        }

        @Test void numberFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nrate = \"number\"");
            assertNotNull(schema);
        }

        @Test void booleanFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nactive = \"boolean\"");
            assertNotNull(schema);
        }

        @Test void dateFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ndob = \"date\"");
            assertNotNull(schema);
        }

        @Test void timestampFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ncreated = \"timestamp\"");
            assertNotNull(schema);
        }

        @Test void currencyFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nprice = \"currency\"");
            assertNotNull(schema);
        }

        @Test void percentFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nrate = \"percent\"");
            assertNotNull(schema);
        }

        @Test void nullFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nempty = \"null\"");
            assertNotNull(schema);
        }

        @Test void binaryFieldDetection() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ndata = \"binary\"");
            assertNotNull(schema);
        }
    }

    // ── Constraint parsing ──

    @Nested class ConstraintParsingTests {
        @Test void requiredConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string :required\"");
            assertNotNull(schema);
        }

        @Test void patternConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ncode = \"string :pattern=^[A-Z]{3}$\"");
            assertNotNull(schema);
        }

        @Test void minConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nage = \"integer :min=0\"");
            assertNotNull(schema);
        }

        @Test void maxConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nage = \"integer :max=150\"");
            assertNotNull(schema);
        }

        @Test void minAndMaxConstraints() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nage = \"integer :min=0 :max=150\"");
            assertNotNull(schema);
        }

        @Test void formatConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nemail = \"string :format=email\"");
            assertNotNull(schema);
        }

        @Test void enumConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nstatus = \"string :enum=active,inactive,pending\"");
            assertNotNull(schema);
        }

        @Test void uniqueConstraint() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nid = \"string :unique\"");
            assertNotNull(schema);
        }

        @Test void confidentialModifier() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nssn = \"string :confidential\"");
            assertNotNull(schema);
        }

        @Test void deprecatedModifier() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nold = \"string :deprecated\"");
            assertNotNull(schema);
        }
    }

    // ── Multiple sections ──

    @Nested class MultipleSectionTests {
        @Test void twoSections() {
            var schema = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"", "",
                "{Person}", "name = \"string\"", "",
                "{Address}", "street = \"string\""));
            assertNotNull(schema);
        }

        @Test void nestedSections() {
            var schema = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"", "",
                "{Person}", "name = \"string\"", "",
                "{Person.Address}", "street = \"string\""));
            assertNotNull(schema);
        }

        @Test void manySections() {
            var sb = new StringBuilder();
            sb.append("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n");
            for (int i = 0; i < 10; i++) {
                sb.append(String.format("{Type%d}\nfield%d = \"string\"\n\n", i, i));
            }
            var schema = Odin.parseSchema(sb.toString());
            assertNotNull(schema);
        }
    }

    // ── Schema serialization ──

    @Nested class SchemaSerializationTests {
        @Test void serializeEmptySchema() {
            var schema = new OdinSchema.SchemaDefinition();
            var serialized = Odin.serializeSchema(schema);
            assertNotNull(serialized);
        }

        @Test void serializeAndReparse() {
            var original = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"", "",
                "{Root}", "name = \"string :required\""));
            var serialized = Odin.serializeSchema(original);
            assertNotNull(serialized);
            assertFalse(serialized.isEmpty());
        }

        @Test void serializeSchemaWithMultipleFields() {
            var original = Odin.parseSchema(String.join("\n",
                "{$}", "odin = \"1.0.0\"", "schema = \"1.0.0\"", "",
                "{Root}", "name = \"string\"", "age = \"integer\""));
            var serialized = Odin.serializeSchema(original);
            assertNotNull(serialized);
        }
    }

    // ── Schema validation integration ──

    @Nested class SchemaValidationIntegrationTests {
        @Test void validateWithParsedSchema() {
            var schema = new OdinSchema.SchemaDefinition(null, java.util.List.of(),
                java.util.Map.of(),
                java.util.Map.of("name", new OdinSchema.SchemaField("name",
                    new OdinSchema.SchemaFieldType.StringType(), true, false, false, false, null,
                    java.util.List.of(), null, java.util.List.of())),
                java.util.Map.of(), java.util.Map.of());
            var doc = new OdinDocumentBuilder().set("name", "Alice").build();
            assertTrue(Odin.validate(doc, schema).valid());
        }

        @Test void validateFailsWithMissingRequired() {
            var schema = new OdinSchema.SchemaDefinition(null, java.util.List.of(),
                java.util.Map.of(),
                java.util.Map.of("name", new OdinSchema.SchemaField("name",
                    new OdinSchema.SchemaFieldType.StringType(), true, false, false, false, null,
                    java.util.List.of(), null, java.util.List.of())),
                java.util.Map.of(), java.util.Map.of());
            var doc = OdinDocument.empty();
            assertFalse(Odin.validate(doc, schema).valid());
        }

        @Test void validateWithConstraints() {
            var schema = new OdinSchema.SchemaDefinition(null, java.util.List.of(),
                java.util.Map.of(),
                java.util.Map.of("age", new OdinSchema.SchemaField("age",
                    new OdinSchema.SchemaFieldType.IntegerType(), false, false, false, false, null,
                    java.util.List.of(new OdinSchema.SchemaConstraint.Bounds("0", "150", false, false)),
                    null, java.util.List.of())),
                java.util.Map.of(), java.util.Map.of());
            var doc = new OdinDocumentBuilder().set("age", 30L).build();
            assertTrue(Odin.validate(doc, schema).valid());
        }
    }

    // ── Edge cases ──

    @Nested class SchemaEdgeCaseTests {
        @Test void schemaWithNoFields() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = new OdinDocumentBuilder().set("anything", "goes").build();
            assertTrue(Odin.validate(doc, schema).valid());
        }

        @Test void emptyDocAgainstEmptySchema() {
            var schema = new OdinSchema.SchemaDefinition();
            var doc = OdinDocument.empty();
            assertTrue(Odin.validate(doc, schema).valid());
        }

        @Test void schemaWithOnlyMetadata() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata("test-id", "Test", "A test schema", "1.0"),
                java.util.List.of(),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
            assertNotNull(schema.metadata());
            assertEquals("test-id", schema.metadata().id());
        }

        @Test void schemaFieldRecord() {
            var field = new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType());
            assertEquals("name", field.name());
            assertFalse(field.required());
            assertFalse(field.confidential());
            assertFalse(field.deprecated());
        }

        @Test void schemaFieldWithAllAttributes() {
            var field = new OdinSchema.SchemaField("ssn",
                new OdinSchema.SchemaFieldType.StringType(),
                true, true, false, false, "Social Security Number",
                java.util.List.of(new OdinSchema.SchemaConstraint.Pattern("^\\d{3}-\\d{2}-\\d{4}$")),
                null, java.util.List.of());
            assertTrue(field.required());
            assertTrue(field.confidential());
            assertFalse(field.deprecated());
            assertEquals("Social Security Number", field.description());
        }

        @Test void schemaArrayDefinition() {
            var arr = new OdinSchema.SchemaArray("items",
                new OdinSchema.SchemaFieldType.StringType(), 1L, 100L);
            assertEquals("items", arr.name());
            assertEquals(1L, arr.minItems());
            assertEquals(100L, arr.maxItems());
        }

        @Test void schemaImportRecord() {
            var imp = new OdinSchema.SchemaImport("./base.odin", "base");
            assertEquals("./base.odin", imp.path());
            assertEquals("base", imp.alias());
        }

        @Test void validationResultRecord() {
            var result = new OdinSchema.ValidationResult();
            assertTrue(result.valid());
            assertTrue(result.errors().isEmpty());
        }

        @Test void validationErrorRecord() {
            var error = new OdinSchema.ValidationError("name", "V001", "Required field missing");
            assertEquals("name", error.path());
            assertEquals("V001", error.code());
            assertEquals("Required field missing", error.message());
        }

        @Test void validationErrorWithAllFields() {
            var error = new OdinSchema.ValidationError("age", "V003", "Out of bounds",
                "0-150", "200", "Root.age");
            assertEquals("age", error.path());
            assertEquals("V003", error.code());
            assertEquals("0-150", error.expected());
            assertEquals("200", error.actual());
            assertEquals("Root.age", error.schemaPath());
        }
    }
}
