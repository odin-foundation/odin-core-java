package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Nested :loop directives in transform segments: a segment may declare multiple
// `:loop path :as alias` lines to iterate a cross-product.
class NestedLoopsTest {

    private static final String HEADER = """
            {$}
            odin = "1.0.0"
            transform = "1.0.0"
            direction = "json->json"
            target.format = "json"
            """;

    private static foundation.odin.types.OdinTransformTypes.TransformResult run(String body, String json) {
        var transform = TransformParser.parse(HEADER + "\n" + body);
        var source = JsonSourceParser.parse(json);
        return TransformEngine.execute(transform, source);
    }

    private static DynValue rows(foundation.odin.types.OdinTransformTypes.TransformResult r) {
        return r.getOutput().get("rows");
    }

    // ── Parser ──

    @Nested
    class ParserTests {
        @Test
        void preservesTwoLoopDirectivesWithAliases() {
            var t = TransformParser.parse(HEADER + """

                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    vin = "@veh.vin"
                    code = "@cov.code"
                    """);
            var seg = t.getSegments().get(0);
            var loops = seg.getDirectives().stream()
                    .filter(d -> "loop".equals(d.getDirectiveType())).toList();
            assertEquals(2, loops.size());
            assertEquals("vehicles", loops.get(0).getValue());
            assertEquals("veh", loops.get(0).getAlias());
            assertEquals(".coverages", loops.get(1).getValue());
            assertEquals("cov", loops.get(1).getAlias());
        }

        @Test
        void preservesThreeLoopDirectivesInOrder() {
            var t = TransformParser.parse(HEADER + """

                    {rows[]}
                    :loop a :as x
                    :loop .bs :as y
                    :loop .cs :as z
                    v = "@z.v"
                    """);
            var loops = t.getSegments().get(0).getDirectives().stream()
                    .filter(d -> "loop".equals(d.getDirectiveType())).toList();
            assertEquals(3, loops.size());
            assertEquals("a", loops.get(0).getValue());
            assertEquals("x", loops.get(0).getAlias());
            assertEquals(".bs", loops.get(1).getValue());
            assertEquals("y", loops.get(1).getAlias());
            assertEquals(".cs", loops.get(2).getValue());
            assertEquals("z", loops.get(2).getAlias());
        }
    }

    // ── Happy path ──

    @Nested
    class HappyPathTests {
        @Test
        void iteratesTwoLevelCrossProduct() {
            var result = run("""
                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    vin = "@veh.vin"
                    code = "@cov.code"
                    """,
                    "{\"vehicles\":[{\"vin\":\"V1\",\"coverages\":[{\"code\":\"A\"},{\"code\":\"B\"}]},"
                            + "{\"vin\":\"V2\",\"coverages\":[{\"code\":\"C\"}]}]}");

            assertTrue(result.isSuccess());
            var rows = rows(result).asArray();
            assertEquals(3, rows.size());
            assertEquals("V1", rows.get(0).get("vin").asString());
            assertEquals("A", rows.get(0).get("code").asString());
            assertEquals("V1", rows.get(1).get("vin").asString());
            assertEquals("B", rows.get(1).get("code").asString());
            assertEquals("V2", rows.get(2).get("vin").asString());
            assertEquals("C", rows.get(2).get("code").asString());
        }

        @Test
        void iteratesThreeLevelCrossProductWithExactCount() {
            var result = run("""
                    {rows[]}
                    :loop a :as x
                    :loop .bs :as y
                    :loop .cs :as z
                    av = "@x.v"
                    bv = "@y.v"
                    cv = "@z.v"
                    """,
                    "{\"a\":["
                            + "{\"v\":\"A1\",\"bs\":[{\"v\":\"B1\",\"cs\":[{\"v\":\"C1\"},{\"v\":\"C2\"}]}]},"
                            + "{\"v\":\"A2\",\"bs\":[{\"v\":\"B2\",\"cs\":[{\"v\":\"C3\"}]},{\"v\":\"B3\",\"cs\":[{\"v\":\"C4\"}]}]}"
                            + "]}");

            assertTrue(result.isSuccess());
            var rows = rows(result).asArray();
            assertEquals(4, rows.size());
            assertEquals("C1", rows.get(0).get("cv").asString());
            assertEquals("C2", rows.get(1).get("cv").asString());
            assertEquals("A2", rows.get(2).get("av").asString());
            assertEquals("B2", rows.get(2).get("bv").asString());
            assertEquals("C4", rows.get(3).get("cv").asString());
        }
    }

    // ── Edge cases ──

    @Nested
    class EdgeCaseTests {
        @Test
        void emptyInnerArrayProducesNoRows() {
            var result = run("""
                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    vin = "@veh.vin"
                    code = "@cov.code"
                    """,
                    "{\"vehicles\":[{\"vin\":\"V1\",\"coverages\":[{\"code\":\"A\"}]},"
                            + "{\"vin\":\"V2\",\"coverages\":[]},"
                            + "{\"vin\":\"V3\",\"coverages\":[{\"code\":\"C\"}]}]}");

            assertTrue(result.isSuccess());
            var rows = rows(result).asArray();
            assertEquals(2, rows.size());
            assertEquals("V1", rows.get(0).get("vin").asString());
            assertEquals("V3", rows.get(1).get("vin").asString());
        }

        @Test
        void counterBindsToInnermostIndexResettingPerOuter() {
            var result = run("""
                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    :counter idx
                    vin = "@veh.vin"
                    n = "@idx"
                    """,
                    "{\"vehicles\":[{\"vin\":\"V1\",\"coverages\":[{},{},{}]},"
                            + "{\"vin\":\"V2\",\"coverages\":[{}]}]}");

            var rows = rows(result).asArray();
            assertEquals(4, rows.size());
            assertEquals(0L, rows.get(0).get("n").asInt64());
            assertEquals(1L, rows.get(1).get("n").asInt64());
            assertEquals(2L, rows.get(2).get("n").asInt64());
            assertEquals(0L, rows.get(3).get("n").asInt64());
            assertEquals("V2", rows.get(3).get("vin").asString());
        }

        @Test
        void singleAliaslessLoopStillWorks() {
            var result = run("""
                    {rows[]}
                    :loop items
                    sku = "@.sku"
                    """,
                    "{\"items\":[{\"sku\":\"A\"},{\"sku\":\"B\"}]}");

            var rows = rows(result).asArray();
            assertEquals(2, rows.size());
            assertEquals("A", rows.get(0).get("sku").asString());
            assertEquals("B", rows.get(1).get("sku").asString());
        }
    }

    // ── Non-array sources ──

    @Nested
    class NonArraySourceTests {
        @Test
        void innerNonArraySourceYieldsNoRowsNoError() {
            var result = run("""
                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    vin = "@veh.vin"
                    code = "@cov.code"
                    """,
                    "{\"vehicles\":[{\"vin\":\"V1\",\"coverages\":\"not-an-array\"}]}");

            assertTrue(result.isSuccess());
            assertTrue(result.getErrors().isEmpty());
            assertEquals(0, rows(result).asArray().size());
        }

        @Test
        void outerNonArraySourceYieldsEmptyResult() {
            var result = run("""
                    {rows[]}
                    :loop vehicles :as veh
                    :loop .coverages :as cov
                    vin = "@veh.vin"
                    code = "@cov.code"
                    """,
                    "{\"vehicles\":\"nope\"}");

            assertTrue(result.isSuccess());
            assertEquals(0, rows(result).asArray().size());
        }
    }
}
