package foundation.odin.validation;

import foundation.odin.types.*;

import java.util.*;
import java.util.regex.PatternSyntaxException;

public final class ValidationEngine {

    private ValidationEngine() {}

    public static OdinSchema.ValidationResult validate(
            OdinDocument doc, OdinSchema.SchemaDefinition schema, OdinOptions.ValidateOptions options) {
        var opts = options != null ? options : OdinOptions.ValidateOptions.DEFAULT;
        var errors = new ArrayList<OdinSchema.ValidationError>();

        // Validate fields
        for (var fieldEntry : schema.fields().entrySet()) {
            String path = fieldEntry.getKey();
            var schemaField = fieldEntry.getValue();

            // Skip type definition fields (from @TypeName sections)
            if (path.startsWith("@")) continue;

            OdinValue docValue = doc.getAssignments().tryGet(path);

            // Conditional check
            if (!schemaField.conditionals().isEmpty()) {
                boolean conditionMet = evaluateConditionals(doc, schemaField.conditionals(), path);
                if (!conditionMet) continue;
            }

            // Required check (V001)
            if (schemaField.required() && docValue == null) {
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

        // Schema-level reference validation
        validateSchemaReferences(schema, errors);

        // Strict mode (V011)
        if (opts.isStrict()) {
            var strictErrors = validateStrictMode(doc, schema);
            errors.addAll(strictErrors);
        }

        return new OdinSchema.ValidationResult(errors.isEmpty(), errors);
    }

    // ── Type Matching ──

    private static boolean checkTypeMatch(OdinValue value, OdinSchema.SchemaFieldType expectedType) {
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

    private static OdinSchema.ValidationError validatePattern(
            OdinValue value, OdinSchema.SchemaConstraint.Pattern pattern, String path) {
        String strValue = value.asString();
        if (strValue == null) return null;

        var analysis = ReDoSProtection.analyze(pattern.pattern());
        if (!analysis.safe()) {
            return new OdinSchema.ValidationError(path, "V004",
                    "Unsafe regex pattern at " + path + ": " + analysis.reason());
        }

        try {
            var compiled = java.util.regex.Pattern.compile(pattern.pattern());
            if (!compiled.matcher(strValue).matches()) {
                return new OdinSchema.ValidationError(path, "V004", "Pattern mismatch at " + path);
            }
        } catch (PatternSyntaxException e) {
            return new OdinSchema.ValidationError(path, "V004",
                    "Invalid pattern at " + path + ": " + e.getMessage());
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
            if (!evaluateInvariant(doc, inv.expression(), objPath)) {
                return new OdinSchema.ValidationError(objPath, "V008",
                        "Invariant violated: " + inv.expression());
            }
        }

        if (constraint instanceof OdinSchema.SchemaObjectConstraint.UniqueArray) {
            var uniqueError = validateUniqueArray(doc, objPath);
            if (uniqueError != null) return uniqueError;
        }

        return null;
    }

    private static boolean evaluateInvariant(OdinDocument doc, String expression, String objPath) {
        String expr = expression.trim();

        // Try operators in order of precedence (multi-char first)
        String[][] ops = {{">=", ">="}, {"<=", "<="}, {"!=", "!="}, {">", ">"}, {"<", "<"}, {" = ", "="}};
        for (String[] opDef : ops) {
            String search = opDef[0];
            String op = opDef[1];
            int idx = expr.indexOf(search);
            if (idx > 0) {
                String lhs = expr.substring(0, idx).trim();
                String rhs = expr.substring(idx + search.length()).trim();

                double lhsVal = resolveInvariantValue(doc, lhs, objPath);
                double rhsVal = resolveInvariantValue(doc, rhs, objPath);

                if (Double.isNaN(lhsVal) || Double.isNaN(rhsVal)) return true;

                return switch (op) {
                    case ">=" -> lhsVal >= rhsVal;
                    case "<=" -> lhsVal <= rhsVal;
                    case ">" -> lhsVal > rhsVal;
                    case "<" -> lhsVal < rhsVal;
                    case "!=" -> lhsVal != rhsVal;
                    case "=" -> Math.abs(lhsVal - rhsVal) < 0.001;
                    default -> true;
                };
            }
        }
        return true;
    }

    private static double resolveInvariantValue(OdinDocument doc, String expr, String objPath) {
        // Handle arithmetic: "subtotal + tax"
        if (expr.contains(" + ")) {
            String[] parts = expr.split("\\s*\\+\\s*");
            double sum = 0;
            for (String part : parts) {
                double val = resolveInvariantValue(doc, part.trim(), objPath);
                if (Double.isNaN(val)) return Double.NaN;
                sum += val;
            }
            return sum;
        }
        if (expr.contains(" - ")) {
            String[] parts = expr.split("\\s*-\\s*", 2);
            double left = resolveInvariantValue(doc, parts[0].trim(), objPath);
            double right = resolveInvariantValue(doc, parts[1].trim(), objPath);
            if (Double.isNaN(left) || Double.isNaN(right)) return Double.NaN;
            return left - right;
        }

        // Try as number literal
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        // Try as field reference
        String fullPath = objPath.isEmpty() ? expr : objPath + "." + expr;
        OdinValue val = doc.getAssignments().tryGet(fullPath);
        if (val == null) return Double.NaN;

        if (val instanceof OdinValue.OdinInteger i) return (double) i.getValue();
        if (val instanceof OdinValue.OdinNumber n) return n.getValue();
        if (val instanceof OdinValue.OdinCurrency c) return c.getValue();
        if (val instanceof OdinValue.OdinPercent p) return p.getValue();
        return Double.NaN;
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

    private static void validateSchemaReferences(OdinSchema.SchemaDefinition schema,
            List<OdinSchema.ValidationError> errors) {
        // Collect all defined types
        var definedTypes = new LinkedHashSet<String>();
        var typeRefFields = new LinkedHashMap<String, String>(); // path -> referenced type name

        // Types defined via @TypeName sections
        for (String typeName : schema.types().keySet()) {
            definedTypes.add(typeName);
        }

        // Fields that reference types
        for (var entry : schema.fields().entrySet()) {
            if (entry.getValue().fieldType() instanceof OdinSchema.SchemaFieldType.TypeRefType ref) {
                typeRefFields.put(entry.getKey(), ref.name());
            }
        }

        // V013: Check unresolved type references
        for (var entry : typeRefFields.entrySet()) {
            String refTarget = entry.getValue();
            if (!definedTypes.contains(refTarget)) {
                errors.add(new OdinSchema.ValidationError(entry.getKey(), "V013",
                        "Unresolved type reference: @" + refTarget));
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
