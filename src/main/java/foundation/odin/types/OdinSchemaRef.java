package foundation.odin.types;

public record OdinSchemaRef(String url, int line) {
    public OdinSchemaRef(String url) { this(url, 0); }
}
