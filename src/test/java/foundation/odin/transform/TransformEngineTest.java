package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinTransformTypes.*;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransformEngineTest {

    // ── Helpers ──

    private static OdinTransform minimalTransform(List<FieldMapping> mappings) {
        var t = new OdinTransform();
        t.getTarget().setFormat("json");
        var seg = new TransformSegment();
        seg.setMappings(mappings);
        t.setSegments(List.of(seg));
        return t;
    }

    private static DynValue obj(Map.Entry<String, DynValue>... entries) {
        return DynValue.ofObject(List.of(entries));
    }

    @SafeVarargs
    private static DynValue obj(Map.Entry<String, DynValue>[]... groups) {
        var all = new ArrayList<Map.Entry<String, DynValue>>();
        for (var g : groups) all.addAll(List.of(g));
        return DynValue.ofObject(all);
    }

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }
    private static DynValue arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }
    private static DynValue str(String s) { return DynValue.ofString(s); }
    private static DynValue integer(long i) { return DynValue.ofInteger(i); }
    private static DynValue flt(double d) { return DynValue.ofFloat(d); }
    private static DynValue bool(boolean b) { return DynValue.ofBool(b); }
    private static DynValue nul() { return DynValue.ofNull(); }

    private static FieldMapping copyMapping(String target, String path) {
        var m = new FieldMapping();
        m.setTarget(target);
        m.setExpression(FieldExpression.copy(path));
        return m;
    }

    private static FieldMapping literalMapping(String target, OdinValue value) {
        var m = new FieldMapping();
        m.setTarget(target);
        m.setExpression(FieldExpression.literal(value));
        return m;
    }

    private static FieldMapping verbMapping(String target, String verb, VerbArg... args) {
        var call = new VerbCall();
        call.setVerb(verb);
        call.setArgs(List.of(args));
        var m = new FieldMapping();
        m.setTarget(target);
        m.setExpression(FieldExpression.transform(call));
        return m;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Simple Copy
    // ═════════════════════════════════════════════════════════════════

    @Nested class SimpleCopyTests {

        @Test void simpleCopy() {
            var t = minimalTransform(List.of(copyMapping("Name", "@.name")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            assertEquals(str("Alice"), result.getOutput().get("Name"));
        }

        @Test void nestedCopy() {
            var t = minimalTransform(List.of(copyMapping("City", "@.address.city")));
            var source = DynValue.ofObject(List.of(kv("address",
                    DynValue.ofObject(List.of(kv("city", str("Springfield")))))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("Springfield"), result.getOutput().get("City"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Literals
    // ═════════════════════════════════════════════════════════════════

    @Nested class LiteralTests {

        @Test void literalString() {
            var t = minimalTransform(List.of(literalMapping("Version", OdinValue.ofString("1.0"))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            assertEquals(str("1.0"), result.getOutput().get("Version"));
        }

        @Test void literalInteger() {
            var t = minimalTransform(List.of(literalMapping("Count", OdinValue.ofInteger(42))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            assertEquals(integer(42), result.getOutput().get("Count"));
        }

        @Test void literalBoolean() {
            var t = minimalTransform(List.of(literalMapping("Active", OdinValue.ofBoolean(true))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            assertEquals(bool(true), result.getOutput().get("Active"));
        }

        @Test void literalNull() {
            var t = minimalTransform(List.of(literalMapping("Empty", OdinValue.ofNull())));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Empty");
            assertTrue(v == null || v.isNull());
        }

        @Test void literalNumber() {
            var t = minimalTransform(List.of(literalMapping("Pi", new OdinValue.OdinNumber(3.14))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            var pi = result.getOutput().get("Pi");
            assertNotNull(pi);
            assertEquals(3.14, pi.asDouble(), 0.001);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Verbs
    // ═════════════════════════════════════════════════════════════════

    @Nested class VerbTests {

        @Test void verbUpper() {
            var t = minimalTransform(List.of(verbMapping("Name", "upper", VerbArg.ref("@.name"))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("alice")))));
            assertTrue(result.isSuccess());
            assertEquals(str("ALICE"), result.getOutput().get("Name"));
        }

        @Test void verbLower() {
            var t = minimalTransform(List.of(verbMapping("Name", "lower", VerbArg.ref("@.name"))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("ALICE")))));
            assertTrue(result.isSuccess());
            assertEquals(str("alice"), result.getOutput().get("Name"));
        }

        @Test void verbConcat() {
            var t = minimalTransform(List.of(verbMapping("FullName", "concat",
                    VerbArg.ref("@.first"), VerbArg.lit(OdinValue.ofString(" ")), VerbArg.ref("@.last"))));
            var source = DynValue.ofObject(List.of(kv("first", str("John")), kv("last", str("Doe"))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("John Doe"), result.getOutput().get("FullName"));
        }

        @Test void nestedVerbUpperOfConcat() {
            var innerCall = new VerbCall();
            innerCall.setVerb("concat");
            innerCall.setArgs(List.of(VerbArg.ref("@.first"), VerbArg.lit(OdinValue.ofString(" ")), VerbArg.ref("@.last")));

            var outerCall = new VerbCall();
            outerCall.setVerb("upper");
            outerCall.setArgs(List.of(VerbArg.nestedCall(innerCall)));

            var m = new FieldMapping();
            m.setTarget("Name");
            m.setExpression(FieldExpression.transform(outerCall));

            var t = minimalTransform(List.of(m));
            var source = DynValue.ofObject(List.of(kv("first", str("john")), kv("last", str("doe"))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("JOHN DOE"), result.getOutput().get("Name"));
        }

        @Test void unknownVerbProducesError() {
            var t = minimalTransform(List.of(verbMapping("Out", "nonexistent_verb", VerbArg.ref("@.x"))));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("x", str("val")))));
            assertTrue(result.getErrors().size() > 0);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Constants
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConstantTests {

        @Test void constantString() {
            var t = minimalTransform(List.of(copyMapping("Version", "$const.version")));
            t.getConstants().put("version", OdinValue.ofString("2.0"));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            assertEquals(str("2.0"), result.getOutput().get("Version"));
        }

        @Test void constantInteger() {
            var t = minimalTransform(List.of(copyMapping("Max", "$const.max")));
            t.getConstants().put("max", OdinValue.ofInteger(100));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            assertEquals(integer(100), result.getOutput().get("Max"));
        }

        @Test void constantMissingReturnsNull() {
            var t = minimalTransform(List.of(copyMapping("X", "$const.missing")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            var x = result.getOutput().get("X");
            assertTrue(x == null || x.isNull());
        }

        @Test void constantUsedInVerb() {
            var t = minimalTransform(List.of(verbMapping("Result", "concat",
                    VerbArg.ref("@.name"), VerbArg.ref("$const.suffix"))));
            t.getConstants().put("suffix", OdinValue.ofString("_END"));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("test")))));
            assertTrue(result.isSuccess());
            assertEquals(str("test_END"), result.getOutput().get("Result"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Array Index Paths
    // ═════════════════════════════════════════════════════════════════

    @Nested class ArrayIndexTests {

        @Test void arrayIndexPath() {
            var t = minimalTransform(List.of(copyMapping("First", "@.items[0].name")));
            var source = DynValue.ofObject(List.of(kv("items", arr(
                    DynValue.ofObject(List.of(kv("name", str("Alpha")))),
                    DynValue.ofObject(List.of(kv("name", str("Beta"))))))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("Alpha"), result.getOutput().get("First"));
        }

        @Test void arrayIndexSecondElement() {
            var t = minimalTransform(List.of(copyMapping("Second", "@.items[1].name")));
            var source = DynValue.ofObject(List.of(kv("items", arr(
                    DynValue.ofObject(List.of(kv("name", str("Alpha")))),
                    DynValue.ofObject(List.of(kv("name", str("Beta"))))))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("Beta"), result.getOutput().get("Second"));
        }

        @Test void arrayIndexOutOfBoundsReturnsNull() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.items[99]")));
            var source = DynValue.ofObject(List.of(kv("items", arr(str("a")))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Out");
            assertTrue(v == null || v.isNull());
        }

        @Test void arrayIndexOnNonArrayReturnsNull() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.x[0]")));
            var source = DynValue.ofObject(List.of(kv("x", str("notarray"))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Out");
            assertTrue(v == null || v.isNull());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Missing Paths
    // ═════════════════════════════════════════════════════════════════

    @Nested class MissingPathTests {

        @Test void missingPathReturnsNull() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.nonexistent")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Out");
            assertTrue(v == null || v.isNull());
        }

        @Test void deepMissingPathReturnsNull() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.a.b.c.d")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("a", str("flat")))));
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Out");
            assertTrue(v == null || v.isNull());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Nested Output Paths
    // ═════════════════════════════════════════════════════════════════

    @Nested class NestedOutputTests {

        @Test void nestedOutputPath() {
            var t = minimalTransform(List.of(copyMapping("address.city", "@.city")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("city", str("Portland")))));
            assertTrue(result.isSuccess());
            var addr = result.getOutput().get("address");
            assertNotNull(addr);
            assertEquals(str("Portland"), addr.get("city"));
        }

        @Test void deepNestedOutputPath() {
            var t = minimalTransform(List.of(copyMapping("a.b.c.d", "@.x")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("x", str("deep")))));
            assertTrue(result.isSuccess());
            var a = result.getOutput().get("a");
            assertNotNull(a);
            var b = a.get("b");
            assertNotNull(b);
            var c = b.get("c");
            assertNotNull(c);
            assertEquals(str("deep"), c.get("d"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Conditional Segments
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConditionalTests {

        @Test void conditionTrueExecutesSegment() {
            var seg = new TransformSegment();
            seg.setCondition("@.active");
            seg.setMappings(List.of(copyMapping("Name", "@.name")));

            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of(seg));

            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("active", bool(true))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            assertEquals(str("Alice"), result.getOutput().get("Name"));
        }

        @Test void conditionFalseSkipsSegment() {
            var seg = new TransformSegment();
            seg.setCondition("@.active");
            seg.setMappings(List.of(copyMapping("Name", "@.name")));

            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of(seg));

            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("active", bool(false))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("Name");
            assertTrue(v == null || v.isNull());
        }

        private DynValue runWithCondition(String condition, DynValue source) {
            var seg = new TransformSegment();
            seg.setCondition(condition);
            seg.setMappings(List.of(copyMapping("Name", "@.name")));
            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of(seg));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            return result.getOutput().get("Name");
        }

        @Test void booleanEqualityConditionTrue() {
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("hasDui", bool(true))));
            assertEquals(str("Alice"), runWithCondition("@.hasDui = true", source));
        }

        @Test void booleanEqualityConditionFalse() {
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("hasDui", bool(false))));
            var v = runWithCondition("@.hasDui = true", source);
            assertTrue(v == null || v.isNull());
        }

        @Test void stringEqualityCondition() {
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("status", str("active"))));
            assertEquals(str("Alice"), runWithCondition("@.status = 'active'", source));
        }

        @Test void numericGreaterThanCondition() {
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("amount", DynValue.ofInteger(100))));
            assertEquals(str("Alice"), runWithCondition("@.amount > 50", source));
            var below = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("amount", DynValue.ofInteger(10))));
            var v = runWithCondition("@.amount > 50", below);
            assertTrue(v == null || v.isNull());
        }

        @Test void notEqualCondition() {
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("type", str("STANDARD"))));
            assertEquals(str("Alice"), runWithCondition("@.type != 'VOID'", source));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Verb-expression conditions
    // ═════════════════════════════════════════════════════════════════

    @Nested class VerbConditionTests {

        private DynValue run(String transformText, String json) {
            var transform = TransformParser.parse(transformText);
            var input = JsonSourceParser.parse(json);
            var result = TransformEngine.execute(transform, input);
            assertTrue(result.isSuccess(), () -> "errors: " + result.getErrors().stream()
                    .map(e -> e.getCode() + ":" + e.getMessage()).toList());
            return result.getOutput();
        }

        @Test void headerInlineVerbConditionTrue() {
            var t = """
{$}
direction = "json->json"

{Quote}
DriverName = @driver.name

{HighRisk :if %and @driver.hasDui %lt @driver.age ##25}
flag = "high-risk"
""";
            var out = run(t, "{\"driver\":{\"name\":\"Pat\",\"hasDui\":true,\"age\":22}}");
            assertNotNull(out.get("HighRisk"));
        }

        @Test void headerInlineVerbConditionFalse() {
            var t = """
{$}
direction = "json->json"

{Quote}
DriverName = @driver.name

{HighRisk :if %and @driver.hasDui %lt @driver.age ##25}
flag = "high-risk"
""";
            var out = run(t, "{\"driver\":{\"name\":\"Sam\",\"hasDui\":true,\"age\":40}}");
            assertNotNull(out.get("Quote"));
            var hr = out.get("HighRisk");
            assertTrue(hr == null || hr.isNull());
        }

        @Test void bodyLineVerbCondition() {
            var t = """
{$}
direction = "json->json"

{Dui}
_if = %eq @driver.state "TX"
state = @driver.state
""";
            var present = run(t, "{\"driver\":{\"state\":\"TX\"}}");
            assertNotNull(present.get("Dui"));
            var absent = run(t, "{\"driver\":{\"state\":\"CA\"}}");
            var dui = absent.get("Dui");
            assertTrue(dui == null || dui.isNull());
        }

        @Test void orVerbCondition() {
            var t = """
{$}
direction = "json->json"

{Flag :if %or @a @b}
v = "yes"
""";
            assertNotNull(run(t, "{\"a\":false,\"b\":true}").get("Flag"));
            var none = run(t, "{\"a\":false,\"b\":false}").get("Flag");
            assertTrue(none == null || none.isNull());
        }

        @Test void notVerbCondition() {
            var t = """
{$}
direction = "json->json"

{Flag :if %not @disabled}
v = "yes"
""";
            assertNotNull(run(t, "{\"disabled\":false}").get("Flag"));
            var off = run(t, "{\"disabled\":true}").get("Flag");
            assertTrue(off == null || off.isNull());
        }

        @Test void referenceTruthyCondition() {
            var t = """
{$}
direction = "json->json"

{Dui :if @driver.hasDui}
v = "yes"
""";
            assertNotNull(run(t, "{\"driver\":{\"hasDui\":true}}").get("Dui"));
            var off = run(t, "{\"driver\":{\"hasDui\":false}}").get("Dui");
            assertTrue(off == null || off.isNull());
        }

        @Test void legacyQuotedInfixCondition() {
            var t = """
{$}
direction = "json->json"

{Dui}
_if = "@driver.has_dui = true"
state = @driver.state
""";
            var present = run(t, "{\"driver\":{\"has_dui\":true,\"state\":\"TX\"}}");
            assertNotNull(present.get("Dui"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Conditional chains (if/elif/else)
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConditionalChainTests {

        private static final String CHAIN = """
{$}
direction = "json->json"

{HighRisk :if %eq @driver.tier "dui"}
band = "high-risk"

{YoungDriver :elif %lt @driver.age ##25}
band = "young-driver"

{Standard :else}
band = "standard"
""";

        private List<String> bands(String json) {
            var result = TransformEngine.execute(TransformParser.parse(CHAIN), JsonSourceParser.parse(json));
            assertTrue(result.isSuccess());
            var keys = new ArrayList<String>();
            for (var e : result.getOutput().asObject()) keys.add(e.getKey());
            return keys;
        }

        @Test void takesIfBranchAndSkipsRest() {
            assertEquals(List.of("HighRisk"), bands("{\"driver\":{\"tier\":\"dui\",\"age\":30}}"));
        }

        @Test void fallsThroughToMatchingElif() {
            assertEquals(List.of("YoungDriver"), bands("{\"driver\":{\"tier\":\"std\",\"age\":20}}"));
        }

        @Test void fallsThroughToElse() {
            assertEquals(List.of("Standard"), bands("{\"driver\":{\"tier\":\"std\",\"age\":40}}"));
        }

        @Test void orphanElifRaisesT012() {
            var t = """
{$}
direction = "json->json"

{A}
x = "1"

{B :elif %eq @y "z"}
v = "2"
""";
            var result = TransformEngine.execute(TransformParser.parse(t), JsonSourceParser.parse("{\"y\":\"q\"}"));
            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> "T012".equals(e.getCode())));
        }

        @Test void orphanElseRaisesT012() {
            var t = """
{$}
direction = "json->json"

{A}
x = "1"

{B :else}
v = "2"
""";
            var result = TransformEngine.execute(TransformParser.parse(t), JsonSourceParser.parse("{}"));
            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream()
                    .anyMatch(e -> "T012".equals(e.getCode())));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Modifiers
    // ═════════════════════════════════════════════════════════════════

    @Nested class ModifierTests {

        @Test void requiredModifierRecorded() {
            var m = copyMapping("Name", "@.name");
            m.setModifiers(new OdinModifiers(true, false, false, false));
            var t = minimalTransform(List.of(m));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            assertTrue(result.getOutputModifiers().containsKey("Name"));
            assertTrue(result.getOutputModifiers().get("Name").isRequired());
        }

        @Test void confidentialModifierRecorded() {
            var m = copyMapping("SSN", "@.ssn");
            m.setModifiers(new OdinModifiers(false, true, false, false));
            var t = minimalTransform(List.of(m));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("ssn", str("123-45-6789")))));
            assertTrue(result.isSuccess());
            assertTrue(result.getOutputModifiers().containsKey("SSN"));
            assertTrue(result.getOutputModifiers().get("SSN").isConfidential());
        }

        @Test void deprecatedModifierRecorded() {
            var m = copyMapping("Old", "@.old");
            m.setModifiers(new OdinModifiers(false, false, true, false));
            var t = minimalTransform(List.of(m));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("old", str("legacy")))));
            assertTrue(result.isSuccess());
            assertTrue(result.getOutputModifiers().containsKey("Old"));
            assertTrue(result.getOutputModifiers().get("Old").isDeprecated());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Confidential Enforcement
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConfidentialEnforcementTests {

        @Test void redactModeNullsConfidentialString() {
            var m = copyMapping("SSN", "@.ssn");
            m.setModifiers(new OdinModifiers(false, true, false, false));
            var t = minimalTransform(List.of(m));
            t.setEnforceConfidential(ConfidentialMode.REDACT);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("ssn", str("123-45-6789")))));
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("SSN");
            assertTrue(v == null || v.isNull());
        }

        @Test void maskModeMasksConfidentialString() {
            var m = copyMapping("SSN", "@.ssn");
            m.setModifiers(new OdinModifiers(false, true, false, false));
            var t = minimalTransform(List.of(m));
            t.setEnforceConfidential(ConfidentialMode.MASK);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("ssn", str("123-45-6789")))));
            assertTrue(result.isSuccess());
            var v = result.getOutput().get("SSN");
            assertNotNull(v);
            // Masked string should be asterisks of same length
            assertTrue(v.asString() != null && v.asString().contains("*"));
        }

        @Test void nonConfidentialFieldUnchanged() {
            var m = copyMapping("Name", "@.name");
            var t = minimalTransform(List.of(m));
            t.setEnforceConfidential(ConfidentialMode.REDACT);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            assertEquals(str("Alice"), result.getOutput().get("Name"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Loop Segments
    // ═════════════════════════════════════════════════════════════════

    @Nested class LoopTests {

        @Test void loopOverArray() {
            var seg = new TransformSegment();
            seg.setName("items");
            seg.setPath("items");
            seg.setIsArray(true);
            seg.setSourcePath("@.data");
            seg.setMappings(List.of(copyMapping("name", "@_item.name")));

            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of(seg));

            var source = DynValue.ofObject(List.of(kv("data", arr(
                    DynValue.ofObject(List.of(kv("name", str("A")))),
                    DynValue.ofObject(List.of(kv("name", str("B"))))))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            var items = result.getOutput().get("items");
            assertNotNull(items);
            var list = items.asArray();
            assertNotNull(list);
            assertEquals(2, list.size());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Empty / Edge Cases
    // ═════════════════════════════════════════════════════════════════

    @Nested class EdgeCaseTests {

        @Test void emptyTransformProducesEmptyOutput() {
            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of());
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
        }

        @Test void nullSourceHandledGracefully() {
            var t = minimalTransform(List.of(copyMapping("X", "@.x")));
            var result = TransformEngine.execute(t, DynValue.ofNull());
            assertTrue(result.isSuccess());
        }

        @Test void copyEntireSource() {
            var t = minimalTransform(List.of(copyMapping("Data", "@")));
            var source = DynValue.ofObject(List.of(kv("name", str("Alice")), kv("age", integer(30))));
            var result = TransformEngine.execute(t, source);
            assertTrue(result.isSuccess());
            var data = result.getOutput().get("Data");
            assertNotNull(data);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Named Segments
    // ═════════════════════════════════════════════════════════════════

    @Nested class NamedSegmentTests {

        @Test void namedSegmentCreatesNestedObject() {
            var seg = new TransformSegment();
            seg.setName("Person");
            seg.setPath("Person");
            seg.setMappings(List.of(copyMapping("Name", "@.name")));

            var t = new OdinTransform();
            t.getTarget().setFormat("json");
            t.setSegments(List.of(seg));

            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            var person = result.getOutput().get("Person");
            assertNotNull(person);
            assertEquals(str("Alice"), person.get("Name"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Copy All Value Types
    // ═════════════════════════════════════════════════════════════════

    @Nested class CopyValueTypeTests {

        @Test void copiesString() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.v")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("v", str("hello")))));
            assertEquals(str("hello"), result.getOutput().get("Out"));
        }

        @Test void copiesInteger() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.v")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("v", integer(42)))));
            assertEquals(integer(42), result.getOutput().get("Out"));
        }

        @Test void copiesFloat() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.v")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("v", flt(3.14)))));
            assertNotNull(result.getOutput().get("Out"));
            assertEquals(3.14, result.getOutput().get("Out").asDouble(), 0.001);
        }

        @Test void copiesBoolean() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.v")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("v", bool(true)))));
            assertEquals(bool(true), result.getOutput().get("Out"));
        }

        @Test void copiesNull() {
            var t = minimalTransform(List.of(copyMapping("Out", "@.v")));
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("v", nul()))));
            var v = result.getOutput().get("Out");
            assertTrue(v == null || v.isNull());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  End-to-End (Parse + Execute)
    // ═════════════════════════════════════════════════════════════════

    @Nested class EndToEndTests {

        @Test void parseAndExecuteLiteralString() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Data}
                    Greeting = "Hello World"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of()));
            assertTrue(result.isSuccess());
            // Literal should be set
            assertNotNull(result.getOutput());
        }

        @Test void parseAndExecuteCopy() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Person}
                    Name = "@.name"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            var person = result.getOutput().get("Person");
            assertNotNull(person);
            assertEquals(str("Alice"), person.get("Name"));
        }

        @Test void parseAndExecuteVerb() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Data}
                    Upper = "%upper @.name"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("alice")))));
            assertTrue(result.isSuccess());
            var data = result.getOutput().get("Data");
            assertNotNull(data);
            assertEquals(str("ALICE"), data.get("Upper"));
        }

        @Test void interpolatesPathExpression() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Data}
                    Greeting = "Hi ${@.name}!"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("name", str("Alice")))));
            assertTrue(result.isSuccess());
            assertEquals(str("Hi Alice!"), result.getOutput().get("Data").get("Greeting"));
        }

        @Test void escapedInterpolationStaysLiteral() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Data}
                    Template = "Use \\${@.field} here"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("field", str("X")))));
            assertTrue(result.isSuccess());
            assertEquals(str("Use ${@.field} here"), result.getOutput().get("Data").get("Template"));
        }

        @Test void escapedDollarBeforeInterpolation() {
            var transformText = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "json->json"
                    target.format = "json"

                    {Data}
                    Price = "Total: \\$${@.amount}"
                    """;
            var t = TransformParser.parse(transformText);
            var result = TransformEngine.execute(t, DynValue.ofObject(List.of(kv("amount", str("42.00")))));
            assertTrue(result.isSuccess());
            assertEquals(str("Total: $42.00"), result.getOutput().get("Data").get("Price"));
        }
    }
}
