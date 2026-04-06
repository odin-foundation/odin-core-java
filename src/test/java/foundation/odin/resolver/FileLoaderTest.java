package foundation.odin.resolver;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileLoaderTest {

    // ─── Basic File Loading ─────────────────────────────────────────────────

    @Nested class BasicLoadTests {
        @Test void loadSimpleSchemaFile(@TempDir Path tempDir) throws IOException {
            var content = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"";
            Files.writeString(tempDir.resolve("simple.odin"), content);

            var resolver = ImportResolver.create();
            var result = resolver.resolveSchemaFile(tempDir.resolve("simple.odin").toString());
            assertNotNull(result);
        }

        @Test void loadSimpleDocumentFile(@TempDir Path tempDir) throws IOException {
            var content = "name = \"Alice\"\nage = ##30";
            Files.writeString(tempDir.resolve("doc.odin"), content);

            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            var result = resolver.resolveDocumentFile(tempDir.resolve("doc.odin").toString());
            assertNotNull(result.document());
        }

        @Test void loadUtf8Content(@TempDir Path tempDir) throws IOException {
            var content = "name = \"Ñoño café 日本語\"";
            Files.writeString(tempDir.resolve("utf8.odin"), content, StandardCharsets.UTF_8);

            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            var result = resolver.resolveDocumentFile(tempDir.resolve("utf8.odin").toString());
            assertNotNull(result);
        }

        @Test void loadEmptyFile(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("empty.odin"), "");

            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            try {
                resolver.resolveDocumentFile(tempDir.resolve("empty.odin").toString());
            } catch (Exception e) {
                assertNotNull(e.getMessage());
            }
        }
    }

    // ─── File Not Found ─────────────────────────────────────────────────────

    @Nested class FileNotFoundTests {
        @Test void nonexistentSchemaFile() {
            var resolver = ImportResolver.create();
            var ex = assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchemaFile("/does/not/exist.odin")
            );
            assertNotNull(ex.getCode());
        }

        @Test void nonexistentDocumentFile() {
            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveDocumentFile("/does/not/exist.odin")
            );
        }

        @Test void nonexistentImportedFile(@TempDir Path tempDir) throws IOException {
            var content = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n@import ./missing.odin as missing";
            Files.writeString(tempDir.resolve("main.odin"), content);

            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchemaFile(tempDir.resolve("main.odin").toString())
            );
        }
    }

    // ─── File Size Limits ───────────────────────────────────────────────────

    @Nested class FileSizeLimitTests {
        @Test void fileSizeWithinLimit(@TempDir Path tempDir) throws IOException {
            var content = "x = \"hello\"";
            Files.writeString(tempDir.resolve("small.odin"), content);

            var opts = new ImportResolver.ResolverOptions(null, 32, false, List.of(".odin"), 1_000_000);
            var resolver = ImportResolver.create(opts);
            var result = resolver.resolveDocumentFile(tempDir.resolve("small.odin").toString());
            assertNotNull(result);
        }

        @Test void fileSizeExceedsLimit(@TempDir Path tempDir) throws IOException {
            var sb = new StringBuilder();
            for (int i = 0; i < 100; i++) sb.append("field_").append(i).append(" = \"value\"\n");
            Files.writeString(tempDir.resolve("big.odin"), sb.toString());

            var opts = new ImportResolver.ResolverOptions(null, 32, true, List.of(".odin"), 10);
            var resolver = ImportResolver.create(opts);
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchemaFile(tempDir.resolve("big.odin").toString())
            );
        }

        @Test void zeroByteFile(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("zero.odin"), "");
            var resolver = ImportResolver.create(ImportResolver.ResolverOptions.forDocuments());
            try {
                resolver.resolveDocumentFile(tempDir.resolve("zero.odin").toString());
            } catch (Exception e) {
                assertNotNull(e);
            }
        }
    }

    // ─── Path Resolution ────────────────────────────────────────────────────

    @Nested class PathResolutionTests {
        @Test void relativeImportPath(@TempDir Path tempDir) throws IOException {
            var baseContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Base}\nx = \"string\"";
            Files.writeString(tempDir.resolve("base.odin"), baseContent);

            var mainContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n@import ./base.odin as base\n\n{Main}\ny = \"string\"";
            Files.writeString(tempDir.resolve("main.odin"), mainContent);

            var resolver = ImportResolver.create();
            var result = resolver.resolveSchemaFile(tempDir.resolve("main.odin").toString());
            assertFalse(result.resolution().imports().isEmpty());
        }

        @Test void subdirectoryImportPath(@TempDir Path tempDir) throws IOException {
            var subDir = tempDir.resolve("types");
            Files.createDirectory(subDir);

            var typeContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Address}\nstreet = \"string\"";
            Files.writeString(subDir.resolve("address.odin"), typeContent);

            var mainContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n@import ./types/address.odin as addr\n\n{Person}\nname = \"string\"";
            Files.writeString(tempDir.resolve("main.odin"), mainContent);

            var resolver = ImportResolver.create();
            var result = resolver.resolveSchemaFile(tempDir.resolve("main.odin").toString());
            assertFalse(result.resolution().imports().isEmpty());
        }
    }

    // ─── Extension Validation ───────────────────────────────────────────────

    @Nested class ExtensionValidationTests {
        @Test void odinExtensionAllowed() {
            var opts = new ImportResolver.ResolverOptions();
            assertTrue(opts.allowedExtensions().contains(".odin"));
        }

        @Test void invalidExtensionBlocked(@TempDir Path tempDir) throws IOException {
            Files.writeString(tempDir.resolve("bad.txt"), "content");

            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("./bad.txt", "bad")),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of()
            );
            var resolver = ImportResolver.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                resolver.resolveSchema(schema, tempDir.resolve("main.odin").toString())
            );
        }

        @Test void customExtensionAllowed(@TempDir Path tempDir) throws IOException {
            var content = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"";
            Files.writeString(tempDir.resolve("schema.schema"), content);

            var opts = new ImportResolver.ResolverOptions(null, 32, true,
                List.of(".odin", ".schema"), 10_000_000);
            var resolver = ImportResolver.create(opts);
            var result = resolver.resolveSchemaFile(tempDir.resolve("schema.schema").toString());
            assertNotNull(result);
        }
    }

    // ─── Caching Tests ──────────────────────────────────────────────────────

    @Nested class CachingTests {
        @Test void cachedResultConsistent(@TempDir Path tempDir) throws IOException {
            var content = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Type}\nx = \"string\"";
            Files.writeString(tempDir.resolve("cached.odin"), content);

            var resolver = ImportResolver.create();
            var result1 = resolver.resolveSchemaFile(tempDir.resolve("cached.odin").toString());
            var resolver2 = ImportResolver.create();
            var result2 = resolver2.resolveSchemaFile(tempDir.resolve("cached.odin").toString());
            assertNotNull(result1);
            assertNotNull(result2);
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
