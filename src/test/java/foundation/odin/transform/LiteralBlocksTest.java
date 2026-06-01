package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// `:literal` segment blocks emit their `"""..."""` body as text with ${...}
// interpolation. ${@path}, ${@.field}, ${%verb ...} interpolate; \${, \$, \\
// escape; nesting is rejected with T014.
class LiteralBlocksTest {

    private static final String HEADER = """
            {$}
            odin = "1.0.0"
            transform = "1.0.0"
            direction = "odin->fixed-width"
            target.format = "fixed-width"
            """;

    private static TransformResult run(String body, DynValue source) {
        var transform = TransformParser.parse(HEADER + "\n" + body);
        return TransformEngine.execute(transform, source);
    }

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }
    @SafeVarargs
    private static DynValue obj(Map.Entry<String, DynValue>... e) { return DynValue.ofObject(List.of(e)); }
    private static DynValue arr(DynValue... items) { return DynValue.ofArray(List.of(items)); }
    private static DynValue str(String s) { return DynValue.ofString(s); }

    // ── Parsing ──

    @Nested
    class ParsingTests {
        @Test
        void capturesLiteralBodyAndLiteralDirective() {
            var t = TransformParser.parse(HEADER
                    + "\n{HDR}\n:literal\n\"\"\"\nHDR|${@policy.number}\n\"\"\"\n");
            var seg = t.getSegments().get(0);
            assertTrue(seg.getDirectives().stream().anyMatch(d -> "literal".equals(d.getDirectiveType())));
            var bodyDir = seg.getDirectives().stream()
                    .filter(d -> "literalBody".equals(d.getDirectiveType())).findFirst().orElse(null);
            assertNotNull(bodyDir);
            assertEquals("\nHDR|${@policy.number}\n", bodyDir.getValue());
        }
    }

    // ── Happy path ──

    @Nested
    class HappyPathTests {
        @Test
        void interpolatesSourcePathAndVerbResult() {
            var r = run("{HDR}\n:literal\n\"\"\"\nHDR|${@policy.number}|${%upper @policy.code}\n\"\"\"\n",
                    obj(kv("policy", obj(kv("number", str("P-100")), kv("code", str("abc"))))));
            assertTrue(r.isSuccess());
            assertEquals("HDR|P-100|ABC", r.getFormatted().trim());
        }

        @Test
        void emitsOneLinePerItemUnderLoop() {
            var r = run("{DET[]}\n:loop @items\n:literal\n\"\"\"\nDET|${@.sku}|${@.qty}\n\"\"\"\n",
                    obj(kv("items", arr(
                            obj(kv("sku", str("A1")), kv("qty", str("2"))),
                            obj(kv("sku", str("B2")), kv("qty", str("5")))))));
            assertTrue(r.isSuccess());
            assertEquals("DET|A1|2\nDET|B2|5", r.getFormatted().trim());
        }
    }

    // ── Edge cases ──

    @Nested
    class EdgeCaseTests {
        @Test
        void honorsEscapeRules() {
            var r = run("{X}\n:literal\n\"\"\"\nlit:\\${@a} dollar:\\$ slash:\\\\ real:${@a}\n\"\"\"\n",
                    obj(kv("a", str("V"))));
            assertTrue(r.isSuccess());
            assertEquals("lit:${@a} dollar:$ slash:\\ real:V", r.getFormatted().trim());
        }

        @Test
        void emitsInterpolationFreeLiteralVerbatim() {
            var r = run("{X}\n:literal\n\"\"\"\nJUST TEXT NO INTERP\n\"\"\"\n", obj());
            assertTrue(r.isSuccess());
            assertEquals("JUST TEXT NO INTERP", r.getFormatted().trim());
        }

        @Test
        void emitsMultiLineBlockAsMultipleLines() {
            var r = run("{X}\n:literal\n\"\"\"\nLINE1 ${@a}\nLINE2\nLINE3 ${@b}\n\"\"\"\n",
                    obj(kv("a", str("1")), kv("b", str("3"))));
            assertTrue(r.isSuccess());
            assertEquals("LINE1 1\nLINE2\nLINE3 3", r.getFormatted().trim());
        }

        @Test
        void stripsOnlyDelimiterNewlinesPreservingInteriorBlanks() {
            var r = run("{X}\n:literal\n\"\"\"\nA\n\nB\n\"\"\"\n", obj());
            assertTrue(r.isSuccess());
            assertEquals("A\n\nB", r.getFormatted().replaceAll("\n+$", ""));
        }
    }

    // ── Errors ──

    @Nested
    class ErrorTests {
        @Test
        void rejectsNestedInterpolationWithT014() {
            var r = run("{X}\n:literal\n\"\"\"\n${@a.${@b}}\n\"\"\"\n",
                    obj(kv("a", obj(kv("b", str("x")))), kv("b", str("k"))));
            assertFalse(r.isSuccess());
            assertTrue(r.getErrors().stream().anyMatch(e -> "T014".equals(e.getCode())));
        }

        @Test
        void reportsUnknownVerbAsTransformError() {
            var r = run("{X}\n:literal\n\"\"\"\n${%nope @a}\n\"\"\"\n", obj(kv("a", str("z"))));
            assertFalse(r.isSuccess());
            assertTrue(r.getErrors().stream().anyMatch(e -> e.getMessage() != null
                    && e.getMessage().contains("Unknown verb")));
        }
    }
}
