package foundation.odin.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    // ── BuildPath ──

    @Nested
    class BuildPathTests {
        @Test void singleSegment() {
            assertEquals("name", PathUtils.buildPath("name"));
        }

        @Test void multipleSegments() {
            assertEquals("Customer.Address.City", PathUtils.buildPath("Customer", "Address", "City"));
        }

        @Test void emptySegments() {
            assertEquals("", PathUtils.buildPath());
        }
    }

    // ── BuildPathWithIndices ──

    @Nested
    class BuildPathWithIndicesTests {
        @Test void noIndex() {
            var result = PathUtils.buildPathWithIndices(
                    new PathUtils.Segment("Customer", null),
                    new PathUtils.Segment("Name", null));
            assertEquals("Customer.Name", result);
        }

        @Test void withIndex() {
            var result = PathUtils.buildPathWithIndices(
                    new PathUtils.Segment("items", 0),
                    new PathUtils.Segment("name", null));
            assertEquals("items[0].name", result);
        }

        @Test void multipleIndices() {
            var result = PathUtils.buildPathWithIndices(
                    new PathUtils.Segment("matrix", 1),
                    new PathUtils.Segment("row", 2));
            assertEquals("matrix[1].row[2]", result);
        }
    }

    // ── SplitPath ──

    @Nested
    class SplitPathTests {
        @Test void simpleSegments() {
            assertEquals(List.of("Customer", "Address", "City"), PathUtils.splitPath("Customer.Address.City"));
        }

        @Test void singleSegment() {
            var segments = PathUtils.splitPath("name");
            assertEquals(1, segments.size());
            assertEquals("name", segments.get(0));
        }

        @Test void withArrayIndex() {
            assertEquals(List.of("items[0]", "name"), PathUtils.splitPath("items[0].name"));
        }

        @Test void emptyString() {
            assertTrue(PathUtils.splitPath("").isEmpty());
        }
    }

    // ── ParentPath ──

    @Nested
    class ParentPathTests {
        @Test void multiLevel() {
            assertEquals("Customer.Address", PathUtils.parentPath("Customer.Address.City"));
        }

        @Test void twoLevel() {
            assertEquals("Customer", PathUtils.parentPath("Customer.Name"));
        }

        @Test void singleSegmentReturnsNull() {
            assertNull(PathUtils.parentPath("name"));
        }
    }

    // ── LeafName ──

    @Nested
    class LeafNameTests {
        @Test void multiLevel() {
            assertEquals("City", PathUtils.leafName("Customer.Address.City"));
        }

        @Test void singleSegment() {
            assertEquals("name", PathUtils.leafName("name"));
        }

        @Test void withArrayIndex() {
            assertEquals("items[0]", PathUtils.leafName("data.items[0]"));
        }
    }

    // ── StartsWith ──

    @Nested
    class StartsWithTests {
        @Test void exactMatch() {
            assertTrue(PathUtils.startsWith("Customer", "Customer"));
        }

        @Test void dotSeparated() {
            assertTrue(PathUtils.startsWith("Customer.Name", "Customer"));
        }

        @Test void arrayIndex() {
            assertTrue(PathUtils.startsWith("items[0].name", "items"));
        }

        @Test void falseForPartialSegment() {
            assertFalse(PathUtils.startsWith("Customer.Name", "Cust"));
        }

        @Test void falseForDifferentPath() {
            assertFalse(PathUtils.startsWith("Order.Id", "Customer"));
        }
    }

    // ── ParseSegment ──

    @Nested
    class ParseSegmentTests {
        @Test void plainName() {
            var result = PathUtils.parseSegment("items");
            assertEquals("items", result.name());
            assertNull(result.index());
        }

        @Test void withIndex() {
            var result = PathUtils.parseSegment("items[3]");
            assertEquals("items", result.name());
            assertEquals(3, result.index());
        }

        @Test void zeroIndex() {
            var result = PathUtils.parseSegment("arr[0]");
            assertEquals("arr", result.name());
            assertEquals(0, result.index());
        }
    }
}
