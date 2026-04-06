package foundation.odin.diff;

import foundation.odin.Odin;
import foundation.odin.types.*;
import foundation.odin.types.OdinErrors.PatchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatcherTest {

    // ─── Apply additions ─────────────────────────────────────────────────

    @Nested class AdditionTests {
        @Test void applyAddition() {
            var doc = OdinDocument.empty();
            var diff = new OdinDiff(
                List.of(new OdinDiff.DiffEntry("name", OdinValue.ofString("Alice"))),
                List.of(), List.of(), List.of());
            var result = Odin.patch(doc, diff);
            assertEquals("Alice", result.getString("name"));
        }

        @Test void applyMultipleAdditions() {
            var doc = OdinDocument.empty();
            var diff = new OdinDiff(
                List.of(
                    new OdinDiff.DiffEntry("a", OdinValue.ofInteger(1)),
                    new OdinDiff.DiffEntry("b", OdinValue.ofInteger(2))
                ),
                List.of(), List.of(), List.of());
            var result = Odin.patch(doc, diff);
            assertEquals(1L, result.getInteger("a"));
            assertEquals(2L, result.getInteger("b"));
        }
    }

    // ─── Apply removals ──────────────────────────────────────────────────

    @Nested class RemovalTests {
        @Test void applyRemoval() {
            var doc = new OdinDocumentBuilder().set("name", "Alice").set("age", 30L).build();
            var diff = new OdinDiff(
                List.of(),
                List.of(new OdinDiff.DiffEntry("name", OdinValue.ofString("Alice"))),
                List.of(), List.of());
            var result = Odin.patch(doc, diff);
            assertFalse(result.has("name"));
            assertTrue(result.has("age"));
        }

        @Test void removeNonExistentPathThrows() {
            var doc = OdinDocument.empty();
            var diff = new OdinDiff(
                List.of(),
                List.of(new OdinDiff.DiffEntry("missing", OdinValue.ofString("x"))),
                List.of(), List.of());
            assertThrows(PatchException.class, () -> Odin.patch(doc, diff));
        }
    }

    // ─── Apply changes ───────────────────────────────────────────────────

    @Nested class ChangeTests {
        @Test void applyChange() {
            var doc = new OdinDocumentBuilder().set("x", 1L).build();
            var diff = new OdinDiff(
                List.of(), List.of(),
                List.of(new OdinDiff.DiffChange("x", OdinValue.ofInteger(1), OdinValue.ofInteger(99))),
                List.of());
            var result = Odin.patch(doc, diff);
            assertEquals(99L, result.getInteger("x"));
        }

        @Test void changeNonExistentPathThrows() {
            var doc = OdinDocument.empty();
            var diff = new OdinDiff(
                List.of(), List.of(),
                List.of(new OdinDiff.DiffChange("missing", OdinValue.ofInteger(1), OdinValue.ofInteger(2))),
                List.of());
            assertThrows(PatchException.class, () -> Odin.patch(doc, diff));
        }
    }

    // ─── Apply moves ────────────────────────────────────────────────────

    @Nested class MoveTests {
        @Test void applyMove() {
            var doc = new OdinDocumentBuilder().set("old", "value").build();
            var diff = new OdinDiff(
                List.of(), List.of(), List.of(),
                List.of(new OdinDiff.DiffMove("old", "new", OdinValue.ofString("value"))));
            var result = Odin.patch(doc, diff);
            assertFalse(result.has("old"));
            assertEquals("value", result.getString("new"));
        }

        @Test void moveNonExistentPathThrows() {
            var doc = OdinDocument.empty();
            var diff = new OdinDiff(
                List.of(), List.of(), List.of(),
                List.of(new OdinDiff.DiffMove("missing", "new", OdinValue.ofString("x"))));
            assertThrows(PatchException.class, () -> Odin.patch(doc, diff));
        }
    }

    // ─── Roundtrip diff/patch ────────────────────────────────────────────

    @Nested class RoundtripTests {
        @Test void diffThenPatch() {
            var a = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("age", 30L)
                .build();
            var b = new OdinDocumentBuilder()
                .set("name", "Bob")
                .set("age", 30L)
                .set("email", "bob@example.com")
                .build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertEquals("Bob", result.getString("name"));
            assertEquals("bob@example.com", result.getString("email"));
            assertEquals(30L, result.getInteger("age"));
        }

        @Test void emptyDiffNoChange() {
            var doc = new OdinDocumentBuilder().set("x", 1L).build();
            var diff = new OdinDiff(List.of(), List.of(), List.of(), List.of());
            var result = Odin.patch(doc, diff);
            assertEquals(1L, result.getInteger("x"));
        }
    }

    // ─── Complex patches ─────────────────────────────────────────────────

    @Nested class ComplexTests {
        @Test void combinedOperations() {
            var doc = new OdinDocumentBuilder()
                .set("keep", "same")
                .set("change", "old")
                .set("remove", "gone")
                .build();
            var diff = new OdinDiff(
                List.of(new OdinDiff.DiffEntry("add", OdinValue.ofString("new"))),
                List.of(new OdinDiff.DiffEntry("remove", OdinValue.ofString("gone"))),
                List.of(new OdinDiff.DiffChange("change", OdinValue.ofString("old"), OdinValue.ofString("updated"))),
                List.of());
            var result = Odin.patch(doc, diff);
            assertEquals("same", result.getString("keep"));
            assertEquals("updated", result.getString("change"));
            assertEquals("new", result.getString("add"));
            assertFalse(result.has("remove"));
        }
    }

    // ─── PatchException ──────────────────────────────────────────────────

    @Nested class PatchExceptionTests {
        @Test void exceptionHasPath() {
            var ex = new PatchException("test message", "some.path");
            assertEquals("some.path", ex.getPath());
            assertEquals("test message", ex.getMessage());
        }
    }
}
