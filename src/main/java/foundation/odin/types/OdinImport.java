package foundation.odin.types;

public record OdinImport(String path, String alias, int line) {
    public OdinImport(String path) { this(path, null, 0); }
}
