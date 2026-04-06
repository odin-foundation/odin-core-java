package foundation.odin.types;

public record OdinConditional(String condition, int line) {
    public OdinConditional(String condition) { this(condition, 0); }
}
