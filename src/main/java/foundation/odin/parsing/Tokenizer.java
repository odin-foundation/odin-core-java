package foundation.odin.parsing;

import foundation.odin.types.OdinErrors;
import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinErrors.ParseErrorCode;
import foundation.odin.types.OdinOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Tokenizer {
    private Tokenizer() {}

    public static List<Token> tokenize(String source, OdinOptions.ParseOptions options) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(options);

        if (source.length() > options.getMaxDocumentSize()) {
            throw new OdinParseException(ParseErrorCode.MaximumDocumentSizeExceeded, 1, 1);
        }

        // Strip BOM if present
        String input = source;
        if (!input.isEmpty() && input.charAt(0) == '\uFEFF') {
            input = input.substring(1);
        }

        var state = new State(input);
        int estimatedSize = input.length() / 12 + 16;
        var tokens = new ArrayList<Token>(estimatedSize);

        while (!state.isAtEnd()) {
            var token = nextToken(state);
            if (token != null) {
                tokens.add(token);
            }
        }

        tokens.add(new Token(TokenType.Eof, state.pos, state.pos, state.line, state.column, ""));
        return tokens;
    }

    // ── State ──

    private static final class State {
        final String source;
        int pos;
        int line;
        int column;

        State(String source) {
            this.source = source;
            this.pos = 0;
            this.line = 1;
            this.column = 1;
        }

        boolean isAtEnd() { return pos >= source.length(); }

        char peek() { return pos < source.length() ? source.charAt(pos) : '\0'; }

        char peekAt(int offset) {
            int idx = pos + offset;
            return idx < source.length() ? source.charAt(idx) : '\0';
        }

        boolean hasCharAt(int offset) { return (pos + offset) < source.length(); }

        char advance() {
            char ch = source.charAt(pos);
            pos++;
            if (ch == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            return ch;
        }

        void skipWhitespace() {
            while (!isAtEnd()) {
                char ch = peek();
                if (ch == ' ' || ch == '\t') {
                    advance();
                } else {
                    break;
                }
            }
        }

        Token makeToken(TokenType type, int start, int startLine, int startCol, String value) {
            return new Token(type, start, pos, startLine, startCol, value);
        }
    }

    // ── Main dispatch ──

    private static Token nextToken(State state) {
        state.skipWhitespace();

        if (state.isAtEnd()) return null;

        char ch = state.peek();
        int startLine = state.line;
        int startCol = state.column;
        int startPos = state.pos;

        switch (ch) {
            case '\n':
                state.advance();
                return new Token(TokenType.Newline, startPos, state.pos, startLine, startCol, "\n");

            case '\r':
                state.advance();
                if (!state.isAtEnd() && state.peek() == '\n') state.advance();
                return new Token(TokenType.Newline, startPos, state.pos, startLine, startCol, "\n");

            case ';':
                return scanComment(state);

            case '{':
                return scanHeader(state);

            case '=':
                state.advance();
                return new Token(TokenType.Equals, startPos, state.pos, startLine, startCol, "=");

            case '"':
                return scanQuotedString(state);

            case '~':
                state.advance();
                return new Token(TokenType.Null, startPos, state.pos, startLine, startCol, "~");

            case '@':
                return scanAt(state);

            case '^':
                return scanBinary(state);

            case '#':
                return scanHash(state);

            case '?':
                state.advance();
                return new Token(TokenType.BooleanPrefix, startPos, state.pos, startLine, startCol, "?");

            case '%':
                return scanVerb(state);

            case '!':
                state.advance();
                return new Token(TokenType.Modifier, startPos, state.pos, startLine, startCol, "!");

            case '*':
                state.advance();
                return new Token(TokenType.Modifier, startPos, state.pos, startLine, startCol, "*");

            case '-':
                return scanDash(state);

            case ',':
                state.advance();
                return new Token(TokenType.Comma, startPos, state.pos, startLine, startCol, ",");

            case ':':
                return scanDirective(state);

            case '|':
                state.advance();
                return new Token(TokenType.Pipe, startPos, state.pos, startLine, startCol, "|");

            case '[':
                return scanBracketPath(state);

            case '&':
                return scanExtensionPath(state);

            case '$':
                return scanMetaPath(state);

            default:
                if (ch >= '0' && ch <= '9') {
                    if (looksLikeDate(state))
                        return scanDateOrTimestamp(state);
                    else
                        return scanNumber(state);
                }
                if (isIdentifierStart(ch))
                    return scanIdentifier(state);
                return scanBareValue(state);
        }
    }

    // ── Scanning methods ──

    private static Token scanComment(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip ';'
        while (!state.isAtEnd() && state.peek() != '\n') {
            state.advance();
        }
        String value = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.Comment, start, startLine, startCol, value);
    }

    private static Token scanQuotedString(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip opening '"'

        var value = new StringBuilder();
        boolean hasEscapes = false;

        while (!state.isAtEnd()) {
            char c = state.source.charAt(state.pos);
            if (c == '"') {
                state.advance(); // skip closing '"'
                if (hasEscapes) {
                    return state.makeToken(TokenType.QuotedString, start, startLine, startCol, value.toString());
                }
                String raw = state.source.substring(start + 1, state.pos - 1);
                return state.makeToken(TokenType.QuotedString, start, startLine, startCol, raw);
            }
            if (c == '\\') {
                hasEscapes = true;
                state.advance();
                if (state.isAtEnd()) {
                    throw new OdinParseException(ParseErrorCode.UnterminatedString, startLine, startCol);
                }
                char esc = state.advance();
                switch (esc) {
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case '\\': value.append('\\'); break;
                    case '"': value.append('"'); break;
                    case '/': value.append('/'); break;
                    case '0': value.append('\0'); break;
                    case 'u': {
                        char unicodeChar = scanUnicodeEscape(state, 4, startLine, startCol);
                        int codePoint = unicodeChar;
                        // Check for surrogate pair
                        if (codePoint >= 0xD800 && codePoint <= 0xDBFF) {
                            if (!state.isAtEnd() && state.peek() == '\\' &&
                                state.hasCharAt(1) && state.peekAt(1) == 'u') {
                                state.advance(); // skip backslash
                                state.advance(); // skip u
                                char lowChar = scanUnicodeEscape(state, 4, startLine, startCol);
                                int lowCode = lowChar;
                                if (lowCode >= 0xDC00 && lowCode <= 0xDFFF) {
                                    int combined = 0x10000 + ((codePoint - 0xD800) << 10) + (lowCode - 0xDC00);
                                    value.append(Character.toChars(combined));
                                }
                            }
                        } else {
                            value.append(unicodeChar);
                        }
                        break;
                    }
                    case 'U': {
                        String unicodeStr = scanUnicodeEscapeString(state, 8, startLine, startCol);
                        value.append(unicodeStr);
                        break;
                    }
                    default:
                        throw new OdinParseException(
                                ParseErrorCode.InvalidEscapeSequence,
                                state.line, state.column,
                                "unknown escape: \\" + esc);
                }
            } else if (c == '\n') {
                throw new OdinParseException(ParseErrorCode.UnterminatedString, startLine, startCol);
            } else {
                value.append(c);
                state.advance();
            }
        }

        throw new OdinParseException(ParseErrorCode.UnterminatedString, startLine, startCol);
    }

    private static char scanUnicodeEscape(State state, int digits, int startLine, int startCol) {
        var hex = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            if (state.isAtEnd()) {
                throw new OdinParseException(
                        ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                        "incomplete unicode escape");
            }
            hex.append(state.advance());
        }

        int code;
        try {
            code = Integer.parseInt(hex.toString(), 16);
        } catch (NumberFormatException e) {
            throw new OdinParseException(
                    ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                    "invalid hex in unicode escape: \\u" + hex);
        }

        // For surrogate range, return the raw char value (will be combined later)
        if (code >= 0xD800 && code <= 0xDFFF)
            return (char) code;

        if (code < 0 || code > 0x10FFFF) {
            throw new OdinParseException(
                    ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                    String.format("invalid unicode code point: U+%04X", code));
        }

        return (char) code;
    }

    private static String scanUnicodeEscapeString(State state, int digits, int startLine, int startCol) {
        var hex = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            if (state.isAtEnd()) {
                throw new OdinParseException(
                        ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                        "unterminated unicode escape");
            }
            hex.append(state.peek());
            state.advance();
        }

        int code;
        try {
            code = Integer.parseInt(hex.toString(), 16);
        } catch (NumberFormatException e) {
            throw new OdinParseException(
                    ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                    "invalid hex in unicode escape: \\U" + hex);
        }

        if (code < 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
            throw new OdinParseException(
                    ParseErrorCode.InvalidEscapeSequence, startLine, startCol,
                    String.format("invalid unicode code point: U+%X", code));
        }

        return new String(Character.toChars(code));
    }

    private static Token scanHeader(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '{'

        var value = new StringBuilder();
        while (!state.isAtEnd()) {
            char c = state.source.charAt(state.pos);
            if (c == '}') {
                state.advance(); // skip '}'
                String headerValue = value.toString();

                // Validate bracket usage in headers
                int bracketStart = headerValue.indexOf('[');
                if (bracketStart >= 0 && !headerValue.startsWith("$table") && !headerValue.startsWith("table.")) {
                    int bracketEnd = headerValue.indexOf(']');
                    if (bracketEnd < 0) {
                        throw new OdinParseException(ParseErrorCode.InvalidArrayIndex, startLine, startCol, headerValue);
                    }
                    String bracketContent = headerValue.substring(bracketStart + 1, bracketEnd);
                    boolean valid = bracketContent.isEmpty()
                            || isAllDigits(bracketContent)
                            || isValidFieldList(bracketContent);
                    if (!valid) {
                        throw new OdinParseException(ParseErrorCode.InvalidArrayIndex, startLine, startCol, headerValue);
                    }
                }

                return state.makeToken(TokenType.Header, start, startLine, startCol, headerValue);
            }
            if (c == '\n') {
                throw new OdinParseException(ParseErrorCode.InvalidHeaderSyntax, startLine, startCol);
            }
            value.append(c);
            state.advance();
        }

        throw new OdinParseException(ParseErrorCode.InvalidHeaderSyntax, startLine, startCol);
    }

    private static Token scanIdentifier(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        char first = state.peek();

        // Check for time literal: T + digit
        if (first == 'T' && state.hasCharAt(1) && state.peekAt(1) >= '0' && state.peekAt(1) <= '9') {
            return scanTime(state);
        }

        // Check for duration literal: P + (digit|T)
        if (first == 'P' && state.hasCharAt(1)) {
            char next = state.peekAt(1);
            if ((next >= '0' && next <= '9') || next == 'T') {
                return scanDuration(state);
            }
        }

        boolean inBracket = false;
        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '.') {
                state.advance();
            } else if (c == '[') {
                inBracket = true;
                state.advance();
            } else if (c == ']') {
                inBracket = false;
                state.advance();
            } else if (c == '-' && inBracket) {
                state.advance();
            } else {
                break;
            }
        }

        String identValue = state.source.substring(start, state.pos);

        // Check for negative array indices -> P003
        if (identValue.contains("[-")) {
            throw new OdinParseException(
                    ParseErrorCode.InvalidArrayIndex, startLine, startCol,
                    "Negative array index in path: " + identValue);
        }

        // Check for special bare words
        if ("true".equals(identValue) || "false".equals(identValue)) {
            return state.makeToken(TokenType.BooleanLiteral, start, startLine, startCol, identValue);
        }

        return state.makeToken(TokenType.Path, start, startLine, startCol, identValue);
    }

    // Top-level metadata path: $.foo.bar (canonical-form output, e.g. $.id = "a").
    private static Token scanMetaPath(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '$'

        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '.' ||
                c == '[' || c == ']') {
                state.advance();
            } else {
                break;
            }
        }

        String value = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.Path, start, startLine, startCol, value);
    }

    private static Token scanExtensionPath(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '&'

        // Scan the dotted identifier after &
        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '.') {
                state.advance();
            } else {
                break;
            }
        }

        String value = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.Path, start, startLine, startCol, value);
    }

    private static Token scanBracketPath(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        boolean inBracket = false;
        while (!state.isAtEnd()) {
            char c = state.peek();
            if (c == '[') {
                inBracket = true;
                state.advance();
            } else if (c == ']') {
                inBracket = false;
                state.advance();
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                       (c >= '0' && c <= '9') || c == '_' || c == '.') {
                state.advance();
            } else if (c == '-' && inBracket) {
                state.advance();
            } else {
                break;
            }
        }

        String pathValue = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.Path, start, startLine, startCol, pathValue);
    }

    private static Token scanBareValue(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        while (!state.isAtEnd()) {
            char c = state.peek();
            if (c == '\n' || c == '\r' || c == ';')
                break;

            if (c == ' ' || c == '\t') {
                int savedPos = state.pos;
                int savedLine = state.line;
                int savedCol = state.column;
                state.skipWhitespace();
                if (state.isAtEnd() || state.peek() == '\n' || state.peek() == '\r' || state.peek() == ';') {
                    break;
                }
                state.pos = savedPos;
                state.line = savedLine;
                state.column = savedCol;
                state.advance();
            } else {
                state.advance();
            }
        }

        String bareValue = state.source.substring(start, state.pos).stripTrailing();
        return state.makeToken(TokenType.BareWord, start, startLine, startCol, bareValue);
    }

    private static Token scanNumber(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        if (!state.isAtEnd() && state.peek() == '-') {
            state.advance();
        }

        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                state.advance();
            } else {
                break;
            }
        }

        String numValue = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.NumericLiteral, start, startLine, startCol, numValue);
    }

    private static Token scanDateOrTimestamp(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        while (!state.isAtEnd()) {
            char c = state.peek();
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t' || c == ';')
                break;
            state.advance();
        }

        String dtValue = state.source.substring(start, state.pos);

        if (dtValue.indexOf('T') >= 0) {
            return state.makeToken(TokenType.TimestampLiteral, start, startLine, startCol, dtValue);
        }
        return state.makeToken(TokenType.DateLiteral, start, startLine, startCol, dtValue);
    }

    private static Token scanTime(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= '0' && c <= '9') || c == 'T' || c == ':' || c == '.') {
                state.advance();
            } else {
                break;
            }
        }

        String timeValue = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.TimeLiteral, start, startLine, startCol, timeValue);
    }

    private static Token scanDuration(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        while (!state.isAtEnd()) {
            char c = state.peek();
            if (c == 'P' || c == 'T' || c == 'Y' || c == 'M' || c == 'W' ||
                c == 'D' || c == 'H' || c == 'S' || (c >= '0' && c <= '9') || c == '.') {
                state.advance();
            } else {
                break;
            }
        }

        String durValue = state.source.substring(start, state.pos);
        return state.makeToken(TokenType.DurationLiteral, start, startLine, startCol, durValue);
    }

    private static Token scanDirective(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip ':'

        var name = new StringBuilder();
        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_') {
                name.append(state.advance());
            } else {
                break;
            }
        }

        return state.makeToken(TokenType.Directive, start, startLine, startCol, name.toString());
    }

    private static Token scanAt(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '@'

        int wordStart = state.pos;
        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '.' ||
                c == '[' || c == ']' || c == '$' || c == ':' || c == '-' || c == '@') {
                state.advance();
            } else {
                break;
            }
        }
        String word = state.source.substring(wordStart, state.pos);

        // Normalize leading zeros in array indices: [007] -> [7]
        if (word.contains("[")) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < word.length()) {
                if (word.charAt(i) == '[') {
                    sb.append('[');
                    i++;
                    int idxStart = i;
                    while (i < word.length() && Character.isDigit(word.charAt(i))) {
                        i++;
                    }
                    if (i > idxStart && i < word.length() && word.charAt(i) == ']') {
                        long idx = Long.parseLong(word.substring(idxStart, i));
                        sb.append(idx);
                    } else {
                        sb.append(word, idxStart, i);
                    }
                } else {
                    sb.append(word.charAt(i));
                    i++;
                }
            }
            word = sb.toString();
        }

        switch (word) {
            case "import": {
                state.skipWhitespace();
                int restStart = state.pos;
                while (!state.isAtEnd() && state.peek() != '\n' && state.peek() != '\r') {
                    state.advance();
                }
                String rest = state.source.substring(restStart, state.pos).stripTrailing();
                int commentIdx = findCommentStart(rest);
                if (commentIdx >= 0)
                    rest = rest.substring(0, commentIdx).stripTrailing();
                return state.makeToken(TokenType.Import, start, startLine, startCol, rest);
            }
            case "schema": {
                state.skipWhitespace();
                int restStart = state.pos;
                while (!state.isAtEnd() && state.peek() != '\n' && state.peek() != '\r') {
                    state.advance();
                }
                String rest = state.source.substring(restStart, state.pos).stripTrailing();
                int commentIdx = findCommentStart(rest);
                if (commentIdx >= 0)
                    rest = rest.substring(0, commentIdx).stripTrailing();
                return state.makeToken(TokenType.Schema, start, startLine, startCol, rest);
            }
            case "if": {
                state.skipWhitespace();
                int restStart = state.pos;
                while (!state.isAtEnd() && state.peek() != '\n' && state.peek() != '\r') {
                    state.advance();
                }
                String rest = state.source.substring(restStart, state.pos).stripTrailing();
                int commentIdx = findCommentStart(rest);
                if (commentIdx >= 0)
                    rest = rest.substring(0, commentIdx).stripTrailing();
                return state.makeToken(TokenType.Conditional, start, startLine, startCol, rest);
            }
            case "": {
                // Bare '@' at column 1 is always invalid
                if (startCol == 1) {
                    throw new OdinParseException(
                            ParseErrorCode.UnexpectedCharacter, startLine, startCol,
                            "Unexpected character: @");
                }
                // Bare '@' followed by '#' is invalid
                if (!state.isAtEnd() && state.peek() == '#') {
                    throw new OdinParseException(
                            ParseErrorCode.UnexpectedCharacter, startLine, startCol,
                            "Unexpected character: @#");
                }
                // Valid in transform context as "current item" reference
                return state.makeToken(TokenType.ReferencePrefix, start, startLine, startCol, "");
            }
            default: {
                // It's a reference: @path.to.thing (even at column 1)
                return state.makeToken(TokenType.ReferencePrefix, start, startLine, startCol, word);
            }
        }
    }

    private static Token scanBinary(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '^'

        int valStart = state.pos;
        while (!state.isAtEnd()) {
            char c = state.peek();
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t' || c == ';')
                break;
            state.advance();
        }
        String binValue = state.source.substring(valStart, state.pos);
        return new Token(TokenType.BinaryPrefix, start, state.pos, startLine, startCol, binValue);
    }

    private static Token scanHash(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '#'

        if (!state.isAtEnd()) {
            char next = state.peek();
            switch (next) {
                case '#': {
                    state.advance();
                    var num = scanNumber(state);
                    return new Token(TokenType.IntegerPrefix, start, num.getEnd(), startLine, startCol, num.getValue());
                }
                case '$': {
                    state.advance();
                    var num = scanNumber(state);
                    String hashValue = num.getValue();
                    // Check for currency code after colon
                    if (!state.isAtEnd() && state.peek() == ':') {
                        state.advance();
                        int codeStart = state.pos;
                        while (!state.isAtEnd()) {
                            char c = state.peek();
                            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
                                state.advance();
                            else
                                break;
                        }
                        String code = state.source.substring(codeStart, state.pos);
                        hashValue = hashValue + ":" + code;
                    }
                    return new Token(TokenType.CurrencyPrefix, start, state.pos, startLine, startCol, hashValue);
                }
                case '%': {
                    state.advance();
                    var num = scanNumber(state);
                    return new Token(TokenType.PercentPrefix, start, num.getEnd(), startLine, startCol, num.getValue());
                }
                default:
                    if ((next >= '0' && next <= '9') || next == '-' || next == '.') {
                        var num = scanNumber(state);
                        return new Token(TokenType.NumberPrefix, start, num.getEnd(), startLine, startCol, num.getValue());
                    }
                    throw new OdinParseException(
                            ParseErrorCode.InvalidTypePrefix, startLine, startCol,
                            "expected number after '#'");
            }
        }

        throw new OdinParseException(
                ParseErrorCode.InvalidTypePrefix, startLine, startCol,
                "expected number after '#'");
    }

    private static Token scanVerb(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;
        state.advance(); // skip '%'

        int nameStart = state.pos;
        // Check for custom verb prefix '&'
        if (!state.isAtEnd() && state.peek() == '&') {
            state.advance();
        }
        while (!state.isAtEnd()) {
            char c = state.peek();
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '_' || c == '.') {
                state.advance();
            } else {
                break;
            }
        }
        String verbName = state.source.substring(nameStart, state.pos);
        return new Token(TokenType.VerbPrefix, start, state.pos, startLine, startCol, verbName);
    }

    private static Token scanDash(State state) {
        int start = state.pos;
        int startLine = state.line;
        int startCol = state.column;

        // Check for document separator '---'
        if (state.hasCharAt(1) && state.peekAt(1) == '-' &&
            state.hasCharAt(2) && state.peekAt(2) == '-') {
            state.advance();
            state.advance();
            state.advance();
            return new Token(TokenType.DocumentSeparator, start, state.pos, startLine, startCol, "---");
        }

        // Check if this is a negative number (followed by digit)
        // But NOT if it's followed by a date pattern (YYYY-MM-DD) — then it's a deprecated modifier
        if (state.hasCharAt(1)) {
            char next = state.peekAt(1);
            if (next >= '0' && next <= '9') {
                // Check if this looks like -YYYY-MM-DD (deprecated modifier + date)
                if (looksLikeDateAt(state, state.pos + 1)) {
                    // It's a deprecated modifier, not a negative number
                    state.advance();
                    return new Token(TokenType.Modifier, start, state.pos, startLine, startCol, "-");
                }
                return scanBareValue(state);
            }
        }

        // Otherwise it's a deprecated modifier
        state.advance();
        return new Token(TokenType.Modifier, start, state.pos, startLine, startCol, "-");
    }

    // ── Helpers ──

    private static boolean looksLikeDate(State state) {
        if (state.pos + 10 > state.source.length()) return false;

        String s = state.source;
        int p = state.pos;

        for (int i = 0; i < 4; i++) {
            if (s.charAt(p + i) < '0' || s.charAt(p + i) > '9') return false;
        }

        return s.charAt(p + 4) == '-'
            && s.charAt(p + 5) >= '0' && s.charAt(p + 5) <= '9'
            && s.charAt(p + 6) >= '0' && s.charAt(p + 6) <= '9'
            && s.charAt(p + 7) == '-'
            && s.charAt(p + 8) >= '0' && s.charAt(p + 8) <= '9'
            && s.charAt(p + 9) >= '0' && s.charAt(p + 9) <= '9';
    }

    private static boolean looksLikeDateAt(State state, int pos) {
        if (pos + 10 > state.source.length()) return false;
        String s = state.source;
        for (int i = 0; i < 4; i++) {
            if (s.charAt(pos + i) < '0' || s.charAt(pos + i) > '9') return false;
        }
        return s.charAt(pos + 4) == '-'
            && s.charAt(pos + 5) >= '0' && s.charAt(pos + 5) <= '9'
            && s.charAt(pos + 6) >= '0' && s.charAt(pos + 6) <= '9'
            && s.charAt(pos + 7) == '-'
            && s.charAt(pos + 8) >= '0' && s.charAt(pos + 8) <= '9'
            && s.charAt(pos + 9) >= '0' && s.charAt(pos + 9) <= '9';
    }

    private static boolean isIdentifierStart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean isValidFieldList(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                  (c >= '0' && c <= '9') || c == '_' || c == ',' || c == ' ')) {
                return false;
            }
        }
        return true;
    }

    private static int findCommentStart(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inQuotes = !inQuotes;
            else if (ch == ';' && !inQuotes) return i;
        }
        return -1;
    }
}
