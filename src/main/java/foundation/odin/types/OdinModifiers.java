package foundation.odin.types;

import java.util.Objects;

public final class OdinModifiers {

    public static final OdinModifiers EMPTY = new OdinModifiers(false, false, false, false, null, false);

    private final boolean required;
    private final boolean confidential;
    private final boolean deprecated;
    private final boolean attr;
    private final String ns;
    private final boolean cdata;

    public OdinModifiers(boolean required, boolean confidential, boolean deprecated, boolean attr) {
        this(required, confidential, deprecated, attr, null, false);
    }

    public OdinModifiers(boolean required, boolean confidential, boolean deprecated, boolean attr, String ns) {
        this(required, confidential, deprecated, attr, ns, false);
    }

    public OdinModifiers(boolean required, boolean confidential, boolean deprecated, boolean attr, String ns, boolean cdata) {
        this.required = required;
        this.confidential = confidential;
        this.deprecated = deprecated;
        this.attr = attr;
        this.ns = ns;
        this.cdata = cdata;
    }

    public boolean isRequired() { return required; }
    public boolean isConfidential() { return confidential; }
    public boolean isDeprecated() { return deprecated; }
    public boolean isAttr() { return attr; }
    public String getNs() { return ns; }
    public boolean isCdata() { return cdata; }

    public boolean isEmpty() { return !required && !confidential && !deprecated && !attr && ns == null && !cdata; }
    public boolean hasAny() { return !isEmpty(); }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OdinModifiers other)) return false;
        return required == other.required &&
               confidential == other.confidential &&
               deprecated == other.deprecated &&
               attr == other.attr &&
               cdata == other.cdata &&
               Objects.equals(ns, other.ns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(required, confidential, deprecated, attr, ns, cdata);
    }

    @Override
    public java.lang.String toString() {
        if (isEmpty()) return "OdinModifiers[]";
        var sb = new StringBuilder("OdinModifiers[");
        boolean first = true;
        if (required) { sb.append("required"); first = false; }
        if (confidential) { if (!first) sb.append(","); sb.append("confidential"); first = false; }
        if (deprecated) { if (!first) sb.append(","); sb.append("deprecated"); first = false; }
        if (attr) { if (!first) sb.append(","); sb.append("attr"); first = false; }
        if (cdata) { if (!first) sb.append(","); sb.append("cdata"); first = false; }
        if (ns != null) { if (!first) sb.append(","); sb.append("ns=").append(ns); }
        sb.append("]");
        return sb.toString();
    }
}
