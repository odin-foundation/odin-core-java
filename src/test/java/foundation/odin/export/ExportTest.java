package foundation.odin.export;

import foundation.odin.export.CsvExport.CsvExportOptions;
import foundation.odin.export.FixedWidthExport.Align;
import foundation.odin.export.FixedWidthExport.Field;
import foundation.odin.export.FixedWidthExport.FixedWidthExportOptions;
import foundation.odin.parsing.OdinParser;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportTest {

    // ═════════════════════════════════════════════════════════════════
    //  JSON Export
    // ═════════════════════════════════════════════════════════════════

    @Nested class JsonExportTests {

        @Test void simpleSection() {
            var doc = OdinParser.parse("{Person}\nname = \"Alice\"\nage = ##30\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Alice"));
            assertTrue(json.contains("30"));
        }

        @Test void nestedSections() {
            var doc = OdinParser.parse("{Company}\nname = \"Acme\"\n{Company.Address}\ncity = \"Portland\"\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Acme"));
            assertTrue(json.contains("Portland"));
        }

        @Test void booleanValues() {
            var doc = OdinParser.parse("{Config}\nenabled = ?true\nverbose = ?false\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("true"));
            assertTrue(json.contains("false"));
        }

        @Test void nullValues() {
            var doc = OdinParser.parse("{Data}\nvalue = ~\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("null"));
        }

        @Test void integerValues() {
            var doc = OdinParser.parse("{Data}\ncount = ##42\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("42"));
        }

        @Test void floatValues() {
            var doc = OdinParser.parse("{Data}\nprice = #3.14\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("3.14"));
        }

        @Test void stringEscaping() {
            var doc = OdinParser.parse("{Data}\nmsg = \"Hello \\\"world\\\"\"\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Hello"));
        }

        @Test void arraySections() {
            var doc = OdinParser.parse("{items[] : name, qty}\n\"Bolt\", ##10\n\"Nut\", ##20\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Bolt"));
            assertTrue(json.contains("Nut"));
        }

        @Test void emptyDocument() {
            var doc = OdinDocument.empty();
            var json = JsonExport.toJson(doc);
            assertNotNull(json);
            assertTrue(json.contains("{"));
        }

        @Test void topLevelScalars() {
            var doc = OdinParser.parse("name = \"Alice\"\nage = ##30\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Alice"), "expected name preserved, got: " + json);
            assertTrue(json.contains("30"), "expected age preserved, got: " + json);
        }

        @Test void topLevelScalarsWithSection() {
            // Top-level scalars must survive even when followed by a section reset.
            var doc = OdinParser.parse("name = \"Alice\"\nage = ##30\n\n{Address}\ncity = \"Portland\"\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("Alice"));
            assertTrue(json.contains("30"));
            assertTrue(json.contains("Portland"));
        }

        @Test void dateValues() {
            var doc = OdinParser.parse("{Data}\ndate = 2024-01-15\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("2024-01-15"));
        }

        @Test void referenceValues() {
            var doc = OdinParser.parse("{Data}\nref = @other.path\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("@other.path"));
        }

        @Test void multipleFieldsInSection() {
            var doc = OdinParser.parse("{Person}\nfirst = \"John\"\nlast = \"Doe\"\nage = ##25\n", ParseOptions.DEFAULT);
            var json = JsonExport.toJson(doc);
            assertTrue(json.contains("John"));
            assertTrue(json.contains("Doe"));
            assertTrue(json.contains("25"));
        }

        @Test void preserveTypesFlag() {
            var doc = OdinParser.parse("{Data}\ncount = ##42\n", ParseOptions.DEFAULT);
            var json1 = JsonExport.toJson(doc, false, false);
            var json2 = JsonExport.toJson(doc, true, false);
            // Both should contain the value
            assertTrue(json1.contains("42"));
            assertTrue(json2.contains("42"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  XML Export
    // ═════════════════════════════════════════════════════════════════

    @Nested class XmlExportTests {

        @Test void simpleSection() {
            var doc = OdinParser.parse("{Person}\nname = \"Alice\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc);
            assertTrue(xml.contains("Alice"));
            assertTrue(xml.contains("<?xml"));
        }

        @Test void containsRootElement() {
            var doc = OdinParser.parse("{Data}\nval = ##1\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, false, false, "myRoot");
            assertTrue(xml.contains("<myRoot>"));
            assertTrue(xml.contains("</myRoot>"));
        }

        @Test void nullValuesSkipped() {
            var doc = OdinParser.parse("{Data}\nval = ~\nname = \"Bob\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc);
            assertTrue(xml.contains("Bob"));
            // Null values should be skipped
            assertFalse(xml.contains("<val"));
        }

        @Test void preserveTypesAddsNamespace() {
            var doc = OdinParser.parse("{Data}\ncount = ##5\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, true, false);
            assertTrue(xml.contains("xmlns:odin"));
            assertTrue(xml.contains("odin:type=\"integer\""));
        }

        @Test void preserveModifiers() {
            // Modifiers on value side: name = !"required"
            var doc = OdinParser.parse("{Data}\nname = !\"required\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, false, true);
            assertTrue(xml.contains("odin:required=\"true\""), "Expected odin:required attribute. Got: " + xml);
        }

        @Test void xmlEscaping() {
            var doc = OdinParser.parse("{Data}\nval = \"A & B < C\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc);
            assertTrue(xml.contains("&amp;"));
            assertTrue(xml.contains("&lt;"));
        }

        @Test void booleanType() {
            var doc = OdinParser.parse("{Data}\nflag = ?true\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, true, false);
            assertTrue(xml.contains("odin:type=\"boolean\""));
            assertTrue(xml.contains("true"));
        }

        @Test void dateType() {
            var doc = OdinParser.parse("{Data}\nborn = 2024-03-15\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, true, false);
            assertTrue(xml.contains("odin:type=\"date\""));
            assertTrue(xml.contains("2024-03-15"));
        }

        @Test void stringNoTypeAttr() {
            var doc = OdinParser.parse("{Data}\nname = \"hello\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc, true, false);
            assertFalse(xml.contains("odin:type=\"string\""));
            assertTrue(xml.contains("hello"));
        }

        @Test void emptyDocument() {
            var doc = OdinDocument.empty();
            var xml = XmlExport.toXml(doc);
            assertTrue(xml.contains("<?xml"));
            assertTrue(xml.contains("<root>"));
            assertTrue(xml.contains("</root>"));
        }

        @Test void multipleFieldsInSection() {
            var doc = OdinParser.parse("{Person}\nfirst = \"Jane\"\nlast = \"Doe\"\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc);
            assertTrue(xml.contains("Jane"));
            assertTrue(xml.contains("Doe"));
            assertTrue(xml.contains("<Person>"));
        }

        @Test void metadataSkipped() {
            var doc = OdinParser.parse("{$}\nodin = \"1.0.0\"\n{Data}\nval = ##1\n", ParseOptions.DEFAULT);
            var xml = XmlExport.toXml(doc);
            assertFalse(xml.contains("<$"));
            assertTrue(xml.contains("<Data>"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  CSV Export
    // ═════════════════════════════════════════════════════════════════

    @Nested class CsvExportTests {

        @Test void arrayRows() {
            var doc = OdinParser.parse("{items[] : name, qty}\n\"Bolt\", ##10\n\"Nut\", ##20\n", ParseOptions.DEFAULT);
            var csv = CsvExport.toCsv(doc);
            assertTrue(csv.contains("name"));
            assertTrue(csv.contains("Bolt"));
            assertTrue(csv.contains("Nut"));
        }

        @Test void singleRowFallback() {
            var doc = OdinParser.parse("{Person}\nname = \"Alice\"\nage = ##30\n", ParseOptions.DEFAULT);
            var csv = CsvExport.toCsv(doc);
            assertNotNull(csv);
            // Should produce single-row output with section.field paths or flatten
        }

        @Test void noHeader() {
            var doc = OdinParser.parse("{items[] : name}\n\"Bolt\"\n\"Nut\"\n", ParseOptions.DEFAULT);
            var opts = new CsvExportOptions().setHeader(false);
            var csv = CsvExport.toCsv(doc, opts);
            assertFalse(csv.contains("name"));
            assertTrue(csv.contains("Bolt"));
        }

        @Test void customDelimiter() {
            var doc = OdinParser.parse("{items[] : name, qty}\n\"Bolt\", ##10\n", ParseOptions.DEFAULT);
            var opts = new CsvExportOptions().setDelimiter(';');
            var csv = CsvExport.toCsv(doc, opts);
            assertTrue(csv.contains(";"));
        }

        @Test void emptyDocument() {
            var doc = OdinDocument.empty();
            var csv = CsvExport.toCsv(doc);
            assertEquals("", csv);
        }

        @Test void quoting() {
            var doc = OdinParser.parse("{items[] : name}\n\"Hello, World\"\n", ParseOptions.DEFAULT);
            var csv = CsvExport.toCsv(doc);
            // Comma in value should trigger quoting
            assertTrue(csv.contains("\"Hello, World\""));
        }

        @Test void booleanValues() {
            var doc = OdinParser.parse("{items[] : active}\n?true\n?false\n", ParseOptions.DEFAULT);
            var csv = CsvExport.toCsv(doc);
            assertTrue(csv.contains("true"));
            assertTrue(csv.contains("false"));
        }

        @Test void nullValues() {
            var doc = OdinParser.parse("{items[] : name, notes}\n\"A\", ~\n", ParseOptions.DEFAULT);
            var csv = CsvExport.toCsv(doc);
            assertTrue(csv.contains("A"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Fixed-Width Export
    // ═════════════════════════════════════════════════════════════════

    @Nested class FixedWidthExportTests {

        @Test void basicLayout() {
            var doc = OdinParser.parse("{Data}\nname = \"Alice\"\nage = ##30\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(20)
                    .setFields(List.of(
                            new Field("Data.name", 0, 10),
                            new Field("Data.age", 10, 10)
                    ));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals(20, result.length());
            assertTrue(result.startsWith("Alice"));
            assertTrue(result.contains("30"));
        }

        @Test void rightAlignment() {
            var doc = OdinParser.parse("{Data}\namt = ##42\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(10)
                    .setFields(List.of(
                            new Field("Data.amt", 0, 10, null, Align.RIGHT)
                    ));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals(10, result.length());
            assertTrue(result.endsWith("42"));
            assertTrue(result.startsWith(" "));
        }

        @Test void truncation() {
            var doc = OdinParser.parse("{Data}\nname = \"VeryLongName\"\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(5)
                    .setFields(List.of(new Field("Data.name", 0, 5)));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals(5, result.length());
            assertEquals("VeryL", result);
        }

        @Test void customPadChar() {
            var doc = OdinParser.parse("{Data}\nname = \"AB\"\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(10)
                    .setPadChar('0')
                    .setFields(List.of(new Field("Data.name", 0, 10)));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals("AB00000000", result);
        }

        @Test void fieldPadCharOverride() {
            var doc = OdinParser.parse("{Data}\nname = \"AB\"\nid = ##1\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(10)
                    .setFields(List.of(
                            new Field("Data.name", 0, 5, '.', Align.LEFT),
                            new Field("Data.id", 5, 5, '0', Align.RIGHT)
                    ));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals(10, result.length());
            assertEquals("AB...00001", result);
        }

        @Test void missingField() {
            var doc = OdinParser.parse("{Data}\nname = \"Alice\"\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(20)
                    .setFields(List.of(
                            new Field("Data.name", 0, 10),
                            new Field("Data.missing", 10, 10)
                    ));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals(20, result.length());
            assertTrue(result.startsWith("Alice"));
        }

        @Test void emptyOptions() {
            var doc = OdinParser.parse("{Data}\nname = \"Alice\"\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions();
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals("", result);
        }

        @Test void zeroLineWidth() {
            var doc = OdinParser.parse("{Data}\nname = \"Alice\"\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions().setLineWidth(0);
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertEquals("", result);
        }

        @Test void booleanFormatting() {
            var doc = OdinParser.parse("{Data}\nflag = ?true\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(10)
                    .setFields(List.of(new Field("Data.flag", 0, 10)));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertTrue(result.startsWith("true"));
        }

        @Test void integerFormatting() {
            var doc = OdinParser.parse("{Data}\ncount = ##999\n", ParseOptions.DEFAULT);
            var opts = new FixedWidthExportOptions()
                    .setLineWidth(10)
                    .setFields(List.of(new Field("Data.count", 0, 10, null, Align.RIGHT)));
            var result = FixedWidthExport.toFixedWidth(doc, opts);
            assertTrue(result.endsWith("999"));
        }
    }
}
