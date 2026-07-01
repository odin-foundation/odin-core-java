package foundation.odin.types;

import java.util.Collections;
import java.util.List;

// ── Parse error codes (P001-P015) ──

public final class OdinErrors {
    private OdinErrors() {}

    public enum ParseErrorCode {
        UnexpectedCharacter,
        BareStringNotAllowed,
        InvalidArrayIndex,
        UnterminatedString,
        InvalidEscapeSequence,
        InvalidTypePrefix,
        DuplicatePathAssignment,
        InvalidHeaderSyntax,
        InvalidDirective,
        MaximumDepthExceeded,
        MaximumDocumentSizeExceeded,
        InvalidUtf8Sequence,
        NonContiguousArrayIndices,
        EmptyDocument,
        ArrayIndexOutOfRange;

        public String code() {
            return switch (this) {
                case UnexpectedCharacter -> "P001";
                case BareStringNotAllowed -> "P002";
                case InvalidArrayIndex -> "P003";
                case UnterminatedString -> "P004";
                case InvalidEscapeSequence -> "P005";
                case InvalidTypePrefix -> "P006";
                case DuplicatePathAssignment -> "P007";
                case InvalidHeaderSyntax -> "P008";
                case InvalidDirective -> "P009";
                case MaximumDepthExceeded -> "P010";
                case MaximumDocumentSizeExceeded -> "P011";
                case InvalidUtf8Sequence -> "P012";
                case NonContiguousArrayIndices -> "P013";
                case EmptyDocument -> "P014";
                case ArrayIndexOutOfRange -> "P015";
            };
        }

        public String message() {
            return switch (this) {
                case UnexpectedCharacter -> "Unexpected character";
                case BareStringNotAllowed -> "Strings must be quoted";
                case InvalidArrayIndex -> "Invalid array index";
                case UnterminatedString -> "Unterminated string";
                case InvalidEscapeSequence -> "Invalid escape sequence";
                case InvalidTypePrefix -> "Invalid type prefix";
                case DuplicatePathAssignment -> "Duplicate path assignment";
                case InvalidHeaderSyntax -> "Invalid header syntax";
                case InvalidDirective -> "Invalid directive";
                case MaximumDepthExceeded -> "Maximum depth exceeded";
                case MaximumDocumentSizeExceeded -> "Maximum document size exceeded";
                case InvalidUtf8Sequence -> "Invalid UTF-8 sequence";
                case NonContiguousArrayIndices -> "Non-contiguous array indices";
                case EmptyDocument -> "Empty document";
                case ArrayIndexOutOfRange -> "Array index out of range";
            };
        }

        public static ParseErrorCode fromCode(String code) {
            return switch (code) {
                case "P001" -> UnexpectedCharacter;
                case "P002" -> BareStringNotAllowed;
                case "P003" -> InvalidArrayIndex;
                case "P004" -> UnterminatedString;
                case "P005" -> InvalidEscapeSequence;
                case "P006" -> InvalidTypePrefix;
                case "P007" -> DuplicatePathAssignment;
                case "P008" -> InvalidHeaderSyntax;
                case "P009" -> InvalidDirective;
                case "P010" -> MaximumDepthExceeded;
                case "P011" -> MaximumDocumentSizeExceeded;
                case "P012" -> InvalidUtf8Sequence;
                case "P013" -> NonContiguousArrayIndices;
                case "P014" -> EmptyDocument;
                case "P015" -> ArrayIndexOutOfRange;
                default -> null;
            };
        }
    }

    // ── Validation error codes (V001-V013) ──

    public enum ValidationErrorCode {
        RequiredFieldMissing,
        TypeMismatch,
        ValueOutOfBounds,
        PatternMismatch,
        InvalidEnumValue,
        ArrayLengthViolation,
        UniqueConstraintViolation,
        InvariantViolation,
        CardinalityConstraintViolation,
        ConditionalRequirementNotMet,
        UnknownField,
        CircularReference,
        UnresolvedReference;

        public String code() {
            return switch (this) {
                case RequiredFieldMissing -> "V001";
                case TypeMismatch -> "V002";
                case ValueOutOfBounds -> "V003";
                case PatternMismatch -> "V004";
                case InvalidEnumValue -> "V005";
                case ArrayLengthViolation -> "V006";
                case UniqueConstraintViolation -> "V007";
                case InvariantViolation -> "V008";
                case CardinalityConstraintViolation -> "V009";
                case ConditionalRequirementNotMet -> "V010";
                case UnknownField -> "V011";
                case CircularReference -> "V012";
                case UnresolvedReference -> "V013";
            };
        }

        public String message() {
            return switch (this) {
                case RequiredFieldMissing -> "Required field missing";
                case TypeMismatch -> "Type mismatch";
                case ValueOutOfBounds -> "Value out of bounds";
                case PatternMismatch -> "Pattern mismatch";
                case InvalidEnumValue -> "Invalid enum value";
                case ArrayLengthViolation -> "Array length violation";
                case UniqueConstraintViolation -> "Unique constraint violation";
                case InvariantViolation -> "Invariant violation";
                case CardinalityConstraintViolation -> "Cardinality constraint violation";
                case ConditionalRequirementNotMet -> "Conditional requirement not met";
                case UnknownField -> "Unknown field";
                case CircularReference -> "Circular reference";
                case UnresolvedReference -> "Unresolved reference";
            };
        }

        public static ValidationErrorCode fromCode(String code) {
            return switch (code) {
                case "V001" -> RequiredFieldMissing;
                case "V002" -> TypeMismatch;
                case "V003" -> ValueOutOfBounds;
                case "V004" -> PatternMismatch;
                case "V005" -> InvalidEnumValue;
                case "V006" -> ArrayLengthViolation;
                case "V007" -> UniqueConstraintViolation;
                case "V008" -> InvariantViolation;
                case "V009" -> CardinalityConstraintViolation;
                case "V010" -> ConditionalRequirementNotMet;
                case "V011" -> UnknownField;
                case "V012" -> CircularReference;
                case "V013" -> UnresolvedReference;
                default -> null;
            };
        }
    }

    // ── OdinParseException ──

    public static class OdinParseException extends RuntimeException {
        private final ParseErrorCode errorCode;
        private final int line;
        private final int column;

        public OdinParseException(ParseErrorCode errorCode, int line, int column) {
            super(errorCode.message() + " at line " + line + ", column " + column);
            this.errorCode = errorCode;
            this.line = line;
            this.column = column;
        }

        public OdinParseException(ParseErrorCode errorCode, int line, int column, String detail) {
            super(errorCode.message() + ": " + detail + " at line " + line + ", column " + column);
            this.errorCode = errorCode;
            this.line = line;
            this.column = column;
        }

        public ParseErrorCode getErrorCode() { return errorCode; }
        public String getCode() { return errorCode.code(); }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }

    // ── ValidationError ──

    public static final class ValidationError {
        private final String path;
        private final ValidationErrorCode errorCode;
        private final String errorMessage;
        private final String expected;
        private final String actual;
        private final String schemaPath;

        public ValidationError(ValidationErrorCode errorCode, String path, String message) {
            this(errorCode, path, message, null, null, null);
        }

        public ValidationError(ValidationErrorCode errorCode, String path, String message,
                               String expected, String actual, String schemaPath) {
            this.path = path;
            this.errorCode = errorCode;
            this.errorMessage = message;
            this.expected = expected;
            this.actual = actual;
            this.schemaPath = schemaPath;
        }

        public String getPath() { return path; }
        public ValidationErrorCode getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public String getCode() { return errorCode.code(); }
        public String getExpected() { return expected; }
        public String getActual() { return actual; }
        public String getSchemaPath() { return schemaPath; }

        @Override
        public String toString() {
            return "[" + getCode() + "] " + errorMessage + " at '" + path + "'";
        }
    }

    // ── ValidationResult ──

    public static final class ValidationResult {
        private final List<ValidationError> errors;

        private ValidationResult(List<ValidationError> errors) {
            this.errors = errors;
        }

        public static ValidationResult valid() {
            return new ValidationResult(Collections.emptyList());
        }

        public static ValidationResult withErrors(List<ValidationError> errors) {
            return new ValidationResult(Collections.unmodifiableList(errors));
        }

        public boolean isValid() { return errors.isEmpty(); }
        public List<ValidationError> getErrors() { return errors; }
    }

    // ── String-based error code constants ──

    public static final class ParseErrorCodes {
        public static final String UNEXPECTED_CHARACTER = "P001";
        public static final String BARE_STRING_NOT_ALLOWED = "P002";
        public static final String INVALID_ARRAY_INDEX = "P003";
        public static final String UNTERMINATED_STRING = "P004";
        public static final String INVALID_ESCAPE_SEQUENCE = "P005";
        public static final String INVALID_TYPE_PREFIX = "P006";
        public static final String DUPLICATE_PATH_ASSIGNMENT = "P007";
        public static final String INVALID_HEADER_SYNTAX = "P008";
        public static final String INVALID_DIRECTIVE = "P009";
        public static final String MAXIMUM_DEPTH_EXCEEDED = "P010";
        public static final String MAXIMUM_DOCUMENT_SIZE_EXCEEDED = "P011";
        public static final String INVALID_UTF8_SEQUENCE = "P012";
        public static final String NON_CONTIGUOUS_ARRAY_INDICES = "P013";
        public static final String EMPTY_DOCUMENT = "P014";
        public static final String ARRAY_INDEX_OUT_OF_RANGE = "P015";

        private ParseErrorCodes() {}

        public static String message(String code) {
            var ec = ParseErrorCode.fromCode(code);
            return ec != null ? ec.message() : "Unknown error";
        }
    }

    public static final class ValidationErrorCodes {
        public static final String REQUIRED_FIELD_MISSING = "V001";
        public static final String TYPE_MISMATCH = "V002";
        public static final String VALUE_OUT_OF_BOUNDS = "V003";
        public static final String PATTERN_MISMATCH = "V004";
        public static final String INVALID_ENUM_VALUE = "V005";
        public static final String ARRAY_LENGTH_VIOLATION = "V006";
        public static final String UNIQUE_CONSTRAINT_VIOLATION = "V007";
        public static final String INVARIANT_VIOLATION = "V008";
        public static final String CARDINALITY_CONSTRAINT_VIOLATION = "V009";
        public static final String CONDITIONAL_REQUIREMENT_NOT_MET = "V010";
        public static final String UNKNOWN_FIELD = "V011";
        public static final String CIRCULAR_REFERENCE = "V012";
        public static final String UNRESOLVED_REFERENCE = "V013";

        private ValidationErrorCodes() {}

        public static String message(String code) {
            var ec = ValidationErrorCode.fromCode(code);
            return ec != null ? ec.message() : "Unknown error";
        }
    }

    // ── Transform error codes (T001-T018) ──

    public static final class TransformErrorCodes {
        public static final String T001_UNKNOWN_VERB = "T001";
        public static final String T002_INVALID_VERB_ARGS = "T002";
        public static final String T003_LOOKUP_TABLE_NOT_FOUND = "T003";
        public static final String T004_LOOKUP_KEY_NOT_FOUND = "T004";
        public static final String T005_SOURCE_PATH_NOT_FOUND = "T005";
        public static final String T006_INVALID_OUTPUT_FORMAT = "T006";
        public static final String T007_INVALID_MODIFIER = "T007";
        public static final String T008_ACCUMULATOR_OVERFLOW = "T008";
        public static final String T009_LOOP_SOURCE_NOT_ARRAY = "T009";
        public static final String T010_POSITION_OVERFLOW = "T010";
        public static final String T011_INCOMPATIBLE_CONVERSION = "T011";
        public static final String T012_DANGLING_BRANCH = "T012";
        public static final String T013_VALIDATION_FAILED = "T013";
        public static final String T014_NESTED_INTERPOLATION = "T014";
        public static final String T015_INVALID_EXPRESSION = "T015";
        public static final String T016_TRANSFORM_BUDGET_EXCEEDED = "T016";
        public static final String T017_TRANSFORM_TIMEOUT_EXCEEDED = "T017";
        public static final String T018_EXPRESSION_DEPTH_EXCEEDED = "T018";

        private TransformErrorCodes() {}
    }

    // Transform fuel budget exceeded (T016).
    public static OdinTransformTypes.TransformError budgetExceededError(int limit) {
        var error = new OdinTransformTypes.TransformError(
                "Transform fuel budget exceeded (limit " + limit + ")");
        error.setCode(TransformErrorCodes.T016_TRANSFORM_BUDGET_EXCEEDED);
        return error;
    }

    // Transform wall-clock timeout exceeded (T017).
    public static OdinTransformTypes.TransformError timeoutExceededError(int limitMs) {
        var error = new OdinTransformTypes.TransformError(
                "Transform timeout exceeded (limit " + limitMs + "ms)");
        error.setCode(TransformErrorCodes.T017_TRANSFORM_TIMEOUT_EXCEEDED);
        return error;
    }

    // Expression evaluation depth exceeded (T018).
    public static OdinTransformTypes.TransformError expressionDepthExceededError(int limit) {
        var error = new OdinTransformTypes.TransformError(
                "Expression evaluation depth exceeded (limit " + limit + ")");
        error.setCode(TransformErrorCodes.T018_EXPRESSION_DEPTH_EXCEEDED);
        return error;
    }

    // Lookup key not found in a table (T004).
    public static OdinTransformTypes.TransformError lookupKeyNotFoundError(String tableName, String key) {
        var error = new OdinTransformTypes.TransformError(
                "Lookup key '" + key + "' not found in table '" + tableName + "'");
        error.setCode(TransformErrorCodes.T004_LOOKUP_KEY_NOT_FOUND);
        return error;
    }

    public static OdinTransformTypes.TransformWarning lookupKeyNotFoundWarning(String tableName, String key) {
        return new OdinTransformTypes.TransformWarning(
                "Lookup key '" + key + "' not found in table '" + tableName + "'");
    }

    // Conditional branch (elif/else) with no preceding if.
    public static OdinTransformTypes.TransformError danglingBranchError(String directive, String segment) {
        var error = new OdinTransformTypes.TransformError(
                "'" + directive + "' segment has no preceding 'if'", segment);
        error.setCode(TransformErrorCodes.T012_DANGLING_BRANCH);
        return error;
    }

    // Unknown verb (T001).
    public static OdinTransformTypes.TransformError unknownVerbError(String verb) {
        var error = new OdinTransformTypes.TransformError("Unknown verb: " + verb);
        error.setCode(TransformErrorCodes.T001_UNKNOWN_VERB);
        return error;
    }

    // Lookup table not declared (T003) — distinct from a missing key (T004).
    public static OdinTransformTypes.TransformError lookupTableNotFoundError(String tableName) {
        var error = new OdinTransformTypes.TransformError("Lookup table not found: " + tableName);
        error.setCode(TransformErrorCodes.T003_LOOKUP_TABLE_NOT_FOUND);
        return error;
    }

    public static OdinTransformTypes.TransformWarning lookupTableNotFoundWarning(String tableName) {
        var warning = new OdinTransformTypes.TransformWarning("Lookup table not found: " + tableName);
        warning.setCode(TransformErrorCodes.T003_LOOKUP_TABLE_NOT_FOUND);
        return warning;
    }

    // Source path not resolvable (T005).
    public static OdinTransformTypes.TransformError sourcePathNotFoundError(String path, String field) {
        var error = new OdinTransformTypes.TransformError("Source path not found: " + path, field);
        error.setCode(TransformErrorCodes.T005_SOURCE_PATH_NOT_FOUND);
        return error;
    }

    public static OdinTransformTypes.TransformWarning sourcePathNotFoundWarning(String path, String field) {
        var warning = new OdinTransformTypes.TransformWarning("Source path not found: " + path);
        warning.setPath(field);
        warning.setCode(TransformErrorCodes.T005_SOURCE_PATH_NOT_FOUND);
        return warning;
    }

    // Required field present but null (legacy SOURCE_MISSING).
    public static OdinTransformTypes.TransformError sourceMissingError(String field) {
        var error = new OdinTransformTypes.TransformError(
                "Required field '" + field + "' is missing or null", field);
        error.setCode("SOURCE_MISSING");
        return error;
    }

    // Invalid or unsupported output format (T006).
    public static OdinTransformTypes.TransformError invalidOutputFormatError(String format) {
        var error = new OdinTransformTypes.TransformError(
                "Invalid or unsupported output format: " + format);
        error.setCode(TransformErrorCodes.T006_INVALID_OUTPUT_FORMAT);
        return error;
    }

    // Accumulator running sum no longer exactly representable (T008).
    public static OdinTransformTypes.TransformError accumulatorOverflowError(String accumulator, double value) {
        String shown = (value == Math.floor(value) && !Double.isInfinite(value))
                ? Long.toString((long) value) : Double.toString(value);
        var error = new OdinTransformTypes.TransformError(
                "Accumulator '" + accumulator + "' overflow with value " + shown);
        error.setCode(TransformErrorCodes.T008_ACCUMULATOR_OVERFLOW);
        return error;
    }

    // Loop source path does not resolve to an array (T009).
    public static OdinTransformTypes.TransformError loopSourceNotArrayError(String path, String segment) {
        var error = new OdinTransformTypes.TransformError(
                "Loop source path '" + path + "' does not resolve to an array", segment);
        error.setCode(TransformErrorCodes.T009_LOOP_SOURCE_NOT_ARRAY);
        return error;
    }

    public static OdinTransformTypes.TransformWarning loopSourceNotArrayWarning(String path, String segment) {
        var warning = new OdinTransformTypes.TransformWarning(
                "Loop source path '" + path + "' does not resolve to an array");
        warning.setPath(segment);
        warning.setCode(TransformErrorCodes.T009_LOOP_SOURCE_NOT_ARRAY);
        return warning;
    }

    // Invalid verb arguments (T002).
    public static OdinTransformTypes.TransformError invalidVerbArgsError(String verb, String detail) {
        var error = new OdinTransformTypes.TransformError(
                "Invalid arguments for '" + verb + "': " + detail);
        error.setCode(TransformErrorCodes.T002_INVALID_VERB_ARGS);
        return error;
    }

    // Field modifier not valid for the target format (T007).
    public static OdinTransformTypes.TransformWarning invalidModifierWarning(String modifier, String format) {
        var warning = new OdinTransformTypes.TransformWarning(
                "Modifier ':" + modifier + "' is not valid for format '" + format + "'");
        warning.setCode(TransformErrorCodes.T007_INVALID_MODIFIER);
        return warning;
    }

    // Fixed-width field position + length exceeds the line width (T010).
    public static OdinTransformTypes.TransformError positionOverflowError(String field, int pos, int len, int lineWidth) {
        var error = new OdinTransformTypes.TransformError(
                "Field '" + field + "' position " + pos + " + length " + len + " exceeds line width " + lineWidth, field);
        error.setCode(TransformErrorCodes.T010_POSITION_OVERFLOW);
        return error;
    }

    // Incompatible unit conversion (T011).
    public static OdinTransformTypes.TransformError incompatibleConversionError(String verb, String detail) {
        var error = new OdinTransformTypes.TransformError(
                "Incompatible conversion in '" + verb + "': " + detail);
        error.setCode(TransformErrorCodes.T011_INCOMPATIBLE_CONVERSION);
        return error;
    }

    // ── PatchError ──

    public static final class PatchError {
        private final String errorMessage;
        private final String path;

        public PatchError(String message, String path) {
            this.errorMessage = message;
            this.path = path;
        }

        public String getErrorMessage() { return errorMessage; }
        public String getPath() { return path; }

        @Override
        public String toString() {
            return "Patch error at '" + path + "': " + errorMessage;
        }
    }

    // ── PatchException ──

    public static class PatchException extends RuntimeException {
        private final String path;

        public PatchException(String message, String path) {
            super(message);
            this.path = path;
        }

        public String getPath() { return path; }
    }

    // ── OdinException (general) ──

    public static class OdinException extends RuntimeException {
        private final String code;

        public OdinException(String code, String message) {
            super(code);
            this.code = code;
        }

        public String getCode() { return code; }
    }
}
