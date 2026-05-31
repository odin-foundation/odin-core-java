package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Conformance for the schema-validator fixes ported from the reference suite.
class SchemaConformanceTest {

    private static OdinSchema.SchemaField field(String schema, String path) {
        return Odin.parseSchema(schema).fields().get(path);
    }

    private static boolean valid(String schema, String input) {
        var doc = input.trim().isEmpty() ? OdinDocument.empty() : Odin.parse(input);
        return Odin.validate(doc, Odin.parseSchema(schema),
                foundation.odin.types.OdinOptions.ValidateOptions.DEFAULT).valid();
    }

    private static OdinSchema.ValidationResult validate(String schema, String input) {
        var doc = input.trim().isEmpty() ? OdinDocument.empty() : Odin.parse(input);
        return Odin.validate(doc, Odin.parseSchema(schema),
                foundation.odin.types.OdinOptions.ValidateOptions.DEFAULT);
    }

    private static String header() {
        return "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n";
    }

    // ── Fix 1: type intersection ──

    @Test
    void intersectionStoresCompositionMember() {
        String schema = header() + "{@hasName}\nname = !\n\n{@hasAge}\nage = !##\n\n{customer}\n= @hasName & @hasAge";
        var comp = field(schema, "customer._composition");
        assertNotNull(comp);
        assertInstanceOf(OdinSchema.SchemaFieldType.TypeRefType.class, comp.fieldType());
        assertEquals("hasName&hasAge", ((OdinSchema.SchemaFieldType.TypeRefType) comp.fieldType()).name());
    }

    @Test
    void intersectionValidatesUnionOfRequiredFields() {
        String schema = header() + "{@hasName}\nname = !\n\n{@hasAge}\nage = !##\n\n{customer}\n= @hasName & @hasAge";
        assertTrue(valid(schema, "{customer}\nname = \"Bob\"\nage = ##5"));
        var missing = validate(schema, "{customer}\nname = \"Bob\"");
        assertFalse(missing.valid());
        assertEquals("V001", missing.errors().get(0).code());
        assertEquals("customer.age", missing.errors().get(0).path());
    }

    @Test
    void intersectionUnresolvedMemberReportsV013() {
        String schema = header() + "{@hasName}\nname = !\n\n{customer}\n= @hasName & @doesNotExist";
        var r = validate(schema, "{customer}\nname = \"Bob\"");
        assertFalse(r.valid());
        assertEquals("V013", r.errors().get(0).code());
    }

    // ── Fix 2: temporal range bounds ──

    @Test
    void temporalBoundsCompareChronologically() {
        String schema = header() + "{root}\nd = date:(2020-06-15..2020-06-20)";
        assertTrue(valid(schema, "{root}\nd = 2020-06-17"));
        assertEquals("V003", validate(schema, "{root}\nd = 2020-06-10").errors().get(0).code());
        assertEquals("V003", validate(schema, "{root}\nd = 2020-06-25").errors().get(0).code());
    }

    @Test
    void temporalBoundsPreservedAsStrings() {
        var f = field(header() + "{root}\nd = date:(2020-06-15..2020-06-20)", "root.d");
        var b = (OdinSchema.SchemaConstraint.Bounds) f.constraints().get(0);
        assertEquals("2020-06-15", b.min());
        assertEquals("2020-06-20", b.max());
    }

    // ── Fix 3: percent type ──

    @Test
    void percentIsFirstClassType() {
        var f = field(header() + "{root}\ntax = #%", "root.tax");
        assertInstanceOf(OdinSchema.SchemaFieldType.PercentType.class, f.fieldType());
    }

    @Test
    void percentValueValidatesAgainstPercentField() {
        String schema = header() + "{root}\ntax = #%";
        assertTrue(valid(schema, "{root}\ntax = #%0.15"));
        assertEquals("V002", validate(schema, "{root}\ntax = \"fifteen\"").errors().get(0).code());
    }

    // ── Fix 4: typed defaults ──

    @Test
    void typedDefaultsAreCaptured() {
        assertDefault(field(header() + "{root}\na = ##3", "root.a"), "integer", 3);
        assertDefault(field(header() + "{root}\nb = #0.05", "root.b"), "number", 0.05);
        assertDefault(field(header() + "{root}\nc = #$5.00", "root.c"), "currency", 5);
        assertDefault(field(header() + "{root}\np = #%0.15", "root.p"), "percent", 0.15);
    }

    @Test
    void defaultTrailingBoundsIsCaptured() {
        var f = field(header() + "{root}\npriority = ##:(1..5) ##3", "root.priority");
        assertDefault(f, "integer", 3);
        var b = (OdinSchema.SchemaConstraint.Bounds) f.constraints().get(0);
        assertEquals("1", b.min());
        assertEquals("5", b.max());
    }

    private static void assertDefault(OdinSchema.SchemaField f, String type, double value) {
        assertNotNull(f.defaultValue(), "expected a default value");
        assertEquals(type, f.defaultValue().type());
        assertEquals(value, ((Number) f.defaultValue().value()).doubleValue(), 1e-9);
    }

    // ── Fix 5: union edge cases ──

    @Test
    void unionKeepsAllMembers() {
        var f = field(header() + "{root}\nu = date|timestamp", "root.u");
        var u = (OdinSchema.SchemaFieldType.UnionType) f.fieldType();
        assertEquals(2, u.types().size());
        assertInstanceOf(OdinSchema.SchemaFieldType.DateType.class, u.types().get(0));
        assertInstanceOf(OdinSchema.SchemaFieldType.TimestampType.class, u.types().get(1));
    }

    @Test
    void unionWithNullMemberAcceptsNull() {
        String schema = header() + "{root}\nn = #|~";
        var f = field(schema, "root.n");
        var u = (OdinSchema.SchemaFieldType.UnionType) f.fieldType();
        assertInstanceOf(OdinSchema.SchemaFieldType.NumberType.class, u.types().get(0));
        assertInstanceOf(OdinSchema.SchemaFieldType.NullType.class, u.types().get(1));
        assertTrue(valid(schema, "{root}\nn = ~"));
    }

    @Test
    void unionDateTimestampAcceptsTimestamp() {
        assertTrue(valid(header() + "{root}\nu = date|timestamp", "{root}\nu = 2020-06-17T10:00:00Z"));
    }

    // ── Fix 6: :if after a pattern constraint ──

    @Test
    void conditionalAfterPatternIsCaptured() {
        var f = field(header() + "{root}\nfield = !:/^[a-z]+$/:if method = paypal", "root.field");
        assertTrue(f.required());
        assertEquals("^[a-z]+$", ((OdinSchema.SchemaConstraint.Pattern) f.constraints().get(0)).pattern());
        assertEquals(1, f.conditionals().size());
        assertEquals("method", f.conditionals().get(0).field());
    }

    @Test
    void patternConditionalEnforcedAndConditional() {
        String schema = header() + "{root}\nfield = !:/^[a-z]+$/:if method = paypal\nmethod = ";
        assertEquals("V010", validate(schema, "{root}\nmethod = \"paypal\"").errors().get(0).code());
        assertTrue(valid(schema, "{root}\nmethod = \"stripe\""));
        assertEquals("V004", validate(schema, "{root}\nfield = \"ABC123\"\nmethod = \"paypal\"").errors().get(0).code());
    }

    // ── Fix 7: glued :computed / :immutable on a temporal type ──

    @Test
    void gluedImmutableKeepsTemporalType() {
        var f = field(header() + "{root}\ncreated_at = !timestamp:immutable", "root.created_at");
        assertInstanceOf(OdinSchema.SchemaFieldType.TimestampType.class, f.fieldType());
        assertTrue(f.required());
        assertTrue(f.immutable());
    }

    @Test
    void gluedComputedKeepsTemporalType() {
        var f = field(header() + "{root}\nstamp = date:computed", "root.stamp");
        assertInstanceOf(OdinSchema.SchemaFieldType.DateType.class, f.fieldType());
        assertTrue(f.computed());
    }

    // ── Fix 8: field-level typeRef recursive validation ──

    @Test
    void fieldTypeRefEnforcesReferencedRequiredFields() {
        String schema = header() + "{@address}\nstreet = !\ncity = !\n\n{customer}\nname = !\nbilling = @address";
        var missing = validate(schema, "{customer}\nname = \"X\"\nbilling.street = \"Main\"");
        assertFalse(missing.valid());
        assertEquals("V001", missing.errors().get(0).code());
        assertEquals("customer.billing.city", missing.errors().get(0).path());

        assertTrue(valid(schema, "{customer}\nname = \"X\""));
        assertTrue(valid(schema, "{customer}\nname = \"X\"\nbilling.street = \"Main\"\nbilling.city = \"NYC\""));
    }

    // ── Fix 9: invariant null operand ──

    @Test
    void invariantNullOperandFails() {
        String schema = header() + "{order}\ntotal = #$\nsubtotal = #$\ntax = ~#$\n:invariant total = subtotal + tax";
        var r = validate(schema, "{order}\ntotal = #$10.00\nsubtotal = #$10.00\ntax = ~");
        assertFalse(r.valid());
        assertEquals("V008", r.errors().get(0).code());
        assertEquals("order", r.errors().get(0).path());
    }

    @Test
    void invariantAllPresentPasses() {
        String schema = header() + "{order}\ntotal = #$\nsubtotal = #$\ntax = #$\n:invariant total = subtotal + tax";
        assertTrue(valid(schema, "{order}\ntotal = #$12.00\nsubtotal = #$10.00\ntax = #$2.00"));
    }

    @Test
    void invariantComparisonNullOperandFails() {
        String schema = header() + "{range}\nstart = ~#\nend = ~#\n:invariant end >= start";
        var r = validate(schema, "{range}\nend = #5\nstart = ~");
        assertFalse(r.valid());
        assertEquals("V008", r.errors().get(0).code());
    }
}
