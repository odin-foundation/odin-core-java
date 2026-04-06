package foundation.odin.resolver;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportResolverTest {

    // ─── Factory Tests ──────────────────────────────────────────────────────

    @Nested class FactoryTests {
        @Test void createDefault() {
            var resolver = ImportResolver.create();
            assertNotNull(resolver);
        }

        @Test void createWithOptions() {
            var opts = new ImportResolver.ResolverOptions();
            var resolver = ImportResolver.create(opts);
            assertNotNull(resolver);
        }

        @Test void createForSchemas() {
            var opts = ImportResolver.ResolverOptions.forSchemas();
            assertNotNull(opts);
            assertTrue(opts.schemaMode());
        }

        @Test void createForDocuments() {
            var opts = ImportResolver.ResolverOptions.forDocuments();
            assertNotNull(opts);
            assertFalse(opts.schemaMode());
        }

        @Test void defaultConstructor() {
            var resolver = new ImportResolver();
            assertNotNull(resolver);
        }

        @Test void constructorWithNull() {
            var resolver = new ImportResolver(null);
            assertNotNull(resolver);
        }
    }

    // ─── Options Tests ──────────────────────────────────────────────────────

    @Nested class OptionsTests {
        @Test void defaultMaxDepth() {
            var opts = new ImportResolver.ResolverOptions();
            assertEquals(32, opts.maxImportDepth());
        }

        @Test void defaultMaxFileSize() {
            var opts = new ImportResolver.ResolverOptions();
            assertEquals(10 * 1024 * 1024, opts.maxFileSize());
        }

        @Test void defaultAllowedExtensions() {
            var opts = new ImportResolver.ResolverOptions();
            assertTrue(opts.allowedExtensions().contains(".odin"));
        }

        @Test void defaultNoSandbox() {
            var opts = new ImportResolver.ResolverOptions();
            assertNull(opts.sandboxRoot());
        }

        @Test void customOptions() {
            var opts = new ImportResolver.ResolverOptions(
                "/sandbox", 10, false, List.of(".odin", ".schema"), 1024
            );
            assertEquals("/sandbox", opts.sandboxRoot());
            assertEquals(10, opts.maxImportDepth());
            assertEquals(1024, opts.maxFileSize());
            assertEquals(2, opts.allowedExtensions().size());
        }
    }

    // ─── Resolve Schema with Imports ────────────────────────────────────────

    @Nested class ResolveSchemaTests {
        @Test void resolveSchemaWithNoImports() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolver = ImportResolver.create();
            var result = resolver.resolveSchema(schema, "/test/schema.odin");
            assertNotNull(result);
            assertNotNull(result.resolution());
            assertTrue(result.resolution().imports().isEmpty());
        }

        @Test void resolveSchemaPreservesSchema() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolver = ImportResolver.create();
            var result = resolver.resolveSchema(schema, "/test/schema.odin");
            assertEquals(schema, result.schema());
        }

        @Test void resolveSchemaResultHasTypeRegistry() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolver = ImportResolver.create();
            var result = resolver.resolveSchema(schema, "/test/schema.odin");
            assertNotNull(result.resolution().typeRegistry());
        }

        @Test void resolveSchemaResultHasResolvedPaths() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolver = ImportResolver.create();
            var result = resolver.resolveSchema(schema, "/test/schema.odin");
            assertNotNull(result.resolution().resolvedPaths());
        }
    }

    // ─── Resolve Document with Imports ──────────────────────────────────────

    @Nested class ResolveDocumentTests {
        @Test void resolveDocumentWithNoImports() {
            var doc = new OdinDocumentBuilder().set("x", "y").build();
            var resolver = ImportResolver.create();
            var result = resolver.resolveDocument(doc, "/test/doc.odin");
            assertNotNull(result);
            assertEquals(doc, result.document());
        }

        @Test void resolveDocumentEmptyDoc() {
            var doc = OdinDocument.empty();
            var resolver = ImportResolver.create();
            var result = resolver.resolveDocument(doc, "/test/doc.odin");
            assertTrue(result.resolution().imports().isEmpty());
        }
    }

    // ─── File Loading Error Tests ───────────────────────────────────────────

    @Nested class FileLoadingErrorTests {
        @Test void missingFileThrows() {
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchemaFile("/nonexistent/path/to/file.odin")
            );
        }

        @Test void missingFileErrorCode() {
            var resolver = ImportResolver.create();
            try {
                resolver.resolveSchemaFile("/nonexistent/path/schema.odin");
                fail("Should have thrown");
            } catch (OdinErrors.OdinException e) {
                assertTrue(e.getMessage().equals("I006") || e.getMessage().equals("I008"),
                    "Expected I006 or I008 but got " + e.getMessage());
            }
        }

        @Test void missingDocumentFileThrows() {
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveDocumentFile("/nonexistent/doc.odin")
            );
        }
    }

    // ─── Path Resolution Security Tests ─────────────────────────────────────

    @Nested class PathSecurityTests {
        @Test void emptyImportPathRejected() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("", "empty")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, "/base/schema.odin")
            );
        }

        @Test void httpUrlRejected() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("http://example.com/schema.odin", "remote")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, "/base/schema.odin")
            );
        }

        @Test void httpsUrlRejected() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("https://example.com/schema.odin", "remote")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, "/base/schema.odin")
            );
        }

        @Test void invalidExtensionRejected() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("./evil.exe", "bad")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, "/base/schema.odin")
            );
        }

        @Test void nullByteInPathRejected() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("./file\0.odin", "bad")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, "/base/schema.odin")
            );
        }
    }

    // ─── Sandbox Tests ──────────────────────────────────────────────────────

    @Nested class SandboxTests {
        @Test void sandboxOptionsSet(@TempDir Path tempDir) {
            var opts = new ImportResolver.ResolverOptions(
                tempDir.toString(), 32, true, List.of(".odin"), 10 * 1024 * 1024
            );
            assertEquals(tempDir.toString(), opts.sandboxRoot());
        }

        @Test void pathOutsideSandboxRejected(@TempDir Path tempDir) throws IOException {
            var sandboxDir = tempDir.resolve("sandbox");
            Files.createDirectory(sandboxDir);

            var opts = new ImportResolver.ResolverOptions(
                sandboxDir.toString(), 32, true, List.of(".odin"), 10 * 1024 * 1024
            );
            var resolver = ImportResolver.create(opts);

            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("../../outside.odin", "esc")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );

            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, sandboxDir.resolve("base.odin").toString())
            );
        }
    }

    // ─── File-Based Import Tests ────────────────────────────────────────────

    @Nested class FileBasedImportTests {
        @Test void resolveActualSchemaFile(@TempDir Path tempDir) throws IOException {
            var schemaContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Root}\nname = \"string\"";
            var schemaFile = tempDir.resolve("test.odin");
            Files.writeString(schemaFile, schemaContent);

            var resolver = ImportResolver.create();
            var result = resolver.resolveSchemaFile(schemaFile.toString());
            assertNotNull(result.schema());
        }

        @Test void resolveActualDocumentFile(@TempDir Path tempDir) throws IOException {
            var docContent = "name = \"Alice\"\nage = ##30";
            var docFile = tempDir.resolve("doc.odin");
            Files.writeString(docFile, docContent);

            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            var result = resolver.resolveDocumentFile(docFile.toString());
            assertNotNull(result.document());
        }

        @Test void importChainAImportsBImportsC(@TempDir Path tempDir) throws IOException {
            var cContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Phone}\nnumber = \"string\"";
            Files.writeString(tempDir.resolve("c.odin"), cContent);

            var bContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n@import ./c.odin as c\n\n{Address}\nstreet = \"string\"";
            Files.writeString(tempDir.resolve("b.odin"), bContent);

            var aContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n@import ./b.odin as b\n\n{Person}\nname = \"string\"";
            Files.writeString(tempDir.resolve("a.odin"), aContent);

            var resolver = ImportResolver.create();
            var result = resolver.resolveSchemaFile(tempDir.resolve("a.odin").toString());
            assertNotNull(result);
            assertFalse(result.resolution().imports().isEmpty());
        }

        @Test void fileTooLargeRejected(@TempDir Path tempDir) throws IOException {
            var opts = new ImportResolver.ResolverOptions(null, 32, true, List.of(".odin"), 10);
            var resolver = ImportResolver.create(opts);

            var content = "a_very_long_string_that_exceeds_10_bytes = \"value\"";
            var file = tempDir.resolve("big.odin");
            Files.writeString(file, content);

            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchemaFile(file.toString())
            );
        }
    }

    // ─── Max Depth Tests ────────────────────────────────────────────────────

    @Nested class MaxDepthTests {
        @Test void maxImportDepthDefault() {
            assertEquals(32, new ImportResolver.ResolverOptions().maxImportDepth());
        }

        @Test void customMaxDepth() {
            var opts = new ImportResolver.ResolverOptions(null, 5, true, List.of(".odin"), 10_000_000);
            assertEquals(5, opts.maxImportDepth());
        }
    }

    // ─── Result Types Tests ─────────────────────────────────────────────────

    @Nested class ResultTypeTests {
        @Test void resolvedImportRecord() {
            var ri = new ImportResolver.ResolvedImport("alias", "/path", "./rel", null, null, 5);
            assertEquals("alias", ri.alias());
            assertEquals("/path", ri.path());
            assertEquals("./rel", ri.originalPath());
            assertEquals(5, ri.line());
            assertNull(ri.schema());
            assertNull(ri.document());
        }

        @Test void resolvedResultRecord() {
            var rr = new ImportResolver.ResolvedResult(
                java.util.Map.of(), java.util.Map.of(), List.of()
            );
            assertTrue(rr.imports().isEmpty());
            assertTrue(rr.typeRegistry().isEmpty());
            assertTrue(rr.resolvedPaths().isEmpty());
        }

        @Test void resolvedSchemaRecord() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolution = new ImportResolver.ResolvedResult(
                java.util.Map.of(), java.util.Map.of(), List.of()
            );
            var rs = new ImportResolver.ResolvedSchema(schema, resolution);
            assertEquals(schema, rs.schema());
            assertEquals(resolution, rs.resolution());
        }

        @Test void resolvedDocumentRecord() {
            var doc = OdinDocument.empty();
            var resolution = new ImportResolver.ResolvedResult(
                java.util.Map.of(), java.util.Map.of(), List.of()
            );
            var rd = new ImportResolver.ResolvedDocument(doc, resolution);
            assertEquals(doc, rd.document());
        }
    }

    // ─── Error Code Tests ───────────────────────────────────────────────────

    @Nested class ErrorCodeTests {
        @Test void emptyImportPathCode() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("", "empty")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            try {
                resolver.resolveSchema(schema, "/base.odin");
                fail("Expected exception");
            } catch (OdinErrors.OdinException e) {
                assertEquals("I001", e.getMessage());
            }
        }

        @Test void remoteUrlCode() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("https://evil.com/schema.odin", "remote")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            try {
                resolver.resolveSchema(schema, "/base.odin");
                fail("Expected exception");
            } catch (OdinErrors.OdinException e) {
                assertEquals("I001", e.getMessage());
            }
        }

        @Test void invalidExtensionCode() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("./bad.exe", "bad")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            try {
                resolver.resolveSchema(schema, "/base.odin");
                fail("Expected exception");
            } catch (OdinErrors.OdinException e) {
                assertEquals("I003", e.getMessage());
            }
        }
    }
}
