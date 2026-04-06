package foundation.odin.transform;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for source parsers (JSON, CSV, XML, YAML, Fixed-width, Flat)
 * and output formatters (JSON, CSV, XML, Fixed-width, Flat, ODIN).
 * Ported from .NET SourceParserFormatterTests.cs
 */
class SourceParserFormatterTest {

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
    //  JSON Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void jsonSource_SimpleObject() {
        var result = JsonSourceParser.parse("{\"name\": \"Alice\", \"age\": 30}");
        assertEquals(DynValue.Type.Object, result.getType());
        assertEquals("Alice", getStr(result, "name"));
    }

    @Test void jsonSource_NestedObject() {
        var result = JsonSourceParser.parse("{\"person\": {\"name\": \"Bob\"}}");
        var person = getField(result, "person");
        assertNotNull(person);
        assertEquals("Bob", getStr(person, "name"));
    }

    @Test void jsonSource_Array() {
        var result = JsonSourceParser.parse("[1, 2, 3]");
        assertEquals(DynValue.Type.Array, result.getType());
        assertEquals(3, result.asArray().size());
    }

    @Test void jsonSource_NestedArray() {
        var result = JsonSourceParser.parse("{\"items\": [{\"id\": 1}, {\"id\": 2}]}");
        var items = getField(result, "items");
        assertNotNull(items);
        assertEquals(2, items.asArray().size());
    }

    @Test void jsonSource_Boolean() {
        var result = JsonSourceParser.parse("{\"active\": true, \"deleted\": false}");
        assertEquals(true, getField(result, "active").asBool());
        assertEquals(false, getField(result, "deleted").asBool());
    }

    @Test void jsonSource_Null() {
        var result = JsonSourceParser.parse("{\"val\": null}");
        assertTrue(getField(result, "val").isNull());
    }

    @Test void jsonSource_Integer() {
        var result = JsonSourceParser.parse("{\"n\": 42}");
        assertEquals(42L, getField(result, "n").asInt64());
    }

    @Test void jsonSource_Float() {
        var result = JsonSourceParser.parse("{\"n\": 3.14}");
        assertEquals(3.14, getField(result, "n").asDouble());
    }

    @Test void jsonSource_EscapedString() {
        var result = JsonSourceParser.parse("{\"text\": \"hello\\nworld\"}");
        assertTrue(getStr(result, "text").contains("\n"));
    }

    @Test void jsonSource_UnicodeEscape() {
        var result = JsonSourceParser.parse("{\"text\": \"caf\\u00e9\"}");
        assertEquals("caf\u00e9", getStr(result, "text"));
    }

    @Test void jsonSource_EmptyObject() {
        var result = JsonSourceParser.parse("{}");
        assertEquals(DynValue.Type.Object, result.getType());
        assertTrue(result.asObject().isEmpty());
    }

    @Test void jsonSource_EmptyArray() {
        var result = JsonSourceParser.parse("[]");
        assertEquals(DynValue.Type.Array, result.getType());
        assertTrue(result.asArray().isEmpty());
    }

    @Test void jsonSource_NullInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> JsonSourceParser.parse(null));
    }

    @Test void jsonSource_EmptyString_Throws() {
        assertThrows(IllegalArgumentException.class, () -> JsonSourceParser.parse(""));
    }

    @Test void jsonSource_MalformedJson_Throws() {
        assertThrows(FormatException.class, () -> JsonSourceParser.parse("{invalid}"));
    }

    @Test void jsonSource_DeeplyNested() {
        var result = JsonSourceParser.parse("{\"a\": {\"b\": {\"c\": {\"d\": \"deep\"}}}}");
        var a = getField(result, "a");
        var b = getField(a, "b");
        var c = getField(b, "c");
        assertEquals("deep", getStr(c, "d"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  CSV Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void csvSource_WithHeaders() {
        var result = CsvSourceParser.parse("Name,Age\nAlice,30\nBob,25", null);
        var arr = result.asArray();
        assertNotNull(arr);
        assertEquals(2, arr.size());
        assertEquals("Alice", getStr(arr.get(0), "Name"));
        assertEquals("Bob", getStr(arr.get(1), "Name"));
    }

    @Test void csvSource_CustomDelimiter() {
        var config = new SourceConfig();
        config.setFormat("csv");
        config.setOptions(Map.of("delimiter", "|"));
        var result = CsvSourceParser.parse("Name|Age\nAlice|30", config);
        var arr = result.asArray();
        assertNotNull(arr);
        assertTrue(arr.size() >= 1);
        assertEquals("Alice", getStr(arr.get(0), "Name"));
    }

    @Test void csvSource_NoHeader() {
        var config = new SourceConfig();
        config.setFormat("csv");
        config.setOptions(Map.of("hasHeader", "false"));
        var result = CsvSourceParser.parse("Alice,30\nBob,25", config);
        var arr = result.asArray();
        assertNotNull(arr);
        assertEquals(2, arr.size());
        assertNotNull(arr.get(0).asArray());
    }

    @Test void csvSource_QuotedField() {
        var result = CsvSourceParser.parse("Name,City\n\"Smith, John\",Portland", null);
        var arr = result.asArray();
        assertEquals("Smith, John", getStr(arr.get(0), "Name"));
    }

    @Test void csvSource_EscapedQuote() {
        var result = CsvSourceParser.parse("Name,Note\nAlice,\"She said \"\"hi\"\"\"", null);
        var arr = result.asArray();
        assertTrue(getStr(arr.get(0), "Note").contains("\"hi\""));
    }

    @Test void csvSource_EmptyFields() {
        var result = CsvSourceParser.parse("A,B,C\n1,,3", null);
        var arr = result.asArray();
        var b = getField(arr.get(0), "B");
        assertNotNull(b);
        assertEquals("", b.asString());
    }

    @Test void csvSource_BooleanInference() {
        var result = CsvSourceParser.parse("Val\ntrue\nfalse", null);
        var arr = result.asArray();
        assertEquals(true, getField(arr.get(0), "Val").asBool());
        assertEquals(false, getField(arr.get(1), "Val").asBool());
    }

    @Test void csvSource_IntegerInference() {
        var result = CsvSourceParser.parse("Val\n42", null);
        var arr = result.asArray();
        assertEquals(42L, getField(arr.get(0), "Val").asInt64());
    }

    @Test void csvSource_FloatInference() {
        var result = CsvSourceParser.parse("Val\n3.14", null);
        var arr = result.asArray();
        assertEquals(3.14, getField(arr.get(0), "Val").asDouble());
    }

    @Test void csvSource_NullInference() {
        var result = CsvSourceParser.parse("Val\nnull", null);
        var arr = result.asArray();
        assertTrue(getField(arr.get(0), "Val").isNull());
    }

    @Test void csvSource_EmptyInput_ReturnsEmptyArray() {
        var result = CsvSourceParser.parse("", null);
        var arr = result.asArray();
        assertNotNull(arr);
        assertTrue(arr.isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════
    //  XML Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void xmlSource_SimpleElement() {
        var result = XmlSourceParser.parse("<root><name>Alice</name></root>");
        var root = getField(result, "root");
        assertNotNull(root);
        assertEquals("Alice", getStr(root, "name"));
    }

    @Test void xmlSource_Attributes() {
        var result = XmlSourceParser.parse("<root><person id=\"1\">Alice</person></root>");
        var root = getField(result, "root");
        var person = getField(root, "person");
        assertNotNull(person);
        assertEquals("1", getStr(person, "@id"));
    }

    @Test void xmlSource_NestedElements() {
        var result = XmlSourceParser.parse("<root><address><city>Portland</city><state>OR</state></address></root>");
        var root = getField(result, "root");
        var address = getField(root, "address");
        assertEquals("Portland", getStr(address, "city"));
        assertEquals("OR", getStr(address, "state"));
    }

    @Test void xmlSource_RepeatedElements_BecomeArray() {
        var result = XmlSourceParser.parse("<root><item>a</item><item>b</item><item>c</item></root>");
        var root = getField(result, "root");
        var items = getField(root, "item");
        assertNotNull(items);
        assertEquals(DynValue.Type.Array, items.getType());
        assertEquals(3, items.asArray().size());
    }

    @Test void xmlSource_SelfClosing_IsNull() {
        var result = XmlSourceParser.parse("<root><empty/></root>");
        var root = getField(result, "root");
        var empty = getField(root, "empty");
        assertNotNull(empty);
        assertTrue(empty.isNull());
    }

    @Test void xmlSource_EmptyElement_IsEmptyString() {
        var result = XmlSourceParser.parse("<root><empty></empty></root>");
        var root = getField(result, "root");
        assertEquals("", getStr(root, "empty"));
    }

    @Test void xmlSource_NilAttribute() {
        var result = XmlSourceParser.parse("<root><val nil=\"true\"/></root>");
        var root = getField(result, "root");
        var val = getField(root, "val");
        assertNotNull(val);
        assertTrue(val.isNull());
    }

    @Test void xmlSource_MixedContent() {
        var result = XmlSourceParser.parse("<root><p>Hello <b>world</b></p></root>");
        var root = getField(result, "root");
        var p = getField(root, "p");
        assertNotNull(p);
    }

    @Test void xmlSource_NullInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> XmlSourceParser.parse(null));
    }

    @Test void xmlSource_EmptyInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> XmlSourceParser.parse(""));
    }

    @Test void xmlSource_InvalidXml_Throws() {
        assertThrows(FormatException.class, () -> XmlSourceParser.parse("<unclosed>"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fixed-Width Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void fixedWidthSource_SingleRecord() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:0:10;Amount:10:5"));
        var result = FixedWidthSourceParser.parse("Alice     00100", config);
        assertEquals(DynValue.Type.Object, result.getType());
        assertEquals("Alice", getStr(result, "Name"));
        assertEquals("00100", getStr(result, "Amount"));
    }

    @Test void fixedWidthSource_MultipleRecords() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:0:5;Val:5:3"));
        var result = FixedWidthSourceParser.parse("Alice100\nBob  200", config);
        assertEquals(DynValue.Type.Array, result.getType());
        var arr = result.asArray();
        assertEquals(2, arr.size());
    }

    @Test void fixedWidthSource_Trimming() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:0:10"));
        var result = FixedWidthSourceParser.parse("Alice     ", config);
        assertEquals("Alice", getStr(result, "Name"));
    }

    @Test void fixedWidthSource_NoColumns_Throws() {
        assertThrows(IllegalArgumentException.class, () ->
                FixedWidthSourceParser.parse("test", new SourceConfig()));
    }

    @Test void fixedWidthSource_EmptyInput_ReturnsEmptyArray() {
        var config = new SourceConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:0:5"));
        var result = FixedWidthSourceParser.parse("", config);
        assertEquals(DynValue.Type.Array, result.getType());
        assertTrue(result.asArray().isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════
    //  YAML Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void yamlSource_SimpleMapping() {
        var result = YamlSourceParser.parse("name: Alice\nage: 30");
        assertEquals("Alice", getStr(result, "name"));
        assertEquals(30L, getField(result, "age").asInt64());
    }

    @Test void yamlSource_NestedMapping() {
        var result = YamlSourceParser.parse("person:\n  name: Alice\n  age: 30");
        var person = getField(result, "person");
        assertNotNull(person);
        assertEquals("Alice", getStr(person, "name"));
    }

    @Test void yamlSource_Array() {
        var result = YamlSourceParser.parse("items:\n  - a\n  - b\n  - c");
        var items = getField(result, "items");
        assertNotNull(items);
        assertEquals(3, items.asArray().size());
    }

    @Test void yamlSource_BooleanTrue() {
        var result = YamlSourceParser.parse("active: true");
        assertEquals(true, getField(result, "active").asBool());
    }

    @Test void yamlSource_BooleanFalse() {
        var result = YamlSourceParser.parse("active: false");
        assertEquals(false, getField(result, "active").asBool());
    }

    @Test void yamlSource_BooleanYesNo() {
        var result = YamlSourceParser.parse("a: yes\nb: no");
        assertEquals(true, getField(result, "a").asBool());
        assertEquals(false, getField(result, "b").asBool());
    }

    @Test void yamlSource_NullValue() {
        var result = YamlSourceParser.parse("val: null");
        assertTrue(getField(result, "val").isNull());
    }

    @Test void yamlSource_TildeNull() {
        var result = YamlSourceParser.parse("val: ~");
        assertTrue(getField(result, "val").isNull());
    }

    @Test void yamlSource_QuotedString() {
        var result = YamlSourceParser.parse("val: \"hello world\"");
        assertEquals("hello world", getStr(result, "val"));
    }

    @Test void yamlSource_IntegerValue() {
        var result = YamlSourceParser.parse("val: 42");
        assertEquals(42L, getField(result, "val").asInt64());
    }

    @Test void yamlSource_FloatValue() {
        var result = YamlSourceParser.parse("val: 3.14");
        assertEquals(3.14, getField(result, "val").asDouble());
    }

    @Test void yamlSource_EmptyInput() {
        var result = YamlSourceParser.parse("");
        assertEquals(DynValue.Type.Object, result.getType());
    }

    @Test void yamlSource_Comments() {
        var result = YamlSourceParser.parse("# comment\nname: Alice\n# another comment");
        assertEquals("Alice", getStr(result, "name"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Flat Source Parser
    // ═════════════════════════════════════════════════════════════════

    @Test void flatSource_SimpleKvp() {
        var result = FlatSourceParser.parse("name=Alice\nage=30");
        assertEquals("Alice", getStr(result, "name"));
        assertEquals(30L, getField(result, "age").asInt64());
    }

    @Test void flatSource_DottedPaths() {
        var result = FlatSourceParser.parse("person.name=Alice\nperson.age=30");
        var person = getField(result, "person");
        assertNotNull(person);
        assertEquals("Alice", getStr(person, "name"));
    }

    @Test void flatSource_ArrayBrackets() {
        var result = FlatSourceParser.parse("items[0]=a\nitems[1]=b");
        var items = getField(result, "items");
        assertNotNull(items);
        assertEquals(2, items.asArray().size());
    }

    @Test void flatSource_Comments() {
        var result = FlatSourceParser.parse("# comment\nname=Alice\n; another comment");
        assertEquals("Alice", getStr(result, "name"));
    }

    @Test void flatSource_NullValue() {
        var result = FlatSourceParser.parse("val=~");
        assertTrue(getField(result, "val").isNull());
    }

    @Test void flatSource_QuotedValue() {
        var result = FlatSourceParser.parse("val=\"hello world\"");
        assertEquals("hello world", getStr(result, "val"));
    }

    @Test void flatSource_BooleanInference() {
        var result = FlatSourceParser.parse("a=true\nb=false");
        assertEquals(true, getField(result, "a").asBool());
        assertEquals(false, getField(result, "b").asBool());
    }

    @Test void flatSource_EmptyInput() {
        var result = FlatSourceParser.parse("");
        assertEquals(DynValue.Type.Object, result.getType());
    }

    // ═════════════════════════════════════════════════════════════════
    //  JSON Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void jsonFormatter_SimpleObject() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("name", DynValue.ofString("Alice")),
                Map.entry("age", DynValue.ofInteger(30))
        ));
        var json = JsonFormatter.format(obj, null);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("Alice"));
        assertTrue(json.contains("30"));
    }

    @Test void jsonFormatter_PrettyPrint() {
        var obj = DynValue.ofObject(List.of(Map.entry("a", DynValue.ofInteger(1))));
        var json = JsonFormatter.format(obj, null);
        assertTrue(json.contains("\n")); // Pretty by default
    }

    @Test void jsonFormatter_Compact() {
        var obj = DynValue.ofObject(List.of(Map.entry("a", DynValue.ofInteger(1))));
        var config = new TargetConfig();
        config.setFormat("json");
        config.setOptions(Map.of("indent", "false"));
        var json = JsonFormatter.format(obj, config);
        assertFalse(json.contains("\n"));
    }

    @Test void jsonFormatter_NullValue() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofNull())));
        var json = JsonFormatter.format(obj, null);
        assertTrue(json.contains("null"));
    }

    @Test void jsonFormatter_BooleanValues() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("a", DynValue.ofBool(true)),
                Map.entry("b", DynValue.ofBool(false))
        ));
        var json = JsonFormatter.format(obj, null);
        assertTrue(json.contains("true"));
        assertTrue(json.contains("false"));
    }

    @Test void jsonFormatter_FloatValue() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofFloat(3.14))));
        var json = JsonFormatter.format(obj, null);
        assertTrue(json.contains("3.14"));
    }

    @Test void jsonFormatter_ArrayValue() {
        var arr = DynValue.ofArray(List.of(
                DynValue.ofInteger(1), DynValue.ofInteger(2), DynValue.ofInteger(3)
        ));
        var json = JsonFormatter.format(arr, null);
        assertTrue(json.contains("["));
        assertTrue(json.contains("1"));
        assertTrue(json.contains("3"));
    }

    @Test void jsonFormatter_NestedObject() {
        var inner = DynValue.ofObject(List.of(Map.entry("b", DynValue.ofString("val"))));
        var outer = DynValue.ofObject(List.of(Map.entry("a", inner)));
        var json = JsonFormatter.format(outer, null);
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
        assertTrue(json.contains("val"));
    }

    @Test void jsonFormatter_NullDynValue() {
        var json = JsonFormatter.format(null, null);
        assertEquals("null", json);
    }

    // ═════════════════════════════════════════════════════════════════
    //  CSV Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void csvFormatter_BasicOutput() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(
                        Map.entry("Name", DynValue.ofString("Alice")),
                        Map.entry("Age", DynValue.ofInteger(30))
                ))
        ));
        var csv = CsvFormatter.format(rows, null);
        assertTrue(csv.contains("Name"));
        assertTrue(csv.contains("Alice"));
    }

    @Test void csvFormatter_WithHeader() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(
                        Map.entry("A", DynValue.ofString("1")),
                        Map.entry("B", DynValue.ofString("2"))
                ))
        ));
        var csv = CsvFormatter.format(rows, null);
        var lines = csv.split("\n");
        assertTrue(lines[0].contains("A"));
        assertTrue(lines[0].contains("B"));
    }

    @Test void csvFormatter_NoHeader() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("A", DynValue.ofString("val"))))
        ));
        var config = new TargetConfig();
        config.setOptions(Map.of("includeHeader", "false"));
        var csv = CsvFormatter.format(rows, config);
        var lines = csv.trim().split("\n");
        assertEquals(1, lines.length, "Expected exactly one line");
    }

    @Test void csvFormatter_Quoting() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("Val", DynValue.ofString("hello,world"))))
        ));
        var csv = CsvFormatter.format(rows, null);
        assertTrue(csv.contains("\"hello,world\""));
    }

    @Test void csvFormatter_NullHandling() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("Val", DynValue.ofNull())))
        ));
        var csv = CsvFormatter.format(rows, null);
        assertNotNull(csv);
    }

    @Test void csvFormatter_CustomDelimiter() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(
                        Map.entry("A", DynValue.ofString("1")),
                        Map.entry("B", DynValue.ofString("2"))
                ))
        ));
        var config = new TargetConfig();
        config.setOptions(Map.of("delimiter", "|"));
        var csv = CsvFormatter.format(rows, config);
        assertTrue(csv.contains("|"));
    }

    @Test void csvFormatter_BooleanOutput() {
        var rows = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("Active", DynValue.ofBool(true))))
        ));
        var csv = CsvFormatter.format(rows, null);
        assertTrue(csv.contains("true"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  XML Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void xmlFormatter_SimpleObject() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("Name", DynValue.ofString("Alice"))
                )))
        ));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.contains("<Root"));
        assertTrue(xml.contains("<Name>Alice</Name>"));
    }

    @Test void xmlFormatter_NullElement() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("Val", DynValue.ofNull())
                )))
        ));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.contains("<Val odin:type=\"null\"></Val>"));
    }

    @Test void xmlFormatter_NestedElements() {
        var inner = DynValue.ofObject(List.of(Map.entry("City", DynValue.ofString("Portland"))));
        var obj = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("Address", inner)
                )))
        ));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.contains("<Address>"));
        assertTrue(xml.contains("<City>Portland</City>"));
    }

    @Test void xmlFormatter_Escaping() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("Val", DynValue.ofString("A & B < C"))
                )))
        ));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&lt;"));
    }

    @Test void xmlFormatter_XmlDeclaration() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Root", DynValue.ofObject(List.of(
                        Map.entry("Val", DynValue.ofString("test"))
                )))
        ));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.startsWith("<?xml"));
    }

    @Test void xmlFormatter_ArraySection() {
        var items = DynValue.ofArray(List.of(
                DynValue.ofObject(List.of(Map.entry("Name", DynValue.ofString("Alice")))),
                DynValue.ofObject(List.of(Map.entry("Name", DynValue.ofString("Bob"))))
        ));
        var obj = DynValue.ofObject(List.of(Map.entry("Person", items)));
        var xml = XmlFormatter.format(obj, null);
        assertTrue(xml.contains("Alice"));
        assertTrue(xml.contains("Bob"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fixed-Width Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void fixedWidthFormatter_BasicOutput() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Name", DynValue.ofString("Alice")),
                Map.entry("Age", DynValue.ofInteger(30))
        ));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:10;Age:5"));
        var result = FixedWidthFormatter.format(obj, config);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Alice"));
    }

    @Test void fixedWidthFormatter_Truncation() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Name", DynValue.ofString("VeryLongNameThatShouldBeTruncated"))
        ));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Name:5"));
        var result = FixedWidthFormatter.format(obj, config);
        assertTrue(result.trim().length() <= 5);
    }

    @Test void fixedWidthFormatter_RightAlignment() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("Val", DynValue.ofInteger(42))
        ));
        var config = new TargetConfig();
        config.setFormat("fixed-width");
        config.setOptions(Map.of("columns", "Val:10:right"));
        var result = FixedWidthFormatter.format(obj, config);
        assertFalse(result.isEmpty());
        var line = result.trim();
        assertTrue(line.endsWith("42"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  Flat Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void flatFormatter_SimpleKvp() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("name", DynValue.ofString("Alice")),
                Map.entry("age", DynValue.ofInteger(30))
        ));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("name=Alice"));
        assertTrue(result.contains("age=30"));
    }

    @Test void flatFormatter_Sorted() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("z", DynValue.ofString("last")),
                Map.entry("a", DynValue.ofString("first"))
        ));
        var result = FlatFormatter.format(obj, null);
        int posA = result.indexOf("a=");
        int posZ = result.indexOf("z=");
        assertTrue(posA < posZ);
    }

    @Test void flatFormatter_Nested() {
        var inner = DynValue.ofObject(List.of(Map.entry("city", DynValue.ofString("Portland"))));
        var obj = DynValue.ofObject(List.of(Map.entry("address", inner)));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("address.city=Portland"));
    }

    @Test void flatFormatter_NullSkipped() {
        var obj = DynValue.ofObject(List.of(
                Map.entry("name", DynValue.ofString("Alice")),
                Map.entry("val", DynValue.ofNull())
        ));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("name=Alice"));
        assertFalse(result.contains("val="));
    }

    @Test void flatFormatter_BooleanValues() {
        var obj = DynValue.ofObject(List.of(Map.entry("active", DynValue.ofBool(true))));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("active=true"));
    }

    @Test void flatFormatter_ArrayValues() {
        var arr = DynValue.ofArray(List.of(DynValue.ofString("a"), DynValue.ofString("b")));
        var obj = DynValue.ofObject(List.of(Map.entry("items", arr)));
        var result = FlatFormatter.format(obj, null);
        assertTrue(result.contains("items[0]=a"));
        assertTrue(result.contains("items[1]=b"));
    }

    // ═════════════════════════════════════════════════════════════════
    //  ODIN Formatter
    // ═════════════════════════════════════════════════════════════════

    @Test void odinFormatter_StringValue() {
        var obj = DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("Alice"))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("\"Alice\""));
    }

    @Test void odinFormatter_IntegerPrefix() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofInteger(42))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("##42"));
    }

    @Test void odinFormatter_FloatPrefix() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofFloat(3.14))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("#3.14"));
    }

    @Test void odinFormatter_BooleanPrefix() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofBool(true))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("?true"));
    }

    @Test void odinFormatter_NullTilde() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofNull())));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("~"));
    }

    @Test void odinFormatter_SectionHeaders() {
        var inner = DynValue.ofObject(List.of(
                Map.entry("Name", DynValue.ofString("Alice")),
                Map.entry("Items", DynValue.ofArray(List.of(DynValue.ofString("a"))))
        ));
        var obj = DynValue.ofObject(List.of(Map.entry("Customer", inner)));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("{$}"));
    }

    @Test void odinFormatter_IncludesHeader() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("test"))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("{$}"));
        assertTrue(result.contains("odin = \"1.0.0\""));
    }

    @Test void odinFormatter_NoHeader() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("test"))));
        var config = new TargetConfig();
        config.setFormat("odin");
        config.setOptions(Map.of("header", "false"));
        var result = OdinFormatter.format(obj, config);
        assertFalse(result.contains("{$}"));
    }

    @Test void odinFormatter_StringEscaping() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("hello\nworld"))));
        var result = OdinFormatter.format(obj, null);
        assertTrue(result.contains("\\n"));
    }

    @Test void odinFormatter_ModifierPrefixes() {
        var obj = DynValue.ofObject(List.of(Map.entry("val", DynValue.ofString("secret"))));
        var mods = Map.of("val", new OdinModifiers(true, true, false, false));
        var result = OdinFormatter.formatWithModifiers(obj, null, mods);
        assertTrue(result.contains("!"));
        assertTrue(result.contains("*"));
    }
}
