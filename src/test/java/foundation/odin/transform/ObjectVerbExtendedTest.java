package foundation.odin.transform;

import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for object-shaping verbs: pick, omit, fromEntries, invert, defaults,
 * renameKeys, and compactObject.
 */
class ObjectVerbExtendedTest {

    private final VerbRegistry registry = new VerbRegistry();
    private final TransformEngine.VerbContext ctx = new TransformEngine.VerbContext();

    private DynValue invoke(String verb, DynValue... args) {
        return registry.invoke(verb, args, ctx);
    }

    private static DynValue S(String v) { return DynValue.ofString(v); }
    private static DynValue I(long v) { return DynValue.ofInteger(v); }
    private static DynValue B(boolean v) { return DynValue.ofBool(v); }
    private static DynValue Null() { return DynValue.ofNull(); }
    private static DynValue Arr(DynValue... items) {
        return DynValue.ofArray(new ArrayList<>(List.of(items)));
    }
    @SafeVarargs
    private static DynValue Obj(Map.Entry<String, DynValue>... entries) {
        return DynValue.ofObject(new ArrayList<>(List.of(entries)));
    }
    private static Map.Entry<String, DynValue> e(String k, DynValue v) {
        return Map.entry(k, v);
    }

    private static List<String> keys(DynValue obj) {
        var ks = new ArrayList<String>();
        for (var entry : obj.asObject()) ks.add(entry.getKey());
        return ks;
    }

    // =========================================================================
    // pick
    // =========================================================================

    @Nested
    class Pick {
        private final DynValue rec = Obj(e("name", S("Ada")), e("role", S("admin")), e("active", B(true)));

        @Test
        void keepsNamedKeysInSourceOrder() {
            var result = invoke("pick", rec, S("name"), S("role"));
            assertEquals(List.of("name", "role"), keys(result));
            assertEquals("Ada", result.get("name").asString());
            assertEquals("admin", result.get("role").asString());
        }

        @Test
        void absentKeyIsSkipped() {
            var result = invoke("pick", rec, S("name"), S("zzz"));
            assertEquals(List.of("name"), keys(result));
            assertEquals("Ada", result.get("name").asString());
        }

        @Test
        void nonObjectYieldsNull() {
            assertTrue(invoke("pick", S("x"), S("name")).isNull());
        }

        @Test
        void noArgsYieldsNull() {
            assertTrue(invoke("pick").isNull());
        }
    }

    // =========================================================================
    // omit
    // =========================================================================

    @Nested
    class Omit {
        private final DynValue rec = Obj(e("name", S("Ada")), e("role", S("admin")), e("active", B(true)));

        @Test
        void dropsNamedKeyPreservingOrder() {
            var result = invoke("omit", rec, S("active"));
            assertEquals(List.of("name", "role"), keys(result));
        }

        @Test
        void absentKeyLeavesObjectUnchanged() {
            var result = invoke("omit", rec, S("zzz"));
            assertEquals(List.of("name", "role", "active"), keys(result));
        }

        @Test
        void nonObjectYieldsNull() {
            assertTrue(invoke("omit", S("x"), S("name")).isNull());
        }
    }

    // =========================================================================
    // fromEntries
    // =========================================================================

    @Nested
    class FromEntries {
        @Test
        void buildsObjectFromPairs() {
            var pairs = Arr(Arr(S("name"), S("Ada")), Arr(S("role"), S("admin")));
            var result = invoke("fromEntries", pairs);
            assertEquals("Ada", result.get("name").asString());
            assertEquals("admin", result.get("role").asString());
        }

        @Test
        void lastDuplicateWins() {
            var pairs = Arr(Arr(S("k"), I(1)), Arr(S("k"), I(2)));
            var result = invoke("fromEntries", pairs);
            assertEquals(2L, result.get("k").asInt64());
        }

        @Test
        void nonArrayYieldsNull() {
            assertTrue(invoke("fromEntries", S("x")).isNull());
        }
    }

    // =========================================================================
    // invert
    // =========================================================================

    @Nested
    class Invert {
        @Test
        void swapsKeysAndValues() {
            var m = Obj(e("a", S("x")), e("b", S("y")));
            var result = invoke("invert", m);
            assertEquals("a", result.get("x").asString());
            assertEquals("b", result.get("y").asString());
        }

        @Test
        void duplicateValueLastKeyWins() {
            var dup = Obj(e("a", S("same")), e("b", S("same")));
            var result = invoke("invert", dup);
            assertEquals("b", result.get("same").asString());
        }

        @Test
        void nonObjectYieldsNull() {
            assertTrue(invoke("invert", S("x")).isNull());
        }
    }

    // =========================================================================
    // defaults
    // =========================================================================

    @Nested
    class Defaults {
        private final DynValue fallback = Obj(e("name", S("Anon")), e("role", S("guest")));

        @Test
        void fillsOnlyMissingKeys() {
            var rec = Obj(e("name", S("Ada")));
            var result = invoke("defaults", rec, fallback);
            assertEquals("Ada", result.get("name").asString());
            assertEquals("guest", result.get("role").asString());
        }

        @Test
        void nonObjectBaseTakesAllDefaults() {
            var result = invoke("defaults", S("x"), fallback);
            assertEquals("Anon", result.get("name").asString());
            assertEquals("guest", result.get("role").asString());
        }
    }

    // =========================================================================
    // renameKeys
    // =========================================================================

    @Nested
    class RenameKeys {
        private final DynValue mapping = Obj(e("fn", S("firstName")));

        @Test
        void renamesMappedKeysPassesOthersThrough() {
            var rec = Obj(e("fn", S("Ada")), e("keep", S("as-is")));
            var result = invoke("renameKeys", rec, mapping);
            assertEquals(List.of("firstName", "keep"), keys(result));
            assertEquals("Ada", result.get("firstName").asString());
            assertEquals("as-is", result.get("keep").asString());
        }

        @Test
        void nonObjectYieldsNull() {
            assertTrue(invoke("renameKeys", S("x"), mapping).isNull());
        }
    }

    // =========================================================================
    // compactObject
    // =========================================================================

    @Nested
    class CompactObject {
        @Test
        void dropsEmptyValuesKeepsFalsyScalars() {
            var rec = Obj(
                    e("name", S("Ada")),
                    e("middle", Null()),
                    e("nickname", S("")),
                    e("zero", I(0)),
                    e("flag", B(false)));
            var result = invoke("compactObject", rec);
            assertEquals(List.of("name", "zero", "flag"), keys(result));
            assertEquals(0L, result.get("zero").asInt64());
            assertEquals(false, result.get("flag").asBool());
        }

        @Test
        void dropsEmptyArraysAndObjects() {
            var rec = Obj(e("a", Arr()), e("b", Obj()), e("c", I(1)));
            var result = invoke("compactObject", rec);
            assertEquals(List.of("c"), keys(result));
        }

        @Test
        void nonObjectYieldsNull() {
            assertTrue(invoke("compactObject", S("x")).isNull());
        }
    }
}
