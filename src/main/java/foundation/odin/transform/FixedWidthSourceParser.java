package foundation.odin.transform;

import foundation.odin.types.DynValue;
import foundation.odin.types.SourceConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FixedWidthSourceParser {

    private FixedWidthSourceParser() {}

    public static DynValue parse(String input, SourceConfig config) {
        if (input == null || input.isEmpty()) {
            return DynValue.ofArray(List.of());
        }

        var columns = parseColumns(config);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Fixed-width source config must include column definitions (columns option).");
        }

        var lines = splitLines(input);
        if (lines.isEmpty()) return DynValue.ofArray(List.of());

        var records = new ArrayList<DynValue>();
        for (var line : lines) {
            var entries = new ArrayList<Map.Entry<String, DynValue>>();
            for (var col : columns) {
                String value;
                if (col.start >= line.length()) {
                    value = "";
                } else {
                    int end = Math.min(col.start + col.width, line.length());
                    value = line.substring(col.start, end).stripTrailing();
                }
                entries.add(Map.entry(col.name, DynValue.ofString(value)));
            }
            records.add(DynValue.ofObject(entries));
        }

        if (records.size() == 1) return records.get(0);
        return DynValue.ofArray(records);
    }

    private static List<String> splitLines(String input) {
        var lines = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\n') {
                int end = i > 0 && input.charAt(i - 1) == '\r' ? i - 1 : i;
                String line = input.substring(start, end);
                if (!line.trim().isEmpty()) lines.add(line);
                start = i + 1;
            }
        }
        if (start < input.length()) {
            String last = input.substring(start);
            if (!last.trim().isEmpty()) lines.add(last);
        }
        return lines;
    }

    private static List<ColumnDef> parseColumns(SourceConfig config) {
        var columns = new ArrayList<ColumnDef>();
        if (config == null) return columns;

        var columnsStr = config.getOptions().get("columns");
        if (columnsStr == null || columnsStr.isEmpty()) return columns;

        for (var part : columnsStr.split(";")) {
            var trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            var segments = trimmed.split(":");
            if (segments.length < 3) continue;
            try {
                columns.add(new ColumnDef(
                        segments[0].trim(),
                        Integer.parseInt(segments[1].trim()),
                        Integer.parseInt(segments[2].trim())
                ));
            } catch (NumberFormatException ignored) {}
        }
        return columns;
    }

    private record ColumnDef(String name, int start, int width) {}
}
