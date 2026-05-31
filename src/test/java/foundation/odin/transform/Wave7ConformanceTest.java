package foundation.odin.transform;

import foundation.odin.Odin;
import foundation.odin.serialization.Canonicalize;
import foundation.odin.types.DynValue;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinSchema;
import foundation.odin.parsing.OdinParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-7 second-pass conformance: canonical precision/modifier order,
 * conditional/computed/binary/decimal validation, and lookup onMissing.
 */
class Wave7ConformanceTest {

    private static String canonical(String source) {
        OdinDocument doc = OdinParser.parse(source, ParseOptions.DEFAULT);
        return new String(Canonicalize.serialize(doc), StandardCharsets.UTF_8);
    }

    @Nested
    class CanonicalPrecision {
        @Test void integerBeyondLongRange() {
            assertEquals("huge = ##12345678901234567890\n", canonical("huge = ##12345678901234567890"));
        }

        @Test void integerBeyondSafeRange() {
            assertEquals("big = ##9007199254740993\n", canonical("big = ##9007199254740993"));
        }

        @Test void highPrecisionDecimal() {
            assertEquals("pi = #3.14159265358979323846\n", canonical("pi = #3.14159265358979323846"));
        }

        @Test void currencyLargeIntegerPart() {
            assertEquals("amt = #$12345678901234567890.50\n", canonical("amt = #$12345678901234567890.50"));
        }

        @Test void currencyHighPrecisionFraction() {
            assertEquals("amt = #$123.450000000000000000\n", canonical("amt = #$123.450000000000000000"));
        }

        @Test void currencyWithCodeNotDuplicated() {
            assertEquals("amount = #$100.00:USD\n", canonical("amount = #$100.00:USD"));
        }
    }

    @Nested
    class CanonicalModifierOrder {
        @Test void allThree() {
            assertEquals("x = !-*\"secret\"\n", canonical("x = !-*\"secret\""));
        }

        @Test void normalizesNonCanonicalInput() {
            assertEquals("x = !-*\"secret\"\n", canonical("x = -*!\"secret\""));
        }

        @Test void requiredConfidential() {
            assertEquals("x = !*\"secret\"\n", canonical("x = !*\"secret\""));
        }

        @Test void requiredDeprecated() {
            assertEquals("x = !-\"secret\"\n", canonical("x = !-\"secret\""));
        }
    }

    @Nested
    class ConditionalValidation {
        private static final String IF_SCHEMA =
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Person}\nstatus =\nphone = ! :if status = \"active\"";
        private static final String UNLESS_SCHEMA =
                "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Person}\nstatus =\nphone = ! :unless status = \"inactive\"";

        private boolean valid(String schema, String input) {
            var sd = Odin.parseSchema(schema);
            var doc = Odin.parse(input);
            return Odin.validate(doc, sd).valid();
        }

        @Test void ifConditionTrueRequired() {
            assertFalse(valid(IF_SCHEMA, "{Person}\nstatus = \"active\""));
        }

        @Test void ifConditionFalseNotRequired() {
            assertTrue(valid(IF_SCHEMA, "{Person}\nstatus = \"inactive\""));
        }

        @Test void unlessConditionTrueNotRequired() {
            assertTrue(valid(UNLESS_SCHEMA, "{Person}\nstatus = \"inactive\""));
        }

        @Test void unlessConditionFalseRequired() {
            assertFalse(valid(UNLESS_SCHEMA, "{Person}\nstatus = \"active\""));
        }
    }

    @Nested
    class ComputedExclusion {
        @Test void computedAbsentNotRequired() {
            var schema = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Order}\ntotal = !# :computed";
            var doc = Odin.parse("{Order}\nname = \"x\"");
            assertTrue(Odin.validate(doc, Odin.parseSchema(schema)).valid());
        }
    }

    @Nested
    class BinarySize {
        private boolean validate(String type, String input) {
            var schema = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{R}\nhash = " + type;
            var doc = Odin.parse("{R}\nhash = " + input);
            return Odin.validate(doc, Odin.parseSchema(schema)).valid();
        }

        @Test void exactSizeValid() {
            assertTrue(validate("^:(4)", "^AAAAAA=="));
        }

        @Test void tooSmallFails() {
            assertFalse(validate("^:(4)", "^AAAA"));
        }

        @Test void tooLargeFails() {
            assertFalse(validate("^:(4)", "^AAAAAAA="));
        }

        @Test void sha256WrongSizeFails() {
            assertFalse(validate("^sha256:(32)", "^sha256:AAAAAAAAAAAAAAAAAAAAAA=="));
        }
    }

    @Nested
    class DecimalPlaces {
        private boolean validate(String input) {
            var schema = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{R}\nrate = #.4";
            var doc = Odin.parse("{R}\nrate = " + input);
            return Odin.validate(doc, Odin.parseSchema(schema)).valid();
        }

        @Test void exactPlacesValid() {
            assertTrue(validate("#1.2345"));
        }

        @Test void tooFewFails() {
            assertFalse(validate("#1.23"));
        }

        @Test void tooManyFails() {
            assertFalse(validate("#1.23456"));
        }
    }

    @Nested
    class LookupOnMissing {
        private static final String TABLE =
                "{$table.RATE[code, value]}\n\"A\", \"hundred\"\n";

        private String transform(String onMissing, String matchKey) {
            return "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\n"
                    + "direction = \"json->json\"\ntarget.format = \"json\"\n"
                    + (onMissing != null ? "target.onMissing = \"" + onMissing + "\"\n" : "")
                    + "\n" + TABLE
                    + "\n{Result}\nout = %lookup RATE.value \"" + matchKey + "\"\n";
        }

        private TransformResultLike run(String onMissing, String matchKey) {
            var result = Odin.executeTransform(transform(onMissing, matchKey), DynValue.ofObject(new java.util.ArrayList<>()));
            return new TransformResultLike(result.getErrors().size(), result.getWarnings().size());
        }

        record TransformResultLike(int errors, int warnings) {}

        @Test void hitProducesNoError() {
            var r = run("fail", "A");
            assertEquals(0, r.errors());
        }

        @Test void missDefaultSilent() {
            var r = run(null, "Z");
            assertEquals(0, r.errors());
            assertEquals(0, r.warnings());
        }

        @Test void missFailRaisesError() {
            var r = run("fail", "Z");
            assertEquals(1, r.errors());
        }

        @Test void missWarnRaisesWarning() {
            var r = run("warn", "Z");
            assertEquals(0, r.errors());
            assertEquals(1, r.warnings());
        }
    }
}
