package foundation.odin.types;

public record FlattenOptions(boolean includeMetadata, boolean includeNulls, boolean sort) {
    public FlattenOptions() { this(false, false, true); }
}
