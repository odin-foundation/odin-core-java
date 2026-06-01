package foundation.odin.parsing;

import foundation.odin.types.*;
import foundation.odin.types.OdinDirective.DirectiveValue;
import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinErrors.ParseErrorCode;
import foundation.odin.types.OdinOptions.ParseOptions;

import java.util.*;

public final class OdinParser {
    private OdinParser() {}

    public static OdinDocument parse(String source, ParseOptions options) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        var opts = options != null ? options : ParseOptions.DEFAULT;
        var tokens = Tokenizer.tokenize(source, opts);
        return parseTokens(tokens, source, opts);
    }

    public static List<OdinDocument> parseMulti(String source, ParseOptions options) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        var opts = options != null ? options : ParseOptions.DEFAULT;
        var tokens = Tokenizer.tokenize(source, opts);
        return parseTokensMulti(tokens, source, opts);
    }

    public static OdinDocument parseTokens(List<Token> tokens, String source, ParseOptions options) {
        var state = new ParserState(tokens, options, source);
        var docs = parseDocuments(state);
        if (docs.isEmpty()) return OdinDocument.empty();
        return docs.get(docs.size() - 1);
    }

    public static List<OdinDocument> parseTokensMulti(List<Token> tokens, String source, ParseOptions options) {
        var state = new ParserState(tokens, options, source);
        return parseDocuments(state);
    }

    // ── Parser State ──

    private static final class ParserState {
        final List<Token> tokens;
        final ParseOptions options;
        final String source;
        int pos;
        String currentHeader;
        String lastAbsoluteHeader; // Tracks the last non-relative header for resolving relative headers

        ParserState(List<Token> tokens, ParseOptions options, String source) {
            this.tokens = tokens;
            this.options = options;
            this.source = source != null ? source : "";
            this.pos = 0;
            this.currentHeader = null;
            this.lastAbsoluteHeader = null;
        }

        boolean isAtEnd() {
            return pos >= tokens.size() || tokens.get(pos).getTokenType() == TokenType.Eof;
        }

        Token peek() {
            return pos < tokens.size() ? tokens.get(pos) : null;
        }

        Token current() {
            return tokens.get(pos);
        }

        Token advance() {
            return tokens.get(pos++);
        }

        void skipNewlines() {
            while (!isAtEnd()) {
                var tt = tokens.get(pos).getTokenType();
                if (tt == TokenType.Newline)
                    pos++;
                else if (tt == TokenType.Comment) {
                    if (!options.isPreserveComments())
                        pos++;
                    else
                        break;
                }
                else
                    break;
            }
        }

        boolean isAssignmentLine() {
            if (isAtEnd()) return false;
            var tok = tokens.get(pos);
            if (tok.getTokenType() != TokenType.Path && tok.getTokenType() != TokenType.BareWord)
                return false;
            int nextPos = pos + 1;
            if (nextPos >= tokens.size()) return false;
            return tokens.get(nextPos).getTokenType() == TokenType.Equals;
        }
    }

    // ── Document Parsing ──

    private static List<OdinDocument> parseDocuments(ParserState state) {
        var documents = new ArrayList<OdinDocument>();

        while (true) {
            var doc = parseSingleDocument(state);
            documents.add(doc);

            state.skipNewlines();
            var peek = state.peek();
            if (!state.isAtEnd() && peek != null && peek.getTokenType() == TokenType.DocumentSeparator) {
                state.advance();
                state.skipNewlines();
                state.currentHeader = null;
            } else {
                break;
            }
        }

        return documents;
    }

    private static OdinDocument parseSingleDocument(ParserState state) {
        var metadata = new OrderedMap<String, OdinValue>();
        var assignments = new OrderedMap<String, OdinValue>();
        var modifiers = new OrderedMap<String, OdinModifiers>();
        var imports = new ArrayList<OdinImport>();
        var schemas = new ArrayList<OdinSchemaRef>();
        var conditionals = new ArrayList<OdinConditional>();
        var comments = new ArrayList<OdinComment>();
        boolean inMetadata = false;
        var arrayIndices = new HashMap<String, List<Integer>>();

        state.skipNewlines();

        while (!state.isAtEnd()) {
            var token = state.current();

            if (token.getTokenType() == TokenType.DocumentSeparator)
                break;

            switch (token.getTokenType()) {
                case Header -> {
                    var result = parseHeaderToken(state, inMetadata, metadata, assignments);
                    inMetadata = result;
                }

                case Import -> parseImportToken(state, imports);

                case Schema -> parseSchemaToken(state, schemas);

                case Conditional -> parseConditionalToken(state, conditionals);

                case Path, BooleanLiteral ->
                    parseAssignment(state, inMetadata, metadata, assignments, modifiers, arrayIndices);

                case Directive -> {
                    // Bare segment-directive line (e.g. `:loop vehicles`, `:counter idx`) →
                    // synthetic `<header>._<name>` assignment, mirroring `_name = "..."`.
                    if (state.currentHeader != null && isBareDirective(token.getValue())) {
                        parseBareDirective(state, assignments);
                    } else {
                        state.advance();
                    }
                }

                case MultilineString -> {
                    // Bare `"""..."""` body line under a segment → synthetic
                    // `<header>._literalBody` assignment for the transform layer's :literal.
                    if (state.currentHeader != null) {
                        parseBareLiteralBlock(state, assignments);
                    } else {
                        state.advance();
                    }
                }

                case Newline, Comment -> {
                    boolean wasComment = token.getTokenType() == TokenType.Comment;
                    boolean wasNewline = !wasComment;
                    if (wasComment && state.options.isPreserveComments()) {
                        var text = token.getValue();
                        if (text.startsWith(";")) text = text.substring(1).trim();
                        comments.add(new OdinComment(text, null, token.getLine()));
                    }
                    state.advance();
                    if (wasNewline && inMetadata && state.currentHeader == null) {
                        var nextPeek = state.peek();
                        if (!state.isAtEnd() && nextPeek != null &&
                            nextPeek.getTokenType() == TokenType.Newline) {
                            inMetadata = false;
                        }
                    }
                }

                case ReferencePrefix -> {
                    // A reference prefix at top level (not in a value position) is invalid
                    // unless it's being consumed by tabular mode
                    throw new OdinParseException(ParseErrorCode.UnexpectedCharacter,
                            token.getLine(), token.getColumn(),
                            "Unexpected character: @" + token.getValue());
                }

                default -> state.advance();
            }
        }

        return new OdinDocument(metadata, assignments, modifiers,
                imports, schemas, conditionals, comments.isEmpty() ? null : comments);
    }

    // ── Bare Segment-Directive Lines ──

    private static final Set<String> BARE_DIRECTIVES =
            Set.of("loop", "counter", "from", "if", "elif", "else", "literal");

    private static boolean isBareDirective(String name) {
        return BARE_DIRECTIVES.contains(name);
    }

    // Parse `:name rest-of-line` into a synthetic `<header>._name = "rest"` assignment.
    private static void parseBareDirective(ParserState state, OrderedMap<String, OdinValue> assignments) {
        String name = state.current().getValue();
        state.advance(); // consume directive token

        String value = "true";
        if (!state.isAtEnd()) {
            var t = state.current();
            var tt = t.getTokenType();
            if (tt != TokenType.Newline && tt != TokenType.Comment) {
                int valStart = t.getStart();
                int valEnd = t.getEnd();
                while (!state.isAtEnd()) {
                    var ct = state.current();
                    var ctt = ct.getTokenType();
                    if (ctt == TokenType.Newline || ctt == TokenType.Comment) break;
                    valEnd = ct.getEnd();
                    state.advance();
                }
                if (valStart >= 0 && valEnd <= state.source.length() && valEnd > valStart) {
                    value = state.source.substring(valStart, valEnd);
                }
            }
        }

        // Repeated `:loop` lines on one segment each get a distinct path so all survive.
        String key = "_" + name;
        if (name.equals("loop")) {
            String base = state.currentHeader + "._loop";
            int n = 1;
            while (assignments.containsKey(n == 1 ? base : base + n)) n++;
            key = n == 1 ? "_loop" : "_loop" + n;
        }
        String fullPath = state.currentHeader + "." + key;
        if (!assignments.containsKey(fullPath)) {
            assignments.set(fullPath, OdinValue.ofString(value));
        }
    }

    // Parse a bare `"""..."""` body line into a synthetic `<header>._literalBody`
    // assignment, pairing with the segment's `:literal` directive.
    private static void parseBareLiteralBlock(ParserState state, OrderedMap<String, OdinValue> assignments) {
        String value = state.current().getValue();
        state.advance(); // consume multiline token

        String fullPath = state.currentHeader + "._literalBody";
        if (!assignments.containsKey(fullPath)) {
            assignments.set(fullPath, OdinValue.ofString(value));
        }
    }

    // ── Header Handling ──

    private static boolean parseHeaderToken(
            ParserState state,
            boolean inMetadata,
            OrderedMap<String, OdinValue> metadata,
            OrderedMap<String, OdinValue> assignments) {
        String headerValue = state.current().getValue();
        state.advance();
        state.skipNewlines();

        if ("$".equals(headerValue)) {
            state.currentHeader = null;
            return true; // inMetadata = true
        } else if (headerValue.startsWith("$table.") || headerValue.startsWith("$.table.") ||
                   headerValue.startsWith("table.")) {
            state.currentHeader = null;
            String tablePart;
            if (headerValue.startsWith("$."))
                tablePart = headerValue.substring(2);
            else if (headerValue.startsWith("$"))
                tablePart = headerValue.substring(1);
            else
                tablePart = headerValue;
            parseTableData(state, tablePart, metadata);
            return true;
        } else if (headerValue.isEmpty()) {
            state.currentHeader = null;
            return false;
        } else if (headerValue.charAt(0) == '$') {
            state.currentHeader = headerValue.substring(1);
            return true;
        } else if (headerValue.charAt(0) == '@') {
            state.currentHeader = headerValue.substring(1);
            return false;
        } else if (parseInlineHeaderDirective(headerValue) != null) {
            // {Segment :type "value"} / {Segment :if "expr"} / {Segment[] :loop path} → synthetic <path>._<name>
            var directive = parseInlineHeaderDirective(headerValue);
            String path = directive[0];
            if (path.startsWith(".") && state.lastAbsoluteHeader != null) {
                path = state.lastAbsoluteHeader + path;
            }
            state.currentHeader = path;
            if (!path.startsWith(".")) state.lastAbsoluteHeader = path;
            assignments.set(path + "._" + directive[1], OdinValue.ofString(directive[2]));
            return false;
        } else if (headerValue.contains("[] :")) {
            state.currentHeader = null;
            // Resolve relative tabular headers (`.subarr[] : ~`) against the last
            // absolute header so rows assign under the parent record, not root.
            String resolved = headerValue;
            if (resolved.startsWith(".") && state.lastAbsoluteHeader != null) {
                resolved = state.lastAbsoluteHeader + headerValue;
            }
            parseTabularSection(state, resolved, assignments);
            return false;
        } else if (headerValue.startsWith(".")) {
            // Relative header — append to last absolute header
            if (state.lastAbsoluteHeader != null) {
                state.currentHeader = state.lastAbsoluteHeader + headerValue;
            } else {
                state.currentHeader = headerValue;
            }
            return false;
        } else {
            state.currentHeader = headerValue;
            state.lastAbsoluteHeader = headerValue;
            return false;
        }
    }

    // Match inline header directives → [path, name, value], else null.
    //   {path :type "value"}     → quoted discriminator value
    //   {path :if <expr>}        → unquoted condition expression to end
    //   {path :elif <expr>}      → unquoted condition expression to end
    //   {path :else}             → bare flag
    private static String[] parseInlineHeaderDirective(String headerValue) {
        int colon = headerValue.indexOf(" :");
        if (colon < 0) return null;
        String path = headerValue.substring(0, colon).trim();
        if (path.isEmpty()) return null;
        String rest = headerValue.substring(colon + 2);
        String name;
        if (rest.startsWith("type")) name = "type";
        else if (rest.startsWith("elif")) name = "elif";
        else if (rest.startsWith("if")) name = "if";
        else if (rest.startsWith("else")) name = "else";
        else if (rest.startsWith("loop")) name = "loop";
        else if (rest.startsWith("counter")) name = "counter";
        else if (rest.startsWith("from")) name = "from";
        else return null;
        String afterName = rest.substring(name.length());
        // Directive name must be a whole token (followed by whitespace or end).
        if (!afterName.isEmpty() && !Character.isWhitespace(afterName.charAt(0))) return null;
        afterName = afterName.stripLeading();

        if (name.equals("else")) {
            if (!afterName.isEmpty()) return null;
            return new String[] { path, name, "true" };
        }
        if (name.equals("type")) {
            // :type keeps a quoted discriminator value.
            if (afterName.length() < 2 || afterName.charAt(0) != '"'
                    || afterName.charAt(afterName.length() - 1) != '"')
                return null;
            return new String[] { path, name, afterName.substring(1, afterName.length() - 1) };
        }
        // :if / :elif / :loop / :counter / :from capture the unquoted expression up to the closing brace.
        if (afterName.isEmpty()) return null;
        String expr = afterName.strip();
        // A fully double-quoted expression is a legacy infix string; unwrap it.
        if (expr.length() >= 2 && expr.charAt(0) == '"' && expr.charAt(expr.length() - 1) == '"') {
            expr = expr.substring(1, expr.length() - 1);
        }
        return new String[] { path, name, expr };
    }

    // ── Import, Schema, Conditional ──

    private static void parseImportToken(ParserState state, List<OdinImport> imports) {
        int line = state.current().getLine();
        int col = state.current().getColumn();
        String value = state.current().getValue();
        state.advance();

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new OdinParseException(ParseErrorCode.InvalidDirective, line, col,
                    "Invalid import directive syntax");
        }

        if (trimmed.endsWith(" as")) {
            throw new OdinParseException(ParseErrorCode.InvalidDirective, line, col,
                    "Import alias requires identifier");
        }

        int asPos = trimmed.indexOf(" as ");
        if (asPos >= 0) {
            String path = stripQuotes(trimmed.substring(0, asPos).trim());
            String alias = trimmed.substring(asPos + 4).trim();
            if (alias.isEmpty()) {
                throw new OdinParseException(ParseErrorCode.InvalidDirective, line, col,
                        "Import alias requires identifier");
            }
            imports.add(new OdinImport(path, alias, line));
        } else {
            imports.add(new OdinImport(stripQuotes(trimmed), null, line));
        }
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
            return s.substring(1, s.length() - 1);
        return s;
    }

    private static void parseSchemaToken(ParserState state, List<OdinSchemaRef> schemas) {
        int line = state.current().getLine();
        int col = state.current().getColumn();
        String value = state.current().getValue();
        state.advance();

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new OdinParseException(ParseErrorCode.InvalidDirective, line, col,
                    "Schema directive requires URL");
        }

        schemas.add(new OdinSchemaRef(stripQuotes(trimmed), line));
    }

    private static void parseConditionalToken(ParserState state, List<OdinConditional> conditionals) {
        int line = state.current().getLine();
        int col = state.current().getColumn();
        String value = state.current().getValue();
        state.advance();

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new OdinParseException(ParseErrorCode.InvalidDirective, line, col,
                    "Conditional directive requires expression");
        }
        conditionals.add(new OdinConditional(trimmed, line));
    }

    // ── Assignment Parsing ──

    private static void parseAssignment(
            ParserState state,
            boolean inMetadata,
            OrderedMap<String, OdinValue> metadata,
            OrderedMap<String, OdinValue> assignments,
            OrderedMap<String, OdinModifiers> modifiers,
            Map<String, List<Integer>> arrayIndices) {
        String pathValue = state.current().getValue();
        int pathLine = state.current().getLine();
        int pathCol = state.current().getColumn();
        state.advance();

        // Top-level metadata assignment ($.path = value), e.g. canonical-form output.
        if (pathValue.startsWith("$.")) {
            inMetadata = true;
            pathValue = pathValue.substring(2);
            state.currentHeader = null;
        }

        // Build full path with current header
        String fullPath;
        if (state.currentHeader != null) {
            if (pathValue.length() > 0 && pathValue.charAt(0) == '[') {
                var header = state.currentHeader;
                if (header.endsWith("[]"))
                    header = header.substring(0, header.length() - 2);
                fullPath = header + pathValue;
            } else {
                fullPath = state.currentHeader + "." + pathValue;
            }
        } else {
            fullPath = pathValue;
        }

        // Normalize leading zeros in array indices: [007] -> [7]
        if (fullPath.contains("[")) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < fullPath.length()) {
                if (fullPath.charAt(i) == '[') {
                    sb.append('[');
                    i++;
                    int start = i;
                    while (i < fullPath.length() && Character.isDigit(fullPath.charAt(i))) {
                        i++;
                    }
                    if (i > start && i < fullPath.length() && fullPath.charAt(i) == ']') {
                        long idx = Long.parseLong(fullPath.substring(start, i));
                        sb.append(idx);
                    } else {
                        sb.append(fullPath, start, i);
                    }
                } else {
                    sb.append(fullPath.charAt(i));
                    i++;
                }
            }
            fullPath = sb.toString();
        }

        // P010: Validate nesting depth
        int depth = 1;
        for (int i = 0; i < fullPath.length(); i++) {
            char c = fullPath.charAt(i);
            if (c == '.' || c == '[')
                depth++;
        }
        if (depth > state.options.getMaxDepth()) {
            throw new OdinParseException(ParseErrorCode.MaximumDepthExceeded, pathLine, pathCol,
                    String.format("Maximum nesting depth exceeded: %d > %d", depth, state.options.getMaxDepth()));
        }

        // P015: Validate all array indices for range
        {
            long cumulativeIndex = 0;
            int searchStart = 0;
            while (true) {
                int bp = fullPath.indexOf('[', searchStart);
                if (bp < 0) break;
                int cp = fullPath.indexOf(']', bp);
                if (cp < 0) break;
                String idxStr = fullPath.substring(bp + 1, cp);
                if (!idxStr.isEmpty()) {
                    try {
                        long parsedIdx = Long.parseLong(idxStr);
                        if (parsedIdx < 0) {
                            throw new OdinParseException(ParseErrorCode.InvalidArrayIndex, pathLine, pathCol,
                                    "Negative array index: " + parsedIdx);
                        }
                        if (parsedIdx > state.options.getMaxArrayIndex()) {
                            throw new OdinParseException(ParseErrorCode.ArrayIndexOutOfRange, pathLine, pathCol,
                                    String.format("Array index out of range: %d > %d", parsedIdx, state.options.getMaxArrayIndex()));
                        }
                        cumulativeIndex += parsedIdx;
                        if (cumulativeIndex > state.options.getMaxArrayIndex()) {
                            throw new OdinParseException(ParseErrorCode.ArrayIndexOutOfRange, pathLine, pathCol,
                                    String.format("Cumulative array index out of range: %d > %d", cumulativeIndex, state.options.getMaxArrayIndex()));
                        }
                    } catch (NumberFormatException e) {
                        // Non-numeric bracket content, skip
                    }
                }
                searchStart = cp + 1;
            }
        }

        // P013: Validate array contiguity
        int bracketPos = fullPath.indexOf('[');
        if (bracketPos >= 0) {
            String arrayBase = fullPath.substring(0, bracketPos);
            int closePos = fullPath.indexOf(']', bracketPos);
            if (closePos > bracketPos) {
                String idxStr = fullPath.substring(bracketPos + 1, closePos);
                if (!idxStr.isEmpty()) {
                    try {
                        int idx = Integer.parseInt(idxStr);
                        var indices = arrayIndices.computeIfAbsent(arrayBase, k -> new ArrayList<>());

                        if (!indices.contains(idx)) {
                            int expected = indices.isEmpty() ? 0 : Collections.max(indices) + 1;
                            if (idx != expected) {
                                throw new OdinParseException(ParseErrorCode.NonContiguousArrayIndices, pathLine, pathCol,
                                        String.format("Non-contiguous array indices: expected %d, got %d", expected, idx));
                            }
                            indices.add(idx);
                        }
                    } catch (NumberFormatException e) {
                        // Non-numeric, skip
                    }
                }
            }
        }

        // Expect '='
        if (state.isAtEnd() || state.current().getTokenType() != TokenType.Equals) {
            throw new OdinParseException(ParseErrorCode.UnexpectedCharacter, pathLine, pathCol,
                    "Expected '=' after '" + fullPath + "'");
        }
        state.advance(); // consume '='

        // Check for duplicate paths
        if (!state.options.isAllowDuplicates()) {
            if ((inMetadata && metadata.containsKey(fullPath)) ||
                (!inMetadata && assignments.containsKey(fullPath))) {
                throw new OdinParseException(ParseErrorCode.DuplicatePathAssignment, pathLine, pathCol, fullPath);
            }
        }

        // Parse modifiers and value
        var modsResult = ValueParser.parseModifiers(state.tokens, state.pos);
        var mods = modsResult.modifiers();
        state.pos += modsResult.consumed();

        if (state.isAtEnd() || state.current().getTokenType() == TokenType.Newline) {
            OdinValue emptyValue = OdinValue.ofString("");
            if (inMetadata) {
                metadata.set(fullPath, emptyValue);
                assignments.set("$." + fullPath, emptyValue);
            } else {
                assignments.set(fullPath, emptyValue);
            }
            return;
        }

        var valueResult = ValueParser.parseValue(state.tokens, state.pos);
        OdinValue value = valueResult.value();
        state.pos += valueResult.consumed();

        // Parse trailing directives
        var directives = new ArrayList<OdinDirective>();
        while (!state.isAtEnd()) {
            var tt = state.current().getTokenType();
            if (tt == TokenType.Newline || tt == TokenType.Comment)
                break;

            if (tt == TokenType.Directive) {
                String dirName = state.current().getValue();
                state.advance();
                DirectiveValue dirValue = null;
                if (!state.isAtEnd()) {
                    var nextTt = state.current().getTokenType();
                    if (nextTt != TokenType.Newline && nextTt != TokenType.Comment && nextTt != TokenType.Directive) {
                        String v = state.current().getValue();
                        state.advance();
                        try {
                            double numVal = Double.parseDouble(v);
                            dirValue = DirectiveValue.fromNumber(numVal);
                        } catch (NumberFormatException e) {
                            dirValue = DirectiveValue.fromString(v);
                        }
                    }
                }
                directives.add(new OdinDirective(dirName, dirValue));
            } else {
                state.advance();
            }
        }

        if (!directives.isEmpty()) {
            value = value.withDirectives(directives);
        }

        if (mods.hasAny()) {
            value = value.withModifiers(mods);
            modifiers.set(fullPath, mods);
        }

        if (inMetadata) {
            assignments.set("$." + fullPath, value);
            metadata.set(fullPath, value);
        } else {
            assignments.set(fullPath, value);
        }
    }

    // ── Table Data Parsing ──

    private static void parseTableData(
            ParserState state,
            String header,
            OrderedMap<String, OdinValue> metadata) {
        int bracketPos = header.indexOf('[');
        if (bracketPos < 0) return;
        int closePos = header.indexOf(']');
        if (closePos < 0) return;

        int tableNameStart = header.indexOf("table.");
        if (tableNameStart < 0) return;
        String tableName = header.substring(tableNameStart + 6, bracketPos);
        String colsStr = header.substring(bracketPos + 1, closePos);
        String[] columns = splitAndTrim(colsStr, ',');

        if (columns.length == 0 || (columns.length == 1 && columns[0].isEmpty())) {
            int colonPos = header.indexOf(':', closePos);
            if (colonPos >= 0) {
                columns = splitAndTrim(header.substring(colonPos + 1), ',');
            }
        }
        if (columns.length == 0 || tableName.isEmpty()) return;

        int rowIndex = 0;

        while (true) {
            state.skipNewlines();
            if (state.isAtEnd()) break;

            var tok = state.peek();
            if (tok != null && (tok.getTokenType() == TokenType.Header || tok.getTokenType() == TokenType.DocumentSeparator))
                break;
            if (tok != null && tok.getTokenType() == TokenType.Comment) {
                state.advance();
                continue;
            }

            var values = new ArrayList<String>();
            String currentVal = null;

            while (!state.isAtEnd()) {
                var t = state.current();
                if (t.getTokenType() == TokenType.Newline || t.getTokenType() == TokenType.Header ||
                    t.getTokenType() == TokenType.DocumentSeparator) break;
                if (t.getTokenType() == TokenType.Comment) {
                    state.advance();
                    break;
                }

                if (t.getTokenType() == TokenType.QuotedString) {
                    currentVal = t.getValue();
                    state.advance();
                    if (!state.isAtEnd()) {
                        var next = state.peek();
                        if (next != null && (next.getTokenType() == TokenType.Newline ||
                            next.getTokenType() == TokenType.Header ||
                            next.getTokenType() == TokenType.Comment ||
                            next.getTokenType() == TokenType.DocumentSeparator)) {
                            if (currentVal != null) {
                                values.add(currentVal);
                                currentVal = null;
                            }
                            break;
                        }
                    }
                } else if (t.getTokenType() == TokenType.Path || t.getTokenType() == TokenType.BareWord) {
                    String v = t.getValue();
                    state.advance();
                    if (",".equals(v)) {
                        if (currentVal != null) {
                            values.add(currentVal);
                            currentVal = null;
                        }
                    } else if (v.indexOf(',') >= 0) {
                        if (currentVal != null) {
                            values.add(currentVal);
                            currentVal = null;
                        }
                        for (String part : v.split(",")) {
                            String trimmed = part.trim().replace("\"", "");
                            if (!trimmed.isEmpty()) values.add(trimmed);
                        }
                    } else {
                        currentVal = v;
                    }
                } else {
                    String v = t.getValue();
                    state.advance();
                    if (",".equals(v)) {
                        if (currentVal != null) {
                            values.add(currentVal);
                            currentVal = null;
                        }
                    } else if (v.indexOf(',') >= 0) {
                        if (currentVal != null) {
                            values.add(currentVal);
                            currentVal = null;
                        }
                        for (String part : v.split(",")) {
                            String trimmed = part.trim().replace("\"", "");
                            if (!trimmed.isEmpty()) values.add(trimmed);
                        }
                    }
                }
            }
            if (currentVal != null) values.add(currentVal);
            if (values.isEmpty()) continue;

            for (int colIdx = 0; colIdx < columns.length && colIdx < values.size(); colIdx++) {
                String key = String.format("table.%s[%d].%s", tableName, rowIndex, columns[colIdx]);
                metadata.set(key, OdinValue.ofString(values.get(colIdx)));
            }
            rowIndex++;
        }
    }

    // ── Tabular Section Parsing ──

    private static void parseTabularSection(
            ParserState state,
            String header,
            OrderedMap<String, OdinValue> assignments) {
        int colonPos = header.indexOf(" : ");
        if (colonPos < 0) return;

        String namePart = header.substring(0, colonPos);
        String colsStr = header.substring(colonPos + 3);
        String[] columns = splitAndTrim(colsStr, ',');

        String baseName = namePart;
        if (baseName.startsWith(".")) baseName = baseName.substring(1);
        if (baseName.endsWith("[]")) baseName = baseName.substring(0, baseName.length() - 2);

        if (columns.length == 0 || baseName.isEmpty()) return;

        // Resolve relative column names: ".city" after "address.line1" -> "address.city"
        resolveRelativeColumns(columns);

        int rowIndex = 0;

        while (true) {
            state.skipNewlines();
            if (state.isAtEnd()) break;

            var tok = state.peek();
            if (tok != null && (tok.getTokenType() == TokenType.Header || tok.getTokenType() == TokenType.DocumentSeparator))
                break;
            if (tok != null && tok.getTokenType() == TokenType.Comment) {
                state.advance();
                continue;
            }

            if (state.isAssignmentLine()) {
                String fieldName = state.advance().getValue();
                state.advance(); // consume '='

                OdinValue val;
                int consumed;
                try {
                    var result = ValueParser.parseValue(state.tokens, state.pos);
                    val = result.value();
                    consumed = result.consumed();
                } catch (OdinParseException e) {
                    while (!state.isAtEnd() &&
                           state.current().getTokenType() != TokenType.Newline &&
                           state.current().getTokenType() != TokenType.Header) {
                        state.advance();
                    }
                    continue;
                }
                state.pos += consumed;

                String fullKey = String.format("%s[].%s", baseName, fieldName);

                var directives = new ArrayList<OdinDirective>();
                if (val.getDirectives() != null) {
                    directives.addAll(val.getDirectives());
                }

                while (!state.isAtEnd()) {
                    var t = state.current();
                    if (t.getTokenType() == TokenType.Newline || t.getTokenType() == TokenType.Header ||
                        t.getTokenType() == TokenType.Comment || t.getTokenType() == TokenType.DocumentSeparator)
                        break;

                    if (t.getTokenType() == TokenType.Directive) {
                        String dirName = t.getValue();
                        state.advance();
                        DirectiveValue dirVal = null;
                        if (!state.isAtEnd()) {
                            var next = state.current();
                            if (next.getTokenType() != TokenType.Newline &&
                                next.getTokenType() != TokenType.Header &&
                                next.getTokenType() != TokenType.Directive &&
                                next.getTokenType() != TokenType.Comment &&
                                next.getTokenType() != TokenType.DocumentSeparator) {
                                String sv = next.getValue();
                                state.advance();
                                try {
                                    double numVal = Double.parseDouble(sv);
                                    dirVal = DirectiveValue.fromNumber(numVal);
                                } catch (NumberFormatException e2) {
                                    dirVal = DirectiveValue.fromString(sv);
                                }
                            }
                        }
                        directives.add(new OdinDirective(dirName, dirVal));
                    } else {
                        state.advance();
                    }
                }

                if (!directives.isEmpty())
                    val = val.withDirectives(directives);

                assignments.set(fullKey, val);

                while (!state.isAtEnd() &&
                       state.current().getTokenType() != TokenType.Newline &&
                       state.current().getTokenType() != TokenType.Header) {
                    state.advance();
                }
                continue;
            }

            // Collect values on this line
            var values = new ArrayList<OdinValue>();

            while (!state.isAtEnd()) {
                var t = state.current();
                if (t.getTokenType() == TokenType.Newline || t.getTokenType() == TokenType.Header ||
                    t.getTokenType() == TokenType.DocumentSeparator) break;
                if (t.getTokenType() == TokenType.Comment) {
                    state.advance();
                    break;
                }

                try {
                    var result = ValueParser.parseValue(state.tokens, state.pos);
                    state.pos += result.consumed();
                    values.add(result.value());

                    while (!state.isAtEnd()) {
                        var ct = state.current();
                        if (ct.getTokenType() == TokenType.Newline || ct.getTokenType() == TokenType.Header ||
                            ct.getTokenType() == TokenType.Comment || ct.getTokenType() == TokenType.DocumentSeparator)
                            break;
                        if (ct.getTokenType() == TokenType.Comma) {
                            state.advance();
                            break;
                        }
                        state.advance();
                    }
                } catch (OdinParseException e) {
                    state.advance();
                }
            }

            if (values.isEmpty()) continue;

            for (int colIdx = 0; colIdx < columns.length && colIdx < values.size(); colIdx++) {
                String key;
                if ("~".equals(columns[colIdx])) {
                    key = String.format("%s[%d]", baseName, rowIndex);
                } else {
                    key = String.format("%s[%d].%s", baseName, rowIndex, columns[colIdx]);
                }
                assignments.set(key, values.get(colIdx));
            }
            rowIndex++;
        }
    }

    // ── Utilities ──

    private static String[] splitAndTrim(String s, char separator) {
        String[] parts = s.split(String.valueOf(separator));
        var result = new ArrayList<String>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result.toArray(new String[0]);
    }

    /**
     * Resolve relative column names in tabular headers.
     * A column starting with "." inherits the parent path from the previous non-relative column.
     * E.g., ["name", "address.line1", ".city", ".state"] -> ["name", "address.line1", "address.city", "address.state"]
     */
    private static void resolveRelativeColumns(String[] columns) {
        String lastParent = null;
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].startsWith(".")) {
                if (lastParent != null) {
                    columns[i] = lastParent + columns[i];
                } else {
                    columns[i] = columns[i].substring(1);
                }
            } else if (columns[i].contains(".")) {
                // Track parent for relative resolution
                int lastDot = columns[i].lastIndexOf('.');
                lastParent = columns[i].substring(0, lastDot);
            } else {
                lastParent = null;
            }
        }
    }
}
