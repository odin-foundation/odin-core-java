package foundation.odin.types;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class OrderedMapTest {

    @Nested
    class CoreOperations {
        @Test void setAndGetByKey() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            assertEquals(1, map.get("a"));
        }

        @Test void setOverwritesExistingKeyInPlace() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            map.set("b", 2);
            map.set("a", 10);
            assertEquals(10, map.get("a"));
            // Order preserved: a is still first
            assertEquals("a", map.keys().get(0));
            assertEquals("b", map.keys().get(1));
        }

        @Test void insertionOrderIsPreserved() {
            var map = new OrderedMap<String, Integer>();
            map.set("c", 3);
            map.set("a", 1);
            map.set("b", 2);
            assertEquals(List.of("c", "a", "b"), map.keys());
        }

        @Test void containsKeyReturnsTrueForExistingKey() {
            var map = new OrderedMap<String, Integer>();
            map.set("x", 42);
            assertTrue(map.containsKey("x"));
            assertFalse(map.containsKey("y"));
        }

        @Test void tryGetReturnsCorrectResult() {
            var map = new OrderedMap<String, Integer>();
            map.set("key", 99);
            assertEquals(99, map.tryGet("key"));
            assertNull(map.tryGet("missing"));
        }

        @Test void removeExistingKeyReturnsTrue() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            map.set("b", 2);
            map.set("c", 3);
            assertTrue(map.remove("b"));
            assertEquals(2, map.size());
            assertFalse(map.containsKey("b"));
            assertEquals(List.of("a", "c"), map.keys());
        }

        @Test void removeNonexistentKeyReturnsFalse() {
            var map = new OrderedMap<String, Integer>();
            assertFalse(map.remove("missing"));
        }

        @Test void clearRemovesAllEntries() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            map.set("b", 2);
            map.clear();
            assertEquals(0, map.size());
        }

        @Test void countReflectsNumberOfEntries() {
            var map = new OrderedMap<String, Integer>();
            assertEquals(0, map.size());
            map.set("a", 1);
            assertEquals(1, map.size());
            map.set("b", 2);
            assertEquals(2, map.size());
        }

        @Test void copyCreatesIndependentCopy() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            var clone = map.copy();
            clone.set("b", 2);
            assertEquals(1, map.size());
            assertEquals(2, clone.size());
        }

        @Test void enumerationYieldsEntriesInOrder() {
            var map = new OrderedMap<String, Integer>();
            map.set("x", 10);
            map.set("y", 20);
            var keys = new ArrayList<String>();
            for (var entry : map) keys.add(entry.getKey());
            assertEquals(List.of("x", "y"), keys);
        }
    }

    @Nested
    class Accessors {
        @Test void getThrowsForMissingKey() {
            var map = new OrderedMap<String, Integer>();
            assertThrows(NoSuchElementException.class, () -> map.get("missing"));
        }

        @Test void valuesReturnsAllValuesInOrder() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            map.set("b", 2);
            assertEquals(List.of(1, 2), map.values());
        }

        @Test void entriesReturnsReadOnlyList() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            var entries = map.entries();
            assertEquals(1, entries.size());
        }
    }

    @Nested
    class Constructors {
        @Test void constructorWithCapacity() {
            var map = new OrderedMap<String, Integer>(16);
            assertEquals(0, map.size());
            map.set("a", 1);
            assertEquals(1, map.size());
        }

        @Test void constructorFromEntries() {
            var source = List.<Map.Entry<String, Integer>>of(
                    Map.entry("a", 1), Map.entry("b", 2));
            var map = new OrderedMap<>(source);
            assertEquals(2, map.size());
            assertEquals(1, map.get("a"));
        }
    }

    @Nested
    class PositionOperations {
        @Test void getAtReturnsEntryByIndex() {
            var map = new OrderedMap<String, Integer>();
            map.set("a", 1);
            map.set("b", 2);
            var entry = map.getAt(1);
            assertEquals("b", entry.getKey());
            assertEquals(2, entry.getValue());
        }

        @Test void removeFirstEntryShiftsIndicesCorrectly() {
            var map = new OrderedMap<String, Integer>();
            map.set("first", 1);
            map.set("second", 2);
            map.set("third", 3);
            map.remove("first");
            assertEquals(2, map.get("second"));
            assertEquals(3, map.get("third"));
            assertEquals("second", map.getAt(0).getKey());
        }
    }
}
