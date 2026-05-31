package foundation.odin.transform;

import foundation.odin.types.*;
import foundation.odin.types.OdinTransformTypes.*;
import foundation.odin.types.OdinTransformTypes.FieldExpression.*;
import foundation.odin.types.OdinTransformTypes.VerbArg.*;
import foundation.odin.types.OdinValue.*;

import java.util.*;
import java.util.function.BiFunction;

public final class TransformEngine {

    private TransformEngine() {}

    // ── Verb Registry ──

    private static final VerbRegistry verbRegistry = new VerbRegistry();

    public static void registerVerb(String name, BiFunction<DynValue[], VerbContext, DynValue> fn) {
        verbRegistry.registerCustom(name, fn);
    }

    public static DynValue invokeVerb(String name, DynValue[] args, VerbContext ctx) {
        return verbRegistry.invoke(name, args, ctx);
    }

    public static DynValue invokeVerb(String name, DynValue... args) {
        var ctx = new VerbContext();
        ctx.source = DynValue.ofNull();
        ctx.loopVars = new LinkedHashMap<>();
        ctx.accumulators = new LinkedHashMap<>();
        ctx.tables = new LinkedHashMap<>();
        ctx.globalOutput = DynValue.ofNull();
        return invokeVerb(name, args, ctx);
    }

    // ── VerbContext ──

    public static final class VerbContext {
        private DynValue source = DynValue.ofNull();
        private Map<String, DynValue> loopVars = new LinkedHashMap<>();
        private Map<String, DynValue> accumulators = new LinkedHashMap<>();
        private Map<String, LookupTable> tables = new LinkedHashMap<>();
        private DynValue globalOutput = DynValue.ofNull();

        public DynValue getSource() { return source; }
        public Map<String, DynValue> getLoopVars() { return loopVars; }
        public Map<String, DynValue> getAccumulators() { return accumulators; }
        public Map<String, LookupTable> getTables() { return tables; }
        public DynValue getGlobalOutput() { return globalOutput; }
    }

    // ── ExecContext ──

    private static final class ExecContext {
        DynValue source;
        Map<String, DynValue> constants;
        Map<String, DynValue> accumulators;
        Map<String, LookupTable> tables;
        Map<String, DynValue> loopVars = new LinkedHashMap<>();
        Map<String, BiFunction<DynValue[], VerbContext, DynValue>> verbs;
        List<TransformWarning> warnings = new ArrayList<>();
        List<TransformError> errors = new ArrayList<>();
        ConfidentialMode enforceConfidential;
        DynValue globalOutput;
        Map<String, OdinModifiers> fieldModifiers = new LinkedHashMap<>();
        String sourceFormat;
    }

    // ── Public Entry Point ──

    public static TransformResult execute(OdinTransform transform, DynValue source) {
        // Check for multi-record mode (discriminator dispatch)
        if (transform.getSource() != null) {
            String discConfig = null;
            var disc = transform.getSource().getDiscriminator();
            if (disc != null) {
                discConfig = switch (disc.getType()) {
                    case POSITION -> ":pos " + (disc.getPos() != null ? disc.getPos() : 0)
                            + " :len " + (disc.getLen() != null ? disc.getLen() : 1);
                    case FIELD -> ":field " + (disc.getField() != null ? disc.getField() : 0);
                    case PATH -> ":path " + (disc.getPath() != null ? disc.getPath() : "");
                };
            }
            if (discConfig == null) {
                discConfig = transform.getSource().getOptions().get("discriminator");
            }
            if (discConfig != null && source.getType() == DynValue.Type.String) {
                return executeMultiRecord(transform, source.asString(), discConfig, transform.getSource().getFormat());
            }
        }

        // Auto-parse raw string sources into structured DynValue
        if (source.getType() == DynValue.Type.String) {
            String srcFmt = null;
            if (transform.getSource() != null && transform.getSource().getFormat() != null && !transform.getSource().getFormat().isEmpty())
                srcFmt = transform.getSource().getFormat();
            else if (transform.getMetadata().getDirection() != null) {
                var parts = transform.getMetadata().getDirection().split("->");
                if (parts.length > 0) srcFmt = parts[0].trim();
            }
            if (srcFmt != null) {
                var parsed = parseSourceFormat(source.asString(), srcFmt);
                if (parsed != null) return execute(transform, parsed);
            }
        }

        var ctx = buildContext(transform, source);
        var output = DynValue.ofObject(new ArrayList<>());

        var segments = orderSegmentsByPass(transform.getSegments());

        Integer currentPass = null;
        boolean isFirstPass = true;
        for (var segment : segments) {
            Integer segPass = segment.getPass();
            if (!Objects.equals(segPass, currentPass) && !isFirstPass) {
                // Reset non-persist accumulators on pass change
                for (var kvp : transform.getAccumulators().entrySet()) {
                    if (!kvp.getValue().isPersist()) {
                        ctx.accumulators.put(kvp.getKey(), odinValueToDyn(kvp.getValue().getInitial()));
                    }
                }
            }
            currentPass = segPass;
            isFirstPass = false;

            output = processSegment(segment, ctx, output, "");
            ctx.globalOutput = output;
        }

        // Confidential enforcement
        if (ctx.enforceConfidential != null) {
            applyConfidentialEnforcement(transform.getSegments(), ctx.enforceConfidential, output);
        }

        // Format output
        String formatted = formatOutput(output, transform.getTarget().getFormat(),
                transform.getTarget().getOptions(), transform.getSegments(), ctx.fieldModifiers);

        var result = new TransformResult();
        result.setSuccess(ctx.errors.isEmpty());
        result.setOutput(output);
        result.setFormatted(formatted);
        result.setErrors(ctx.errors);
        result.setWarnings(ctx.warnings);
        result.setOutputModifiers(ctx.fieldModifiers);
        return result;
    }

    // ── Multi-Record Execution ──

    private enum DiscriminatorMode { POSITION, FIELD }

    private record DiscriminatorConfig(DiscriminatorMode mode, int pos, int len, int fieldIndex) {}

    private static DiscriminatorConfig parseDiscriminatorConfig(String config) {
        var parts = config.trim().split("\\s+");
        Integer pos = null, len = null, field = null;
        for (int i = 0; i < parts.length; i++) {
            if (":pos".equals(parts[i]) && i + 1 < parts.length) {
                try { pos = Integer.parseInt(parts[++i]); } catch (NumberFormatException ignored) {}
            } else if (":len".equals(parts[i]) && i + 1 < parts.length) {
                try { len = Integer.parseInt(parts[++i]); } catch (NumberFormatException ignored) {}
            } else if (":field".equals(parts[i]) && i + 1 < parts.length) {
                try { field = Integer.parseInt(parts[++i]); } catch (NumberFormatException ignored) {}
            }
        }
        if (field != null) return new DiscriminatorConfig(DiscriminatorMode.FIELD, 0, 0, field);
        if (pos != null && len != null) return new DiscriminatorConfig(DiscriminatorMode.POSITION, pos, len, 0);
        return null;
    }

    private static String extractDiscriminatorValue(String line, DiscriminatorMode mode, int pos, int len, int fieldIndex, String delimiter) {
        if (mode == DiscriminatorMode.POSITION) {
            if (pos + len <= line.length()) return line.substring(pos, pos + len).trim();
            if (pos < line.length()) return line.substring(pos).trim();
            return "";
        } else {
            var fields = line.split(delimiter.contains(",") ? "," : delimiter, -1);
            if (fieldIndex < fields.length) return fields[fieldIndex].trim();
            return "";
        }
    }

    private static DynValue parseSourceFormat(String input, String format) {
        try {
            return switch (format) {
                case "json" -> JsonSourceParser.parse(input);
                case "xml" -> XmlSourceParser.parse(input);
                case "csv" -> CsvSourceParser.parse(input, null);
                case "flat", "properties", "flat-kvp" -> FlatSourceParser.parse(input);
                case "yaml", "flat-yaml" -> YamlSourceParser.parse(input);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static DynValue parseRecord(String line, String format, String delimiter) {
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        entries.add(Map.entry("_raw", DynValue.ofString(line)));
        entries.add(Map.entry("_line", DynValue.ofString(line)));

        if ("csv".equals(format) || "delimited".equals(format)) {
            var fields = line.split(delimiter.contains(",") ? "," : delimiter, -1);
            for (int i = 0; i < fields.length; i++)
                entries.add(Map.entry(String.valueOf(i), DynValue.ofString(fields[i])));
        }

        return DynValue.ofObject(entries);
    }

    private static TransformResult executeMultiRecord(OdinTransform transform, String rawInput, String discConfig, String sourceFormat) {
        var parsed = parseDiscriminatorConfig(discConfig);
        if (parsed == null) {
            var result = new TransformResult();
            result.setSuccess(false);
            result.setErrors(List.of(new TransformError("Invalid discriminator config: " + discConfig, "")));
            return result;
        }

        var mode = parsed.mode();
        int pos = parsed.pos();
        int len = parsed.len();
        int fieldIndex = parsed.fieldIndex();

        String delimiter = ",";
        if (transform.getSource() != null) {
            var delimVal = transform.getSource().getOptions().get("delimiter");
            if (delimVal != null) delimiter = delimVal;
        }

        // Build segment routing map: _type literal value → segment
        var segmentMap = new LinkedHashMap<String, TransformSegment>();
        for (var seg : transform.getSegments()) {
            for (var mapping : seg.getMappings()) {
                if ("_type".equals(mapping.getTarget()) && mapping.getExpression() instanceof FieldExpression.LiteralExpression litExpr) {
                    if (litExpr.getValue() instanceof OdinValue.OdinString s) {
                        for (var typeVal : s.getValue().split(","))
                            segmentMap.put(typeVal.trim(), seg);
                    }
                }
            }
            // Also check items list
            for (var item : seg.getItems()) {
                var m = item.asMapping();
                if (m != null && "_type".equals(m.getTarget()) && m.getExpression() instanceof FieldExpression.LiteralExpression litExpr) {
                    if (litExpr.getValue() instanceof OdinValue.OdinString s) {
                        for (var typeVal : s.getValue().split(","))
                            segmentMap.put(typeVal.trim(), seg);
                    }
                }
            }
        }

        var ctx = buildContext(transform, DynValue.ofNull());
        ctx.sourceFormat = sourceFormat;

        var output = DynValue.ofObject(new ArrayList<>());
        var arrayAccumulators = new LinkedHashMap<String, List<DynValue>>();

        // Initialize array accumulators
        for (var seg : transform.getSegments()) {
            if (seg.getName().endsWith("[]")) {
                var arrName = seg.getName().substring(0, seg.getName().length() - 2);
                arrayAccumulators.put(arrName, new ArrayList<>());
            }
        }

        // Process each record
        var lines = rawInput.split("[\\r\\n]+");
        for (var line : lines) {
            if (line.trim().isEmpty()) continue;

            var discValue = extractDiscriminatorValue(line, mode, pos, len, fieldIndex, delimiter);
            var segment = segmentMap.get(discValue);
            if (segment == null) continue;

            var recordSource = parseRecord(line, sourceFormat, delimiter);
            var recordOutput = DynValue.ofObject(new ArrayList<>());

            // Process items (interleaved mappings/children)
            for (var item : segment.getItems()) {
                var m = item.asMapping();
                if (m != null) {
                    if ("_type".equals(m.getTarget())) continue;
                    recordOutput = processMapping(m, ctx, recordSource, recordOutput, "");
                }
                var child = item.asChild();
                if (child != null) {
                    for (var cm : child.getMappings()) {
                        var fullTarget = child.getName() + "." + cm.getTarget();
                        var wrapper = new FieldMapping();
                        wrapper.setTarget(fullTarget);
                        wrapper.setExpression(cm.getExpression());
                        wrapper.setDirectives(cm.getDirectives());
                        wrapper.setModifiers(cm.getModifiers());
                        recordOutput = processMapping(wrapper, ctx, recordSource, recordOutput, "");
                    }
                }
            }

            // If items list is empty, fallback to mappings list
            if (segment.getItems().isEmpty()) {
                for (var mapping : segment.getMappings()) {
                    if ("_type".equals(mapping.getTarget())) continue;
                    recordOutput = processMapping(mapping, ctx, recordSource, recordOutput, "");
                }
            }

            // Merge into output
            var segName = segment.getName().endsWith("[]")
                    ? segment.getName().substring(0, segment.getName().length() - 2)
                    : segment.getName();

            if (segment.getName().endsWith("[]")) {
                var accList = arrayAccumulators.get(segName);
                if (accList != null) accList.add(recordOutput);
            } else {
                mergeRecordIntoOutput(output, segName, recordOutput);
            }
        }

        // Merge array accumulators into output in segment order
        var outputEntries = output.asObject();
        if (outputEntries != null) {
            for (var seg : transform.getSegments()) {
                if (!seg.getName().endsWith("[]")) continue;
                var arrName = seg.getName().substring(0, seg.getName().length() - 2);
                var items = arrayAccumulators.get(arrName);
                if (items != null) {
                    outputEntries.add(Map.entry(arrName, DynValue.ofArray(items)));
                }
            }
        }

        String formatted = formatOutput(output, transform.getTarget().getFormat(),
                transform.getTarget().getOptions(), transform.getSegments(), ctx.fieldModifiers);

        var result = new TransformResult();
        result.setSuccess(ctx.errors.isEmpty());
        result.setOutput(output);
        result.setFormatted(formatted);
        result.setErrors(ctx.errors);
        result.setWarnings(ctx.warnings);
        result.setOutputModifiers(ctx.fieldModifiers);
        return result;
    }

    private static void mergeRecordIntoOutput(DynValue output, String segName, DynValue recordOutput) {
        var entries = output.asObject();
        if (entries == null) return;
        var recEntries = recordOutput.asObject();
        if (recEntries == null) return;

        int existingIdx = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(segName)) { existingIdx = i; break; }
        }

        if (existingIdx >= 0) {
            var existing = entries.get(existingIdx).getValue().asObject();
            if (existing != null) {
                existing.addAll(recEntries);
            }
        } else {
            entries.add(Map.entry(segName, DynValue.ofObject(new ArrayList<>(recEntries))));
        }
    }

    // ── Context Setup ──

    private static ExecContext buildContext(OdinTransform transform, DynValue source) {
        var ctx = new ExecContext();
        ctx.source = source != null ? source : DynValue.ofNull();
        ctx.constants = new LinkedHashMap<>();
        for (var entry : transform.getConstants().entrySet()) {
            ctx.constants.put(entry.getKey(), odinValueToDyn(entry.getValue()));
        }
        ctx.accumulators = new LinkedHashMap<>();
        for (var entry : transform.getAccumulators().entrySet()) {
            ctx.accumulators.put(entry.getKey(), odinValueToDyn(entry.getValue().getInitial()));
        }
        ctx.tables = transform.getTables();
        ctx.verbs = verbRegistry.toMap();
        ctx.enforceConfidential = transform.getEnforceConfidential();
        ctx.globalOutput = DynValue.ofObject(new ArrayList<>());
        ctx.sourceFormat = transform.getSource() != null ? transform.getSource().getFormat() : "";
        return ctx;
    }

    // ── Segment Processing ──

    private static DynValue processSegment(TransformSegment segment, ExecContext ctx, DynValue output, String pathPrefix) {
        // Condition check
        if (segment.getCondition() != null) {
            if (!evaluateCondition(segment.getCondition(), ctx)) return output;
        }

        // Discriminator check
        if (segment.getSegmentDiscriminator() != null) {
            var disc = segment.getSegmentDiscriminator();
            var discVal = resolvePath(ctx.source, disc.getPath(), ctx.constants, ctx.accumulators);
            if (discVal == null || !matchDiscriminator(discVal, disc.getValue())) return output;
        }

        String name = segment.getName();
        boolean isRoot = name.isEmpty() || name.equals("$") || name.equals("_root");

        // Clean name (remove [] suffix)
        String cleanName = name.endsWith("[]") ? name.substring(0, name.length() - 2) : name;

        // Parse array index [N]
        Integer arrayIndex = null;
        int bracketPos = cleanName.indexOf('[');
        if (bracketPos >= 0 && cleanName.endsWith("]")) {
            try {
                arrayIndex = Integer.parseInt(cleanName.substring(bracketPos + 1, cleanName.length() - 1));
                cleanName = cleanName.substring(0, bracketPos);
            } catch (NumberFormatException ignored) {}
        }

        String currentPrefix = isRoot ? pathPrefix
                : (pathPrefix.isEmpty() ? cleanName : pathPrefix + "." + cleanName);

        // Side-effect-only segments (name starts with _)
        if (name.startsWith("_") && !isRoot && arrayIndex == null) {
            for (var mapping : segment.getMappings()) {
                processMapping(mapping, ctx, ctx.source, output, currentPrefix);
            }
            return output;
        }

        // Array loop
        if (segment.getSourcePath() != null) {
            var arrayVal = resolvePath(ctx.source, segment.getSourcePath(), ctx.constants, ctx.accumulators);
            var items = arrayVal != null && arrayVal.asArray() != null ? arrayVal.asArray() : new ArrayList<DynValue>();
            if (arrayVal != null && arrayVal.asArray() == null && !arrayVal.isNull()) {
                items = List.of(arrayVal);
            }

            var resultItems = new ArrayList<DynValue>();
            boolean isValueOnly = segment.getMappings().stream().allMatch(m -> m.getTarget().equals("_"));
            for (int idx = 0; idx < items.size(); idx++) {
                var item = items.get(idx);
                ctx.loopVars.put("_item", item);
                ctx.loopVars.put("_index", DynValue.ofInteger(idx));
                ctx.loopVars.put("_length", DynValue.ofInteger(items.size()));

                var rowOutput = DynValue.ofObject(new ArrayList<>());
                for (var mapping : segment.getMappings()) {
                    if (mapping.getTarget().equals("_")) {
                        // Identity mapping: row becomes the evaluated value itself
                        try {
                            var val = evaluateExpression(mapping.getExpression(), ctx, item, rowOutput);
                            val = applyMappingDirectives(val, mapping.getDirectives(), ctx.sourceFormat, mapping.getExpression());
                            if (isValueOnly) {
                                rowOutput = val;
                            }
                            // else: side effect only (e.g., accumulate already happened during evaluation)
                        } catch (Exception e) {
                            ctx.errors.add(new TransformError("mapping '_': " + e.getMessage(), "_"));
                        }
                    } else {
                        rowOutput = processMapping(mapping, ctx, item, rowOutput, currentPrefix);
                    }
                }
                resultItems.add(rowOutput);
            }

            ctx.loopVars.remove("_item");
            ctx.loopVars.remove("_index");
            ctx.loopVars.remove("_length");

            var arrayResult = DynValue.ofArray(resultItems);
            if (!isRoot) {
                setPath(output, cleanName, arrayResult);
            } else {
                output = arrayResult;
            }
            return output;
        }

        // Standard segment (no loop)
        if (isRoot) {
            for (var mapping : segment.getMappings()) {
                output = processMapping(mapping, ctx, ctx.source, output, pathPrefix);
            }
            for (var child : segment.getChildren()) {
                output = processSegment(child, ctx, output, pathPrefix);
            }
        } else if (arrayIndex != null) {
            // Array-indexed segment (e.g., {vehicles[0]}) → insert into array at index
            var segOutput = DynValue.ofObject(new ArrayList<>());
            for (var mapping : segment.getMappings()) {
                segOutput = processMapping(mapping, ctx, ctx.source, segOutput, currentPrefix);
            }
            for (var child : segment.getChildren()) {
                segOutput = processSegment(child, ctx, segOutput, currentPrefix);
            }
            // Get or create the array at cleanName
            var outputEntries = output.asObject();
            if (outputEntries != null) {
                int existingIdx = -1;
                for (int i = 0; i < outputEntries.size(); i++) {
                    if (outputEntries.get(i).getKey().equals(cleanName)) { existingIdx = i; break; }
                }
                List<DynValue> arr;
                if (existingIdx >= 0) {
                    var existing = outputEntries.get(existingIdx).getValue();
                    arr = existing.asArray() != null ? new ArrayList<>(existing.asArray()) : new ArrayList<>();
                } else {
                    arr = new ArrayList<>();
                }
                // Extend array to fit the index
                while (arr.size() <= arrayIndex) arr.add(DynValue.ofNull());
                arr.set(arrayIndex, segOutput);
                var arrayVal = DynValue.ofArray(arr);
                if (existingIdx >= 0) {
                    outputEntries.set(existingIdx, Map.entry(cleanName, arrayVal));
                } else {
                    outputEntries.add(Map.entry(cleanName, arrayVal));
                }
            }
        } else {
            // Named segment → create nested object
            var segOutput = getOrCreateObject(output, cleanName);
            for (var mapping : segment.getMappings()) {
                segOutput = processMapping(mapping, ctx, ctx.source, segOutput, currentPrefix);
            }
            for (var child : segment.getChildren()) {
                segOutput = processSegment(child, ctx, segOutput, currentPrefix);
            }
            setPath(output, cleanName, segOutput);
        }

        return output;
    }

    // ── Mapping Processing ──

    private static DynValue processMapping(FieldMapping mapping, ExecContext ctx, DynValue currentSource, DynValue output, String pathPrefix) {
        try {
            var value = evaluateExpression(mapping.getExpression(), ctx, currentSource, output);

            // Apply mapping directives (type coercion, extraction)
            value = applyMappingDirectives(value, mapping.getDirectives(), ctx.sourceFormat, mapping.getExpression());

            // Confidential during processing
            if (mapping.getModifiers() != null && mapping.getModifiers().isConfidential() && ctx.enforceConfidential != null) {
                value = applyConfidentialToValue(value, ctx.enforceConfidential);
            }

            if (!mapping.getTarget().equals("_")) {
                setPath(output, mapping.getTarget(), value);

                // Record modifiers
                if (mapping.getModifiers() != null && mapping.getModifiers().hasAny()) {
                    var fullKey = pathPrefix.isEmpty() ? mapping.getTarget() : pathPrefix + "." + mapping.getTarget();
                    ctx.fieldModifiers.put(fullKey, mapping.getModifiers());
                }
            }
        } catch (Exception e) {
            ctx.errors.add(new TransformError(e.getMessage(), mapping.getTarget()));
        }
        return output;
    }

    // ── Expression Evaluation ──

    private static DynValue evaluateExpression(FieldExpression expr, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        return switch (expr) {
            case CopyExpression copy -> {
                var path = copy.getPath();

                // Check loop vars
                if (ctx.loopVars.containsKey(path)) yield ctx.loopVars.get(path);
                if (path.startsWith("_item") || path.startsWith("@_item")) {
                    var cleanPath = path.startsWith("@") ? path.substring(1) : path;
                    if (cleanPath.equals("_item")) yield ctx.loopVars.getOrDefault("_item", DynValue.ofNull());
                    if (cleanPath.startsWith("_item.")) {
                        var subPath = cleanPath.substring("_item.".length());
                        var item = ctx.loopVars.get("_item");
                        yield item != null ? resolveSubPath(item, subPath) : DynValue.ofNull();
                    }
                }
                if (path.equals("_index") || path.equals("@_index")) yield ctx.loopVars.getOrDefault("_index", DynValue.ofNull());
                if (path.equals("_length") || path.equals("@_length")) yield ctx.loopVars.getOrDefault("_length", DynValue.ofNull());

                yield resolvePathWithOutput(currentSource, currentOutput, ctx.globalOutput, path, ctx.constants, ctx.accumulators);
            }

            case LiteralExpression lit -> odinValueToDyn(lit.getValue());

            case TransformExpression txExpr -> executeVerbCall(txExpr.getCall(), ctx, currentSource, currentOutput);

            case ObjectExpression objExpr -> {
                var entries = new ArrayList<Map.Entry<String, DynValue>>();
                for (var field : objExpr.getFields()) {
                    var val = evaluateExpression(field.getExpression(), ctx, currentSource, currentOutput);
                    entries.add(Map.entry(field.getTarget(), val != null ? val : DynValue.ofNull()));
                }
                yield DynValue.ofObject(entries);
            }
        };
    }

    // ── Verb Call Execution ──

    private static DynValue executeVerbCall(VerbCall call, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        // Short-circuit: ifElse
        if (call.getVerb().equals("ifElse") && call.getArgs().size() >= 3) {
            var condition = evaluateVerbArg(call.getArgs().get(0), ctx, currentSource, currentOutput);
            return isTruthy(condition)
                    ? evaluateVerbArg(call.getArgs().get(1), ctx, currentSource, currentOutput)
                    : evaluateVerbArg(call.getArgs().get(2), ctx, currentSource, currentOutput);
        }

        // Short-circuit: cond
        if (call.getVerb().equals("cond") && !call.getArgs().isEmpty()) {
            var args = call.getArgs();
            for (int i = 0; i + 1 < args.size(); i += 2) {
                var condition = evaluateVerbArg(args.get(i), ctx, currentSource, currentOutput);
                if (isTruthy(condition)) {
                    return evaluateVerbArg(args.get(i + 1), ctx, currentSource, currentOutput);
                }
            }
            if (args.size() % 2 == 1) {
                return evaluateVerbArg(args.get(args.size() - 1), ctx, currentSource, currentOutput);
            }
            return DynValue.ofNull();
        }

        // Evaluate all arguments
        var evaluatedArgs = new DynValue[call.getArgs().size()];
        for (int i = 0; i < call.getArgs().size(); i++) {
            evaluatedArgs[i] = evaluateVerbArg(call.getArgs().get(i), ctx, currentSource, currentOutput);
        }

        // Look up verb
        var verbFn = ctx.verbs.get(call.getVerb());
        if (verbFn == null) {
            if (call.isCustom()) {
                return evaluatedArgs.length > 0 ? evaluatedArgs[0] : DynValue.ofNull();
            }
            throw new UnsupportedOperationException("Unknown verb: " + call.getVerb());
        }

        var verbCtx = new VerbContext();
        verbCtx.source = ctx.source;
        verbCtx.loopVars = new LinkedHashMap<>(ctx.loopVars);
        verbCtx.accumulators = ctx.accumulators;
        verbCtx.tables = ctx.tables;
        verbCtx.globalOutput = ctx.globalOutput;

        var result = verbFn.apply(evaluatedArgs, verbCtx);

        // Update accumulators if accumulate/set verb
        if (call.getVerb().equals("accumulate") || call.getVerb().equals("set")) {
            if (evaluatedArgs.length >= 1) {
                var nameStr = coerceToString(evaluatedArgs[0]);
                if (!nameStr.isEmpty()) ctx.accumulators.put(nameStr, result);
            }
        }

        return result;
    }

    private static DynValue evaluateVerbArg(VerbArg arg, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        return switch (arg) {
            case ReferenceArg refArg -> {
                var path = refArg.getPath();
                DynValue resolved;

                // Loop vars
                if (ctx.loopVars.containsKey(path)) resolved = ctx.loopVars.get(path);
                else if (path.startsWith("_item") || path.startsWith("@_item")) {
                    var cleanPath = path.startsWith("@") ? path.substring(1) : path;
                    if (cleanPath.equals("_item")) resolved = ctx.loopVars.getOrDefault("_item", DynValue.ofNull());
                    else if (cleanPath.startsWith("_item.")) {
                        var subPath = cleanPath.substring("_item.".length());
                        var item = ctx.loopVars.get("_item");
                        resolved = item != null ? resolveSubPath(item, subPath) : DynValue.ofNull();
                    } else resolved = resolvePathWithOutput(currentSource, currentOutput, ctx.globalOutput, path, ctx.constants, ctx.accumulators);
                } else if (path.equals("_index") || path.equals("@_index")) resolved = ctx.loopVars.getOrDefault("_index", DynValue.ofNull());
                else if (path.equals("_length") || path.equals("@_length")) resolved = ctx.loopVars.getOrDefault("_length", DynValue.ofNull());
                else resolved = resolvePathWithOutput(currentSource, currentOutput, ctx.globalOutput, path, ctx.constants, ctx.accumulators);

                // Apply directives (e.g., :pos, :len for FWF extraction)
                if (refArg.getDirectives() != null && !refArg.getDirectives().isEmpty()) {
                    resolved = applyDirectivesForSource(resolved, refArg.getDirectives(), ctx.sourceFormat);
                }

                yield resolved;
            }
            case LiteralArg litArg -> odinValueToDyn(litArg.getValue());
            case VerbCallArg vcArg -> executeVerbCall(vcArg.getNestedCall(), ctx, currentSource, currentOutput);
        };
    }

    // ── Path Resolution ──

    static DynValue resolvePath(DynValue source, String path, Map<String, DynValue> constants, Map<String, DynValue> accumulators) {
        if (path == null || path.isEmpty()) return source;

        // Constants
        if (path.startsWith("$const.")) {
            var name = path.substring("$const.".length());
            return constants.getOrDefault(name, DynValue.ofNull());
        }
        if (path.startsWith("$constants.")) {
            var name = path.substring("$constants.".length());
            return constants.getOrDefault(name, DynValue.ofNull());
        }

        // Accumulators
        if (path.startsWith("$accumulator.")) {
            var name = path.substring("$accumulator.".length());
            return accumulators.getOrDefault(name, DynValue.ofNull());
        }
        if (path.startsWith("$accumulators.")) {
            var name = path.substring("$accumulators.".length());
            return accumulators.getOrDefault(name, DynValue.ofNull());
        }

        // Strip @ prefix and leading dot
        var cleaned = path;
        if (cleaned.startsWith("@")) cleaned = cleaned.substring(1);
        if (cleaned.startsWith(".")) cleaned = cleaned.substring(1);

        if (cleaned.isEmpty()) return source;

        return resolveSubPath(source, cleaned);
    }

    private static DynValue resolvePathWithOutput(DynValue source, DynValue output, DynValue globalOutput,
                                                   String path, Map<String, DynValue> constants, Map<String, DynValue> accumulators) {
        if (path == null || path.isEmpty()) return source;

        // Constants/accumulators
        if (path.startsWith("$const.") || path.startsWith("$constants.")
            || path.startsWith("$accumulator.") || path.startsWith("$accumulators.")) {
            return resolvePath(source, path, constants, accumulators);
        }

        // @ prefix → source
        if (path.startsWith("@") || path.startsWith(".")) {
            return resolvePath(source, path, constants, accumulators);
        }

        // Bare path → try output, globalOutput, then source
        var fromOutput = resolvePath(output, path, constants, accumulators);
        if (fromOutput != null && !fromOutput.isNull()) return fromOutput;

        if (globalOutput != null) {
            var fromGlobal = resolvePath(globalOutput, path, constants, accumulators);
            if (fromGlobal != null && !fromGlobal.isNull()) return fromGlobal;
        }

        return resolvePath(source, path, constants, accumulators);
    }

    private static DynValue resolveSubPath(DynValue value, String path) {
        if (value == null || path == null || path.isEmpty()) return value;

        var segments = parsePathSegments(path);
        var current = value;
        for (var seg : segments) {
            if (current == null || current.isNull()) return DynValue.ofNull();

            if (seg.index >= 0) {
                // field[index]
                if (!seg.field.isEmpty()) {
                    current = current.get(seg.field);
                    if (current == null) return DynValue.ofNull();
                }
                current = current.getIndex(seg.index);
            } else {
                current = current.get(seg.field);
            }

            if (current == null) return DynValue.ofNull();
        }
        return current;
    }

    private record PathSeg(String field, int index) {}

    private static List<PathSeg> parsePathSegments(String path) {
        var segments = new ArrayList<PathSeg>();
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '.') { i++; continue; }

            // Find end of field name
            int fieldStart = i;
            while (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[') i++;
            var field = path.substring(fieldStart, i);

            // Check for array index
            if (i < path.length() && path.charAt(i) == '[') {
                int closePos = path.indexOf(']', i);
                if (closePos > i) {
                    try {
                        int idx = Integer.parseInt(path.substring(i + 1, closePos));
                        segments.add(new PathSeg(field, idx));
                        i = closePos + 1;
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (!field.isEmpty()) {
                segments.add(new PathSeg(field, -1));
            }
        }
        return segments;
    }

    // ── Path Assignment ──

    static void setPath(DynValue output, String path, DynValue value) {
        if (output == null || path == null || path.isEmpty()) return;

        var parts = path.split("\\.");
        if (parts.length == 1) {
            setSingleField(output, parts[0], value);
            return;
        }

        // Navigate/create intermediate objects
        var current = output;
        for (int i = 0; i < parts.length - 1; i++) {
            var next = current.get(parts[i]);
            if (next == null || next.isNull()) {
                next = DynValue.ofObject(new ArrayList<>());
                setSingleField(current, parts[i], next);
            }
            current = next;
        }
        setSingleField(current, parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static void setSingleField(DynValue obj, String field, DynValue value) {
        var entries = obj.asObject();
        if (entries == null) return;

        // Check for array index syntax: field[N]
        int bracketPos = field.indexOf('[');
        if (bracketPos >= 0 && field.endsWith("]")) {
            var cleanField = field.substring(0, bracketPos);
            var suffix = field.substring(bracketPos);

            if (suffix.equals("[]")) {
                // Array push
                var existing = findEntry(entries, cleanField);
                if (existing != null && existing.asArray() != null) {
                    existing.asArray().add(value);
                } else {
                    var arr = new ArrayList<DynValue>();
                    arr.add(value);
                    setOrAppend(entries, cleanField, DynValue.ofArray(arr));
                }
                return;
            }

            try {
                int idx = Integer.parseInt(suffix.substring(1, suffix.length() - 1));
                var existing = findEntry(entries, cleanField);
                if (existing != null && existing.asArray() != null) {
                    var list = existing.asArray();
                    while (list.size() <= idx) list.add(DynValue.ofNull());
                    list.set(idx, value);
                } else {
                    var arr = new ArrayList<DynValue>();
                    while (arr.size() <= idx) arr.add(DynValue.ofNull());
                    arr.set(idx, value);
                    setOrAppend(entries, cleanField, DynValue.ofArray(arr));
                }
                return;
            } catch (NumberFormatException ignored) {}
        }

        setOrAppend(entries, field, value);
    }

    private static DynValue findEntry(List<Map.Entry<String, DynValue>> entries, String key) {
        for (var e : entries) {
            if (e.getKey().equals(key)) return e.getValue();
        }
        return null;
    }

    private static void setOrAppend(List<Map.Entry<String, DynValue>> entries, String key, DynValue value) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(key)) {
                entries.set(i, Map.entry(key, value));
                return;
            }
        }
        entries.add(Map.entry(key, value));
    }

    private static DynValue getOrCreateObject(DynValue parent, String field) {
        var existing = parent.get(field);
        if (existing != null && !existing.isNull()) return existing;
        return DynValue.ofObject(new ArrayList<>());
    }

    // ── Confidential Enforcement ──

    private static void applyConfidentialEnforcement(List<TransformSegment> segments, ConfidentialMode mode, DynValue output) {
        var paths = new ArrayList<String>();
        collectConfidentialPaths(segments, "", paths);
        for (var path : paths) {
            var current = resolveSubPath(output, path);
            if (current != null && !current.isNull()) {
                var masked = applyConfidentialToValue(current, mode);
                setPath(output, path, masked);
            }
        }
    }

    private static void collectConfidentialPaths(List<TransformSegment> segments, String prefix, List<String> paths) {
        for (var seg : segments) {
            String segPrefix = seg.getName().isEmpty() || seg.getName().equals("$") || seg.getName().equals("_root")
                    ? prefix
                    : (prefix.isEmpty() ? seg.getName() : prefix + "." + seg.getName());

            for (var mapping : seg.getMappings()) {
                if (mapping.getModifiers() != null && mapping.getModifiers().isConfidential()) {
                    var fullPath = segPrefix.isEmpty() ? mapping.getTarget() : segPrefix + "." + mapping.getTarget();
                    paths.add(fullPath);
                }
            }
            collectConfidentialPaths(seg.getChildren(), segPrefix, paths);
        }
    }

    private static DynValue applyConfidentialToValue(DynValue val, ConfidentialMode mode) {
        if (mode == ConfidentialMode.REDACT) return DynValue.ofNull();
        // Mask: strings become asterisks, others become null
        var s = val.asString();
        if (s != null) return DynValue.ofString("*".repeat(s.length()));
        return DynValue.ofNull();
    }

    // ── Segment Ordering ──

    private static List<TransformSegment> orderSegmentsByPass(List<TransformSegment> segments) {
        var refs = new ArrayList<>(segments);
        refs.sort((a, b) -> {
            int aKey = (a.getPass() == null || a.getPass() == 0) ? Integer.MAX_VALUE : a.getPass();
            int bKey = (b.getPass() == null || b.getPass() == 0) ? Integer.MAX_VALUE : b.getPass();
            return Integer.compare(aKey, bKey);
        });
        return refs;
    }

    // ── Discriminator Matching ──

    private static boolean matchDiscriminator(DynValue value, String expected) {
        if (value == null || value.isNull()) return expected == null || expected.isEmpty();
        var s = coerceToString(value);
        return s.equals(expected);
    }

    // ── Value Conversion ──

    public static DynValue odinValueToDyn(OdinValue val) {
        return switch (val) {
            case OdinNull ignored -> DynValue.ofNull();
            case OdinBoolean b -> DynValue.ofBool(b.getValue());
            case OdinString s -> DynValue.ofString(s.getValue());
            case OdinInteger i -> DynValue.ofInteger(i.getValue());
            case OdinNumber n -> DynValue.ofFloat(n.getValue());
            case OdinCurrency c -> {
                if (c.getRaw() != null) {
                    // Extract numeric part (raw may include ":USD" suffix)
                    String numPart = c.getRaw();
                    int colonPos = numPart.indexOf(':');
                    if (colonPos >= 0) numPart = numPart.substring(0, colonPos);
                    // Check if double roundtrip loses precision
                    String formatted = String.format("%." + c.getDecimalPlaces() + "f", c.getValue());
                    if (!formatted.equals(numPart) && !String.valueOf(c.getValue()).equals(numPart))
                        yield DynValue.ofCurrencyRaw(numPart, (byte) c.getDecimalPlaces(), c.getCurrencyCode());
                }
                yield DynValue.ofCurrency(c.getValue(), (byte) c.getDecimalPlaces(), c.getCurrencyCode());
            }
            case OdinPercent p -> DynValue.ofPercent(p.getValue());
            case OdinDate d -> DynValue.ofDate(d.getRaw());
            case OdinTimestamp ts -> DynValue.ofTimestamp(ts.getRaw());
            case OdinTime t -> DynValue.ofTime(t.getValue());
            case OdinDuration d -> DynValue.ofDuration(d.getValue());
            case OdinReference r -> DynValue.ofString(r.getPath());
            case OdinBinary bin -> DynValue.ofBinary(Base64.getEncoder().encodeToString(bin.getData()));
            case OdinArray arr -> {
                var items = new ArrayList<DynValue>();
                for (var item : arr.getItems()) {
                    var v = item.asValue();
                    items.add(v != null ? odinValueToDyn(v) : DynValue.ofNull());
                }
                yield DynValue.ofArray(items);
            }
            case OdinObject obj -> {
                var entries = new ArrayList<Map.Entry<String, DynValue>>();
                for (var kvp : obj.getFields()) entries.add(Map.entry(kvp.getKey(), odinValueToDyn(kvp.getValue())));
                yield DynValue.ofObject(entries);
            }
            case OdinVerb ignored -> DynValue.ofNull();
            default -> DynValue.ofNull();
        };
    }

    // ── Helpers ──

    private static final java.util.regex.Pattern CONDITION_PATTERN =
            java.util.regex.Pattern.compile("^(@?[\\w.\\[\\]]+)\\s*(==|!=|<>|<=|>=|=|<|>)\\s*(.+)$");

    // Evaluate a segment condition with boolean operators: not > and > or.
    private static boolean evaluateCondition(String condition, ExecContext ctx) {
        return evaluateOr(condition.trim(), ctx);
    }

    // OR has the lowest precedence.
    private static boolean evaluateOr(String expr, ExecContext ctx) {
        var terms = splitTopLevel(expr, "or");
        if (terms.size() > 1) {
            for (var t : terms) {
                if (evaluateAnd(t, ctx)) return true;
            }
            return false;
        }
        return evaluateAnd(expr, ctx);
    }

    // AND binds tighter than OR.
    private static boolean evaluateAnd(String expr, ExecContext ctx) {
        var factors = splitTopLevel(expr, "and");
        if (factors.size() > 1) {
            for (var f : factors) {
                if (!evaluateNot(f, ctx)) return false;
            }
            return true;
        }
        return evaluateNot(expr, ctx);
    }

    // NOT binds tightest; a leading `not` negates the primary that follows.
    private static boolean evaluateNot(String expr, ExecContext ctx) {
        var trimmed = expr.trim();
        if (startsWithNot(trimmed)) {
            return !evaluateNot(trimmed.substring(3).trim(), ctx);
        }
        return evaluatePrimary(trimmed, ctx);
    }

    // True when the expression begins with a whole-word `not` (case-insensitive).
    private static boolean startsWithNot(String expr) {
        return expr.length() > 3
                && (expr.charAt(0) == 'n' || expr.charAt(0) == 'N')
                && (expr.charAt(1) == 'o' || expr.charAt(1) == 'O')
                && (expr.charAt(2) == 't' || expr.charAt(2) == 'T')
                && Character.isWhitespace(expr.charAt(3));
    }

    // Split on a whole-word boolean operator at top level, ignoring quoted text.
    private static java.util.List<String> splitTopLevel(String expr, String op) {
        var parts = new ArrayList<String>();
        int start = 0;
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (inSingle) {
                if (c == '\'') inSingle = false;
                continue;
            }
            if (inDouble) {
                if (c == '"') inDouble = false;
                continue;
            }
            if (c == '\'') { inSingle = true; continue; }
            if (c == '"') { inDouble = true; continue; }
            if (i == 0 || i + op.length() >= expr.length()) continue;
            char before = expr.charAt(i - 1);
            char after = expr.charAt(i + op.length());
            if (Character.isWhitespace(before) && Character.isWhitespace(after)
                    && expr.regionMatches(true, i, op, 0, op.length())) {
                parts.add(expr.substring(start, i));
                i += op.length() - 1;
                start = i + 1;
            }
        }
        parts.add(expr.substring(start));
        var out = new ArrayList<String>();
        for (var p : parts) {
            var t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // A single comparison expression or a bare truthy path.
    private static boolean evaluatePrimary(String condition, ExecContext ctx) {
        var trimmed = condition.trim();
        var matcher = CONDITION_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            var left = resolvePath(ctx.source, matcher.group(1), ctx.constants, ctx.accumulators);
            return compareCondition(left, matcher.group(2), parseConditionValue(matcher.group(3).trim()));
        }
        return isTruthy(resolvePath(ctx.source, trimmed, ctx.constants, ctx.accumulators));
    }

    private static DynValue parseConditionValue(String raw) {
        if (raw.length() >= 2 &&
                ((raw.charAt(0) == '\'' && raw.charAt(raw.length() - 1) == '\'') ||
                 (raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"'))) {
            return DynValue.ofString(raw.substring(1, raw.length() - 1));
        }
        var lower = raw.toLowerCase();
        if ("true".equals(lower)) return DynValue.ofBool(true);
        if ("false".equals(lower)) return DynValue.ofBool(false);
        if ("null".equals(lower) || "nil".equals(lower)) return DynValue.ofNull();
        try {
            double d = Double.parseDouble(raw);
            return d == Math.floor(d) && !Double.isInfinite(d)
                    ? DynValue.ofInteger((long) d) : DynValue.ofFloat(d);
        } catch (NumberFormatException ignored) {
            return DynValue.ofString(raw);
        }
    }

    private static Double conditionNumber(DynValue val) {
        if (val == null || val.isNull()) return null;
        return switch (val.getType()) {
            case Integer -> val.asInt64() == null ? null : val.asInt64().doubleValue();
            case Float, Currency, Percent, FloatRaw, CurrencyRaw -> val.asDouble();
            case String -> {
                try { yield Double.parseDouble(val.asString()); }
                catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private static String conditionString(DynValue val) {
        if (val == null || val.isNull()) return "";
        return switch (val.getType()) {
            case Bool -> Boolean.TRUE.equals(val.asBool()) ? "true" : "false";
            case Integer -> val.asInt64() == null ? "" : Long.toString(val.asInt64());
            case Float, Currency, Percent, FloatRaw, CurrencyRaw -> {
                var d = val.asDouble();
                if (d == null) yield "";
                yield d == Math.floor(d) && !Double.isInfinite(d) ? Long.toString((long) (double) d) : Double.toString(d);
            }
            default -> {
                var s = val.asString();
                yield s != null ? s : "";
            }
        };
    }

    private static boolean compareCondition(DynValue left, String op, DynValue right) {
        var ls = conditionString(left);
        var rs = conditionString(right);
        var ln = conditionNumber(left);
        var rn = conditionNumber(right);
        boolean numeric = ln != null && rn != null;
        return switch (op) {
            case "=", "==" -> ls.equals(rs);
            case "!=", "<>" -> !ls.equals(rs);
            case "<" -> numeric ? ln < rn : ls.compareTo(rs) < 0;
            case "<=" -> numeric ? ln <= rn : ls.compareTo(rs) <= 0;
            case ">" -> numeric ? ln > rn : ls.compareTo(rs) > 0;
            case ">=" -> numeric ? ln >= rn : ls.compareTo(rs) >= 0;
            default -> false;
        };
    }

    public static boolean isTruthy(DynValue val) {
        if (val == null || val.isNull()) return false;
        return switch (val.getType()) {
            case Bool -> Boolean.TRUE.equals(val.asBool());
            case Integer -> val.asInt64() != null && val.asInt64() != 0;
            case Float, Currency, Percent -> val.asDouble() != null && val.asDouble() != 0.0;
            case String, Date, Timestamp, Time, Duration, Reference, Binary, FloatRaw, CurrencyRaw -> {
                var s = val.asString();
                yield s != null && !s.isEmpty();
            }
            case Array -> val.asArray() != null && !val.asArray().isEmpty();
            case Object -> val.asObject() != null && !val.asObject().isEmpty();
            default -> false;
        };
    }

    // ── Directive Application ──

    private static DynValue applyMappingDirectives(DynValue val, List<OdinDirective> directives,
            String sourceFormat, FieldExpression expr) {
        if (directives == null || directives.isEmpty()) return val;

        // When expression is a verb call, skip pos/len (already applied at ref arg level)
        if (expr instanceof TransformExpression) {
            var filtered = new ArrayList<OdinDirective>();
            for (var d : directives) {
                if (!"pos".equals(d.getName()) && !"len".equals(d.getName()))
                    filtered.add(d);
            }
            return filtered.isEmpty() ? val : applyDirectivesForSource(val, filtered, sourceFormat);
        }

        return applyDirectivesForSource(val, directives, sourceFormat);
    }

    private static DynValue applyDirectivesForSource(DynValue val, List<OdinDirective> directives, String sourceFormat) {
        if (directives.isEmpty()) return val;

        boolean isRawText = "fixed-width".equals(sourceFormat) || "flat".equals(sourceFormat)
                || "flat-kvp".equals(sourceFormat) || "flat-yaml".equals(sourceFormat)
                || "csv".equals(sourceFormat) || "delimited".equals(sourceFormat);

        if (isRawText) return applyTypeDirectives(val, directives);

        // Filter out extraction directives for structured formats
        var filtered = new ArrayList<OdinDirective>();
        for (var d : directives) {
            String n = d.getName();
            if (!"pos".equals(n) && !"len".equals(n) && !"leftPad".equals(n)
                    && !"rightPad".equals(n) && !"truncate".equals(n))
                filtered.add(d);
        }
        return filtered.isEmpty() ? val : applyTypeDirectives(val, filtered);
    }

    static DynValue applyTypeDirectives(DynValue val, List<OdinDirective> directives) {
        if (directives.isEmpty()) return val;

        // Handle :default directive — replace null values with the default
        for (var dir : directives) {
            if ("default".equals(dir.getName()) && (val == null || val.isNull())) {
                if (dir.getValue() != null) {
                    var s = dir.getValue().asString();
                    var n = dir.getValue().asNumber();
                    if (s != null) val = DynValue.ofString(s);
                    else if (n != null) val = DynValue.ofFloat(n);
                    else val = DynValue.ofNull();
                }
                break;
            }
        }

        Integer pos = null, len = null, fieldIndex = null;
        boolean shouldTrim = false;
        Integer decimalPlaces = null;
        String currencyCode = null;
        String typeNameFound = null;

        for (var dir : directives) {
            switch (dir.getName()) {
                case "pos" -> pos = directiveAsInt(dir);
                case "len" -> len = directiveAsInt(dir);
                case "field" -> fieldIndex = directiveAsInt(dir);
                case "trim" -> shouldTrim = true;
                case "type" -> typeNameFound = dir.getValue() != null ? dir.getValue().asString() : null;
                case "decimals" -> {
                    var numVal = dir.getValue() != null ? dir.getValue().asNumber() : null;
                    if (numVal != null) decimalPlaces = numVal.intValue();
                    else {
                        var strVal = dir.getValue() != null ? dir.getValue().asString() : null;
                        if (strVal != null) {
                            try { decimalPlaces = Integer.parseInt(strVal); }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                }
                case "currencyCode" -> currencyCode = dir.getValue() != null ? dir.getValue().asString() : null;
                case "date", "time", "duration", "timestamp",
                     "boolean", "integer", "number",
                     "currency", "reference", "binary", "percent" -> typeNameFound = dir.getName();
            }
        }

        // Phase 1: extraction
        if (pos != null || fieldIndex != null || shouldTrim) {
            String s;
            if (val.getType() == DynValue.Type.String) s = val.asString() != null ? val.asString() : "";
            else if (val.isNull()) return val;
            else s = coerceToString(val);

            if (fieldIndex != null) {
                String[] fields = s.split(",");
                s = fieldIndex < fields.length ? fields[fieldIndex] : "";
            }
            if (pos != null) {
                int start = Math.min(pos, s.length());
                if (len != null) {
                    int end = Math.min(start + len, s.length());
                    s = s.substring(start, end);
                } else {
                    s = s.substring(start);
                }
            }
            if (shouldTrim) s = s.trim();
            val = DynValue.ofString(s);
        }

        // Phase 2: type coercion
        if (typeNameFound != null)
            return coerceToType(val, typeNameFound, decimalPlaces, currencyCode);

        return val;
    }

    private static Integer directiveAsInt(OdinDirective dir) {
        var numVal = dir.getValue() != null ? dir.getValue().asNumber() : null;
        if (numVal != null) return numVal.intValue();
        var strVal = dir.getValue() != null ? dir.getValue().asString() : null;
        if (strVal != null) {
            try { return Integer.parseInt(strVal); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static DynValue coerceToType(DynValue val, String typeName, Integer decimalPlaces, String currencyCode) {
        return switch (typeName) {
            case "integer" -> {
                var d = val.asDouble();
                if (d != null) yield DynValue.ofInteger((long) d.doubleValue());
                Long l = val.asInt64();
                if (l != null) yield DynValue.ofInteger(l);
                var s = val.asString();
                if (s != null) {
                    try { yield DynValue.ofInteger(Long.parseLong(s)); }
                    catch (NumberFormatException ignored) {}
                }
                var b = val.asBool();
                if (b != null) yield DynValue.ofInteger(b ? 1 : 0);
                yield val;
            }
            case "number" -> {
                if (val.getType() == DynValue.Type.Integer) yield DynValue.ofFloat((double) val.asInt64());
                if (val.getType() == DynValue.Type.Currency || val.getType() == DynValue.Type.CurrencyRaw)
                    yield DynValue.ofFloat(val.asDouble());
                var s = val.asString();
                if (s != null) {
                    try {
                        double f = Double.parseDouble(s);
                        // Check if OdinFormatter's integer shortcut would alter the representation
                        // e.g., "0.0" → Float(0.0) → "#0" (loses ".0"), so use FloatRaw
                        if (Double.toString(f).equals(s)) {
                            if (f == Math.floor(f) && !Double.isInfinite(f) && Math.abs(f) < 1e15 && s.contains("."))
                                yield DynValue.ofFloatRaw(s);
                            yield DynValue.ofFloat(f);
                        }
                        yield DynValue.ofFloatRaw(s);
                    } catch (NumberFormatException ignored) {}
                }
                yield val;
            }
            case "currency" -> {
                byte dp = (byte) (decimalPlaces != null ? decimalPlaces : 2);
                if (val.getType() == DynValue.Type.Float || val.getType() == DynValue.Type.FloatRaw) {
                    double d = val.asDouble();
                    // Compare F-format to G-format (like .NET): if they differ, trailing zeros matter
                    String fixedStr = String.format("%." + dp + "f", d);
                    String gStr = String.valueOf(d);
                    if (!fixedStr.equals(gStr))
                        yield DynValue.ofCurrencyRaw(fixedStr, dp, currencyCode);
                    yield DynValue.ofCurrency(d, dp, currencyCode);
                }
                if (val.getType() == DynValue.Type.Integer)
                    yield DynValue.ofCurrency((double) val.asInt64(), dp, currencyCode);
                var s = val.asString();
                if (s != null) {
                    var cleaned = s.replace("$", "").replace(",", "").replace("\u00A3", "").replace("\u20AC", "");
                    byte actualDp = (byte) (decimalPlaces != null ? decimalPlaces : (s.indexOf('.') >= 0 ? cleaned.length() - cleaned.indexOf('.') - 1 : 2));
                    try {
                        double f = Double.parseDouble(cleaned);
                        // Use G-format comparison (like .NET) to detect trailing zeros
                        // String.valueOf(149.5) = "149.5" != "149.50" → CurrencyRaw
                        String rt = String.valueOf(f);
                        if (rt.equals(cleaned))
                            yield DynValue.ofCurrency(f, actualDp, currencyCode);
                        yield DynValue.ofCurrencyRaw(cleaned, actualDp, currencyCode);
                    } catch (NumberFormatException ignored) {}
                }
                yield val;
            }
            case "percent" -> {
                if (val.getType() == DynValue.Type.Float || val.getType() == DynValue.Type.FloatRaw)
                    yield DynValue.ofPercent(val.asDouble());
                if (val.getType() == DynValue.Type.Integer)
                    yield DynValue.ofPercent((double) val.asInt64());
                var s = val.asString();
                if (s != null) {
                    var cleaned = s.replace("%", "");
                    try { yield DynValue.ofPercent(Double.parseDouble(cleaned)); }
                    catch (NumberFormatException ignored) {}
                }
                yield val;
            }
            case "boolean" -> {
                var s = val.asString();
                if (s != null) {
                    yield switch (s.toLowerCase()) {
                        case "true", "yes", "1" -> DynValue.ofBool(true);
                        case "false", "no", "0" -> DynValue.ofBool(false);
                        default -> val;
                    };
                }
                if (val.getType() == DynValue.Type.Integer) yield DynValue.ofBool(val.asInt64() != 0);
                if (val.getType() == DynValue.Type.Float) yield DynValue.ofBool(val.asDouble() != 0.0);
                yield val;
            }
            case "date" -> val.getType() == DynValue.Type.String ? DynValue.ofDate(val.asString()) : val;
            case "time" -> val.getType() == DynValue.Type.String ? DynValue.ofTime(val.asString()) : val;
            case "timestamp" -> {
                if (val.getType() == DynValue.Type.String) {
                    String tsStr = val.asString();
                    // Try to normalize to UTC ISO 8601 with milliseconds
                    try {
                        var parsed = java.time.OffsetDateTime.parse(tsStr);
                        var utc = parsed.toInstant().atOffset(java.time.ZoneOffset.UTC);
                        String normalized = utc.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
                        yield DynValue.ofTimestamp(normalized);
                    } catch (Exception e1) {
                        try {
                            var inst = java.time.Instant.parse(tsStr);
                            var utc = inst.atOffset(java.time.ZoneOffset.UTC);
                            String normalized = utc.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
                            yield DynValue.ofTimestamp(normalized);
                        } catch (Exception e2) {
                            yield DynValue.ofTimestamp(tsStr);
                        }
                    }
                }
                yield val;
            }
            case "duration" -> val.getType() == DynValue.Type.String ? DynValue.ofDuration(val.asString()) : val;
            case "reference" -> val.getType() == DynValue.Type.String ? DynValue.ofReference(val.asString()) : val;
            case "binary" -> val.getType() == DynValue.Type.String ? DynValue.ofBinary(val.asString()) : val;
            default -> val;
        };
    }

    static String coerceToString(DynValue val) {
        if (val == null || val.isNull()) return "";
        var s = val.asString();
        if (s != null) return s;
        if (val.asInt64() != null) return Long.toString(val.asInt64());
        if (val.asDouble() != null) return Double.toString(val.asDouble());
        if (val.asBool() != null) return val.asBool() ? "true" : "false";
        return "";
    }

    // ── Output Formatting ──

    private static String formatOutput(DynValue output, String targetFormat,
            Map<String, String> options, List<TransformSegment> segments,
            Map<String, OdinModifiers> modifiers) {
        if (targetFormat == null || targetFormat.isEmpty()) targetFormat = "json";

        var config = new TargetConfig();
        config.setFormat(targetFormat);
        config.setOptions(new LinkedHashMap<>(options != null ? options : Map.of()));

        return switch (targetFormat.toLowerCase()) {
            case "odin" -> {
                if (!config.getOptions().containsKey("includeHeader"))
                    config.getOptions().put("includeHeader", "false");
                yield OdinFormatter.formatWithModifiers(output, config, modifiers);
            }
            case "json" -> JsonFormatter.format(output, config);
            case "xml" -> XmlFormatter.formatWithModifiers(output, config, modifiers);
            case "csv" -> CsvFormatter.format(output, config);
            case "fixed-width" -> FixedWidthFormatter.formatFromSegments(output, segments, config);
            case "flat", "properties" -> FlatFormatter.format(output, config);
            default -> JsonFormatter.format(output, config);
        };
    }
}
