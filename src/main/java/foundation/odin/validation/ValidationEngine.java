package foundation.odin.validation;

import foundation.odin.resolver.ImportResolver.TypeRegistry;
import foundation.odin.types.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ValidationEngine {

    private ValidationEngine() {}

    // ── Schema-only memo ──

    // Schema-only results that do not depend on any document: V017 schema-definition
    // errors, V012/V013 type-reference errors, the composition-expanded base field map,
    // and the registry under which they were computed (verified on cache hit).
    private record SchemaMemo(
            TypeRegistry registry,
            List<OdinSchema.ValidationError> schemaErrors,
            Map<String, OdinSchema.SchemaField> baseFields) {}

    // Keyed by schema identity; entries are reused across documents validated against
    // the same schema and registry.
    private static final Map<OdinSchema.SchemaDefinition, SchemaMemo> SCHEMA_MEMO =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static SchemaMemo getSchemaMemo(OdinSchema.SchemaDefinition schema, TypeRegistry registry) {
        var cached = SCHEMA_MEMO.get(schema);
        if (cached != null && cached.registry() == registry) return cached;

        var schemaErrors = new ArrayList<OdinSchema.ValidationError>();
        SchemaDefinitionValidator.validate(schema, registry, schemaErrors);
        validateSchemaReferences(schema, registry, schemaErrors);
        var baseFields = expandBaseFields(schema, registry);

        var memo = new SchemaMemo(registry, schemaErrors, baseFields);
        SCHEMA_MEMO.put(schema, memo);
        return memo;
    }

    // ── Compiled pattern cache ──

    // A pattern string compiled once: SAFE carries a precompiled Pattern; UNSAFE marks a
    // pattern rejected by the ReDoS analysis; INVALID marks a pattern that fails to compile.
    private sealed interface CompiledPattern
            permits SafePattern, UnsafePattern, InvalidPattern {}
    private record SafePattern(Pattern pattern) implements CompiledPattern {}
    private record UnsafePattern(String reason) implements CompiledPattern {}
    private record InvalidPattern(String message) implements CompiledPattern {}

    private static final Map<String, CompiledPattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static CompiledPattern getCompiledPattern(String pattern) {
        return PATTERN_CACHE.computeIfAbsent(pattern, p -> {
            var analysis = ReDoSProtection.analyze(p);
            if (!analysis.safe()) return new UnsafePattern(analysis.reason());
            try {
                return new SafePattern(Pattern.compile(p));
            } catch (PatternSyntaxException e) {
                return new InvalidPattern(e.getMessage());
            }
        });
    }

    public static OdinSchema.ValidationResult validate(
            OdinDocument doc, OdinSchema.SchemaDefinition schema, OdinOptions.ValidateOptions options) {
        return validate(doc, schema, options, null);
    }

    public static OdinSchema.ValidationResult validate(
            OdinDocument doc, OdinSchema.SchemaDefinition schema, OdinOptions.ValidateOptions options,
            TypeRegistry registry) {
        var opts = options != null ? options : OdinOptions.ValidateOptions.DEFAULT;
        var errors = new ArrayList<OdinSchema.ValidationError>();

        // Schema-only results (V017 definition + V012/V013 references + base field map)
        // are computed once per schema and reused across documents.
        var memo = getSchemaMemo(schema, registry);

        // Append copies of the cached schema-level errors (callers may mutate results).
        for (var error : memo.schemaErrors()) {
            errors.add(new OdinSchema.ValidationError(error.path(), error.code(), error.message(),
                    error.expected(), error.actual(), error.schemaPath()));
        }
        if (opts.isFailFast() && !errors.isEmpty()) {
            return new OdinSchema.ValidationResult(false, errors);
        }

        // Augment cached schema fields with document-dependent field-level typeRef compositions.
        var expandedFields = augmentWithPresentCompositions(doc, schema, registry, memo.baseFields());

        // Validate fields
        for (var fieldEntry : expandedFields.entrySet()) {
            String path = fieldEntry.getKey();
            var schemaField = fieldEntry.getValue();

            // Skip type definition / composition markers
            if (path.startsWith("@") || path.endsWith("._composition")) continue;

            OdinValue docValue = doc.getAssignments().tryGet(path);

            // Conditional check
            if (!schemaField.conditionals().isEmpty()) {
                boolean conditionMet = evaluateConditionals(doc, schemaField.conditionals(), path);
                if (!conditionMet) continue;
            }

            // Required check (V001)
            if (schemaField.required() && docValue == null) {
                // Computed fields are produced downstream, not supplied as input
                if (schemaField.computed()) continue;
                // Check if this is an array element field pattern (e.g., items.name in {items[]} section)
                // If the field is inside an array, don't flag as missing — array validation handles it
                boolean isArrayField = false;
                for (String arrayPath : schema.arrays().keySet()) {
                    if (path.startsWith(arrayPath + ".")) {
                        isArrayField = true;
                        break;
                    }
                }
                if (!isArrayField) {
                    String code = schemaField.conditionals().isEmpty() ? "V001" : "V010";
                    errors.add(new OdinSchema.ValidationError(path, code, "Required field missing: " + path));
                    if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                }
                continue;
            }

            if (docValue == null) continue;

            // Type check (V002)
            if (!checkTypeMatch(docValue, schemaField.fieldType())) {
                errors.add(new OdinSchema.ValidationError(path, "V002",
                        "Type mismatch at " + path + ": expected " + schemaField.fieldType() + ", got " + docValue.getType()));
                if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                continue;
            }

            // Decimal precision: #.N enforces exactly N places (V003)
            if (schemaField.fieldType() instanceof OdinSchema.SchemaFieldType.NumberType nt
                    && nt.decimalPlaces() != null && docValue instanceof OdinValue.OdinNumber num) {
                String raw = num.getRaw() != null ? num.getRaw() : String.valueOf(num.getValue());
                int dot = raw.indexOf('.');
                int actualPlaces = dot < 0 ? 0 : raw.length() - dot - 1;
                if (actualPlaces != (nt.decimalPlaces() & 0xFF)) {
                    errors.add(new OdinSchema.ValidationError(path, "V003",
                            "Decimal places mismatch at " + path + ": expected exactly " + (nt.decimalPlaces() & 0xFF)));
                    if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                    continue;
                }
            }

            // Currency precision: #$.N enforces exactly N places (V003); bare #$ defaults to 2.
            if (schemaField.fieldType() instanceof OdinSchema.SchemaFieldType.CurrencyType ct
                    && ct.decimalPlaces() != null && docValue instanceof OdinValue.OdinCurrency cur) {
                int expectedPlaces = ct.decimalPlaces() & 0xFF;
                int actualPlaces = cur.getDecimalPlaces() & 0xFF;
                if (actualPlaces != expectedPlaces) {
                    errors.add(new OdinSchema.ValidationError(path, "V003",
                            "Currency decimal places mismatch at " + path + ": expected exactly " + expectedPlaces));
                    if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                    continue;
                }
            }

            // Constraint validation
            for (var constraint : schemaField.constraints()) {
                var error = validateConstraint(docValue, constraint, path);
                if (error != null) {
                    errors.add(error);
                    if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                }
            }
        }

        // Array validation (V006)
        for (var arrayEntry : schema.arrays().entrySet()) {
            var arrayErrors = validateArray(doc, arrayEntry.getKey(), arrayEntry.getValue());
            errors.addAll(arrayErrors);
            if (opts.isFailFast() && !errors.isEmpty()) return new OdinSchema.ValidationResult(false, errors);
        }

        // Object-level constraints
        for (var objEntry : schema.objectConstraints().entrySet()) {
            String objPath = objEntry.getKey();
            for (var constraint : objEntry.getValue()) {
                var error = validateObjectConstraint(doc, constraint, objPath, schema);
                if (error != null) {
                    errors.add(error);
                    if (opts.isFailFast()) return new OdinSchema.ValidationResult(false, errors);
                }
            }
        }

        // Reference validation (V012, V013)
        if (opts.isValidateReferences()) {
            validateReferences(doc, errors);
        }

        // Strict mode (V011)
        if (opts.isStrict()) {
            var strictErrors = validateStrictMode(doc, schema);
            errors.addAll(strictErrors);
        }

        return new OdinSchema.ValidationResult(errors.isEmpty(), errors);
    }

    // ── Type Matching ──

    private static boolean checkTypeMatch(OdinValue value, OdinSchema.SchemaFieldType expectedType) {
        // A union accepts a value matching any of its members.
        if (expectedType instanceof OdinSchema.SchemaFieldType.UnionType union) {
            if (value instanceof OdinValue.OdinNull) {
                return union.types().stream()
                        .anyMatch(t -> t instanceof OdinSchema.SchemaFieldType.NullType);
            }
            for (var member : union.types()) {
                if (checkTypeMatch(value, member)) return true;
            }
            return false;
        }

        if (value instanceof OdinValue.OdinNull) return true;

        // String type also accepts date/timestamp when format constraint is used
        if (expectedType instanceof OdinSchema.SchemaFieldType.StringType)
            return value instanceof OdinValue.OdinString
                    || value instanceof OdinValue.OdinDate
                    || value instanceof OdinValue.OdinTimestamp
                    || value instanceof OdinValue.OdinTime;
        if (expectedType instanceof OdinSchema.SchemaFieldType.BooleanType)
            return value instanceof OdinValue.OdinBoolean;
        if (expectedType instanceof OdinSchema.SchemaFieldType.IntegerType)
            return value instanceof OdinValue.OdinInteger;
        if (expectedType instanceof OdinSchema.SchemaFieldType.NumberType)
            return value instanceof OdinValue.OdinNumber || value instanceof OdinValue.OdinInteger;
        if (expectedType instanceof OdinSchema.SchemaFieldType.CurrencyType)
            return value instanceof OdinValue.OdinCurrency;
        if (expectedType instanceof OdinSchema.SchemaFieldType.PercentType)
            return value instanceof OdinValue.OdinPercent;
        if (expectedType instanceof OdinSchema.SchemaFieldType.DateType)
            return value instanceof OdinValue.OdinDate;
        if (expectedType instanceof OdinSchema.SchemaFieldType.TimestampType)
            return value instanceof OdinValue.OdinTimestamp;
        if (expectedType instanceof OdinSchema.SchemaFieldType.TimeType)
            return value instanceof OdinValue.OdinTime;
        if (expectedType instanceof OdinSchema.SchemaFieldType.DurationType)
            return value instanceof OdinValue.OdinDuration;
        if (expectedType instanceof OdinSchema.SchemaFieldType.BinaryType)
            return value instanceof OdinValue.OdinBinary;
        if (expectedType instanceof OdinSchema.SchemaFieldType.NullType)
            return value instanceof OdinValue.OdinNull;
        if (expectedType instanceof OdinSchema.SchemaFieldType.TypeRefType)
            return true;

        return true;
    }

    // ── Constraint Validation ──

    private static OdinSchema.ValidationError validateConstraint(
            OdinValue value, OdinSchema.SchemaConstraint constraint, String path) {

        if (constraint instanceof OdinSchema.SchemaConstraint.Bounds bounds)
            return validateBounds(value, bounds, path);
        if (constraint instanceof OdinSchema.SchemaConstraint.Pattern pattern)
            return validatePattern(value, pattern, path);
        if (constraint instanceof OdinSchema.SchemaConstraint.Enum enumConstraint)
            return validateEnum(value, enumConstraint, path);
        if (constraint instanceof OdinSchema.SchemaConstraint.Format format)
            return validateFormat(value, format, path);

        return null;
    }

    private static OdinSchema.ValidationError validateBounds(
            OdinValue value, OdinSchema.SchemaConstraint.Bounds bounds, String path) {
        // Temporal bounds: compare chronologically against ISO literals.
        if (value instanceof OdinValue.OdinDate || value instanceof OdinValue.OdinTimestamp) {
            return validateTemporalBounds(value, bounds, path);
        }

        // Byte-length bounds for binary values.
        if (value instanceof OdinValue.OdinBinary bin) {
            int len = bin.getData().length;
            if (bounds.min() != null) {
                try {
                    if (len < Long.parseLong(bounds.min())) {
                        return new OdinSchema.ValidationError(path, "V003",
                                "Binary size out of bounds at " + path + ": " + len + " < " + bounds.min());
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (bounds.max() != null) {
                try {
                    if (len > Long.parseLong(bounds.max())) {
                        return new OdinSchema.ValidationError(path, "V003",
                                "Binary size out of bounds at " + path + ": " + len + " > " + bounds.max());
                    }
                } catch (NumberFormatException ignored) {}
            }
            return null;
        }

        Double numValue = null;

        if (value instanceof OdinValue.OdinInteger i) numValue = (double) i.getValue();
        else if (value instanceof OdinValue.OdinNumber n) numValue = n.getValue();
        else if (value instanceof OdinValue.OdinCurrency c) numValue = c.getValue();
        else if (value instanceof OdinValue.OdinPercent p) numValue = p.getValue();
        else if (value instanceof OdinValue.OdinString s) numValue = (double) s.getValue().length();

        if (numValue == null) return null;

        if (bounds.min() != null) {
            try {
                double min = Double.parseDouble(bounds.min());
                boolean violated = bounds.minExclusive() ? numValue <= min : numValue < min;
                if (violated) {
                    return new OdinSchema.ValidationError(path, "V003",
                            "Value out of bounds at " + path + ": " + numValue + " < " + min);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (bounds.max() != null) {
            try {
                double max = Double.parseDouble(bounds.max());
                boolean violated = bounds.maxExclusive() ? numValue >= max : numValue > max;
                if (violated) {
                    return new OdinSchema.ValidationError(path, "V003",
                            "Value out of bounds at " + path + ": " + numValue + " > " + max);
                }
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    private static OdinSchema.ValidationError validateTemporalBounds(
            OdinValue value, OdinSchema.SchemaConstraint.Bounds bounds, String path) {
        Long actual = temporalToEpochMs(value);
        if (actual == null) return null;

        if (bounds.min() != null) {
            Long min = temporalToEpochMs(bounds.min());
            if (min != null && actual < min) {
                return new OdinSchema.ValidationError(path, "V003",
                        "Date below minimum at " + path + ": " + bounds.min());
            }
        }
        if (bounds.max() != null) {
            Long max = temporalToEpochMs(bounds.max());
            if (max != null && actual > max) {
                return new OdinSchema.ValidationError(path, "V003",
                        "Date above maximum at " + path + ": " + bounds.max());
            }
        }
        return null;
    }

    private static Long temporalToEpochMs(OdinValue value) {
        if (value instanceof OdinValue.OdinTimestamp ts) return ts.getEpochMs();
        if (value instanceof OdinValue.OdinDate d) return temporalToEpochMs(d.getRaw());
        return null;
    }

    // Parse an ISO date/timestamp literal to epoch milliseconds for chronological compare.
    private static Long temporalToEpochMs(String iso) {
        if (iso == null) return null;
        String s = iso.trim();
        try {
            if (s.length() == 10) { // yyyy-MM-dd
                return java.time.LocalDate.parse(s)
                        .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            try {
                return java.time.Instant.parse(s).toEpochMilli();
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    private static OdinSchema.ValidationError validatePattern(
            OdinValue value, OdinSchema.SchemaConstraint.Pattern pattern, String path) {
        String strValue = value.asString();
        if (strValue == null) return null;

        // ReDoS analysis and compilation run once per distinct pattern string.
        var compiled = getCompiledPattern(pattern.pattern());

        if (compiled instanceof UnsafePattern unsafe) {
            return new OdinSchema.ValidationError(path, "V004",
                    "Unsafe regex pattern at " + path + ": " + unsafe.reason());
        }
        if (compiled instanceof InvalidPattern invalid) {
            return new OdinSchema.ValidationError(path, "V004",
                    "Invalid pattern at " + path + ": " + invalid.message());
        }

        var regex = ((SafePattern) compiled).pattern();
        if (!regex.matcher(strValue).matches()) {
            return new OdinSchema.ValidationError(path, "V004", "Pattern mismatch at " + path);
        }

        return null;
    }

    private static OdinSchema.ValidationError validateEnum(
            OdinValue value, OdinSchema.SchemaConstraint.Enum enumConstraint, String path) {
        String strValue = value.asString();
        if (strValue == null) return null;

        if (!enumConstraint.values().contains(strValue)) {
            return new OdinSchema.ValidationError(path, "V005", "Invalid enum value at " + path + ": " + strValue);
        }
        return null;
    }

    private static OdinSchema.ValidationError validateFormat(
            OdinValue value, OdinSchema.SchemaConstraint.Format format, String path) {
        String strValue = value.asString();
        if (strValue == null) return null;

        if (!FormatValidators.validate(strValue, format.formatName())) {
            return new OdinSchema.ValidationError(path, "V004",
                    "Format validation failed at " + path + " for format: " + format.formatName());
        }
        return null;
    }

    // ── Array Validation ──

    private static List<OdinSchema.ValidationError> validateArray(
            OdinDocument doc, String arrayPath, OdinSchema.SchemaArray schemaArray) {
        var errors = new ArrayList<OdinSchema.ValidationError>();
        int count = 0;

        String prefix = arrayPath + "[";
        for (var entry : doc.getAssignments().entries()) {
            String path = entry.getKey();
            if (path.startsWith(prefix)) {
                int bracketEnd = path.indexOf(']', prefix.length());
                if (bracketEnd >= 0) {
                    try {
                        int idx = Integer.parseInt(path.substring(prefix.length(), bracketEnd));
                        if (idx >= count) count = idx + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (schemaArray.minItems() != null && count < schemaArray.minItems()) {
            errors.add(new OdinSchema.ValidationError(arrayPath, "V006",
                    "Array " + arrayPath + " has " + count + " items, minimum is " + schemaArray.minItems()));
        }

        if (schemaArray.maxItems() != null && count > schemaArray.maxItems()) {
            errors.add(new OdinSchema.ValidationError(arrayPath, "V006",
                    "Array " + arrayPath + " has " + count + " items, maximum is " + schemaArray.maxItems()));
        }

        return errors;
    }

    // ── Object Constraints ──

    private static OdinSchema.ValidationError validateObjectConstraint(
            OdinDocument doc, OdinSchema.SchemaObjectConstraint constraint,
            String objPath, OdinSchema.SchemaDefinition schema) {

        if (constraint instanceof OdinSchema.SchemaObjectConstraint.Cardinality card) {
            long presentCount = card.fields().stream()
                    .filter(f -> {
                        String fullPath = objPath.isEmpty() ? f : objPath + "." + f;
                        return doc.getAssignments().tryGet(fullPath) != null;
                    })
                    .count();

            if (card.min() != null && presentCount < card.min()) {
                return new OdinSchema.ValidationError(objPath, "V009",
                        "Cardinality constraint violated: at least " + card.min() + " required from: " + card.fields());
            }
            if (card.max() != null && presentCount > card.max()) {
                return new OdinSchema.ValidationError(objPath, "V009",
                        "Cardinality constraint violated: at most " + card.max() + " allowed from: " + card.fields());
            }
        }

        if (constraint instanceof OdinSchema.SchemaObjectConstraint.Invariant inv) {
            return validateInvariant(doc, inv.expression(), objPath);
        }

        if (constraint instanceof OdinSchema.SchemaObjectConstraint.UniqueArray) {
            var uniqueError = validateUniqueArray(doc, objPath);
            if (uniqueError != null) return uniqueError;
        }

        return null;
    }

    // Evaluate an invariant expression. Absent operands make it inapplicable;
    // a null operand or a false result is a V008 violation; a malformed expression is V008.
    private static OdinSchema.ValidationError validateInvariant(
            OdinDocument doc, String expression, String objPath) {
        String expr = expression.trim();

        InvariantEvaluator.FieldResolver resolve = name -> {
            String fullPath = objPath.isEmpty() ? name : objPath + "." + name;
            return doc.getAssignments().tryGet(fullPath);
        };

        InvariantEvaluator.InvariantResult result;
        try {
            result = InvariantEvaluator.evaluate(expr, resolve);
        } catch (RuntimeException e) {
            return new OdinSchema.ValidationError(objPath, "V008",
                    "Invalid invariant expression: " + expr);
        }

        // Absent operands: invariant does not apply.
        if (result.value() == null && !result.nullOperand()) return null;

        if (Boolean.FALSE.equals(result.value())) {
            return new OdinSchema.ValidationError(objPath, "V008",
                    "Invariant violated: " + expr);
        }
        return null;
    }

    private static OdinSchema.ValidationError validateUniqueArray(OdinDocument doc, String arrayPath) {
        // Collect array element composite keys (all fields for each index)
        String prefix = arrayPath + "[";
        var elementValues = new LinkedHashMap<Integer, StringBuilder>();

        for (var entry : doc.getAssignments().entries()) {
            String path = entry.getKey();
            if (path.startsWith(prefix)) {
                int bracketEnd = path.indexOf(']', prefix.length());
                if (bracketEnd >= 0) {
                    try {
                        int idx = Integer.parseInt(path.substring(prefix.length(), bracketEnd));
                        String strVal = valueToString(entry.getValue());
                        elementValues.computeIfAbsent(idx, k -> new StringBuilder()).append(strVal).append("|");
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        var seen = new LinkedHashSet<String>();
        for (var elem : elementValues.values()) {
            String key = elem.toString();
            if (!seen.add(key)) {
                return new OdinSchema.ValidationError(arrayPath, "V007",
                        "Duplicate value in unique array");
            }
        }
        return null;
    }

    // ── Reference Validation ──

    private static void validateReferences(OdinDocument doc, List<OdinSchema.ValidationError> errors) {
        var refPaths = new LinkedHashMap<String, String>(); // path -> target

        for (var entry : doc.getAssignments().entries()) {
            if (entry.getValue() instanceof OdinValue.OdinReference ref) {
                refPaths.put(entry.getKey(), ref.getPath());
            }
        }

        // Check unresolved references (V013)
        for (var entry : refPaths.entrySet()) {
            String target = entry.getValue();
            if (!doc.has(target)) {
                errors.add(new OdinSchema.ValidationError(entry.getKey(), "V013",
                        "Unresolved reference: " + target));
            }
        }

        // Check circular references (V012)
        for (var entry : refPaths.entrySet()) {
            var visited = new LinkedHashSet<String>();
            String current = entry.getKey();
            while (current != null) {
                if (!visited.add(current)) {
                    errors.add(new OdinSchema.ValidationError(entry.getKey(), "V012",
                            "Circular reference detected at: " + entry.getKey()));
                    break;
                }
                current = refPaths.get(current);
                if (current == null) {
                    // target might itself be a ref path
                    String target = refPaths.get(visited.getLast());
                    if (target != null && refPaths.containsKey(target)) {
                        current = target;
                    }
                }
            }
        }
    }

    // ── Conditional Evaluation ──

    private static boolean evaluateConditionals(OdinDocument doc,
            List<OdinSchema.SchemaConditional> conditionals, String fieldPath) {
        String objPath = "";
        int lastDot = fieldPath.lastIndexOf('.');
        if (lastDot > 0) objPath = fieldPath.substring(0, lastDot);

        for (var cond : conditionals) {
            String condFieldPath = objPath.isEmpty() ? cond.field() : objPath + "." + cond.field();
            OdinValue condFieldValue = doc.getAssignments().tryGet(condFieldPath);

            boolean matches = false;
            if (condFieldValue != null) {
                String actual = valueToString(condFieldValue);
                String expected = conditionValueToString(cond.value());
                matches = switch (cond.operator()) {
                    case EQ -> actual.equals(expected);
                    case NOT_EQ -> !actual.equals(expected);
                    case GT -> compareNumeric(actual, expected) > 0;
                    case LT -> compareNumeric(actual, expected) < 0;
                    case GTE -> compareNumeric(actual, expected) >= 0;
                    case LTE -> compareNumeric(actual, expected) <= 0;
                };
            }

            if (cond.unless()) matches = !matches;
            if (!matches) return false;
        }
        return true;
    }

    private static String conditionValueToString(OdinSchema.ConditionalValue value) {
        if (value instanceof OdinSchema.ConditionalValue.StringVal s) return s.value();
        if (value instanceof OdinSchema.ConditionalValue.NumberVal n) return String.valueOf(n.value());
        if (value instanceof OdinSchema.ConditionalValue.BoolVal b) return String.valueOf(b.value());
        return "";
    }

    private static int compareNumeric(String a, String b) {
        try {
            return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private static String valueToString(OdinValue value) {
        if (value instanceof OdinValue.OdinString s) return s.getValue();
        if (value instanceof OdinValue.OdinInteger i) return String.valueOf(i.getValue());
        if (value instanceof OdinValue.OdinNumber n) return String.valueOf(n.getValue());
        if (value instanceof OdinValue.OdinBoolean b) return String.valueOf(b.getValue());
        if (value instanceof OdinValue.OdinCurrency c) return String.valueOf(c.getValue());
        return value.toString();
    }

    // ── Schema Reference Validation ──

    // Build the document-independent base field map: schema fields plus fields
    // contributed by object-level type compositions (parent._composition = @a & @b).
    // Computed once per schema and cached in the schema memo.
    private static Map<String, OdinSchema.SchemaField> expandBaseFields(
            OdinSchema.SchemaDefinition schema, TypeRegistry registry) {
        var result = new LinkedHashMap<String, OdinSchema.SchemaField>();

        // Pass 1: object-level compositions (parent._composition = @a & @b).
        for (var entry : schema.fields().entrySet()) {
            String path = entry.getKey();
            var field = entry.getValue();
            if (path.endsWith("._composition")
                    && field.fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref) {
                String parent = path.substring(0, path.length() - "._composition".length());
                for (String member : ref.name().split("&")) {
                    var type = lookupType(schema, registry, member.trim());
                    if (type != null) mergeTypeFields(result, parent, type);
                }
            }
        }

        // Pass 2: explicit schema fields (override inherited).
        for (var entry : schema.fields().entrySet()) {
            if (entry.getKey().endsWith("._composition")) continue;
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    // Augment the cached base field map with field-level typeRef compositions that
    // depend on the document: a field typed @SomeType enforces that type's fields under
    // the field path when the sub-object is present or the field is required. Returns a
    // fresh map; the cached base map is never mutated.
    private static Map<String, OdinSchema.SchemaField> augmentWithPresentCompositions(
            OdinDocument doc, OdinSchema.SchemaDefinition schema, TypeRegistry registry,
            Map<String, OdinSchema.SchemaField> baseFields) {
        var result = new LinkedHashMap<>(baseFields);

        for (var entry : baseFields.entrySet()) {
            String path = entry.getKey();
            var field = entry.getValue();
            if (!(field.fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref)) continue;

            var types = new ArrayList<OdinSchema.SchemaType>();
            for (String member : ref.name().split("&")) {
                var t = lookupType(schema, registry, member.trim());
                if (t != null) types.add(t);
            }
            if (types.isEmpty()) continue; // runtime reference, not a defined type

            if (!isObjectPresent(doc, path) && !field.required()) continue;

            for (var type : types) {
                for (var typeField : type.fields()) {
                    if ("_composition".equals(typeField.name())) continue;
                    String fullPath = path + "." + typeField.name();
                    result.putIfAbsent(fullPath, renamed(typeField, fullPath));
                }
            }
        }

        return result;
    }

    private static void mergeTypeFields(Map<String, OdinSchema.SchemaField> result,
            String parent, OdinSchema.SchemaType type) {
        for (var typeField : type.fields()) {
            if ("_composition".equals(typeField.name())) continue;
            String fullPath = parent + "." + typeField.name();
            result.put(fullPath, renamed(typeField, fullPath));
        }
    }

    private static OdinSchema.SchemaField renamed(OdinSchema.SchemaField f, String path) {
        return new OdinSchema.SchemaField(path, f.fieldType(), f.required(), f.confidential(),
                f.deprecated(), f.immutable(), f.description(), f.constraints(),
                f.defaultValue(), f.conditionals(), f.computed(), f.nullable());
    }

    // An object at path is present when the path or any descendant holds a value.
    private static boolean isObjectPresent(OdinDocument doc, String path) {
        if (doc.getAssignments().tryGet(path) != null) return true;
        String prefix = path + ".";
        for (var key : doc.getAssignments().keys()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    // Resolve a type name via the import registry first, then local schema types.
    private static OdinSchema.SchemaType lookupType(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, String name) {
        if (registry != null) {
            var fromRegistry = registry.lookup(name);
            if (fromRegistry != null) return fromRegistry;
        }
        return schema.types().get(name);
    }

    private static void validateSchemaReferences(OdinSchema.SchemaDefinition schema,
            TypeRegistry registry, List<OdinSchema.ValidationError> errors) {
        var typeRefFields = new LinkedHashMap<String, String>(); // path -> referenced type name

        // Fields that reference types
        for (var entry : schema.fields().entrySet()) {
            if (entry.getValue().fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref) {
                typeRefFields.put(entry.getKey(), ref.name());
            }
        }

        // V013: Check unresolved type references (registry resolves @alias.typename).
        // An intersection typeRef carries multiple &-joined member names.
        for (var entry : typeRefFields.entrySet()) {
            for (String member : entry.getValue().split("&")) {
                String refTarget = member.trim();
                if (refTarget.isEmpty()) continue;
                if (lookupType(schema, registry, refTarget) == null) {
                    errors.add(new OdinSchema.ValidationError(entry.getKey(), "V013",
                            "Unresolved type reference: @" + refTarget));
                }
            }
        }

        // V012: Check circular type references
        // Build a graph from type definitions in types()
        var typeGraph = new LinkedHashMap<String, List<String>>();
        for (var typeEntry : schema.types().entrySet()) {
            String typeName = typeEntry.getKey();
            for (var field : typeEntry.getValue().fields()) {
                if (field.fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref) {
                    String target = ref.name();
                    typeGraph.computeIfAbsent(typeName, k -> new ArrayList<>()).add(target);
                }
            }
        }
        // Also check fields referencing types
        for (var entry : schema.fields().entrySet()) {
            if (entry.getValue().fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref) {
                String target = ref.name();
                // This field references a type — not a cycle source, just for unresolved checks
            }
        }

        // Detect cycles via DFS (report only first cycle found per connected component)
        var reportedCycles = new LinkedHashSet<String>();
        for (String type : typeGraph.keySet()) {
            if (reportedCycles.contains(type)) continue;
            var cyclePath = new LinkedHashSet<String>();
            if (hasCycle(type, typeGraph, cyclePath, reportedCycles)) {
                errors.add(new OdinSchema.ValidationError("@" + type, "V012",
                        "Circular type reference detected: @" + type));
            }
        }
    }

    private static boolean hasCycle(String current, Map<String, List<String>> graph,
            Set<String> path, Set<String> reported) {
        if (!path.add(current)) {
            reported.addAll(path);
            return true;
        }
        var refs = graph.get(current);
        if (refs != null) {
            for (String ref : refs) {
                if (hasCycle(ref, graph, path, reported)) return true;
            }
        }
        path.remove(current);
        return false;
    }

    // ── Strict Mode ──

    private static List<OdinSchema.ValidationError> validateStrictMode(
            OdinDocument doc, OdinSchema.SchemaDefinition schema) {
        var errors = new ArrayList<OdinSchema.ValidationError>();

        for (var entry : doc.getAssignments().entries()) {
            String path = entry.getKey();
            if (path.startsWith("$.")) continue;

            boolean known = schema.fields().containsKey(path);
            if (!known) {
                boolean isArray = false;
                for (String arrayPath : schema.arrays().keySet()) {
                    if (path.startsWith(arrayPath + "[")) { isArray = true; break; }
                }
                if (!isArray) {
                    errors.add(new OdinSchema.ValidationError(path, "V011", "Unknown field: " + path));
                }
            }
        }

        return errors;
    }
}
