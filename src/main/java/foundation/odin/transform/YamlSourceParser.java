package foundation.odin.transform;

import foundation.odin.types.DynValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class YamlSourceParser {

    private YamlSourceParser() {}

    public static DynValue parse(String input) {
        if (input == null || input.isEmpty()) {
            return DynValue.ofObject(List.of());
        }

        var lines = preprocess(input);
        if (lines.isEmpty()) return DynValue.ofObject(List.of());

        int[] pos = {0};
        return parseBlock(lines, pos, 0);
    }

    // ─── Internal types ──────────────────────────────────────────────

    private record YamlLine(int indent, String content) {}

    // ─── Preprocessing ───────────────────────────────────────────────

    private static List<YamlLine> preprocess(String input) {
        var result = new ArrayList<YamlLine>();
        var rawLines = input.split("\n", -1);

        for (var rawLine : rawLines) {
            var line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            var content = stripComment(line).stripTrailing();
            var trimmedStart = content.stripLeading();
            if (trimmedStart.isEmpty()) continue;
            int indent = content.length() - trimmedStart.length();
            result.add(new YamlLine(indent, trimmedStart));
        }
        return result;
    }

    private static String stripComment(String line) {
        boolean inSingle = false, inDouble = false;
        var sb = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !inDouble) { inSingle = !inSingle; sb.append(ch); }
            else if (ch == '"' && !inSingle) { inDouble = !inDouble; sb.append(ch); }
            else if (ch == '#' && !inSingle && !inDouble) {
                if (sb.isEmpty() || sb.charAt(sb.length() - 1) == ' ' || sb.charAt(sb.length() - 1) == '\t')
                    break;
                sb.append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ─── Block parsing ───────────────────────────────────────────────

    private static DynValue parseBlock(List<YamlLine> lines, int[] pos, int baseIndent) {
        if (pos[0] >= lines.size()) return DynValue.ofObject(List.of());
        var first = lines.get(pos[0]);
        if (first.content().startsWith("- ") || first.content().equals("-"))
            return parseArray(lines, pos, baseIndent);
        else
            return parseMapping(lines, pos, baseIndent);
    }

    // ─── Mapping parsing ─────────────────────────────────────────────

    private static DynValue parseMapping(List<YamlLine> lines, int[] pos, int baseIndent) {
        var entries = new ArrayList<Map.Entry<String, DynValue>>();

        while (pos[0] < lines.size()) {
            var line = lines.get(pos[0]);
            if (line.indent() < baseIndent) break;
            if (line.indent() > baseIndent) break;

            var content = line.content();
            if (content.startsWith("- ") || content.equals("-")) break;

            int colonPos = findColon(content);
            if (colonPos >= 0) {
                String key = content.substring(0, colonPos).trim();
                String afterColon = content.substring(colonPos + 1).trim();

                if (afterColon.isEmpty()) {
                    pos[0]++;
                    if (pos[0] < lines.size() && lines.get(pos[0]).indent() > baseIndent) {
                        int childIndent = lines.get(pos[0]).indent();
                        var child = parseBlock(lines, pos, childIndent);
                        entries.add(Map.entry(key, child));
                    } else {
                        entries.add(Map.entry(key, DynValue.ofNull()));
                    }
                } else {
                    entries.add(Map.entry(key, parseScalar(afterColon)));
                    pos[0]++;
                }
            } else {
                pos[0]++;
            }
        }

        return DynValue.ofObject(entries);
    }

    // ─── Array parsing ───────────────────────────────────────────────

    private static DynValue parseArray(List<YamlLine> lines, int[] pos, int baseIndent) {
        var items = new ArrayList<DynValue>();

        while (pos[0] < lines.size()) {
            var line = lines.get(pos[0]);
            if (line.indent() < baseIndent) break;
            if (line.indent() > baseIndent) break;

            var content = line.content();
            if (!content.startsWith("- ") && !content.equals("-")) break;

            String afterDash = content.equals("-") ? "" : content.substring(2).trim();

            if (afterDash.isEmpty()) {
                pos[0]++;
                if (pos[0] < lines.size() && lines.get(pos[0]).indent() > baseIndent) {
                    int childIndent = lines.get(pos[0]).indent();
                    var child = parseBlock(lines, pos, childIndent);
                    items.add(child);
                } else {
                    items.add(DynValue.ofNull());
                }
            } else {
                int colonPos = findColon(afterDash);
                if (colonPos >= 0) {
                    String key = afterDash.substring(0, colonPos).trim();
                    String valStr = afterDash.substring(colonPos + 1).trim();

                    var objEntries = new ArrayList<Map.Entry<String, DynValue>>();
                    if (valStr.isEmpty()) {
                        pos[0]++;
                        if (pos[0] < lines.size() && lines.get(pos[0]).indent() > baseIndent) {
                            int childIndent = lines.get(pos[0]).indent();
                            var child = parseBlock(lines, pos, childIndent);
                            objEntries.add(Map.entry(key, child));
                        } else {
                            objEntries.add(Map.entry(key, DynValue.ofNull()));
                        }
                    } else {
                        objEntries.add(Map.entry(key, parseScalar(valStr)));
                        pos[0]++;
                    }

                    int continuationIndent = baseIndent + 2;
                    while (pos[0] < lines.size() && lines.get(pos[0]).indent() >= continuationIndent) {
                        var cont = lines.get(pos[0]);
                        if (cont.indent() > continuationIndent) break;

                        int cp = findColon(cont.content());
                        if (cp >= 0) {
                            String ck = cont.content().substring(0, cp).trim();
                            String cv = cont.content().substring(cp + 1).trim();
                            if (cv.isEmpty()) {
                                pos[0]++;
                                if (pos[0] < lines.size() && lines.get(pos[0]).indent() > continuationIndent) {
                                    int ci = lines.get(pos[0]).indent();
                                    var child = parseBlock(lines, pos, ci);
                                    objEntries.add(Map.entry(ck, child));
                                } else {
                                    objEntries.add(Map.entry(ck, DynValue.ofNull()));
                                }
                            } else {
                                objEntries.add(Map.entry(ck, parseScalar(cv)));
                                pos[0]++;
                            }
                        } else {
                            pos[0]++;
                        }
                    }

                    items.add(DynValue.ofObject(objEntries));
                } else {
                    items.add(parseScalar(afterDash));
                    pos[0]++;
                }
            }
        }

        return DynValue.ofArray(items);
    }

    // ─── Colon finder ────────────────────────────────────────────────

    private static int findColon(String s) {
        boolean inSingle = false, inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'' && !inDouble) inSingle = !inSingle;
            else if (ch == '"' && !inSingle) inDouble = !inDouble;
            else if (ch == ':' && !inSingle && !inDouble) {
                if (i + 1 >= s.length() || s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t')
                    return i;
            }
        }
        return -1;
    }

    // ─── Scalar parsing ──────────────────────────────────────────────

    private static DynValue parseScalar(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return DynValue.ofNull();

        // Quoted strings
        if (trimmed.length() >= 2) {
            if ((trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') ||
                (trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\'')) {
                return DynValue.ofString(trimmed.substring(1, trimmed.length() - 1));
            }
        }

        // Null
        if ("null".equals(trimmed) || "~".equals(trimmed)) return DynValue.ofNull();

        // Booleans
        if (trimmed.equalsIgnoreCase("true") || "yes".equals(trimmed) || "on".equals(trimmed))
            return DynValue.ofBool(true);
        if (trimmed.equalsIgnoreCase("false") || "no".equals(trimmed) || "off".equals(trimmed))
            return DynValue.ofBool(false);

        // Integer
        try { return DynValue.ofInteger(Long.parseLong(trimmed)); }
        catch (NumberFormatException ignored) {}

        // Float
        if (trimmed.contains(".") || trimmed.contains("e") || trimmed.contains("E")) {
            try { return DynValue.ofFloat(Double.parseDouble(trimmed)); }
            catch (NumberFormatException ignored) {}
        }

        return DynValue.ofString(trimmed);
    }
}
