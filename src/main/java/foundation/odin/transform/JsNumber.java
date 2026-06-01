package foundation.odin.transform;

import java.math.BigDecimal;

/** Formats a double the way ECMAScript Number.prototype.toString() does. */
final class JsNumber {

    private JsNumber() {}

    static String toString(double n) {
        if (Double.isNaN(n)) return "NaN";
        if (Double.isInfinite(n)) return n > 0 ? "Infinity" : "-Infinity";
        if (n == 0.0) return "0";

        boolean negative = n < 0;
        double abs = Math.abs(n);

        // Shortest round-tripping decimal digits, exponent-free.
        String repr = Double.toString(abs);
        String digits;
        int pointExp; // power of ten for the first digit (k-1 in ECMA terms)

        int ePos = repr.indexOf('E');
        if (ePos >= 0) {
            String mantissa = repr.substring(0, ePos);
            int exp = Integer.parseInt(repr.substring(ePos + 1));
            int dot = mantissa.indexOf('.');
            String intPart = dot >= 0 ? mantissa.substring(0, dot) : mantissa;
            String fracPart = dot >= 0 ? mantissa.substring(dot + 1) : "";
            String all = intPart + fracPart;
            int lead = intPart.length();
            digits = stripTrailingZeros(stripLeadingZeros(all));
            int firstSig = firstSignificant(all);
            pointExp = (lead - 1 - firstSig) + exp;
        } else {
            int dot = repr.indexOf('.');
            String intPart = dot >= 0 ? repr.substring(0, dot) : repr;
            String fracPart = dot >= 0 ? repr.substring(dot + 1) : "";
            String all = intPart + fracPart;
            int firstSig = firstSignificant(all);
            digits = stripTrailingZeros(all.substring(firstSig));
            pointExp = intPart.length() - 1 - firstSig;
        }

        if (digits.isEmpty()) return "0";

        String body = assemble(digits, pointExp);
        return negative ? "-" + body : body;
    }

    // ECMA-262 Number::toString decimal/exponential selection.
    private static String assemble(String digits, int pointExp) {
        int k = digits.length();
        int nE = pointExp + 1; // position of decimal point relative to first digit

        if (k <= nE && nE <= 21) {
            return digits + "0".repeat(nE - k);
        }
        if (0 < nE && nE <= 21) {
            return digits.substring(0, nE) + "." + digits.substring(nE);
        }
        if (-6 < nE && nE <= 0) {
            return "0." + "0".repeat(-nE) + digits;
        }
        // Exponential form.
        String mantissa = k == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        int exp = nE - 1;
        return mantissa + "e" + (exp >= 0 ? "+" : "-") + Math.abs(exp);
    }

    private static int firstSignificant(String all) {
        int i = 0;
        while (i < all.length() && all.charAt(i) == '0') i++;
        return i == all.length() ? all.length() - 1 : i;
    }

    private static String stripLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    private static String stripTrailingZeros(String s) {
        int end = s.length();
        while (end > 1 && s.charAt(end - 1) == '0') end--;
        return s.substring(0, end);
    }
}
