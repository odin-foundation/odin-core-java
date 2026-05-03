package foundation.odin.types;

import java.util.*;

public final class OdinSchema {

    // ── Schema Definition ──

    public record SchemaDefinition(
            SchemaMetadata metadata,
            List<SchemaImport> imports,
            Map<String, SchemaType> types,
            Map<String, SchemaField> fields,
            Map<String, SchemaArray> arrays,
            Map<String, List<SchemaObjectConstraint>> objectConstraints
    ) {
        public SchemaDefinition() {
            this(new SchemaMetadata(null, null, null, null), List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    public record SchemaMetadata(String id, String title, String description, String version) {}

    public record SchemaImport(String path, String alias) {}

    // ── Schema Types ──

    public record SchemaType(String name, String description, List<SchemaField> fields, List<String> parents) {
        public SchemaType(String name, List<SchemaField> fields) {
            this(name, null, fields, List.of());
        }
    }

    public record SchemaArray(String name, SchemaFieldType itemType, Long minItems, Long maxItems) {}

    // ── Schema Field ──

    public record SchemaField(
            String name,
            SchemaFieldType fieldType,
            boolean required,
            boolean confidential,
            boolean deprecated,
            boolean immutable,
            String description,
            List<SchemaConstraint> constraints,
            String defaultValue,
            List<SchemaConditional> conditionals
    ) {
        public SchemaField(String name, SchemaFieldType fieldType) {
            this(name, fieldType, false, false, false, false, null, List.of(), null, List.of());
        }
    }

    // ── Schema Field Types ──

    public sealed interface SchemaFieldType permits
            SchemaFieldType.StringType, SchemaFieldType.BooleanType, SchemaFieldType.NullType,
            SchemaFieldType.NumberType, SchemaFieldType.IntegerType, SchemaFieldType.CurrencyType,
            SchemaFieldType.PercentType, SchemaFieldType.DateType, SchemaFieldType.TimestampType,
            SchemaFieldType.TimeType, SchemaFieldType.DurationType,
            SchemaFieldType.EnumType, SchemaFieldType.UnionType,
            SchemaFieldType.ReferenceType, SchemaFieldType.BinaryType,
            SchemaFieldType.TypeRefType {

        record StringType() implements SchemaFieldType {}
        record BooleanType() implements SchemaFieldType {}
        record NullType() implements SchemaFieldType {}
        record NumberType(Byte decimalPlaces) implements SchemaFieldType {}
        record IntegerType() implements SchemaFieldType {}
        record CurrencyType(Byte decimalPlaces) implements SchemaFieldType {}
        record PercentType() implements SchemaFieldType {}
        record DateType() implements SchemaFieldType {}
        record TimestampType() implements SchemaFieldType {}
        record TimeType() implements SchemaFieldType {}
        record DurationType() implements SchemaFieldType {}
        record EnumType(List<String> values) implements SchemaFieldType {}
        record UnionType(List<SchemaFieldType> types) implements SchemaFieldType {}
        record ReferenceType(String target) implements SchemaFieldType {}
        record BinaryType() implements SchemaFieldType {}
        record TypeRefType(String name) implements SchemaFieldType {}
    }

    // ── Schema Constraints ──

    public sealed interface SchemaConstraint permits
            SchemaConstraint.Bounds, SchemaConstraint.Pattern,
            SchemaConstraint.Enum, SchemaConstraint.Unique,
            SchemaConstraint.Size, SchemaConstraint.Format {

        record Bounds(String min, String max, boolean minExclusive, boolean maxExclusive)
                implements SchemaConstraint {}
        record Pattern(String pattern) implements SchemaConstraint {}
        record Enum(List<String> values) implements SchemaConstraint {}
        record Unique() implements SchemaConstraint {}
        record Size(Long min, Long max) implements SchemaConstraint {}
        record Format(String formatName) implements SchemaConstraint {}
    }

    // ── Schema Conditionals ──

    public record SchemaConditional(
            String field,
            ConditionalOperator operator,
            ConditionalValue value,
            boolean unless
    ) {}

    public enum ConditionalOperator { EQ, NOT_EQ, GT, LT, GTE, LTE }

    public sealed interface ConditionalValue permits
            ConditionalValue.StringVal, ConditionalValue.NumberVal, ConditionalValue.BoolVal {
        record StringVal(String value) implements ConditionalValue {}
        record NumberVal(double value) implements ConditionalValue {}
        record BoolVal(boolean value) implements ConditionalValue {}
    }

    // ── Object Constraints ──

    public sealed interface SchemaObjectConstraint permits
            SchemaObjectConstraint.Invariant, SchemaObjectConstraint.Cardinality,
            SchemaObjectConstraint.UniqueArray {
        record Invariant(String expression) implements SchemaObjectConstraint {}
        record Cardinality(List<String> fields, Long min, Long max) implements SchemaObjectConstraint {}
        record UniqueArray() implements SchemaObjectConstraint {}
    }

    // ── Validation Result ──

    public record ValidationResult(boolean valid, List<ValidationError> errors) {
        public ValidationResult() { this(true, List.of()); }
    }

    public record ValidationError(
            String path, String code, String message,
            String expected, String actual, String schemaPath
    ) {
        public ValidationError(String path, String code, String message) {
            this(path, code, message, null, null, null);
        }
    }

    private OdinSchema() {}
}
