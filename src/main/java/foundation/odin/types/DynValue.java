package foundation.odin.types;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.*;

public final class DynValue {

    public enum Type {
        Null, Bool, Integer, Float, FloatRaw, Currency, CurrencyRaw,
        Percent, Reference, Binary, Date, Timestamp, Time, Duration,
        String, Array, Object
    }

    private final Type type;
    private final boolean boolValue;
    private final long intValue;
    private final double floatValue;
    private final byte decimalPlaces;
    private final java.lang.String stringValue;
    private final java.lang.String currencyCode;
    private final List<DynValue> arrayValue;
    private final List<Map.Entry<java.lang.String, DynValue>> objectValue;

    private DynValue(Type type, boolean boolValue, long intValue, double floatValue,
                     byte decimalPlaces, java.lang.String stringValue, java.lang.String currencyCode,
                     List<DynValue> arrayValue, List<Map.Entry<java.lang.String, DynValue>> objectValue) {
        this.type = type;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.floatValue = floatValue;
        this.decimalPlaces = decimalPlaces;
        this.stringValue = stringValue;
        this.currencyCode = currencyCode;
        this.arrayValue = arrayValue;
        this.objectValue = objectValue;
    }

    public Type getType() { return type; }
    public boolean isNull() { return type == Type.Null; }

    // ── Factory methods ──

    public static DynValue ofNull() { return new DynValue(Type.Null, false, 0, 0, (byte) 0, null, null, null, null); }
    public static DynValue ofBool(boolean v) { return new DynValue(Type.Bool, v, 0, 0, (byte) 0, null, null, null, null); }
    public static DynValue ofInteger(long v) { return new DynValue(Type.Integer, false, v, 0, (byte) 0, null, null, null, null); }
    public static DynValue ofFloat(double v) { return new DynValue(Type.Float, false, 0, v, (byte) 0, null, null, null, null); }
    public static DynValue ofFloatRaw(java.lang.String raw) { return new DynValue(Type.FloatRaw, false, 0, 0, (byte) 0, raw, null, null, null); }

    public static DynValue ofCurrency(double v, byte dp, java.lang.String code) {
        return new DynValue(Type.Currency, false, 0, v, dp, null, code, null, null);
    }
    public static DynValue ofCurrency(double v) { return ofCurrency(v, (byte) 2, null); }

    public static DynValue ofCurrencyRaw(java.lang.String raw, byte dp, java.lang.String code) {
        return new DynValue(Type.CurrencyRaw, false, 0, 0, dp, raw, code, null, null);
    }
    public static DynValue ofCurrencyRaw(java.lang.String raw) { return ofCurrencyRaw(raw, (byte) 2, null); }

    public static DynValue ofPercent(double v) { return new DynValue(Type.Percent, false, 0, v, (byte) 0, null, null, null, null); }
    public static DynValue ofReference(java.lang.String path) { return new DynValue(Type.Reference, false, 0, 0, (byte) 0, path, null, null, null); }
    public static DynValue ofBinary(java.lang.String base64) { return new DynValue(Type.Binary, false, 0, 0, (byte) 0, base64, null, null, null); }
    public static DynValue ofDate(java.lang.String date) { return new DynValue(Type.Date, false, 0, 0, (byte) 0, date, null, null, null); }
    public static DynValue ofTimestamp(java.lang.String ts) { return new DynValue(Type.Timestamp, false, 0, 0, (byte) 0, ts, null, null, null); }
    public static DynValue ofTime(java.lang.String time) { return new DynValue(Type.Time, false, 0, 0, (byte) 0, time, null, null, null); }
    public static DynValue ofDuration(java.lang.String dur) { return new DynValue(Type.Duration, false, 0, 0, (byte) 0, dur, null, null, null); }
    public static DynValue ofString(java.lang.String v) { return new DynValue(Type.String, false, 0, 0, (byte) 0, v, null, null, null); }
    public static DynValue ofArray(List<DynValue> items) { return new DynValue(Type.Array, false, 0, 0, (byte) 0, null, null, items, null); }
    public static DynValue ofObject(List<Map.Entry<java.lang.String, DynValue>> entries) { return new DynValue(Type.Object, false, 0, 0, (byte) 0, null, null, null, entries); }

    // ── Typed accessors ──

    public java.lang.String asString() {
        return switch (type) {
            case String, Reference, Binary, Date, Timestamp, Time, Duration, FloatRaw, CurrencyRaw -> stringValue;
            default -> null;
        };
    }

    public Long asInt64() {
        return type == Type.Integer ? intValue : null;
    }

    public Double asDouble() {
        return switch (type) {
            case Float, Currency, Percent -> floatValue;
            case Integer -> (double) intValue;
            case FloatRaw, CurrencyRaw -> {
                if (stringValue != null) {
                    try { yield Double.parseDouble(stringValue); }
                    catch (NumberFormatException e) { yield null; }
                }
                yield null;
            }
            default -> null;
        };
    }

    public Boolean asBool() {
        return type == Type.Bool ? boolValue : null;
    }

    public java.lang.String getCurrencyCode() {
        return (type == Type.Currency || type == Type.CurrencyRaw) ? currencyCode : null;
    }

    public byte getDecimalPlaces() {
        return (type == Type.Currency || type == Type.CurrencyRaw) ? decimalPlaces : 2;
    }

    public List<DynValue> asArray() {
        return type == Type.Array ? arrayValue : null;
    }

    public List<Map.Entry<java.lang.String, DynValue>> asObject() {
        return type == Type.Object ? objectValue : null;
    }

    public DynValue get(java.lang.String key) {
        if (objectValue == null) return null;
        for (var entry : objectValue) {
            if (entry.getKey().equals(key)) return entry.getValue();
        }
        return null;
    }

    public DynValue getIndex(int index) {
        if (arrayValue == null || index < 0 || index >= arrayValue.size()) return null;
        return arrayValue.get(index);
    }

    // ── Extract methods ──

    public List<DynValue> extractArray() {
        if (type == Type.Array) return arrayValue != null ? new ArrayList<>(arrayValue) : null;
        if (type == Type.String && stringValue != null) {
            var trimmed = stringValue.trim();
            if (trimmed.length() >= 2 && trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']')
                return parseArrayString(trimmed);
        }
        return null;
    }

    public List<Map.Entry<java.lang.String, DynValue>> extractObject() {
        if (type == Type.Object) return objectValue != null ? new ArrayList<>(objectValue) : null;
        if (type == Type.String && stringValue != null) {
            var trimmed = stringValue.trim();
            if (trimmed.length() >= 2 && trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}')
                return parseObjectString(trimmed);
        }
        return null;
    }

    // ── Gson integration ──

    public static DynValue fromJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return ofNull();
        if (element.isJsonPrimitive()) {
            var prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) return ofBool(prim.getAsBoolean());
            if (prim.isNumber()) {
                var raw = prim.getAsString();
                if (!raw.contains(".") && !raw.contains("e") && !raw.contains("E")) {
                    try { return ofInteger(prim.getAsLong()); }
                    catch (NumberFormatException ignored) {}
                }
                try { return ofFloat(prim.getAsDouble()); }
                catch (NumberFormatException ignored) {}
                return ofFloatRaw(raw);
            }
            return ofString(prim.getAsString());
        }
        if (element.isJsonArray()) {
            var arr = element.getAsJsonArray();
            var items = new ArrayList<DynValue>(arr.size());
            for (var item : arr) items.add(fromJsonElement(item));
            return ofArray(items);
        }
        if (element.isJsonObject()) {
            var obj = element.getAsJsonObject();
            var entries = new ArrayList<Map.Entry<java.lang.String, DynValue>>(obj.size());
            for (var e : obj.entrySet()) {
                entries.add(Map.entry(e.getKey(), fromJsonElement(e.getValue())));
            }
            return ofObject(entries);
        }
        return ofNull();
    }

    public JsonElement toJsonElement() {
        return switch (type) {
            case Null -> JsonNull.INSTANCE;
            case Bool -> new JsonPrimitive(boolValue);
            case Integer -> new JsonPrimitive(intValue);
            case Float, Currency, Percent -> new JsonPrimitive(floatValue);
            case FloatRaw, CurrencyRaw -> {
                if (stringValue != null) {
                    try { yield new JsonPrimitive(Double.parseDouble(stringValue)); }
                    catch (NumberFormatException e) { yield new JsonPrimitive(stringValue); }
                }
                yield JsonNull.INSTANCE;
            }
            case String, Reference, Binary, Date, Timestamp, Time, Duration ->
                    stringValue != null ? new JsonPrimitive(stringValue) : JsonNull.INSTANCE;
            case Array -> {
                var arr = new JsonArray();
                if (arrayValue != null) for (var item : arrayValue) arr.add(item.toJsonElement());
                yield arr;
            }
            case Object -> {
                var obj = new JsonObject();
                if (objectValue != null) for (var entry : objectValue) obj.add(entry.getKey(), entry.getValue().toJsonElement());
                yield obj;
            }
        };
    }

    // ── Equality ──

    @Override
    public boolean equals(java.lang.Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DynValue other)) return false;
        if (type != other.type) return false;
        return switch (type) {
            case Null -> true;
            case Bool -> boolValue == other.boolValue;
            case Integer -> intValue == other.intValue;
            case Float, Percent -> Double.compare(floatValue, other.floatValue) == 0;
            case Currency -> Double.compare(floatValue, other.floatValue) == 0 &&
                    decimalPlaces == other.decimalPlaces &&
                    Objects.equals(currencyCode, other.currencyCode);
            case FloatRaw, String, Reference, Binary, Date, Timestamp, Time, Duration ->
                    Objects.equals(stringValue, other.stringValue);
            case CurrencyRaw -> Objects.equals(stringValue, other.stringValue) &&
                    decimalPlaces == other.decimalPlaces &&
                    Objects.equals(currencyCode, other.currencyCode);
            case Array -> listEquals(arrayValue, other.arrayValue);
            case Object -> objectEquals(objectValue, other.objectValue);
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case Null -> type.hashCode();
            case Bool -> Objects.hash(type, boolValue);
            case Integer -> Objects.hash(type, intValue);
            case Float, Percent -> Objects.hash(type, floatValue);
            case Currency -> Objects.hash(type, floatValue, decimalPlaces, currencyCode);
            case CurrencyRaw -> Objects.hash(type, stringValue, decimalPlaces, currencyCode);
            default -> Objects.hash(type, stringValue);
        };
    }

    @Override
    public java.lang.String toString() {
        return switch (type) {
            case Null -> "null";
            case Bool -> boolValue ? "true" : "false";
            case Integer -> Long.toString(intValue);
            case Float -> Double.toString(floatValue);
            case FloatRaw -> stringValue != null ? stringValue : "";
            case Currency -> {
                var val = Double.toString(floatValue);
                yield currencyCode != null ? "#$" + val + ":" + currencyCode : "#$" + val;
            }
            case CurrencyRaw -> currencyCode != null ? "#$" + stringValue + ":" + currencyCode : "#$" + stringValue;
            case Percent -> "#%" + floatValue;
            case Reference -> "@" + stringValue;
            case Binary -> "^" + stringValue;
            case String -> "\"" + stringValue + "\"";
            case Date, Timestamp, Time, Duration -> stringValue != null ? stringValue : "";
            case Array -> "[" + (arrayValue != null ? arrayValue.size() : 0) + " items]";
            case Object -> "{" + (objectValue != null ? objectValue.size() : 0) + " fields}";
        };
    }

    // ── Private helpers ──

    private static List<DynValue> parseArrayString(java.lang.String s) {
        var trimmed = s.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']')
            return null;
        var inner = trimmed.substring(1, trimmed.length() - 1);
        var items = splitTopLevel(inner);
        var result = new ArrayList<DynValue>();
        for (var item : items) {
            var t = item.trim();
            if (!t.isEmpty()) result.add(parseElement(t));
        }
        return result;
    }

    private static List<Map.Entry<java.lang.String, DynValue>> parseObjectString(java.lang.String s) {
        var trimmed = s.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '{' || trimmed.charAt(trimmed.length() - 1) != '}')
            return null;
        var inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) return new ArrayList<>();
        var pairs = splitTopLevel(inner);
        var result = new ArrayList<Map.Entry<java.lang.String, DynValue>>();
        for (var pair : pairs) {
            var p = pair.trim();
            if (p.isEmpty()) continue;
            int colonPos = findColonSeparator(p);
            if (colonPos < 0) continue;
            var keyStr = p.substring(0, colonPos).trim();
            var valStr = p.substring(colonPos + 1).trim();
            java.lang.String key;
            if (keyStr.length() >= 2 && keyStr.charAt(0) == '"' && keyStr.charAt(keyStr.length() - 1) == '"')
                key = unescapeString(keyStr.substring(1, keyStr.length() - 1));
            else
                key = keyStr;
            result.add(Map.entry(key, parseElement(valStr)));
        }
        return result;
    }

    private static List<java.lang.String> splitTopLevel(java.lang.String s) {
        var items = new ArrayList<java.lang.String>();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '[' || c == '{') depth++;
                else if (c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    items.add(s.substring(start, i));
                    start = i + 1;
                }
            }
        }
        if (start < s.length()) items.add(s.substring(start));
        return items;
    }

    private static DynValue parseElement(java.lang.String s) {
        s = s.trim();
        if (s.equals("~") || s.equals("null")) return ofNull();
        if (s.equals("?true") || s.equals("true")) return ofBool(true);
        if (s.equals("?false") || s.equals("false")) return ofBool(false);
        if (s.length() > 2 && s.charAt(0) == '#' && s.charAt(1) == '#') {
            try { return ofInteger(Long.parseLong(s.substring(2))); }
            catch (NumberFormatException ignored) {}
        }
        if (s.length() > 2 && s.charAt(0) == '#' && s.charAt(1) == '$') {
            try { return ofFloat(Double.parseDouble(s.substring(2))); }
            catch (NumberFormatException ignored) {}
        }
        if (s.length() > 1 && s.charAt(0) == '#') {
            try { return ofFloat(Double.parseDouble(s.substring(1))); }
            catch (NumberFormatException ignored) {}
        }
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
            return ofString(unescapeString(s.substring(1, s.length() - 1)));
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            var arr = parseArrayString(s);
            if (arr != null) return ofArray(arr);
        }
        try { return ofInteger(Long.parseLong(s)); }
        catch (NumberFormatException ignored) {}
        try { return ofFloat(Double.parseDouble(s)); }
        catch (NumberFormatException ignored) {}
        if (s.length() >= 2 && s.charAt(0) == '{' && s.charAt(s.length() - 1) == '}') {
            var obj = parseObjectString(s);
            if (obj != null) return ofObject(obj);
        }
        return ofString(s);
    }

    private static java.lang.String unescapeString(java.lang.String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r");
    }

    private static int findColonSeparator(java.lang.String s) {
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (c == ':' && !inString) return i;
        }
        return -1;
    }

    private static boolean listEquals(List<DynValue> a, List<DynValue> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }

    private static boolean objectEquals(List<Map.Entry<java.lang.String, DynValue>> a,
                                        List<Map.Entry<java.lang.String, DynValue>> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getKey().equals(b.get(i).getKey()) || !a.get(i).getValue().equals(b.get(i).getValue()))
                return false;
        }
        return true;
    }
}
