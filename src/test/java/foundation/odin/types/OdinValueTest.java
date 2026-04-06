package foundation.odin.types;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OdinValueTest {

    // ── Creation and Type Discriminator ──

    @Nested
    class CreationTests {
        @Test void nullHasCorrectType() {
            var v = OdinValue.ofNull();
            assertEquals(OdinValueType.Null, v.getType());
            assertTrue(v.isNull());
            assertEquals("~", v.toString());
        }

        @Test void booleanTrue() {
            var v = OdinValue.ofBoolean(true);
            assertEquals(OdinValueType.Boolean, v.getType());
            assertTrue(v.isBoolean());
            assertEquals(true, v.asBool());
            assertEquals("true", v.toString());
        }

        @Test void booleanFalse() {
            var v = OdinValue.ofBoolean(false);
            assertEquals(false, v.asBool());
            assertEquals("false", v.toString());
        }

        @Test void stringStoresValue() {
            var v = OdinValue.ofString("hello");
            assertEquals(OdinValueType.String, v.getType());
            assertTrue(v.isString());
            assertEquals("hello", v.asString());
            assertEquals("\"hello\"", v.toString());
        }

        @Test void integerStoresValue() {
            var v = OdinValue.ofInteger(42);
            assertEquals(OdinValueType.Integer, v.getType());
            assertTrue(v.isInteger());
            assertEquals(42L, v.asInt64());
            assertEquals(42.0, v.asDouble());
        }

        @Test void integerWithRawPreservesRaw() {
            var v = OdinValue.ofInteger(12345, "12345");
            assertInstanceOf(OdinValue.OdinInteger.class, v);
            assertEquals("12345", ((OdinValue.OdinInteger) v).getRaw());
        }

        @Test void numberStoresValue() {
            var v = OdinValue.ofNumber(3.14);
            assertEquals(OdinValueType.Number, v.getType());
            assertTrue(v.isNumber());
            assertEquals(3.14, v.asDouble());
        }

        @Test void currencyStoresValue() {
            var v = OdinValue.ofCurrency(99.99, (byte) 2);
            assertEquals(OdinValueType.Currency, v.getType());
            assertTrue(v.isCurrency());
            assertEquals(99.99, v.asDouble());
            assertNotNull(v.asDecimal());
        }

        @Test void currencyWithCode() {
            var v = OdinValue.ofCurrency(100.0, (byte) 2, "EUR");
            assertInstanceOf(OdinValue.OdinCurrency.class, v);
            assertEquals("EUR", ((OdinValue.OdinCurrency) v).getCurrencyCode());
        }

        @Test void percentStoresValue() {
            var v = OdinValue.ofPercent(0.25);
            assertEquals(OdinValueType.Percent, v.getType());
            assertTrue(v.isPercent());
            assertEquals(0.25, v.asDouble());
        }

        @Test void dateStoresComponents() {
            var v = OdinValue.ofDate(2024, (byte) 12, (byte) 25);
            assertEquals(OdinValueType.Date, v.getType());
            assertTrue(v.isDate());
            assertInstanceOf(OdinValue.OdinDate.class, v);
            var d = (OdinValue.OdinDate) v;
            assertEquals(2024, d.getYear());
            assertEquals(12, d.getMonth());
            assertEquals(25, d.getDay());
        }

        @Test void timestampStoresEpochMs() {
            var v = OdinValue.ofTimestamp(1718452200000L, "2024-06-15T14:30:00Z");
            assertEquals(OdinValueType.Timestamp, v.getType());
            assertTrue(v.isTimestamp());
            assertEquals("2024-06-15T14:30:00Z", v.toString());
        }

        @Test void timeStoresValue() {
            var v = OdinValue.ofTime("T09:30:00");
            assertEquals(OdinValueType.Time, v.getType());
            assertTrue(v.isTime());
            assertEquals("T09:30:00", v.toString());
        }

        @Test void durationStoresValue() {
            var v = OdinValue.ofDuration("PT30M");
            assertEquals(OdinValueType.Duration, v.getType());
            assertTrue(v.isDuration());
            assertEquals("PT30M", v.toString());
        }

        @Test void referenceStoresPath() {
            var v = OdinValue.ofReference("policy.id");
            assertEquals(OdinValueType.Reference, v.getType());
            assertTrue(v.isReference());
            assertEquals("policy.id", v.asReference());
            assertEquals("@policy.id", v.toString());
        }

        @Test void binaryStoresData() {
            var v = OdinValue.ofBinary(new byte[]{1, 2, 3});
            assertEquals(OdinValueType.Binary, v.getType());
            assertTrue(v.isBinary());
        }

        @Test void binaryWithAlgorithm() {
            var v = OdinValue.ofBinary(new byte[]{1, 2}, "sha256");
            assertInstanceOf(OdinValue.OdinBinary.class, v);
            assertEquals("sha256", ((OdinValue.OdinBinary) v).getAlgorithm());
        }

        @Test void verbStoresNameAndArgs() {
            var args = List.<OdinValue>of(OdinValue.ofString("test"));
            var v = OdinValue.ofVerb("upper", args);
            assertEquals(OdinValueType.Verb, v.getType());
            assertTrue(v.isVerb());
            var verb = (OdinValue.OdinVerb) v;
            assertEquals("upper", verb.getName());
            assertEquals(1, verb.getArgs().size());
        }

        @Test void arrayStoresItems() {
            var items = List.of(OdinArrayItem.fromValue(OdinValue.ofString("a")));
            var v = OdinValue.ofArray(items);
            assertEquals(OdinValueType.Array, v.getType());
            assertTrue(v.isArray());
            assertEquals(1, v.asArray().size());
        }

        @Test void objectStoresFields() {
            var fields = List.<Map.Entry<String, OdinValue>>of(
                    Map.entry("key", OdinValue.ofString("val")));
            var v = OdinValue.ofObject(fields);
            assertEquals(OdinValueType.Object, v.getType());
            assertTrue(v.isObject());
        }
    }

    // ── Composite Type Checks ──

    @Nested
    class CompositeTypeChecks {
        @Test void isNumericForNumericTypes() {
            assertTrue(OdinValue.ofInteger(1).isNumeric());
            assertTrue(OdinValue.ofNumber(1.0).isNumeric());
            assertTrue(OdinValue.ofCurrency(1.0).isNumeric());
            assertTrue(OdinValue.ofPercent(0.5).isNumeric());
        }

        @Test void isNumericFalseForString() {
            assertFalse(OdinValue.ofString("test").isNumeric());
        }

        @Test void isTemporalForTemporalTypes() {
            assertTrue(OdinValue.ofDate(2024, (byte) 1, (byte) 1).isTemporal());
            assertTrue(OdinValue.ofTimestamp(0L, "").isTemporal());
            assertTrue(OdinValue.ofTime("T00:00:00").isTemporal());
            assertTrue(OdinValue.ofDuration("PT1H").isTemporal());
        }
    }

    // ── Typed Accessors Return Null for Wrong Type ──

    @Nested
    class WrongTypeAccessors {
        @Test void asStringReturnsNullForNonString() {
            assertNull(OdinValue.ofInteger(42).asString());
        }

        @Test void asInt64ReturnsNullForNonInteger() {
            assertNull(OdinValue.ofString("test").asInt64());
        }

        @Test void asDoubleReturnsNullForNonNumeric() {
            assertNull(OdinValue.ofString("test").asDouble());
        }

        @Test void asBoolReturnsNullForNonBoolean() {
            assertNull(OdinValue.ofString("test").asBool());
        }

        @Test void asDecimalReturnsNullForNonCurrency() {
            assertNull(OdinValue.ofString("test").asDecimal());
        }

        @Test void asReferenceReturnsNullForNonReference() {
            assertNull(OdinValue.ofString("test").asReference());
        }

        @Test void asArrayReturnsNullForNonArray() {
            assertNull(OdinValue.ofString("test").asArray());
        }
    }

    // ── Factory Methods ──

    @Nested
    class FactoryMethods {
        @Test void factoryNull() { assertInstanceOf(OdinValue.OdinNull.class, OdinValue.ofNull()); }
        @Test void factoryBoolean() { assertEquals(true, OdinValue.ofBoolean(true).asBool()); }
        @Test void factoryString() { assertEquals("test", OdinValue.ofString("test").asString()); }
        @Test void factoryInteger() { assertEquals(99L, OdinValue.ofInteger(99).asInt64()); }

        @Test void factoryIntegerFromRaw() {
            var v = OdinValue.ofInteger(12345, "12345");
            assertEquals(12345L, v.asInt64());
            assertEquals("12345", ((OdinValue.OdinInteger) v).getRaw());
        }

        @Test void factoryNumber() { assertEquals(2.718, OdinValue.ofNumber(2.718).asDouble()); }

        @Test void factoryNumberWithPlaces() {
            var v = OdinValue.ofNumber(3.14, (byte) 2);
            assertInstanceOf(OdinValue.OdinNumber.class, v);
            assertEquals((byte) 2, ((OdinValue.OdinNumber) v).getDecimalPlaces());
        }

        @Test void factoryCurrency() { assertEquals(99.99, OdinValue.ofCurrency(99.99, (byte) 2).asDouble()); }

        @Test void factoryCurrencyWithCode() {
            var v = OdinValue.ofCurrency(100.0, (byte) 2, "EUR");
            assertEquals("EUR", ((OdinValue.OdinCurrency) v).getCurrencyCode());
        }

        @Test void factoryPercent() { assertEquals(0.25, OdinValue.ofPercent(0.25).asDouble()); }

        @Test void factoryDate() {
            var v = OdinValue.ofDate(2024, (byte) 12, (byte) 25);
            var d = (OdinValue.OdinDate) v;
            assertEquals(2024, d.getYear());
            assertEquals(12, d.getMonth());
            assertEquals(25, d.getDay());
        }

        @Test void factoryDateFromStrValid() {
            var v = OdinValue.dateFromStr("2024-06-15");
            assertNotNull(v);
            assertEquals(2024, v.getYear());
            assertEquals(6, v.getMonth());
            assertEquals(15, v.getDay());
        }

        @Test void factoryDateFromStrInvalidReturnsNull() {
            assertNull(OdinValue.dateFromStr("not-a-date"));
            assertNull(OdinValue.dateFromStr("2024"));
        }

        @Test void factoryTimestamp() {
            var v = OdinValue.ofTimestamp(0L, "1970-01-01T00:00:00Z");
            assertEquals("1970-01-01T00:00:00Z", v.toString());
        }

        @Test void factoryTime() { assertEquals("T09:30:00", OdinValue.ofTime("T09:30:00").toString()); }
        @Test void factoryDuration() { assertEquals("PT30M", OdinValue.ofDuration("PT30M").toString()); }
        @Test void factoryReference() { assertEquals("path", OdinValue.ofReference("path").asReference()); }

        @Test void factoryBinary() {
            var v = OdinValue.ofBinary(new byte[]{1, 2, 3});
            assertEquals(3, ((OdinValue.OdinBinary) v).getData().length);
        }

        @Test void factoryBinaryWithAlgorithm() {
            var v = OdinValue.ofBinary(new byte[]{1}, "sha256");
            assertEquals("sha256", ((OdinValue.OdinBinary) v).getAlgorithm());
        }

        @Test void factoryVerb() {
            var args = List.<OdinValue>of(OdinValue.ofString("x"));
            var v = OdinValue.ofVerb("upper", args);
            assertEquals("upper", ((OdinValue.OdinVerb) v).getName());
            assertFalse(((OdinValue.OdinVerb) v).isCustom());
        }

        @Test void factoryCustomVerb() {
            var args = List.<OdinValue>of();
            var v = OdinValue.ofCustomVerb("ns.fn", args);
            assertTrue(((OdinValue.OdinVerb) v).isCustom());
        }

        @Test void factoryArray() {
            var items = List.of(OdinArrayItem.fromValue(OdinValue.ofNull()));
            assertEquals(1, OdinValue.ofArray(items).asArray().size());
        }

        @Test void factoryObject() {
            var fields = List.<Map.Entry<String, OdinValue>>of(Map.entry("a", OdinValue.ofNull()));
            var v = OdinValue.ofObject(fields);
            assertEquals(1, ((OdinValue.OdinObject) v).getFields().size());
        }
    }

    // ── WithModifiers ──

    @Nested
    class WithModifiersTests {
        @Test void returnsNewValueWithModifiers() {
            var v = OdinValue.ofString("secret");
            var mods = new OdinModifiers(false, true, false, false);
            var v2 = v.withModifiers(mods);
            assertTrue(v2.isConfidential());
            assertEquals("secret", v2.asString());
        }

        @Test void allCombinations() {
            var v = OdinValue.ofString("x");
            var mods = new OdinModifiers(true, true, true, false);
            var v2 = v.withModifiers(mods);
            assertTrue(v2.isRequired());
            assertTrue(v2.isConfidential());
            assertTrue(v2.isDeprecated());
        }

        @Test void nullModifiersClearsModifiers() {
            var v = OdinValue.ofString("x");
            var v2 = v.withModifiers(new OdinModifiers(true, false, false, false));
            var v3 = v2.withModifiers(null);
            assertFalse(v3.isRequired());
        }

        @Test void preservesTypeForEachSubclass() {
            var values = List.<OdinValue>of(
                    OdinValue.ofNull(), OdinValue.ofBoolean(true), OdinValue.ofString("s"),
                    OdinValue.ofInteger(1), OdinValue.ofNumber(1.0), OdinValue.ofCurrency(1.0),
                    OdinValue.ofPercent(0.1), OdinValue.ofDate(2024, (byte) 1, (byte) 1),
                    OdinValue.ofTimestamp(0, "ts"), OdinValue.ofTime("T00:00"),
                    OdinValue.ofDuration("PT1H"), OdinValue.ofReference("r"),
                    OdinValue.ofBinary(new byte[0]), OdinValue.ofVerb("v", List.of()),
                    OdinValue.ofArray(List.of()), OdinValue.ofObject(List.of()));

            var mods = new OdinModifiers(true, false, false, false);
            for (var v : values) {
                var v2 = v.withModifiers(mods);
                assertEquals(v.getType(), v2.getType());
                assertTrue(v2.isRequired());
            }
        }
    }

    // ── ArrayItem ──

    @Nested
    class ArrayItemTests {
        @Test void fromValueReturnsValue() {
            var item = OdinArrayItem.fromValue(OdinValue.ofString("test"));
            assertNotNull(item.asValue());
            assertEquals("test", item.asValue().asString());
            assertNull(item.asRecord());
        }

        @Test void recordReturnsFields() {
            var fields = List.<Map.Entry<String, OdinValue>>of(
                    Map.entry("name", OdinValue.ofString("Alice")));
            var item = OdinArrayItem.record(fields);
            assertNotNull(item.asRecord());
            assertEquals(1, item.asRecord().size());
            assertNull(item.asValue());
        }
    }
}
