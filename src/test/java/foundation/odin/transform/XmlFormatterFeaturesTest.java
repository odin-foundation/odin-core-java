package foundation.odin.transform;

import foundation.odin.Odin;
import foundation.odin.types.DynValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for two XML target features in the transform engine:
 *   (A) target namespaces ({$target.namespace} + :ns field modifier)
 *   (B) emitTypeHints (suppresses odin: attributes when false)
 *
 * Tests parse a small transform, run the engine, and assert on getFormatted().
 */
class XmlFormatterFeaturesTest {

    private static Map.Entry<String, DynValue> kv(String k, DynValue v) { return Map.entry(k, v); }
    private static DynValue str(String s) { return DynValue.ofString(s); }

    // Source object with one integer and one currency value.
    private static DynValue typedSource() {
        return DynValue.ofObject(List.of(
                kv("count", DynValue.ofInteger(42)),
                kv("price", DynValue.ofCurrency(9.99, (byte) 2, "USD"))));
    }

    private static String run(String transformText, DynValue source) {
        var t = TransformParser.parse(transformText);
        var result = TransformEngine.execute(t, source);
        assertTrue(result.isSuccess(), () -> "transform failed: " + result.getErrors());
        return result.getFormatted();
    }

    // ── (B) emitTypeHints ──

    @Nested class EmitTypeHintsTests {

        // Default (true): integer and currency values carry odin:type and the odin namespace.
        @Test void defaultEmitsTypeHints() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"

                    {$source}
                    format = "odin"

                    {Data}
                    Count = @.count
                    Price = @.price
                    """;
            var xml = run(transform, typedSource());

            assertTrue(xml.contains("xmlns:odin=\"https://odin.foundation/ns\""), xml);
            assertTrue(xml.contains("odin:type=\"integer\""), xml);
            // Values present regardless of type-hint shape.
            assertTrue(xml.contains(">42<"), xml);
            assertTrue(xml.contains(">9.99<"), xml);
        }

        // Currency from raw odin source renders odin:type="currency" + odin:currencyCode.
        @Test void currencyEmitsCurrencyTypeAndCode() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"

                    {$source}
                    format = "odin"

                    {Data}
                    Total = @.total
                    """;
            var doc = Odin.parse("total = #$9.99:USD");
            var source = DynValue.ofObject(List.of(
                    kv("total", TransformEngine.odinValueToDyn(doc.get("total")))));
            var xml = run(transform, source);

            assertTrue(xml.contains("odin:type=\"currency\""), xml);
            assertTrue(xml.contains("odin:currencyCode=\"USD\""), xml);
            assertTrue(xml.contains(">9.99<"), xml);
        }

        // Code-less currency is first-class currency with preserved decimals, no code.
        @Test void codelessCurrencyRendersAsCurrencyWithDecimals() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"

                    {$source}
                    format = "odin"

                    {Data}
                    Total = @.total
                    """;
            var doc = Odin.parse("total = #$50.00");
            var source = DynValue.ofObject(List.of(
                    kv("total", TransformEngine.odinValueToDyn(doc.get("total")))));
            var xml = run(transform, source);

            assertTrue(xml.contains("odin:type=\"currency\""), xml);
            assertFalse(xml.contains("odin:currencyCode"), xml);
            assertTrue(xml.contains(">50.00<"), xml);
        }

        // emitTypeHints=false: no odin: attributes at all, values still rendered.
        @Test void disabledSuppressesAllOdinAttributes() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"
                    target.emitTypeHints = ?false

                    {$source}
                    format = "odin"

                    {Data}
                    Count = @.count
                    Price = @.price
                    """;
            var xml = run(transform, typedSource());

            assertFalse(xml.contains("odin:type"), xml);
            assertFalse(xml.contains("odin:currencyCode"), xml);
            assertFalse(xml.contains("xmlns:odin"), xml);
            assertFalse(xml.contains("odin:"), xml);
            // Values still present.
            assertTrue(xml.contains(">42<"), xml);
            assertTrue(xml.contains(">9.99<"), xml);
        }
    }

    // ── (A) target namespaces ──

    @Nested class NamespaceTests {

        // :ns p prefixes the element and declares xmlns:p on the root; a field
        // without :ns stays unprefixed.
        @Test void nsModifierPrefixesElementAndRootDeclaresXmlns() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"

                    {$source}
                    format = "odin"

                    {$target.namespace}
                    p = "urn:x"

                    {Doc}
                    Prefixed = @.a :ns p
                    Plain = @.b
                    """;
            var source = DynValue.ofObject(List.of(kv("a", str("AA")), kv("b", str("BB"))));
            var xml = run(transform, source);

            assertTrue(xml.contains("xmlns:p=\"urn:x\""), xml);
            assertTrue(xml.contains("<p:Prefixed>AA</p:Prefixed>"), xml);
            assertTrue(xml.contains("<Plain>BB</Plain>"), xml);
            assertFalse(xml.contains("<p:Plain>"), xml);
        }

        // Two prefixes → both xmlns: declarations on the root element.
        @Test void multipleNamespacesAllDeclaredOnRoot() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"

                    {$source}
                    format = "odin"

                    {$target.namespace}
                    p = "urn:one"
                    q = "urn:two"

                    {Doc}
                    First = @.a :ns p
                    Second = @.b :ns q
                    """;
            var source = DynValue.ofObject(List.of(kv("a", str("AA")), kv("b", str("BB"))));
            var xml = run(transform, source);

            assertTrue(xml.contains("xmlns:p=\"urn:one\""), xml);
            assertTrue(xml.contains("xmlns:q=\"urn:two\""), xml);
            assertTrue(xml.contains("<p:First>AA</p:First>"), xml);
            assertTrue(xml.contains("<q:Second>BB</q:Second>"), xml);
        }

        // Namespace + emitTypeHints=false together: prefixed element + xmlns: present,
        // but no odin: attributes (no xmlns:odin) even with a typed value.
        @Test void namespaceWithTypeHintsDisabled() {
            var transform = """
                    {$}
                    odin = "1.0.0"
                    transform = "1.0.0"
                    direction = "odin->xml"
                    target.format = "xml"
                    target.emitTypeHints = ?false

                    {$source}
                    format = "odin"

                    {$target.namespace}
                    p = "urn:x"

                    {Doc}
                    Amount = @.amount :ns p
                    Note = @.note
                    """;
            var source = DynValue.ofObject(List.of(
                    kv("amount", DynValue.ofInteger(7)),
                    kv("note", str("hi"))));
            var xml = run(transform, source);

            assertTrue(xml.contains("xmlns:p=\"urn:x\""), xml);
            assertTrue(xml.contains("<p:Amount>7</p:Amount>"), xml);
            assertFalse(xml.contains("odin:"), xml);
            assertFalse(xml.contains("xmlns:odin"), xml);
        }
    }
}
