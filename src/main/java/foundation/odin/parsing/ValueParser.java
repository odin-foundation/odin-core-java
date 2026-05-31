package foundation.odin.parsing;

import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinErrors.ParseErrorCode;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinValue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public final class ValueParser {
    private ValueParser() {}

    public record ParseResult(OdinValue value, int consumed) {}
    public record ModifierResult(OdinModifiers modifiers, int consumed) {}

    public static ParseResult parseValue(List<Token> tokens, int pos) {
        if (pos >= tokens.size()) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, 0, 0);
        }

        var token = tokens.get(pos);

        return switch (token.getTokenType()) {
            case Null -> new ParseResult(OdinValue.ofNull(), 1);

            case BooleanLiteral -> new ParseResult(
                    OdinValue.ofBoolean("true".equals(token.getValue())), 1);

            case BooleanPrefix -> {
                if (pos + 1 < tokens.size()) {
                    var next = tokens.get(pos + 1);
                    if ("true".equals(next.getValue()))
                        yield new ParseResult(OdinValue.ofBoolean(true), 2);
                    if ("false".equals(next.getValue()))
                        yield new ParseResult(OdinValue.ofBoolean(false), 2);
                }
                yield new ParseResult(OdinValue.ofBoolean(true), 1);
            }

            case QuotedString -> new ParseResult(OdinValue.ofString(token.getValue()), 1);

            case BareWord -> {
                if ("true".equals(token.getValue()))
                    yield new ParseResult(OdinValue.ofBoolean(true), 1);
                if ("false".equals(token.getValue()))
                    yield new ParseResult(OdinValue.ofBoolean(false), 1);
                throw new OdinParseException(
                        ParseErrorCode.BareStringNotAllowed, token.getLine(), token.getColumn(),
                        "Unquoted string \"" + token.getValue() + "\" - use double quotes");
            }

            case NumberPrefix -> new ParseResult(parseNumber(token.getValue(), token.getLine(), token.getColumn()), 1);
            case IntegerPrefix -> new ParseResult(parseInteger(token.getValue(), token.getLine(), token.getColumn()), 1);
            case CurrencyPrefix -> new ParseResult(parseCurrency(token.getValue(), token.getLine(), token.getColumn()), 1);
            case PercentPrefix -> new ParseResult(parsePercent(token.getValue(), token.getLine(), token.getColumn()), 1);
            case ReferencePrefix -> new ParseResult(OdinValue.ofReference(token.getValue()), 1);
            case BinaryPrefix -> new ParseResult(parseBinary(token.getValue(), token.getLine(), token.getColumn()), 1);
            case DateLiteral -> new ParseResult(parseDateValue(token.getValue(), token.getLine(), token.getColumn()), 1);
            case TimeLiteral -> new ParseResult(OdinValue.ofTime(token.getValue()), 1);
            case DurationLiteral -> new ParseResult(OdinValue.ofDuration(token.getValue()), 1);
            case TimestampLiteral -> new ParseResult(OdinValue.ofTimestamp(0, token.getValue()), 1);

            case Path -> {
                // Path tokens in value position can be temporal values
                if (isDateLike(token.getValue())) {
                    try {
                        yield new ParseResult(parseDateValue(token.getValue(), token.getLine(), token.getColumn()), 1);
                    } catch (OdinParseException e) {
                        // Fall through to bare string error
                    }
                }
                if (!token.getValue().isEmpty() && token.getValue().charAt(0) == 'T' && token.getValue().indexOf(':') >= 0) {
                    yield new ParseResult(OdinValue.ofTime(token.getValue()), 1);
                }
                if (token.getValue().length() > 1 && token.getValue().charAt(0) == 'P') {
                    char second = token.getValue().charAt(1);
                    if ((second >= '0' && second <= '9') || second == 'T') {
                        yield new ParseResult(OdinValue.ofDuration(token.getValue()), 1);
                    }
                }
                throw new OdinParseException(
                        ParseErrorCode.BareStringNotAllowed, token.getLine(), token.getColumn(),
                        "Unquoted string \"" + token.getValue() + "\" - use double quotes");
            }

            case VerbPrefix -> {
                String verbName = token.getValue();
                boolean isCustom = !verbName.isEmpty() && verbName.charAt(0) == '&';
                String prefix = "%" + verbName;
                var parts = new ArrayList<String>();
                parts.add(prefix);
                int consumed = 1;
                int i = pos + 1;
                while (i < tokens.size()) {
                    var t = tokens.get(i);
                    if (t.getTokenType() == TokenType.Newline || t.getTokenType() == TokenType.Comment)
                        break;

                    String text = switch (t.getTokenType()) {
                        case ReferencePrefix -> "@" + t.getValue();
                        case IntegerPrefix -> "##" + t.getValue();
                        case NumberPrefix -> "#" + t.getValue();
                        case CurrencyPrefix -> "#$" + t.getValue();
                        case PercentPrefix -> "#%" + t.getValue();
                        case BooleanPrefix -> "?";
                        case QuotedString -> "\"" + t.getValue() + "\"";
                        case Null -> "~";
                        case Directive -> ":" + t.getValue();
                        case VerbPrefix -> "%" + t.getValue();
                        default -> t.getValue();
                    };
                    parts.add(text);
                    consumed++;
                    i++;
                }
                String rawExpr = String.join(" ", parts);
                var verb = new OdinValue.OdinVerb(rawExpr, Collections.emptyList(), isCustom);
                yield new ParseResult(verb, consumed);
            }

            case NumericLiteral -> new ParseResult(parseNumber(token.getValue(), token.getLine(), token.getColumn()), 1);

            default -> throw new OdinParseException(
                    ParseErrorCode.UnexpectedCharacter, token.getLine(), token.getColumn(),
                    "unexpected token type " + token.getTokenType() + " for value");
        };
    }

    public static ModifierResult parseModifiers(List<Token> tokens, int pos) {
        boolean required = false;
        boolean confidential = false;
        boolean deprecated = false;
        int consumed = 0;

        while (pos + consumed < tokens.size() && tokens.get(pos + consumed).getTokenType() == TokenType.Modifier) {
            String val = tokens.get(pos + consumed).getValue();
            if ("!".equals(val)) required = true;
            else if ("*".equals(val)) confidential = true;
            else if ("-".equals(val)) deprecated = true;
            else break;
            consumed++;
        }

        var modifiers = new OdinModifiers(required, confidential, deprecated, false);
        return new ModifierResult(modifiers, consumed);
    }

    // ── Internal parse helpers ──

    static OdinValue parseNumber(String raw, int line, int col) {
        if (raw == null || raw.isEmpty()) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "empty number after '#'");
        }
        if (raw.startsWith("--")) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "invalid number: " + raw);
        }

        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "invalid number: " + raw);
        }

        Byte decimalPlaces = null;
        if (raw.contains(".")) {
            String lower = raw.toLowerCase();
            int ePos = lower.indexOf('e');
            String numPart = ePos >= 0 ? raw.substring(0, ePos) : raw;
            int dotPos = numPart.indexOf('.');
            if (dotPos >= 0) {
                decimalPlaces = (byte) (numPart.length() - dotPos - 1);
            }
        }

        return new OdinValue.OdinNumber(value, decimalPlaces, raw);
    }

    static OdinValue parseInteger(String raw, int line, int col) {
        if (raw == null || raw.isEmpty()) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "empty integer after '##'");
        }

        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // Reject non-integral decimals (##4.2); allow exponent form (##1e3) and large integers.
            double parsed;
            try {
                parsed = Double.parseDouble(raw);
            } catch (NumberFormatException e2) {
                throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "invalid integer: " + raw);
            }
            if (parsed != Math.floor(parsed) || Double.isInfinite(parsed)) {
                throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col,
                        "Integer (##) value cannot have a fractional part: " + raw);
            }
            value = (long) parsed; // exponent/large form: store truncated, preserve raw
        }

        return OdinValue.ofInteger(value, raw);
    }

    static OdinValue parseCurrency(String raw, int line, int col) {
        String numPart;
        String currencyCode = null;
        int colonPos = raw.indexOf(':');
        if (colonPos >= 0) {
            numPart = raw.substring(0, colonPos);
            currencyCode = raw.substring(colonPos + 1).toUpperCase();
        } else {
            numPart = raw;
        }

        double value;
        try {
            value = Double.parseDouble(numPart);
        } catch (NumberFormatException e) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "invalid currency: " + raw);
        }

        int ePos = numPart.toLowerCase().indexOf('e');
        String dpPart = ePos >= 0 ? numPart.substring(0, ePos) : numPart;
        int dotPos = dpPart.indexOf('.');
        byte decimalPlaces = dotPos >= 0 ? (byte) (dpPart.length() - dotPos - 1) : (byte) 2;

        return new OdinValue.OdinCurrency(value, decimalPlaces, currencyCode, raw);
    }

    static OdinValue parsePercent(String raw, int line, int col) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new OdinParseException(ParseErrorCode.InvalidTypePrefix, line, col, "invalid percent: " + raw);
        }

        return new OdinValue.OdinPercent(value, raw);
    }

    static OdinValue parseBinary(String raw, int line, int col) {
        if (raw == null || raw.isEmpty()) {
            return OdinValue.ofBinary(new byte[0]);
        }

        int colonPos = raw.indexOf(':');
        if (colonPos >= 0) {
            String algorithm = raw.substring(0, colonPos);
            String b64Data = raw.substring(colonPos + 1);
            validateBase64(b64Data, line, col);
            byte[] data = base64Decode(b64Data);
            return OdinValue.ofBinary(data, algorithm);
        } else {
            validateBase64(raw, line, col);
            byte[] data = base64Decode(raw);
            return OdinValue.ofBinary(data);
        }
    }

    static void validateBase64(String input, int line, int col) {
        boolean paddingStarted = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
                (ch >= '0' && ch <= '9') || ch == '+' || ch == '/') {
                if (paddingStarted) {
                    throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col, "Invalid Base64 padding");
                }
            } else if (ch == '=') {
                paddingStarted = true;
            } else if (ch == '\n' || ch == '\r') {
                // Allow newlines
            } else {
                throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col,
                        "Invalid Base64 character at position " + i);
            }
        }
    }

    static byte[] base64Decode(String input) {
        try {
            return Base64.getDecoder().decode(input);
        } catch (IllegalArgumentException e) {
            // Fallback lenient decoder
            var output = new ArrayList<Byte>(input.length() * 3 / 4);
            int buffer = 0;
            int bits = 0;

            for (int i = 0; i < input.length(); i++) {
                char ch = input.charAt(i);
                int val;
                if (ch >= 'A' && ch <= 'Z') val = ch - 'A';
                else if (ch >= 'a' && ch <= 'z') val = ch - 'a' + 26;
                else if (ch >= '0' && ch <= '9') val = ch - '0' + 52;
                else if (ch == '+') val = 62;
                else if (ch == '/') val = 63;
                else continue;

                buffer = (buffer << 6) | val;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    output.add((byte) (buffer >> bits));
                    buffer &= (1 << bits) - 1;
                }
            }

            byte[] result = new byte[output.size()];
            for (int i = 0; i < output.size(); i++) result[i] = output.get(i);
            return result;
        }
    }

    static OdinValue parseDateValue(String raw, int line, int col) {
        String[] parts = raw.split("-");
        if (parts.length != 3) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col, "invalid date: " + raw);
        }

        int year;
        byte month;
        byte day;
        try {
            year = Integer.parseInt(parts[0]);
            month = Byte.parseByte(parts[1]);
            day = Byte.parseByte(parts[2]);
        } catch (NumberFormatException e) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col, "invalid date: " + raw);
        }

        if (month < 1 || month > 12) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col,
                    "Invalid month " + month + " in date " + raw);
        }

        byte maxDay = daysInMonth(year, month);
        if (day < 1 || day > maxDay) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, line, col,
                    "Invalid day " + day + " for month " + month + " in date " + raw);
        }

        return new OdinValue.OdinDate(year, month, day, raw);
    }

    static boolean isDateLike(String s) {
        if (s.length() < 10) return false;
        if (s.charAt(4) != '-') return false;
        if (s.charAt(7) != '-') return false;
        for (int i = 0; i < 4; i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    static byte daysInMonth(int year, byte month) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> (byte) 31;
            case 4, 6, 9, 11 -> (byte) 30;
            case 2 -> isLeapYear(year) ? (byte) 29 : (byte) 28;
            default -> (byte) 0;
        };
    }

    static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
