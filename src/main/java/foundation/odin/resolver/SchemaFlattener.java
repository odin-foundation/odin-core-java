package foundation.odin.resolver;

import foundation.odin.types.*;

import java.util.*;
import java.util.stream.Collectors;

public final class SchemaFlattener {

    // ─── Options ────────────────────────────────────────────────────────────

    public enum ConflictResolution { NAMESPACE, OVERWRITE, ERROR }

    public record FlattenerOptions(
            ConflictResolution conflictResolution,
            boolean treeShake,
            ImportResolver.ResolverOptions resolverOptions
    ) {
        public FlattenerOptions() {
            this(ConflictResolution.NAMESPACE, true, new ImportResolver.ResolverOptions());
        }
    }

    public record FlattenedResult(
            OdinSchema.SchemaDefinition schema,
            List<String> sourceFiles,
            List<String> warnings
    ) {}

    // ─── State ──────────────────────────────────────────────────────────────

    private final FlattenerOptions options;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, String> typeSourceMap = new LinkedHashMap<>(); // typeName -> namespace (null = local)
    private final Set<String> referencedTypes = new LinkedHashSet<>();

    public SchemaFlattener(FlattenerOptions options) {
        this.options = options != null ? options : new FlattenerOptions();
    }

    public SchemaFlattener() {
        this(new FlattenerOptions());
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public FlattenedResult flattenFile(String filePath) {
        var resolver = new ImportResolver(options.resolverOptions());
        var resolved = resolver.resolveSchemaFile(filePath);
        return flattenResolved(resolved);
    }

    public FlattenedResult flattenResolved(ImportResolver.ResolvedSchema resolved) {
        warnings.clear();
        typeSourceMap.clear();
        referencedTypes.clear();

        var schema = resolved.schema();
        var resolution = resolved.resolution();

        // 1. Build type source map
        buildTypeSourceMap(resolution, schema);

        // 2. Merge all types
        var mergedTypes = mergeTypes(resolution, schema);

        // 3. Expand type inheritance
        mergedTypes = expandTypeInheritance(mergedTypes);

        // 4. Merge fields, arrays, constraints
        var mergedFields = mergeFields(resolution, schema);
        var mergedArrays = mergeArrays(resolution, schema);
        var mergedConstraints = mergeConstraints(resolution, schema);

        // 5. Tree shake if enabled
        if (options.treeShake() && !mergedTypes.isEmpty()) {
            collectReferencedTypes(schema, mergedTypes, mergedFields, mergedArrays);

            int originalCount = mergedTypes.size();
            mergedTypes = filterReferencedTypes(mergedTypes);
            int removed = originalCount - mergedTypes.size();
            if (removed > 0) {
                warnings.add("Tree shaking removed " + removed + " unused types");
            }

            mergedFields = filterReferencedFields(mergedFields);
            mergedArrays = filterReferencedArrays(mergedArrays);
            mergedConstraints = filterReferencedConstraints(mergedConstraints);
        }

        // 6. Build flattened schema
        var flatSchema = new OdinSchema.SchemaDefinition(
                schema.metadata(),
                List.of(),
                Map.copyOf(mergedTypes),
                Map.copyOf(mergedFields),
                Map.copyOf(mergedArrays),
                Map.copyOf(mergedConstraints)
        );

        var sourceFiles = new ArrayList<>(resolution.resolvedPaths());
        return new FlattenedResult(flatSchema, sourceFiles, List.copyOf(warnings));
    }

    // ─── Qualified Naming ───────────────────────────────────────────────────

    static String buildQualifiedName(String typeName, String ns) {
        if (ns == null) return typeName;
        if (ns.equals(typeName)) return typeName;
        if (typeName.startsWith(ns + ".")) return typeName;
        if (typeName.startsWith(ns + "_")) return typeName;
        return ns + "_" + typeName;
    }

    // ─── Type Source Map ────────────────────────────────────────────────────

    private void buildTypeSourceMap(ImportResolver.ResolvedResult resolution,
                                    OdinSchema.SchemaDefinition schema) {
        // Add types from imports with their namespace
        for (var entry : resolution.typeRegistry().entrySet()) {
            String name = entry.getKey();
            // Imported types have namespace prefix like "alias_TypeName"
            int underscore = name.indexOf('_');
            if (underscore >= 0) {
                String ns = name.substring(0, underscore);
                String typeName = name.substring(underscore + 1);
                typeSourceMap.put(typeName, ns);
            }
        }

        // Primary schema types override (null namespace = local)
        if (schema.types() != null) {
            for (String typeName : schema.types().keySet()) {
                typeSourceMap.put(typeName, null);
            }
        }
    }

    // ─── Merge Types ────────────────────────────────────────────────────────

    private Map<String, OdinSchema.SchemaType> mergeTypes(
            ImportResolver.ResolvedResult resolution,
            OdinSchema.SchemaDefinition schema) {
        var merged = new LinkedHashMap<String, OdinSchema.SchemaType>();

        // Add imported types first
        for (var entry : resolution.typeRegistry().entrySet()) {
            String name = entry.getKey();
            var type = entry.getValue();
            addType(merged, name, type, null); // already qualified from resolver
        }

        // Primary schema types win
        if (schema.types() != null) {
            for (var entry : schema.types().entrySet()) {
                addType(merged, entry.getKey(), entry.getValue(), null);
            }
        }

        return merged;
    }

    private void addType(Map<String, OdinSchema.SchemaType> merged,
                         String qualifiedName, OdinSchema.SchemaType schemaType, String ns) {
        // Conflict handling
        if (merged.containsKey(qualifiedName)) {
            switch (options.conflictResolution()) {
                case ERROR -> {
                    warnings.add("Type name conflict: " + qualifiedName);
                    return;
                }
                case OVERWRITE -> {
                    warnings.add("Type '" + qualifiedName + "' overwritten by import");
                }
                case NAMESPACE -> {
                    warnings.add("Type '" + qualifiedName + "' conflicts with existing type");
                }
            }
        }

        // Rewrite type references in fields
        var updatedFields = new ArrayList<OdinSchema.SchemaField>();
        if (schemaType.fields() != null) {
            for (var field : schemaType.fields()) {
                updatedFields.add(updateFieldReferences(field));
            }
        }

        merged.put(qualifiedName, new OdinSchema.SchemaType(
                schemaType.name(),
                schemaType.description(),
                updatedFields,
                schemaType.parents() != null ? new ArrayList<>(schemaType.parents()) : List.of()
        ));
    }

    // ─── Type Inheritance Expansion ─────────────────────────────────────────

    private Map<String, OdinSchema.SchemaType> expandTypeInheritance(
            Map<String, OdinSchema.SchemaType> types) {
        var expanded = new LinkedHashMap<String, OdinSchema.SchemaType>();
        var visited = new HashSet<String>();

        for (var entry : types.entrySet()) {
            expanded.put(entry.getKey(), expandSingleType(entry.getKey(), entry.getValue(), types, visited));
        }

        return expanded;
    }

    private OdinSchema.SchemaType expandSingleType(
            String typeName, OdinSchema.SchemaType schemaType,
            Map<String, OdinSchema.SchemaType> allTypes, Set<String> visited) {

        if (schemaType.parents() == null || schemaType.parents().isEmpty()) {
            return cloneSchemaType(schemaType);
        }

        if (visited.contains(typeName)) {
            warnings.add("Circular type inheritance detected for '" + typeName + "'");
            return cloneSchemaType(schemaType);
        }
        visited.add(typeName);

        var mergedFields = new ArrayList<OdinSchema.SchemaField>();
        var mergedFieldNames = new LinkedHashSet<String>();

        for (String parentName : schemaType.parents()) {
            String qualifiedParent = resolveTypeName(parentName);
            var parentType = allTypes.get(qualifiedParent);
            if (parentType != null) {
                var expandedParent = expandSingleType(qualifiedParent, parentType, allTypes, visited);
                if (expandedParent.fields() != null) {
                    for (var field : expandedParent.fields()) {
                        if (!mergedFieldNames.contains(field.name())) {
                            mergedFieldNames.add(field.name());
                            mergedFields.add(cloneSchemaField(field));
                        }
                    }
                }
            }
        }

        // Add/override with local fields
        if (schemaType.fields() != null) {
            for (var field : schemaType.fields()) {
                if (mergedFieldNames.contains(field.name())) {
                    warnings.add("Field '" + field.name() + "' in type '" + typeName + "' overrides base type field");
                    mergedFields.removeIf(f -> f.name().equals(field.name()));
                }
                mergedFieldNames.add(field.name());
                mergedFields.add(cloneSchemaField(field));
            }
        }

        visited.remove(typeName);

        return new OdinSchema.SchemaType(
                schemaType.name(),
                schemaType.description(),
                mergedFields,
                schemaType.parents() != null ? new ArrayList<>(schemaType.parents()) : List.of()
        );
    }

    private String resolveTypeName(String typeName) {
        if (typeName.contains(".")) {
            int lastDot = typeName.lastIndexOf('.');
            String ns = typeName.substring(0, lastDot).replace('.', '_');
            String name = typeName.substring(lastDot + 1);
            return buildQualifiedName(name, ns);
        }

        String sourceNs = typeSourceMap.get(typeName);
        if (sourceNs != null) {
            return buildQualifiedName(typeName, sourceNs);
        }
        return typeName;
    }

    // ─── Reference Rewriting ────────────────────────────────────────────────

    private OdinSchema.SchemaField updateFieldReferences(OdinSchema.SchemaField field) {
        var updated = cloneSchemaField(field);
        var fieldType = field.fieldType();

        if (fieldType instanceof OdinSchema.SchemaFieldType.ReferenceType ref) {
            return new OdinSchema.SchemaField(
                    updated.name(),
                    new OdinSchema.SchemaFieldType.ReferenceType(rewriteTypeReference(ref.target())),
                    updated.required(), updated.confidential(), updated.deprecated(), updated.immutable(),
                    updated.description(), updated.constraints(), updated.defaultValue(), updated.conditionals()
            );
        } else if (fieldType instanceof OdinSchema.SchemaFieldType.TypeRefType typeRef) {
            return new OdinSchema.SchemaField(
                    updated.name(),
                    new OdinSchema.SchemaFieldType.TypeRefType(rewriteTypeReference(typeRef.name())),
                    updated.required(), updated.confidential(), updated.deprecated(), updated.immutable(),
                    updated.description(), updated.constraints(), updated.defaultValue(), updated.conditionals()
            );
        }

        return updated;
    }

    private String rewriteTypeReference(String name) {
        if (name.contains(".")) {
            int lastDot = name.lastIndexOf('.');
            String ns = name.substring(0, lastDot).replace('.', '_');
            String typePart = name.substring(lastDot + 1);
            return buildQualifiedName(typePart, ns);
        }

        if (options.conflictResolution() == ConflictResolution.NAMESPACE) {
            String ns = typeSourceMap.get(name);
            if (ns != null) {
                return buildQualifiedName(name, ns);
            }
        }

        return name;
    }

    // ─── Merge Fields / Arrays / Constraints from Imports ───────────────────

    private Map<String, OdinSchema.SchemaField> mergeFields(
            ImportResolver.ResolvedResult resolution,
            OdinSchema.SchemaDefinition schema) {
        var merged = new LinkedHashMap<String, OdinSchema.SchemaField>();

        // From imported schemas in type registry — look for import entries
        for (var importEntry : resolution.imports().entrySet()) {
            var imp = importEntry.getValue();
            if (imp.schema() != null && imp.schema().fields() != null) {
                for (var entry : imp.schema().fields().entrySet()) {
                    String qualifiedPath = entry.getKey();
                    if (options.conflictResolution() == ConflictResolution.NAMESPACE && imp.alias() != null) {
                        qualifiedPath = imp.alias() + "_" + entry.getKey();
                    }
                    merged.put(qualifiedPath, updateFieldReferences(entry.getValue()));
                }
            }
        }

        // Primary schema fields override
        if (schema.fields() != null) {
            for (var entry : schema.fields().entrySet()) {
                merged.put(entry.getKey(), updateFieldReferences(entry.getValue()));
            }
        }

        return merged;
    }

    private Map<String, OdinSchema.SchemaArray> mergeArrays(
            ImportResolver.ResolvedResult resolution,
            OdinSchema.SchemaDefinition schema) {
        var merged = new LinkedHashMap<String, OdinSchema.SchemaArray>();

        for (var importEntry : resolution.imports().entrySet()) {
            var imp = importEntry.getValue();
            if (imp.schema() != null && imp.schema().arrays() != null) {
                for (var entry : imp.schema().arrays().entrySet()) {
                    String qualifiedPath = entry.getKey();
                    if (options.conflictResolution() == ConflictResolution.NAMESPACE && imp.alias() != null) {
                        qualifiedPath = imp.alias() + "_" + entry.getKey();
                    }
                    var arr = entry.getValue();
                    merged.put(qualifiedPath, new OdinSchema.SchemaArray(
                            qualifiedPath, arr.itemType(), arr.minItems(), arr.maxItems()));
                }
            }
        }

        if (schema.arrays() != null) {
            merged.putAll(schema.arrays());
        }

        return merged;
    }

    private Map<String, List<OdinSchema.SchemaObjectConstraint>> mergeConstraints(
            ImportResolver.ResolvedResult resolution,
            OdinSchema.SchemaDefinition schema) {
        var merged = new LinkedHashMap<String, List<OdinSchema.SchemaObjectConstraint>>();

        for (var importEntry : resolution.imports().entrySet()) {
            var imp = importEntry.getValue();
            if (imp.schema() != null && imp.schema().objectConstraints() != null) {
                for (var entry : imp.schema().objectConstraints().entrySet()) {
                    String qualifiedPath = entry.getKey();
                    if (options.conflictResolution() == ConflictResolution.NAMESPACE && imp.alias() != null) {
                        qualifiedPath = imp.alias() + "_" + entry.getKey();
                    }
                    merged.computeIfAbsent(qualifiedPath, k -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
        }

        if (schema.objectConstraints() != null) {
            for (var entry : schema.objectConstraints().entrySet()) {
                merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }

        return merged;
    }

    // ─── Tree Shaking ───────────────────────────────────────────────────────

    private void collectReferencedTypes(
            OdinSchema.SchemaDefinition primarySchema,
            Map<String, OdinSchema.SchemaType> allTypes,
            Map<String, OdinSchema.SchemaField> mergedFields,
            Map<String, OdinSchema.SchemaArray> mergedArrays) {

        // Start with all types defined in the primary schema
        if (primarySchema.types() != null) {
            for (String typeName : primarySchema.types().keySet()) {
                markTypeReferenced(typeName, allTypes, mergedFields, mergedArrays, new HashSet<>());
            }
        }

        // Types referenced from primary schema fields
        if (primarySchema.fields() != null) {
            for (var field : primarySchema.fields().values()) {
                collectTypeRefsFromFieldType(field.fieldType(), allTypes, mergedFields, mergedArrays, new HashSet<>());
            }
        }

        // Types referenced from primary schema arrays
        if (primarySchema.arrays() != null) {
            for (var array : primarySchema.arrays().values()) {
                if (array.itemType() != null) {
                    collectTypeRefsFromFieldType(array.itemType(), allTypes, mergedFields, mergedArrays, new HashSet<>());
                }
            }
        }
    }

    private void markTypeReferenced(
            String typeName,
            Map<String, OdinSchema.SchemaType> allTypes,
            Map<String, OdinSchema.SchemaField> mergedFields,
            Map<String, OdinSchema.SchemaArray> mergedArrays,
            Set<String> processedTypePaths) {

        if (!referencedTypes.add(typeName)) return;

        var type = allTypes.get(typeName);
        if (type != null) {
            if (type.fields() != null) {
                for (var field : type.fields()) {
                    collectTypeRefsFromFieldType(field.fieldType(), allTypes, mergedFields, mergedArrays, processedTypePaths);
                }
            }
            // Process parents
            if (type.parents() != null) {
                for (String parent : type.parents()) {
                    String qualifiedParent = resolveTypeName(parent);
                    markTypeReferenced(qualifiedParent, allTypes, mergedFields, mergedArrays, processedTypePaths);
                }
            }
        }

        // Process field sections for this type path
        processFieldSectionsForType(typeName, allTypes, mergedFields, mergedArrays, processedTypePaths);
    }

    private void processFieldSectionsForType(
            String typePath,
            Map<String, OdinSchema.SchemaType> allTypes,
            Map<String, OdinSchema.SchemaField> mergedFields,
            Map<String, OdinSchema.SchemaArray> mergedArrays,
            Set<String> processedTypePaths) {

        if (processedTypePaths.contains(typePath)) return;
        processedTypePaths.add(typePath);

        String prefix = typePath + ".";

        // Find nested types
        for (var entry : allTypes.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                if (referencedTypes.add(entry.getKey())) {
                    var nestedType = entry.getValue();
                    if (nestedType.fields() != null) {
                        for (var field : nestedType.fields()) {
                            collectTypeRefsFromFieldType(field.fieldType(), allTypes, mergedFields, mergedArrays, processedTypePaths);
                        }
                    }
                    if (nestedType.parents() != null) {
                        for (String parent : nestedType.parents()) {
                            String qualifiedParent = resolveTypeName(parent);
                            markTypeReferenced(qualifiedParent, allTypes, mergedFields, mergedArrays, processedTypePaths);
                        }
                    }
                    processFieldSectionsForType(entry.getKey(), allTypes, mergedFields, mergedArrays, processedTypePaths);
                }
            }
        }

        // Find field paths
        for (var entry : mergedFields.entrySet()) {
            if (entry.getKey().startsWith(prefix) || entry.getKey().equals(typePath)) {
                collectTypeRefsFromFieldType(entry.getValue().fieldType(), allTypes, mergedFields, mergedArrays, processedTypePaths);
            }
        }

        // Find array paths
        for (var entry : mergedArrays.entrySet()) {
            if (entry.getKey().startsWith(prefix) || entry.getKey().equals(typePath)) {
                collectTypeRefsFromFieldType(entry.getValue().itemType(), allTypes, mergedFields, mergedArrays, processedTypePaths);
            }
        }
    }

    private void collectTypeRefsFromFieldType(
            OdinSchema.SchemaFieldType fieldType,
            Map<String, OdinSchema.SchemaType> allTypes,
            Map<String, OdinSchema.SchemaField> mergedFields,
            Map<String, OdinSchema.SchemaArray> mergedArrays,
            Set<String> processedTypePaths) {

        if (fieldType == null) return;

        String target = null;
        if (fieldType instanceof OdinSchema.SchemaFieldType.ReferenceType ref) {
            target = ref.target();
        } else if (fieldType instanceof OdinSchema.SchemaFieldType.TypeRefType typeRef) {
            target = typeRef.name();
        } else if (fieldType instanceof OdinSchema.SchemaFieldType.UnionType union) {
            for (var member : union.types()) {
                collectTypeRefsFromFieldType(member, allTypes, mergedFields, mergedArrays, processedTypePaths);
            }
            return;
        } else {
            return;
        }

        if (target == null) return;

        String qualified = resolveTypeRefName(target);

        // Try underscore format if not found
        if (!allTypes.containsKey(qualified) && target.contains("_")) {
            qualified = target;
        }

        if (referencedTypes.add(qualified)) {
            var refType = allTypes.get(qualified);
            if (refType != null) {
                if (refType.fields() != null) {
                    for (var f : refType.fields()) {
                        collectTypeRefsFromFieldType(f.fieldType(), allTypes, mergedFields, mergedArrays, processedTypePaths);
                    }
                }
                if (refType.parents() != null) {
                    for (String parent : refType.parents()) {
                        String qualifiedParent = resolveTypeName(parent);
                        markTypeReferenced(qualifiedParent, allTypes, mergedFields, mergedArrays, processedTypePaths);
                    }
                }
            }
            processFieldSectionsForType(qualified, allTypes, mergedFields, mergedArrays, processedTypePaths);
        }
    }

    private String resolveTypeRefName(String name) {
        if (name.contains(".")) {
            int lastDot = name.lastIndexOf('.');
            String ns = name.substring(0, lastDot).replace('.', '_');
            String typePart = name.substring(lastDot + 1);
            return buildQualifiedName(typePart, ns);
        }
        String sourceNs = typeSourceMap.get(name);
        if (sourceNs != null) {
            return buildQualifiedName(name, sourceNs);
        }
        return name;
    }

    // ─── Tree Shaking Filters ───────────────────────────────────────────────

    private Map<String, OdinSchema.SchemaType> filterReferencedTypes(
            Map<String, OdinSchema.SchemaType> types) {
        return types.entrySet().stream()
                .filter(e -> referencedTypes.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, OdinSchema.SchemaField> filterReferencedFields(
            Map<String, OdinSchema.SchemaField> fields) {
        return fields.entrySet().stream()
                .filter(e -> isTypePathReferenced(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, OdinSchema.SchemaArray> filterReferencedArrays(
            Map<String, OdinSchema.SchemaArray> arrays) {
        return arrays.entrySet().stream()
                .filter(e -> isTypePathReferenced(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, List<OdinSchema.SchemaObjectConstraint>> filterReferencedConstraints(
            Map<String, List<OdinSchema.SchemaObjectConstraint>> constraints) {
        return constraints.entrySet().stream()
                .filter(e -> isTypePathReferenced(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private boolean isTypePathReferenced(String path) {
        int idx = path.indexOf('.');
        String typePath = idx >= 0 ? path.substring(0, idx) : path;
        if (referencedTypes.contains(typePath)) return true;
        // Primary schema fields don't have a type prefix
        if (!typePath.contains("_")) return true;
        return false;
    }

    // ─── Cloning Helpers ────────────────────────────────────────────────────

    private static OdinSchema.SchemaType cloneSchemaType(OdinSchema.SchemaType source) {
        var fields = source.fields() != null
                ? source.fields().stream().map(SchemaFlattener::cloneSchemaField).collect(Collectors.toList())
                : new ArrayList<OdinSchema.SchemaField>();
        return new OdinSchema.SchemaType(
                source.name(),
                source.description(),
                fields,
                source.parents() != null ? new ArrayList<>(source.parents()) : List.of()
        );
    }

    private static OdinSchema.SchemaField cloneSchemaField(OdinSchema.SchemaField source) {
        return new OdinSchema.SchemaField(
                source.name(),
                source.fieldType(),
                source.required(),
                source.confidential(),
                source.deprecated(),
                source.immutable(),
                source.description(),
                source.constraints() != null ? new ArrayList<>(source.constraints()) : List.of(),
                source.defaultValue(),
                source.conditionals() != null ? new ArrayList<>(source.conditionals()) : List.of()
        );
    }

    // ─── Factory ────────────────────────────────────────────────────────────

    public static SchemaFlattener create(FlattenerOptions options) {
        return new SchemaFlattener(options);
    }

    public static SchemaFlattener create() {
        return new SchemaFlattener();
    }
}
