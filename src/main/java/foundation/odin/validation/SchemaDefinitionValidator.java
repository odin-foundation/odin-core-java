package foundation.odin.validation;

import foundation.odin.resolver.ImportResolver.TypeRegistry;
import foundation.odin.types.OdinSchema;
import foundation.odin.types.OdinSchema.SchemaField;
import foundation.odin.types.OdinSchema.SchemaFieldType;
import foundation.odin.types.OdinSchema.SchemaType;
import foundation.odin.types.OdinSchema.SchemaConstraint;
import foundation.odin.types.OdinSchema.ValidationError;

import java.util.*;

/**
 * Validates that the schema itself is well-formed, independent of any document:
 * override narrowing, intersection field conflicts, tabular column rules,
 * and default-value rules. Violations are reported as V017.
 */
public final class SchemaDefinitionValidator {

    private SchemaDefinitionValidator() {}

    private static final Set<String> PRIMITIVE_KINDS = Set.of(
            "StringType", "BooleanType", "NumberType", "IntegerType", "CurrencyType",
            "PercentType", "DateType", "TimestampType", "TimeType", "DurationType",
            "EnumType", "BinaryType", "NullType");

    public static void validate(OdinSchema.SchemaDefinition schema, TypeRegistry registry,
            List<ValidationError> errors) {
        validateTypeDefinitions(schema, registry, errors);
        validatePathCompositions(schema, registry, errors);
        validateTabularColumns(schema, registry, errors);
        validateDefaults(schema, errors);
    }

    private static void addError(List<ValidationError> errors, String path, String message) {
        errors.add(new ValidationError(path, "V017", message));
    }

    private static SchemaType lookupType(OdinSchema.SchemaDefinition schema, TypeRegistry registry, String name) {
        if (registry != null) {
            var t = registry.lookup(name);
            if (t != null) return t;
        }
        return schema.types().get(name);
    }

    private static SchemaField fieldByName(SchemaType type, String name) {
        if (type == null) return null;
        for (var f : type.fields()) {
            if (name.equals(f.name())) return f;
        }
        return null;
    }

    private static List<String> memberNames(String joined) {
        var out = new ArrayList<String>();
        for (String n : joined.split("&")) {
            String t = n.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ── Override and intersection (type definitions) ──

    private static void validateTypeDefinitions(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, List<ValidationError> errors) {
        for (var entry : schema.types().entrySet()) {
            String typeName = entry.getKey();
            var type = entry.getValue();
            var composition = fieldByName(type, "_composition");
            if (composition == null
                    || !(composition.fieldType() instanceof SchemaFieldType.TypeRefType ref)) continue;

            var members = memberNames(ref.name());
            if (ref.override()) {
                validateOverride(schema, registry, typeName, type, members, errors);
            } else if (members.size() > 1) {
                validateIntersectionConflicts(schema, registry, typeName, members, errors);
            }
        }
    }

    private static void validateOverride(OdinSchema.SchemaDefinition schema, TypeRegistry registry,
            String typeName, SchemaType type, List<String> baseNames, List<ValidationError> errors) {
        var baseFields = new LinkedHashMap<String, SchemaField>();
        for (String baseName : baseNames) {
            var base = lookupType(schema, registry, baseName);
            if (base == null) continue;
            for (var f : base.fields()) {
                if (!"_composition".equals(f.name())) baseFields.put(f.name(), f);
            }
        }

        for (var override : type.fields()) {
            if ("_composition".equals(override.name())) continue;
            var base = baseFields.get(override.name());
            if (base == null) continue;
            checkOverrideField(errors, "@" + typeName + "." + override.name(), base, override);
        }
    }

    private static void checkOverrideField(List<ValidationError> errors, String label,
            SchemaField base, SchemaField override) {
        // Base type must match.
        if (!sameBaseType(base.fieldType(), override.fieldType())) {
            addError(errors, label, "Override changes field type");
        }
        // required: optional→required allowed, required→optional forbidden.
        if (base.required() && !override.required()) {
            addError(errors, label, "Override relaxes required field to optional");
        }
        // nullable: may remove, may not add.
        if (!base.nullable() && override.nullable()) {
            addError(errors, label, "Override adds nullability");
        }
        // bounds: may only narrow.
        var baseBounds = findBounds(base.constraints());
        var overrideBounds = findBounds(override.constraints());
        if (baseBounds != null && overrideBounds != null && widensBounds(baseBounds, overrideBounds)) {
            addError(errors, label, "Override widens constraint bounds");
        }
    }

    private static void validateIntersectionConflicts(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, String typeName, List<String> memberNames, List<ValidationError> errors) {
        var seen = new LinkedHashMap<String, SchemaField>();
        for (String memberName : memberNames) {
            var member = lookupType(schema, registry, memberName);
            if (member == null) continue;
            for (var f : member.fields()) {
                if ("_composition".equals(f.name())) continue;
                var prior = seen.get(f.name());
                if (prior != null && !sameFieldDefinition(prior, f)) {
                    addError(errors, "@" + typeName + "." + f.name(),
                            "Intersection field conflict: '" + f.name() + "' differs between members");
                } else if (prior == null) {
                    seen.put(f.name(), f);
                }
            }
        }
    }

    // ── Path-level compositions ({path} = @base :override) ──

    private static void validatePathCompositions(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, List<ValidationError> errors) {
        for (var entry : schema.fields().entrySet()) {
            String path = entry.getKey();
            var field = entry.getValue();
            if (!path.endsWith("._composition")
                    || !(field.fieldType() instanceof SchemaFieldType.TypeRefType ref)) continue;
            String parentPath = path.substring(0, path.length() - "._composition".length());
            var members = memberNames(ref.name());

            if (ref.override()) {
                var baseFields = new LinkedHashMap<String, SchemaField>();
                for (String baseName : members) {
                    var base = lookupType(schema, registry, baseName);
                    if (base == null) continue;
                    for (var f : base.fields()) {
                        if (!"_composition".equals(f.name())) baseFields.put(f.name(), f);
                    }
                }
                for (var fieldEntry : schema.fields().entrySet()) {
                    String fieldPath = fieldEntry.getKey();
                    if (!fieldPath.startsWith(parentPath + ".") || fieldPath.endsWith("._composition")) continue;
                    String localName = fieldPath.substring(parentPath.length() + 1);
                    if (localName.contains(".")) continue;
                    var base = baseFields.get(localName);
                    if (base == null) continue;
                    checkOverrideField(errors, fieldPath, base, fieldEntry.getValue());
                }
            } else if (members.size() > 1) {
                validateIntersectionConflicts(schema, registry, parentPath, members, errors);
            }
        }
    }

    // ── Tabular column rules ──

    private static void validateTabularColumns(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, List<ValidationError> errors) {
        for (var entry : schema.arrays().entrySet()) {
            String arrayPath = entry.getKey();
            var array = entry.getValue();
            if (array.columns() == null || array.columns().isEmpty()) continue;
            for (String column : array.columns()) {
                String label = arrayPath + "[]." + column;

                if (isMultiLevelColumn(column)) {
                    addError(errors, label, "Tabular column uses multi-level path");
                    continue;
                }

                String itemName = column.replaceAll("\\[\\d+\\]$", "");
                var field = array.itemFields().get(itemName);
                if (field == null) field = array.itemFields().get(column);
                if (field == null) continue;

                if (!isPrimitiveColumnType(schema, registry, field.fieldType())) {
                    addError(errors, label, "Tabular column must be a primitive type");
                }
            }
        }
    }

    private static boolean isMultiLevelColumn(String column) {
        int dotCount = (int) column.chars().filter(c -> c == '.').count();
        int indexCount = column.split("\\[\\d+\\]", -1).length - 1;
        if (dotCount > 1 || indexCount > 1) return true;
        return dotCount == 1 && indexCount == 1;
    }

    private static boolean isPrimitiveColumnType(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, SchemaFieldType type) {
        if (type instanceof SchemaFieldType.TypeRefType) return false;
        if (type instanceof SchemaFieldType.UnionType u) {
            return u.types().stream().allMatch(t -> isPrimitiveColumnType(schema, registry, t));
        }
        if (type instanceof SchemaFieldType.ReferenceType) return false;
        return PRIMITIVE_KINDS.contains(type.getClass().getSimpleName());
    }

    // ── Default-value rules ──

    private static void validateDefaults(OdinSchema.SchemaDefinition schema, List<ValidationError> errors) {
        for (var entry : schema.fields().entrySet()) {
            String path = entry.getKey();
            if (path.endsWith("._composition")) continue;
            checkDefault(errors, path, entry.getValue());
        }
        for (var type : schema.types().values()) {
            for (var field : type.fields()) {
                if ("_composition".equals(field.name())) continue;
                checkDefault(errors, "@" + type.name() + "." + field.name(), field);
            }
        }
        for (var entry : schema.arrays().entrySet()) {
            for (var fieldEntry : entry.getValue().itemFields().entrySet()) {
                checkDefault(errors, entry.getKey() + "[]." + fieldEntry.getKey(), fieldEntry.getValue());
            }
        }
    }

    private static void checkDefault(List<ValidationError> errors, String label, SchemaField field) {
        if (field.defaultValue() == null) return;

        if (field.required()) {
            addError(errors, label, "Required field cannot have a default value");
            return;
        }

        if (!defaultSatisfiesConstraints(field, field.defaultValue())) {
            addError(errors, label, "Default value violates field constraints");
        }
    }

    private static boolean defaultSatisfiesConstraints(SchemaField field, OdinSchema.DefaultValue value) {
        for (var constraint : field.constraints()) {
            if (constraint instanceof SchemaConstraint.Bounds bounds) {
                if (!boundsSatisfied(bounds, value)) return false;
            } else if (constraint instanceof SchemaConstraint.Enum en) {
                if (!"string".equals(value.type()) || !en.values().contains(String.valueOf(value.value()))) {
                    return false;
                }
            } else if (constraint instanceof SchemaConstraint.Pattern pat) {
                if ("string".equals(value.type())) {
                    try {
                        if (!java.util.regex.Pattern.compile(pat.pattern())
                                .matcher(String.valueOf(value.value())).find()) return false;
                    } catch (RuntimeException ignored) { /* invalid pattern handled elsewhere */ }
                }
            }
        }
        if (field.fieldType() instanceof SchemaFieldType.EnumType en) {
            if (!"string".equals(value.type()) || !en.values().contains(String.valueOf(value.value()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean boundsSatisfied(SchemaConstraint.Bounds c, OdinSchema.DefaultValue value) {
        Double target = null;
        if (value.value() instanceof Number n
                && ("number".equals(value.type()) || "integer".equals(value.type())
                || "currency".equals(value.type()) || "percent".equals(value.type()))) {
            target = n.doubleValue();
        } else if ("string".equals(value.type()) && value.value() != null) {
            target = (double) String.valueOf(value.value()).length();
        }
        if (target == null) return true;

        if (c.min() != null) {
            try { if (target < Double.parseDouble(c.min())) return false; } catch (NumberFormatException ignored) {}
        }
        if (c.max() != null) {
            try { if (target > Double.parseDouble(c.max())) return false; } catch (NumberFormatException ignored) {}
        }
        return true;
    }

    // ── Helpers ──

    private static boolean sameBaseType(SchemaFieldType a, SchemaFieldType b) {
        return a.getClass().equals(b.getClass());
    }

    private static SchemaConstraint.Bounds findBounds(List<SchemaConstraint> constraints) {
        for (var c : constraints) {
            if (c instanceof SchemaConstraint.Bounds b) return b;
        }
        return null;
    }

    private static boolean widensBounds(SchemaConstraint.Bounds base, SchemaConstraint.Bounds override) {
        Double baseMin = parse(base.min());
        Double baseMax = parse(base.max());
        Double overMin = parse(override.min());
        Double overMax = parse(override.max());
        // min: override may only raise (narrow). Removing or lowering min widens.
        if (baseMin != null) {
            if (overMin == null || overMin < baseMin) return true;
        }
        // max: override may only lower (narrow). Removing or raising max widens.
        if (baseMax != null) {
            if (overMax == null || overMax > baseMax) return true;
        }
        return false;
    }

    private static Double parse(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static boolean sameFieldDefinition(SchemaField a, SchemaField b) {
        if (!a.fieldType().getClass().equals(b.fieldType().getClass())) return false;
        if (a.required() != b.required()) return false;
        if (a.nullable() != b.nullable()) return false;
        return Objects.equals(a.constraints(), b.constraints());
    }
}
