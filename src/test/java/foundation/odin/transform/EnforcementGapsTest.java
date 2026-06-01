package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.OdinTransformTypes.TransformResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Transform engine enforcement gaps: stable error codes (T001, T003, T005, T006,
// T008, T009), onMissing policy for source fields, and @import resolution.
class EnforcementGapsTest {

    // ── Helpers ──

    private static DynValue obj(Map.Entry<String, DynValue>... entries) {
        return DynValue.ofObject(List.of(entries));
    }

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }
    private static DynValue str(String s) { return DynValue.ofString(s); }
    private static DynValue integer(long i) { return DynValue.ofInteger(i); }

    // Build a transform header for an odin->{format} transform with optional target options.
    private static String header(String format, Map<String, String> target) {
        var sb = new StringBuilder();
        sb.append("{$}\n")
          .append("odin = \"1.0.0\"\n")
          .append("transform = \"1.0.0\"\n")
          .append("direction = \"odin->").append(format).append("\"\n\n")
          .append("{$source}\nformat = \"odin\"\n\n")
          .append("{$target}\nformat = \"").append(format).append("\"\n");
        if (target != null) {
            for (var e : target.entrySet()) sb.append(e.getKey()).append(" = \"").append(e.getValue()).append("\"\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static TransformResult run(String body, DynValue source) {
        return run(body, source, "odin", null);
    }

    private static TransformResult run(String body, DynValue source, String format, Map<String, String> target) {
        var transform = TransformParser.parse(header(format, target) + body);
        return TransformEngine.execute(transform, source);
    }

    private static boolean hasWarning(TransformResult r, String code) {
        for (var w : r.getWarnings()) if (code.equals(w.getCode())) return true;
        return false;
    }

    private static boolean hasError(TransformResult r, String code) {
        for (var e : r.getErrors()) if (code.equals(e.getCode())) return true;
        return false;
    }

    // ── T001 — unknown verb ──

    @Nested
    class UnknownVerb {
        @Test
        void emitsT001ForUnknownBuiltinVerb() {
            var r = run("{out}\nx = %notAVerb @.a", obj(kv("a", integer(1))));
            assertFalse(r.isSuccess());
            assertEquals("T001", r.getErrors().get(0).getCode());
            assertEquals("x", r.getErrors().get(0).getPath());
        }

        @Test
        void doesNotRaiseForUnregisteredCustomVerb() {
            var r = run("{out}\nx = %&my.thing @.a", obj(kv("a", str("v"))));
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
        }

        @Test
        void demotesT001ToWarningUnderOnErrorWarn() {
            var r = run("{out}\nx = %notAVerb @.a", obj(kv("a", integer(1))),
                    "odin", Map.of("onError", "warn"));
            assertTrue(r.isSuccess());
            assertTrue(hasWarning(r, "T001"));
        }
    }

    // ── T003 — lookup table not found ──

    @Nested
    class LookupTableNotFound {
        @Test
        void emitsT003WhenTableUndeclaredAndOnMissingFail() {
            var r = run("{out}\nx = %lookup \"GHOST.code\" @.k", obj(kv("k", str("active"))),
                    "odin", Map.of("onMissing", "fail"));
            assertFalse(r.isSuccess());
            assertEquals("T003", r.getErrors().get(0).getCode());
        }

        @Test
        void staysSilentForUndeclaredTableUnderDefaultPolicy() {
            var r = run("{out}\nx = %lookup \"GHOST.code\" @.k", obj(kv("k", str("active"))));
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
        }

        @Test
        void demotesT003ToWarningUnderOnMissingWarn() {
            var r = run("{out}\nx = %lookup \"GHOST.code\" @.k", obj(kv("k", str("active"))),
                    "odin", Map.of("onMissing", "warn"));
            assertTrue(r.isSuccess());
            assertTrue(hasWarning(r, "T003"));
        }

        @Test
        void stillEmitsT004ForMissingKeyInDeclaredTable() {
            var body = "{$table.T[name, code]}\n\"foo\", ##1\n\n{out}\nx = %lookup \"T.code\" @.k";
            var r = run(body, obj(kv("k", str("bar"))), "odin", Map.of("onMissing", "fail"));
            assertFalse(r.isSuccess());
            assertEquals("T004", r.getErrors().get(0).getCode());
        }
    }

    // ── T005 — source path not found / onMissing ──

    @Nested
    class SourcePathNotFound {
        @Test
        void emitsT005WhenRequiredSourcePathAbsent() {
            var r = run("{out}\nx = @.does.not.exist :required", obj(kv("a", integer(1))));
            assertFalse(r.isSuccess());
            assertEquals("T005", r.getErrors().get(0).getCode());
        }

        @Test
        void emitsT005ForAbsentPathUnderOnMissingFailWithoutRequired() {
            var r = run("{out}\nx = @.does.not.exist", obj(kv("a", integer(1))),
                    "odin", Map.of("onMissing", "fail"));
            assertFalse(r.isSuccess());
            assertEquals("T005", r.getErrors().get(0).getCode());
        }

        @Test
        void warnsForAbsentPathUnderOnMissingWarn() {
            var r = run("{out}\nx = @.does.not.exist", obj(kv("a", integer(1))),
                    "odin", Map.of("onMissing", "warn"));
            assertTrue(r.isSuccess());
            assertTrue(hasWarning(r, "T005"));
        }

        @Test
        void staysSilentForAbsentPathUnderDefaultSkipPolicy() {
            var r = run("{out}\nx = @.does.not.exist", obj(kv("a", integer(1))));
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
        }

        @Test
        void treatsPresentNullRequiredFieldAsSourceMissingNotT005() {
            var r = run("{out}\nx = @.a :required", obj(kv("a", DynValue.ofNull())));
            assertFalse(r.isSuccess());
            assertEquals("SOURCE_MISSING", r.getErrors().get(0).getCode());
        }

        @Test
        void doesNotRaiseT005WhenVerbResultIsNull() {
            var r = run("{out}\nx = %upper @.missing", obj(kv("a", integer(1))),
                    "odin", Map.of("onMissing", "fail"));
            assertFalse(hasError(r, "T005"));
        }
    }

    // ── T006 — invalid output format ──

    @Nested
    class InvalidOutputFormat {
        @Test
        void emitsT006ForUnregisteredTargetFormat() {
            var r = run("{out}\nx = @.a", obj(kv("a", integer(1))), "notaformat", null);
            assertFalse(r.isSuccess());
            assertTrue(hasError(r, "T006"));
        }

        @Test
        void stillProducesOutputForEachKnownFormat() {
            for (var fmt : List.of("odin", "json", "xml")) {
                var r = run("{out}\nx = @.a", obj(kv("a", integer(1))), fmt, null);
                assertFalse(hasError(r, "T006"), "format " + fmt);
                assertTrue(r.getFormatted().length() > 0, "format " + fmt);
            }
        }

        @Test
        void csvKnownFormatProducesTabularOutputWithoutT006() {
            var rows = DynValue.ofArray(List.of(obj(kv("a", integer(1)))));
            var r = run("{out[]}\n:loop rows\nx = @.a", obj(kv("rows", rows)), "csv", null);
            assertFalse(hasError(r, "T006"));
            assertTrue(r.getFormatted().length() > 0);
        }
    }

    // ── T009 — loop source not array ──

    @Nested
    class LoopSourceNotArray {
        @Test
        void emitsT009ForPresentNonArrayScalar() {
            var r = run("{out[]}\n:loop notArr\nx = @.a", obj(kv("notArr", str("scalar"))));
            assertFalse(r.isSuccess());
            assertEquals("T009", r.getErrors().get(0).getCode());
        }

        @Test
        void yieldsZeroRowsNoErrorForAbsentLoopSource() {
            var r = run("{out[]}\n:loop missing\nx = @.a", obj(kv("a", integer(1))));
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
        }

        @Test
        void demotesT009ToWarningUnderOnErrorWarn() {
            var r = run("{out[]}\n:loop notArr\nx = @.a", obj(kv("notArr", str("scalar"))),
                    "odin", Map.of("onError", "warn"));
            assertTrue(r.isSuccess());
            assertTrue(hasWarning(r, "T009"));
        }
    }

    // ── T008 — accumulator overflow ──

    @Nested
    class AccumulatorOverflow {
        @Test
        void emitsT008WhenIntegerAccumulatorExceedsSafeCapacity() {
            var body = "{$accumulator}\ntotal = ##0\n\n{out}\nx = %accumulate \"total\" @.a";
            var huge = DynValue.ofFloat(1.0e20); // beyond 2^53-1
            var r = run(body, obj(kv("a", huge)));
            assertFalse(r.isSuccess());
            assertEquals("T008", r.getErrors().get(0).getCode());
        }

        @Test
        void doesNotRaiseForOrdinaryAccumulation() {
            var body = "{$accumulator}\ntotal = ##0\n\n{out}\nx = %accumulate \"total\" @.a";
            var r = run(body, obj(kv("a", integer(5))));
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
        }
    }

    // ── @import resolution ──

    @Nested
    class ImportResolution {
        private static final String TABLES_DOC = """
                {$}
                odin = "1.0.0"
                transform = "1.0.0"
                direction = "odin->odin"

                {$source}
                format = "odin"

                {$target}
                format = "odin"

                {$table.STATES[code, name]}
                "CA", "California"
                "TX", "Texas"
                """;

        private static final String SHARED_DOC = """
                {$}
                odin = "1.0.0"
                transform = "1.0.0"
                direction = "odin->odin"

                {$source}
                format = "odin"

                {$target}
                format = "odin"

                {shared}
                greeting = "hello"
                """;

        private static final String MAIN = """
                {$}
                odin = "1.0.0"
                transform = "1.0.0"
                direction = "odin->odin"

                @import ./tables/states.odin
                @import ./mappings/shared.odin

                {$source}
                format = "odin"

                {$target}
                format = "odin"
                onMissing = "fail"

                {out}
                state = %lookup "STATES.name" @.code
                """;

        private static final TransformEngine.ImportResolver RESOLVER = p -> {
            if (p.contains("states")) return TABLES_DOC;
            if (p.contains("shared")) return SHARED_DOC;
            return null;
        };

        private static TransformEngine.TransformOptions opts() {
            return new TransformEngine.TransformOptions().setImportResolver(RESOLVER);
        }

        @Test
        void makesImportedTableUsableByLookup() {
            var r = TransformEngine.execute(TransformParser.parse(MAIN), obj(kv("code", str("CA"))), opts());
            assertTrue(r.isSuccess());
            assertTrue(r.getErrors().isEmpty());
            assertTrue(r.getFormatted().contains("California"));
        }

        @Test
        void mergesImportedMappingSegmentIntoOutput() {
            var r = TransformEngine.execute(TransformParser.parse(MAIN), obj(kv("code", str("TX"))), opts());
            assertTrue(r.getFormatted().contains("greeting"));
            assertTrue(r.getFormatted().contains("hello"));
        }

        @Test
        void leavesImportedTableUnresolvedWithoutResolverT003() {
            var r = TransformEngine.execute(TransformParser.parse(MAIN), obj(kv("code", str("CA"))));
            assertFalse(r.isSuccess());
            assertEquals("T003", r.getErrors().get(0).getCode());
        }

        @Test
        void localDeclarationsTakePrecedenceOverImported() {
            var localTable = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->odin"

                    @import ./tables/states.odin

                    {$source}
                    format = "odin"

                    {$target}
                    format = "odin"

                    {$table.STATES[code, name]}
                    "CA", "Local-California"

                    {out}
                    state = %lookup "STATES.name" @.code
                    """;
            var r = TransformEngine.execute(TransformParser.parse(localTable), obj(kv("code", str("CA"))), opts());
            assertTrue(r.getFormatted().contains("Local-California"));
        }

        @Test
        void ignoresImportTheResolverCannotSatisfy() {
            var t = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->odin"

                    @import ./missing/nowhere.odin

                    {$source}
                    format = "odin"

                    {$target}
                    format = "odin"

                    {out}
                    x = @.a
                    """;
            var r = TransformEngine.execute(TransformParser.parse(t), obj(kv("a", integer(1))), opts());
            assertTrue(r.isSuccess());
        }
    }
}
