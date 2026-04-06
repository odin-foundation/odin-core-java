package foundation.odin.types;

public final class OdinOptions {
    private OdinOptions() {}

    // ── ParseOptions ──

    public static final class ParseOptions {
        private final int maxDepth;
        private final int maxDocumentSize;
        private final int maxArrayIndex;
        private final boolean preserveComments;
        private final boolean allowEmpty;
        private final boolean allowDuplicates;

        public static final ParseOptions DEFAULT = new ParseOptions(64, 10 * 1024 * 1024, 10_000, false, false, false);

        public ParseOptions(int maxDepth, int maxDocumentSize, int maxArrayIndex,
                            boolean preserveComments, boolean allowEmpty, boolean allowDuplicates) {
            this.maxDepth = maxDepth;
            this.maxDocumentSize = maxDocumentSize;
            this.maxArrayIndex = maxArrayIndex;
            this.preserveComments = preserveComments;
            this.allowEmpty = allowEmpty;
            this.allowDuplicates = allowDuplicates;
        }

        public int getMaxDepth() { return maxDepth; }
        public int getMaxDocumentSize() { return maxDocumentSize; }
        public int getMaxArrayIndex() { return maxArrayIndex; }
        public boolean isPreserveComments() { return preserveComments; }
        public boolean isAllowEmpty() { return allowEmpty; }
        public boolean isAllowDuplicates() { return allowDuplicates; }

        public ParseOptions withMaxDepth(int maxDepth) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }

        public ParseOptions withMaxDocumentSize(int maxDocumentSize) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }

        public ParseOptions withMaxArrayIndex(int maxArrayIndex) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }

        public ParseOptions withPreserveComments(boolean preserveComments) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }

        public ParseOptions withAllowEmpty(boolean allowEmpty) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }

        public ParseOptions withAllowDuplicates(boolean allowDuplicates) {
            return new ParseOptions(maxDepth, maxDocumentSize, maxArrayIndex, preserveComments, allowEmpty, allowDuplicates);
        }
    }

    // ── StringifyOptions ──

    public static final class StringifyOptions {
        private final boolean includeMetadata;
        private final boolean preserveOrder;
        private final String indent;

        public static final StringifyOptions DEFAULT = new StringifyOptions(true, true, "");

        public StringifyOptions(boolean includeMetadata, boolean preserveOrder, String indent) {
            this.includeMetadata = includeMetadata;
            this.preserveOrder = preserveOrder;
            this.indent = indent;
        }

        public boolean isIncludeMetadata() { return includeMetadata; }
        public boolean isPreserveOrder() { return preserveOrder; }
        public String getIndent() { return indent; }

        public StringifyOptions withIncludeMetadata(boolean includeMetadata) {
            return new StringifyOptions(includeMetadata, preserveOrder, indent);
        }

        public StringifyOptions withPreserveOrder(boolean preserveOrder) {
            return new StringifyOptions(includeMetadata, preserveOrder, indent);
        }

        public StringifyOptions withIndent(String indent) {
            return new StringifyOptions(includeMetadata, preserveOrder, indent);
        }
    }

    // ── ValidateOptions ──

    public static final class ValidateOptions {
        private final boolean strict;
        private final boolean validateReferences;
        private final boolean failFast;

        public static final ValidateOptions DEFAULT = new ValidateOptions(false, true, false);

        public ValidateOptions() {
            this(false, false, false);
        }

        public ValidateOptions(boolean strict, boolean validateReferences, boolean failFast) {
            this.strict = strict;
            this.validateReferences = validateReferences;
            this.failFast = failFast;
        }

        public boolean isStrict() { return strict; }
        public boolean isValidateReferences() { return validateReferences; }
        public boolean isFailFast() { return failFast; }

        public ValidateOptions withStrict(boolean strict) {
            return new ValidateOptions(strict, validateReferences, failFast);
        }

        public ValidateOptions withValidateReferences(boolean validateReferences) {
            return new ValidateOptions(strict, validateReferences, failFast);
        }

        public ValidateOptions withFailFast(boolean failFast) {
            return new ValidateOptions(strict, validateReferences, failFast);
        }

        public ValidateOptions setStrict(boolean strict) { return withStrict(strict); }
        public ValidateOptions setValidateReferences(boolean v) { return withValidateReferences(v); }
        public ValidateOptions setFailFast(boolean v) { return withFailFast(v); }
    }
}
