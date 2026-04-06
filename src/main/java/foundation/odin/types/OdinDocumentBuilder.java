package foundation.odin.types;

import java.util.ArrayList;
import java.util.List;

public final class OdinDocumentBuilder {
    private final OrderedMap<String, OdinValue> metadata = new OrderedMap<>();
    private final OrderedMap<String, OdinValue> assignments = new OrderedMap<>();
    private final OrderedMap<String, OdinModifiers> modifiers = new OrderedMap<>();
    private final List<OdinImport> imports = new ArrayList<>();
    private final List<OdinSchemaRef> schemas = new ArrayList<>();
    private final List<OdinConditional> conditionals = new ArrayList<>();
    private final List<OdinComment> comments = new ArrayList<>();

    public OdinDocumentBuilder metadata(String key, OdinValue value) {
        metadata.set(key, value);
        return this;
    }

    public OdinDocumentBuilder metadata(String key, String value) {
        metadata.set(key, OdinValue.ofString(value));
        return this;
    }

    public OdinDocumentBuilder set(String path, OdinValue value) {
        assignments.set(path, value);
        return this;
    }

    public OdinDocumentBuilder set(String path, String value) {
        assignments.set(path, OdinValue.ofString(value));
        return this;
    }

    public OdinDocumentBuilder set(String path, long value) {
        assignments.set(path, OdinValue.ofInteger(value));
        return this;
    }

    public OdinDocumentBuilder set(String path, double value) {
        assignments.set(path, OdinValue.ofNumber(value));
        return this;
    }

    public OdinDocumentBuilder set(String path, boolean value) {
        assignments.set(path, OdinValue.ofBoolean(value));
        return this;
    }

    public OdinDocumentBuilder setString(String path, String value) {
        assignments.set(path, OdinValue.ofString(value));
        return this;
    }

    public OdinDocumentBuilder setInteger(String path, long value) {
        assignments.set(path, OdinValue.ofInteger(value));
        return this;
    }

    public OdinDocumentBuilder setNumber(String path, double value) {
        assignments.set(path, OdinValue.ofNumber(value));
        return this;
    }

    public OdinDocumentBuilder setBoolean(String path, boolean value) {
        assignments.set(path, OdinValue.ofBoolean(value));
        return this;
    }

    public OdinDocumentBuilder setNull(String path) {
        assignments.set(path, OdinValue.ofNull());
        return this;
    }

    public OdinDocumentBuilder setCurrency(String path, double value) {
        assignments.set(path, OdinValue.ofCurrency(value));
        return this;
    }

    public OdinDocumentBuilder setCurrency(String path, double value, byte decimalPlaces) {
        assignments.set(path, OdinValue.ofCurrency(value, decimalPlaces));
        return this;
    }

    public OdinDocumentBuilder setCurrency(String path, double value, byte decimalPlaces, String currencyCode) {
        assignments.set(path, OdinValue.ofCurrency(value, decimalPlaces, currencyCode));
        return this;
    }

    public OdinDocumentBuilder withModifiers(String path, OdinModifiers modifiers) {
        this.modifiers.set(path, modifiers);
        return this;
    }

    public OdinDocumentBuilder addImport(String importPath) {
        imports.add(new OdinImport(importPath));
        return this;
    }

    public OdinDocumentBuilder addImport(String importPath, String alias) {
        imports.add(new OdinImport(importPath, alias, 0));
        return this;
    }

    public OdinDocumentBuilder addSchema(String url) {
        schemas.add(new OdinSchemaRef(url));
        return this;
    }

    public OdinDocumentBuilder addComment(String text) {
        comments.add(new OdinComment(text));
        return this;
    }

    public OdinDocumentBuilder addComment(String text, String associatedPath) {
        comments.add(new OdinComment(text, associatedPath, 0));
        return this;
    }

    public OdinDocument build() {
        return new OdinDocument(metadata, assignments, modifiers,
                imports, schemas, conditionals, comments);
    }
}
