package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.SourceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CsvSourceParser {

    private CsvSourceParser() {}

    public static DynValue parse(String input, SourceConfig config) {
        if (input == null || input.isEmpty()) {
            return DynValue.ofArray(List.of());
        }

        char delimiter = ',';
        boolean hasHeader = true;

        if (config != null) {
            var delim = config.getOptions().get("delimiter");
            if (delim != null && !delim.isEmpty()) delimiter = delim.charAt(0);
            var hdr = config.getOptions().get("hasHeader");
            if ("false".equals(hdr)) hasHeader = false;
        }

        var rows = splitRows(input, delimiter);
        if (rows.isEmpty()) return DynValue.ofArray(List.of());

        if (hasHeader) {
            var headers = rows.get(0);
            var result = new ArrayList<DynValue>();
            for (int r = 1; r < rows.size(); r++) {
                var row = rows.get(r);
                var entries = new ArrayList<Map.Entry<String, DynValue>>();
                for (int c = 0; c < headers.size(); c++) {
                    String val = c < row.size() ? row.get(c) : "";
                    entries.add(Map.entry(headers.get(c), inferType(val)));
                }
                result.add(DynValue.ofObject(entries));
            }
            return DynValue.ofArray(result);
        } else {
            var result = new ArrayList<DynValue>();
            for (var row : rows) {
                var items = new ArrayList<DynValue>();
                for (var cell : row) items.add(inferType(cell));
                result.add(DynValue.ofArray(items));
            }
            return DynValue.ofArray(result);
        }
    }

    private static List<List<String>> splitRows(String input, char delimiter) {
        var rows = new ArrayList<List<String>>();
        var currentField = new StringBuilder();
        var currentRow = new ArrayList<String>();
        boolean inQuotes = false;
        int i = 0;

        while (i < input.length()) {
            char ch = input.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    currentField.append(ch);
                    i++;
                }
            } else if (ch == '"') {
                inQuotes = true;
                i++;
            } else if (ch == delimiter) {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                i++;
            } else if (ch == '\r') {
                if (i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                if (!currentRow.isEmpty()) rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
                i++;
            } else if (ch == '\n') {
                currentRow.add(currentField.toString());
                currentField.setLength(0);
                if (!currentRow.isEmpty()) rows.add(new ArrayList<>(currentRow));
                currentRow.clear();
                i++;
            } else {
                currentField.append(ch);
                i++;
            }
        }

        if (inQuotes) throw new FormatException("Unterminated quoted field in CSV.");

        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }

        return rows;
    }

    private static DynValue inferType(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return DynValue.ofString("");
        if (trimmed.equalsIgnoreCase("true")) return DynValue.ofBool(true);
        if (trimmed.equalsIgnoreCase("false")) return DynValue.ofBool(false);
        if (trimmed.equalsIgnoreCase("null")) return DynValue.ofNull();

        try {
            long intVal = Long.parseLong(trimmed);
            return DynValue.ofInteger(intVal);
        } catch (NumberFormatException ignored) {}

        if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
            try {
                double dblVal = Double.parseDouble(trimmed);
                return DynValue.ofFloat(dblVal);
            } catch (NumberFormatException ignored) {}
        }

        return DynValue.ofString(trimmed);
    }
}
