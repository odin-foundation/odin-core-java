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

    // Execution guard limits, overridable via ODIN_ environment variables. A value
    // of 0 means unbounded; expression depth keeps its standing default.
    public static int MAX_EXPRESSION_DEPTH = envInt("MAX_EXPRESSION_DEPTH", 32);
    public static int MAX_TRANSFORM_FUEL = envInt("MAX_TRANSFORM_FUEL", 0);
    public static int TRANSFORM_TIMEOUT_MS = envInt("TRANSFORM_TIMEOUT_MS", 0);

    private static int envInt(String name, int fallback) {
        var raw = System.getenv("ODIN_" + name);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
