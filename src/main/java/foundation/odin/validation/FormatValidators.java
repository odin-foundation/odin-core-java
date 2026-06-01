package foundation.odin.validation;

public final class FormatValidators {

    private FormatValidators() {}

    public static boolean validate(String value, String format) {
        if (value == null) return true;
        if (format == null || format.isEmpty()) return true;

        return switch (format) {
            case "email" -> validateEmail(value);
            case "url" -> validateUrl(value);
            case "uri" -> validateUri(value);
            case "uuid" -> validateUuid(value);
            case "hostname" -> validateHostname(value);
            case "datetime", "date-time" -> validateDatetime(value);
            case "ipv4" -> validateIpv4(value);
            case "ipv6" -> validateIpv6(value);
            case "phone" -> validatePhone(value);
            case "zip" -> validateZip(value);
            case "date-iso" -> value.matches("^\\d{4}-\\d{2}-\\d{2}$");
            case "naic" -> validateNaic(value);
            case "fein" -> validateFein(value);
            case "vin" -> validateVin(value);
            case "currency-code" -> validateCurrencyCode(value);
            case "country-alpha2" -> validateIsoCode2(value, ISO_ALPHA2);
            case "country-alpha3" -> validateIsoCode3(value, ISO_ALPHA3);
            case "state-us" -> validateIsoCode2(value, US_STATES);
            case "creditcard", "credit-card" -> validateLuhn(value);
            case "ssn" -> validateTaxId(value);
            case "iban" -> validateIban(value);
            case "bic", "swift" -> validateBic(value);
            case "routing" -> value.matches("^\\d{9}$");
            case "cusip" -> value.matches("^[A-Za-z0-9]{9}$");
            case "isin" -> value.matches("^[A-Za-z]{2}[A-Za-z0-9]{9}\\d$");
            case "lei" -> value.matches("^[A-Za-z0-9]{20}$");
            case "npi" -> value.matches("^\\d{10}$");
            case "dea" -> value.matches("^[A-Za-z]{2}\\d{7}$");
            case "imei" -> value.matches("^\\d{15}$");
            case "iccid" -> value.matches("^\\d{19,20}$");
            default -> true;
        };
    }

    private static boolean validateEmail(String value) {
        if (value.isEmpty()) return true;
        int atIdx = value.indexOf('@');
        if (atIdx <= 0 || atIdx == value.length() - 1) return false;
        if (value.indexOf('@', atIdx + 1) >= 0) return false;
        String local = value.substring(0, atIdx);
        String domain = value.substring(atIdx + 1);
        if (local.isEmpty() || domain.isEmpty()) return false;
        return domain.indexOf('.') > 0;
    }

    private static boolean validateUrl(String value) {
        if (value.isEmpty()) return true;
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static boolean validateUri(String value) {
        if (value.isEmpty()) return true;
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:\\S*$");
    }

    private static boolean validateHostname(String value) {
        if (value.isEmpty()) return true;
        return value.matches(
                "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");
    }

    private static boolean validateDatetime(String value) {
        if (value.isEmpty()) return true;
        return value.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*$");
    }

    private static boolean validateIban(String value) {
        if (value.isEmpty()) return true;
        return value.matches("^[A-Za-z]{2}\\d{2}[A-Za-z0-9]{4,30}$");
    }

    private static boolean validateBic(String value) {
        if (value.isEmpty()) return true;
        return value.matches("^[A-Za-z]{4}[A-Za-z]{2}[A-Za-z0-9]{2}([A-Za-z0-9]{3})?$");
    }

    private static boolean validateUuid(String value) {
        if (value.isEmpty()) return true;
        if (value.length() != 36) return false;
        for (int i = 0; i < 36; i++) {
            char ch = value.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (ch != '-') return false;
            } else {
                if (!isHexDigit(ch)) return false;
            }
        }
        return true;
    }

    private static boolean validateIpv4(String value) {
        if (value.isEmpty()) return true;
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) return false;
            if (octet.length() > 1 && octet.charAt(0) == '0') return false;
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) return false;
            }
            int val = Integer.parseInt(octet);
            if (val < 0 || val > 255) return false;
        }
        return true;
    }

    private static boolean validateIpv6(String value) {
        if (value.isEmpty()) return true;
        int compressedCount = 0;
        int idx = 0;
        while ((idx = value.indexOf("::", idx)) >= 0) {
            compressedCount++;
            idx += 2;
        }
        if (compressedCount > 1) return false;

        if (compressedCount == 1) {
            String[] parts = value.split("::", -1);
            String[] left = parts[0].isEmpty() ? new String[0] : parts[0].split(":", -1);
            String[] right = parts.length > 1 && !parts[1].isEmpty() ? parts[1].split(":", -1) : new String[0];
            int totalGroups = left.length + right.length;
            if (totalGroups > 8) return false;
            for (String group : left) if (!isValidIpv6Group(group)) return false;
            for (String group : right) if (!isValidIpv6Group(group)) return false;
            return true;
        }

        String[] groups = value.split(":", -1);
        if (groups.length != 8) return false;
        for (String group : groups) {
            if (!isValidIpv6Group(group)) return false;
        }
        return true;
    }

    private static boolean isValidIpv6Group(String group) {
        if (group.isEmpty() || group.length() > 4) return false;
        for (int i = 0; i < group.length(); i++) {
            if (!isHexDigit(group.charAt(i))) return false;
        }
        return true;
    }

    private static boolean validatePhone(String value) {
        if (value.isEmpty()) return true;
        int digitCount = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) {
                digitCount++;
            } else if (ch != '+' && ch != '-' && ch != ' ' && ch != '(' && ch != ')' && ch != '.') {
                return false;
            }
        }
        return digitCount >= 7;
    }

    private static boolean validateZip(String value) {
        if (value.isEmpty()) return true;
        if (value.length() == 5) {
            return allDigits(value);
        }
        if (value.length() == 10 && value.charAt(5) == '-') {
            return allDigits(value.substring(0, 5)) && allDigits(value.substring(6));
        }
        return false;
    }

    private static boolean validateNaic(String value) {
        return value.length() == 5 && allDigits(value);
    }

    private static boolean validateFein(String value) {
        if (value.isEmpty()) return true;
        if (value.length() != 10 || value.charAt(2) != '-') return false;
        return allDigits(value.substring(0, 2)) && allDigits(value.substring(3));
    }

    private static boolean validateVin(String value) {
        if (value.isEmpty()) return true;
        if (value.length() != 17) return false;
        for (int i = 0; i < 17; i++) {
            char ch = Character.toUpperCase(value.charAt(i));
            if (!Character.isLetterOrDigit(ch)) return false;
            if (ch == 'I' || ch == 'O' || ch == 'Q') return false;
        }
        return true;
    }

    private static boolean validateCurrencyCode(String value) {
        if (value.isEmpty()) return true;
        if (value.length() != 3) return false;
        for (int i = 0; i < 3; i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') return false;
        }
        return java.util.Arrays.binarySearch(CURRENCY_CODES, value) >= 0;
    }

    private static final String[] CURRENCY_CODES = {
        "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN",
        "BAM", "BBD", "BDT", "BGN", "BHD", "BIF", "BMD", "BND", "BOB", "BRL",
        "BSD", "BTN", "BWP", "BYN", "BZD", "CAD", "CDF", "CHF", "CLP", "CNY",
        "COP", "CRC", "CUP", "CVE", "CZK", "DJF", "DKK", "DOP", "DZD", "EGP",
        "ERN", "ETB", "EUR", "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD",
        "GNF", "GTQ", "GYD", "HKD", "HNL", "HRK", "HTG", "HUF", "IDR", "ILS",
        "INR", "IQD", "IRR", "ISK", "JMD", "JOD", "JPY", "KES", "KGS", "KHR",
        "KMF", "KPW", "KRW", "KWD", "KYD", "KZT", "LAK", "LBP", "LKR", "LRD",
        "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU",
        "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK",
        "NPR", "NZD", "OMR", "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG",
        "QAR", "RON", "RSD", "RUB", "RWF", "SAR", "SBD", "SCR", "SDG", "SEK",
        "SGD", "SHP", "SLE", "SOS", "SRD", "SSP", "STN", "SVC", "SYP", "SZL",
        "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS", "UAH",
        "UGX", "USD", "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF", "XCD",
        "XOF", "XPF", "YER", "ZAR", "ZMW", "ZWL"
    };

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean validateIsoCode2(String value, String[] codes) {
        if (value.length() != 2) return false;
        for (int i = 0; i < 2; i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') return false;
        }
        return java.util.Arrays.binarySearch(codes, value) >= 0;
    }

    private static final String[] ISO_ALPHA2 = {
        "AE", "AR", "AT", "AU", "BD", "BE", "BG", "BH", "BR", "CA",
        "CH", "CL", "CN", "CO", "CY", "CZ", "DE", "DK", "EG", "ES",
        "FI", "FR", "GB", "GH", "GR", "HK", "HR", "HU", "ID", "IE",
        "IL", "IN", "IS", "IT", "JO", "JP", "KE", "KR", "KW", "LB",
        "MA", "MX", "MY", "NG", "NL", "NO", "NZ", "OM", "PE", "PH",
        "PK", "PL", "PT", "QA", "RO", "RU", "SA", "SE", "SG", "TH",
        "TR", "TW", "TZ", "UA", "UG", "US", "VN", "ZA"
    };

    private static boolean validateIsoCode3(String value, String[] codes) {
        if (value.length() != 3) return false;
        for (int i = 0; i < 3; i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') return false;
        }
        return java.util.Arrays.binarySearch(codes, value) >= 0;
    }

    private static final String[] ISO_ALPHA3 = {
        "ARE", "ARG", "AUS", "AUT", "BEL", "BGD", "BGR", "BHR", "BRA", "CAN",
        "CHE", "CHL", "CHN", "COL", "CYP", "CZE", "DEU", "DNK", "EGY", "ESP",
        "FIN", "FRA", "GBR", "GHA", "GRC", "HKG", "HRV", "HUN", "IDN", "IND",
        "IRL", "ISL", "ISR", "ITA", "JOR", "JPN", "KEN", "KOR", "KWT", "LBN",
        "MAR", "MEX", "MYS", "NGA", "NLD", "NOR", "NZL", "OMN", "PAK", "PER",
        "PHL", "POL", "PRT", "QAT", "ROU", "RUS", "SAU", "SGP", "SWE", "THA",
        "TUR", "TWN", "TZA", "UGA", "UKR", "USA", "VNM", "ZAF"
    };

    private static final String[] US_STATES = {
        "AK", "AL", "AR", "AS", "AZ", "CA", "CO", "CT", "DC", "DE",
        "FL", "GA", "GU", "HI", "IA", "ID", "IL", "IN", "KS", "KY",
        "LA", "MA", "MD", "ME", "MI", "MN", "MO", "MP", "MS", "MT",
        "NC", "ND", "NE", "NH", "NJ", "NM", "NV", "NY", "OH", "OK",
        "OR", "PA", "PR", "RI", "SC", "SD", "TN", "TX", "UT", "VA",
        "VI", "VT", "WA", "WI", "WV", "WY"
    };

    private static boolean validateLuhn(String value) {
        if (value.isEmpty()) return true;
        var digits = new java.util.ArrayList<Integer>();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) digits.add(ch - '0');
            else if (ch != ' ' && ch != '-') return false;
        }
        if (digits.size() < 13 || digits.size() > 19) return false;

        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.size() - 1; i >= 0; i--) {
            int d = digits.get(i);
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static boolean validateTaxId(String value) {
        if (value.isEmpty()) return true;
        var digits = new java.util.ArrayList<Character>();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) digits.add(ch);
        }
        if (digits.size() != 9) return false;

        boolean validFormat = value.length() == 9
                || (value.length() == 11 && value.charAt(3) == '-' && value.charAt(6) == '-');
        if (!validFormat) return false;

        if (digits.get(0) == '0' && digits.get(1) == '0' && digits.get(2) == '0') return false;

        return true;
    }

    private static boolean isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }
}
