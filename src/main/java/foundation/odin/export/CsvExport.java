package foundation.odin.export;

import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinValue;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CsvExport {

    private CsvExport() {}

    public static final class CsvExportOptions {
        private String arrayPath;
        private char delimiter = ',';
        private boolean header = true;

        public CsvExportOptions() {}

        public String getArrayPath() { return arrayPath; }
        public CsvExportOptions setArrayPath(String arrayPath) { this.arrayPath = arrayPath; return this; }

        public char getDelimiter() { return delimiter; }
        public CsvExportOptions setDelimiter(char delimiter) { this.delimiter = delimiter; return this; }

        public boolean isHeader() { return header; }
        public CsvExportOptions setHeader(boolean header) { this.header = header; return this; }
    }

    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("^(.+?)\\[(\\d+)]\\.(.+)$");

    public static String toCsv(OdinDocument doc) {
        return toCsv(doc, new CsvExportOptions());
    }

    public static String toCsv(OdinDocument doc, CsvExportOptions options) {
        String delimiter = String.valueOf(options.delimiter);

        var rows = collectRows(doc, options.arrayPath);
        if (rows.isEmpty()) {
            return singleRowFallback(doc, options);
        }

        var columns = new ArrayList<String>();
        var columnSet = new LinkedHashSet<String>();
        for (var row : rows) {
            for (String key : row.keySet()) {
                if (columnSet.add(key)) columns.add(key);
            }
        }

        var sb = new StringBuilder();
        if (options.header) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(delimiter);
                sb.append(escapeCsv(columns.get(i), delimiter, '"'));
            }
            sb.append('\n');
        }

        for (var row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(delimiter);
                OdinValue val = row.get(columns.get(i));
                if (val != null) sb.append(escapeCsv(formatValue(val), delimiter, '"'));
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static List<Map<String, OdinValue>> collectRows(OdinDocument doc, String arrayPath) {
        var rows = new TreeMap<Integer, Map<String, OdinValue>>();
        String detectedPrefix = arrayPath;

        for (var entry : doc.getAssignments()) {
            Matcher m = ARRAY_INDEX_PATTERN.matcher(entry.getKey());
            if (!m.matches()) continue;

            String prefix = m.group(1);
            int index = Integer.parseInt(m.group(2));
            String field = m.group(3);

            if (detectedPrefix == null) detectedPrefix = prefix;
            if (!prefix.equals(detectedPrefix)) continue;

            rows.computeIfAbsent(index, k -> new LinkedHashMap<>()).put(field, entry.getValue());
        }

        return new ArrayList<>(rows.values());
    }

    private static String singleRowFallback(OdinDocument doc, CsvExportOptions options) {
        String delimiter = String.valueOf(options.delimiter);
        var columns = new ArrayList<String>();
        var values = new ArrayList<String>();

        for (var entry : doc.getAssignments()) {
            if (entry.getKey().startsWith("$")) continue;
            if (entry.getKey().contains("[")) continue;
            columns.add(entry.getKey());
            values.add(formatValue(entry.getValue()));
        }

        if (columns.isEmpty()) return "";

        var sb = new StringBuilder();
        if (options.header) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(delimiter);
                sb.append(escapeCsv(columns.get(i), delimiter, '"'));
            }
            sb.append('\n');
        }

        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(escapeCsv(values.get(i), delimiter, '"'));
        }
        sb.append('\n');

        return sb.toString();
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

    private static String escapeCsv(String value, String delimiter, char quoteChar) {
        boolean needsQuoting = value.contains(delimiter)
                || value.indexOf(quoteChar) >= 0
                || value.contains("\n")
                || value.contains("\r");
        if (needsQuoting)
            return quoteChar + value.replace(String.valueOf(quoteChar), String.valueOf(quoteChar) + quoteChar) + quoteChar;
        return value;
    }
}
