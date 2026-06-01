package foundation.odin.parsing;

import foundation.odin.Odin;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Typed tabular cells (integer/currency/negative) in any column position must keep
// their column — a typed cell must not drop or shift the row.
class TabularTypedCellsTest {

    private static OdinValue get(foundation.odin.types.OdinDocument doc, String path) {
        return doc.getAssignments().get(path);
    }

    private static long asInt(OdinValue v) {
        assertInstanceOf(OdinValue.OdinInteger.class, v);
        return ((OdinValue.OdinInteger) v).getValue();
    }

    private static String asStr(OdinValue v) {
        assertInstanceOf(OdinValue.OdinString.class, v);
        return ((OdinValue.OdinString) v).getValue();
    }

    // ── Happy path: typed cell in each column position ──

    @Nested
    class ColumnPositionTests {
        @Test
        void integerFirstColumnKeepsTrailingColumn() {
            var doc = Odin.parse("{rows[] : qty, name}\n##5, \"widget\"\n##12, \"gadget\"");
            assertEquals(5, asInt(get(doc, "rows[0].qty")));
            assertEquals("widget", asStr(get(doc, "rows[0].name")));
            assertEquals(12, asInt(get(doc, "rows[1].qty")));
            assertEquals("gadget", asStr(get(doc, "rows[1].name")));
        }

        @Test
        void negativeIntegerCellKeepsSignAndColumn() {
            var doc = Odin.parse("{temps[] : label, value}\n\"low\", ##-5\n\"high\", ##42");
            assertEquals("low", asStr(get(doc, "temps[0].label")));
            assertEquals(-5, asInt(get(doc, "temps[0].value")));
            assertEquals("high", asStr(get(doc, "temps[1].label")));
            assertEquals(42, asInt(get(doc, "temps[1].value")));
        }

        @Test
        void interleavedTypedColumnsKeepEveryColumn() {
            var doc = Odin.parse("{items[] : qty, name, price}\n##10, \"Widget\", #$5.99\n##5, \"Gadget\", #$12.50");
            assertEquals(10, asInt(get(doc, "items[0].qty")));
            assertEquals("Widget", asStr(get(doc, "items[0].name")));
            assertInstanceOf(OdinValue.OdinCurrency.class, get(doc, "items[0].price"));
            assertEquals(5, asInt(get(doc, "items[1].qty")));
            assertEquals("Gadget", asStr(get(doc, "items[1].name")));
            assertInstanceOf(OdinValue.OdinCurrency.class, get(doc, "items[1].price"));
        }
    }

    // ── Edge cases ──

    @Nested
    class EdgeCaseTests {
        @Test
        void allTypedRowKeepsEveryColumn() {
            var doc = Odin.parse("{points[] : x, y, z}\n##1, ##2, ##3\n##-4, ##5, ##-6");
            assertEquals(1, asInt(get(doc, "points[0].x")));
            assertEquals(2, asInt(get(doc, "points[0].y")));
            assertEquals(3, asInt(get(doc, "points[0].z")));
            assertEquals(-4, asInt(get(doc, "points[1].x")));
            assertEquals(5, asInt(get(doc, "points[1].y")));
            assertEquals(-6, asInt(get(doc, "points[1].z")));
        }

        @Test
        void singleIntegerColumnProducesObjectArray() {
            var doc = Odin.parse("{counts[] : value}\n##42\n##0");
            assertEquals(42, asInt(get(doc, "counts[0].value")));
            assertEquals(0, asInt(get(doc, "counts[1].value")));
        }

        @Test
        void largeIntegerCellRetainsPrecision() {
            var doc = Odin.parse("{big[] : label, n}\n\"max\", ##9007199254740991");
            assertEquals("max", asStr(get(doc, "big[0].label")));
            assertEquals(9007199254740991L, asInt(get(doc, "big[0].n")));
        }
    }
}
