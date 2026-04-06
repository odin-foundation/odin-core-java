package foundation.odin.transform;

import foundation.odin.types.DynValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FlatSourceParser {

    private FlatSourceParser() {}

    public static DynValue parse(String input) {
        if (input == null || input.isEmpty()) {
            return DynValue.ofObject(List.of());
        }

        var root = new ArrayList<Map.Entry<String, DynValue>>();
        var lines = input.split("\n");

        for (var rawLine : lines) {
            var line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            var trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.charAt(0) == '#' || trimmed.charAt(0) == ';') continue;

            int eqPos = trimmed.indexOf('=');
            if (eqPos < 0) continue;

            String key = trimmed.substring(0, eqPos).trim();
            String rawValue = trimmed.substring(eqPos + 1).trim();

            DynValue dynVal;
            if (rawValue.isEmpty() || rawValue.equals("~")) {
                dynVal = DynValue.ofNull();
            } else if (rawValue.length() >= 2 && rawValue.charAt(0) == '"' && rawValue.charAt(rawValue.length() - 1) == '"') {
                dynVal = DynValue.ofString(rawValue.substring(1, rawValue.length() - 1));
            } else {
                dynVal = inferType(rawValue);
            }

            setPath(root, key, dynVal);
        }

        return DynValue.ofObject(root);
    }

    private static DynValue inferType(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return DynValue.ofString("");
        if (trimmed.equalsIgnoreCase("true")) return DynValue.ofBool(true);
        if (trimmed.equalsIgnoreCase("false")) return DynValue.ofBool(false);
        if (trimmed.equalsIgnoreCase("null")) return DynValue.ofNull();

        try { return DynValue.ofInteger(Long.parseLong(trimmed)); }
        catch (NumberFormatException ignored) {}

        if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
            try { return DynValue.ofFloat(Double.parseDouble(trimmed)); }
            catch (NumberFormatException ignored) {}
        }

        return DynValue.ofString(trimmed);
    }

    // ─── Path parsing and setting ────────────────────────────────────

    private static void setPath(List<Map.Entry<String, DynValue>> root, String path, DynValue value) {
        var segments = parsePath(path);
        if (segments.isEmpty()) return;
        setSegments(root, segments, 0, value);
    }

    private sealed interface PathSegment permits PathSegment.Key, PathSegment.Index {
        record Key(String name) implements PathSegment {}
        record Index(int index) implements PathSegment {}
    }

    private static List<PathSegment> parsePath(String path) {
        var segments = new ArrayList<PathSegment>();
        var current = new StringBuilder();

        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == '.') {
                if (current.length() > 0) {
                    segments.add(new PathSegment.Key(current.toString()));
                    current.setLength(0);
                }
            } else if (ch == '[') {
                if (current.length() > 0) {
                    segments.add(new PathSegment.Key(current.toString()));
                    current.setLength(0);
                }
                var idxStr = new StringBuilder();
                i++;
                while (i < path.length() && path.charAt(i) != ']') {
                    idxStr.append(path.charAt(i));
                    i++;
                }
                try {
                    segments.add(new PathSegment.Index(Integer.parseInt(idxStr.toString())));
                } catch (NumberFormatException e) {
                    segments.add(new PathSegment.Key(idxStr.toString()));
                }
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) segments.add(new PathSegment.Key(current.toString()));
        return segments;
    }

    private static void setSegments(List<Map.Entry<String, DynValue>> entries, List<PathSegment> segments, int segIdx, DynValue value) {
        if (segIdx >= segments.size()) return;
        var seg = segments.get(segIdx);

        if (seg instanceof PathSegment.Key keySeg) {
            String key = keySeg.name();
            if (segIdx == segments.size() - 1) {
                int existing = findEntry(entries, key);
                if (existing >= 0) {
                    entries.set(existing, Map.entry(key, value));
                } else {
                    entries.add(Map.entry(key, value));
                }
            } else {
                var nextSeg = segments.get(segIdx + 1);
                int existing = findEntry(entries, key);

                if (nextSeg instanceof PathSegment.Index) {
                    List<DynValue> arr;
                    if (existing >= 0 && entries.get(existing).getValue().asArray() != null) {
                        arr = entries.get(existing).getValue().asArray();
                    } else {
                        arr = new ArrayList<>();
                        var newVal = DynValue.ofArray(arr);
                        if (existing >= 0) entries.set(existing, Map.entry(key, newVal));
                        else entries.add(Map.entry(key, newVal));
                    }
                    setInArray(arr, segments, segIdx + 1, value);
                } else {
                    List<Map.Entry<String, DynValue>> obj;
                    if (existing >= 0 && entries.get(existing).getValue().asObject() != null) {
                        obj = entries.get(existing).getValue().asObject();
                    } else {
                        obj = new ArrayList<>();
                        var newVal = DynValue.ofObject(obj);
                        if (existing >= 0) entries.set(existing, Map.entry(key, newVal));
                        else entries.add(Map.entry(key, newVal));
                    }
                    setSegments(obj, segments, segIdx + 1, value);
                }
            }
        }
    }

    private static void setInArray(List<DynValue> arr, List<PathSegment> segments, int segIdx, DynValue value) {
        if (segIdx >= segments.size()) return;
        if (!(segments.get(segIdx) instanceof PathSegment.Index idxSeg)) return;

        int idx = idxSeg.index();
        while (arr.size() <= idx) arr.add(DynValue.ofNull());

        if (segIdx == segments.size() - 1) {
            arr.set(idx, value);
        } else {
            var nextSeg = segments.get(segIdx + 1);
            if (nextSeg instanceof PathSegment.Key) {
                var obj = arr.get(idx).asObject();
                if (obj == null) {
                    obj = new ArrayList<>();
                    arr.set(idx, DynValue.ofObject(obj));
                }
                setSegments(obj, segments, segIdx + 1, value);
            } else {
                var inner = arr.get(idx).asArray();
                if (inner == null) {
                    inner = new ArrayList<>();
                    arr.set(idx, DynValue.ofArray(inner));
                }
                setInArray(inner, segments, segIdx + 1, value);
            }
        }
    }

    private static int findEntry(List<Map.Entry<String, DynValue>> entries, String key) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getKey().equals(key)) return i;
        }
        return -1;
    }
}
