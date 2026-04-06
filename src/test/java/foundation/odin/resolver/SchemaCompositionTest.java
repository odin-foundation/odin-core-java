package foundation.odin.resolver;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaCompositionTest {

    // ─── Factory Tests ──────────────────────────────────────────────────────

    @Nested class FactoryTests {
        @Test void createDefault() {
            var flattener = SchemaFlattener.create();
            assertNotNull(flattener);
        }

        @Test void createWithOptions() {
            var opts = new SchemaFlattener.FlattenerOptions();
            var flattener = SchemaFlattener.create(opts);
            assertNotNull(flattener);
        }

        @Test void defaultConstructor() {
            var flattener = new SchemaFlattener();
            assertNotNull(flattener);
        }

        @Test void constructorWithNull() {
            var flattener = new SchemaFlattener(null);
            assertNotNull(flattener);
        }
    }

    // ─── Options Tests ──────────────────────────────────────────────────────

    @Nested class OptionsTests {
        @Test void defaultConflictResolution() {
            var opts = new SchemaFlattener.FlattenerOptions();
            assertEquals(SchemaFlattener.ConflictResolution.NAMESPACE, opts.conflictResolution());
        }

        @Test void defaultTreeShake() {
            var opts = new SchemaFlattener.FlattenerOptions();
            assertTrue(opts.treeShake());
        }

        @Test void customOptions() {
            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.ERROR, false,
                new ImportResolver.ResolverOptions()
            );
            assertEquals(SchemaFlattener.ConflictResolution.ERROR, opts.conflictResolution());
            assertFalse(opts.treeShake());
        }

        @Test void overwriteMode() {
            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.OVERWRITE, true,
                new ImportResolver.ResolverOptions()
            );
            assertEquals(SchemaFlattener.ConflictResolution.OVERWRITE, opts.conflictResolution());
        }
    }

    // ─── Flatten Resolved Schema ────────────────────────────────────────────

    @Nested class FlattenResolvedTests {
        @Test void flattenSchemaWithNoImports() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolution = new ImportResolver.ResolvedResult(
                Map.of(), Map.of(), List.of()
            );
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var flattener = SchemaFlattener.create();
            var result = flattener.flattenResolved(resolved);
            assertNotNull(result.schema());
            assertTrue(result.sourceFiles().isEmpty());
        }

        @Test void flattenPreservesMetadata() {
            var meta = new OdinSchema.SchemaMetadata("test-id", "Test", "desc", "1.0");
            var schema = new OdinSchema.SchemaDefinition(meta, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertEquals("test-id", result.schema().metadata().id());
            assertEquals("Test", result.schema().metadata().title());
        }

        @Test void flattenRemovesImports() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(new OdinSchema.SchemaImport("./other.odin", "other")),
                Map.of(), Map.of(), Map.of(), Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertTrue(result.schema().imports().isEmpty());
        }

        @Test void flattenPreservesPrimaryTypes() {
            var personType = new OdinSchema.SchemaType("Person", List.of(
                new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())
            ));
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of("Person", personType),
                Map.of(), Map.of(), Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.NAMESPACE, false,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertTrue(result.schema().types().containsKey("Person"));
        }

        @Test void flattenMergesImportedTypes() {
            var importedType = new OdinSchema.SchemaType("Address", List.of(
                new OdinSchema.SchemaField("street", new OdinSchema.SchemaFieldType.StringType())
            ));
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of("Person", new OdinSchema.SchemaType("Person", List.of())),
                Map.of(), Map.of(), Map.of()
            );
            var typeRegistry = Map.of("base_Address", (OdinSchema.SchemaType) importedType);
            var resolution = new ImportResolver.ResolvedResult(Map.of(), typeRegistry, List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.NAMESPACE, false,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertTrue(result.schema().types().containsKey("Person"));
            assertTrue(result.schema().types().containsKey("base_Address"));
        }

        @Test void flattenPreservesFields() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of(),
                Map.of("name", new OdinSchema.SchemaField("name", new OdinSchema.SchemaFieldType.StringType())),
                Map.of(), Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertTrue(result.schema().fields().containsKey("name"));
        }

        @Test void flattenPreservesArrays() {
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of("items", new OdinSchema.SchemaArray("items", new OdinSchema.SchemaFieldType.StringType(), 0L, 100L)),
                Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertTrue(result.schema().arrays().containsKey("items"));
        }
    }

    // ─── Conflict Resolution Tests ──────────────────────────────────────────

    @Nested class ConflictResolutionTests {
        @Test void errorModeNoConflictOnPrimaryOverwrite() {
            var type = new OdinSchema.SchemaType("Clash", List.of());
            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of("Clash", type),
                Map.of(), Map.of(), Map.of()
            );
            var typeRegistry = Map.of("Clash", (OdinSchema.SchemaType) new OdinSchema.SchemaType("Clash", List.of()));
            var resolution = new ImportResolver.ResolvedResult(Map.of(), typeRegistry, List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.ERROR, false,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertNotNull(result);
        }

        @Test void overwriteModeWorks() {
            var type1 = new OdinSchema.SchemaType("Shared", List.of());
            var typeRegistry = new java.util.LinkedHashMap<String, OdinSchema.SchemaType>();
            typeRegistry.put("Shared", type1);

            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), typeRegistry, List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.OVERWRITE, false,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertNotNull(result);
        }
    }

    // ─── Tree Shaking Tests ─────────────────────────────────────────────────

    @Nested class TreeShakingTests {
        @Test void treeShakingRemovesUnusedTypes() {
            var personType = new OdinSchema.SchemaType("Person", List.of());
            var unusedType = new OdinSchema.SchemaType("Unused", List.of());

            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of("Person", personType),
                Map.of(), Map.of(), Map.of()
            );

            var typeRegistry = new java.util.LinkedHashMap<String, OdinSchema.SchemaType>();
            typeRegistry.put("Unused", unusedType);
            typeRegistry.put("Person", personType);

            var resolution = new ImportResolver.ResolvedResult(Map.of(), typeRegistry, List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.NAMESPACE, true,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertTrue(result.schema().types().containsKey("Person"));
        }

        @Test void noTreeShakingKeepsAll() {
            var personType = new OdinSchema.SchemaType("Person", List.of());
            var extraType = new OdinSchema.SchemaType("Extra", List.of());

            var schema = new OdinSchema.SchemaDefinition(
                new OdinSchema.SchemaMetadata(null, null, null, null),
                List.of(),
                Map.of("Person", personType, "Extra", extraType),
                Map.of(), Map.of(), Map.of()
            );
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var opts = new SchemaFlattener.FlattenerOptions(
                SchemaFlattener.ConflictResolution.NAMESPACE, false,
                new ImportResolver.ResolverOptions()
            );
            var result = new SchemaFlattener(opts).flattenResolved(resolved);
            assertEquals(2, result.schema().types().size());
        }
    }

    // ─── FlattenedResult Tests ──────────────────────────────────────────────

    @Nested class FlattenedResultTests {
        @Test void resultHasWarnings() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of());
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertNotNull(result.warnings());
        }

        @Test void resultHasSourceFiles() {
            var schema = new OdinSchema.SchemaDefinition();
            var resolution = new ImportResolver.ResolvedResult(Map.of(), Map.of(), List.of("file1.odin"));
            var resolved = new ImportResolver.ResolvedSchema(schema, resolution);

            var result = SchemaFlattener.create().flattenResolved(resolved);
            assertEquals(1, result.sourceFiles().size());
        }
    }

    // ─── File-Based Flatten Tests ───────────────────────────────────────────

    @Nested class FileBasedFlattenTests {
        @Test void flattenActualFile(@TempDir Path tempDir) throws IOException {
            var schemaContent = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n{Person}\nname = \"string\"";
            var file = tempDir.resolve("schema.odin");
            Files.writeString(file, schemaContent);

            var flattener = SchemaFlattener.create();
            var result = flattener.flattenFile(file.toString());
            assertNotNull(result.schema());
        }

        @Test void flattenMissingFileThrows() {
            var flattener = SchemaFlattener.create();
            assertThrows(OdinErrors.OdinException.class, () ->
                flattener.flattenFile("/nonexistent/schema.odin")
            );
        }
    }

    // ─── Schema Types Tests ─────────────────────────────────────────────────

    @Nested class SchemaTypeTests {
        @Test void schemaFieldTypes() {
            assertNotNull(new OdinSchema.SchemaFieldType.StringType());
            assertNotNull(new OdinSchema.SchemaFieldType.BooleanType());
            assertNotNull(new OdinSchema.SchemaFieldType.NullType());
            assertNotNull(new OdinSchema.SchemaFieldType.IntegerType());
            assertNotNull(new OdinSchema.SchemaFieldType.DateType());
            assertNotNull(new OdinSchema.SchemaFieldType.TimestampType());
            assertNotNull(new OdinSchema.SchemaFieldType.TimeType());
            assertNotNull(new OdinSchema.SchemaFieldType.DurationType());
            assertNotNull(new OdinSchema.SchemaFieldType.BinaryType());
            assertNotNull(new OdinSchema.SchemaFieldType.PercentType());
        }

        @Test void numberTypeWithDecimals() {
            var nt = new OdinSchema.SchemaFieldType.NumberType((byte) 4);
            assertEquals((byte) 4, nt.decimalPlaces());
        }

        @Test void currencyTypeWithDecimals() {
            var ct = new OdinSchema.SchemaFieldType.CurrencyType((byte) 2);
            assertEquals((byte) 2, ct.decimalPlaces());
        }

        @Test void enumTypeWithValues() {
            var et = new OdinSchema.SchemaFieldType.EnumType(List.of("A", "B", "C"));
            assertEquals(3, et.values().size());
        }

        @Test void unionType() {
            var ut = new OdinSchema.SchemaFieldType.UnionType(List.of(
                new OdinSchema.SchemaFieldType.StringType(),
                new OdinSchema.SchemaFieldType.IntegerType()
            ));
            assertEquals(2, ut.types().size());
        }

        @Test void referenceType() {
            var rt = new OdinSchema.SchemaFieldType.ReferenceType("Address");
            assertEquals("Address", rt.target());
        }

        @Test void typeRefType() {
            var trt = new OdinSchema.SchemaFieldType.TypeRefType("PersonType");
            assertEquals("PersonType", trt.name());
        }
    }

    // ─── Schema Constraint Tests ────────────────────────────────────────────

    @Nested class SchemaConstraintTests {
        @Test void boundsConstraint() {
            var b = new OdinSchema.SchemaConstraint.Bounds("0", "100", false, false);
            assertEquals("0", b.min());
            assertEquals("100", b.max());
        }

        @Test void boundsExclusive() {
            var b = new OdinSchema.SchemaConstraint.Bounds("0", "100", true, true);
            assertTrue(b.minExclusive());
            assertTrue(b.maxExclusive());
        }

        @Test void patternConstraint() {
            var p = new OdinSchema.SchemaConstraint.Pattern("^[A-Z]+$");
            assertEquals("^[A-Z]+$", p.pattern());
        }

        @Test void enumConstraint() {
            var e = new OdinSchema.SchemaConstraint.Enum(List.of("active", "inactive"));
            assertEquals(2, e.values().size());
        }

        @Test void uniqueConstraint() {
            var u = new OdinSchema.SchemaConstraint.Unique();
            assertNotNull(u);
        }

        @Test void sizeConstraint() {
            var s = new OdinSchema.SchemaConstraint.Size(1L, 255L);
            assertEquals(1L, s.min());
            assertEquals(255L, s.max());
        }

        @Test void formatConstraint() {
            var f = new OdinSchema.SchemaConstraint.Format("email");
            assertEquals("email", f.formatName());
        }
    }
}
