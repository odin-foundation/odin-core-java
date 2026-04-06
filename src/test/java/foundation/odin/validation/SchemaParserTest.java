package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class SchemaParserTest {

    // ── Basic parsing ──

    @Nested class BasicTests {
        @Test void parseSimpleSchema() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n" +
                "{Person}\nname = \"string :required\"\nage = \"integer\"");
            assertNotNull(schema);
        }

        @Test void parseSchemaWithMetadata() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\ntitle = \"Test Schema\"\n\n" +
                "{Person}\nname = \"string\"");
            assertNotNull(schema.metadata());
        }

        @Test void parseEmptySchema() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"");
            assertNotNull(schema);
        }
    }

    // ── Field types ──

    @Nested class FieldTypeTests {
        @Test void stringField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string\"");
            assertNotNull(schema);
            assertFalse(schema.types().isEmpty() && schema.fields().isEmpty());
        }

        @Test void integerField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ncount = \"integer\"");
            assertNotNull(schema);
        }

        @Test void numberField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nrate = \"number\"");
            assertNotNull(schema);
        }

        @Test void booleanField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nactive = \"boolean\"");
            assertNotNull(schema);
        }

        @Test void dateField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ndob = \"date\"");
            assertNotNull(schema);
        }

        @Test void timestampField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nts = \"timestamp\"");
            assertNotNull(schema);
        }

        @Test void currencyField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nprice = \"currency\"");
            assertNotNull(schema);
        }

        @Test void percentField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nrate = \"percent\"");
            assertNotNull(schema);
        }

        @Test void nullField() {
            var schema = Odin.parseSchema("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nempty = \"null\"");
            assertNotNull(schema);
        }
    }

    // ── Constraints ──

    @Nested class ConstraintTests {
        @Test void requiredModifier() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string :required\"");
            assertNotNull(schema);
        }

        @Test void boundsConstraint() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nage = \"integer :min=0 :max=150\"");
            assertNotNull(schema);
        }

        @Test void patternConstraint() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\ncode = \"string :pattern=^[A-Z]{3}$\"");
            assertNotNull(schema);
        }

        @Test void formatConstraint() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nemail = \"string :format=email\"");
            assertNotNull(schema);
        }

        @Test void enumConstraint() {
            var schema = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nstatus = \"string :enum=active,inactive,pending\"");
            assertNotNull(schema);
        }
    }

    // ── Roundtrip ──

    @Nested class RoundtripTests {
        @Test void serializeAndReparse() {
            var original = Odin.parseSchema(
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string :required\"");
            var serialized = Odin.serializeSchema(original);
            assertNotNull(serialized);
            assertFalse(serialized.isEmpty());
        }
    }
}
