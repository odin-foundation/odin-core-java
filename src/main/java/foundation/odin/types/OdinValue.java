package foundation.odin.types;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract sealed class OdinValue
        permits OdinValue.OdinNull, OdinValue.OdinBoolean, OdinValue.OdinString,
                OdinValue.OdinInteger, OdinValue.OdinNumber, OdinValue.OdinCurrency,
                OdinValue.OdinPercent, OdinValue.OdinDate, OdinValue.OdinTimestamp,
                OdinValue.OdinTime, OdinValue.OdinDuration, OdinValue.OdinReference,
                OdinValue.OdinBinary, OdinValue.OdinVerb, OdinValue.OdinArray,
                OdinValue.OdinObject {

    private OdinModifiers modifiers;
    private List<OdinDirective> directives = Collections.emptyList();

    public abstract OdinValueType getType();

    public OdinModifiers getModifiers() { return modifiers; }
    void setModifiers(OdinModifiers modifiers) { this.modifiers = modifiers; }

    public List<OdinDirective> getDirectives() { return directives; }
    void setDirectives(List<OdinDirective> directives) { this.directives = directives; }

    // Modifier convenience
    public boolean isRequired() { return modifiers != null && modifiers.isRequired(); }
    public boolean isConfidential() { return modifiers != null && modifiers.isConfidential(); }
    public boolean isDeprecated() { return modifiers != null && modifiers.isDeprecated(); }

    // Type checks
    public boolean isNull() { return getType() == OdinValueType.Null; }
    public boolean isBoolean() { return getType() == OdinValueType.Boolean; }
    public boolean isString() { return getType() == OdinValueType.String; }
    public boolean isInteger() { return getType() == OdinValueType.Integer; }
    public boolean isNumber() { return getType() == OdinValueType.Number; }
    public boolean isCurrency() { return getType() == OdinValueType.Currency; }
    public boolean isPercent() { return getType() == OdinValueType.Percent; }
    public boolean isNumeric() {
        var t = getType();
        return t == OdinValueType.Integer || t == OdinValueType.Number ||
               t == OdinValueType.Currency || t == OdinValueType.Percent;
    }
    public boolean isTemporal() {
        var t = getType();
        return t == OdinValueType.Date || t == OdinValueType.Timestamp ||
               t == OdinValueType.Time || t == OdinValueType.Duration;
    }
    public boolean isDate() { return getType() == OdinValueType.Date; }
    public boolean isTimestamp() { return getType() == OdinValueType.Timestamp; }
    public boolean isTime() { return getType() == OdinValueType.Time; }
    public boolean isDuration() { return getType() == OdinValueType.Duration; }
    public boolean isReference() { return getType() == OdinValueType.Reference; }
    public boolean isBinary() { return getType() == OdinValueType.Binary; }
    public boolean isVerb() { return getType() == OdinValueType.Verb; }
    public boolean isArray() { return getType() == OdinValueType.Array; }
    public boolean isObject() { return getType() == OdinValueType.Object; }

    // Typed accessors (override in subclasses)
    public Boolean asBool() { return null; }
    public java.lang.String asString() { return null; }
    public Long asInt64() { return null; }
    public Double asDouble() { return null; }
    public java.math.BigDecimal asDecimal() { return null; }
    public java.lang.String asReference() { return null; }
    public List<OdinArrayItem> asArray() { return null; }

    public abstract OdinValue withModifiers(OdinModifiers modifiers);
    public abstract OdinValue withDirectives(List<OdinDirective> directives);

    // Copy modifiers/directives to a new value
    protected void copyMetaTo(OdinValue target) {
        target.modifiers = this.modifiers;
        target.directives = this.directives;
    }

    // ── 16 Subclasses ──

    public static final class OdinNull extends OdinValue {
        @Override public OdinValueType getType() { return OdinValueType.Null; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinNull(); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinNull(); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "~"; }
    }

    public static final class OdinBoolean extends OdinValue {
        private final boolean value;
        public OdinBoolean(boolean value) { this.value = value; }
        public boolean getValue() { return value; }
        @Override public OdinValueType getType() { return OdinValueType.Boolean; }
        @Override public Boolean asBool() { return value; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinBoolean(value); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinBoolean(value); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return value ? "true" : "false"; }
    }

    public static final class OdinString extends OdinValue {
        private final java.lang.String value;
        public OdinString(java.lang.String value) { this.value = value; }
        public java.lang.String getValue() { return value; }
        @Override public OdinValueType getType() { return OdinValueType.String; }
        @Override public java.lang.String asString() { return value; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinString(value); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinString(value); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "\"" + value + "\""; }
    }

    public static final class OdinInteger extends OdinValue {
        private final long value;
        private final java.lang.String raw;
        public OdinInteger(long value) { this(value, null); }
        public OdinInteger(long value, java.lang.String raw) { this.value = value; this.raw = raw; }
        public long getValue() { return value; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Integer; }
        @Override public Long asInt64() { return value; }
        @Override public Double asDouble() { return (double) value; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinInteger(value, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinInteger(value, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return raw != null ? "##" + raw : "##" + value; }
    }

    public static final class OdinNumber extends OdinValue {
        private final double value;
        private final Byte decimalPlaces;
        private final java.lang.String raw;
        public OdinNumber(double value) { this(value, null, null); }
        public OdinNumber(double value, Byte decimalPlaces, java.lang.String raw) {
            this.value = value; this.decimalPlaces = decimalPlaces; this.raw = raw;
        }
        public double getValue() { return value; }
        public Byte getDecimalPlaces() { return decimalPlaces; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Number; }
        @Override public Double asDouble() { return value; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinNumber(value, decimalPlaces, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinNumber(value, decimalPlaces, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return raw != null ? "#" + raw : "#" + value; }
    }

    public static final class OdinCurrency extends OdinValue {
        private final double value;
        private final byte decimalPlaces;
        private final java.lang.String currencyCode;
        private final java.lang.String raw;
        public OdinCurrency(double value) { this(value, (byte) 2, null, null); }
        public OdinCurrency(double value, byte decimalPlaces, java.lang.String currencyCode, java.lang.String raw) {
            this.value = value; this.decimalPlaces = decimalPlaces;
            this.currencyCode = currencyCode; this.raw = raw;
        }
        public double getValue() { return value; }
        public byte getDecimalPlaces() { return decimalPlaces; }
        public java.lang.String getCurrencyCode() { return currencyCode; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Currency; }
        @Override public Double asDouble() { return value; }
        @Override public java.math.BigDecimal asDecimal() { return java.math.BigDecimal.valueOf(value); }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinCurrency(value, decimalPlaces, currencyCode, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinCurrency(value, decimalPlaces, currencyCode, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() {
            var val = raw != null ? raw : java.lang.String.valueOf(value);
            return currencyCode != null ? "#$" + val + ":" + currencyCode : "#$" + val;
        }
    }

    public static final class OdinPercent extends OdinValue {
        private final double value;
        private final java.lang.String raw;
        public OdinPercent(double value) { this(value, null); }
        public OdinPercent(double value, java.lang.String raw) { this.value = value; this.raw = raw; }
        public double getValue() { return value; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Percent; }
        @Override public Double asDouble() { return value; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinPercent(value, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinPercent(value, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return raw != null ? "#%" + raw : "#%" + value; }
    }

    public static final class OdinDate extends OdinValue {
        private final int year;
        private final byte month;
        private final byte day;
        private final java.lang.String raw;
        public OdinDate(int year, byte month, byte day) {
            this(year, month, day, java.lang.String.format("%04d-%02d-%02d", year, (int) month, (int) day));
        }
        public OdinDate(int year, byte month, byte day, java.lang.String raw) {
            this.year = year; this.month = month; this.day = day; this.raw = raw;
        }
        public int getYear() { return year; }
        public byte getMonth() { return month; }
        public byte getDay() { return day; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Date; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinDate(year, month, day, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinDate(year, month, day, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return raw; }
    }

    public static final class OdinTimestamp extends OdinValue {
        private final long epochMs;
        private final java.lang.String raw;
        public OdinTimestamp(long epochMs, java.lang.String raw) { this.epochMs = epochMs; this.raw = raw; }
        public long getEpochMs() { return epochMs; }
        public java.lang.String getRaw() { return raw; }
        @Override public OdinValueType getType() { return OdinValueType.Timestamp; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinTimestamp(epochMs, raw); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinTimestamp(epochMs, raw); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return raw; }
    }

    public static final class OdinTime extends OdinValue {
        private final java.lang.String value;
        public OdinTime(java.lang.String value) { this.value = value; }
        public java.lang.String getValue() { return value; }
        @Override public OdinValueType getType() { return OdinValueType.Time; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinTime(value); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinTime(value); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return value; }
    }

    public static final class OdinDuration extends OdinValue {
        private final java.lang.String value;
        public OdinDuration(java.lang.String value) { this.value = value; }
        public java.lang.String getValue() { return value; }
        @Override public OdinValueType getType() { return OdinValueType.Duration; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinDuration(value); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinDuration(value); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return value; }
    }

    public static final class OdinReference extends OdinValue {
        private final java.lang.String path;
        public OdinReference(java.lang.String path) { this.path = path; }
        public java.lang.String getPath() { return path; }
        @Override public OdinValueType getType() { return OdinValueType.Reference; }
        @Override public java.lang.String asReference() { return path; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinReference(path); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinReference(path); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "@" + path; }
    }

    public static final class OdinBinary extends OdinValue {
        private final byte[] data;
        private final java.lang.String algorithm;
        public OdinBinary(byte[] data) { this(data, null); }
        public OdinBinary(byte[] data, java.lang.String algorithm) { this.data = data; this.algorithm = algorithm; }
        public byte[] getData() { return data; }
        public java.lang.String getAlgorithm() { return algorithm; }
        @Override public OdinValueType getType() { return OdinValueType.Binary; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinBinary(data, algorithm); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinBinary(data, algorithm); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return algorithm != null ? "^" + algorithm + ":<data>" : "^<data>"; }
    }

    public static final class OdinVerb extends OdinValue {
        private final java.lang.String name;
        private final boolean custom;
        private final List<OdinValue> args;
        public OdinVerb(java.lang.String name, List<OdinValue> args) { this(name, args, false); }
        public OdinVerb(java.lang.String name, List<OdinValue> args, boolean custom) {
            this.name = name; this.args = args; this.custom = custom;
        }
        public java.lang.String getName() { return name; }
        public boolean isCustom() { return custom; }
        public List<OdinValue> getArgs() { return args; }
        @Override public OdinValueType getType() { return OdinValueType.Verb; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinVerb(name, args, custom); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinVerb(name, args, custom); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "%" + name; }
    }

    public static final class OdinArray extends OdinValue {
        private final List<OdinArrayItem> items;
        public OdinArray(List<OdinArrayItem> items) { this.items = items; }
        public List<OdinArrayItem> getItems() { return items; }
        @Override public OdinValueType getType() { return OdinValueType.Array; }
        @Override public List<OdinArrayItem> asArray() { return items; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinArray(items); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinArray(items); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "[" + items.size() + " items]"; }
    }

    public static final class OdinObject extends OdinValue {
        private final List<Map.Entry<java.lang.String, OdinValue>> fields;
        public OdinObject(List<Map.Entry<java.lang.String, OdinValue>> fields) { this.fields = fields; }
        public List<Map.Entry<java.lang.String, OdinValue>> getFields() { return fields; }
        @Override public OdinValueType getType() { return OdinValueType.Object; }
        @Override public OdinValue withModifiers(OdinModifiers m) { var v = new OdinObject(fields); copyMetaTo(v); v.setModifiers(m); return v; }
        @Override public OdinValue withDirectives(List<OdinDirective> d) { var v = new OdinObject(fields); copyMetaTo(v); v.setDirectives(d); return v; }
        @Override public java.lang.String toString() { return "{" + fields.size() + " fields}"; }
    }

    // ── Factory (OdinValues) ──

    public static OdinNull ofNull() { return new OdinNull(); }
    public static OdinBoolean ofBoolean(boolean value) { return new OdinBoolean(value); }
    public static OdinString ofString(java.lang.String value) { return new OdinString(value); }
    public static OdinInteger ofInteger(long value) { return new OdinInteger(value); }
    public static OdinInteger ofInteger(long value, java.lang.String raw) { return new OdinInteger(value, raw); }
    public static OdinNumber ofNumber(double value) { return new OdinNumber(value); }
    public static OdinNumber ofNumber(double value, byte decimalPlaces) { return new OdinNumber(value, decimalPlaces, null); }
    public static OdinCurrency ofCurrency(double value) { return new OdinCurrency(value); }
    public static OdinCurrency ofCurrency(double value, byte decimalPlaces) { return new OdinCurrency(value, decimalPlaces, null, null); }
    public static OdinCurrency ofCurrency(double value, byte decimalPlaces, java.lang.String code) { return new OdinCurrency(value, decimalPlaces, code, null); }
    public static OdinPercent ofPercent(double value) { return new OdinPercent(value); }
    public static OdinDate ofDate(int year, byte month, byte day) { return new OdinDate(year, month, day); }
    public static OdinDate ofDate(int year, byte month, byte day, java.lang.String raw) { return new OdinDate(year, month, day, raw); }

    public static OdinDate dateFromStr(java.lang.String raw) {
        var parts = raw.split("-");
        if (parts.length != 3) return null;
        try {
            int year = Integer.parseInt(parts[0]);
            byte month = Byte.parseByte(parts[1]);
            byte day = Byte.parseByte(parts[2]);
            return new OdinDate(year, month, day, raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static OdinTimestamp ofTimestamp(long epochMs, java.lang.String raw) { return new OdinTimestamp(epochMs, raw); }
    public static OdinTime ofTime(java.lang.String value) { return new OdinTime(value); }
    public static OdinDuration ofDuration(java.lang.String value) { return new OdinDuration(value); }
    public static OdinReference ofReference(java.lang.String path) { return new OdinReference(path); }
    public static OdinBinary ofBinary(byte[] data) { return new OdinBinary(data); }
    public static OdinBinary ofBinary(byte[] data, java.lang.String algorithm) { return new OdinBinary(data, algorithm); }
    public static OdinVerb ofVerb(java.lang.String name, List<OdinValue> args) { return new OdinVerb(name, args); }
    public static OdinVerb ofCustomVerb(java.lang.String name, List<OdinValue> args) { return new OdinVerb(name, args, true); }
    public static OdinArray ofArray(List<OdinArrayItem> items) { return new OdinArray(items); }
    public static OdinObject ofObject(List<Map.Entry<java.lang.String, OdinValue>> fields) { return new OdinObject(fields); }
}
