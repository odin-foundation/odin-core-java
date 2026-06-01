package foundation.odin.types;

import java.util.*;

// ── Enums ──

public final class OdinTransformTypes {
    private OdinTransformTypes() {}

    public enum ConfidentialMode { REDACT, MASK }

    public enum DiscriminatorType { POSITION, FIELD, PATH }

    // ── OdinTransform ──

    public static final class OdinTransform {
        private TransformMetadata metadata = new TransformMetadata();
        private TransformSourceConfig source;
        private TransformTargetConfig target = new TransformTargetConfig();
        private Map<String, OdinValue> constants = new LinkedHashMap<>();
        private Map<String, AccumulatorDef> accumulators = new LinkedHashMap<>();
        private Map<String, LookupTable> tables = new LinkedHashMap<>();
        private List<TransformSegment> segments = new ArrayList<>();
        private List<ImportRef> imports = new ArrayList<>();
        private List<Integer> passes = new ArrayList<>();
        private ConfidentialMode enforceConfidential;
        private boolean strictTypes;

        public TransformMetadata getMetadata() { return metadata; }
        public void setMetadata(TransformMetadata metadata) { this.metadata = metadata; }

        public TransformSourceConfig getSource() { return source; }
        public void setSource(TransformSourceConfig source) { this.source = source; }

        public TransformTargetConfig getTarget() { return target; }
        public void setTarget(TransformTargetConfig target) { this.target = target; }

        public Map<String, OdinValue> getConstants() { return constants; }
        public void setConstants(Map<String, OdinValue> constants) { this.constants = constants; }

        public Map<String, AccumulatorDef> getAccumulators() { return accumulators; }
        public void setAccumulators(Map<String, AccumulatorDef> accumulators) { this.accumulators = accumulators; }

        public Map<String, LookupTable> getTables() { return tables; }
        public void setTables(Map<String, LookupTable> tables) { this.tables = tables; }

        public List<TransformSegment> getSegments() { return segments; }
        public void setSegments(List<TransformSegment> segments) { this.segments = segments; }

        public List<ImportRef> getImports() { return imports; }
        public void setImports(List<ImportRef> imports) { this.imports = imports; }

        public List<Integer> getPasses() { return passes; }
        public void setPasses(List<Integer> passes) { this.passes = passes; }

        public ConfidentialMode getEnforceConfidential() { return enforceConfidential; }
        public void setEnforceConfidential(ConfidentialMode mode) { this.enforceConfidential = mode; }

        public boolean isStrictTypes() { return strictTypes; }
        public void setStrictTypes(boolean strictTypes) { this.strictTypes = strictTypes; }
    }

    // ── TransformMetadata ──

    public static final class TransformMetadata {
        private String odinVersion;
        private String transformVersion;
        private String direction;
        private String name;
        private String description;

        public String getOdinVersion() { return odinVersion; }
        public void setOdinVersion(String v) { this.odinVersion = v; }

        public String getTransformVersion() { return transformVersion; }
        public void setTransformVersion(String v) { this.transformVersion = v; }

        public String getDirection() { return direction; }
        public void setDirection(String v) { this.direction = v; }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    // ── TransformSourceConfig ──

    public static final class TransformSourceConfig {
        private String format = "";
        private Map<String, String> options = new LinkedHashMap<>();
        private Map<String, String> namespaces = new LinkedHashMap<>();
        private SourceDiscriminator discriminator;

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }

        public Map<String, String> getNamespaces() { return namespaces; }
        public void setNamespaces(Map<String, String> namespaces) { this.namespaces = namespaces; }

        public SourceDiscriminator getDiscriminator() { return discriminator; }
        public void setDiscriminator(SourceDiscriminator d) { this.discriminator = d; }
    }

    // ── TransformTargetConfig ──

    public static final class TransformTargetConfig {
        private String format = "";
        private Map<String, String> options = new LinkedHashMap<>();

        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }

        public Map<String, String> getOptions() { return options; }
        public void setOptions(Map<String, String> options) { this.options = options; }
    }

    // ── AccumulatorDef ──

    public static final class AccumulatorDef {
        private String name = "";
        private OdinValue initial = OdinValue.ofNull();
        private boolean persist;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public OdinValue getInitial() { return initial; }
        public void setInitial(OdinValue initial) { this.initial = initial; }

        public boolean isPersist() { return persist; }
        public void setPersist(boolean persist) { this.persist = persist; }
    }

    // ── LookupTable ──

    public static final class LookupTable {
        private String name = "";
        private List<String> columns = new ArrayList<>();
        private List<List<DynValue>> rows = new ArrayList<>();
        private DynValue defaultValue;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }

        public List<List<DynValue>> getRows() { return rows; }
        public void setRows(List<List<DynValue>> rows) { this.rows = rows; }

        public DynValue getDefault() { return defaultValue; }
        public void setDefault(DynValue defaultValue) { this.defaultValue = defaultValue; }
    }

    // ── TransformSegment ──

    public static final class TransformSegment {
        private String name = "";
        private String path = "";
        private String sourcePath;
        private String counterName;
        private Discriminator segmentDiscriminator;
        private boolean isArray;
        private List<SegmentDirective> directives = new ArrayList<>();
        private List<FieldMapping> mappings = new ArrayList<>();
        private List<TransformSegment> children = new ArrayList<>();
        private List<SegmentItem> items = new ArrayList<>();
        private Integer pass;
        private String condition;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

        public String getCounterName() { return counterName; }
        public void setCounterName(String counterName) { this.counterName = counterName; }

        public Discriminator getSegmentDiscriminator() { return segmentDiscriminator; }
        public void setSegmentDiscriminator(Discriminator d) { this.segmentDiscriminator = d; }

        public boolean isArray() { return isArray; }
        public void setIsArray(boolean isArray) { this.isArray = isArray; }

        public List<SegmentDirective> getDirectives() { return directives; }
        public void setDirectives(List<SegmentDirective> directives) { this.directives = directives; }

        public List<FieldMapping> getMappings() { return mappings; }
        public void setMappings(List<FieldMapping> mappings) { this.mappings = mappings; }

        public List<TransformSegment> getChildren() { return children; }
        public void setChildren(List<TransformSegment> children) { this.children = children; }

        public List<SegmentItem> getItems() { return items; }
        public void setItems(List<SegmentItem> items) { this.items = items; }

        public Integer getPass() { return pass; }
        public void setPass(Integer pass) { this.pass = pass; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
    }

    // ── SegmentDirective ──

    public static final class SegmentDirective {
        private String directiveType = "";
        private String value;
        private FieldExpression expr;
        private String alias;

        public String getDirectiveType() { return directiveType; }
        public void setDirectiveType(String type) { this.directiveType = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public FieldExpression getExpr() { return expr; }
        public void setExpr(FieldExpression expr) { this.expr = expr; }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    // ── SegmentItem (discriminated union) ──

    public static abstract sealed class SegmentItem permits SegmentItem.MappingItem, SegmentItem.ChildItem {
        public static SegmentItem fromMapping(FieldMapping mapping) { return new MappingItem(mapping); }
        public static SegmentItem fromChild(TransformSegment child) { return new ChildItem(child); }

        public FieldMapping asMapping() { return null; }
        public TransformSegment asChild() { return null; }

        public static final class MappingItem extends SegmentItem {
            private final FieldMapping mapping;
            public MappingItem(FieldMapping mapping) { this.mapping = mapping; }
            @Override public FieldMapping asMapping() { return mapping; }
        }

        public static final class ChildItem extends SegmentItem {
            private final TransformSegment child;
            public ChildItem(TransformSegment child) { this.child = child; }
            @Override public TransformSegment asChild() { return child; }
        }
    }

    // ── Discriminator ──

    public static final class Discriminator {
        private String path = "";
        private String value = "";

        public Discriminator() {}
        public Discriminator(String path, String value) { this.path = path; this.value = value; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // ── SourceDiscriminator ──

    public static final class SourceDiscriminator {
        private DiscriminatorType type;
        private Integer pos;
        private Integer len;
        private Integer field;
        private String path;

        public DiscriminatorType getType() { return type; }
        public void setType(DiscriminatorType type) { this.type = type; }

        public Integer getPos() { return pos; }
        public void setPos(Integer pos) { this.pos = pos; }

        public Integer getLen() { return len; }
        public void setLen(Integer len) { this.len = len; }

        public Integer getField() { return field; }
        public void setField(Integer field) { this.field = field; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    // ── MultiRecordInput ──

    public static final class MultiRecordInput {
        private List<String> records = new ArrayList<>();
        private String delimiter;

        public List<String> getRecords() { return records; }
        public void setRecords(List<String> records) { this.records = records; }

        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    }

    // ── FieldMapping ──

    public static final class FieldMapping {
        private String target = "";
        private FieldExpression expression;
        private OdinModifiers modifiers;
        private List<OdinDirective> directives = new ArrayList<>();

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public FieldExpression getExpression() { return expression; }
        public void setExpression(FieldExpression expression) { this.expression = expression; }

        public OdinModifiers getModifiers() { return modifiers; }
        public void setModifiers(OdinModifiers modifiers) { this.modifiers = modifiers; }

        public List<OdinDirective> getDirectives() { return directives; }
        public void setDirectives(List<OdinDirective> directives) { this.directives = directives; }
    }

    // ── FieldExpression (discriminated union) ──

    public static abstract sealed class FieldExpression
            permits FieldExpression.CopyExpression, FieldExpression.TransformExpression,
                    FieldExpression.LiteralExpression, FieldExpression.ObjectExpression {

        public static CopyExpression copy(String path) { return new CopyExpression(path); }
        public static TransformExpression transform(VerbCall call) { return new TransformExpression(call); }
        public static LiteralExpression literal(OdinValue value) { return new LiteralExpression(value); }
        public static ObjectExpression object(List<FieldMapping> fields) { return new ObjectExpression(fields); }

        public static final class CopyExpression extends FieldExpression {
            private final String path;
            public CopyExpression(String path) { this.path = path; }
            public String getPath() { return path; }
        }

        public static final class TransformExpression extends FieldExpression {
            private final VerbCall call;
            public TransformExpression(VerbCall call) { this.call = call; }
            public VerbCall getCall() { return call; }
        }

        public static final class LiteralExpression extends FieldExpression {
            private final OdinValue value;
            public LiteralExpression(OdinValue value) { this.value = value; }
            public OdinValue getValue() { return value; }
        }

        public static final class ObjectExpression extends FieldExpression {
            private final List<FieldMapping> fields;
            public ObjectExpression(List<FieldMapping> fields) { this.fields = fields; }
            public List<FieldMapping> getFields() { return fields; }
        }
    }

    // ── VerbCall ──

    public static final class VerbCall {
        private String verb = "";
        private boolean isCustom;
        private List<VerbArg> args = new ArrayList<>();

        public String getVerb() { return verb; }
        public void setVerb(String verb) { this.verb = verb; }

        public boolean isCustom() { return isCustom; }
        public void setIsCustom(boolean isCustom) { this.isCustom = isCustom; }

        public List<VerbArg> getArgs() { return args; }
        public void setArgs(List<VerbArg> args) { this.args = args; }
    }

    // ── VerbArg (discriminated union) ──

    public static abstract sealed class VerbArg
            permits VerbArg.ReferenceArg, VerbArg.LiteralArg, VerbArg.VerbCallArg {

        public static ReferenceArg ref(String path) { return new ReferenceArg(path, List.of()); }
        public static ReferenceArg ref(String path, List<OdinDirective> directives) { return new ReferenceArg(path, directives); }
        public static LiteralArg lit(OdinValue value) { return new LiteralArg(value); }
        public static VerbCallArg nestedCall(VerbCall call) { return new VerbCallArg(call); }

        public static final class ReferenceArg extends VerbArg {
            private final String path;
            private final List<OdinDirective> directives;
            public ReferenceArg(String path, List<OdinDirective> directives) { this.path = path; this.directives = directives; }
            public String getPath() { return path; }
            public List<OdinDirective> getDirectives() { return directives; }
        }

        public static final class LiteralArg extends VerbArg {
            private final OdinValue value;
            public LiteralArg(OdinValue value) { this.value = value; }
            public OdinValue getValue() { return value; }
        }

        public static final class VerbCallArg extends VerbArg {
            private final VerbCall nestedCall;
            public VerbCallArg(VerbCall call) { this.nestedCall = call; }
            public VerbCall getNestedCall() { return nestedCall; }
        }
    }

    // ── ImportRef ──

    public static final class ImportRef {
        private String path = "";
        private String alias;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
    }

    // ── TransformResult ──

    public static final class TransformResult {
        private boolean success;
        private DynValue output;
        private String formatted;
        private List<TransformError> errors = new ArrayList<>();
        private List<TransformWarning> warnings = new ArrayList<>();
        private Map<String, OdinModifiers> outputModifiers = new LinkedHashMap<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public DynValue getOutput() { return output; }
        public void setOutput(DynValue output) { this.output = output; }

        public String getFormatted() { return formatted; }
        public void setFormatted(String formatted) { this.formatted = formatted; }

        public List<TransformError> getErrors() { return errors; }
        public void setErrors(List<TransformError> errors) { this.errors = errors; }

        public List<TransformWarning> getWarnings() { return warnings; }
        public void setWarnings(List<TransformWarning> warnings) { this.warnings = warnings; }

        public Map<String, OdinModifiers> getOutputModifiers() { return outputModifiers; }
        public void setOutputModifiers(Map<String, OdinModifiers> m) { this.outputModifiers = m; }
    }

    // ── TransformError ──

    public static final class TransformError {
        private String message = "";
        private String path;
        private String code;

        public TransformError() {}
        public TransformError(String message) { this.message = message; }
        public TransformError(String message, String path) { this.message = message; this.path = path; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    // ── TransformWarning ──

    public static final class TransformWarning {
        private String message = "";
        private String path;

        public TransformWarning() {}
        public TransformWarning(String message) { this.message = message; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
