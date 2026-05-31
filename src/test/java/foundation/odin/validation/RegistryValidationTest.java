package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.resolver.ImportResolver;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryValidationTest {

    // In-memory reader serving inline schema sources by path; keeps tests self-contained.
    private static final class MemReader implements ImportResolver.FileReader {
        private final Map<String, String> files;
        MemReader(Map<String, String> files) { this.files = files; }

        @Override public String readFile(String path) {
            var content = files.get(path);
            if (content == null) {
                throw new OdinErrors.OdinException("I006", "File not found: " + path);
            }
            return content;
        }

        @Override public String resolvePath(String basePath, String importPath) {
            return importPath;
        }
    }

    private long v013Count(OdinSchema.ValidationResult r) {
        return r.errors().stream().filter(e -> "V013".equals(e.code())).count();
    }

    // An imported @alias.typename reference is unresolved (V013) without a registry,
    // and resolves once the import registry is supplied.
    @Test void importedTypeRefResolvesWithRegistry() {
        var files = Map.of(
            "main.odin",
            "@import \"types.odin\" as types\n" +
            "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n",
            "types.odin",
            "{@policy_status}\nvalue = !\n"
        );

        var resolver = new ImportResolver(new MemReader(files), ImportResolver.ResolverOptions.forSchemas());
        var resolved = resolver.resolveSchemaFile("main.odin");
        var reg = resolved.resolution().registry();
        assertNotNull(reg.lookup("types.policy_status"), "registry should resolve types.policy_status");

        var statusField = new OdinSchema.SchemaField("status",
            new OdinSchema.SchemaFieldType.TypeRefType("types.policy_status"),
            true, false, false, false, null, List.of(), null, List.of());
        var schema = resolved.schema();
        var schemaWithRef = new OdinSchema.SchemaDefinition(schema.metadata(), schema.imports(),
            schema.types(), Map.of("status", statusField), schema.arrays(), schema.objectConstraints());

        var noRegistry = Odin.validate(OdinDocument.empty(), schemaWithRef, null);
        assertEquals(1, v013Count(noRegistry), "expected V013 without registry");

        var withRegistry = Odin.validate(OdinDocument.empty(), schemaWithRef, null, reg);
        assertEquals(0, v013Count(withRegistry), "registry must resolve @types.policy_status");
    }

    // A relative {.sub} header inside a {@type} nests its fields into that type, not the root.
    @Test void relativeSubsectionNestsIntoType() {
        var schema = Odin.parseSchema(
            "{@policy}\nnumber = !\n{.term}\neffective = !date\nexpiration = !date\n");

        var policy = schema.types().get("policy");
        assertNotNull(policy, "policy type should be defined");
        var fieldNames = policy.fields().stream()
            .map(OdinSchema.SchemaField::name).toList();
        assertTrue(fieldNames.contains("term.effective"), "term.effective not in policy type: " + fieldNames);
        assertTrue(fieldNames.contains("term.expiration"), "term.expiration not in policy type: " + fieldNames);

        assertFalse(schema.fields().containsKey("term.effective"), "term.* must not leak to the schema root");
        assertFalse(schema.fields().containsKey("term.expiration"), "term.* must not leak to the schema root");
    }
}
