package foundation.odin.transform;

import foundation.odin.types.*;
import foundation.odin.types.OdinTransformTypes.*;
import foundation.odin.types.OdinTransformTypes.FieldExpression.*;
import foundation.odin.types.OdinTransformTypes.VerbArg.*;
import foundation.odin.types.OdinValue.*;
import foundation.odin.transform.verbs.VerbHelpers;
import foundation.odin.utils.SecurityLimits;

import java.util.*;
import java.util.function.BiFunction;

public final class TransformEngine {

    private TransformEngine() {}

    // Carries a stable transform error code thrown during expression evaluation so
    // the mapping handler can preserve the code instead of collapsing to a generic error.
    private static final class CodedTransformException extends RuntimeException {
        private final TransformError transformError;
        CodedTransformException(TransformError error) {
            super(error.getMessage());
            this.transformError = error;
        }
        TransformError getTransformError() { return transformError; }
    }

    // Execution guard abort (fuel, timeout, or depth). The onError policy never
    // downgrades it; the execute boundary surfaces its error as a failed result.
    private static final class TransformAbortException extends RuntimeException {
        private final TransformError transformError;
        TransformAbortException(TransformError error) {
            super(error.getMessage());
            this.transformError = error;
        }
        TransformError getTransformError() { return transformError; }
    }

    // Wall clock for the timeout guard, overridable so tests inject deterministic time.
    static java.util.function.LongSupplier clock = System::currentTimeMillis;

    // Read the wall clock once per this many charged units.
    private static final int CLOCK_CHECK_INTERVAL = 1024;

    // Verbs whose cost grows with an array argument. Charged proportional to the
    // array width so large-array work cannot escape the budget at a flat unit.
    private static final Set<String> SORT_VERBS = Set.of("sort");
    private static final Set<String> WIDTH_VERBS = Set.of(
            "distinct", "groupBy", "keyBy", "countBy", "reduce",
            "sum", "avg", "min", "max", "count", "sumIf", "avgIf", "countIf",
            "union", "intersection", "difference", "symmetricDifference",
            "map", "filter", "window", "explode", "flatten", "reverse");

    // Width of the first array-typed argument, or 0 if none.
    private static int firstArrayWidth(DynValue[] args) {
        for (var arg : args) {
            if (arg != null && arg.getType() == DynValue.Type.Array) {
                var items = arg.asArray();
                return items != null ? items.size() : 0;
            }
        }
        return 0;
    }

    // ── Per-Mapping Precompute ──

    // Precompiled validation data for a :validate / :enum / :range modifier on a mapping.
    private static final class CompiledValidation {
        java.util.regex.Pattern regex; // null when no :validate or pattern invalid
        boolean regexInvalid;          // :validate present but pattern failed to compile
        String pattern;
        Set<String> enumSet;
        List<String> enumLabel;
        String rangeStr;
        Double rangeMin;
        Double rangeMax;
        boolean active;                // any of :validate / :enum / :range present
    }

    // Directive references and boolean flags derived once from a mapping's directives
    // and modifiers, which are data-independent and shared across executions.
    private static final class MappingMods {
        OdinDirective ifDir;
        OdinDirective unlessDir;
        OdinDirective objectDir;
        OdinDirective validateDir;
        OdinDirective enumDir;
        OdinDirective rangeDir;
        boolean hasDefault;
        boolean hasRaw;
        boolean hasArray;
        boolean required;
        boolean confidential;
        CompiledValidation validation;
    }

    // Keyed by mapping identity; entries are reused across executions of the same transform.
    private static final Map<FieldMapping, MappingMods> MAPPING_MODS =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static MappingMods getMappingMods(FieldMapping mapping) {
        var cached = MAPPING_MODS.get(mapping);
        if (cached != null) return cached;

        var m = new MappingMods();
        var directives = mapping.getDirectives();
        if (directives != null) {
            for (var d : directives) {
                switch (d.getName()) {
                    case "if" -> { if (m.ifDir == null) m.ifDir = d; }
                    case "unless" -> { if (m.unlessDir == null) m.unlessDir = d; }
                    case "object" -> { if (m.objectDir == null) m.objectDir = d; }
                    case "validate" -> { if (m.validateDir == null) m.validateDir = d; }
                    case "enum" -> { if (m.enumDir == null) m.enumDir = d; }
                    case "range" -> { if (m.rangeDir == null) m.rangeDir = d; }
                    case "default" -> m.hasDefault = true;
                    case "raw" -> m.hasRaw = true;
                    case "array" -> m.hasArray = true;
                    default -> {}
                }
            }
        }
        var mods = mapping.getModifiers();
        m.required = mods != null && mods.isRequired();
        m.confidential = mods != null && mods.isConfidential();
        m.validation = compileValidation(m.validateDir, m.enumDir, m.rangeDir);

        MAPPING_MODS.put(mapping, m);
        return m;
    }

    private static CompiledValidation compileValidation(OdinDirective validateDir,
            OdinDirective enumDir, OdinDirective rangeDir) {
        var v = new CompiledValidation();
        v.active = validateDir != null || enumDir != null || rangeDir != null;

        if (validateDir != null && validateDir.getValue() != null) {
            v.pattern = validateDir.getValue().asString();
            try {
                v.regex = java.util.regex.Pattern.compile(v.pattern);
            } catch (Exception e) {
                v.regexInvalid = true;
            }
        }
        if (enumDir != null && enumDir.getValue() != null) {
            var allowed = new ArrayList<String>();
            for (var part : enumDir.getValue().asString().split(",")) {
                var s = part.trim();
                if (s.length() >= 2 && ((s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
                        || (s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'')))
                    s = s.substring(1, s.length() - 1);
                allowed.add(s);
            }
            v.enumSet = new LinkedHashSet<>(allowed);
            v.enumLabel = allowed;
        }
        if (rangeDir != null && rangeDir.getValue() != null) {
            v.rangeStr = rangeDir.getValue().asString();
            var parts = v.rangeStr.split("\\.\\.");
            v.rangeMin = parseDoubleOrNull(parts.length > 0 ? parts[0] : "");
            v.rangeMax = parseDoubleOrNull(parts.length > 1 ? parts[1] : "");
        }
        return v;
    }

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
        private String onMissing;
        private List<TransformError> errors;
        private List<TransformWarning> warnings;

        public DynValue getSource() { return source; }
        public Map<String, DynValue> getLoopVars() { return loopVars; }
        public Map<String, DynValue> getAccumulators() { return accumulators; }
        public Map<String, LookupTable> getTables() { return tables; }
        public DynValue getGlobalOutput() { return globalOutput; }
        public String getOnMissing() { return onMissing; }

        // Report a lookup key miss honoring the onMissing policy (default silent null).
        public void reportMissing(String tableName, String key) {
            if ("fail".equals(onMissing)) {
                if (errors != null) errors.add(OdinErrors.lookupKeyNotFoundError(tableName, key));
            } else if ("warn".equals(onMissing)) {
                if (warnings != null) warnings.add(OdinErrors.lookupKeyNotFoundWarning(tableName, key));
            }
        }

        // Record an accumulator overflow (T008); always an error regardless of onMissing.
        public void reportAccumulatorOverflow(String accumulator, double value) {
            if (errors != null) errors.add(OdinErrors.accumulatorOverflowError(accumulator, value));
        }

        // Report an undeclared lookup table (T003) honoring the onMissing policy.
        public void reportTableMissing(String tableName) {
            if ("fail".equals(onMissing)) {
                if (errors != null) errors.add(OdinErrors.lookupTableNotFoundError(tableName));
            } else if ("warn".equals(onMissing)) {
                if (warnings != null) warnings.add(OdinErrors.lookupTableNotFoundWarning(tableName));
            }
        }

        // Record a verb-level error (T002/T011 and similar).
        public void addError(TransformError error) {
            if (errors != null) errors.add(error);
        }
    }

    // ── ExecContext ──

    private static final class ExecContext {
        DynValue source;
        Map<String, DynValue> constants;
        Map<String, DynValue> accumulators;
        Map<String, LookupTable> tables;
        Map<String, DynValue> loopVars = new LinkedHashMap<>();
        Set<String> loopAliases = new HashSet<>();
        Map<String, BiFunction<DynValue[], VerbContext, DynValue>> verbs;
        List<TransformWarning> warnings = new ArrayList<>();
        List<TransformError> errors = new ArrayList<>();
        ConfidentialMode enforceConfidential;
        DynValue globalOutput;
        Map<String, OdinModifiers> fieldModifiers = new LinkedHashMap<>();
        String sourceFormat;
        String targetFormat;
        Map<String, String> targetOptions;
        String onValidation;
        String onError;
        String onMissing;
        boolean strictTypes;

        // Execution guard state. Fuel and timeout charge only when their cap is > 0.
        int fuelCap;
        int timeoutMs;
        int maxExprDepth;
        long fuelUsed;
        int exprDepth;
        int opsSinceClock;
        long startTime;
    }

    // ── Transform Options ──

    @FunctionalInterface
    public interface ImportResolver {
        // Resolve an @import path to ODIN transform text, or null to leave it unresolved.
        String resolve(String path);
    }

    public static final class TransformOptions {
        private ImportResolver importResolver;
        private Integer maxTransformFuel;   // per-call fuel budget; null falls back to the global limit
        private Integer transformTimeoutMs; // per-call wall-clock timeout in ms; null falls back to the global limit
        private Integer maxExpressionDepth; // per-call expression-depth cap; null falls back to the global limit

        public ImportResolver getImportResolver() { return importResolver; }
        public TransformOptions setImportResolver(ImportResolver r) { this.importResolver = r; return this; }

        public Integer getMaxTransformFuel() { return maxTransformFuel; }
        public TransformOptions setMaxTransformFuel(Integer v) { this.maxTransformFuel = v; return this; }

        public Integer getTransformTimeoutMs() { return transformTimeoutMs; }
        public TransformOptions setTransformTimeoutMs(Integer v) { this.transformTimeoutMs = v; return this; }

        public Integer getMaxExpressionDepth() { return maxExpressionDepth; }
        public TransformOptions setMaxExpressionDepth(Integer v) { this.maxExpressionDepth = v; return this; }
    }

    // Merge imported lookup tables, constants, accumulators, and named segments into
    // the transform. Local declarations win over imported ones; imported segments are
    // appended so their mappings remain emittable.
    private static void resolveImports(OdinTransform transform, ImportResolver resolver) {
        var seen = new HashSet<String>();
        for (var imp : transform.getImports()) {
            if (!seen.add(imp.getPath())) continue;
            var text = resolver.resolve(imp.getPath());
            if (text == null) continue;

            OdinTransform imported;
            try {
                imported = TransformParser.parse(text);
            } catch (Exception e) {
                continue;
            }

            for (var e : imported.getTables().entrySet())
                transform.getTables().putIfAbsent(e.getKey(), e.getValue());
            for (var e : imported.getConstants().entrySet())
                transform.getConstants().putIfAbsent(e.getKey(), e.getValue());
            for (var e : imported.getAccumulators().entrySet())
                transform.getAccumulators().putIfAbsent(e.getKey(), e.getValue());

            var existingPaths = new HashSet<String>();
            for (var s : transform.getSegments()) existingPaths.add(s.getPath());
            for (var segment : imported.getSegments()) {
                if (segment.getPath().isEmpty() || existingPaths.contains(segment.getPath())) continue;
                transform.getSegments().add(segment);
            }
        }
    }

    // ── Public Entry Point ──

    public static TransformResult execute(OdinTransform transform, DynValue source, TransformOptions options) {
        if (options != null && options.getImportResolver() != null && !transform.getImports().isEmpty()) {
            resolveImports(transform, options.getImportResolver());
        }
        return executeInternal(transform, source, options);
    }

    public static TransformResult execute(OdinTransform transform, DynValue source) {
        return executeInternal(transform, source, null);
    }

    private static TransformResult executeInternal(OdinTransform transform, DynValue source,
            TransformOptions options) {
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
                return executeMultiRecord(transform, source.asString(), discConfig, transform.getSource().getFormat(), options);
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
                if (parsed != null) return executeInternal(transform, parsed, options);
            }
        }

        var ctx = buildContext(transform, source, options);
        var output = DynValue.ofObject(new ArrayList<>());

        var segments = orderSegmentsByPass(transform.getSegments());

        // Group segments by pass (preserving order) so conditional chains reset at pass boundaries.
        var passOrder = new ArrayList<Integer>();
        var byPass = new LinkedHashMap<Integer, List<TransformSegment>>();
        for (var segment : segments) {
            Integer segPass = segment.getPass();
            if (!byPass.containsKey(segPass)) passOrder.add(segPass);
            byPass.computeIfAbsent(segPass, k -> new ArrayList<>()).add(segment);
        }

        boolean isFirstPass = true;
        try {
            for (var passNum : passOrder) {
                if (!isFirstPass) {
                    // Reset non-persist accumulators on pass change
                    for (var kvp : transform.getAccumulators().entrySet()) {
                        if (!kvp.getValue().isPersist()) {
                            ctx.accumulators.put(kvp.getKey(), odinValueToDyn(kvp.getValue().getInitial()));
                        }
                    }
                }
                isFirstPass = false;
                output = processSegmentList(byPass.get(passNum), ctx, output);
            }
        } catch (TransformAbortException e) {
            return abortResult(ctx, e, output);
        }

        // Confidential enforcement
        if (ctx.enforceConfidential != null) {
            applyConfidentialEnforcement(transform.getSegments(), ctx.enforceConfidential, output);
        }

        // Format output
        String targetFormat = resolveTargetFormat(transform);
        if (targetFormat == null || targetFormat.isEmpty()) targetFormat = "json";
        String formatted;
        if (!isKnownFormat(targetFormat)) {
            // T006: unregistered target format.
            ctx.errors.add(OdinErrors.invalidOutputFormatError(targetFormat));
            formatted = "";
        } else {
            formatted = formatOutput(output, targetFormat,
                    transform.getTarget().getOptions(), transform.getSegments(), ctx.fieldModifiers);
        }

        var result = new TransformResult();
        result.setSuccess(ctx.errors.isEmpty());
        result.setOutput(output);
        result.setFormatted(formatted);
        result.setErrors(ctx.errors);
        result.setWarnings(ctx.warnings);
        result.setOutputModifiers(ctx.fieldModifiers);
        return result;
    }

    // Surface a guard abort as a failed result; the abort is never thrown past the
    // execute boundary.
    private static TransformResult abortResult(ExecContext ctx, TransformAbortException e, DynValue output) {
        ctx.errors.add(e.getTransformError());
        var result = new TransformResult();
        result.setSuccess(false);
        result.setOutput(output);
        result.setFormatted("");
        result.setErrors(ctx.errors);
        result.setWarnings(ctx.warnings);
        result.setOutputModifiers(ctx.fieldModifiers);
        return result;
    }

    // Resolve the effective target format: the explicit {$target} format, else the
    // emit side of the direction header.
    private static String resolveTargetFormat(OdinTransform transform) {
        var fmt = transform.getTarget() != null ? transform.getTarget().getFormat() : null;
        if (fmt != null && !fmt.isEmpty()) return fmt;
        var direction = transform.getMetadata() != null ? transform.getMetadata().getDirection() : null;
        if (direction != null && direction.contains("->")) {
            var parts = direction.split("->");
            if (parts.length > 1) return parts[parts.length - 1].trim();
        }
        return fmt;
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

    private static TransformResult executeMultiRecord(OdinTransform transform, String rawInput, String discConfig, String sourceFormat, TransformOptions options) {
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

        var ctx = buildContext(transform, DynValue.ofNull(), options);
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
        try {
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
        } catch (TransformAbortException e) {
            return abortResult(ctx, e, output);
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

    private static ExecContext buildContext(OdinTransform transform, DynValue source, TransformOptions options) {
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
        var targetOpts = transform.getTarget() != null ? transform.getTarget().getOptions() : null;
        ctx.onValidation = targetOpts != null ? targetOpts.get("onValidation") : null;
        ctx.onError = targetOpts != null ? targetOpts.get("onError") : null;
        ctx.onMissing = targetOpts != null ? targetOpts.get("onMissing") : null;
        ctx.strictTypes = transform.isStrictTypes();
        ctx.targetFormat = transform.getTarget() != null ? transform.getTarget().getFormat() : null;
        ctx.targetOptions = targetOpts;
        // A per-call option overrides the global limit; unset falls back to it.
        ctx.fuelCap = options != null && options.getMaxTransformFuel() != null
                ? options.getMaxTransformFuel() : SecurityLimits.MAX_TRANSFORM_FUEL;
        ctx.timeoutMs = options != null && options.getTransformTimeoutMs() != null
                ? options.getTransformTimeoutMs() : SecurityLimits.TRANSFORM_TIMEOUT_MS;
        ctx.maxExprDepth = options != null && options.getMaxExpressionDepth() != null
                ? options.getMaxExpressionDepth() : SecurityLimits.MAX_EXPRESSION_DEPTH;
        if (ctx.timeoutMs > 0) ctx.startTime = clock.getAsLong();
        return ctx;
    }

    // ── Segment Processing ──

    // Branch state for an if/elif/else chain.
    private enum BranchState { NONE, PENDING, TAKEN }

    // Process a list of segments, honoring if/elif/else conditional chains.
    // A chain is a run of consecutive segments: one `if`, then any `elif`, then an
    // optional `else`. Only the first branch whose condition holds is emitted; the
    // chain breaks on any non-chain segment.
    private static DynValue processSegmentList(List<TransformSegment> segments, ExecContext ctx, DynValue output) {
        var branch = BranchState.NONE;

        for (var segment : segments) {
            var ifDir = findDirective(segment, "if");
            var elifDir = findDirective(segment, "elif");
            var elseDir = findDirective(segment, "else");

            if (ifDir != null) {
                boolean taken = evaluateSegmentCondition(ifDir, ctx);
                branch = taken ? BranchState.TAKEN : BranchState.PENDING;
                if (taken) output = processSegment(segment, ctx, output, "");
            } else if (elifDir != null) {
                if (branch == BranchState.NONE) {
                    ctx.errors.add(OdinErrors.danglingBranchError("elif", segment.getPath()));
                    continue;
                }
                if (branch == BranchState.TAKEN) continue;
                boolean taken = evaluateSegmentCondition(elifDir, ctx);
                branch = taken ? BranchState.TAKEN : BranchState.PENDING;
                if (taken) output = processSegment(segment, ctx, output, "");
            } else if (elseDir != null) {
                if (branch == BranchState.NONE) {
                    ctx.errors.add(OdinErrors.danglingBranchError("else", segment.getPath()));
                    continue;
                }
                if (branch == BranchState.PENDING) output = processSegment(segment, ctx, output, "");
                branch = BranchState.NONE;
            } else {
                branch = BranchState.NONE;
                output = processSegment(segment, ctx, output, "");
            }
            ctx.globalOutput = output;
        }
        return output;
    }

    private static SegmentDirective findDirective(TransformSegment segment, String type) {
        if (segment.getDirectives() == null) return null;
        for (var d : segment.getDirectives()) {
            if (type.equals(d.getDirectiveType())) return d;
        }
        return null;
    }

    // Evaluate a segment condition: a verb/reference expression coerced to truthy,
    // or a legacy infix string via the comparison evaluator.
    private static boolean evaluateSegmentCondition(SegmentDirective directive, ExecContext ctx) {
        if (directive.getExpr() != null) {
            var result = evaluateExpression(directive.getExpr(), ctx, ctx.source, ctx.globalOutput);
            return isTruthy(result);
        }
        var value = directive.getValue();
        if (value == null || value.isEmpty()) return false;
        return evaluateCondition(value, ctx);
    }

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

        // Literal block: emit interpolated text lines instead of field mappings.
        if (hasDirective(segment, "literal")) {
            return processLiteralSegment(segment, ctx, output, cleanName, isRoot);
        }

        // Computation-only sink: a `_`-prefixed section runs for side effects only
        // (accumulators, verbs) and never appears in the output.
        boolean isSink = name.startsWith("_") && !isRoot && arrayIndex == null;

        // Side-effect-only non-looping sink
        if (isSink && segment.getSourcePath() == null) {
            for (var mapping : segment.getMappings()) {
                processMapping(mapping, ctx, ctx.source, output, currentPrefix);
            }
            return output;
        }

        // Array loop (one or more :loop directives drive a nested cross-product).
        var loopDirectives = collectLoopDirectives(segment);
        if (!loopDirectives.isEmpty() && segment.isArray()) {
            var resultItems = new ArrayList<DynValue>();
            String fromPath = directiveValue(segment, "from");
            // A non-array loop source raises a coded error honoring onError.
            try {
                iterateLoops(loopDirectives, 0, ctx, segment, fromPath, segment.getCounterName(),
                        null, currentPrefix, resultItems, null);
            } catch (CodedTransformException e) {
                var onError = ctx.onError != null ? ctx.onError : "fail";
                if ("fail".equals(onError)) {
                    ctx.errors.add(e.getTransformError());
                } else if ("warn".equals(onError)) {
                    var te = e.getTransformError();
                    var w = new TransformWarning(te.getMessage());
                    w.setPath(te.getPath());
                    w.setCode(te.getCode());
                    ctx.warnings.add(w);
                }
                return output;
            }

            if (isSink) return output;

            var arrayResult = DynValue.ofArray(resultItems);
            if (!isRoot) {
                setPath(output, cleanName, arrayResult);
            } else {
                output = arrayResult;
            }
            return output;
        }
        // Legacy single-source loop with no loop directive.
        if (segment.getSourcePath() != null) {
            var arrayVal = resolvePath(ctx.source, segment.getSourcePath(), ctx.constants, ctx.accumulators);
            var items = arrayVal != null && arrayVal.asArray() != null ? arrayVal.asArray() : new ArrayList<DynValue>();
            if (arrayVal != null && arrayVal.asArray() == null && !arrayVal.isNull()) {
                items = List.of(arrayVal);
            }

            var resultItems = new ArrayList<DynValue>();
            boolean isValueOnly = segment.getMappings().stream().allMatch(m -> m.getTarget().equals("_"));
            String counterName = segment.getCounterName();
            for (int idx = 0; idx < items.size(); idx++) {
                var item = items.get(idx);
                ctx.loopVars.put("_item", item);
                ctx.loopVars.put("_index", DynValue.ofInteger(idx));
                ctx.loopVars.put("_length", DynValue.ofInteger(items.size()));
                if (counterName != null && !counterName.isEmpty()) {
                    ctx.loopVars.put(counterName, DynValue.ofInteger(idx));
                }

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
                        } catch (TransformAbortException e) {
                            throw e;
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
            if (counterName != null && !counterName.isEmpty()) ctx.loopVars.remove(counterName);

            if (isSink) return output;

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

    // ── Loop / Literal Directive Helpers ──

    private static boolean hasDirective(TransformSegment segment, String type) {
        for (var d : segment.getDirectives()) {
            if (type.equals(d.getDirectiveType())) return true;
        }
        return false;
    }

    private static String directiveValue(TransformSegment segment, String type) {
        for (var d : segment.getDirectives()) {
            if (type.equals(d.getDirectiveType())) return d.getValue();
        }
        return null;
    }

    private static List<SegmentDirective> collectLoopDirectives(TransformSegment segment) {
        var loops = new ArrayList<SegmentDirective>();
        for (var d : segment.getDirectives()) {
            if ("loop".equals(d.getDirectiveType())) loops.add(d);
        }
        return loops;
    }

    // Drive one or more :loop directives as a nested cross-product. Each level binds
    // its alias and current item, then recurses into the next loop; the innermost
    // level emits one result element per item. Relative inner paths (`.field`) resolve
    // against the enclosing item. A non-array source at any level yields no rows.
    private static void iterateLoops(List<SegmentDirective> loops, int depth, ExecContext ctx,
            TransformSegment segment, String firstFrom, String counterName,
            DynValue currentItem, String currentPrefix, List<DynValue> results,
            java.util.function.Consumer<DynValue> onItem) {
        var loop = loops.get(depth);
        boolean isOutermost = depth == 0;
        boolean isInnermost = depth == loops.size() - 1;

        // Outermost: _from > loop value > segment path; inner loops resolve relative to the outer item.
        String loopPath = isOutermost
                ? firstNonEmpty(firstFrom, loop.getValue(), segment.getName())
                : (loop.getValue() != null ? loop.getValue() : "");
        if (loopPath.startsWith("@")) loopPath = loopPath.substring(1);
        if (loopPath.endsWith("[]")) loopPath = loopPath.substring(0, loopPath.length() - 2);

        DynValue itemsVal;
        if (loopPath.startsWith(".")) {
            itemsVal = currentItem != null ? resolveSubPath(currentItem, loopPath.substring(1)) : DynValue.ofNull();
        } else if (isOutermost) {
            itemsVal = resolvePath(ctx.source, loopPath, ctx.constants, ctx.accumulators);
        } else {
            String first = loopPath.contains(".") ? loopPath.substring(0, loopPath.indexOf('.')) : loopPath;
            if (ctx.loopAliases.contains(first)) {
                itemsVal = resolveLoopAlias(loopPath, ctx);
            } else {
                itemsVal = currentItem != null ? resolveSubPath(currentItem, loopPath) : DynValue.ofNull();
            }
        }

        var items = itemsVal != null ? itemsVal.asArray() : null;
        if (items == null) {
            // A present non-array scalar is a T009 error; an absent/null source
            // yields zero rows silently.
            if (itemsVal != null && !itemsVal.isNull()) {
                throw new CodedTransformException(
                        OdinErrors.loopSourceNotArrayError(loopPath, segment.getPath()));
            }
            return;
        }

        boolean isValueOnly = segment.getMappings().stream().allMatch(m -> m.getTarget().equals("_"));

        for (int idx = 0; idx < items.size(); idx++) {
            var item = items.get(idx);

            String alias = loop.getAlias();
            DynValue prevAliasVal = null;
            boolean hadAlias = false;
            if (alias != null && !alias.isEmpty()) {
                hadAlias = ctx.loopVars.containsKey(alias);
                prevAliasVal = ctx.loopVars.get(alias);
                ctx.loopVars.put(alias, item);
                ctx.loopAliases.add(alias);
            }
            ctx.loopVars.put("_item", item);
            ctx.loopVars.put("_index", DynValue.ofInteger(idx));
            ctx.loopVars.put("_length", DynValue.ofInteger(items.size()));
            // Innermost loop owns the counter; it resets per enclosing item.
            if (isInnermost && counterName != null && !counterName.isEmpty()) {
                ctx.loopVars.put(counterName, DynValue.ofInteger(idx));
            }

            if (!isInnermost) {
                iterateLoops(loops, depth + 1, ctx, segment, null, counterName,
                        item, currentPrefix, results, onItem);
            } else if (onItem != null) {
                onItem.accept(item);
            } else {
                var rowOutput = DynValue.ofObject(new ArrayList<>());
                for (var mapping : segment.getMappings()) {
                    if (mapping.getTarget().equals("_")) {
                        try {
                            var val = evaluateExpression(mapping.getExpression(), ctx, item, rowOutput);
                            val = applyMappingDirectives(val, mapping.getDirectives(), ctx.sourceFormat, mapping.getExpression());
                            if (isValueOnly) rowOutput = val;
                        } catch (TransformAbortException e) {
                            throw e;
                        } catch (Exception e) {
                            ctx.errors.add(new TransformError("mapping '_': " + e.getMessage(), "_"));
                        }
                    } else {
                        rowOutput = processMapping(mapping, ctx, item, rowOutput, currentPrefix);
                    }
                }
                results.add(rowOutput);
            }

            if (alias != null && !alias.isEmpty()) {
                if (hadAlias) ctx.loopVars.put(alias, prevAliasVal);
                else { ctx.loopVars.remove(alias); ctx.loopAliases.remove(alias); }
            }
        }
        if (isOutermost) {
            ctx.loopVars.remove("_item");
            ctx.loopVars.remove("_index");
            ctx.loopVars.remove("_length");
            if (counterName != null && !counterName.isEmpty()) ctx.loopVars.remove(counterName);
        }
    }

    private static String firstNonEmpty(String... vals) {
        for (var v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    // Render a :literal segment to interpolated text lines, once per loop item (or once
    // when not looping). Lines are stored under a `__literalLines` marker for the formatter.
    private static DynValue processLiteralSegment(TransformSegment segment, ExecContext ctx,
            DynValue output, String cleanName, boolean isRoot) {
        String body = directiveValue(segment, "literalBody");
        String template = normalizeLiteralBody(body != null ? body : "");
        var lines = new ArrayList<DynValue>();

        java.util.function.Consumer<DynValue> render = item -> {
            String rendered = renderLiteral(template, ctx, item);
            for (var line : rendered.split("\n", -1)) lines.add(DynValue.ofString(line));
        };

        var loopDirectives = collectLoopDirectives(segment);
        if (!loopDirectives.isEmpty() && segment.isArray()) {
            String fromPath = directiveValue(segment, "from");
            try {
                iterateLoops(loopDirectives, 0, ctx, segment, fromPath, segment.getCounterName(),
                        null, cleanName, new ArrayList<>(), render);
            } catch (CodedTransformException e) {
                var onError = ctx.onError != null ? ctx.onError : "fail";
                if ("fail".equals(onError)) {
                    ctx.errors.add(e.getTransformError());
                } else if ("warn".equals(onError)) {
                    var te = e.getTransformError();
                    var w = new TransformWarning(te.getMessage());
                    w.setPath(te.getPath());
                    w.setCode(te.getCode());
                    ctx.warnings.add(w);
                }
                return output;
            }
        } else {
            render.accept(ctx.loopVars.get("_item"));
        }

        var marker = DynValue.ofObject(List.of(Map.entry("__literalLines", DynValue.ofArray(lines))));
        if (!isRoot) {
            setPath(output, cleanName, marker);
        }
        return output;
    }

    // Strip one leading and one trailing newline so the `"""` delimiters, written on
    // their own lines, do not contribute blank output lines. Interior blanks are kept.
    private static String normalizeLiteralBody(String body) {
        String s = body;
        if (s.startsWith("\r\n")) s = s.substring(2);
        else if (s.startsWith("\n")) s = s.substring(1);
        if (s.endsWith("\r\n")) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("\n")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String renderLiteral(String template, ExecContext ctx, DynValue item) {
        DynValue src = item != null ? item : ctx.source;
        try {
            return interpolateLiteralBlock(template, ctx, src);
        } catch (NestedInterpolationException e) {
            var err = new TransformError("Nested interpolation is not allowed: ${" + e.getExpr() + "}", "");
            err.setCode(OdinErrors.TransformErrorCodes.T014_NESTED_INTERPOLATION);
            ctx.errors.add(err);
            return "";
        } catch (TransformAbortException e) {
            throw e;
        } catch (RuntimeException e) {
            // Verb and resolution failures honor the target onError policy.
            String onError = ctx.onError != null ? ctx.onError : "fail";
            if ("warn".equals(onError)) {
                ctx.warnings.add(new TransformWarning(e.getMessage()));
            } else if (!"ignore".equals(onError) && !"skip".equals(onError)) {
                ctx.errors.add(new TransformError(e.getMessage(), ""));
            }
            return "";
        }
    }

    // Render a literal block body, resolving ${@path}, ${@.field}, ${%verb …} per item.
    // Escapes: \${ → ${, \$ → $, \\ → \. A nested ${ inside an expression is rejected.
    private static String interpolateLiteralBlock(String template, ExecContext ctx, DynValue src) {
        var out = new StringBuilder();
        int i = 0;
        int len = template.length();
        while (i < len) {
            char ch = template.charAt(i);
            if (ch == '\\') {
                char next = i + 1 < len ? template.charAt(i + 1) : '\0';
                if (next == '$' && i + 2 < len && template.charAt(i + 2) == '{') {
                    out.append("${");
                    i += 3;
                    continue;
                }
                if (next == '\\') { out.append('\\'); i += 2; continue; }
                if (next == '$') { out.append('$'); i += 2; continue; }
                out.append('\\');
                i += 1;
                continue;
            }
            if (ch == '$' && i + 1 < len && template.charAt(i + 1) == '{') {
                int close = template.indexOf('}', i + 2);
                if (close == -1) { out.append(template.substring(i)); break; }
                String expr = template.substring(i + 2, close);
                if (expr.contains("${")) throw new NestedInterpolationException(expr);
                out.append(evaluateInterpolationExpr(expr.trim(), ctx, src));
                i = close + 1;
                continue;
            }
            out.append(ch);
            i += 1;
        }
        return out.toString();
    }

    private static String evaluateInterpolationExpr(String expr, ExecContext ctx, DynValue src) {
        if (expr.startsWith("@") || expr.startsWith("%")) {
            var fieldExpr = TransformParser.parseInlineExpression(expr);
            var value = evaluateExpression(fieldExpr, ctx, src, DynValue.ofObject(new ArrayList<>()));
            return coerceToString(value);
        }
        return "${" + expr + "}";
    }

    private static final class NestedInterpolationException extends RuntimeException {
        private final String expr;
        NestedInterpolationException(String expr) { this.expr = expr; }
        String getExpr() { return expr; }
    }

    // ── Mapping Processing ──

    private static DynValue processMapping(FieldMapping mapping, ExecContext ctx, DynValue currentSource, DynValue output, String pathPrefix) {
        try {
            var mods = getMappingMods(mapping);
            // Field :if / :unless conditions evaluate `path` truthiness or `path op value`.
            var ifDir = mods.ifDir;
            if (ifDir != null && ifDir.getValue() != null) {
                if (!evaluateFieldCondition(ifDir.getValue().asString(), ctx, currentSource)) return output;
            }
            var unlessDir = mods.unlessDir;
            if (unlessDir != null && unlessDir.getValue() != null) {
                if (evaluateFieldCondition(unlessDir.getValue().asString(), ctx, currentSource)) return output;
            }

            // A :default modifier handles a missing lookup; suppress errors raised during evaluation.
            boolean hasDefault = mods.hasDefault;
            int errorsBefore = hasDefault ? ctx.errors.size() : 0;

            DynValue value;
            // :object builds a nested object from an inline {key = @path, ...} spec.
            var objectDir = mods.objectDir;
            if (objectDir != null && objectDir.getValue() != null) {
                value = buildInlineObject(objectDir.getValue().asString(), ctx, currentSource, output);
            } else {
                value = evaluateExpression(mapping.getExpression(), ctx, currentSource, output);
            }

            // If a :default rescued a null result, drop errors raised during evaluation.
            if (hasDefault && ctx.errors.size() > errorsBefore) {
                while (ctx.errors.size() > errorsBefore) ctx.errors.remove(ctx.errors.size() - 1);
            }

            // Apply mapping directives (type coercion, extraction)
            value = applyMappingDirectives(value, mapping.getDirectives(), ctx.sourceFormat, mapping.getExpression());

            // T007: warn on a field modifier that the target format does not support.
            // T010: a fixed-width field whose pos+len exceeds the line width.
            checkModifierFormat(mapping, ctx);

            // Validation modifiers: :validate, :enum, :range (honors onValidation policy).
            if (!validateFieldValue(value, mapping, mods, ctx)) return output;

            // Missing source path: a :required field always fails (T005); an ordinary
            // field honors the onMissing policy (fail -> T005, warn -> warning,
            // skip/default -> keep null). A path present-but-null is not "missing".
            boolean required = mods.required;
            if (value.isNull() && isCopySourceAbsent(mapping, mods, ctx, currentSource)) {
                var rawPath = mapping.getExpression() instanceof CopyExpression cp
                        ? cp.getPath() : mapping.getTarget();
                var path = rawPath.startsWith(".") ? rawPath.substring(1) : rawPath;
                if (path.startsWith("@")) path = path.substring(1);
                if (required) {
                    ctx.errors.add(OdinErrors.sourcePathNotFoundError(path, mapping.getTarget()));
                    return output;
                }
                if ("fail".equals(ctx.onMissing)) {
                    ctx.errors.add(OdinErrors.sourcePathNotFoundError(path, mapping.getTarget()));
                    return output;
                }
                if ("warn".equals(ctx.onMissing)) {
                    ctx.warnings.add(OdinErrors.sourcePathNotFoundWarning(path, mapping.getTarget()));
                }
            } else if (required && value.isNull()) {
                // Required field present but explicitly null.
                ctx.errors.add(OdinErrors.sourceMissingError(mapping.getTarget()));
                return output;
            }

            // Confidential during processing
            if (mods.confidential && ctx.enforceConfidential != null) {
                value = applyConfidentialToValue(value, ctx.enforceConfidential);
            }

            // :raw emits inline JSON structurally instead of an escaped string.
            if (mods.hasRaw) {
                value = parseRawJsonValue(value);
            }

            // :array wraps the value in a single-element array.
            if (mods.hasArray) {
                value = DynValue.ofArray(new ArrayList<>(List.of(value)));
            }

            // `_`-prefixed targets are computation-only sinks: evaluated for side
            // effects (accumulators, counters) but never emitted to the output.
            if (!mapping.getTarget().startsWith("_")) {
                setPath(output, mapping.getTarget(), value);

                // Record modifiers
                if (mapping.getModifiers() != null && mapping.getModifiers().hasAny()) {
                    var fullKey = pathPrefix.isEmpty() ? mapping.getTarget() : pathPrefix + "." + mapping.getTarget();
                    ctx.fieldModifiers.put(fullKey, mapping.getModifiers());
                }
            }
        } catch (TransformAbortException e) {
            // Guard aborts are not downgraded by onError.
            throw e;
        } catch (CodedTransformException e) {
            // Coded errors carry a stable T-code; preserve it under fail/warn.
            var onError = ctx.onError != null ? ctx.onError : "fail";
            var te = e.getTransformError();
            te.setPath(mapping.getTarget());
            if ("fail".equals(onError)) {
                ctx.errors.add(te);
            } else if ("warn".equals(onError)) {
                var w = new TransformWarning(te.getMessage());
                w.setPath(mapping.getTarget());
                w.setCode(te.getCode());
                ctx.warnings.add(w);
            }
        } catch (Exception e) {
            var onError = ctx.onError != null ? ctx.onError : "fail";
            if ("warn".equals(onError)) {
                var w = new TransformWarning(e.getMessage());
                w.setPath(mapping.getTarget());
                ctx.warnings.add(w);
            } else if (!"skip".equals(onError) && !"ignore".equals(onError)) {
                ctx.errors.add(new TransformError(e.getMessage(), mapping.getTarget()));
            }
        }
        return output;
    }

    private static OdinDirective findMappingDirective(FieldMapping mapping, String name) {
        if (mapping.getDirectives() == null) return null;
        for (var d : mapping.getDirectives()) {
            if (name.equals(d.getName())) return d;
        }
        return null;
    }

    // Format-specific field modifiers and the formats that accept them.
    private static final Map<String, Set<String>> FORMAT_SPECIFIC_MODIFIERS = Map.of(
            "pos", Set.of("fixed-width", "fwf"),
            "len", Set.of("fixed-width", "fwf"),
            "leftPad", Set.of("fixed-width", "fwf"),
            "rightPad", Set.of("fixed-width", "fwf"),
            "truncate", Set.of("fixed-width", "fwf"),
            "element", Set.of("xml"),
            "attr", Set.of("xml"),
            "ns", Set.of("xml"),
            "cdata", Set.of("xml"),
            "raw", Set.of("json"));

    private static void checkModifierFormat(FieldMapping mapping, ExecContext ctx) {
        if (mapping.getDirectives() == null) return;
        String fmt = ctx.targetFormat != null ? ctx.targetFormat : "odin";

        for (var d : mapping.getDirectives()) {
            var allowed = FORMAT_SPECIFIC_MODIFIERS.get(d.getName());
            if (allowed != null && !allowed.contains(fmt)) {
                ctx.warnings.add(OdinErrors.invalidModifierWarning(d.getName(), fmt));
            }
        }

        // T010: fixed-width pos + len beyond the configured line width.
        if ("fixed-width".equals(fmt) || "fwf".equals(fmt)) {
            Integer pos = directiveInt(mapping, "pos");
            Integer len = directiveInt(mapping, "len");
            int lineWidth = targetOptionInt(ctx, "lineWidth", 80);
            if (pos != null && len != null && pos + len > lineWidth) {
                ctx.errors.add(OdinErrors.positionOverflowError(mapping.getTarget(), pos, len, lineWidth));
            }
        }
    }

    private static Integer directiveInt(FieldMapping mapping, String name) {
        var d = findMappingDirective(mapping, name);
        if (d == null || d.getValue() == null) return null;
        var num = d.getValue().asNumber();
        if (num != null) return (int) Math.floor(num);
        var s = d.getValue().asString();
        try { return s != null ? Integer.parseInt(s.trim()) : null; }
        catch (NumberFormatException e) { return null; }
    }

    private static int targetOptionInt(ExecContext ctx, String key, int fallback) {
        if (ctx.targetOptions == null) return fallback;
        var v = ctx.targetOptions.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // Evaluate a field :if/:unless condition: `path` truthiness or `path op value`.
    private static boolean evaluateFieldCondition(String condition, ExecContext ctx, DynValue currentSource) {
        var trimmed = condition.trim();
        var matcher = CONDITION_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            var left = resolveFieldConditionPath(matcher.group(1), ctx, currentSource);
            return compareCondition(left, matcher.group(2), parseConditionValue(matcher.group(3).trim()));
        }
        return isTruthy(resolveFieldConditionPath(trimmed, ctx, currentSource));
    }

    private static DynValue resolveFieldConditionPath(String path, ExecContext ctx, DynValue currentSource) {
        if (path.startsWith(".")) {
            return resolvePath(currentSource, path, ctx.constants, ctx.accumulators);
        }
        if (currentSource != null && currentSource != ctx.source) {
            var fromCurrent = resolvePath(currentSource, path, ctx.constants, ctx.accumulators);
            if (fromCurrent != null && !fromCurrent.isNull()) return fromCurrent;
        }
        return resolvePath(ctx.source, path, ctx.constants, ctx.accumulators);
    }

    // Build a structural object from an inline :object {key = @path, ...} spec.
    private static DynValue buildInlineObject(String spec, ExecContext ctx, DynValue currentSource, DynValue output) {
        var trimmed = spec.trim();
        if (trimmed.startsWith("{")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("}")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        if (!trimmed.trim().isEmpty()) {
            for (var pair : splitObjectPairs(trimmed)) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                var key = pair.substring(0, eq).trim();
                var rhs = pair.substring(eq + 1).trim();
                if (key.isEmpty()) continue;
                var exprResult = parseStringExprForObject(rhs);
                var val = evaluateExpression(exprResult, ctx, currentSource, output);
                entries.add(Map.entry(key, val != null ? val : DynValue.ofNull()));
            }
        }
        return DynValue.ofObject(entries);
    }

    private static FieldExpression parseStringExprForObject(String rhs) {
        if (rhs.startsWith("@")) return FieldExpression.copy(rhs.substring(1));
        if (rhs.startsWith("$const.") || rhs.startsWith("$constants.")) return FieldExpression.copy(rhs);
        return FieldExpression.literal(OdinValue.ofString(rhs));
    }

    // Split an inline object body on commas not nested inside braces.
    private static List<String> splitObjectPairs(String body) {
        var pairs = new ArrayList<String>();
        int depth = 0;
        var current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') depth--;
            if (ch == ',' && depth == 0) {
                pairs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().trim().isEmpty()) pairs.add(current.toString());
        return pairs;
    }

    // Parse a string value as JSON for :raw, producing a structural value.
    private static DynValue parseRawJsonValue(DynValue value) {
        if (value.getType() != DynValue.Type.String) return value;
        var s = value.asString();
        if (s == null) return value;
        try {
            return JsonSourceParser.parse(s);
        } catch (Exception e) {
            return value;
        }
    }

    // Validate a value against :validate / :enum / :range directives.
    // Returns false when the field should be dropped (onValidation = skip or fail).
    private static boolean validateFieldValue(DynValue value, FieldMapping mapping, MappingMods mods, ExecContext ctx) {
        if (value == null || value.isNull()) return true;

        var v = mods.validation;
        if (!v.active) return true;

        var failures = new ArrayList<String>();
        var str = coerceToString(value);

        if (mods.validateDir != null && mods.validateDir.getValue() != null) {
            if (v.regexInvalid) {
                failures.add("invalid validation pattern '" + v.pattern + "'");
            } else if (!v.regex.matcher(str).find()) {
                failures.add("value '" + str + "' does not match pattern '" + v.pattern + "'");
            }
        }

        if (mods.enumDir != null && mods.enumDir.getValue() != null) {
            if (!v.enumSet.contains(str)) {
                failures.add("value '" + str + "' is not one of [" + String.join(", ", v.enumLabel) + "]");
            }
        }

        if (mods.rangeDir != null && mods.rangeDir.getValue() != null) {
            Double num = conditionNumber(value);
            if (num == null) {
                failures.add("value '" + str + "' is not numeric for range " + v.rangeStr);
            } else if ((v.rangeMin != null && num < v.rangeMin) || (v.rangeMax != null && num > v.rangeMax)) {
                failures.add("value " + str + " is outside range " + v.rangeStr);
            }
        }

        if (failures.isEmpty()) return true;

        var policy = ctx.onValidation != null ? ctx.onValidation : "fail";
        var message = "Validation failed for '" + mapping.getTarget() + "': " + String.join("; ", failures);
        if (policy.equals("warn")) {
            var w = new TransformWarning(message);
            w.setPath(mapping.getTarget());
            ctx.warnings.add(w);
            return true;
        }
        if (policy.equals("skip")) {
            return false;
        }
        var err = new TransformError(message, mapping.getTarget());
        err.setCode(OdinErrors.TransformErrorCodes.T013_VALIDATION_FAILED);
        ctx.errors.add(err);
        return false;
    }

    private static Double parseDoubleOrNull(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
    }

    // ── Expression Evaluation ──

    // Guard boundary: enforce depth, charge one fuel unit, and batch the wall-clock
    // check before delegating. All evaluation funnels through here, so charging
    // stays concentrated at this single point.
    private static DynValue evaluateExpression(FieldExpression expr, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        if (++ctx.exprDepth > ctx.maxExprDepth) {
            ctx.exprDepth--;
            throw new TransformAbortException(OdinErrors.expressionDepthExceededError(ctx.maxExprDepth));
        }
        charge(ctx, 1);
        try {
            return evaluateExpressionInner(expr, ctx, currentSource, currentOutput);
        } finally {
            ctx.exprDepth--;
        }
    }

    // Charge fuel and, at a coarse interval, the wall clock. Both are no-ops unless
    // their cap is set (> 0), so unbounded transforms pay nothing.
    private static void charge(ExecContext ctx, long units) {
        if (ctx.fuelCap > 0) {
            ctx.fuelUsed += units;
            if (ctx.fuelUsed > ctx.fuelCap) {
                throw new TransformAbortException(OdinErrors.budgetExceededError(ctx.fuelCap));
            }
        }
        if (ctx.timeoutMs > 0) {
            ctx.opsSinceClock += units;
            if (ctx.opsSinceClock >= CLOCK_CHECK_INTERVAL) {
                ctx.opsSinceClock = 0;
                if (clock.getAsLong() - ctx.startTime > ctx.timeoutMs) {
                    throw new TransformAbortException(OdinErrors.timeoutExceededError(ctx.timeoutMs));
                }
            }
        }
    }

    // Charge width for a verb doing O(n)/O(n log n) work over an array argument.
    // Runs whenever fuel or timeout is set, so a timeout-only host can interrupt
    // a single wide-array verb.
    private static void chargeVerbWidth(ExecContext ctx, String verb, DynValue[] args) {
        if (ctx.fuelCap <= 0 && ctx.timeoutMs <= 0) return;
        int n = firstArrayWidth(args);
        if (n <= 0) return;
        if (SORT_VERBS.contains(verb)) {
            charge(ctx, (long) n * (long) Math.ceil(Math.log(Math.max(n, 2)) / Math.log(2)));
        } else if (WIDTH_VERBS.contains(verb)) {
            charge(ctx, n);
        }
    }

    private static DynValue evaluateExpressionInner(FieldExpression expr, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        return switch (expr) {
            case CopyExpression copy -> {
                var path = copy.getPath();

                // A :counter is readable via @$accumulator.<name> when no accumulator owns the name.
                var counterAcc = accumulatorCounter(path, ctx);
                if (counterAcc != null) yield counterAcc;

                // Check loop vars
                if (ctx.loopVars.containsKey(path)) yield ctx.loopVars.get(path);
                // A loop alias may be addressed with a sub-path: @veh.vin → alias `veh`.
                var aliasResolved = resolveLoopAlias(path, ctx);
                if (aliasResolved != null) yield aliasResolved;
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

            case LiteralExpression lit -> {
                var litVal = lit.getValue();
                if (litVal instanceof OdinValue.OdinString strVal && strVal.getValue().contains("${")) {
                    yield interpolateString(strVal.getValue(), ctx, currentSource, currentOutput);
                }
                yield odinValueToDyn(litVal);
            }

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

    // Interpolate ${...} expressions in a string template.
    // ${@path}/${@.path} resolve a source path; ${%verb args} runs a verb; \${...} is a literal ${...}.
    private static final java.util.regex.Pattern INTERPOLATION =
            java.util.regex.Pattern.compile("\\\\?\\$\\{([^}]+)\\}");

    private static DynValue interpolateString(String template, ExecContext ctx,
            DynValue currentSource, DynValue currentOutput) {
        var matcher = INTERPOLATION.matcher(template);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group(0);
            String expr = matcher.group(1).trim();
            String replacement;
            if (match.startsWith("\\")) {
                replacement = "${" + matcher.group(1) + "}";
            } else if (expr.startsWith("@") || expr.startsWith("%")) {
                var fieldExpr = TransformParser.parseInlineExpression(expr);
                var value = evaluateExpression(fieldExpr, ctx, currentSource, currentOutput);
                replacement = coerceToString(value);
            } else {
                replacement = match;
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return DynValue.ofString(sb.toString());
    }

    // ── Verb Call Execution ──

    // Control-flow verbs whose branches evaluate lazily: the condition runs first
    // and only the selected branch runs, so unselected branches do not fire side
    // effects and and/or/coalesce short-circuit.
    private static final java.util.Set<String> LAZY_VERBS = java.util.Set.of(
            "ifElse", "ifNull", "ifEmpty", "coalesce", "and", "or", "cond", "switch");

    private static DynValue executeVerbCall(VerbCall call, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        // Guard boundary: nested verb calls recurse through here, so enforce depth
        // and charge one unit per call node.
        if (++ctx.exprDepth > ctx.maxExprDepth) {
            ctx.exprDepth--;
            throw new TransformAbortException(OdinErrors.expressionDepthExceededError(ctx.maxExprDepth));
        }
        charge(ctx, 1);
        try {
            return executeVerbCallInner(call, ctx, currentSource, currentOutput);
        } finally {
            ctx.exprDepth--;
        }
    }

    private static DynValue executeVerbCallInner(VerbCall call, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        // Strict mode validates all argument types, so it evaluates eagerly.
        if (!call.isCustom() && !ctx.strictTypes && LAZY_VERBS.contains(call.getVerb())) {
            var lazy = evaluateLazyVerb(call, ctx, currentSource, currentOutput);
            if (lazy != null) return lazy;
        }

        // Evaluate all arguments
        var evaluatedArgs = new DynValue[call.getArgs().size()];
        for (int i = 0; i < call.getArgs().size(); i++) {
            evaluatedArgs[i] = evaluateVerbArg(call.getArgs().get(i), ctx, currentSource, currentOutput);
        }
        chargeVerbWidth(ctx, call.getVerb(), evaluatedArgs);

        // Look up verb
        var verbFn = ctx.verbs.get(call.getVerb());
        if (verbFn == null) {
            if (call.isCustom()) {
                return evaluatedArgs.length > 0 ? evaluatedArgs[0] : DynValue.ofNull();
            }
            // T001: unknown built-in verb.
            throw new CodedTransformException(OdinErrors.unknownVerbError(call.getVerb()));
        }

        // T002: strict-mode verb argument type validation.
        if (ctx.strictTypes && !call.isCustom()) {
            String typeError = VerbSignatures.validate(call.getVerb(), evaluatedArgs);
            if (typeError != null)
                throw new CodedTransformException(OdinErrors.invalidVerbArgsError(call.getVerb(), typeError));
        }

        var verbCtx = new VerbContext();
        verbCtx.source = ctx.source;
        verbCtx.loopVars = new LinkedHashMap<>(ctx.loopVars);
        verbCtx.accumulators = ctx.accumulators;
        verbCtx.tables = ctx.tables;
        verbCtx.globalOutput = ctx.globalOutput;
        verbCtx.onMissing = ctx.onMissing;
        verbCtx.errors = ctx.errors;
        verbCtx.warnings = ctx.warnings;

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

    // Evaluate a control-flow verb lazily, running only the arguments needed to
    // decide the result. Returns null to defer to eager evaluation (too few args).
    private static DynValue evaluateLazyVerb(VerbCall call, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        var args = call.getArgs();
        java.util.function.IntFunction<DynValue> ev = i -> evaluateVerbArg(args.get(i), ctx, currentSource, currentOutput);
        switch (call.getVerb()) {
            case "ifElse":
                if (args.size() < 3) return null;
                return isTruthy(ev.apply(0)) ? ev.apply(1) : ev.apply(2);
            case "ifNull": {
                if (args.size() < 2) return null;
                var v0 = ev.apply(0);
                return v0.isNull() ? ev.apply(1) : v0;
            }
            case "ifEmpty": {
                if (args.size() < 2) return null;
                var v0 = ev.apply(0);
                boolean empty = v0.isNull()
                        || (v0.getType() == DynValue.Type.String && "".equals(v0.asString()));
                return empty ? ev.apply(1) : v0;
            }
            case "coalesce": {
                for (int i = 0; i < args.size(); i++) {
                    var v = ev.apply(i);
                    boolean skip = v.isNull()
                            || (v.getType() == DynValue.Type.String && "".equals(v.asString()));
                    if (!skip) return v;
                }
                return DynValue.ofNull();
            }
            case "and":
                if (args.size() < 2) return null;
                if (!isTruthy(ev.apply(0))) return DynValue.ofBool(false);
                return DynValue.ofBool(isTruthy(ev.apply(1)));
            case "or":
                if (args.size() < 2) return null;
                if (isTruthy(ev.apply(0))) return DynValue.ofBool(true);
                return DynValue.ofBool(isTruthy(ev.apply(1)));
            case "cond": {
                if (args.isEmpty()) return null;
                int i = 0;
                while (i + 1 < args.size()) {
                    if (isTruthy(ev.apply(i))) return ev.apply(i + 1);
                    i += 2;
                }
                return args.size() % 2 == 1 ? ev.apply(args.size() - 1) : DynValue.ofNull();
            }
            case "switch": {
                if (args.size() < 2) return null;
                var subject = ev.apply(0);
                for (int i = 1; i + 1 < args.size(); i += 2) {
                    if (VerbHelpers.dynValuesEqual(subject, ev.apply(i))) return ev.apply(i + 1);
                }
                return (args.size() - 1) % 2 == 1 ? ev.apply(args.size() - 1) : DynValue.ofNull();
            }
            default:
                return null;
        }
    }

    private static DynValue evaluateVerbArg(VerbArg arg, ExecContext ctx, DynValue currentSource, DynValue currentOutput) {
        return switch (arg) {
            case ReferenceArg refArg -> {
                var path = refArg.getPath();
                DynValue resolved;

                var counterAcc = accumulatorCounter(path, ctx);
                var aliasResolved = accumulatorCounter(path, ctx) == null ? resolveLoopAlias(path, ctx) : null;
                // Loop vars
                if (counterAcc != null) resolved = counterAcc;
                else if (ctx.loopVars.containsKey(path)) resolved = ctx.loopVars.get(path);
                else if (aliasResolved != null) resolved = aliasResolved;
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

    // Resolve $accumulator.<name> to a live :counter loop variable when no accumulator owns the name.
    private static DynValue accumulatorCounter(String path, ExecContext ctx) {
        String name = null;
        if (path.startsWith("$accumulator.")) name = path.substring("$accumulator.".length());
        else if (path.startsWith("$accumulators.")) name = path.substring("$accumulators.".length());
        if (name == null) return null;
        if (ctx.accumulators.containsKey(name)) return null;
        var counter = ctx.loopVars.get(name);
        return counter != null ? counter : null;
    }

    // Resolve a path whose first segment names a bound loop alias (e.g. @veh.vin).
    // Returns null when the path is not alias-rooted.
    private static DynValue resolveLoopAlias(String path, ExecContext ctx) {
        if (path == null || path.isEmpty()) return null;
        String clean = path.startsWith("@") ? path.substring(1) : path;
        if (clean.isEmpty() || clean.charAt(0) == '.') return null;
        int dot = clean.indexOf('.');
        int bracket = clean.indexOf('[');
        int end = clean.length();
        if (dot >= 0) end = Math.min(end, dot);
        if (bracket >= 0) end = Math.min(end, bracket);
        String first = clean.substring(0, end);
        if (!ctx.loopAliases.contains(first)) return null;
        var aliasValue = ctx.loopVars.get(first);
        if (aliasValue == null) return null;
        String rest = clean.length() > end ? clean.substring(end) : "";
        if (rest.startsWith(".")) rest = rest.substring(1);
        return rest.isEmpty() ? aliasValue : resolveSubPath(aliasValue, rest);
    }

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

    // Whether a mapping copies a source path that is absent (no such key) — distinct
    // from a path present with a null value. Only plain copy expressions qualify;
    // verbs, literals, objects, special and counter paths are never "missing source".
    private static boolean isCopySourceAbsent(FieldMapping mapping, MappingMods mods, ExecContext ctx, DynValue currentSource) {
        if (!(mapping.getExpression() instanceof CopyExpression copy)) return false;
        if (mods.required && mods.objectDir != null) {
            return false;
        }
        if (mods.hasDefault || mods.objectDir != null) {
            return false;
        }
        var path = copy.getPath();
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith("$") || path.equals("_index") || path.equals("_item")
                || path.equals("_length")) return false;
        if (ctx.loopVars.containsKey(path)) return false;
        if (resolveLoopAlias(path, ctx) != null) return false;

        String clean = path.startsWith("@") ? path.substring(1) : path;
        DynValue target;
        String targetPath;
        if (clean.startsWith(".")) {
            target = currentSource != null ? currentSource : ctx.source;
            targetPath = clean.substring(1);
        } else {
            int dot = clean.indexOf('.');
            String first = dot >= 0 ? clean.substring(0, dot) : clean;
            if (ctx.loopAliases.contains(first)) return false;
            target = ctx.source;
            targetPath = clean;
        }
        return targetPath.isEmpty() ? false : !pathPresent(target, targetPath);
    }

    // Whether every segment of a dotted/indexed path exists on the value.
    private static boolean pathPresent(DynValue value, String path) {
        if (value == null) return false;
        var segments = parsePathSegments(path);
        var current = value;
        for (var seg : segments) {
            if (current == null || current.isNull()) return false;
            if (seg.index >= 0) {
                if (!seg.field.isEmpty()) {
                    if (!hasField(current, seg.field)) return false;
                    current = current.get(seg.field);
                    if (current == null || current.isNull()) return false;
                }
                if (current.asArray() == null || seg.index >= current.asArray().size()) return false;
                current = current.getIndex(seg.index);
            } else {
                if (!hasField(current, seg.field)) return false;
                current = current.get(seg.field);
            }
            if (current == null) return false;
        }
        return true;
    }

    private static boolean hasField(DynValue obj, String field) {
        var entries = obj.asObject();
        if (entries == null) return false;
        for (var e : entries) if (e.getKey().equals(field)) return true;
        return false;
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

    // Evaluate a segment condition: comparison expression or path truthiness.
    private static boolean evaluateCondition(String condition, ExecContext ctx) {
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
                    // Compare F-format to G-format: if they differ, trailing zeros matter
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
                        // Use G-format comparison to detect trailing zeros
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

    private static final Set<String> KNOWN_FORMATS = Set.of(
            "odin", "json", "xml", "csv", "fixed-width", "flat", "properties");

    private static boolean isKnownFormat(String format) {
        return format != null && KNOWN_FORMATS.contains(format.toLowerCase());
    }

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
