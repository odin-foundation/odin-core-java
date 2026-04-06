package foundation.odin.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class FormatValidatorsTest {

    // ─── Email ───────────────────────────────────────────────────────────

    @Nested class EmailTests {
        @Test void validEmail() { assertTrue(FormatValidators.validate("user@example.com", "email")); }
        @Test void validEmailWithDots() { assertTrue(FormatValidators.validate("first.last@example.com", "email")); }
        @Test void validEmailWithPlus() { assertTrue(FormatValidators.validate("user+tag@example.com", "email")); }
        @Test void validEmailSubdomain() { assertTrue(FormatValidators.validate("user@sub.domain.com", "email")); }
        @Test void invalidEmailNoAt() { assertFalse(FormatValidators.validate("userexample.com", "email")); }
        @Test void invalidEmailNoDomain() { assertFalse(FormatValidators.validate("user@", "email")); }
        @Test void invalidEmailNoLocal() { assertFalse(FormatValidators.validate("@example.com", "email")); }
        @Test void emptyEmailPassesGuard() { assertTrue(FormatValidators.validate("", "email")); }
    }

    // ─── URL ─────────────────────────────────────────────────────────────

    @Nested class UrlTests {
        @Test void validHttpUrl() { assertTrue(FormatValidators.validate("http://example.com", "url")); }
        @Test void validHttpsUrl() { assertTrue(FormatValidators.validate("https://example.com", "url")); }
        @Test void validUrlWithPath() { assertTrue(FormatValidators.validate("https://example.com/path/to/resource", "url")); }
        @Test void validUrlWithQuery() { assertTrue(FormatValidators.validate("https://example.com?q=test", "url")); }
        @Test void validUrlWithPort() { assertTrue(FormatValidators.validate("https://example.com:8080", "url")); }
        @Test void invalidUrlNoScheme() { assertFalse(FormatValidators.validate("example.com", "url")); }
        @Test void emptyUrlPassesGuard() { assertTrue(FormatValidators.validate("", "url")); }
    }

    // ─── UUID ────────────────────────────────────────────────────────────

    @Nested class UuidTests {
        @Test void validUuid() { assertTrue(FormatValidators.validate("550e8400-e29b-41d4-a716-446655440000", "uuid")); }
        @Test void validUuidUppercase() { assertTrue(FormatValidators.validate("550E8400-E29B-41D4-A716-446655440000", "uuid")); }
        @Test void invalidUuidTooShort() { assertFalse(FormatValidators.validate("550e8400-e29b-41d4-a716", "uuid")); }
        @Test void invalidUuidNoDashes() { assertFalse(FormatValidators.validate("550e8400e29b41d4a716446655440000", "uuid")); }
        @Test void emptyUuidPassesGuard() { assertTrue(FormatValidators.validate("", "uuid")); }
        @Test void invalidUuidBadChars() { assertFalse(FormatValidators.validate("550g8400-e29b-41d4-a716-446655440000", "uuid")); }
    }

    // ─── IPv4 ────────────────────────────────────────────────────────────

    @Nested class Ipv4Tests {
        @Test void validIpv4() { assertTrue(FormatValidators.validate("192.168.1.1", "ipv4")); }
        @Test void validIpv4Localhost() { assertTrue(FormatValidators.validate("127.0.0.1", "ipv4")); }
        @Test void validIpv4AllZeros() { assertTrue(FormatValidators.validate("0.0.0.0", "ipv4")); }
        @Test void validIpv4Max() { assertTrue(FormatValidators.validate("255.255.255.255", "ipv4")); }
        @Test void invalidIpv4TooHigh() { assertFalse(FormatValidators.validate("256.1.1.1", "ipv4")); }
        @Test void invalidIpv4TooFewOctets() { assertFalse(FormatValidators.validate("192.168.1", "ipv4")); }
        @Test void invalidIpv4Letters() { assertFalse(FormatValidators.validate("192.168.a.1", "ipv4")); }
        @Test void emptyIpv4PassesGuard() { assertTrue(FormatValidators.validate("", "ipv4")); }
    }

    // ─── IPv6 ────────────────────────────────────────────────────────────

    @Nested class Ipv6Tests {
        @Test void validIpv6Full() { assertTrue(FormatValidators.validate("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "ipv6")); }
        @Test void validIpv6Compressed() { assertTrue(FormatValidators.validate("2001:db8::1", "ipv6")); }
        @Test void validIpv6Loopback() { assertTrue(FormatValidators.validate("::1", "ipv6")); }
        @Test void emptyIpv6PassesGuard() { assertTrue(FormatValidators.validate("", "ipv6")); }
    }

    // ─── Phone ───────────────────────────────────────────────────────────

    @Nested class PhoneTests {
        @Test void validPhone() { assertTrue(FormatValidators.validate("+1-555-123-4567", "phone")); }
        @Test void validPhoneParens() { assertTrue(FormatValidators.validate("(555) 123-4567", "phone")); }
        @Test void validPhoneDots() { assertTrue(FormatValidators.validate("555.123.4567", "phone")); }
        @Test void invalidPhoneTooShort() { assertFalse(FormatValidators.validate("123", "phone")); }
        @Test void emptyPhonePassesGuard() { assertTrue(FormatValidators.validate("", "phone")); }
        @Test void invalidPhoneLetters() { assertFalse(FormatValidators.validate("abc-def-ghij", "phone")); }
    }

    // ─── ZIP ─────────────────────────────────────────────────────────────

    @Nested class ZipTests {
        @Test void validZip5() { assertTrue(FormatValidators.validate("12345", "zip")); }
        @Test void validZipPlus4() { assertTrue(FormatValidators.validate("12345-6789", "zip")); }
        @Test void invalidZipTooShort() { assertFalse(FormatValidators.validate("1234", "zip")); }
        @Test void invalidZipLetters() { assertFalse(FormatValidators.validate("abcde", "zip")); }
        @Test void emptyZipPassesGuard() { assertTrue(FormatValidators.validate("", "zip")); }
    }

    // ─── Date ISO ────────────────────────────────────────────────────────

    @Nested class DateIsoTests {
        @Test void validDate() { assertTrue(FormatValidators.validate("2024-06-15", "date-iso")); }
        @Test void validDateJan1() { assertTrue(FormatValidators.validate("2024-01-01", "date-iso")); }
        @Test void validDateDec31() { assertTrue(FormatValidators.validate("2024-12-31", "date-iso")); }
        @Test void invalidDateIso() { assertFalse(FormatValidators.validate("anything", "date-iso")); }
    }

    // ─── VIN ─────────────────────────────────────────────────────────────

    @Nested class VinTests {
        @Test void validVin() { assertTrue(FormatValidators.validate("1HGBH41JXMN109186", "vin")); }
        @Test void invalidVinTooShort() { assertFalse(FormatValidators.validate("1HGBH41JXM", "vin")); }
        @Test void invalidVinBadChars() { assertFalse(FormatValidators.validate("1HGBH41IXMN109186", "vin")); }
        @Test void emptyVinPassesGuard() { assertTrue(FormatValidators.validate("", "vin")); }
    }

    // ─── Currency code ───────────────────────────────────────────────────

    @Nested class CurrencyCodeTests {
        @Test void validUsd() { assertTrue(FormatValidators.validate("USD", "currency-code")); }
        @Test void validEur() { assertTrue(FormatValidators.validate("EUR", "currency-code")); }
        @Test void validGbp() { assertTrue(FormatValidators.validate("GBP", "currency-code")); }
        @Test void validJpy() { assertTrue(FormatValidators.validate("JPY", "currency-code")); }
        @Test void invalidCurrencyCode() { assertFalse(FormatValidators.validate("XYZ", "currency-code")); }
        @Test void invalidCurrencyCodeLower() { assertFalse(FormatValidators.validate("usd", "currency-code")); }
        @Test void emptyCurrencyPassesGuard() { assertTrue(FormatValidators.validate("", "currency-code")); }
    }

    // ─── Country codes ───────────────────────────────────────────────────

    @Nested class CountryCodeTests {
        @Test void validAlpha2Us() { assertTrue(FormatValidators.validate("US", "country-alpha2")); }
        @Test void validAlpha2Gb() { assertTrue(FormatValidators.validate("GB", "country-alpha2")); }
        @Test void invalidAlpha2() { assertFalse(FormatValidators.validate("XX", "country-alpha2")); }
        @Test void validAlpha3Usa() { assertTrue(FormatValidators.validate("USA", "country-alpha3")); }
        @Test void validAlpha3Gbr() { assertTrue(FormatValidators.validate("GBR", "country-alpha3")); }
        @Test void invalidAlpha3() { assertFalse(FormatValidators.validate("XXX", "country-alpha3")); }
    }

    // ─── US State ────────────────────────────────────────────────────────

    @Nested class StateTests {
        @Test void validStateTx() { assertTrue(FormatValidators.validate("TX", "state-us")); }
        @Test void validStateCa() { assertTrue(FormatValidators.validate("CA", "state-us")); }
        @Test void validStateNy() { assertTrue(FormatValidators.validate("NY", "state-us")); }
        @Test void invalidState() { assertFalse(FormatValidators.validate("XX", "state-us")); }
        @Test void invalidStateLower() { assertFalse(FormatValidators.validate("tx", "state-us")); }
    }

    // ─── Credit card ─────────────────────────────────────────────────────

    @Nested class CreditCardTests {
        @Test void validVisa() { assertTrue(FormatValidators.validate("4111111111111111", "creditcard")); }
        @Test void validMastercard() { assertTrue(FormatValidators.validate("5500000000000004", "creditcard")); }
        @Test void invalidCcBadLuhn() { assertFalse(FormatValidators.validate("4111111111111112", "creditcard")); }
        @Test void invalidCcTooShort() { assertFalse(FormatValidators.validate("411111111111", "creditcard")); }
        @Test void emptyCcPassesGuard() { assertTrue(FormatValidators.validate("", "creditcard")); }
    }

    // ─── SSN ─────────────────────────────────────────────────────────────

    @Nested class SsnTests {
        @Test void validSsn() { assertTrue(FormatValidators.validate("123-45-6789", "ssn")); }
        @Test void validSsnNoDashes() { assertTrue(FormatValidators.validate("123456789", "ssn")); }
        @Test void emptySsnPassesGuard() { assertTrue(FormatValidators.validate("", "ssn")); }
    }

    // ─── FEIN ────────────────────────────────────────────────────────────

    @Nested class FeinTests {
        @Test void validFein() { assertTrue(FormatValidators.validate("12-3456789", "fein")); }
        @Test void invalidFeinNoDash() { assertFalse(FormatValidators.validate("123456789", "fein")); }
        @Test void emptyFeinPassesGuard() { assertTrue(FormatValidators.validate("", "fein")); }
    }

    // ─── Unknown format ──────────────────────────────────────────────────

    @Nested class UnknownFormatTests {
        @Test void unknownFormatReturnsTrue() {
            assertTrue(FormatValidators.validate("anything", "nonexistent-format"));
        }
    }
}
