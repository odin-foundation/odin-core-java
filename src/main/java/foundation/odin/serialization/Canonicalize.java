package foundation.odin.serialization;

import foundation.odin.types.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Canonicalize {
    private Canonicalize() {}

    public static byte[] serialize(OdinDocument doc) {
        // Collect ALL paths: metadata as $.key, plus all assignments
        var allPaths = new ArrayList<Map.Entry<String, OdinValue>>();

        for (var entry : doc.getMetadata()) {
            allPaths.add(Map.entry("$." + entry.getKey(), entry.getValue()));
        }
        for (var entry : doc.getAssignments().entries()) {
            // Skip $.xxx entries — already added from metadata
            if (entry.getKey().startsWith("$.")) continue;
            allPaths.add(entry);
        }

        // Sort using canonical path comparison
        allPaths.sort((a, b) -> canonicalPathCompare(a.getKey(), b.getKey()));

        var sb = new StringBuilder();
        for (var entry : allPaths) {
            String path = entry.getKey();
            OdinValue value = entry.getValue();

            sb.append(path);
            sb.append(" = ");

            // Write modifiers
            OdinModifiers mods = value.getModifiers();
            if (mods == null) {
                mods = doc.getPathModifiers().tryGet(path);
            }
            if (mods != null) {
                Stringify.writeModifiers(sb, mods);
            }

            writeCanonicalValue(sb, value);
            sb.append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static void writeCanonicalValue(StringBuilder sb, OdinValue value) {
        switch (value) {
            case OdinValue.OdinNull n -> sb.append('~');
            case OdinValue.OdinBoolean b -> sb.append(b.getValue() ? "true" : "false");
            case OdinValue.OdinString s -> {
                sb.append('"');
                Stringify.writeEscapedString(sb, s.getValue());
                sb.append('"');
            }
            case OdinValue.OdinInteger i -> {
                sb.append("##");
                // Prefer raw to preserve integers beyond long range
                sb.append(i.getRaw() != null ? i.getRaw() : Long.toString(i.getValue()));
            }
            case OdinValue.OdinNumber n -> {
                sb.append('#');
                // Prefer raw to preserve precision beyond double range
                sb.append(n.getRaw() != null
                        ? formatCanonicalNumber(n.getRaw())
                        : formatCanonicalNumber(n.getValue()));
            }
            case OdinValue.OdinCurrency c -> {
                sb.append("#$");
                sb.append(formatCanonicalCurrency(c));
                if (c.getCurrencyCode() != null) {
                    sb.append(':');
                    sb.append(c.getCurrencyCode().toUpperCase());
                }
            }
            case OdinValue.OdinPercent p ->
                sb.append(p.getRaw() != null ? "#%" + p.getRaw() : "#%" + p.getValue());
            case OdinValue.OdinDate d -> sb.append(d.getRaw());
            case OdinValue.OdinTimestamp ts -> sb.append(ts.getRaw());
            case OdinValue.OdinTime t -> sb.append(t.getValue());
            case OdinValue.OdinDuration dur -> sb.append(dur.getValue());
            case OdinValue.OdinReference r -> {
                sb.append('@');
                sb.append(r.getPath());
            }
            case OdinValue.OdinBinary bin -> {
                sb.append('^');
                if (bin.getAlgorithm() != null) {
                    sb.append(bin.getAlgorithm());
                    sb.append(':');
                }
                sb.append(Base64.getEncoder().encodeToString(bin.getData()));
            }
            default -> Stringify.writeValue(sb, value);
        }

        // Write trailing directives
        for (var directive : value.getDirectives()) {
            Stringify.writeDirective(sb, directive);
        }
    }

    /**
     * Format a number stripping trailing zeros.
     */
    static String formatCanonicalNumber(double value) {
        return formatCanonicalNumber(Double.toString(value));
    }

    /**
     * Format a number string stripping trailing zeros.
     */
    static String formatCanonicalNumber(String s) {
        // Strip trailing zeros after decimal point
        if (s.contains(".") && !s.contains("e") && !s.contains("E")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Format a currency value with at least 2 decimal places.
     * Prefers raw to preserve precision and integer parts beyond double range.
     */
    static String formatCanonicalCurrency(OdinValue.OdinCurrency c) {
        String raw = c.getRaw();
        if (raw != null) {
            // Raw may carry a trailing :code suffix; keep only the numeric part
            int colon = raw.indexOf(':');
            if (colon >= 0) raw = raw.substring(0, colon);
            boolean negative = raw.startsWith("-");
            String unsigned = negative ? raw.substring(1) : raw;
            int dotIndex = unsigned.indexOf('.');
            String intPart = dotIndex < 0 ? unsigned : unsigned.substring(0, dotIndex);
            String fracPart = dotIndex < 0 ? "" : unsigned.substring(dotIndex + 1);
            StringBuilder frac = new StringBuilder(fracPart);
            while (frac.length() < 2) frac.append('0');
            return (negative ? "-" : "") + intPart + "." + frac;
        }
        int dp = Math.max(c.getDecimalPlaces(), 2);
        return String.format("%." + dp + "f", c.getValue());
    }

    /**
     * Canonical path comparison:
     * 1. $ metadata paths sort first
     * 2. & extension paths sort last
     * 3. Segment-by-segment, array indices sort numerically
     */
    static int canonicalPathCompare(String a, String b) {
        boolean aIsMeta = a.startsWith("$");
        boolean bIsMeta = b.startsWith("$");
        if (aIsMeta && !bIsMeta) return -1;
        if (!aIsMeta && bIsMeta) return 1;

        boolean aIsExt = a.startsWith("&");
        boolean bIsExt = b.startsWith("&");
        if (aIsExt && !bIsExt) return 1;
        if (!aIsExt && bIsExt) return -1;

        var aSegs = splitPathSegments(a);
        var bSegs = splitPathSegments(b);

        for (int i = 0; i < Math.min(aSegs.size(), bSegs.size()); i++) {
            String aSeg = aSegs.get(i);
            String bSeg = bSegs.get(i);

            Integer aIdx = parseArrayIndex(aSeg);
            Integer bIdx = parseArrayIndex(bSeg);
            if (aIdx != null && bIdx != null) {
                if (!aIdx.equals(bIdx)) return aIdx - bIdx;
                continue;
            }

            int cmp = aSeg.compareTo(bSeg);
            if (cmp != 0) return cmp;
        }

        return aSegs.size() - bSegs.size();
    }

    static List<String> splitPathSegments(String path) {
        var segments = new ArrayList<String>();
        var current = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch == '.') {
                if (current.length() > 0) segments.add(current.toString());
                current.setLength(0);
            } else if (ch == '[') {
                if (current.length() > 0) segments.add(current.toString());
                int end = path.indexOf(']', i);
                if (end == -1) {
                    segments.add(path.substring(i));
                    return segments;
                }
                segments.add(path.substring(i, end + 1));
                current.setLength(0);
                i = end;
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) segments.add(current.toString());
        return segments;
    }

    static Integer parseArrayIndex(String segment) {
        if (segment.startsWith("[") && segment.endsWith("]")) {
            try {
                return Integer.parseInt(segment.substring(1, segment.length() - 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
