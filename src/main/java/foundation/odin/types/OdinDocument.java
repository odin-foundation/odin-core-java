package foundation.odin.types;

import java.util.*;

// ── Supporting types ──

// ── OdinDocument ──

public final class OdinDocument {
    private final OrderedMap<String, OdinValue> metadata;
    private final OrderedMap<String, OdinValue> assignments;
    private final OrderedMap<String, OdinModifiers> pathModifiers;
    private final List<OdinImport> imports;
    private final List<OdinSchemaRef> schemas;
    private final List<OdinConditional> conditionals;
    private final List<OdinComment> comments;

    public OdinDocument() {
        this(null, null, null, null, null, null, null);
    }

    public OdinDocument(
            OrderedMap<String, OdinValue> metadata,
            OrderedMap<String, OdinValue> assignments,
            OrderedMap<String, OdinModifiers> modifiers,
            List<OdinImport> imports,
            List<OdinSchemaRef> schemas,
            List<OdinConditional> conditionals,
            List<OdinComment> comments) {
        this.metadata = metadata != null ? metadata : new OrderedMap<>();
        this.assignments = assignments != null ? assignments : new OrderedMap<>();
        this.pathModifiers = modifiers != null ? modifiers : new OrderedMap<>();
        this.imports = imports != null ? Collections.unmodifiableList(imports) : Collections.emptyList();
        this.schemas = schemas != null ? Collections.unmodifiableList(schemas) : Collections.emptyList();
        this.conditionals = conditionals != null ? Collections.unmodifiableList(conditionals) : Collections.emptyList();
        this.comments = comments != null ? Collections.unmodifiableList(comments) : Collections.emptyList();
    }

    public static OdinDocument empty() { return new OdinDocument(); }

    public OrderedMap<String, OdinValue> getMetadata() { return metadata; }
    public OrderedMap<String, OdinValue> getAssignments() { return assignments; }
    public OrderedMap<String, OdinModifiers> getPathModifiers() { return pathModifiers; }
    public List<OdinImport> getImports() { return imports; }
    public List<OdinSchemaRef> getSchemas() { return schemas; }
    public List<OdinConditional> getConditionals() { return conditionals; }
    public List<OdinComment> getComments() { return comments; }

    public OdinValue get(String path) {
        if (path.startsWith("$.")) {
            var metaKey = path.substring(2);
            return metadata.tryGet(metaKey);
        }
        return assignments.tryGet(path);
    }

    public String getString(String path) {
        var v = get(path);
        return v != null ? v.asString() : null;
    }

    public Long getInteger(String path) {
        var v = get(path);
        return v != null ? v.asInt64() : null;
    }

    public Double getNumber(String path) {
        var v = get(path);
        return v != null ? v.asDouble() : null;
    }

    public Boolean getBoolean(String path) {
        var v = get(path);
        return v != null ? v.asBool() : null;
    }

    public boolean has(String path) {
        return get(path) != null;
    }

    public OdinValue resolve(String path) {
        var value = get(path);
        if (value == null) return null;

        if (value instanceof OdinValue.OdinReference refVal) {
            var seen = new HashSet<String>();
            seen.add(path);
            var currentPath = refVal.getPath();

            while (true) {
                if (seen.contains(currentPath))
                    throw new IllegalStateException("Circular reference detected: " + path);
                seen.add(currentPath);

                var current = get(currentPath);
                if (current == null)
                    throw new IllegalStateException("Unresolved reference: " + currentPath);
                if (current instanceof OdinValue.OdinReference nextRef)
                    currentPath = nextRef.getPath();
                else
                    return current;
            }
        }

        return value;
    }

    public List<String> paths() {
        return assignments.keys();
    }

    public OdinDocument with(String path, OdinValue value) {
        var newAssignments = assignments.copy();
        newAssignments.set(path, value);
        return new OdinDocument(metadata, newAssignments, pathModifiers,
                imports, schemas, conditionals, comments);
    }

    public OdinDocument without(String path) {
        var newAssignments = assignments.copy();
        newAssignments.remove(path);
        return new OdinDocument(metadata, newAssignments, pathModifiers,
                imports, schemas, conditionals, comments);
    }

    public OrderedMap<String, String> flatten(FlattenOptions options) {
        var opts = options != null ? options : new FlattenOptions();
        var result = new OrderedMap<String, String>();

        if (opts.includeMetadata()) {
            for (var entry : metadata) {
                result.set("$." + entry.getKey(), formatValueForFlatten(entry.getValue()));
            }
        }

        for (var entry : assignments) {
            if (!opts.includeNulls() && entry.getValue().isNull()) continue;
            result.set(entry.getKey(), formatValueForFlatten(entry.getValue()));
        }

        if (opts.sort()) {
            var entries = new ArrayList<>(result.entries());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            return new OrderedMap<>(entries);
        }

        return result;
    }

    public OrderedMap<String, String> flatten() {
        return flatten(null);
    }

    private static String formatValueForFlatten(OdinValue value) {
        return switch (value) {
            case OdinValue.OdinNull n -> "~";
            case OdinValue.OdinBoolean b -> b.getValue() ? "true" : "false";
            case OdinValue.OdinString s -> s.getValue();
            case OdinValue.OdinInteger i -> i.getRaw() != null ? i.getRaw() : Long.toString(i.getValue());
            case OdinValue.OdinNumber n -> n.getRaw() != null ? n.getRaw() : Double.toString(n.getValue());
            case OdinValue.OdinCurrency c -> c.getRaw() != null ? c.getRaw() : Double.toString(c.getValue());
            case OdinValue.OdinPercent p -> p.getRaw() != null ? p.getRaw() : Double.toString(p.getValue());
            case OdinValue.OdinDate d -> d.getRaw();
            case OdinValue.OdinTimestamp ts -> ts.getRaw();
            case OdinValue.OdinTime t -> t.getValue();
            case OdinValue.OdinDuration d -> d.getValue();
            case OdinValue.OdinReference r -> "@" + r.getPath();
            case OdinValue.OdinBinary b -> "<binary>";
            default -> value.toString();
        };
    }
}
