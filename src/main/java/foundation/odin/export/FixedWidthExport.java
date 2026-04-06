package foundation.odin.export;

import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.*;

public final class FixedWidthExport {

    private FixedWidthExport() {}

    public enum Align { LEFT, RIGHT }

    public static final class Field {
        private final String path;
        private final int pos;
        private final int len;
        private final Character padChar;
        private final Align align;

        public Field(String path, int pos, int len) {
            this(path, pos, len, null, Align.LEFT);
        }

        public Field(String path, int pos, int len, Character padChar, Align align) {
            this.path = path;
            this.pos = pos;
            this.len = len;
            this.padChar = padChar;
            this.align = align;
        }

        public String getPath() { return path; }
        public int getPos() { return pos; }
        public int getLen() { return len; }
        public Character getPadChar() { return padChar; }
        public Align getAlign() { return align; }
    }

    public static final class FixedWidthExportOptions {
        private int lineWidth;
        private List<Field> fields = Collections.emptyList();
        private char padChar = ' ';

        public FixedWidthExportOptions() {}

        public int getLineWidth() { return lineWidth; }
        public FixedWidthExportOptions setLineWidth(int lineWidth) { this.lineWidth = lineWidth; return this; }

        public List<Field> getFields() { return fields; }
        public FixedWidthExportOptions setFields(List<Field> fields) { this.fields = fields; return this; }

        public char getPadChar() { return padChar; }
        public FixedWidthExportOptions setPadChar(char padChar) { this.padChar = padChar; return this; }
    }

    public static String toFixedWidth(OdinDocument doc, FixedWidthExportOptions options) {
        if (options.fields.isEmpty() || options.lineWidth <= 0) return "";

        char[] line = new char[options.lineWidth];
        Arrays.fill(line, options.padChar);

        for (var field : options.fields) {
            String value = getValueAtPath(doc, field.path);
            if (value.length() > field.len) value = value.substring(0, field.len);

            char pad = field.padChar != null ? field.padChar : options.padChar;

            int end = Math.min(field.pos + field.len, options.lineWidth);
            for (int i = field.pos; i < end; i++) line[i] = pad;

            if (field.align == Align.RIGHT) {
                int startPos = field.pos + field.len - value.length();
                for (int i = 0; i < value.length() && startPos + i < options.lineWidth; i++)
                    line[startPos + i] = value.charAt(i);
            } else {
                for (int i = 0; i < value.length() && field.pos + i < options.lineWidth; i++)
                    line[field.pos + i] = value.charAt(i);
            }
        }

        return new String(line);
    }

    private static String getValueAtPath(OdinDocument doc, String path) {
        OdinValue value = doc.get(path);
        if (value == null) return "";
        return formatValue(value);
    }

    private static String formatValue(OdinValue value) {
        return switch (value) {
            case OdinValue.OdinNull n -> "";
            case OdinValue.OdinBoolean b -> b.getValue() ? "true" : "false";
            case OdinValue.OdinString s -> s.getValue();
            case OdinValue.OdinInteger i -> Long.toString(i.getValue());
            case OdinValue.OdinNumber n -> formatNumber(n.getValue());
            case OdinValue.OdinCurrency c -> formatNumber(c.getValue());
            case OdinValue.OdinPercent p -> formatNumber(p.getValue());
            case OdinValue.OdinDate d -> d.getRaw();
            case OdinValue.OdinTimestamp ts -> ts.getRaw();
            case OdinValue.OdinTime t -> t.getValue();
            case OdinValue.OdinDuration d -> d.getValue();
            case OdinValue.OdinReference r -> "@" + r.getPath();
            case OdinValue.OdinBinary b -> {
                String b64 = Base64.getEncoder().encodeToString(b.getData());
                yield b.getAlgorithm() != null ? "^" + b.getAlgorithm() + ":" + b64 : "^" + b64;
            }
            default -> value.toString();
        };
    }

    private static String formatNumber(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
            return Long.toString((long) d);
        return Double.toString(d);
    }
}
