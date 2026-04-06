package foundation.odin.transform;

import foundation.odin.types.OdinTransformTypes.*;
import foundation.odin.types.OdinTransformTypes.FieldExpression.*;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransformParserTest {

    private static final String HEADER = """
            {$}
            odin = "1.0.0"
            transform = "1.0.0"
            """;

    private static OdinTransform parse(String body) {
        return TransformParser.parse(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n\n" + body);
    }

    private static OdinTransform parseRaw(String text) {
        return TransformParser.parse(text);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Header / Metadata Parsing
    // ═════════════════════════════════════════════════════════════════

    @Nested class MetadataTests {

        @Test void parsesOdinVersion() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n");
            assertEquals("1.0.0", t.getMetadata().getOdinVersion());
        }

        @Test void parsesTransformVersion() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n");
            assertEquals("1.0.0", t.getMetadata().getTransformVersion());
        }

        @Test void parsesDirection() {
            var t = parseRaw(HEADER + "direction = \"json->odin\"\ntarget.format = \"odin\"\n");
            assertEquals("json->odin", t.getMetadata().getDirection());
        }

        @Test void parsesTargetFormatJson() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n");
            assertEquals("json", t.getTarget().getFormat());
        }

        @Test void parsesTargetFormatCsv() {
            var t = parseRaw(HEADER + "direction = \"json->csv\"\ntarget.format = \"csv\"\n");
            assertEquals("csv", t.getTarget().getFormat());
        }

        @Test void parsesTargetFormatOdin() {
            var t = parseRaw(HEADER + "direction = \"json->odin\"\ntarget.format = \"odin\"\n");
            assertEquals("odin", t.getTarget().getFormat());
        }

        @Test void parsesDirectionXmlToOdin() {
            var t = parseRaw(HEADER + "direction = \"xml->odin\"\ntarget.format = \"odin\"\n");
            assertEquals("xml->odin", t.getMetadata().getDirection());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Segments
    // ═════════════════════════════════════════════════════════════════

    @Nested class SegmentTests {

        @Test void parsesSingleSegment() {
            var t = parse("{Person}\nName = \"@.name\"\n");
            assertTrue(t.getSegments().size() >= 1);
            assertEquals("Person", t.getSegments().get(0).getName());
        }

        @Test void parsesMultipleSegments() {
            var t = parse("{Person}\nName = \"@.name\"\n\n{Address}\nCity = \"@.city\"\n");
            assertTrue(t.getSegments().size() >= 2);
        }

        @Test void parsesMappingCopyExpression() {
            var t = parse("{Data}\nName = \"@.name\"\n");
            assertTrue(t.getSegments().size() >= 1);
            var mappings = t.getSegments().get(0).getMappings();
            assertTrue(mappings.size() >= 1);
            assertEquals("Name", mappings.get(0).getTarget());
            assertInstanceOf(CopyExpression.class, mappings.get(0).getExpression());
        }

        @Test void parsesMappingVerbExpression() {
            var t = parse("{Data}\nName = \"%upper @.name\"\n");
            assertTrue(t.getSegments().size() >= 1);
            var mappings = t.getSegments().get(0).getMappings();
            assertTrue(mappings.size() >= 1);
            assertInstanceOf(TransformExpression.class, mappings.get(0).getExpression());
            var expr = (TransformExpression) mappings.get(0).getExpression();
            assertEquals("upper", expr.getCall().getVerb());
        }

        @Test void parsesMappingLiteralExpression() {
            var t = parse("{Data}\nVersion = \"1.0\"\n");
            assertTrue(t.getSegments().size() >= 1);
            var mappings = t.getSegments().get(0).getMappings();
            assertTrue(mappings.size() >= 1);
        }

        @Test void parsesNestedVerbExpression() {
            var t = parse("{Data}\nName = \"%upper %concat @.first \\\" \\\" @.last\"\n");
            assertTrue(t.getSegments().size() >= 1);
            var mappings = t.getSegments().get(0).getMappings();
            assertTrue(mappings.size() >= 1);
            assertInstanceOf(TransformExpression.class, mappings.get(0).getExpression());
            var expr = (TransformExpression) mappings.get(0).getExpression();
            assertEquals("upper", expr.getCall().getVerb());
            assertTrue(expr.getCall().getArgs().size() >= 1);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Constants
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConstantTests {

        @Test void parsesConstants() {
            var t = parse("{const}\nversion = \"2.0\"\nmax = ##100\n");
            assertTrue(t.getConstants().size() >= 2);
            assertTrue(t.getConstants().containsKey("version"));
            assertTrue(t.getConstants().containsKey("max"));
        }

        @Test void parsesConstantStringValue() {
            var t = parse("{const}\nprefix = \"Hello\"\n");
            assertTrue(t.getConstants().containsKey("prefix"));
            var val = t.getConstants().get("prefix");
            assertInstanceOf(OdinValue.OdinString.class, val);
            assertEquals("Hello", ((OdinValue.OdinString) val).getValue());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Modifiers
    // ═════════════════════════════════════════════════════════════════

    @Nested class ModifierTests {

        @Test void parsesRequiredModifier() {
            var t = parse("{Data}\nName = \"@.name :required\"\n");
            var mappings = t.getSegments().get(0).getMappings();
            assertNotNull(mappings.get(0).getModifiers());
            assertTrue(mappings.get(0).getModifiers().isRequired());
        }

        @Test void parsesConfidentialModifier() {
            var t = parse("{Data}\nSSN = \"@.ssn :confidential\"\n");
            var mappings = t.getSegments().get(0).getMappings();
            assertNotNull(mappings.get(0).getModifiers());
            assertTrue(mappings.get(0).getModifiers().isConfidential());
        }

        @Test void parsesDeprecatedModifier() {
            var t = parse("{Data}\nOld = \"@.old :deprecated\"\n");
            var mappings = t.getSegments().get(0).getMappings();
            assertNotNull(mappings.get(0).getModifiers());
            assertTrue(mappings.get(0).getModifiers().isDeprecated());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Confidential Enforcement
    // ═════════════════════════════════════════════════════════════════

    @Nested class ConfidentialEnforcementTests {

        @Test void parsesEnforceConfidentialRedact() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\nenforceConfidential = \"redact\"\n");
            assertEquals(ConfidentialMode.REDACT, t.getEnforceConfidential());
        }

        @Test void parsesEnforceConfidentialMask() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\nenforceConfidential = \"mask\"\n");
            assertEquals(ConfidentialMode.MASK, t.getEnforceConfidential());
        }

        @Test void noEnforceConfidentialByDefault() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n");
            assertNull(t.getEnforceConfidential());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Source Config
    // ═════════════════════════════════════════════════════════════════

    @Nested class SourceConfigTests {

        @Test void parsesSourceFormat() {
            var t = parseRaw(HEADER + "direction = \"csv->json\"\nsource.format = \"csv\"\ntarget.format = \"json\"\n");
            assertNotNull(t.getSource());
            assertEquals("csv", t.getSource().getFormat());
        }

        @Test void parsesSourceOptions() {
            var t = parseRaw(HEADER + "direction = \"csv->json\"\nsource.format = \"csv\"\nsource.delimiter = \"|\"\ntarget.format = \"json\"\n");
            assertNotNull(t.getSource());
            assertTrue(t.getSource().getOptions().containsKey("delimiter"));
            assertEquals("|", t.getSource().getOptions().get("delimiter"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Verb Arguments
    // ═════════════════════════════════════════════════════════════════

    @Nested class VerbArgTests {

        @Test void parsesVerbWithMultipleArgs() {
            var t = parse("{Data}\nFull = \"%concat @.first \\\" \\\" @.last\"\n");
            var expr = (TransformExpression) t.getSegments().get(0).getMappings().get(0).getExpression();
            assertEquals("concat", expr.getCall().getVerb());
            assertTrue(expr.getCall().getArgs().size() >= 3);
        }

        @Test void parsesVerbWithLiteralArg() {
            var t = parse("{Data}\nX = \"%concat @.name \\\"!\\\"\"\n");
            var expr = (TransformExpression) t.getSegments().get(0).getMappings().get(0).getExpression();
            assertEquals("concat", expr.getCall().getVerb());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Edge Cases
    // ═════════════════════════════════════════════════════════════════

    @Nested class EdgeCaseTests {

        @Test void parsesEmptyTransform() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n");
            assertNotNull(t);
            assertTrue(t.getSegments().isEmpty());
        }

        @Test void parsesTransformWithComments() {
            var t = parse("; Segment comment\n{Data}\nName = \"@.name\"  ; mapping comment\n");
            assertTrue(t.getSegments().size() >= 1);
            assertTrue(t.getSegments().get(0).getMappings().size() >= 1);
        }

        @Test void parsesMappingTarget() {
            var t = parse("{Data}\nOutputField = \"@.input_field\"\n");
            assertEquals("OutputField", t.getSegments().get(0).getMappings().get(0).getTarget());
        }

        @Test void parsesCopyExpressionPath() {
            var t = parse("{Data}\nName = \"@.person.name\"\n");
            var expr = t.getSegments().get(0).getMappings().get(0).getExpression();
            assertInstanceOf(CopyExpression.class, expr);
            assertEquals(".person.name", ((CopyExpression) expr).getPath());
        }

        @Test void parsesArrayIndexInCopyPath() {
            var t = parse("{Data}\nFirst = \"@.items[0].name\"\n");
            var expr = t.getSegments().get(0).getMappings().get(0).getExpression();
            assertInstanceOf(CopyExpression.class, expr);
            assertEquals(".items[0].name", ((CopyExpression) expr).getPath());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Lookup Tables
    // ═════════════════════════════════════════════════════════════════

    @Nested class LookupTableTests {

        @Test void parsesLookupTable() {
            var t = parseRaw(HEADER + "direction = \"json->json\"\ntarget.format = \"json\"\n\n" +
                    "{table.states[] : code, name}\nCA, California\nNY, New York\nTX, Texas\n");
            assertTrue(t.getTables().size() >= 1);
            assertTrue(t.getTables().containsKey("states"));
            var table = t.getTables().get("states");
            assertTrue(table.getColumns().size() >= 2);
            assertTrue(table.getRows().size() >= 3);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Multiple Mappings
    // ═════════════════════════════════════════════════════════════════

    @Nested class MultipleMappingTests {

        @Test void parsesMultipleMappingsInSegment() {
            var t = parse("{Person}\nFirst = \"@.first\"\nLast = \"@.last\"\nAge = \"@.age\"\n");
            assertTrue(t.getSegments().get(0).getMappings().size() >= 3);
            assertEquals("First", t.getSegments().get(0).getMappings().get(0).getTarget());
            assertEquals("Last", t.getSegments().get(0).getMappings().get(1).getTarget());
            assertEquals("Age", t.getSegments().get(0).getMappings().get(2).getTarget());
        }

        @Test void parsesMixedExpressionTypes() {
            var t = parse("{Data}\nName = \"@.name\"\nUpper = \"%upper @.name\"\nVersion = \"1.0\"\n");
            var mappings = t.getSegments().get(0).getMappings();
            assertTrue(mappings.size() >= 3);
            assertInstanceOf(CopyExpression.class, mappings.get(0).getExpression());
            assertInstanceOf(TransformExpression.class, mappings.get(1).getExpression());
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Target Options
    // ═════════════════════════════════════════════════════════════════

    @Nested class TargetOptionTests {

        @Test void parsesTargetOptions() {
            var t = parseRaw(HEADER + "direction = \"json->csv\"\ntarget.format = \"csv\"\ntarget.delimiter = \"|\"\ntarget.includeHeader = \"true\"\n");
            assertEquals("csv", t.getTarget().getFormat());
            assertTrue(t.getTarget().getOptions().containsKey("delimiter"));
            assertEquals("|", t.getTarget().getOptions().get("delimiter"));
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Discriminator Parsing
    // ═════════════════════════════════════════════════════════════════

    @Nested class DiscriminatorTests {

        @Test void parsesSourceDiscriminatorPosition() {
            var t = parseRaw(HEADER + "direction = \"fixed-width->json\"\nsource.format = \"fixed-width\"\n" +
                    "source.discriminator.type = \"position\"\nsource.discriminator.pos = \"0\"\nsource.discriminator.len = \"2\"\n" +
                    "target.format = \"json\"\n");
            assertNotNull(t.getSource());
            assertNotNull(t.getSource().getDiscriminator());
            assertEquals(DiscriminatorType.POSITION, t.getSource().getDiscriminator().getType());
            assertEquals(0, t.getSource().getDiscriminator().getPos());
            assertEquals(2, t.getSource().getDiscriminator().getLen());
        }

        @Test void parsesSourceDiscriminatorField() {
            var t = parseRaw(HEADER + "direction = \"csv->json\"\nsource.format = \"csv\"\n" +
                    "source.discriminator.type = \"field\"\nsource.discriminator.field = \"0\"\n" +
                    "target.format = \"json\"\n");
            assertNotNull(t.getSource());
            assertNotNull(t.getSource().getDiscriminator());
            assertEquals(DiscriminatorType.FIELD, t.getSource().getDiscriminator().getType());
            assertEquals(0, t.getSource().getDiscriminator().getField());
        }
    }
}
