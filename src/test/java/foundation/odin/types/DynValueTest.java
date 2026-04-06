package foundation.odin.types;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DynValueTest {

    // ── Factory Methods & Type Checking ──

    @Nested
    class FactoryMethods {
        @Test void nullIsNull() {
            var v = DynValue.ofNull();
            assertEquals(DynValue.Type.Null, v.getType());
            assertTrue(v.isNull());
        }

        @Test void boolTrue() {
            var v = DynValue.ofBool(true);
            assertEquals(DynValue.Type.Bool, v.getType());
            assertEquals(true, v.asBool());
        }

        @Test void boolFalse() {
            var v = DynValue.ofBool(false);
            assertEquals(false, v.asBool());
        }

        @Test void integerStoresValue() {
            var v = DynValue.ofInteger(42);
            assertEquals(DynValue.Type.Integer, v.getType());
            assertEquals(42L, v.asInt64());
            assertEquals(42.0, v.asDouble());
        }

        @Test void floatStoresValue() {
            var v = DynValue.ofFloat(3.14);
            assertEquals(DynValue.Type.Float, v.getType());
            assertEquals(3.14, v.asDouble());
        }

        @Test void floatRawStoresRawString() {
            var v = DynValue.ofFloatRaw("1234567890.123456789");
            assertEquals(DynValue.Type.FloatRaw, v.getType());
            assertEquals("1234567890.123456789", v.asString());
        }

        @Test void stringStoresValue() {
            var v = DynValue.ofString("hello");
            assertEquals(DynValue.Type.String, v.getType());
            assertEquals("hello", v.asString());
        }

        @Test void currencyStoresValue() {
            var v = DynValue.ofCurrency(99.99, (byte) 2, "USD");
            assertEquals(DynValue.Type.Currency, v.getType());
            assertEquals(99.99, v.asDouble());
            assertEquals("USD", v.getCurrencyCode());
            assertEquals((byte) 2, v.getDecimalPlaces());
        }

        @Test void currencyRawStoresRawString() {
            var v = DynValue.ofCurrencyRaw("100.50", (byte) 2, "EUR");
            assertEquals(DynValue.Type.CurrencyRaw, v.getType());
            assertEquals("100.50", v.asString());
            assertEquals("EUR", v.getCurrencyCode());
        }

        @Test void percentStoresValue() {
            var v = DynValue.ofPercent(0.15);
            assertEquals(DynValue.Type.Percent, v.getType());
            assertEquals(0.15, v.asDouble());
        }

        @Test void referenceStoresPath() {
            var v = DynValue.ofReference("policy.id");
            assertEquals(DynValue.Type.Reference, v.getType());
            assertEquals("policy.id", v.asString());
        }

        @Test void binaryStoresBase64() {
            var v = DynValue.ofBinary("SGVsbG8=");
            assertEquals(DynValue.Type.Binary, v.getType());
            assertEquals("SGVsbG8=", v.asString());
        }

        @Test void dateStoresDateString() {
            var v = DynValue.ofDate("2024-06-15");
            assertEquals(DynValue.Type.Date, v.getType());
            assertEquals("2024-06-15", v.asString());
        }

        @Test void timestampStoresTimestampString() {
            var v = DynValue.ofTimestamp("2024-06-15T14:30:00Z");
            assertEquals(DynValue.Type.Timestamp, v.getType());
            assertEquals("2024-06-15T14:30:00Z", v.asString());
        }

        @Test void timeStoresTimeString() {
            var v = DynValue.ofTime("14:30:00");
            assertEquals(DynValue.Type.Time, v.getType());
            assertEquals("14:30:00", v.asString());
        }

        @Test void durationStoresDurationString() {
            var v = DynValue.ofDuration("PT30M");
            assertEquals(DynValue.Type.Duration, v.getType());
            assertEquals("PT30M", v.asString());
        }

        @Test void arrayStoresItems() {
            var items = List.of(DynValue.ofInteger(1), DynValue.ofString("two"));
            var v = DynValue.ofArray(items);
            assertEquals(DynValue.Type.Array, v.getType());
            assertEquals(2, v.asArray().size());
        }

        @Test void objectStoresEntries() {
            var entries = List.<Map.Entry<String, DynValue>>of(
                    Map.entry("key", DynValue.ofString("val")));
            var v = DynValue.ofObject(entries);
            assertEquals(DynValue.Type.Object, v.getType());
            assertEquals(1, v.asObject().size());
        }
    }

    // ── Wrong Type Accessors ──

    @Nested
    class WrongTypeAccessors {
        @Test void asStringReturnsNullForNonStringTypes() {
            assertNull(DynValue.ofInteger(42).asString());
            assertNull(DynValue.ofBool(true).asString());
            assertNull(DynValue.ofNull().asString());
        }

        @Test void asInt64ReturnsNullForNonInteger() {
            assertNull(DynValue.ofString("x").asInt64());
            assertNull(DynValue.ofFloat(1.0).asInt64());
        }

        @Test void asDoubleReturnsNullForNonNumeric() {
            assertNull(DynValue.ofString("x").asDouble());
            assertNull(DynValue.ofBool(true).asDouble());
        }

        @Test void asBoolReturnsNullForNonBool() {
            assertNull(DynValue.ofString("x").asBool());
            assertNull(DynValue.ofInteger(1).asBool());
        }

        @Test void asArrayReturnsNullForNonArray() {
            assertNull(DynValue.ofString("x").asArray());
        }

        @Test void asObjectReturnsNullForNonObject() {
            assertNull(DynValue.ofString("x").asObject());
        }
    }

    // ── Object/Array Access ──

    @Nested
    class StructuredAccess {
        @Test void getReturnsFieldFromObject() {
            var entries = List.<Map.Entry<String, DynValue>>of(
                    Map.entry("name", DynValue.ofString("Alice")));
            var v = DynValue.ofObject(entries);
            assertEquals("Alice", v.get("name").asString());
        }

        @Test void getReturnsNullForMissingKey() {
            var v = DynValue.ofObject(List.of());
            assertNull(v.get("missing"));
        }

        @Test void getReturnsNullForNonObject() {
            assertNull(DynValue.ofString("x").get("key"));
        }

        @Test void getIndexReturnsArrayElement() {
            var v = DynValue.ofArray(List.of(DynValue.ofInteger(10), DynValue.ofInteger(20)));
            assertEquals(20L, v.getIndex(1).asInt64());
        }

        @Test void getIndexReturnsNullForOutOfRange() {
            var v = DynValue.ofArray(List.of(DynValue.ofInteger(1)));
            assertNull(v.getIndex(5));
            assertNull(v.getIndex(-1));
        }
    }

    // ── Equality ──

    @Nested
    class EqualityTests {
        @Test void nullValues() { assertEquals(DynValue.ofNull(), DynValue.ofNull()); }
        @Test void boolValues() { assertEquals(DynValue.ofBool(true), DynValue.ofBool(true)); }
        @Test void integerValues() { assertEquals(DynValue.ofInteger(42), DynValue.ofInteger(42)); }
        @Test void floatValues() { assertEquals(DynValue.ofFloat(3.14), DynValue.ofFloat(3.14)); }
        @Test void stringValues() { assertEquals(DynValue.ofString("hi"), DynValue.ofString("hi")); }

        @Test void differentTypesNotEqual() {
            assertNotEquals(DynValue.ofInteger(1), DynValue.ofString("1"));
        }

        @Test void arrayValues() {
            var a = DynValue.ofArray(List.of(DynValue.ofInteger(1)));
            var b = DynValue.ofArray(List.of(DynValue.ofInteger(1)));
            assertEquals(a, b);
        }

        @Test void objectValues() {
            var a = DynValue.ofObject(List.of(Map.entry("k", DynValue.ofString("v"))));
            var b = DynValue.ofObject(List.of(Map.entry("k", DynValue.ofString("v"))));
            assertEquals(a, b);
        }

        @Test void nullReferenceNotEqual() {
            assertNotEquals(null, DynValue.ofNull());
        }

        @Test void hashCodeConsistentForEqualValues() {
            var a = DynValue.ofInteger(42);
            var b = DynValue.ofInteger(42);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    // ── ToString ──

    @Nested
    class ToStringTests {
        @Test void nullToString() { assertEquals("null", DynValue.ofNull().toString()); }
        @Test void boolToString() { assertEquals("true", DynValue.ofBool(true).toString()); }
        @Test void integerToString() { assertEquals("42", DynValue.ofInteger(42).toString()); }
        @Test void stringToString() { assertEquals("\"hello\"", DynValue.ofString("hello").toString()); }
    }

    // ── FromJsonElement ──

    @Nested
    class JsonTests {
        @Test void fromJsonNull() {
            var el = JsonParser.parseString("null");
            var v = DynValue.fromJsonElement(el);
            assertTrue(v.isNull());
        }

        @Test void fromJsonBoolean() {
            var el = JsonParser.parseString("true");
            var v = DynValue.fromJsonElement(el);
            assertEquals(true, v.asBool());
        }

        @Test void fromJsonInteger() {
            var el = JsonParser.parseString("42");
            var v = DynValue.fromJsonElement(el);
            assertEquals(42L, v.asInt64());
        }

        @Test void fromJsonFloat() {
            var el = JsonParser.parseString("3.14");
            var v = DynValue.fromJsonElement(el);
            assertEquals(3.14, v.asDouble());
        }

        @Test void fromJsonString() {
            var el = JsonParser.parseString("\"hello\"");
            var v = DynValue.fromJsonElement(el);
            assertEquals("hello", v.asString());
        }

        @Test void fromJsonArray() {
            var el = JsonParser.parseString("[1, \"two\", true]");
            var v = DynValue.fromJsonElement(el);
            assertEquals(DynValue.Type.Array, v.getType());
            assertEquals(3, v.asArray().size());
        }

        @Test void fromJsonObject() {
            var el = JsonParser.parseString("{\"name\": \"Alice\", \"age\": 30}");
            var v = DynValue.fromJsonElement(el);
            assertEquals(DynValue.Type.Object, v.getType());
            assertEquals("Alice", v.get("name").asString());
        }

        @Test void toJsonElementRoundTrip() {
            var original = DynValue.ofObject(List.of(
                    Map.entry("name", DynValue.ofString("Bob")),
                    Map.entry("age", DynValue.ofInteger(25))));
            var json = original.toJsonElement();
            var roundTripped = DynValue.fromJsonElement(json);
            assertEquals(original, roundTripped);
        }
    }
}
