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

        // Relative-header context for {.sub} nesting.
        String previousHeaderType = "";
        String previousHeaderPath = "";
        String currentTypeSubPath = "";

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

                // Relative header {.sub}: nest under the last absolute context.
                if (headerContent.startsWith(".")) {
                    currentIsArray = false;
                    String sub = headerContent.substring(1);
                    if (!previousHeaderType.isEmpty()) {
                        // Re-open the parent type; route fields under the sub-path (e.g. policy.term.*).
                        currentHeader = "@" + previousHeaderType;
                        currentTypeSubPath = sub;
                        types.putIfAbsent(previousHeaderType, new OdinSchema.SchemaType(previousHeaderType, new ArrayList<>()));
                    } else {
                        // Object context: relative headers are siblings under the last absolute path.
                        currentTypeSubPath = "";
                        currentHeader = previousHeaderPath.isEmpty() ? sub : previousHeaderPath + "." + sub;
                    }
                    continue;
                }

                // Absolute header resets the relative sub-path context.
                currentTypeSubPath = "";
                if (headerContent.endsWith("[]")) {
                    currentHeader = headerContent.substring(0, headerContent.length() - 2);
                    currentIsArray = true;
                    previousHeaderPath = currentHeader;
                    previousHeaderType = "";
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
                        previousHeaderType = typeName;
                        previousHeaderPath = "";
                    } else {
                        previousHeaderPath = headerContent;
                        previousHeaderType = "";
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
                importPath = stripQuotes(importPath);
                alias = stripQuotes(alias);
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
                previousHeaderType = typeName;
                previousHeaderPath = "";
                currentTypeSubPath = "";
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

            // Type composition / intersection: = @a & @b
            if (line.startsWith("=") && line.contains("@")) {
                String rhs = line.substring(1).trim();
                var refs = new ArrayList<String>();
                for (String part : rhs.split("&")) {
                    String p = part.trim();
                    if (p.startsWith("@")) {
                        int end = 1;
                        while (end < p.length() && p.charAt(end) != ' ' && p.charAt(end) != ':') end++;
                        refs.add(p.substring(1, end));
                    }
                }
                if (!refs.isEmpty()) {
                    String joined = String.join("&", refs);
                    var compField = new OdinSchema.SchemaField("_composition",
                            new OdinSchema.SchemaFieldType.TypeRefType(joined));
                    String headerPrefix = currentHeader != null ? currentHeader : "";
                    if (headerPrefix.startsWith("@")) {
                        var typeEntry = types.get(headerPrefix.substring(1));
                        if (typeEntry != null) typeEntry.fields().add(compField);
                    } else if (currentIsArray) {
                        // Array type inheritance: store under the array path's item fields (no-op model).
                    } else if (!headerPrefix.isEmpty()) {
                        fields.put(headerPrefix + "._composition", compField);
                    }
                }
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
                String headerPrefix = currentHeader != null ? currentHeader : "";
                boolean isTypeDefinition = headerPrefix.startsWith("@") || headerPrefix.startsWith("{@");
                if (isTypeDefinition) {
                    // Prefix the field key with the relative sub-path inside {.sub} sections.
                    String typeFieldName = currentTypeSubPath.isEmpty() ? cleanFieldName : currentTypeSubPath + "." + cleanFieldName;
                    var field = parseFieldSpec(typeFieldName, valueSpec);
                    String typeName = headerPrefix.startsWith("@") ? headerPrefix.substring(1) : headerPrefix;
                    var typeEntry = types.get(typeName);
                    if (typeEntry != null) {
                        typeEntry.fields().add(field);
                    }
                    continue;
                }
                var field = parseFieldSpec(cleanFieldName, valueSpec);
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
        boolean computed = false;
        boolean nullable = false;
        OdinSchema.SchemaFieldType fieldType = new OdinSchema.SchemaFieldType.StringType();
        var constraints = new ArrayList<OdinSchema.SchemaConstraint>();
        var conditionals = new ArrayList<OdinSchema.SchemaConditional>();

        String s = spec;

        // Handle ODIN native inline prefixes: !, -, *, ~ (nullable)
        // A bare leading ~ marks the field nullable; ~ inside a union is handled later.
        while (!s.isEmpty()) {
            if (s.charAt(0) == '!') { required = true; s = s.substring(1).trim(); }
            else if (s.charAt(0) == '-') { deprecated = true; s = s.substring(1).trim(); }
            else if (s.charAt(0) == '*') { confidential = true; s = s.substring(1).trim(); }
            else if (s.charAt(0) == '~' && s.length() > 1 && isTypePrefixChar(s.charAt(1))) {
                nullable = true; s = s.substring(1).trim();
            }
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
        // Parse the (possibly union) type specification.
        else {
            var base = parseBaseType(s);
            if (base != null) {
                fieldType = base.type();
                s = base.rest();
                // Union members: type|type2[|type3...]
                if (s.startsWith("|")) {
                    var members = new ArrayList<OdinSchema.SchemaFieldType>();
                    members.add(fieldType);
                    while (s.startsWith("|")) {
                        s = s.substring(1);
                        var next = parseBaseType(s);
                        if (next == null) break;
                        members.add(next.type());
                        s = next.rest();
                    }
                    fieldType = new OdinSchema.SchemaFieldType.UnionType(members);
                }
            }
        }

        s = s.trim();

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
            } else if (s.startsWith(":computed")) {
                computed = true;
                s = s.substring(9).trim();
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

        OdinSchema.DefaultValue defaultValue = parseDefaultValue(fieldType, s.trim());

        return new OdinSchema.SchemaField(name, fieldType, required, confidential, deprecated,
                immutable, null, constraints, defaultValue, conditionals, computed, nullable);
    }

    private static boolean isTypePrefixChar(char c) {
        return c == '#' || c == '?' || c == '^' || Character.isLetter(c);
    }

    // Capture a typed default literal trailing the type/constraints (e.g. "##3", "5.00", "0.15").
    private static OdinSchema.DefaultValue parseDefaultValue(
            OdinSchema.SchemaFieldType fieldType, String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;

        if (t.startsWith("##")) {
            try { return new OdinSchema.DefaultValue("integer", Long.parseLong(t.substring(2).trim())); }
            catch (NumberFormatException e) { return null; }
        }
        if (t.startsWith("#%")) {
            try { return new OdinSchema.DefaultValue("percent", Double.parseDouble(t.substring(2).trim())); }
            catch (NumberFormatException e) { return null; }
        }
        if (t.startsWith("#$")) {
            try { return new OdinSchema.DefaultValue("currency", Double.parseDouble(t.substring(2).trim())); }
            catch (NumberFormatException e) { return null; }
        }
        if (t.startsWith("#")) {
            try { return new OdinSchema.DefaultValue("number", Double.parseDouble(t.substring(1).trim())); }
            catch (NumberFormatException e) { return null; }
        }
        if (t.equals("true") || t.equals("false")) {
            return new OdinSchema.DefaultValue("boolean", Boolean.parseBoolean(t));
        }
        // Bare literal: tag by the declared field type (the prefix was consumed as the type).
        try {
            if (fieldType instanceof OdinSchema.SchemaFieldType.IntegerType) {
                return new OdinSchema.DefaultValue("integer", Long.parseLong(t));
            }
            if (fieldType instanceof OdinSchema.SchemaFieldType.PercentType) {
                return new OdinSchema.DefaultValue("percent", Double.parseDouble(t));
            }
            if (fieldType instanceof OdinSchema.SchemaFieldType.CurrencyType) {
                return new OdinSchema.DefaultValue("currency", Double.parseDouble(t));
            }
            if (fieldType instanceof OdinSchema.SchemaFieldType.NumberType) {
                return new OdinSchema.DefaultValue("number", Double.parseDouble(t));
            }
        } catch (NumberFormatException ignored) {}
        return new OdinSchema.DefaultValue("string", stripQuotes(t));
    }

    // A parsed base type plus the unconsumed remainder of the spec.
    private record TypeParse(OdinSchema.SchemaFieldType type, String rest) {}

    // Parse a single (non-union) base type from the start of a spec fragment.
    private static TypeParse parseBaseType(String s) {
        if (s.startsWith("##")) return new TypeParse(new OdinSchema.SchemaFieldType.IntegerType(), s.substring(2));
        if (s.startsWith("#%")) return new TypeParse(new OdinSchema.SchemaFieldType.PercentType(), s.substring(2));
        if (s.startsWith("#$")) return new TypeParse(new OdinSchema.SchemaFieldType.CurrencyType(null), s.substring(2));
        if (s.startsWith("#")) return new TypeParse(new OdinSchema.SchemaFieldType.NumberType(null), s.substring(1));
        if (s.startsWith("?")) return new TypeParse(new OdinSchema.SchemaFieldType.BooleanType(), s.substring(1));
        if (s.startsWith("^")) return new TypeParse(new OdinSchema.SchemaFieldType.BinaryType(), s.substring(1));
        if (s.startsWith("~")) return new TypeParse(new OdinSchema.SchemaFieldType.NullType(), s.substring(1));
        if (s.startsWith("@") && !s.startsWith("@.")) {
            // Read the reference name up to whitespace, '|', ':' or end.
            int i = 1;
            while (i < s.length() && s.charAt(i) != ' ' && s.charAt(i) != '|' && s.charAt(i) != ':') i++;
            String typeName = s.substring(1, i);
            return new TypeParse(new OdinSchema.SchemaFieldType.TypeRefType(typeName), s.substring(i));
        }
        // Named temporal/scalar keywords (a glued :directive suffix is left for the constraint loop).
        String[][] named = {
                {"timestamp", "timestamp"}, {"duration", "duration"}, {"datetime", "timestamp"},
                {"date", "date"}, {"time", "time"}, {"string", "string"}, {"integer", "integer"},
                {"number", "number"}, {"boolean", "boolean"}, {"currency", "currency"},
                {"percent", "percent"}, {"binary", "binary"}, {"null", "null"}
        };
        for (String[] kw : named) {
            if (s.startsWith(kw[0])) {
                return new TypeParse(namedType(kw[1]), s.substring(kw[0].length()));
            }
        }
        return null;
    }

    private static OdinSchema.SchemaFieldType namedType(String kind) {
        return switch (kind) {
            case "timestamp" -> new OdinSchema.SchemaFieldType.TimestampType();
            case "date" -> new OdinSchema.SchemaFieldType.DateType();
            case "time" -> new OdinSchema.SchemaFieldType.TimeType();
            case "duration" -> new OdinSchema.SchemaFieldType.DurationType();
            case "integer" -> new OdinSchema.SchemaFieldType.IntegerType();
            case "number" -> new OdinSchema.SchemaFieldType.NumberType(null);
            case "boolean" -> new OdinSchema.SchemaFieldType.BooleanType();
            case "currency" -> new OdinSchema.SchemaFieldType.CurrencyType(null);
            case "percent" -> new OdinSchema.SchemaFieldType.PercentType();
            case "binary" -> new OdinSchema.SchemaFieldType.BinaryType();
            case "null" -> new OdinSchema.SchemaFieldType.NullType();
            default -> new OdinSchema.SchemaFieldType.StringType();
        };
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

    // Strip a single pair of matching surrounding quotes.
    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
