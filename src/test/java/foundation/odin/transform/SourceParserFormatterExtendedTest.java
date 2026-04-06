package foundation.odin.transform;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended source parser and formatter tests.
 * Ported from .NET SourceParserFormatterExtendedTests.cs
 */
class SourceParserFormatterExtendedTest {

    // ─── Helpers ──────────────────────────────────────────────────────

    private static String getStr(DynValue obj, String key) {
        var entries = obj.asObject();
        if (entries == null) return "";
        for (var entry : entries) {
            if (entry.getKey().equals(key)) return entry.getValue().asString() != null ? entry.getValue().asString() : "";
        }
        return "";
    }

    private static DynValue getField(DynValue obj, String key) {
        var entries = obj.asObject();
        if (entries == null) return null;
        for (var entry : entries) {
            if (entry.getKey().equals(key)) return entry.getValue();
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════
    //  JSON Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void jsonExt_ArrayOfObjects() {
        var result = JsonSourceParser.parse("[{\"id\": 1}, {\"id\": 2}, {\"id\": 3}]");
        var arr = result.asArray();
        assertEquals(3, arr.size());
        assertEquals(1L, getField(arr.get(0), "id").asInt64());
        assertEquals(3L, getField(arr.get(2), "id").asInt64());
    }

    @Test void jsonExt_NullValuesInObject() {
        var result = JsonSourceParser.parse("{\"a\": null, \"b\": null}");
        assertTrue(getField(result, "a").isNull());
        assertTrue(getField(result, "b").isNull());
    }

    @Test void jsonExt_NegativeNumbers() {
        var result = JsonSourceParser.parse("{\"neg\": -42, \"negf\": -3.14}");
        assertEquals(-42L, getField(result, "neg").asInt64());
        assertEquals(-3.14, getField(result, "negf").asDouble());
    }

    @Test void jsonExt_StringWithEscapes() {
        var result = JsonSourceParser.parse("{\"msg\": \"line1\\nline2\\ttab\"}");
        assertTrue(getStr(result, "msg").contains("\n"));
        assertTrue(getStr(result, "msg").contains("\t"));
    }

    @Test void jsonExt_DeeplyNestedFiveLevels() {
        var result = JsonSourceParser.parse("{\"l1\": {\"l2\": {\"l3\": {\"l4\": {\"l5\": \"deep\"}}}}}");
        var l1 = getField(result, "l1");
        var l2 = getField(l1, "l2");
        var l3 = getField(l2, "l3");
        var l4 = getField(l3, "l4");
        assertEquals("deep", getStr(l4, "l5"));
    }

    @Test void jsonExt_MixedArray() {
        var result = JsonSourceParser.parse("[1, \"two\", true, null, 3.14]");
        var arr = result.asArray();
        assertEquals(5, arr.size());
        assertEquals(1L, arr.get(0).asInt64());
        assertEquals("two", arr.get(1).asString());
        assertEquals(true, arr.get(2).asBool());
        assertTrue(arr.get(3).isNull());
        assertEquals(3.14, arr.get(4).asDouble());
    }

    @Test void jsonExt_LargeInteger() {
        var result = JsonSourceParser.parse("{\"big\": 9007199254740992}");
        assertEquals(9007199254740992L, getField(result, "big").asInt64());
    }

    @Test void jsonExt_ZeroValues() {
        var result = JsonSourceParser.parse("{\"i\": 0, \"f\": 0.0}");
        assertEquals(0L, getField(result, "i").asInt64());
    }

    @Test void jsonExt_NestedArrays() {
        var result = JsonSourceParser.parse("[[1, 2], [3, 4]]");
        var outer = result.asArray();
        assertEquals(2, outer.size());
        var inner = outer.get(0).asArray();
        assertEquals(1L, inner.get(0).asInt64());
        assertEquals(2L, inner.get(1).asInt64());
    }

    @Test void jsonExt_WhitespaceVariations() {
        var result = JsonSourceParser.parse("  {  \"a\"  :  1  }  ");
        assertEquals(1L, getField(result, "a").asInt64());
    }

    @Test void jsonExt_SingleValue_String() {
        var result = JsonSourceParser.parse("\"hello\"");
        assertEquals("hello", result.asString());
    }

    @Test void jsonExt_SingleValue_Number() {
        var result = JsonSourceParser.parse("42");
        assertEquals(42L, result.asInt64());
    }

    @Test void jsonExt_SingleValue_Boolean() {
        var result = JsonSourceParser.parse("true");
        assertEquals(true, result.asBool());
    }

    @Test void jsonExt_SingleValue_Null() {
        var result = JsonSourceParser.parse("null");
        assertTrue(result.isNull());
    }

    // ═════════════════════════════════════════════════════════════════
    //  CSV Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void csvExt_SingleColumn() {
        var result = CsvSourceParser.parse("name\nAlice\nBob", null);
        var arr = result.asArray();
        assertEquals(2, arr.size());
        assertEquals("Alice", getStr(arr.get(0), "name"));
    }

    @Test void csvExt_ManyRows() {
        var sb = new StringBuilder("id,val\n");
        for (int i = 0; i < 50; i++) sb.append(i).append(",data").append(i).append("\n");
        var result = CsvSourceParser.parse(sb.toString(), null);
        assertEquals(50, result.asArray().size());
    }

    @Test void csvExt_SemicolonDelimiter() {
        var config = new SourceConfig();
        config.setFormat("csv");
        config.setOptions(Map.of("delimiter", ";"));
        var result = CsvSourceParser.parse("a;b\n1;2", config);
        var arr = result.asArray();
        assertEquals(1, arr.size());
    }

    @Test void csvExt_HeaderOnlyNoData() {
        var result = CsvSourceParser.parse("name,age", null);
        var arr = result.asArray();
        assertTrue(arr.isEmpty());
    }

    @Test void csvExt_NullInference() {
        var result = CsvSourceParser.parse("val\nnull", null);
        var arr = result.asArray();
        assertTrue(getField(arr.get(0), "val").isNull());
    }

    @Test void csvExt_MixedTypeInference() {
        var result = CsvSourceParser.parse("a,b,c,d\n42,3.14,true,hello", null);
        var arr = result.asArray();
        var row = arr.get(0);
        assertEquals(42L, getField(row, "a").asInt64());
        assertEquals(3.14, getField(row, "b").asDouble());
        assertEquals(true, getField(row, "c").asBool());
        assertEquals("hello", getStr(row, "d"));
    }

    @Test void csvExt_EscapedQuoteInField() {
        var result = CsvSourceParser.parse("val\n\"he said \"\"hi\"\"\"", null);
        var arr = result.asArray();
        assertTrue(getStr(arr.get(0), "val").contains("\"hi\""));
    }

    @Test void csvExt_FieldWithNewline() {
        var result = CsvSourceParser.parse("val\n\"line1\nline2\"", null);
        var arr = result.asArray();
        assertTrue(getStr(arr.get(0), "val").contains("\n"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  XML Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void xmlExt_MultipleAttributes() {
        var result = XmlSourceParser.parse("<item id=\"1\" type=\"product\"><name>Widget</name></item>");
        var root = getField(result, "item");
        assertEquals("1", getStr(root, "@id"));
        assertEquals("product", getStr(root, "@type"));
    }

    @Test void xmlExt_DeeplyNested() {
        var result = XmlSourceParser.parse("<a><b><c><d><e>deep</e></d></c></b></a>");
        var a = getField(result, "a");
        var b = getField(a, "b");
        var c = getField(b, "c");
        var d = getField(c, "d");
        assertEquals("deep", getStr(d, "e"));
    }

    @Test void xmlExt_EmptyRoot() {
        var result = XmlSourceParser.parse("<root></root>");
        var root = getField(result, "root");
        assertNotNull(root);
    }

    @Test void xmlExt_MultipleRepeatedElements() {
        var result = XmlSourceParser.parse("<root><a>1</a><a>2</a><b>x</b><b>y</b></root>");
        var root = getField(result, "root");
        var aField = getField(root, "a");
        assertNotNull(aField);
        assertEquals(DynValue.Type.Array, aField.getType());
        assertEquals(2, aField.asArray().size());
    }

    @Test void xmlExt_SelfClosingMultiple() {
        var result = XmlSourceParser.parse("<root><br/><hr/></root>");
        var root = getField(result, "root");
        assertTrue(getField(root, "br").isNull());
        assertTrue(getField(root, "hr").isNull());
    }

    @Test void xmlExt_SpecialCharsInText() {
        var result = XmlSourceParser.parse("<msg>Price: 5 &lt; 10 &amp; 3 &gt; 1</msg>");
        var msg = getField(result, "msg");
        assertNotNull(msg);
        var text = msg.asString() != null ? msg.asString() : getStr(result, "msg");
        assertTrue(text.contains("<"));
        assertTrue(text.contains("&"));
    }

    @Test void xmlExt_NumericTextContent() {
        var result = XmlSourceParser.parse("<root><count>42</count></root>");
        var root = getField(result, "root");
        assertEquals("42", getStr(root, "count"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  YAML Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void yamlExt_DeeplyNested() {
        var result = YamlSourceParser.parse("a:\n  b:\n    c:\n      d: deep");
        var a = getField(result, "a");
        var b = getField(a, "b");
        var c = getField(b, "c");
        assertEquals("deep", getStr(c, "d"));
    }

    @Test void yamlExt_BooleanVariants() {
        var result = YamlSourceParser.parse("a: yes\nb: no\nc: on\nd: off\ne: true\nf: false");
        assertEquals(true, getField(result, "a").asBool());
        assertEquals(false, getField(result, "b").asBool());
        assertEquals(true, getField(result, "c").asBool());
        assertEquals(false, getField(result, "d").asBool());
        assertEquals(true, getField(result, "e").asBool());
        assertEquals(false, getField(result, "f").asBool());
    }

    @Test void yamlExt_ArrayOfScalars() {
        var result = YamlSourceParser.parse("items:\n  - 1\n  - 2\n  - 3");
        var items = getField(result, "items").asArray();
        assertEquals(3, items.size());
    }

    @Test void yamlExt_MixedTypes() {
        var result = YamlSourceParser.parse("s: hello\ni: 42\nf: 3.14\nb: true\nn: null");
        assertEquals("hello", getStr(result, "s"));
        assertEquals(42L, getField(result, "i").asInt64());
        assertEquals(3.14, getField(result, "f").asDouble());
        assertEquals(true, getField(result, "b").asBool());
        assertTrue(getField(result, "n").isNull());
    }

    @Test void yamlExt_MultipleComments() {
        var result = YamlSourceParser.parse("# first\nname: Alice\n# middle\nage: 30\n# end");
        assertEquals("Alice", getStr(result, "name"));
        assertEquals(30L, getField(result, "age").asInt64());
    }

    @Test void yamlExt_EmptyValue() {
        var result = YamlSourceParser.parse("val:");
        assertTrue(getField(result, "val").isNull());
    }

    @Test void yamlExt_SingleQuotedString() {
        var result = YamlSourceParser.parse("val: 'hello world'");
        assertEquals("hello world", getStr(result, "val"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fixed-Width Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void fixedExt_VariousWidths() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "A:0:2;B:2:4;C:6:4;D:10:2"));
        var result = FixedWidthSourceParser.parse("AB1234CDEF56", config);
        assertEquals("AB", getStr(result, "A"));
        assertEquals("1234", getStr(result, "B"));
        assertEquals("CDEF", getStr(result, "C"));
        assertEquals("56", getStr(result, "D"));
    }

    @Test void fixedExt_TrimmingWithPadding() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "A:0:10;B:10:10"));
        var result = FixedWidthSourceParser.parse("Hello     World     ", config);
        assertEquals("Hello", getStr(result, "A"));
        assertEquals("World", getStr(result, "B"));
    }

    @Test void fixedExt_FieldBeyondLine() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Present:0:2;Absent:50:10"));
        var result = FixedWidthSourceParser.parse("AB", config);
        assertEquals("AB", getStr(result, "Present"));
        assertEquals("", getStr(result, "Absent"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Flat Source Parser Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void flatExt_MixedTypes() {
        var result = FlatSourceParser.parse("s=hello\ni=42\nf=3.14\nb=true\nn=~");
        assertEquals("hello", getStr(result, "s"));
        assertEquals(42L, getField(result, "i").asInt64());
        assertEquals(3.14, getField(result, "f").asDouble());
        assertEquals(true, getField(result, "b").asBool());
        assertTrue(getField(result, "n").isNull());
    }

    @Test void flatExt_DeeplyDottedPath() {
        var result = FlatSourceParser.parse("a.b.c.d=deep");
        var a = getField(result, "a");
        var b = getField(a, "b");
        var c = getField(b, "c");
        assertEquals("deep", getStr(c, "d"));
    }

    @Test void flatExt_MultipleArrayIndices() {
        var result = FlatSourceParser.parse("items[0]=a\nitems[1]=b\nitems[2]=c");
        var items = getField(result, "items").asArray();
        assertEquals(3, items.size());
    }

    @Test void flatExt_SpacesAroundEquals() {
        var result = FlatSourceParser.parse("name = Alice\nage = 30");
        assertEquals("Alice", getStr(result, "name"));
        assertEquals(30L, getField(result, "age").asInt64());
    }

    // ═════════════════════════════════════════════════════════════════
    //  JSON Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void jsonFmtExt_CompactObject() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("a", DynValue.ofInteger(1)),
                Map.entry("b", DynValue.ofString("two"))
        ));
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(obj, config);
        assertFalse(result.contains("\n"));
        assertTrue(result.contains("\"a\""));
        assertTrue(result.contains("\"two\""));
    }

    @Test void jsonFmtExt_NestedObjects() {
        var inner = DynValue.ofObject(List.of(Map.entry("inner", DynValue.ofInteger(42))));
        var outer = DynValue.ofObject(List.of(Map.entry("outer", inner)));
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(outer, config);
        assertTrue(result.contains("\"outer\""));
        assertTrue(result.contains("\"inner\""));
        assertTrue(result.contains("42"));
    }

    @Test void jsonFmtExt_ArraySimple() {
        var arr = DynValue.ofArray(List.of(DynValue.ofInteger(1), DynValue.ofInteger(2), DynValue.ofInteger(3)));
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(arr, config);
        assertTrue(result.contains("1"));
        assertTrue(result.contains("3"));
    }

    @Test void jsonFmtExt_NullValue() {
        var result = JsonFormatter.format(DynValue.ofNull(), null);
        assertTrue(result.contains("null"));
    }

    @Test void jsonFmtExt_EmptyObject() {
        var obj = DynValue.ofObject(List.of());
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(obj, config);
        assertEquals("{}", result);
    }

    @Test void jsonFmtExt_EmptyArray() {
        var arr = DynValue.ofArray(List.of());
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(arr, config);
        assertEquals("[]", result);
    }

    @Test void jsonFmtExt_IntegerValues() {
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        assertEquals("0", JsonFormatter.format(DynValue.ofInteger(0), config));
        assertEquals("-99", JsonFormatter.format(DynValue.ofInteger(-99), config));
    }

    @Test void jsonFmtExt_FloatValue() {
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var result = JsonFormatter.format(DynValue.ofFloat(3.14), config);
        assertTrue(result.startsWith("3.14"));
    }

    @Test void jsonFmtExt_BoolValues() {
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        assertEquals("true", JsonFormatter.format(DynValue.ofBool(true), config));
        assertEquals("false", JsonFormatter.format(DynValue.ofBool(false), config));
    }

    // ═════════════════════════════════════════════════════════════════
    //  CSV Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void csvFmtExt_BasicHeadersAndRows() {
        var val = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("Alice")), Map.entry("age", DynValue.ofInteger(30)))),
                DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("Bob")), Map.entry("age", DynValue.ofInteger(25))))
        ));
        var csv = CsvFormatter.format(val, null);
        var lines = csv.split("\n");
        assertTrue(lines[0].contains("name"));
        assertTrue(lines[0].contains("age"));
        assertTrue(csv.contains("Alice"));
        assertTrue(csv.contains("Bob"));
    }

    @Test void csvFmtExt_QuotingCommas() {
        var val = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("msg", DynValue.ofString("hello, world"))))
        ));
        var csv = CsvFormatter.format(val, null);
        assertTrue(csv.contains("\"hello, world\""));
    }

    @Test void csvFmtExt_QuotingQuotes() {
        var val = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("msg", DynValue.ofString("say \"hi\""))))
        ));
        var csv = CsvFormatter.format(val, null);
        assertTrue(csv.contains("\"\"hi\"\""));
    }

    @Test void csvFmtExt_EmptyArray() {
        var csv = CsvFormatter.format(DynValue.ofArray(List.of()), null);
        assertTrue(csv.isEmpty() || csv.trim().isEmpty());
    }

    @Test void csvFmtExt_NullAsEmpty() {
        var val = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("a", DynValue.ofString("x")), Map.entry("b", DynValue.ofNull())))
        ));
        var csv = CsvFormatter.format(val, null);
        assertTrue(csv.contains("x,"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  XML Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void xmlFmtExt_NestedObjects() {
        var val = DynValue.ofObject(List.of(
                Map.entry("person", DynValue.ofObject(List.of(
                        Map.entry("address", DynValue.ofObject(List.of(
                                Map.entry("city", DynValue.ofString("NYC"))
                        )))
                )))
        ));
        var xml = XmlFormatter.format(val, null);
        assertTrue(xml.contains("<person>"));
        assertTrue(xml.contains("<address>"));
        assertTrue(xml.contains("<city>NYC</city>"));
    }

    @Test void xmlFmtExt_SpecialCharsEscaped() {
        var val = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("msg", DynValue.ofString("a & b < c"))
                )))
        ));
        var xml = XmlFormatter.format(val, null);
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&lt;"));
    }

    @Test void xmlFmtExt_IntegerElement() {
        var val = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("count", DynValue.ofInteger(42))
                )))
        ));
        var xml = XmlFormatter.format(val, null);
        assertTrue(xml.contains("<count odin:type=\"integer\">42</count>"));
    }

    @Test void xmlFmtExt_NullSelfClosing() {
        var val = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("empty", DynValue.ofNull())
                )))
        ));
        var xml = XmlFormatter.format(val, null);
        assertTrue(xml.contains("<empty odin:type=\"null\"></empty>"));
    }

    @Test void xmlFmtExt_BoolElement() {
        var val = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("yes", DynValue.ofBool(true)),
                        Map.entry("no", DynValue.ofBool(false))
                )))
        ));
        var xml = XmlFormatter.format(val, null);
        assertTrue(xml.contains("<yes odin:type=\"boolean\">true</yes>"));
        assertTrue(xml.contains("<no odin:type=\"boolean\">false</no>"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fixed-Width Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void fixedFmtExt_StringLeftAligned() {
        var obj = DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("Hi"))));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "name:10"));
        var result = FixedWidthFormatter.format(obj, config);
        assertTrue(result.contains("Hi"));
        // Line length before newline should be >= 10 (padding preserved)
        String line = result.split("\n")[0];
        assertTrue(line.length() >= 10);
    }

    @Test void fixedFmtExt_TruncationString() {
        var obj = DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("VeryLongName"))));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "name:5"));
        var result = FixedWidthFormatter.format(obj, config);
        assertTrue(result.trim().length() <= 5);
    }

    @Test void fixedFmtExt_MultipleRecords() {
        var val = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("id", DynValue.ofInteger(1)))),
                DynValue.ofObject(List.of(Map.entry("id", DynValue.ofInteger(2)))),
                DynValue.ofObject(List.of(Map.entry("id", DynValue.ofInteger(3))))
        ));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "id:5"));
        var result = FixedWidthFormatter.format(val, config);
        assertEquals(3, result.trim().split("\n").length);
    }

    @Test void fixedFmtExt_MissingField() {
        var obj = DynValue.ofObject(List.of(Map.entry("a", DynValue.ofString("x"))));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "a:5;missing:5"));
        var result = FixedWidthFormatter.format(obj, config);
        // Line length before newline should be 10 (5 + 5 with padding)
        String line = result.split("\n")[0];
        assertEquals(10, line.length());
    }

    // ═════════════════════════════════════════════════════════════════
    //  Flat Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void flatFmtExt_NestedObject() {
        var inner = DynValue.ofObject(List.of(Map.entry("city", DynValue.ofString("Portland"))));
        var outer = DynValue.ofObject(List.of(Map.entry("address", inner)));
        var result = FlatFormatter.format(outer, null);
        assertTrue(result.contains("address.city=Portland"));
    }

    @Test void flatFmtExt_ArrayValues() {
        var arr = DynValue.ofArray(List.of(DynValue.ofString("a"), DynValue.ofString("b"), DynValue.ofString("c")));
        var obj = DynValue.ofObject(List.of(Map.entry("items", arr)));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("items[0]=a"));
        assertTrue(result.contains("items[1]=b"));
        assertTrue(result.contains("items[2]=c"));
    }

    @Test void flatFmtExt_Sorted() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("z", DynValue.ofString("last")),
                Map.entry("a", DynValue.ofString("first")),
                Map.entry("m", DynValue.ofString("middle"))
        ));
        var result = FlatFormatter.format(obj, null);
        int posA = result.indexOf("a=");
        int posM = result.indexOf("m=");
        int posZ = result.indexOf("z=");
        assertTrue(posA < posM && posM < posZ);
    }

    @Test void flatFmtExt_IntegerValues() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("count", DynValue.ofInteger(42)),
                Map.entry("neg", DynValue.ofInteger(-5))
        ));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("count=42"));
        assertTrue(result.contains("neg=-5"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  ODIN Formatter Extended
    // ═════════════════════════════════════════════════════════════════

    @Test void odinFmtExt_ArrayValue() {
        var inner = DynValue.ofObject(List.of(
                Map.entry("Items", DynValue.ofArray(List.of(DynValue.ofString("a"), DynValue.ofString("b"))))
        ));
        var result = OdinFormatter.format(inner, null);
        assertTrue(result.contains("Items[]"));
        assertTrue(result.contains("\"a\""));
        assertTrue(result.contains("\"b\""));
    }

    @Test void odinFmtExt_NestedSections() {
        var inner = DynValue.ofObject(List.of(
                Map.entry("A", DynValue.ofObject(List.of(Map.entry("x", DynValue.ofInteger(1))))),
                Map.entry("B", DynValue.ofObject(List.of(Map.entry("y", DynValue.ofInteger(2)))))
        ));
        var result = OdinFormatter.format(inner, null);
        assertTrue(result.contains("##1"));
        assertTrue(result.contains("##2"));
    }

    @Test void odinFmtExt_DeprecatedModifier() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("old"))));
        var mods = Map.of("val", new OdinModifiers(false, false, true, false));
        var result = OdinFormatter.formatWithModifiers(obj, null, mods);
        assertTrue(result.contains("-"));
    }

    @Test void odinFmtExt_AllThreeModifiers() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("x"))));
        var mods = Map.of("val", new OdinModifiers(true, true, true, false));
        var result = OdinFormatter.formatWithModifiers(obj, null, mods);
        assertTrue(result.contains("!"));
        assertTrue(result.contains("*"));
        assertTrue(result.contains("-"));
    }

    @Test void odinFmtExt_NoHeaderOption() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("test"))));
        var config = new TargetConfig();
        config.setFormat("odin");
        config.setOptions(Map.of("header", "false"));
        var result = OdinFormatter.format(obj, config);
        assertFalse(result.contains("{$}"));
    }

    @Test void odinFmtExt_NullTilde() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofNull())));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("~"));
    }

    @Test void odinFmtExt_BooleanPrefix() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("t", DynValue.ofBool(true)),
                Map.entry("f", DynValue.ofBool(false))
        ));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("?true"));
        assertTrue(result.contains("?false"));
    }
}
