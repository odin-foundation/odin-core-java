package foundation.odin.utils;

import java.time.Duration;

public final class SecurityLimits {
    private SecurityLimits() {}

    public static final int MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
    public static final int MAX_NESTING_DEPTH = 64;
    public static final int MAX_ARRAY_INDEX = 10_000;
    public static final int MAX_RECORDS = 100_000;
    public static final int MAX_ASSIGNMENTS = 100_000;
    public static final int MAX_REGEX_PATTERN_LENGTH = 500;
    public static final Duration REGEX_TIMEOUT = Duration.ofSeconds(1);
}
