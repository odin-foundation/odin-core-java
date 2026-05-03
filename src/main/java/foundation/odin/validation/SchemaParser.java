package foundation.odin.validation;

import foundation.odin.types.*;

import java.util.*;

public final class SchemaParser {

    private SchemaParser() {}

    public static OdinSchema.SchemaDefinition parse(String schemaText) {
        var types = new LinkedHashMap<String, OdinSchema.SchemaType>();
        var fields = new LinkedHashMap<String, OdinSchema.SchemaField>();
        var arrays = new LinkedHashMap<String, OdinSchema.SchemaArray>();
        var objectConstraints = new LinkedHashMap<String, List<OdinSchema.SchemaObjectConstraint>>();
        var imports = new ArrayList<OdinSchema.SchemaImport>();
        OdinSchema.SchemaMetadata metadata = null;

        String currentHeader = null;
        boolean currentIsArray = false;
        boolean inMetadata = false;
        String metaId = null, metaTitle = null, metaDescription = null, metaVersion = null;

        String[] lines = schemaText.split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;

            // Header: {$}, {SectionName}, {items[]}, {@TypeName}
            if (line.startsWith("{") && line.endsWith("}")) {
                String headerContent = line.substring(1, line.length() - 1);

                if (headerContent.equals("$") || headerContent.startsWith("$")) {
                    inMetadata = true;
                    currentHeader = null;
                    currentIsArray = false;
                    continue;
                }

                inMetadata = false;
                if (headerContent.endsWith("[]")) {
                    currentHeader = headerContent.substring(0, headerContent.length() - 2);
                    currentIsArray = true;
                    // Ensure array entry exists (may be updated by bounds later)
                    arrays.putIfAbsent(currentHeader, new OdinSchema.SchemaArray(currentHeader,
                            new OdinSchema.SchemaFieldType.StringType(), null, null));
                } else {
                    currentHeader = headerContent;
                    currentIsArray = false;
                    // Track @TypeName sections as type definitions
                    if (headerContent.startsWith("@")) {
                        String typeName = headerContent.substring(1);
                        // Handle inheritance: @Extended : @Base
                        int colonIdx = typeName.indexOf(" : ");
                        if (colonIdx >= 0) {
                            typeName = typeName.substring(0, colonIdx).trim();
                        }
                        types.putIfAbsent(typeName, new OdinSchema.SchemaType(typeName, new ArrayList<>()));
                    }
                }
                continue;
            }

            // @import directives: @import ./path.odin as alias
            if (line.startsWith("@import ")) {
                String rest = line.substring(8).trim();
                String importPath;
                String alias;
                int asIdx = rest.indexOf(" as ");
                if (asIdx >= 0) {
                    importPath = rest.substring(0, asIdx).trim();
                    alias = rest.substring(asIdx + 4).trim();
                } else {
                    importPath = rest;
                    alias = rest;
                }
                imports.add(new OdinSchema.SchemaImport(importPath, alias));
                continue;
            }

            // Bare @TypeName lines (type definitions without {} wrapper)
            if (line.startsWith("@") && !line.contains("=")) {
                String typeLine = line.substring(1).trim();
                // Handle inheritance: @Extended : @Base
                int colonIdx = typeLine.indexOf(" : ");
                String typeName = colonIdx >= 0 ? typeLine.substring(0, colonIdx).trim() : typeLine;
                types.putIfAbsent(typeName, new OdinSchema.SchemaType(typeName, new ArrayList<>()));
                currentHeader = line; // Use @TypeName as context for subsequent field lines
                currentIsArray = false;
                inMetadata = false;
                continue;
            }

            // Object-level constraints: :invariant, :of, :one_of, :exactly_one, :unique, :(bounds)
            if (line.startsWith(":invariant ") || line.startsWith(":of ") ||
                    line.startsWith(":one_of ") || line.startsWith(":exactly_one ") ||
                    line.equals(":unique") || (line.startsWith(":(") && !line.contains("="))) {
                String objPath = currentHeader != null ? currentHeader : "";
                parseObjectLevelConstraint(line, objPath, objectConstraints, arrays, currentIsArray);
                continue;
            }

            // Field assignment
            int eqPos = findAssignmentEquals(line);
            if (eqPos > 0) {
                String fieldName = line.substring(0, eqPos).trim();
                String valueSpec = line.substring(eqPos + 1).trim();

                // Remove surrounding quotes
                if (valueSpec.startsWith("\"") && valueSpec.endsWith("\"")) {
                    valueSpec = valueSpec.substring(1, valueSpec.length() - 1);
                }

                if (inMetadata) {
                    switch (fieldName) {
                        case "id" -> metaId = valueSpec;
                        case "title" -> metaTitle = valueSpec;
                        case "description" -> metaDescription = valueSpec;
                        case "version" -> metaVersion = valueSpec;
                    }
                    continue;
                }

                // Strip array indicator from field names
                String cleanFieldName = fieldName.endsWith("[]") ? fieldName.substring(0, fieldName.length() - 2) : fieldName;
                var field = parseFieldSpec(cleanFieldName, valueSpec);
                String headerPrefix = currentHeader != null ? currentHeader : "";
                boolean isTypeDefinition = headerPrefix.startsWith("@") || headerPrefix.startsWith("{@");
                if (isTypeDefinition) {
                    // Add to type definition's fields list
                    String typeName = headerPrefix.startsWith("@") ? headerPrefix.substring(1) : headerPrefix;
                    var typeEntry = types.get(typeName);
                    if (typeEntry != null) {
                        typeEntry.fields().add(field);
                    }
                    continue;
                }
                String fullPath;
                if (!headerPrefix.isEmpty()) {
                    fullPath = headerPrefix + "." + cleanFieldName;
                } else {
                    fullPath = cleanFieldName;
                }
                fields.put(fullPath, field);
            }
        }

        metadata = new OdinSchema.SchemaMetadata(metaId, metaTitle, metaDescription, metaVersion);
        return new OdinSchema.SchemaDefinition(metadata, imports, types, fields, arrays, objectConstraints);
    }

    private static int findAssignmentEquals(String line) {
        // Find the first '=' that is an assignment (not inside a constraint like :if x = y)
        // Simple heuristic: find the first '=' in the line
        int eqPos = line.indexOf('=');
        if (eqPos <= 0) return -1;
        // Check if it's preceded by ':if' or similar — in that case, use first '='
        return eqPos;
    }

    private static void parseObjectLevelConstraint(String line, String objPath,
            Map<String, List<OdinSchema.SchemaObjectConstraint>> objectConstraints,
            Map<String, OdinSchema.SchemaArray> arrays, boolean isArray) {

        String s = line.trim();

        // Array bounds: :(min..max) on array headers
        if (isArray && s.startsWith(":(")) {
            int closeParen = s.indexOf(')');
            if (closeParen > 2) {
                String boundsStr = s.substring(2, closeParen);
                int dotDot = boundsStr.indexOf("..");
                Long min = null, max = null;
                if (dotDot >= 0) {
                    String minStr = boundsStr.substring(0, dotDot).trim();
                    String maxStr = boundsStr.substring(dotDot + 2).trim();
                    if (!minStr.isEmpty()) min = Long.parseLong(minStr);
                    if (!maxStr.isEmpty()) max = Long.parseLong(maxStr);
                }
                arrays.put(objPath, new OdinSchema.SchemaArray(objPath, new OdinSchema.SchemaFieldType.StringType(), min, max));
            }
            return;
        }

        // :unique on array
        if (isArray && s.equals(":unique")) {
            objectConstraints.computeIfAbsent(objPath, k -> new ArrayList<>())
                    .add(new OdinSchema.SchemaObjectConstraint.UniqueArray());
            return;
        }

        // :invariant expression
        if (s.startsWith(":invariant ")) {
            String expression = s.substring(11).trim();
            objectConstraints.computeIfAbsent(objPath, k -> new ArrayList<>())
                    .add(new OdinSchema.SchemaObjectConstraint.Invariant(expression));
            return;
        }

        // :of (min..max) field1, field2, ...
        if (s.startsWith(":of ")) {
            parseCardinalityConstraint(s.substring(4).trim(), objPath, objectConstraints);
            return;
        }

        // :one_of field1, field2, ...
        if (s.startsWith(":one_of ")) {
            String fieldsStr = s.substring(8).trim();
            var fieldList = parseFieldList(fieldsStr);
            objectConstraints.computeIfAbsent(objPath, k -> new ArrayList<>())
                    .add(new OdinSchema.SchemaObjectConstraint.Cardinality(fieldList, 1L, null));
            return;
        }

        // :exactly_one field1, field2, ...
        if (s.startsWith(":exactly_one ")) {
            String fieldsStr = s.substring(13).trim();
            var fieldList = parseFieldList(fieldsStr);
            objectConstraints.computeIfAbsent(objPath, k -> new ArrayList<>())
                    .add(new OdinSchema.SchemaObjectConstraint.Cardinality(fieldList, 1L, 1L));
            return;
        }
    }

    private static void parseCardinalityConstraint(String s,
            String objPath, Map<String, List<OdinSchema.SchemaObjectConstraint>> objectConstraints) {
        // Format: (min..max) field1, field2, ...  OR  (min..) field1, field2, ...
        Long min = null, max = null;
        String fieldsStr = s;

        if (s.startsWith("(")) {
            int closeParen = s.indexOf(')');
            if (closeParen > 0) {
                String boundsStr = s.substring(1, closeParen);
                int dotDot = boundsStr.indexOf("..");
                if (dotDot >= 0) {
                    String minStr = boundsStr.substring(0, dotDot).trim();
                    String maxStr = boundsStr.substring(dotDot + 2).trim();
                    if (!minStr.isEmpty()) min = Long.parseLong(minStr);
                    if (!maxStr.isEmpty()) max = Long.parseLong(maxStr);
                }
                fieldsStr = s.substring(closeParen + 1).trim();
            }
        }

        var fieldList = parseFieldList(fieldsStr);
        objectConstraints.computeIfAbsent(objPath, k -> new ArrayList<>())
                .add(new OdinSchema.SchemaObjectConstraint.Cardinality(fieldList, min, max));
    }

    private static List<String> parseFieldList(String fieldsStr) {
        var result = new ArrayList<String>();
        for (String f : fieldsStr.split(",")) {
            String trimmed = f.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static OdinSchema.SchemaField parseFieldSpec(String name, String spec) {
        boolean required = false;
        boolean confidential = false;
        boolean deprecated = false;
        boolean immutable = false;
        OdinSchema.SchemaFieldType fieldType = new OdinSchema.SchemaFieldType.StringType();
        var constraints = new ArrayList<OdinSchema.SchemaConstraint>();
        var conditionals = new ArrayList<OdinSchema.SchemaConditional>();

        String s = spec;

        // Handle ODIN native inline prefixes: !, -, *
        while (!s.isEmpty()) {
            if (s.charAt(0) == '!') { required = true; s = s.substring(1).trim(); }
            else if (s.charAt(0) == '-') { deprecated = true; s = s.substring(1).trim(); }
            else if (s.charAt(0) == '*') { confidential = true; s = s.substring(1).trim(); }
            else break;
        }

        // Inline enum: (value1, value2, value3)
        if (s.startsWith("(")) {
            int closeParen = s.indexOf(')');
            if (closeParen > 0) {
                String enumStr = s.substring(1, closeParen);
                var values = new ArrayList<String>();
                for (String v : enumStr.split(",")) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty()) values.add(trimmed);
                }
                constraints.add(new OdinSchema.SchemaConstraint.Enum(values));
                fieldType = new OdinSchema.SchemaFieldType.StringType();
                s = s.substring(closeParen + 1).trim();
            }
        }
        // Parse ODIN native type prefixes: ##, #$, #, ?, ^, ~, @TypeRef
        else if (s.startsWith("##")) {
            fieldType = new OdinSchema.SchemaFieldType.IntegerType();
            s = s.substring(2).trim();
        } else if (s.startsWith("#$")) {
            fieldType = new OdinSchema.SchemaFieldType.CurrencyType(null);
            s = s.substring(2).trim();
        } else if (s.startsWith("#")) {
            fieldType = new OdinSchema.SchemaFieldType.NumberType(null);
            s = s.substring(1).trim();
        } else if (s.startsWith("?")) {
            fieldType = new OdinSchema.SchemaFieldType.BooleanType();
            s = s.substring(1).trim();
        } else if (s.startsWith("^")) {
            fieldType = new OdinSchema.SchemaFieldType.BinaryType();
            s = s.substring(1).trim();
        } else if (s.startsWith("~")) {
            fieldType = new OdinSchema.SchemaFieldType.NullType();
            s = s.substring(1).trim();
        } else if (s.startsWith("@") && !s.startsWith("@.")) {
            int end = s.indexOf(' ');
            String typeName = end >= 0 ? s.substring(1, end) : s.substring(1);
            fieldType = new OdinSchema.SchemaFieldType.TypeRefType(typeName);
            s = end >= 0 ? s.substring(end).trim() : "";
        } else if (s.startsWith("string")) {
            fieldType = new OdinSchema.SchemaFieldType.StringType();
            s = s.substring(6).trim();
        } else if (s.startsWith("integer")) {
            fieldType = new OdinSchema.SchemaFieldType.IntegerType();
            s = s.substring(7).trim();
        } else if (s.startsWith("number")) {
            fieldType = new OdinSchema.SchemaFieldType.NumberType(null);
            s = s.substring(6).trim();
        } else if (s.startsWith("boolean")) {
            fieldType = new OdinSchema.SchemaFieldType.BooleanType();
            s = s.substring(7).trim();
        } else if (s.startsWith("date")) {
            fieldType = new OdinSchema.SchemaFieldType.DateType();
            s = s.substring(4).trim();
        } else if (s.startsWith("timestamp")) {
            fieldType = new OdinSchema.SchemaFieldType.TimestampType();
            s = s.substring(9).trim();
        } else if (s.startsWith("currency")) {
            fieldType = new OdinSchema.SchemaFieldType.CurrencyType(null);
            s = s.substring(8).trim();
        } else if (s.startsWith("percent")) {
            fieldType = new OdinSchema.SchemaFieldType.PercentType();
            s = s.substring(7).trim();
        } else if (s.startsWith("binary")) {
            fieldType = new OdinSchema.SchemaFieldType.BinaryType();
            s = s.substring(6).trim();
        } else if (s.startsWith("null")) {
            fieldType = new OdinSchema.SchemaFieldType.NullType();
            s = s.substring(4).trim();
        }

        // Parse inline constraints
        while (!s.isEmpty()) {
            if (s.startsWith(":(")) {
                int closeParen = s.indexOf(')');
                if (closeParen > 2) {
                    String boundsStr = s.substring(2, closeParen);
                    parseBoundsInline(boundsStr, constraints);
                    s = s.substring(closeParen + 1).trim();
                } else break;
            } else if (s.startsWith(":/")) {
                int lastSlash = s.lastIndexOf('/');
                if (lastSlash > 2) {
                    String pattern = s.substring(2, lastSlash);
                    constraints.add(new OdinSchema.SchemaConstraint.Pattern(pattern));
                    s = s.substring(lastSlash + 1).trim();
                } else break;
            } else if (s.startsWith(":if ")) {
                // Conditional: :if field = value
                String condStr = s.substring(4).trim();
                parseConditional(condStr, conditionals, false);
                break;
            } else if (s.startsWith(":unless ")) {
                String condStr = s.substring(8).trim();
                parseConditional(condStr, conditionals, true);
                break;
            } else if (s.startsWith(":optional")) {
                required = false;
                s = s.substring(9).trim();
            } else if (s.startsWith(":required")) {
                required = true;
                s = s.substring(9).trim();
            } else if (s.startsWith(":confidential")) {
                confidential = true;
                s = s.substring(13).trim();
            } else if (s.startsWith(":deprecated")) {
                deprecated = true;
                s = s.substring(11).trim();
            } else if (s.startsWith(":immutable")) {
                immutable = true;
                s = s.substring(10).trim();
            } else if (s.startsWith(":unique")) {
                constraints.add(new OdinSchema.SchemaConstraint.Unique());
                s = s.substring(7).trim();
            } else if (s.startsWith(":format")) {
                s = s.substring(7).trim();
                if (s.startsWith("=")) s = s.substring(1).trim();
                String val = extractValue(s);
                constraints.add(new OdinSchema.SchemaConstraint.Format(val));
                s = skipPastValue(s).trim();
            } else if (s.startsWith(":pattern")) {
                s = s.substring(8).trim();
                if (s.startsWith("=")) s = s.substring(1).trim();
                if (s.startsWith("\"")) {
                    int closeQuote = s.indexOf('"', 1);
                    if (closeQuote > 0) {
                        constraints.add(new OdinSchema.SchemaConstraint.Pattern(s.substring(1, closeQuote)));
                        s = s.substring(closeQuote + 1).trim();
                    } else break;
                } else {
                    int end = s.indexOf(" :");
                    String pattern = end >= 0 ? s.substring(0, end) : s;
                    constraints.add(new OdinSchema.SchemaConstraint.Pattern(pattern));
                    s = end >= 0 ? s.substring(end).trim() : "";
                }
            } else if (s.startsWith(":min=")) {
                String val = extractValue(s.substring(5));
                constraints.add(new OdinSchema.SchemaConstraint.Bounds(val, null, false, false));
                s = skipPastValue(s.substring(5)).trim();
            } else if (s.startsWith(":max=")) {
                String val = extractValue(s.substring(5));
                boolean merged = false;
                for (int i = 0; i < constraints.size(); i++) {
                    if (constraints.get(i) instanceof OdinSchema.SchemaConstraint.Bounds b && b.max() == null) {
                        constraints.set(i, new OdinSchema.SchemaConstraint.Bounds(b.min(), val, b.minExclusive(), false));
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    constraints.add(new OdinSchema.SchemaConstraint.Bounds(null, val, false, false));
                }
                s = skipPastValue(s.substring(5)).trim();
            } else if (s.startsWith(":enum=")) {
                String val = s.substring(6).trim();
                int end = val.indexOf(" :");
                String enumStr = end >= 0 ? val.substring(0, end) : val;
                var values = new ArrayList<String>();
                for (String v : enumStr.split(",")) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty()) values.add(trimmed);
                }
                constraints.add(new OdinSchema.SchemaConstraint.Enum(values));
                s = end >= 0 ? val.substring(end).trim() : "";
            } else if (s.startsWith(":")) {
                int nextSpace = s.indexOf(' ', 1);
                s = nextSpace >= 0 ? s.substring(nextSpace).trim() : "";
            } else {
                break;
            }
        }

        return new OdinSchema.SchemaField(name, fieldType, required, confidential, deprecated,
                immutable, null, constraints, null, conditionals);
    }

    private static void parseConditional(String condStr,
            List<OdinSchema.SchemaConditional> conditionals, boolean unless) {
        // Parse: field = value, field > value, field != value, etc.
        OdinSchema.ConditionalOperator op = OdinSchema.ConditionalOperator.EQ;
        int opPos = -1;
        int opLen = 1;

        // Find operator
        int neqPos = condStr.indexOf("!=");
        int gtePos = condStr.indexOf(">=");
        int ltePos = condStr.indexOf("<=");
        int gtPos = condStr.indexOf(">");
        int ltPos = condStr.indexOf("<");
        int eqPos = condStr.indexOf("=");

        if (neqPos >= 0) { opPos = neqPos; opLen = 2; op = OdinSchema.ConditionalOperator.NOT_EQ; }
        else if (gtePos >= 0) { opPos = gtePos; opLen = 2; op = OdinSchema.ConditionalOperator.GTE; }
        else if (ltePos >= 0) { opPos = ltePos; opLen = 2; op = OdinSchema.ConditionalOperator.LTE; }
        else if (gtPos >= 0) { opPos = gtPos; opLen = 1; op = OdinSchema.ConditionalOperator.GT; }
        else if (ltPos >= 0) { opPos = ltPos; opLen = 1; op = OdinSchema.ConditionalOperator.LT; }
        else if (eqPos >= 0) { opPos = eqPos; opLen = 1; op = OdinSchema.ConditionalOperator.EQ; }

        if (opPos < 0) return;

        String field = condStr.substring(0, opPos).trim();
        String valueStr = condStr.substring(opPos + opLen).trim();

        OdinSchema.ConditionalValue value;
        if (valueStr.equalsIgnoreCase("true") || valueStr.equalsIgnoreCase("false")) {
            value = new OdinSchema.ConditionalValue.BoolVal(Boolean.parseBoolean(valueStr));
        } else {
            try {
                double num = Double.parseDouble(valueStr);
                value = new OdinSchema.ConditionalValue.NumberVal(num);
            } catch (NumberFormatException e) {
                value = new OdinSchema.ConditionalValue.StringVal(valueStr);
            }
        }

        conditionals.add(new OdinSchema.SchemaConditional(field, op, value, unless));
    }

    private static void parseBoundsInline(String boundsStr, List<OdinSchema.SchemaConstraint> constraints) {
        int dotDot = boundsStr.indexOf("..");
        if (dotDot >= 0) {
            String minStr = boundsStr.substring(0, dotDot).trim();
            String maxStr = boundsStr.substring(dotDot + 2).trim();
            String min = minStr.isEmpty() ? null : minStr;
            String max = maxStr.isEmpty() ? null : maxStr;
            constraints.add(new OdinSchema.SchemaConstraint.Bounds(min, max, false, false));
        } else {
            constraints.add(new OdinSchema.SchemaConstraint.Bounds(boundsStr.trim(), boundsStr.trim(), false, false));
        }
    }

    private static String extractValue(String s) {
        int end = s.indexOf(' ');
        return end >= 0 ? s.substring(0, end) : s;
    }

    private static String skipPastValue(String s) {
        int end = s.indexOf(' ');
        return end >= 0 ? s.substring(end) : "";
    }
}
