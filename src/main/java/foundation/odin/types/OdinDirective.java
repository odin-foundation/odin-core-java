package foundation.odin.types;

import java.util.Objects;

public final class OdinDirective {
    private final String name;
    private final DirectiveValue value;

    public OdinDirective(String name) {
        this(name, null);
    }

    public OdinDirective(String name, DirectiveValue value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return name; }
    public DirectiveValue getValue() { return value; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OdinDirective other)) return false;
        return Objects.equals(name, other.name) && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    // ── DirectiveValue ──

    public static abstract sealed class DirectiveValue
            permits StringDirectiveValue, NumberDirectiveValue {

        public static DirectiveValue fromString(String value) {
            return new StringDirectiveValue(value);
        }

        public static DirectiveValue fromNumber(double value) {
            return new NumberDirectiveValue(value);
        }

        public String asString() { return null; }
        public Double asNumber() { return null; }
    }

    public static final class StringDirectiveValue extends DirectiveValue {
        private final String value;

        public StringDirectiveValue(String value) { this.value = value; }
        public String getValue() { return value; }

        @Override public String asString() { return value; }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof StringDirectiveValue other)) return false;
            return Objects.equals(value, other.value);
        }

        @Override public int hashCode() { return Objects.hashCode(value); }
        @Override public String toString() { return value; }
    }

    public static final class NumberDirectiveValue extends DirectiveValue {
        private final double value;

        public NumberDirectiveValue(double value) { this.value = value; }
        public double getValue() { return value; }

        @Override public Double asNumber() { return value; }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NumberDirectiveValue other)) return false;
            return Double.compare(value, other.value) == 0;
        }

        @Override public int hashCode() { return Double.hashCode(value); }
        @Override public String toString() { return java.lang.String.valueOf(value); }
    }
}
