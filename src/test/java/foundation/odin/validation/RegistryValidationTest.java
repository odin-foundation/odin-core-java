package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.resolver.ImportResolver;
import foundation.odin.types.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegistryValidationTest {

    private static final String CANONICAL =
        "../../schemas/insurance/personal/auto/policy.schema.odin";

    private OdinSchema.SchemaDefinition canonicalSchema() throws Exception {
        return Odin.parseSchema(Files.readString(Paths.get(CANONICAL)));
    }

    private long v013Count(OdinSchema.ValidationResult r) {
        return r.errors().stream().filter(e -> "V013".equals(e.code())).count();
    }

    @Test void importStripsQuotesAndKeepsAlias() throws Exception {
        var schema = canonicalSchema();
        var imp = schema.imports().stream()
            .filter(i -> "types".equals(i.alias())).findFirst().orElseThrow();
        assertEquals("../../common/types.schema.odin", imp.path());
        assertFalse(imp.path().contains("\""));
    }

    @Test void registryResolvesNamespacedType() throws Exception {
        var schema = canonicalSchema();
        var resolver = new ImportResolver(ImportResolver.ResolverOptions.forSchemas());
        var reg = resolver.resolveSchema(schema, CANONICAL).resolution().registry();
        assertNotNull(reg.lookup("types.policy_status"));
    }

    @Test void topLevelImportedTypeRefV013ResolvedByRegistry() throws Exception {
        var schema = canonicalSchema();
        var resolver = new ImportResolver(ImportResolver.ResolverOptions.forSchemas());
        var reg = resolver.resolveSchema(schema, CANONICAL).resolution().registry();

        var statusField = new OdinSchema.SchemaField("status",
            new OdinSchema.SchemaFieldType.TypeRefType("types.policy_status"),
            true, false, false, false, null, List.of(), null, List.of());
        var schemaWithRef = new OdinSchema.SchemaDefinition(schema.metadata(), schema.imports(),
            schema.types(), Map.of("status", statusField), schema.arrays(), schema.objectConstraints());

        var noRegistry = Odin.validate(OdinDocument.empty(), schemaWithRef, null);
        assertEquals(1, v013Count(noRegistry));

        var withRegistry = Odin.validate(OdinDocument.empty(), schemaWithRef, null, reg);
        assertEquals(0, v013Count(withRegistry));
    }

    @Test void termFieldsNestUnderPolicyType() throws Exception {
        var schema = canonicalSchema();
        var policy = schema.types().get("policy");
        assertNotNull(policy, "policy type missing");
        var fieldNames = policy.fields().stream()
            .map(OdinSchema.SchemaField::name).toList();
        assertTrue(fieldNames.contains("term.effective"), "term.effective not in policy type: " + fieldNames);
        assertTrue(fieldNames.contains("term.expiration"), "term.expiration not in policy type: " + fieldNames);
    }

    @Test void emptyDocYieldsNoRootRequiredErrors() throws Exception {
        var schema = canonicalSchema();
        var result = Odin.validateWithImports(OdinDocument.empty(), schema, CANONICAL, null);
        var rootRequired = result.errors().stream()
            .filter(e -> "V001".equals(e.code())).count();
        assertEquals(0, rootRequired, "errors: " + result.errors());
    }

    @Test void minimalSatisfyingDocValidatesClean() throws Exception {
        var schema = canonicalSchema();
        var doc = new OdinDocumentBuilder()
            .set(".term.effective", OdinValue.dateFromStr("2024-01-01"))
            .set(".term.expiration", OdinValue.dateFromStr("2025-01-01"))
            .build();
        var result = Odin.validateWithImports(doc, schema, CANONICAL, null);
        assertTrue(result.valid(), "errors: " + result.errors());
    }
}
