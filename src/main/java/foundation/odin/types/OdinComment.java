package foundation.odin.types;

public record OdinComment(String text, String associatedPath, int line) {
    public OdinComment(String text) { this(text, null, 0); }
}
