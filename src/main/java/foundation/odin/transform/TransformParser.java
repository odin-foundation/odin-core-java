package foundation.odin.transform;

import foundation.odin.parsing.OdinParser;
import foundation.odin.types.*;
import foundation.odin.types.OdinDirective.DirectiveValue;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinTransformTypes.*;
import foundation.odin.types.OdinTransformTypes.FieldExpression.*;
import foundation.odin.types.OdinTransformTypes.VerbArg.*;
import foundation.odin.types.OdinValue.*;

import java.util.*;

public final class TransformParser {

    private TransformParser() {}

    // ── Public entry points ──

    public static OdinTransform parse(String input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        var doc = OdinParser.parse(input, ParseOptions.DEFAULT);
        return parseTransformDoc(doc);
    }

    public static OdinTransform parseTransformDoc(OdinDocument doc) {
        var metadata = parseMetadata(doc);
        var source = parseSourceConfig(doc);
        var target = parseTargetConfig(doc);
        var constants = parseConstants(doc);
        var accumulators = parseAccumulators(doc);
        var tables = parseLookupTables(doc);
        var imports = parseImports(doc);
        var enforceConfidential = parseEnforceConfidential(doc);
        var strictTypes = parseStrictTypes(doc);
        var segments = parseSegments(doc);
        var passes = collectPasses(segments);

        var transform = new OdinTransform();
        transform.setMetadata(metadata);
        transform.setSource(source);
        transform.setTarget(target);
        transform.setConstants(constants);
        transform.setAccumulators(accumulators);
        transform.setTables(tables);
        transform.setSegments(segments);
        transform.setImports(imports);
        transform.setPasses(passes);
        transform.setEnforceConfidential(enforceConfidential);
        transform.setStrictTypes(strictTypes);
        return transform;
    }

    // ── Metadata ──

    private static TransformMetadata parseMetadata(OdinDocument doc) {
        var m = new TransformMetadata();
        m.setOdinVersion(getMetaString(doc, "odin"));
        m.setTransformVersion(getMetaString(doc, "transform"));
        m.setDirection(getMetaString(doc, "direction"));
        m.setName(getMetaString(doc, "name"));
        m.setDescription(getMetaString(doc, "description"));
        return m;
    }

    // ── Source / Target Config ──

    private static TransformSourceConfig parseSourceConfig(OdinDocument doc) {
        var format = getMetaString(doc, "source.format");
        if (format == null) return null;

        var options = new LinkedHashMap<String, String>();
        var namespaces = new LinkedHashMap<String, String>();

        for (var entry : doc.getMetadata()) {
            var key = entry.getKey();
            if (key.startsWith("source.namespace.")) {
                namespaces.put(key.substring("source.namespace.".length()), odinValueToString(entry.getValue()));
            } else if (key.startsWith("source.")) {
                var rest = key.substring("source.".length());
                if (!rest.equals("format") && !rest.startsWith("discriminator.")) {
                    options.put(rest, odinValueToString(entry.getValue()));
                }
            }
        }

        var discriminator = parseSourceDiscriminator(doc);

        var config = new TransformSourceConfig();
        config.setFormat(format);
        config.setOptions(options);
        config.setNamespaces(namespaces);
        config.setDiscriminator(discriminator);
        return config;
    }

    private static SourceDiscriminator parseSourceDiscriminator(OdinDocument doc) {
        var discTypeStr = getMetaString(doc, "source.discriminator.type");
        if (discTypeStr == null) return null;

        DiscriminatorType discType;
        switch (discTypeStr) {
            case "position": discType = DiscriminatorType.POSITION; break;
            case "field": discType = DiscriminatorType.FIELD; break;
            case "path": discType = DiscriminatorType.PATH; break;
            default: return null;
        }

        Integer pos = parseMetaInt(doc, "source.discriminator.pos");
        Integer len = parseMetaInt(doc, "source.discriminator.len");
        Integer field = parseMetaInt(doc, "source.discriminator.field");
        String path = getMetaString(doc, "source.discriminator.path");

        var disc = new SourceDiscriminator();
        disc.setType(discType);
        disc.setPos(pos);
        disc.setLen(len);
        disc.setField(field);
        disc.setPath(path);
        return disc;
    }

    private static TransformTargetConfig parseTargetConfig(OdinDocument doc) {
        var format = getMetaString(doc, "target.format");
        if (format == null) format = "";

        var options = new LinkedHashMap<String, String>();
        for (var entry : doc.getMetadata()) {
            var key = entry.getKey();
            if (key.startsWith("target.")) {
                var rest = key.substring("target.".length());
                if (!rest.equals("format")) {
                    options.put(rest, odinValueToString(entry.getValue()));
                }
            }
        }

        var config = new TransformTargetConfig();
        config.setFormat(format);
        config.setOptions(options);
        return config;
    }

    // ── Constants ──

    private static Map<String, OdinValue> parseConstants(OdinDocument doc) {
        var constants = new LinkedHashMap<String, OdinValue>();
        var arrayEntries = new LinkedHashMap<String, List<int[]>>();
        var arrayValues = new LinkedHashMap<String, List<OdinValue>>();

        for (var source : List.of(doc.getMetadata(), doc.getAssignments())) {
            for (var entry : source) {
                if (!entry.getKey().startsWith("const.")) continue;
                var name = entry.getKey().substring("const.".length());

                int bracketPos = name.indexOf('[');
                if (bracketPos >= 0 && name.endsWith("]")) {
                    var baseName = name.substring(0, bracketPos);
                    var idxStr = name.substring(bracketPos + 1, name.length() - 1);
                    try {
                        int idx = Integer.parseInt(idxStr);
                        arrayEntries.computeIfAbsent(baseName, k -> new ArrayList<>()).add(new int[]{idx});
                        arrayValues.computeIfAbsent(baseName, k -> new ArrayList<>()).add(entry.getValue());
                        continue;
                    } catch (NumberFormatException ignored) {}
                }

                constants.put(name, entry.getValue());
            }
        }

        // Build arrays from indexed entries
        for (var kvp : arrayEntries.entrySet()) {
            var indices = kvp.getValue();
            var values = arrayValues.get(kvp.getKey());
            int maxIdx = 0;
            for (var idx : indices) if (idx[0] > maxIdx) maxIdx = idx[0];

            var arr = new ArrayList<OdinArrayItem>(maxIdx + 1);
            for (int i = 0; i <= maxIdx; i++) arr.add(OdinArrayItem.fromValue(OdinValue.ofNull()));

            for (int i = 0; i < indices.size(); i++) {
                arr.set(indices.get(i)[0], OdinArrayItem.fromValue(values.get(i)));
            }

            constants.put(kvp.getKey(), OdinValue.ofArray(arr));
        }

        return constants;
    }

    // ── Accumulators ──

    private static Map<String, AccumulatorDef> parseAccumulators(OdinDocument doc) {
        var accumulators = new LinkedHashMap<String, AccumulatorDef>();

        for (var source : List.of(doc.getMetadata(), doc.getAssignments())) {
            for (var entry : source) {
                if (!entry.getKey().startsWith("accumulator.")) continue;
                var name = entry.getKey().substring("accumulator.".length());
                if (name.endsWith("._persist")) continue;

                var def = new AccumulatorDef();
                def.setName(name);
                def.setInitial(entry.getValue());
                def.setPersist(false);
                accumulators.put(name, def);
            }
        }

        for (var source : List.of(doc.getMetadata(), doc.getAssignments())) {
            for (var entry : source) {
                if (!entry.getKey().startsWith("accumulator.")) continue;
                var name = entry.getKey().substring("accumulator.".length());
                if (!name.endsWith("._persist")) continue;

                var accName = name.substring(0, name.length() - "._persist".length());
                var def = accumulators.get(accName);
                if (def != null && entry.getValue() instanceof OdinBoolean b) {
                    def.setPersist(b.getValue());
                }
            }
        }

        return accumulators;
    }

    // ── Lookup Tables ──

    private static Map<String, LookupTable> parseLookupTables(OdinDocument doc) {
        var tables = new LinkedHashMap<String, LookupTable>();
        var tableRows = new LinkedHashMap<String, List<TableRowEntry>>();
        var tableDefaults = new LinkedHashMap<String, DynValue>();

        for (var source : List.of(doc.getMetadata(), doc.getAssignments())) {
            for (var entry : source) {
                if (!entry.getKey().startsWith("table.")) continue;
                var rest = entry.getKey().substring("table.".length());

                if (rest.endsWith("._default")) {
                    var nameAndDefault = rest.substring(0, rest.length() - "._default".length());
                    if (!nameAndDefault.isEmpty() && nameAndDefault.indexOf('[') < 0) {
                        tableDefaults.put(nameAndDefault, odinValueToDynForTable(entry.getValue()));
                        continue;
                    }
                }

                int bracketPos = rest.indexOf('[');
                if (bracketPos < 0) continue;
                int closePos = rest.indexOf(']', bracketPos);
                if (closePos < 0) continue;

                var tableName = rest.substring(0, bracketPos);
                var idxStr = rest.substring(bracketPos + 1, closePos);
                var afterBracket = rest.substring(closePos + 1);

                int rowIdx;
                try { rowIdx = Integer.parseInt(idxStr); } catch (NumberFormatException e) { continue; }

                var colName = afterBracket.startsWith(".") ? afterBracket.substring(1) : afterBracket;
                if (colName.isEmpty()) continue;

                tableRows.computeIfAbsent(tableName, k -> new ArrayList<>())
                        .add(new TableRowEntry(rowIdx, colName, odinValueToDynForTable(entry.getValue())));
            }
        }

        for (var kvp : tableRows.entrySet()) {
            var tableName = kvp.getKey();
            var rows = kvp.getValue();

            var columns = new ArrayList<String>();
            for (var r : rows) {
                if (!columns.contains(r.column)) columns.add(r.column);
            }

            int maxRow = 0;
            for (var r : rows) if (r.rowIndex > maxRow) maxRow = r.rowIndex;

            var rowData = new LinkedHashMap<Integer, Map<String, DynValue>>();
            for (var r : rows) {
                rowData.computeIfAbsent(r.rowIndex, k -> new LinkedHashMap<>()).put(r.column, r.value);
            }

            var builtRows = new ArrayList<List<DynValue>>();
            for (int i = 0; i <= maxRow; i++) {
                var rd = rowData.get(i);
                if (rd != null) {
                    var row = new ArrayList<DynValue>();
                    for (var col : columns) {
                        row.add(rd.getOrDefault(col, DynValue.ofNull()));
                    }
                    builtRows.add(row);
                }
            }

            var table = new LookupTable();
            table.setName(tableName);
            table.setColumns(columns);
            table.setRows(builtRows);
            table.setDefault(tableDefaults.get(tableName));
            tables.put(tableName, table);
        }

        return tables;
    }

    private record TableRowEntry(int rowIndex, String column, DynValue value) {}

    // ── Imports ──

    private static List<ImportRef> parseImports(OdinDocument doc) {
        var imports = new ArrayList<ImportRef>();
        for (var imp : doc.getImports()) {
            var ref = new ImportRef();
            ref.setPath(imp.path());
            ref.setAlias(imp.alias());
            imports.add(ref);
        }
        return imports;
    }

    // ── Confidential / Strict Types ──

    private static ConfidentialMode parseEnforceConfidential(OdinDocument doc) {
        var val = getMetaString(doc, "enforceConfidential");
        if (val == null) return null;
        return switch (val) {
            case "redact" -> ConfidentialMode.REDACT;
            case "mask" -> ConfidentialMode.MASK;
            default -> null;
        };
    }

    private static boolean parseStrictTypes(OdinDocument doc) {
        var strVal = getMetaString(doc, "strictTypes");
        if (strVal != null) return "true".equals(strVal);

        var val = doc.getMetadata().tryGet("strictTypes");
        if (val instanceof OdinBoolean b) return b.getValue();
        return false;
    }

    // ── Merge directive modifiers ──

    private static OdinModifiers mergeDirectiveModifiers(OdinModifiers modifiers, List<OdinDirective> directives) {
        boolean hasConf = false, hasReq = false, hasDep = false, hasAttr = false;
        String ns = null;
        for (var d : directives) {
            switch (d.getName()) {
                case "confidential": hasConf = true; break;
                case "required": hasReq = true; break;
                case "deprecated": hasDep = true; break;
                case "attr": hasAttr = true; break;
                case "ns": if (d.getValue() != null) ns = d.getValue().asString(); break;
            }
        }

        if (!hasConf && !hasReq && !hasDep && !hasAttr && ns == null) return modifiers;

        boolean mReq = modifiers != null && modifiers.isRequired();
        boolean mConf = modifiers != null && modifiers.isConfidential();
        boolean mDep = modifiers != null && modifiers.isDeprecated();
        boolean mAttr = modifiers != null && modifiers.isAttr();
        String mNs = modifiers != null ? modifiers.getNs() : null;

        return new OdinModifiers(mReq || hasReq, mConf || hasConf, mDep || hasDep, mAttr || hasAttr,
                ns != null ? ns : mNs);
    }

    // ── Segments ──

    private static List<TransformSegment> parseSegments(OdinDocument doc) {
        var sectionOrder = new ArrayList<String>();
        var sectionFields = new LinkedHashMap<String, List<SectionField>>();

        for (var entry : doc.getAssignments()) {
            if (entry.getKey().startsWith("$.")) continue;

            var split = splitSectionKey(entry.getKey());
            var section = split[0];
            var field = split[1];
            if (!section.isEmpty() && section.charAt(0) == '$') continue;
            if (section.equals("const") || section.equals("accumulator") || section.startsWith("table.")) continue;

            if (!sectionFields.containsKey(section)) {
                sectionOrder.add(section);
                sectionFields.put(section, new ArrayList<>());
            }

            OdinModifiers modifiers = doc.getPathModifiers().tryGet(entry.getKey());
            sectionFields.get(section).add(new SectionField(field, entry.getValue(), modifiers));
        }

        var segments = new ArrayList<TransformSegment>();
        for (var sectionName : sectionOrder) {
            var fields = sectionFields.get(sectionName);
            if (fields != null) {
                segments.add(buildSegment(sectionName, fields));
            }
        }

        return segments;
    }

    private record SectionField(String field, OdinValue value, OdinModifiers modifiers) {}

    private static String[] splitSectionKey(String key) {
        int dotPos = key.indexOf('.');
        if (dotPos >= 0) return new String[]{key.substring(0, dotPos), key.substring(dotPos + 1)};
        return new String[]{"", key};
    }

    private static boolean needsChildSegment(String childName, List<SectionField> fields) {
        if (childName.contains("[]")) return true;
        for (var f : fields) {
            if (f.field.startsWith("_") || f.field.contains("[]")) return true;
            if (f.field.contains("._")) return true;
        }
        return false;
    }

    private static TransformSegment buildSegment(String name, List<SectionField> fields) {
        String sourcePath = null;
        Discriminator discriminator = null;
        Integer pass = null;
        String condition = null;
        var directives = new ArrayList<SegmentDirective>();
        var mappings = new ArrayList<FieldMapping>();
        var children = new ArrayList<TransformSegment>();
        var childFields = new LinkedHashMap<String, List<SectionField>>();

        var itemOrder = new ArrayList<Object>(); // FieldMapping or String (child ref)
        var seenChildren = new LinkedHashSet<String>();

        for (var sf : fields) {
            var field = sf.field;
            var value = sf.value;
            var modifiers = sf.modifiers;

            int dotPos = field.indexOf('.');
            if (dotPos >= 0) {
                var childSection = field.substring(0, dotPos);
                var childField = field.substring(dotPos + 1);

                if (seenChildren.add(childSection)) itemOrder.add(childSection);
                childFields.computeIfAbsent(childSection, k -> new ArrayList<>())
                        .add(new SectionField(childField, value, modifiers));
                continue;
            }

            if (field.startsWith("_")) {
                switch (field) {
                    case "_loop":
                    case "_from":
                        sourcePath = odinValueToString(value);
                        break;
                    case "_pass":
                        Long passLong = value.asInt64();
                        if (passLong != null) {
                            pass = passLong.intValue();
                        } else {
                            try { pass = Integer.parseInt(odinValueToString(value)); } catch (NumberFormatException ignored) {}
                        }
                        break;
                    case "_if":
                    case "_when":
                        directives.add(buildConditionDirective("if", value));
                        break;
                    case "_elif":
                        directives.add(buildConditionDirective("elif", value));
                        break;
                    case "_else":
                        directives.add(buildConditionDirective("else", value));
                        break;
                    case "_discriminator":
                        if (value instanceof OdinReference refVal) {
                            discriminator = new Discriminator(refVal.getPath(), "");
                        } else {
                            discriminator = new Discriminator(odinValueToString(value), "");
                        }
                        break;
                    case "_discriminatorValue":
                    case "_value":
                        if (discriminator != null) {
                            discriminator.setValue(odinValueToString(value));
                        } else {
                            discriminator = new Discriminator("", odinValueToString(value));
                        }
                        break;
                    default: {
                        var m = buildFieldMapping(field, value, modifiers);
                        itemOrder.add(m);
                        mappings.add(m);
                        break;
                    }
                }
            } else {
                var m = buildFieldMapping(field, value, modifiers);
                itemOrder.add(m);
                mappings.add(m);
            }
        }

        var items = new ArrayList<SegmentItem>();
        for (var itemRef : itemOrder) {
            if (itemRef instanceof FieldMapping fm) {
                items.add(SegmentItem.fromMapping(fm));
            } else if (itemRef instanceof String childName) {
                var cf = childFields.remove(childName);
                if (cf != null) {
                    if (needsChildSegment(childName, cf)) {
                        var seg = buildSegment(childName, cf);
                        children.add(seg);
                        items.add(SegmentItem.fromChild(seg));
                    } else {
                        for (var csf : cf) {
                            var fullTarget = childName + "." + csf.field;
                            var m = buildFieldMapping(fullTarget, csf.value, csf.modifiers);
                            mappings.add(m);
                            items.add(SegmentItem.fromMapping(m));
                        }
                    }
                }
            }
        }

        // Rebuild mappings from items to preserve correct interleaved order
        var orderedMappings = new ArrayList<FieldMapping>();
        for (var item : items) {
            var m = item.asMapping();
            if (m != null) orderedMappings.add(m);
        }

        boolean isArray = name.endsWith("[]");

        var segment = new TransformSegment();
        segment.setName(name);
        segment.setPath(name);
        segment.setSourcePath(sourcePath);
        segment.setSegmentDiscriminator(discriminator);
        segment.setIsArray(isArray);
        segment.setMappings(orderedMappings);
        segment.setChildren(children);
        segment.setItems(items);
        segment.setPass(pass);
        segment.setDirectives(directives);
        segment.setCondition(condition);
        return segment;
    }

    // Build an if/elif/else segment directive. Verb / %-expression / reference
    // conditions are stored as a parsed expression; a legacy infix string is kept verbatim.
    private static SegmentDirective buildConditionDirective(String type, OdinValue value) {
        var directive = new SegmentDirective();
        directive.setDirectiveType(type);
        if (type.equals("else")) {
            directive.setValue("");
            return directive;
        }
        switch (value) {
            case OdinVerb ignored -> directive.setExpr(valueToFieldExpressionWithDirectives(value).expr);
            case OdinReference ref -> directive.setExpr(FieldExpression.copy(ref.getPath()));
            case OdinString s -> {
                var trimmed = s.getValue().trim();
                if (trimmed.startsWith("%") || trimmed.startsWith("@")) {
                    directive.setExpr(valueToFieldExpressionWithDirectives(value).expr);
                } else {
                    directive.setValue(trimmed);
                }
            }
            default -> directive.setValue(odinValueToString(value));
        }
        if (directive.getValue() == null) directive.setValue("");
        return directive;
    }

    private static FieldMapping buildFieldMapping(String target, OdinValue value, OdinModifiers modifiers) {
        var dirs = new ArrayList<OdinDirective>();
        if (value.getDirectives() != null) {
            dirs.addAll(value.getDirectives());
        }

        var result = valueToFieldExpressionWithDirectives(value);
        var expr = result.expr;
        var trailingDirs = result.dirs;

        for (var td : trailingDirs) {
            boolean exists = false;
            for (var d : dirs) if (d.getName().equals(td.getName())) { exists = true; break; }
            if (!exists) dirs.add(td);
        }

        var fmtDirs = collectFormattingDirectives(expr);
        for (var fd : fmtDirs) {
            boolean exists = false;
            for (var d : dirs) if (d.getName().equals(fd.getName())) { exists = true; break; }
            if (!exists) dirs.add(fd);
        }

        var mergedMods = mergeDirectiveModifiers(modifiers, dirs);

        var mapping = new FieldMapping();
        mapping.setTarget(target);
        mapping.setExpression(expr);
        mapping.setDirectives(dirs);
        mapping.setModifiers(mergedMods);
        return mapping;
    }

    // ── Verb Arity Map ──

    static int getVerbArity(String verb) {
        return switch (verb) {
            case "today", "now" -> 0;
            case "upper", "lower", "trim", "trimLeft", "trimRight",
                 "coerceString", "coerceNumber", "coerceInteger", "coerceBoolean",
                 "coerceDate", "coerceTimestamp", "tryCoerce",
                 "toArray", "toObject",
                 "not", "isNull", "isString", "isNumber", "isBoolean",
                 "isArray", "isObject", "isDate", "typeOf",
                 "capitalize", "titleCase", "length", "reverseString",
                 "camelCase", "snakeCase", "kebabCase", "pascalCase",
                 "slugify", "normalizeSpace", "stripAccents", "clean",
                 "wordCount", "soundex",
                 "abs", "floor", "ceil", "negate", "sign", "trunc",
                 "isFinite", "isNaN", "ln", "log10", "exp", "sqrt",
                 "formatInteger", "formatCurrency",
                 "startOfDay", "endOfDay", "startOfMonth", "endOfMonth",
                 "startOfYear", "endOfYear", "dayOfWeek", "weekOfYear",
                 "quarter", "isLeapYear", "toUnix", "fromUnix",
                 "base64Encode", "base64Decode", "urlEncode", "urlDecode",
                 "jsonEncode", "jsonDecode", "hexEncode", "hexDecode",
                 "sha256", "md5", "sha1", "sha512", "crc32",
                 "nextBusinessDay", "formatDuration",
                 "flatten", "distinct", "sort", "sortDesc", "reverse",
                 "compact", "unique", "cumsum", "cumprod",
                 "sum", "count", "min", "max", "avg", "first", "last",
                 "std", "stdSample", "variance", "varianceSample",
                 "median", "mode", "rowNumber",
                 "uuid", "sequence", "resetSequence",
                 "keys", "values", "entries",
                 "toRadians", "toDegrees" -> 1;
            case "ifNull", "ifEmpty",
                 "and", "or", "xor", "eq", "ne", "lt", "lte", "gt", "gte",
                 "contains", "startsWith", "endsWith", "truncate", "join",
                 "mask", "match", "leftOf", "rightOf", "repeat",
                 "matches", "levenshtein", "tokenize",
                 "add", "subtract", "multiply", "divide", "mod",
                 "formatNumber", "pow", "log", "formatPercent", "parseInt",
                 "formatLocaleNumber", "round",
                 "formatDate", "parseDate", "addDays", "addMonths", "addYears",
                 "addHours", "addMinutes", "addSeconds", "formatTime",
                 "formatTimestamp", "parseTimestamp", "isBefore", "isAfter",
                 "daysBetweenDates", "ageFromDate", "isValidDate",
                 "formatLocaleDate",
                 "formatPhone", "movingAvg", "businessDays",
                 "accumulate", "set",
                 "percentile", "quantile", "covariance", "correlation",
                 "weightedAvg", "npv", "irr", "zscore",
                 "sortBy", "map", "indexOf", "at", "includes", "concatArrays",
                 "zip", "groupBy", "take", "drop", "chunk", "pluck",
                 "dedupe", "diff", "pctChange", "limit",
                 "nanoid",
                 "has", "merge", "jsonPath",
                 "assert" -> 2;
            case "ifElse", "between",
                 "substring", "replace", "replaceRegex", "padLeft", "padRight",
                 "pad", "split", "extract", "wrap", "center",
                 "clamp", "random", "safeDivide",
                 "dateDiff", "isBetween",
                 "reduce", "pivot", "unpivot", "convertUnit",
                 "compound", "discount", "pmt", "fv", "pv", "depreciation",
                 "slice", "range", "shift", "rank", "lag", "lead",
                 "sample", "fillMissing",
                 "get" -> 3;
            case "rate", "nper",
                 "filter", "every", "some", "find", "findIndex", "partition",
                 "bearing", "midpoint" -> 4;
            case "distance", "interpolate" -> 5;
            case "inBoundingBox" -> 6;
            default -> -1; // variadic
        };
    }

    // ── Transform Expression Parser ──

    private record ExprWithDirs(FieldExpression expr, List<OdinDirective> dirs) {}

    private static ExprWithDirs parseStringExpressionWithDirectives(String raw) {
        var trimmed = raw.trim();

        if (trimmed.startsWith("%")) {
            var result = parseVerbExpression(trimmed);
            var remaining = result.consumed < trimmed.length() ? trimmed.substring(result.consumed) : "";
            var dirs = parseRemainingDirectives(remaining);
            return new ExprWithDirs(result.expr, dirs);
        }

        if (trimmed.startsWith("@")) {
            var afterAt = trimmed.substring(1);
            var path = extractPathToken(afterAt);
            int pathEnd = 1 + path.length();
            var remaining = pathEnd < trimmed.length() ? trimmed.substring(pathEnd) : "";
            var dirs = parseRemainingDirectives(remaining);
            return new ExprWithDirs(FieldExpression.copy(path), dirs);
        }

        return new ExprWithDirs(FieldExpression.literal(OdinValue.ofString(raw)), new ArrayList<>());
    }

    private static List<OdinDirective> parseRemainingDirectives(String s) {
        var dirs = new ArrayList<OdinDirective>();
        var trimmed = s.trim();
        if (trimmed.isEmpty()) return dirs;

        int pos = 0;
        while (pos < trimmed.length()) {
            while (pos < trimmed.length() && Character.isWhitespace(trimmed.charAt(pos))) pos++;
            if (pos >= trimmed.length() || trimmed.charAt(pos) != ':') break;

            var result = parseExtractionDirective(trimmed.substring(pos));
            if (result.dir != null) {
                dirs.add(result.dir);
                pos += result.consumed;
            } else {
                break;
            }
        }
        return dirs;
    }

    private record ParsedVerbExpr(FieldExpression expr, int consumed) {}

    private static ParsedVerbExpr parseVerbExpression(String raw) {
        boolean isCustom = raw.startsWith("%&");
        int start = isCustom ? 2 : 1;

        int verbEnd = raw.length();
        for (int i = start; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                verbEnd = i;
                break;
            }
        }
        var verb = raw.substring(start, verbEnd);

        if (verb.isEmpty()) {
            return new ParsedVerbExpr(FieldExpression.literal(OdinValue.ofString(raw)), raw.length());
        }

        int arity = getVerbArity(verb);
        var argsStr = verbEnd < raw.length() ? raw.substring(verbEnd) : "";
        var result = parseExpressionArgs(argsStr, arity);

        var verbCall = new VerbCall();
        verbCall.setVerb(verb);
        verbCall.setIsCustom(isCustom);
        verbCall.setArgs(result.args);
        return new ParsedVerbExpr(FieldExpression.transform(verbCall), verbEnd + result.consumed);
    }

    private record ParsedVerbArg(VerbArg arg, int consumed) {}

    private static ParsedVerbArg parseVerbArgExpression(String raw) {
        boolean isCustom = raw.startsWith("%&");
        int start = isCustom ? 2 : 1;

        int verbEnd = raw.length();
        for (int i = start; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                verbEnd = i;
                break;
            }
        }
        var verb = raw.substring(start, verbEnd);

        if (verb.isEmpty()) {
            return new ParsedVerbArg(VerbArg.lit(OdinValue.ofString(raw)), raw.length());
        }

        int arity = getVerbArity(verb);
        var argsStr = verbEnd < raw.length() ? raw.substring(verbEnd) : "";
        var result = parseExpressionArgs(argsStr, arity);

        var verbCall = new VerbCall();
        verbCall.setVerb(verb);
        verbCall.setIsCustom(isCustom);
        verbCall.setArgs(result.args);
        return new ParsedVerbArg(VerbArg.nestedCall(verbCall), verbEnd + result.consumed);
    }

    private record ParsedArgs(List<VerbArg> args, int consumed) {}

    private static ParsedArgs parseExpressionArgs(String argsStr, int limit) {
        var args = new ArrayList<VerbArg>();
        int pos = 0;

        while (pos < argsStr.length() && Character.isWhitespace(argsStr.charAt(pos))) pos++;

        while (pos < argsStr.length()) {
            if (limit >= 0 && args.size() >= limit) break;
            if (argsStr.charAt(pos) == ':') break;

            if (argsStr.charAt(pos) == '%') {
                var result = parseVerbArgExpression(argsStr.substring(pos));
                args.add(result.arg);
                pos += result.consumed;
            } else if (argsStr.charAt(pos) == '@') {
                int pathStart = pos + 1;
                int pathEnd = findTokenEnd(argsStr, pathStart);
                var path = argsStr.substring(pathStart, pathEnd);
                pos = pathEnd;

                while (pos < argsStr.length() && Character.isWhitespace(argsStr.charAt(pos))) pos++;

                var refDirectives = new ArrayList<OdinDirective>();
                while (pos < argsStr.length() && argsStr.charAt(pos) == ':') {
                    var result = parseExtractionDirective(argsStr.substring(pos));
                    if (result.dir != null) {
                        refDirectives.add(result.dir);
                        pos += result.consumed;
                        while (pos < argsStr.length() && Character.isWhitespace(argsStr.charAt(pos))) pos++;
                    } else {
                        break;
                    }
                }

                args.add(VerbArg.ref(path, refDirectives));
            } else if (argsStr.charAt(pos) == '"') {
                var result = parseQuotedStringArg(argsStr.substring(pos));
                args.add(VerbArg.lit(OdinValue.ofString(result.value)));
                pos += result.consumed;
            } else if (pos + 1 < argsStr.length() && argsStr.charAt(pos) == '#' && argsStr.charAt(pos + 1) == '$') {
                int numStart = pos + 2;
                int numEnd = findNumberEnd(argsStr, numStart);
                var numStr = argsStr.substring(numStart, numEnd);
                try {
                    double v = Double.parseDouble(numStr);
                    int dotIdx = numStr.indexOf('.');
                    byte dp = dotIdx >= 0 ? (byte)(numStr.length() - dotIdx - 1) : (byte)2;
                    args.add(VerbArg.lit(OdinValue.ofCurrency(v, dp)));
                } catch (NumberFormatException ignored) {}
                pos = numEnd;
            } else if (pos + 1 < argsStr.length() && argsStr.charAt(pos) == '#' && argsStr.charAt(pos + 1) == '#') {
                int numStart = pos + 2;
                int numEnd = findNumberEnd(argsStr, numStart);
                var raw = argsStr.substring(numStart, numEnd);
                try {
                    long val = Long.parseLong(raw);
                    args.add(VerbArg.lit(OdinValue.ofInteger(val)));
                } catch (NumberFormatException ignored) {}
                pos = numEnd;
            } else if (argsStr.charAt(pos) == '#') {
                int numStart = pos + 1;
                int numEnd = findNumberEnd(argsStr, numStart);
                var raw = argsStr.substring(numStart, numEnd);
                try {
                    double v = Double.parseDouble(raw);
                    int dotIdx = raw.indexOf('.');
                    Byte dp = dotIdx >= 0 ? (byte)(raw.length() - dotIdx - 1) : null;
                    args.add(VerbArg.lit(dp != null ? OdinValue.ofNumber(v, dp) : OdinValue.ofNumber(v)));
                } catch (NumberFormatException ignored) {}
                pos = numEnd;
            } else if (argsStr.charAt(pos) == '~') {
                args.add(VerbArg.lit(OdinValue.ofNull()));
                pos += 1;
            } else if (pos + 4 <= argsStr.length() && argsStr.substring(pos, pos + 4).equals("true")
                       && (pos + 4 >= argsStr.length() || Character.isWhitespace(argsStr.charAt(pos + 4)))) {
                args.add(VerbArg.lit(OdinValue.ofBoolean(true)));
                pos += 4;
            } else if (pos + 5 <= argsStr.length() && argsStr.substring(pos, pos + 5).equals("false")
                       && (pos + 5 >= argsStr.length() || Character.isWhitespace(argsStr.charAt(pos + 5)))) {
                args.add(VerbArg.lit(OdinValue.ofBoolean(false)));
                pos += 5;
            } else {
                int end = findTokenEnd(argsStr, pos);
                var val = argsStr.substring(pos, end);
                args.add(VerbArg.lit(OdinValue.ofString(val)));
                pos = end;
            }

            while (pos < argsStr.length() && Character.isWhitespace(argsStr.charAt(pos))) pos++;
        }

        return new ParsedArgs(args, pos);
    }

    private static String extractPathToken(String s) {
        int end = findTokenEnd(s, 0);
        return s.substring(0, end);
    }

    private record ParsedDirective(OdinDirective dir, int consumed) {}

    private static ParsedDirective parseExtractionDirective(String s) {
        if (s.isEmpty() || s.charAt(0) != ':') return new ParsedDirective(null, 0);

        int nameStart = 1;
        int nameEnd = s.length();
        for (int i = nameStart; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                nameEnd = i;
                break;
            }
        }
        var name = s.substring(nameStart, nameEnd);

        boolean recognized = switch (name) {
            case "pos", "len", "field", "trim", "type",
                 "date", "time", "duration", "timestamp",
                 "boolean", "integer", "number",
                 "currency", "reference", "binary", "percent",
                 "decimals", "currencyCode",
                 "leftPad", "rightPad", "truncate", "default",
                 "upper", "lower",
                 "required", "confidential", "deprecated", "attr", "ns" -> true;
            default -> false;
        };

        if (!recognized) return new ParsedDirective(null, 0);

        int consumed = nameEnd;

        boolean needsValue = switch (name) {
            case "pos", "len", "field", "type", "decimals",
                 "currencyCode", "leftPad", "rightPad", "default", "ns" -> true;
            default -> false;
        };

        DirectiveValue value = null;
        if (needsValue) {
            while (consumed < s.length() && Character.isWhitespace(s.charAt(consumed))) consumed++;

            if (consumed < s.length()) {
                if (s.charAt(consumed) == '"') {
                    var result = parseQuotedStringArg(s.substring(consumed));
                    consumed += result.consumed;
                    value = DirectiveValue.fromString(result.value);
                } else {
                    int valEnd = s.length();
                    for (int i = consumed; i < s.length(); i++) {
                        if (Character.isWhitespace(s.charAt(i))) {
                            valEnd = i;
                            break;
                        }
                    }
                    var valStr = s.substring(consumed, valEnd);
                    consumed = valEnd;

                    try {
                        double n = Double.parseDouble(valStr);
                        value = DirectiveValue.fromNumber(n);
                    } catch (NumberFormatException e) {
                        value = DirectiveValue.fromString(valStr);
                    }
                }
            }
        }

        return new ParsedDirective(new OdinDirective(name, value), consumed);
    }

    private static int findTokenEnd(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return i;
        }
        return s.length();
    }

    private static int findNumberEnd(String s, int start) {
        int i = start;
        if (i < s.length() && s.charAt(i) == '-') i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') { i++; continue; }
            if ((c == 'e' || c == 'E' || c == '+' || c == '-') && i > start) { i++; continue; }
            break;
        }
        return i == start ? Math.min(s.length(), start + 1) : i;
    }

    private record ParsedString(String value, int consumed) {}

    private static ParsedString parseQuotedStringArg(String s) {
        if (s.isEmpty() || s.charAt(0) != '"') return new ParsedString("", 0);
        var result = new StringBuilder();
        int i = 1;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                switch (s.charAt(i + 1)) {
                    case '"': result.append('"'); i += 2; break;
                    case '\\': result.append('\\'); i += 2; break;
                    case 'n': result.append('\n'); i += 2; break;
                    case 't': result.append('\t'); i += 2; break;
                    case 'r': result.append('\r'); i += 2; break;
                    default: result.append(s.charAt(i)); i++; break;
                }
            } else if (s.charAt(i) == '"') {
                i++;
                break;
            } else {
                result.append(s.charAt(i));
                i++;
            }
        }
        return new ParsedString(result.toString(), i);
    }

    // ── Field Expression Conversion ──

    private static ExprWithDirs valueToFieldExpressionWithDirectives(OdinValue value) {
        return switch (value) {
            case OdinReference refVal ->
                new ExprWithDirs(FieldExpression.copy(refVal.getPath()), new ArrayList<>());

            case OdinVerb verbVal -> {
                if (verbVal.getArgs().isEmpty() && verbVal.getName().startsWith("%")) {
                    yield parseStringExpressionWithDirectives(verbVal.getName());
                }
                var verbCall = new VerbCall();
                verbCall.setVerb(verbVal.getName());
                verbCall.setIsCustom(verbVal.isCustom());
                var args = new ArrayList<VerbArg>();
                for (var arg : verbVal.getArgs()) args.add(odinValueToVerbArg(arg));
                verbCall.setArgs(args);
                yield new ExprWithDirs(FieldExpression.transform(verbCall), new ArrayList<>());
            }

            case OdinObject objVal -> {
                var fieldMappings = new ArrayList<FieldMapping>();
                for (var kvp : objVal.getFields()) {
                    OdinModifiers mods = kvp.getValue().getModifiers();
                    var dirs = new ArrayList<OdinDirective>();
                    if (kvp.getValue().getDirectives() != null) dirs.addAll(kvp.getValue().getDirectives());
                    var inner = valueToFieldExpressionWithDirectives(kvp.getValue());
                    var fm = new FieldMapping();
                    fm.setTarget(kvp.getKey());
                    fm.setExpression(inner.expr);
                    fm.setDirectives(dirs);
                    fm.setModifiers(mods);
                    fieldMappings.add(fm);
                }
                yield new ExprWithDirs(FieldExpression.object(fieldMappings), new ArrayList<>());
            }

            case OdinString strVal -> {
                var trimmed = strVal.getValue().trim();
                if (trimmed.startsWith("@")) yield parseStringExpressionWithDirectives(trimmed);
                if (trimmed.startsWith("%")) yield parseStringExpressionWithDirectives(trimmed);
                if (trimmed.startsWith("$const.") || trimmed.startsWith("$constants."))
                    yield new ExprWithDirs(FieldExpression.copy(trimmed), new ArrayList<>());
                yield new ExprWithDirs(FieldExpression.literal(value), new ArrayList<>());
            }

            default -> new ExprWithDirs(FieldExpression.literal(value), new ArrayList<>());
        };
    }

    private static final String[] FORMATTING_DIRECTIVE_NAMES = {
        "pos", "len", "leftPad", "rightPad", "truncate", "default", "upper", "lower"
    };

    private static List<OdinDirective> collectFormattingDirectives(FieldExpression expr) {
        var collected = new ArrayList<OdinDirective>();
        if (expr instanceof TransformExpression txExpr) {
            collectFromVerbArgs(txExpr.getCall().getArgs(), collected);
        }
        return collected;
    }

    private static void collectFromVerbArgs(List<VerbArg> args, List<OdinDirective> collected) {
        for (var arg : args) {
            if (arg instanceof ReferenceArg refArg) {
                for (var dir : refArg.getDirectives()) {
                    boolean isFormatting = false;
                    for (var name : FORMATTING_DIRECTIVE_NAMES) {
                        if (dir.getName().equals(name)) { isFormatting = true; break; }
                    }
                    if (!isFormatting) continue;
                    boolean exists = false;
                    for (var d : collected) if (d.getName().equals(dir.getName())) { exists = true; break; }
                    if (!exists) collected.add(dir);
                }
            } else if (arg instanceof VerbCallArg vcArg) {
                collectFromVerbArgs(vcArg.getNestedCall().getArgs(), collected);
            }
        }
    }

    private static VerbArg odinValueToVerbArg(OdinValue value) {
        return switch (value) {
            case OdinReference refVal -> {
                var dirs = new ArrayList<OdinDirective>();
                if (refVal.getDirectives() != null) dirs.addAll(refVal.getDirectives());
                yield VerbArg.ref(refVal.getPath(), dirs);
            }
            case OdinVerb verbVal -> {
                var args = new ArrayList<VerbArg>();
                for (var arg : verbVal.getArgs()) args.add(odinValueToVerbArg(arg));
                var vc = new VerbCall();
                vc.setVerb(verbVal.getName());
                vc.setIsCustom(verbVal.isCustom());
                vc.setArgs(args);
                yield VerbArg.nestedCall(vc);
            }
            default -> VerbArg.lit(value);
        };
    }

    // ── Pass Collection ──

    private static List<Integer> collectPasses(List<TransformSegment> segments) {
        var passes = new ArrayList<Integer>();
        collectPassesRecursive(segments, passes);
        passes.sort(null);
        var deduped = new ArrayList<Integer>();
        int prev = -1;
        for (var p : passes) {
            if (p != prev) {
                deduped.add(p);
                prev = p;
            }
        }
        return deduped;
    }

    private static void collectPassesRecursive(List<TransformSegment> segments, List<Integer> passes) {
        for (var seg : segments) {
            if (seg.getPass() != null) passes.add(seg.getPass());
            collectPassesRecursive(seg.getChildren(), passes);
        }
    }

    // ── Helpers ──

    private static String getMetaString(OdinDocument doc, String key) {
        var value = doc.getMetadata().tryGet(key);
        if (value == null) return null;
        return odinValueToString(value);
    }

    private static Integer parseMetaInt(OdinDocument doc, String key) {
        var str = getMetaString(doc, key);
        if (str == null) return null;
        try { return Integer.parseInt(str); } catch (NumberFormatException e) { return null; }
    }

    static String odinValueToString(OdinValue value) {
        return switch (value) {
            case OdinString s -> s.getValue();
            case OdinTime t -> t.getValue();
            case OdinDuration d -> d.getValue();
            case OdinBoolean b -> b.getValue() ? "true" : "false";
            case OdinInteger i -> i.getRaw() != null ? i.getRaw() : Long.toString(i.getValue());
            case OdinNumber n -> n.getRaw() != null ? n.getRaw() : Double.toString(n.getValue());
            case OdinCurrency c -> c.getRaw() != null ? c.getRaw() : Double.toString(c.getValue());
            case OdinPercent p -> p.getRaw() != null ? p.getRaw() : Double.toString(p.getValue());
            case OdinValue.OdinNull ignored -> "~";
            case OdinReference r -> "@" + r.getPath();
            case OdinDate d -> d.getRaw();
            case OdinTimestamp ts -> ts.getRaw();
            case OdinBinary ignored -> "<binary>";
            case OdinVerb v -> "%" + v.getName();
            case OdinArray a -> "[" + a.getItems().size() + " items]";
            case OdinObject o -> "{" + o.getFields().size() + " fields}";
            default -> value.toString();
        };
    }

    private static DynValue odinValueToDynForTable(OdinValue val) {
        return switch (val) {
            case OdinValue.OdinNull ignored -> DynValue.ofNull();
            case OdinBoolean b -> DynValue.ofBool(b.getValue());
            case OdinString s -> DynValue.ofString(s.getValue());
            case OdinTime t -> DynValue.ofString(t.getValue());
            case OdinDuration d -> DynValue.ofString(d.getValue());
            case OdinInteger i -> DynValue.ofInteger(i.getValue());
            case OdinNumber n -> DynValue.ofFloat(n.getValue());
            case OdinCurrency c -> DynValue.ofFloat(c.getValue());
            case OdinPercent p -> DynValue.ofFloat(p.getValue());
            case OdinDate d -> DynValue.ofString(d.getRaw());
            case OdinTimestamp ts -> DynValue.ofString(ts.getRaw());
            case OdinReference r -> DynValue.ofString(r.getPath());
            default -> DynValue.ofString(odinValueToString(val));
        };
    }
}
