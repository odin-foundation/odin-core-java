package foundation.odin.utils;

import java.util.ArrayList;
import java.util.List;

public final class PathUtils {
    private PathUtils() {}

    public record Segment(String name, Integer index) {}

    public static String buildPath(String... segments) {
        var sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(segments[i]);
        }
        return sb.toString();
    }

    public static String buildPathWithIndices(Segment... segments) {
        var sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(segments[i].name());
            if (segments[i].index() != null) {
                sb.append('[');
                sb.append(segments[i].index());
                sb.append(']');
            }
        }
        return sb.toString();
    }

    public static List<String> splitPath(String path) {
        var segments = new ArrayList<String>();
        var current = new StringBuilder();

        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.' && current.length() > 0) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0)
            segments.add(current.toString());

        return segments;
    }

    public static String parentPath(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot > 0 ? path.substring(0, lastDot) : null;
    }

    public static String leafName(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    public static boolean startsWith(String path, String prefix) {
        if (!path.startsWith(prefix))
            return false;
        return path.length() == prefix.length() || path.charAt(prefix.length()) == '.' || path.charAt(prefix.length()) == '[';
    }

    public static Segment parseSegment(String segment) {
        int bracketPos = segment.indexOf('[');
        if (bracketPos < 0)
            return new Segment(segment, null);

        String name = segment.substring(0, bracketPos);
        String indexStr = segment.substring(bracketPos + 1, segment.length() - 1);
        try {
            int index = Integer.parseInt(indexStr);
            return new Segment(name, index);
        } catch (NumberFormatException e) {
            return new Segment(segment, null);
        }
    }
}
