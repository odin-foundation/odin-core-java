package foundation.odin.diff;

import foundation.odin.Odin;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffPatchExtendedTest {

    // ─── All ODIN value types ────────────────────────────────────────────

    @Nested class TypeComparisonTests {
        @Test void nullValuesEqual() {
            var a = new OdinDocumentBuilder().setNull("x").build();
            var b = new OdinDocumentBuilder().setNull("x").build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void booleanTrueEqual() {
            var a = new OdinDocumentBuilder().set("f", true).build();
            var b = new OdinDocumentBuilder().set("f", true).build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void booleanDifferent() {
            var a = new OdinDocumentBuilder().set("f", true).build();
            var b = new OdinDocumentBuilder().set("f", false).build();
            var diff = Odin.diff(a, b);
            assertFalse(diff.isEmpty());
            assertEquals(1, diff.changed().size());
        }

        @Test void integerEqual() {
            var a = new OdinDocumentBuilder().set("n", 42L).build();
            var b = new OdinDocumentBuilder().set("n", 42L).build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void integerDifferent() {
            var a = new OdinDocumentBuilder().set("n", 42L).build();
            var b = new OdinDocumentBuilder().set("n", 100L).build();
            assertFalse(Odin.diff(a, b).isEmpty());
        }

        @Test void numberEqual() {
            var a = new OdinDocumentBuilder().set("r", 3.14).build();
            var b = new OdinDocumentBuilder().set("r", 3.14).build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void numberDifferent() {
            var a = new OdinDocumentBuilder().set("r", 3.14).build();
            var b = new OdinDocumentBuilder().set("r", 2.71).build();
            assertFalse(Odin.diff(a, b).isEmpty());
        }

        @Test void currencyEqual() {
            var a = new OdinDocumentBuilder().setCurrency("p", 99.99).build();
            var b = new OdinDocumentBuilder().setCurrency("p", 99.99).build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void currencyDifferent() {
            var a = new OdinDocumentBuilder().setCurrency("p", 99.99).build();
            var b = new OdinDocumentBuilder().setCurrency("p", 49.99).build();
            assertFalse(Odin.diff(a, b).isEmpty());
        }

        @Test void stringEqual() {
            var a = new OdinDocumentBuilder().set("s", "hello").build();
            var b = new OdinDocumentBuilder().set("s", "hello").build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void stringDifferent() {
            var a = new OdinDocumentBuilder().set("s", "hello").build();
            var b = new OdinDocumentBuilder().set("s", "world").build();
            assertFalse(Odin.diff(a, b).isEmpty());
        }
    }

    // ─── Sectioned documents ─────────────────────────────────────────────

    @Nested class SectionedDocumentTests {
        @Test void sameSection() {
            var a = new OdinDocumentBuilder()
                .set("person.name", "Alice")
                .set("person.age", 30L)
                .build();
            var b = new OdinDocumentBuilder()
                .set("person.name", "Alice")
                .set("person.age", 30L)
                .build();
            assertTrue(Odin.diff(a, b).isEmpty());
        }

        @Test void sectionFieldChanged() {
            var a = new OdinDocumentBuilder()
                .set("person.name", "Alice")
                .build();
            var b = new OdinDocumentBuilder()
                .set("person.name", "Bob")
                .build();
            var diff = Odin.diff(a, b);
            assertEquals(1, diff.changed().size());
            assertEquals("person.name", diff.changed().get(0).path());
        }

        @Test void sectionFieldAdded() {
            var a = new OdinDocumentBuilder()
                .set("person.name", "Alice")
                .build();
            var b = new OdinDocumentBuilder()
                .set("person.name", "Alice")
                .set("person.age", 30L)
                .build();
            var diff = Odin.diff(a, b);
            assertEquals(1, diff.added().size());
            assertEquals("person.age", diff.added().get(0).path());
        }
    }

    // ─── Round-trip tests ────────────────────────────────────────────────

    @Nested class RoundTripTests {
        @Test void roundTripAddition() {
            var a = new OdinDocumentBuilder().set("x", 1L).build();
            var b = new OdinDocumentBuilder().set("x", 1L).set("y", 2L).build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertEquals(1L, result.getInteger("x"));
            assertEquals(2L, result.getInteger("y"));
        }

        @Test void roundTripRemoval() {
            var a = new OdinDocumentBuilder().set("x", 1L).set("y", 2L).build();
            var b = new OdinDocumentBuilder().set("x", 1L).build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertTrue(result.has("x"));
            assertFalse(result.has("y"));
        }

        @Test void roundTripChange() {
            var a = new OdinDocumentBuilder().set("x", "old").build();
            var b = new OdinDocumentBuilder().set("x", "new").build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertEquals("new", result.getString("x"));
        }

        @Test void roundTripMove() {
            var a = new OdinDocumentBuilder().set("oldKey", "val").build();
            var b = new OdinDocumentBuilder().set("newKey", "val").build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertFalse(result.has("oldKey"));
            assertEquals("val", result.getString("newKey"));
        }

        @Test void roundTripComplex() {
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
            var result = Odin.patch(a, diff);
            assertEquals("same", result.getString("keep"));
            assertEquals("new", result.getString("change"));
            assertEquals("fresh", result.getString("add"));
            assertFalse(result.has("remove"));
        }

        @Test void roundTripEmptyToPopulated() {
            var a = OdinDocument.empty();
            var b = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("age", 30L)
                .build();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertEquals("Alice", result.getString("name"));
            assertEquals(30L, result.getInteger("age"));
        }

        @Test void roundTripPopulatedToEmpty() {
            var a = new OdinDocumentBuilder()
                .set("name", "Alice")
                .set("age", 30L)
                .build();
            var b = OdinDocument.empty();
            var diff = Odin.diff(a, b);
            var result = Odin.patch(a, diff);
            assertFalse(result.has("name"));
            assertFalse(result.has("age"));
        }
    }

    // ─── Multi-field operations ──────────────────────────────────────────

    @Nested class MultiFieldTests {
        @Test void multipleAdditions() {
            var a = OdinDocument.empty();
            var b = new OdinDocumentBuilder()
                .set("a", 1L).set("b", 2L).set("c", 3L).build();
            var diff = Odin.diff(a, b);
            assertEquals(3, diff.added().size());
        }

        @Test void multipleRemovals() {
            var a = new OdinDocumentBuilder()
                .set("a", 1L).set("b", 2L).set("c", 3L).build();
            var b = OdinDocument.empty();
            var diff = Odin.diff(a, b);
            // Some may be detected as moves if values match
            int totalOps = diff.removed().size() + diff.moved().size();
            assertTrue(totalOps >= 3);
        }

        @Test void multipleChanges() {
            var a = new OdinDocumentBuilder()
                .set("a", "x").set("b", "y").set("c", "z").build();
            var b = new OdinDocumentBuilder()
                .set("a", "1").set("b", "2").set("c", "3").build();
            var diff = Odin.diff(a, b);
            assertEquals(3, diff.changed().size());
        }
    }

    // ─── OdinDiff isEmpty ────────────────────────────────────────────────

    @Nested class IsEmptyTests {
        @Test void emptyDiff() {
            var diff = new OdinDiff(List.of(), List.of(), List.of(), List.of());
            assertTrue(diff.isEmpty());
        }

        @Test void notEmptyWithAdded() {
            var diff = new OdinDiff(
                List.of(new OdinDiff.DiffEntry("x", OdinValue.ofNull())),
                List.of(), List.of(), List.of());
            assertFalse(diff.isEmpty());
        }

        @Test void notEmptyWithRemoved() {
            var diff = new OdinDiff(
                List.of(),
                List.of(new OdinDiff.DiffEntry("x", OdinValue.ofNull())),
                List.of(), List.of());
            assertFalse(diff.isEmpty());
        }

        @Test void notEmptyWithChanged() {
            var diff = new OdinDiff(
                List.of(), List.of(),
                List.of(new OdinDiff.DiffChange("x", OdinValue.ofNull(), OdinValue.ofString("a"))),
                List.of());
            assertFalse(diff.isEmpty());
        }

        @Test void notEmptyWithMoved() {
            var diff = new OdinDiff(
                List.of(), List.of(), List.of(),
                List.of(new OdinDiff.DiffMove("a", "b", OdinValue.ofNull())));
            assertFalse(diff.isEmpty());
        }
    }
}
