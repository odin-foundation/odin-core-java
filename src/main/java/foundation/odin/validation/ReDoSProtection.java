package foundation.odin.validation;

public final class ReDoSProtection {

    private ReDoSProtection() {}

    public static final int MAX_PATTERN_LENGTH = 1024;
    public static final int MAX_NESTING_DEPTH = 10;
    public static final int MAX_QUANTIFIERS = 20;

    public static RedosAnalysis analyze(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new RedosAnalysis(true, null, 0);
        }

        if (pattern.length() > MAX_PATTERN_LENGTH) {
            return new RedosAnalysis(false, "Pattern exceeds maximum length", pattern.length());
        }

        int maxDepth = 0;
        int currentDepth = 0;
        int quantifierCount = 0;
        boolean inCharClass = false;
        boolean escaped = false;

        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }

            if (ch == '[' && !inCharClass) {
                inCharClass = true;
                continue;
            }

            if (ch == ']' && inCharClass) {
                inCharClass = false;
                continue;
            }

            if (inCharClass) continue;

            if (ch == '(') {
                currentDepth++;
                if (currentDepth > maxDepth) maxDepth = currentDepth;
            } else if (ch == ')') {
                if (currentDepth > 0) currentDepth--;
            } else if (ch == '+' || ch == '*' || ch == '?') {
                quantifierCount++;
            } else if (ch == '{') {
                quantifierCount++;
            }
        }

        if (maxDepth > MAX_NESTING_DEPTH) {
            return new RedosAnalysis(false, "Nesting depth exceeds limit", computeComplexity(quantifierCount, maxDepth, pattern.length()));
        }

        if (quantifierCount > MAX_QUANTIFIERS) {
            return new RedosAnalysis(false, "Too many quantifiers", computeComplexity(quantifierCount, maxDepth, pattern.length()));
        }

        if (hasNestedQuantifiers(pattern)) {
            return new RedosAnalysis(false, "Nested quantifiers detected", computeComplexity(quantifierCount, maxDepth, pattern.length()));
        }

        return new RedosAnalysis(true, null, computeComplexity(quantifierCount, maxDepth, pattern.length()));
    }

    private static boolean hasNestedQuantifiers(String pattern) {
        var stack = new java.util.ArrayDeque<Boolean>();
        boolean escaped = false;
        boolean inCharClass = false;

        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (ch == '\\') {
                escaped = true;
                continue;
            }

            if (ch == '[' && !inCharClass) {
                inCharClass = true;
                continue;
            }
            if (ch == ']' && inCharClass) {
                inCharClass = false;
                continue;
            }
            if (inCharClass) continue;

            if (ch == '(') {
                stack.push(false);
            } else if (ch == ')') {
                boolean groupHadQuantifier = !stack.isEmpty() && stack.pop();

                if (groupHadQuantifier && i + 1 < pattern.length()) {
                    char next = pattern.charAt(i + 1);
                    if (next == '+' || next == '*' || next == '?' || next == '{') {
                        return true;
                    }
                }
            } else if (ch == '+' || ch == '*' || ch == '?') {
                if (!stack.isEmpty()) {
                    stack.pop();
                    stack.push(true);
                }
            } else if (ch == '{') {
                if (!stack.isEmpty()) {
                    stack.pop();
                    stack.push(true);
                }
            }
        }

        return false;
    }

    private static int computeComplexity(int quantifiers, int depth, int length) {
        return (quantifiers * 2) + (depth * 3) + (length / 10);
    }

    public record RedosAnalysis(boolean safe, String reason, int complexity) {}
}
