package foundation.odin.parsing;

public final class Token {
    private final TokenType tokenType;
    private final int start;
    private final int end;
    private final int line;
    private final int column;
    private final String value;

    public Token(TokenType tokenType, int start, int end, int line, int column, String value) {
        this.tokenType = tokenType;
        this.start = start;
        this.end = end;
        this.line = line;
        this.column = column;
        this.value = value != null ? value : "";
    }

    public TokenType getTokenType() { return tokenType; }
    public int getStart() { return start; }
    public int getEnd() { return end; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getValue() { return value; }

    @Override
    public String toString() {
        return tokenType + "(" + value + ") [" + line + ":" + column + "]";
    }
}
