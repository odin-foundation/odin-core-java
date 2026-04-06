package foundation.odin.types;

import java.util.Objects;

public final class OdinModifiers {

    public static final OdinModifiers EMPTY = new OdinModifiers(false, false, false, false);

    private final boolean required;
    private final boolean confidential;
    private final boolean deprecated;
    private final boolean attr;

    public OdinModifiers(boolean required, boolean confidential, boolean deprecated, boolean attr) {
        this.required = required;
        this.confidential = confidential;
        this.deprecated = deprecated;
        this.attr = attr;
    }

    public boolean isRequired() { return required; }
    public boolean isConfidential() { return confidential; }
    public boolean isDeprecated() { return deprecated; }
    public boolean isAttr() { return attr; }

    public boolean isEmpty() { return !required && !confidential && !deprecated && !attr; }
    public boolean hasAny() { return !isEmpty(); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OdinModifiers other)) return false;
        return required == other.required &&
               confidential == other.confidential &&
               deprecated == other.deprecated &&
               attr == other.attr;
    }

    @Override
    public int hashCode() {
        return Objects.hash(required, confidential, deprecated, attr);
    }

    @Override
    public java.lang.String toString() {
        if (isEmpty()) return "OdinModifiers[]";
        var sb = new StringBuilder("OdinModifiers[");
        boolean first = true;
        if (required) { sb.append("required"); first = false; }
        if (confidential) { if (!first) sb.append(","); sb.append("confidential"); first = false; }
        if (deprecated) { if (!first) sb.append(","); sb.append("deprecated"); first = false; }
        if (attr) { if (!first) sb.append(","); sb.append("attr"); }
        sb.append("]");
        return sb.toString();
    }
}
