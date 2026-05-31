package foundation.odin.parsing;

import foundation.odin.types.*;
import foundation.odin.types.OdinOptions.ParseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Directive parsing tests — {$} header, version directives, import directives,
 * format directives, unknown directives, malformed directives, multiple directive sections.
 */
class ParserDirectivesTest {

    private OdinDocument parse(String odin) { return OdinParser.parse(odin, ParseOptions.DEFAULT); }

    // ─── Metadata section ({$}) ───────────────────────────────────────────

    @Nested class MetadataSectionTests {
        @Test void basicMetadata() {
            var doc = parse("{$}\nodin = \"1.0.0\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
        }

        @Test void metadataWithMultipleEntries() {
            var doc = parse("{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ntarget.format = \"json\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("1.0.0", doc.getString("$.transform"));
        }

        @Test void metadataFollowedByAssignment() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void metadataFollowedBySection() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\n{Policy}\nname = \"P1\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("P1", doc.getString("Policy.name"));
        }

        @Test void metadataAccessViaGet() {
            var doc = parse("{$}\nodin = \"1.0.0\"");
            var val = doc.get("$.odin");
            assertNotNull(val);
            assertTrue(val.isString());
        }

        @Test void metadataNotInAssignmentPaths() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertTrue(doc.paths().contains("name"));
        }

        @Test void emptyMetadataSection() {
            var doc = parse("{$}\n\n{}\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("name"));
        }

        @Test void metadataWithComments() {
            var doc = parse("{$}\n; comment in metadata\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
        }

        @Test void metadataDirection() {
            var doc = parse("{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->odin\"");
            assertEquals("json->odin", doc.getString("$.direction"));
        }

        @Test void metadataTargetFormat() {
            var doc = parse("{$}\nodin = \"1.0.0\"\ntarget.format = \"csv\"");
            assertNotNull(doc.getMetadata());
        }
    }

    // ─── Import directives ────────────────────────────────────────────────

    @Nested class ImportDirectiveTests {
        @Test void simpleImport() {
            var doc = parse("@import ./base.odin\nname = \"Alice\"");
            assertEquals(1, doc.getImports().size());
            assertEquals("./base.odin", doc.getImports().get(0).path());
        }

        @Test void importWithAlias() {
            var doc = parse("@import ./base.odin as base\nname = \"Alice\"");
            assertEquals("base", doc.getImports().get(0).alias());
        }

        @Test void multipleImports() {
            var doc = parse("@import ./base.odin\n@import ./utils.odin\nname = \"Alice\"");
            assertEquals(2, doc.getImports().size());
        }

        @Test void importWithRelativePath() {
            var doc = parse("@import ../shared/types.odin\nname = \"Alice\"");
            assertEquals("../shared/types.odin", doc.getImports().get(0).path());
        }

        @Test void importWithAbsoluteUrl() {
            var doc = parse("@import https://example.com/schemas/base.odin\nname = \"Alice\"");
            assertEquals("https://example.com/schemas/base.odin", doc.getImports().get(0).path());
        }

        @Test void importBeforeMetadata() {
            var doc = parse("@import ./base.odin\n{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertFalse(doc.getImports().isEmpty());
            assertEquals("1.0.0", doc.getString("$.odin"));
        }

        @Test void importAfterMetadata() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\n@import ./base.odin\nname = \"Alice\"");
            assertFalse(doc.getImports().isEmpty());
        }

        @Test void importNoAlias() {
            var doc = parse("@import ./base.odin\nname = \"Alice\"");
            assertNull(doc.getImports().get(0).alias());
        }
    }

    // ─── Schema directives ────────────────────────────────────────────────

    @Nested class SchemaDirectiveTests {
        @Test void simpleSchemaDirective() {
            var doc = parse("@schema https://example.com/schema.odin\nname = \"Alice\"");
            assertFalse(doc.getSchemas().isEmpty());
        }

        @Test void schemaWithLocalPath() {
            var doc = parse("@schema ./schemas/person.odin\nname = \"Alice\"");
            assertFalse(doc.getSchemas().isEmpty());
        }

        @Test void multipleSchemaDirectives() {
            var doc = parse("@schema ./base.odin\n@schema ./extensions.odin\nname = \"Alice\"");
            assertTrue(doc.getSchemas().size() >= 2);
        }

        @Test void schemaAndImportTogether() {
            var doc = parse("@import ./base.odin\n@schema ./schema.odin\nname = \"Alice\"");
            assertFalse(doc.getImports().isEmpty());
            assertFalse(doc.getSchemas().isEmpty());
        }
    }

    // ─── Conditional directives ───────────────────────────────────────────

    @Nested class ConditionalDirectiveTests {
        @Test void simpleConditional() {
            var doc = parse("@if env == \"prod\"\nname = \"Alice\"");
            assertFalse(doc.getConditionals().isEmpty());
        }

        @Test void conditionalWithNotEqual() {
            var doc = parse("@if env != \"dev\"\nname = \"Alice\"");
            assertFalse(doc.getConditionals().isEmpty());
        }

        @Test void multipleConditionals() {
            var doc = parse("@if env == \"prod\"\n@if region == \"us\"\nname = \"Alice\"");
            assertTrue(doc.getConditionals().size() >= 2);
        }
    }

    // ─── Version handling ─────────────────────────────────────────────────

    @Nested class VersionTests {
        @Test void version100() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\nname = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
        }

        @Test void versionWithSchema() {
            var doc = parse("{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\nname = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("1.0.0", doc.getString("$.schema"));
        }

        @Test void versionWithTransform() {
            var doc = parse("{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\n\nname = \"Alice\"");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("1.0.0", doc.getString("$.transform"));
        }
    }

    // ─── Directive with data ──────────────────────────────────────────────

    @Nested class DirectiveWithDataTests {
        @Test void directiveThenData() {
            var doc = parse("@import ./base.odin\n\nname = \"Alice\"\nage = ##30");
            assertEquals("Alice", doc.getString("name"));
            assertEquals(30L, doc.getInteger("age"));
        }

        @Test void directiveBeforeSection() {
            var doc = parse("@import ./base.odin\n\n{Person}\nname = \"Alice\"");
            assertEquals("Alice", doc.getString("Person.name"));
        }

        @Test void metadataImportAndData() {
            var doc = parse(String.join("\n",
                "{$}",
                "odin = \"1.0.0\"",
                "",
                "@import ./base.odin",
                "",
                "{Person}",
                "name = \"Alice\"",
                "age = ##30"
            ));
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertFalse(doc.getImports().isEmpty());
            assertEquals("Alice", doc.getString("Person.name"));
            assertEquals(30L, doc.getInteger("Person.age"));
        }

        @Test void directiveDoesNotAffectParsing() {
            var doc = parse("@import ./base.odin\nname = \"Alice\"\ncount = ##5");
            assertEquals("Alice", doc.getString("name"));
            assertEquals(5L, doc.getInteger("count"));
        }
    }

    // ─── Edge cases ───────────────────────────────────────────────────────

    @Nested class DirectiveEdgeCaseTests {
        @Test void emptyDocWithOnlyMetadata() {
            var doc = parse("{$}\nodin = \"1.0.0\"");
            assertNotNull(doc);
            assertEquals("1.0.0", doc.getString("$.odin"));
        }

        @Test void rootSectionResetAfterHeader() {
            var doc = parse(String.join("\n",
                "{Policy}",
                "name = \"Test\"",
                "{}",
                "rootField = \"at root\""
            ));
            assertEquals("Test", doc.getString("Policy.name"));
            assertEquals("at root", doc.getString("rootField"));
        }

        @Test void metadataStringValues() {
            var doc = parse("{$}\nodin = \"1.0.0\"\ntitle = \"My Document\"\ndescription = \"A test\"");
            assertEquals("My Document", doc.getString("$.title"));
            assertEquals("A test", doc.getString("$.description"));
        }

        @Test void metadataWithIntegerValues() {
            var doc = parse("{$}\nodin = \"1.0.0\"\n\ncount = ##42");
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals(42L, doc.getInteger("count"));
        }
    }

    // ─── Inline header directives ({Section :type "x"} / {Section :if "expr"}) ──

    @Nested class InlineHeaderDirectiveTests {
        @Test void headerInlineType() {
            var doc = parse("{Coverage :type \"COLLISION\"}\nlimit = ##500");
            assertEquals("COLLISION", doc.getString("Coverage._type"));
            assertEquals(500L, doc.getInteger("Coverage.limit"));
        }

        @Test void headerInlineIfEmitsCondition() {
            var doc = parse("{DuiDetails :if \"@driver.has_dui = true\"}\nstate = \"TX\"");
            assertEquals("@driver.has_dui = true", doc.getString("DuiDetails._if"));
            assertEquals("TX", doc.getString("DuiDetails.state"));
        }

        @Test void headerInlineIfDoesNotPolluteHeaderPath() {
            var doc = parse("{DuiDetails :if \"@driver.has_dui = true\"}\nstate = \"TX\"");
            assertNull(doc.getString("DuiDetails :if \"@driver.has_dui = true\".state"));
        }

        @Test void tabularColonStillParsesAsColumns() {
            var doc = parse("{rows[] : id, name}\n##1, \"Alice\"\n##2, \"Bob\"");
            assertEquals(1L, doc.getInteger("rows[0].id"));
            assertEquals("Alice", doc.getString("rows[0].name"));
            assertEquals("Bob", doc.getString("rows[1].name"));
        }
    }
}
