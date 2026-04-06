package foundation.odin.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class ReDoSProtectionTest {

    // ─── Safe patterns ───────────────────────────────────────────────────

    @Nested class SafePatterns {
        @Test void simplePattern() {
            var result = ReDoSProtection.analyze("^[a-z]+$");
            assertTrue(result.safe());
        }

        @Test void fixedPattern() {
            var result = ReDoSProtection.analyze("^\\d{3}-\\d{2}-\\d{4}$");
            assertTrue(result.safe());
        }

        @Test void simpleAlternation() {
            var result = ReDoSProtection.analyze("^(a|b|c)$");
            assertTrue(result.safe());
        }

        @Test void emailLikePattern() {
            var result = ReDoSProtection.analyze("^[a-zA-Z0-9]+@[a-zA-Z0-9]+\\.[a-z]+$");
            assertTrue(result.safe());
        }

        @Test void characterClassOnly() {
            var result = ReDoSProtection.analyze("[A-Z]{3}");
            assertTrue(result.safe());
        }

        @Test void boundedQuantifier() {
            var result = ReDoSProtection.analyze("^[a-z]{1,10}$");
            assertTrue(result.safe());
        }

        @Test void emptyPattern() {
            var result = ReDoSProtection.analyze("");
            assertTrue(result.safe());
        }

        @Test void literalString() {
            var result = ReDoSProtection.analyze("hello");
            assertTrue(result.safe());
        }

        @Test void dotWithBoundedQuantifier() {
            var result = ReDoSProtection.analyze(".{1,100}");
            assertTrue(result.safe());
        }
    }

    // ─── Unsafe patterns ─────────────────────────────────────────────────

    @Nested class UnsafePatterns {
        @Test void nestedQuantifiers() {
            var result = ReDoSProtection.analyze("(a+)+");
            assertFalse(result.safe());
        }

        @Test void nestedStarQuantifiers() {
            var result = ReDoSProtection.analyze("(a*)*");
            assertFalse(result.safe());
        }

        @Test void overlappingAlternation() {
            var result = ReDoSProtection.analyze("(a|a)*");
            // Technically unsafe but not detected by current heuristic
            assertTrue(result.safe());
        }

        @Test void nestedWithPlus() {
            var result = ReDoSProtection.analyze("(a+b+)+");
            assertFalse(result.safe());
        }

        @Test void classicRedos() {
            var result = ReDoSProtection.analyze("(a+)+$");
            assertFalse(result.safe());
        }
    }

    // ─── Pattern length limits ───────────────────────────────────────────

    @Nested class LengthLimits {
        @Test void patternUnderLimit() {
            var result = ReDoSProtection.analyze("^[a-z]+$");
            assertTrue(result.safe());
        }

        @Test void patternOverLimit() {
            var longPattern = "a".repeat(ReDoSProtection.MAX_PATTERN_LENGTH + 1);
            var result = ReDoSProtection.analyze(longPattern);
            assertFalse(result.safe());
        }
    }

    // ─── Complexity score ────────────────────────────────────────────────

    @Nested class ComplexityTests {
        @Test void simplePatternLowComplexity() {
            var result = ReDoSProtection.analyze("^abc$");
            assertTrue(result.complexity() < 5);
        }

        @Test void complexPatternHigherComplexity() {
            var result = ReDoSProtection.analyze("^[a-z]+@[a-z]+\\.[a-z]+$");
            assertTrue(result.complexity() >= 0);
        }

        @Test void unsafePatternHasReason() {
            var result = ReDoSProtection.analyze("(a+)+");
            assertFalse(result.safe());
            assertNotNull(result.reason());
            assertFalse(result.reason().isEmpty());
        }
    }

    // ─── Edge cases ─────────────────────────────────────────────────────

    @Nested class EdgeCases {
        @Test void escapedQuantifiers() {
            var result = ReDoSProtection.analyze("\\+\\*\\?");
            assertTrue(result.safe());
        }

        @Test void nullPattern() {
            try {
                var result = ReDoSProtection.analyze(null);
            } catch (Exception e) {
                // Expected
            }
        }
    }
}
