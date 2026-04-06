package foundation.odin.validation;

import foundation.odin.types.*;

import java.util.*;

public final class SchemaSerializer {

    private SchemaSerializer() {}

    public static String serialize(OdinSchema.SchemaDefinition schema) {
        var sb = new StringBuilder();

        var meta = schema.metadata();
        if (meta != null) {
            sb.append("{$}\n");
            if (meta.id() != null) sb.append("id = \"").append(meta.id()).append("\"\n");
            if (meta.title() != null) sb.append("title = \"").append(meta.title()).append("\"\n");
            if (meta.description() != null) sb.append("description = \"").append(meta.description()).append("\"\n");
            if (meta.version() != null) sb.append("version = \"").append(meta.version()).append("\"\n");
            sb.append('\n');
        }

        for (var imp : schema.imports()) {
            sb.append("@import \"").append(imp.path()).append('"');
            if (imp.alias() != null) sb.append(" as ").append(imp.alias());
            sb.append('\n');
        }
        if (!schema.imports().isEmpty()) sb.append('\n');

        for (var typeEntry : schema.types().entrySet()) {
            var type = typeEntry.getValue();
            sb.append("{@").append(type.name()).append("}\n");
            for (var field : type.fields()) {
                writeSchemaField(sb, field.name(), field);
            }
            sb.append('\n');
        }

        var sections = new LinkedHashMap<String, List<Map.Entry<String, OdinSchema.SchemaField>>>();
        for (var fieldEntry : schema.fields().entrySet()) {
            String path = fieldEntry.getKey();
            int dotPos = path.indexOf('.');
            String section = "";
            if (dotPos > 0) {
                String candidate = path.substring(0, dotPos);
                if (Character.isUpperCase(candidate.charAt(0))) {
                    section = candidate;
                }
            }
            sections.computeIfAbsent(section, k -> new ArrayList<>()).add(fieldEntry);
        }

        for (var sectionEntry : sections.entrySet()) {
            if (!sectionEntry.getKey().isEmpty()) {
                sb.append('{').append(sectionEntry.getKey()).append("}\n");
            }
            for (var fieldEntry : sectionEntry.getValue()) {
                String fieldName = fieldEntry.getKey();
                if (!sectionEntry.getKey().isEmpty()) {
                    fieldName = fieldName.substring(sectionEntry.getKey().length() + 1);
                }
                writeSchemaField(sb, fieldName, fieldEntry.getValue());
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static void writeSchemaField(StringBuilder sb, String name, OdinSchema.SchemaField field) {
        sb.append(name).append(" = ");
        sb.append(formatFieldType(field.fieldType()));

        for (var constraint : field.constraints()) {
            writeConstraint(sb, constraint);
        }

        if (field.required()) sb.append(" :required");
        if (field.confidential()) sb.append(" :confidential");
        if (field.deprecated()) sb.append(" :deprecated");

        sb.append('\n');
    }

    private static void writeConstraint(StringBuilder sb, OdinSchema.SchemaConstraint constraint) {
        if (constraint instanceof OdinSchema.SchemaConstraint.Bounds bounds) {
            if (bounds.min() != null && bounds.max() != null) {
                sb.append(" :(").append(bounds.min()).append("..").append(bounds.max()).append(')');
            } else if (bounds.min() != null) {
                sb.append(" :min ").append(bounds.min());
            } else if (bounds.max() != null) {
                sb.append(" :max ").append(bounds.max());
            }
        } else if (constraint instanceof OdinSchema.SchemaConstraint.Pattern pattern) {
            sb.append(" :pattern \"").append(pattern.pattern()).append('"');
        } else if (constraint instanceof OdinSchema.SchemaConstraint.Enum enumConstraint) {
            sb.append(" :enum(").append(String.join(", ", enumConstraint.values())).append(')');
        } else if (constraint instanceof OdinSchema.SchemaConstraint.Format format) {
            sb.append(" :format ").append(format.formatName());
        } else if (constraint instanceof OdinSchema.SchemaConstraint.Unique) {
            sb.append(" :unique");
        }
    }

    static String formatFieldType(OdinSchema.SchemaFieldType type) {
        if (type instanceof OdinSchema.SchemaFieldType.StringType) return "\"\"";
        if (type instanceof OdinSchema.SchemaFieldType.BooleanType) return "?";
        if (type instanceof OdinSchema.SchemaFieldType.NumberType) return "#";
        if (type instanceof OdinSchema.SchemaFieldType.IntegerType) return "##";
        if (type instanceof OdinSchema.SchemaFieldType.CurrencyType) return "#$";
        if (type instanceof OdinSchema.SchemaFieldType.PercentType) return "#%";
        if (type instanceof OdinSchema.SchemaFieldType.DateType) return ":date";
        if (type instanceof OdinSchema.SchemaFieldType.TimestampType) return ":timestamp";
        if (type instanceof OdinSchema.SchemaFieldType.TimeType) return ":time";
        if (type instanceof OdinSchema.SchemaFieldType.DurationType) return ":duration";
        if (type instanceof OdinSchema.SchemaFieldType.BinaryType) return "^";
        if (type instanceof OdinSchema.SchemaFieldType.NullType) return "~";
        if (type instanceof OdinSchema.SchemaFieldType.TypeRefType ref) return "@" + ref.name();
        return "\"\"";
    }
}
