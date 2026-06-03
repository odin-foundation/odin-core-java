package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.text.Normalizer;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringVerbs {

    private StringVerbs() {}

    // ── Helpers ──

    private static String toStr(DynValue v) {
        switch (v.getType()) {
            case String: case Date: case Timestamp: case Time: case Duration:
            case Reference: case Binary: case FloatRaw: case CurrencyRaw:
                var s = v.asString();
                return s != null ? s : "";
            case Integer: return Long.toString(v.asInt64());
            case Float: case Currency: case Percent:
                return formatDouble(v.asDouble());
            case Bool: return v.asBool() ? "true" : "false";
            case Null: return "";
            default: return "";
        }
    }

    private static int toInt(DynValue v, int fallback) {
        var d = v.asDouble();
        if (d != null) return (int) d.doubleValue();
        var i = v.asInt64();
        if (i != null) return (int) i.longValue();
        var s = v.asString();
        if (s != null) {
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return fallback;
    }

    private static int toInt(DynValue v) { return toInt(v, 0); }

    private static List<String> splitWords(String s) {
        var words = new ArrayList<String>();
        var current = new StringBuilder();
        char[] chars = s.toCharArray();
        int len = chars.length;
        for (int i = 0; i < len; i++) {
            char ch = chars[i];
            if (ch == ' ' || ch == '\t' || ch == '_' || ch == '-') {
                if (current.length() > 0) {
                    words.add(current.toString());
                    current.setLength(0);
                }
            } else if (Character.isUpperCase(ch) && i > 0) {
                char prev = chars[i - 1];
                if (Character.isLowerCase(prev) || Character.isDigit(prev)) {
                    if (current.length() > 0) {
                        words.add(current.toString());
                        current.setLength(0);
                    }
                } else if (Character.isUpperCase(prev)) {
                    boolean nextIsLower = i + 1 < len && Character.isLowerCase(chars[i + 1]);
                    if (nextIsLower && current.length() > 0) {
                        words.add(current.toString());
                        current.setLength(0);
                    }
                }
                current.append(ch);
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) words.add(current.toString());
        return words;
    }

    private static Pattern createRegex(String pattern) {
        return Pattern.compile(pattern);
    }

    private static char getPadChar(DynValue v) {
        var s = toStr(v);
        return !s.isEmpty() ? s.charAt(0) : ' ';
    }

    private static String removeAccents(String s) {
        var normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        var sb = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            var uc = Character.getType(normalized.charAt(i));
            if (uc != Character.NON_SPACING_MARK)
                sb.append(normalized.charAt(i));
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC);
    }

    private static String formatDouble(double d) {
        if (d == (long) d) return Long.toString((long) d);
        return Double.toString(d);
    }

    // ── Registration ──

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("capitalize", StringVerbs::capitalize);
        reg.put("titleCase", StringVerbs::titleCase);
        reg.put("contains", StringVerbs::contains);
        reg.put("startsWith", StringVerbs::startsWith);
        reg.put("endsWith", StringVerbs::endsWith);
        reg.put("replace", StringVerbs::replace);
        reg.put("replaceRegex", StringVerbs::replaceRegex);
        reg.put("padLeft", StringVerbs::padLeft);
        reg.put("padRight", StringVerbs::padRight);
        reg.put("pad", StringVerbs::pad);
        reg.put("truncate", StringVerbs::truncate);
        reg.put("split", StringVerbs::split);
        reg.put("join", StringVerbs::join);
        reg.put("mask", StringVerbs::mask);
        reg.put("reverseString", StringVerbs::reverseString);
        reg.put("repeat", StringVerbs::repeat);
        reg.put("substring", StringVerbs::substring);
        reg.put("length", StringVerbs::length);
        reg.put("camelCase", StringVerbs::camelCase);
        reg.put("snakeCase", StringVerbs::snakeCase);
        reg.put("kebabCase", StringVerbs::kebabCase);
        reg.put("pascalCase", StringVerbs::pascalCase);
        reg.put("slugify", StringVerbs::slugify);
        reg.put("match", StringVerbs::match);
        reg.put("extract", StringVerbs::extract);
        reg.put("normalizeSpace", StringVerbs::normalizeSpace);
        reg.put("leftOf", StringVerbs::leftOf);
        reg.put("rightOf", StringVerbs::rightOf);
        reg.put("wrap", StringVerbs::wrap);
        reg.put("center", StringVerbs::center);
        reg.put("matches", StringVerbs::matches);
        reg.put("stripAccents", StringVerbs::stripAccents);
        reg.put("clean", StringVerbs::clean);
        reg.put("wordCount", StringVerbs::wordCount);
        reg.put("tokenize", StringVerbs::tokenize);
        reg.put("levenshtein", StringVerbs::levenshtein);
        reg.put("soundex", StringVerbs::soundex);
        reg.put("formatPhone", StringVerbs::formatPhone);
        reg.put("escapeHtml", StringVerbs::escapeHtml);
        reg.put("unescapeHtml", StringVerbs::unescapeHtml);
        reg.put("escapeXml", StringVerbs::escapeXml);
        reg.put("stripTags", StringVerbs::stripTags);
        reg.put("template", StringVerbs::template);
    }

    // ── Markup escaping / templating ──

    private static String escapeMarkup(String s, boolean xmlApos) {
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append(xmlApos ? "&apos;" : "&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static DynValue escapeHtml(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofString(escapeMarkup(toStr(args[0]), false));
    }

    private static DynValue escapeXml(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofString(escapeMarkup(toStr(args[0]), true));
    }

    private static final Pattern NAMED_ENTITY = Pattern.compile("&(amp|lt|gt|quot|apos|#39);");
    private static final Pattern DEC_ENTITY = Pattern.compile("&#(\\d+);");
    private static final Pattern HEX_ENTITY = Pattern.compile("&#x([0-9a-fA-F]+);");

    private static DynValue unescapeHtml(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        String s = toStr(args[0]);
        var named = NAMED_ENTITY.matcher(s);
        var nsb = new StringBuilder();
        while (named.find()) {
            String rep = switch (named.group(1)) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                default -> "'"; // apos or #39
            };
            named.appendReplacement(nsb, Matcher.quoteReplacement(rep));
        }
        named.appendTail(nsb);
        s = nsb.toString();

        var dec = DEC_ENTITY.matcher(s);
        var dsb = new StringBuilder();
        while (dec.find()) {
            dec.appendReplacement(dsb, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(dec.group(1))))));
        }
        dec.appendTail(dsb);
        s = dsb.toString();

        var hex = HEX_ENTITY.matcher(s);
        var hsb = new StringBuilder();
        while (hex.find()) {
            hex.appendReplacement(hsb, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(hex.group(1), 16)))));
        }
        hex.appendTail(hsb);
        return DynValue.ofString(hsb.toString());
    }

    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private static DynValue stripTags(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofString(TAG.matcher(toStr(args[0])).replaceAll(""));
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)\\}");

    private static DynValue template(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        String tpl = toStr(args[0]);
        var fields = new HashMap<String, DynValue>();
        var obj = args[1].asObject();
        if (obj == null) obj = args[1].extractObject();
        if (obj != null) for (var e : obj) fields.put(e.getKey(), e.getValue());
        var m = PLACEHOLDER.matcher(tpl);
        var sb = new StringBuilder();
        while (m.find()) {
            String k = m.group(1).trim();
            DynValue v = fields.get(k);
            String rep = (v == null || v.isNull()) ? "" : toStr(v);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return DynValue.ofString(sb.toString());
    }

    // ── String Verbs ──

    private static DynValue capitalize(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        if (s.isEmpty()) return DynValue.ofString("");
        return DynValue.ofString(Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT));
    }

    private static DynValue titleCase(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        if (s.isEmpty()) return DynValue.ofString("");

        var parts = s.split("[ \t]", -1);
        var sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            var word = parts[i];
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue contains(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        if (args[0].isNull()) return DynValue.ofBool(false);
        return DynValue.ofBool(toStr(args[0]).contains(toStr(args[1])));
    }

    private static DynValue startsWith(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        if (args[0].isNull()) return DynValue.ofBool(false);
        return DynValue.ofBool(toStr(args[0]).startsWith(toStr(args[1])));
    }

    private static DynValue endsWith(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        if (args[0].isNull()) return DynValue.ofBool(false);
        return DynValue.ofBool(toStr(args[0]).endsWith(toStr(args[1])));
    }

    private static DynValue replace(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        return DynValue.ofString(toStr(args[0]).replace(toStr(args[1]), toStr(args[2])));
    }

    private static DynValue replaceRegex(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        try {
            var regex = createRegex(toStr(args[1]));
            return DynValue.ofString(regex.matcher(s).replaceAll(toStr(args[2])));
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue padLeft(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int width = toInt(args[1]);
        char padChar = args.length >= 3 ? getPadChar(args[2]) : ' ';
        if (s.length() >= width) return DynValue.ofString(s);
        return DynValue.ofString(String.valueOf(padChar).repeat(width - s.length()) + s);
    }

    private static DynValue padRight(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int width = toInt(args[1]);
        char padChar = args.length >= 3 ? getPadChar(args[2]) : ' ';
        if (s.length() >= width) return DynValue.ofString(s);
        return DynValue.ofString(s + String.valueOf(padChar).repeat(width - s.length()));
    }

    private static DynValue pad(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        var s = toStr(args[0]);
        int width = toInt(args[1]);
        char padChar = getPadChar(args[2]);
        if (s.length() >= width) return DynValue.ofString(s);
        return DynValue.ofString(s + String.valueOf(padChar).repeat(width - s.length()));
    }

    private static DynValue truncate(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int maxLen = toInt(args[1]);
        var ellipsis = args.length >= 3 ? toStr(args[2]) : "";
        if (s.length() <= maxLen) return DynValue.ofString(s);
        if (!ellipsis.isEmpty() && maxLen > ellipsis.length())
            return DynValue.ofString(s.substring(0, maxLen - ellipsis.length()) + ellipsis);
        return DynValue.ofString(s.substring(0, maxLen));
    }

    private static DynValue split(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        var delimiter = toStr(args[1]);

        String[] parts;
        if (delimiter.isEmpty()) {
            parts = new String[s.length()];
            for (int i = 0; i < s.length(); i++) parts[i] = String.valueOf(s.charAt(i));
        } else {
            parts = s.split(Pattern.quote(delimiter), -1);
        }

        if (args.length >= 3) {
            int index = toInt(args[2]);
            if (index < 0) index = parts.length + index;
            if (index < 0 || index >= parts.length) return DynValue.ofNull();
            return DynValue.ofString(parts[index]);
        }

        var items = new ArrayList<DynValue>(parts.length);
        for (var p : parts) items.add(DynValue.ofString(p));
        return DynValue.ofArray(items);
    }

    private static DynValue join(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var arr = args[0].asArray();
        if (arr == null) arr = args[0].extractArray();
        if (arr == null) return DynValue.ofNull();
        var delimiter = toStr(args[1]);
        var sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(toStr(arr.get(i)));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue mask(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        var pattern = toStr(args[1]);
        var sb = new StringBuilder();
        int valueIndex = 0;
        for (int i = 0; i < pattern.length() && valueIndex < s.length(); i++) {
            char maskChar = pattern.charAt(i);
            if (maskChar == '#' || maskChar == 'A' || maskChar == '*') {
                sb.append(s.charAt(valueIndex));
                valueIndex++;
            } else {
                sb.append(maskChar);
            }
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue reverseString(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        return DynValue.ofString(new StringBuilder(toStr(args[0])).reverse().toString());
    }

    private static DynValue repeat(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int count = toInt(args[1]);
        if (count < 0) return DynValue.ofNull();
        if (count == 0) return DynValue.ofString("");
        return DynValue.ofString(s.repeat(count));
    }

    private static DynValue substring(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int start = Math.max(0, toInt(args[1]));
        if (start >= s.length()) return DynValue.ofString("");
        if (args.length >= 3) {
            int length = toInt(args[2]);
            if (length <= 0) return DynValue.ofString("");
            int end = Math.min(start + length, s.length());
            return DynValue.ofString(s.substring(start, end));
        }
        return DynValue.ofString(s.substring(start));
    }

    private static DynValue length(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofInteger(0);
        if (args[0].isNull()) return DynValue.ofInteger(0);
        var arr = args[0].asArray();
        if (arr != null) return DynValue.ofInteger(arr.size());
        var obj = args[0].asObject();
        if (obj != null) return DynValue.ofInteger(obj.size());
        return DynValue.ofInteger(toStr(args[0]).length());
    }

    private static DynValue camelCase(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var words = splitWords(toStr(args[0]));
        if (words.isEmpty()) return DynValue.ofString("");
        var sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            var word = words.get(i);
            if (word.isEmpty()) continue;
            if (i == 0) {
                sb.append(word.toLowerCase(Locale.ROOT));
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue snakeCase(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var words = splitWords(toStr(args[0]));
        var sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('_');
            sb.append(words.get(i).toLowerCase(Locale.ROOT));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue kebabCase(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var words = splitWords(toStr(args[0]));
        var sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sb.append('-');
            sb.append(words.get(i).toLowerCase(Locale.ROOT));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue pascalCase(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var words = splitWords(toStr(args[0]));
        if (words.isEmpty()) return DynValue.ofString("");
        var sb = new StringBuilder();
        for (var word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue slugify(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]).toLowerCase(Locale.ROOT);
        // Keep only ASCII word chars, whitespace, and hyphen (accents are dropped).
        s = s.replaceAll("[^A-Za-z0-9_\\s-]", "");
        // Whitespace and underscores become hyphens, then collapse and trim.
        s = s.replaceAll("[\\s_]+", "-");
        s = s.replaceAll("-+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return DynValue.ofString(s);
    }

    private static DynValue match(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        try {
            var regex = createRegex(toStr(args[1]));
            return DynValue.ofBool(regex.matcher(toStr(args[0])).find());
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue extract(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        int groupIndex = args.length >= 3 ? toInt(args[2]) : 0;
        try {
            var regex = createRegex(toStr(args[1]));
            var m = regex.matcher(toStr(args[0]));
            if (!m.find()) return DynValue.ofNull();
            if (groupIndex < 0 || groupIndex > m.groupCount()) return DynValue.ofNull();
            var g = m.group(groupIndex);
            return g != null ? DynValue.ofString(g) : DynValue.ofNull();
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue normalizeSpace(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]).trim();
        var sb = new StringBuilder(s.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace) { sb.append(' '); lastWasSpace = true; }
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue leftOf(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        var delimiter = toStr(args[1]);
        int idx = s.indexOf(delimiter);
        if (idx < 0) return DynValue.ofString(s);
        return DynValue.ofString(s.substring(0, idx));
    }

    private static DynValue rightOf(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        var delimiter = toStr(args[1]);
        int idx = s.indexOf(delimiter);
        if (idx < 0) return DynValue.ofString("");
        return DynValue.ofString(s.substring(idx + delimiter.length()));
    }

    private static DynValue wrap(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var s = toStr(args[0]);
        int width = toInt(args[1]);
        if (width <= 0) return DynValue.ofNull();
        if (s.length() <= width) return DynValue.ofString(s);

        var lines = new ArrayList<String>();
        var line = new StringBuilder();
        for (var word : s.split("\\s+")) {
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= width) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return DynValue.ofString(String.join("\n", lines));
    }

    private static DynValue center(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return args.length > 0 ? args[0] : DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        int width = toInt(args[1]);
        char padChar = args.length >= 3 ? getPadChar(args[2]) : ' ';
        if (s.length() >= width) return DynValue.ofString(s);
        int totalPad = width - s.length();
        int leftPad = totalPad / 2;
        int rightPad = totalPad - leftPad;
        return DynValue.ofString(String.valueOf(padChar).repeat(leftPad) + s + String.valueOf(padChar).repeat(rightPad));
    }

    private static DynValue matches(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofBool(false);
        if (args[0].isNull()) return DynValue.ofBool(false);
        try {
            var regex = createRegex(toStr(args[1]));
            return DynValue.ofBool(regex.matcher(toStr(args[0])).find());
        } catch (Exception e) {
            return DynValue.ofBool(false);
        }
    }

    private static DynValue stripAccents(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        return DynValue.ofString(removeAccents(toStr(args[0])));
    }

    private static DynValue clean(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]);
        // Drop control chars (keep tab/newline/CR), normalize Unicode spaces, collapse, trim.
        String cleaned = s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        cleaned = cleaned.replaceAll("[\\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return DynValue.ofString(cleaned);
    }

    // ── Text Analysis ──

    private static DynValue wordCount(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofInteger(0);
        if (args[0].isNull()) return DynValue.ofInteger(0);
        var s = toStr(args[0]).trim();
        if (s.isEmpty()) return DynValue.ofInteger(0);
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                count++;
            }
        }
        return DynValue.ofInteger(count);
    }

    private static DynValue tokenize(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofArray(new ArrayList<>());
        if (args[0].isNull()) return DynValue.ofArray(new ArrayList<>());
        var s = toStr(args[0]);
        var items = new ArrayList<DynValue>();
        String delim = args.length >= 2 ? toStr(args[1]) : "";
        if (delim.isEmpty()) {
            for (var tok : s.split("\\s+")) {
                if (!tok.isEmpty()) items.add(DynValue.ofString(tok));
            }
        } else {
            for (var tok : s.split(java.util.regex.Pattern.quote(delim), -1)) {
                var t = tok.strip();
                if (!t.isEmpty()) items.add(DynValue.ofString(t));
            }
        }
        return DynValue.ofArray(items);
    }

    private static DynValue levenshtein(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofInteger(0);
        if (args[0].isNull() || args[1].isNull()) return DynValue.ofInteger(0);
        var a = toStr(args[0]);
        var b = toStr(args[1]);
        if (a.isEmpty()) return DynValue.ofInteger(b.length());
        if (b.isEmpty()) return DynValue.ofInteger(a.length());

        int lenA = a.length(), lenB = b.length();
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];
        for (int j = 0; j <= lenB; j++) prev[j] = j;
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            var tmp = prev; prev = curr; curr = tmp;
        }
        return DynValue.ofInteger(prev[lenB]);
    }

    private static DynValue soundex(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        var s = toStr(args[0]).trim();
        if (s.isEmpty()) return DynValue.ofString("");

        var result = new StringBuilder(4);
        char firstLetter = Character.toUpperCase(s.charAt(0));
        if (!Character.isLetter(firstLetter)) return DynValue.ofString("0000");
        result.append(firstLetter);

        char prevCode = soundexCode(firstLetter);
        for (int i = 1; i < s.length() && result.length() < 4; i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (!Character.isLetter(c)) continue;
            char code = soundexCode(c);
            if (code != '0' && code != prevCode) result.append(code);
            prevCode = code;
        }

        while (result.length() < 4) result.append('0');
        return DynValue.ofString(result.toString());
    }

    private static char soundexCode(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'B', 'F', 'P', 'V' -> '1';
            case 'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> '2';
            case 'D', 'T' -> '3';
            case 'L' -> '4';
            case 'M', 'N' -> '5';
            case 'R' -> '6';
            default -> '0';
        };
    }

    // ── formatPhone ──

    private static DynValue formatPhone(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();

        String raw = toStr(args[0]);
        String country = toStr(args[1]).toUpperCase(Locale.ROOT);

        // Strip non-digit characters
        String digits = raw.replaceAll("\\D", "");

        return switch (country) {
            case "US", "CA" -> {
                String d = digits.length() == 11 && digits.charAt(0) == '1' ? digits.substring(1) : digits;
                if (d.length() != 10) yield DynValue.ofString(raw);
                yield DynValue.ofString("(" + d.substring(0, 3) + ") " + d.substring(3, 6) + "-" + d.substring(6));
            }
            case "GB" -> {
                String d = digits.startsWith("44") ? digits.substring(2) : digits;
                if (d.length() < 10 || d.length() > 11) yield DynValue.ofString(raw);
                yield DynValue.ofString("+44 " + d.substring(0, 4) + " " + d.substring(4));
            }
            case "DE" -> {
                String d = digits.startsWith("49") ? digits.substring(2) : digits;
                if (d.length() < 10 || d.length() > 11) yield DynValue.ofString(raw);
                yield DynValue.ofString("+49 " + d.substring(0, 4) + " " + d.substring(4));
            }
            case "FR" -> {
                String d = digits.startsWith("33") ? digits.substring(2) : digits;
                if (d.length() != 9) yield DynValue.ofString(raw);
                yield DynValue.ofString("+33 " + d.charAt(0) + " " + d.substring(1, 3) + " " + d.substring(3, 5) + " " + d.substring(5, 7) + " " + d.substring(7));
            }
            case "AU" -> {
                String d = digits.startsWith("61") ? digits.substring(2) : digits;
                if (d.length() != 9) yield DynValue.ofString(raw);
                yield DynValue.ofString("+61 " + d.charAt(0) + " " + d.substring(1, 5) + " " + d.substring(5));
            }
            case "JP" -> {
                String d = digits.startsWith("81") ? digits.substring(2) : digits;
                if (d.length() < 10 || d.length() > 11) yield DynValue.ofString(raw);
                yield DynValue.ofString("+81 " + d.substring(0, 2) + "-" + d.substring(2, 6) + "-" + d.substring(6));
            }
            default -> DynValue.ofString(raw);
        };
    }
}
