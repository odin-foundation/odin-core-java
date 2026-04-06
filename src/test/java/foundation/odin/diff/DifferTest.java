package foundation.odin.diff;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class DifferTest {

    // ─── No changes ──────────────────────────────────────────────────────

    @Nested class NoChangeTests {
        @Test void identicalDocuments() {
            var doc = new OdinDocumentBuilder().set("x", "hello").build();
            var diff = Odin.diff(doc, doc);
            assertTrue(diff.isEmpty());
        }

        @Test void bothEmpty() {
            var diff = Odin.diff(OdinDocument.empty(), OdinDocument.empty());
            assertTrue(diff.isEmpty());
        }

        @Test void sameValues() {
            var a = new OdinDocumentBuilder().set("a", 1L).set("b", "hello").build();
            var b = new OdinDocumentBuilder().set("a", 1L).set("b", "hello").build();
            var diff = Odin.diff(a, b);
            assertTrue(diff.isEmpty());
        }
    }

    // ─── Added fields ────────────────────────────────────────────────────

    @Nested class AddedTests {
        @Test void fieldAdded() {
            var a = OdinDocument.empty();
            var b = new OdinDocumentBuilder().set("name", "Alice").build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.isEmpty());
            assertFalse(diff.added().isEmpty());
            assertEquals("name", diff.added().get(0).path());
        }

        @Test void multipleFieldsAdded() {
            var a = OdinDocument.empty();
            var b = new OdinDocumentBuilder().set("x", 1L).set("y", 2L).build();
            var diff = Odin.diff(a, b);
            assertEquals(2, diff.added().size());
        }
    }

    // ─── Removed fields ──────────────────────────────────────────────────

    @Nested class RemovedTests {
        @Test void fieldRemoved() {
            var a = new OdinDocumentBuilder().set("name", "Alice").build();
            var b = OdinDocument.empty();
            var diff = Odin.diff(a, b);
            assertFalse(diff.isEmpty());
            assertFalse(diff.removed().isEmpty());
            assertEquals("name", diff.removed().get(0).path());
        }

        @Test void multipleFieldsRemoved() {
            var a = new OdinDocumentBuilder().set("x", 1L).set("y", 2L).build();
            var b = OdinDocument.empty();
            var diff = Odin.diff(a, b);
            assertEquals(2, diff.removed().size());
        }
    }

    // ─── Changed fields ──────────────────────────────────────────────────

    @Nested class ChangedTests {
        @Test void valueChanged() {
            var a = new OdinDocumentBuilder().set("x", 1L).build();
            var b = new OdinDocumentBuilder().set("x", 2L).build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.isEmpty());
            assertFalse(diff.changed().isEmpty());
            assertEquals("x", diff.changed().get(0).path());
        }

        @Test void typeChanged() {
            var a = new OdinDocumentBuilder().set("x", "hello").build();
            var b = new OdinDocumentBuilder().set("x", 42L).build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.changed().isEmpty());
        }

        @Test void stringValueChanged() {
            var a = new OdinDocumentBuilder().set("name", "Alice").build();
            var b = new OdinDocumentBuilder().set("name", "Bob").build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.changed().isEmpty());
        }
    }

    // ─── Mixed operations ────────────────────────────────────────────────

    @Nested class MixedTests {
        @Test void addedAndRemoved() {
            var a = new OdinDocumentBuilder().set("old", "value").build();
            var b = new OdinDocumentBuilder().set("new", "value").build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.isEmpty());
            assertTrue(!diff.moved().isEmpty() || (!diff.added().isEmpty() && !diff.removed().isEmpty()));
        }

        @Test void addedChangedRemoved() {
            var a = new OdinDocumentBuilder()
                .set("keep", "same")
                .set("change", "old")
                .set("remove", "gone")
                .build();
            var b = new OdinDocumentBuilder()
                .set("keep", "same")
                .set("change", "new")
                .set("add", "fresh")
                .build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.added().isEmpty());
            assertFalse(diff.changed().isEmpty());
            assertFalse(diff.removed().isEmpty());
        }
    }

    // ─── OdinDiff record ─────────────────────────────────────────────────

    @Nested class DiffRecordTests {
        @Test void diffEntryRecord() {
            var entry = new OdinDiff.DiffEntry("path", OdinValue.ofString("value"));
            assertEquals("path", entry.path());
            assertEquals("value", entry.value().asString());
        }

        @Test void diffChangeRecord() {
            var change = new OdinDiff.DiffChange("x",
                OdinValue.ofInteger(1), OdinValue.ofInteger(2));
            assertEquals("x", change.path());
        }

        @Test void diffMoveRecord() {
            var move = new OdinDiff.DiffMove("old.path", "new.path", OdinValue.ofString("val"));
            assertEquals("old.path", move.fromPath());
            assertEquals("new.path", move.toPath());
        }
    }

    // ─── Value equality ──────────────────────────────────────────────────

    @Nested class ValueEqualityTests {
        @Test void nullsEqual() {
            assertTrue(Differ.valuesEqual(OdinValue.ofNull(), OdinValue.ofNull()));
        }

        @Test void booleansEqual() {
            assertTrue(Differ.valuesEqual(OdinValue.ofBoolean(true), OdinValue.ofBoolean(true)));
            assertFalse(Differ.valuesEqual(OdinValue.ofBoolean(true), OdinValue.ofBoolean(false)));
        }

        @Test void stringsEqual() {
            assertTrue(Differ.valuesEqual(OdinValue.ofString("a"), OdinValue.ofString("a")));
            assertFalse(Differ.valuesEqual(OdinValue.ofString("a"), OdinValue.ofString("b")));
        }

        @Test void integersEqual() {
            assertTrue(Differ.valuesEqual(OdinValue.ofInteger(42), OdinValue.ofInteger(42)));
            assertFalse(Differ.valuesEqual(OdinValue.ofInteger(1), OdinValue.ofInteger(2)));
        }

        @Test void numbersEqual() {
            assertTrue(Differ.valuesEqual(OdinValue.ofNumber(3.14), OdinValue.ofNumber(3.14)));
            assertFalse(Differ.valuesEqual(OdinValue.ofNumber(1.0), OdinValue.ofNumber(2.0)));
        }

        @Test void differentTypesNotEqual() {
            assertFalse(Differ.valuesEqual(OdinValue.ofString("42"), OdinValue.ofInteger(42)));
        }

        @Test void nullValueNotEqual() {
            assertFalse(Differ.valuesEqual(null, OdinValue.ofString("x")));
            assertFalse(Differ.valuesEqual(OdinValue.ofString("x"), null));
        }

        @Test void sameReferenceEqual() {
            var val = OdinValue.ofString("test");
            assertTrue(Differ.valuesEqual(val, val));
        }
    }

    // ─── Move detection ──────────────────────────────────────────────────

    @Nested class MoveDetectionTests {
        @Test void sameValueDifferentKeyDetectedAsMove() {
            var a = new OdinDocumentBuilder().set("oldKey", "movedValue").build();
            var b = new OdinDocumentBuilder().set("newKey", "movedValue").build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.moved().isEmpty());
            assertEquals("oldKey", diff.moved().get(0).fromPath());
            assertEquals("newKey", diff.moved().get(0).toPath());
        }

        @Test void moveReducesAddedAndRemoved() {
            var a = new OdinDocumentBuilder().set("old", "val").build();
            var b = new OdinDocumentBuilder().set("new", "val").build();
            var diff = Odin.diff(a, b);
            assertTrue(diff.added().isEmpty());
            assertTrue(diff.removed().isEmpty());
            assertEquals(1, diff.moved().size());
        }
    }
}
