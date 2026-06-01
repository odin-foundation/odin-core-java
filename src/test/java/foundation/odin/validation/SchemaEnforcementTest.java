package foundation.odin.validation;

import foundation.odin.Odin;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema-validation enforcement: invariant evaluation, currency and percent bounds,
 * override narrowing, intersection conflicts, tabular columns, and default-value rules.
 */
class SchemaEnforcementTest {

    private static final String H = "{$}\nodin = \"1.0.0\"\nschema = \"1.0.0\"\n\n";

    private static OdinSchema.ValidationResult run(String schemaText, String inputText) {
        var schema = Odin.parseSchema(H + schemaText);
        OdinDocument doc = inputText.isEmpty() ? OdinDocument.empty() : Odin.parse(inputText);
        return Odin.validate(doc, schema);
    }

    private static List<String> codesAt(OdinSchema.ValidationResult result, String path) {
        return result.errors().stream()
                .filter(e -> e.path().equals(path))
                .map(OdinSchema.ValidationError::code)
                .toList();
    }

    @Nested class InvariantEvaluation {
        @Test void passesThreeTermAdditive() {
            var r = run(
                    "{order}\nsubtotal = #$\ntax = #$\nshipping = #$\ntotal = #$\n:invariant total = subtotal + tax + shipping",
                    "{order}\nsubtotal = #$10.00\ntax = #$1.00\nshipping = #$2.00\ntotal = #$13.00");
            assertTrue(r.valid());
        }

        @Test void failsThreeTermAdditiveWhenInconsistent() {
            var r = run(
                    "{order}\nsubtotal = #$\ntax = #$\nshipping = #$\ntotal = #$\n:invariant total = subtotal + tax + shipping",
                    "{order}\nsubtotal = #$10.00\ntax = #$1.00\nshipping = #$2.00\ntotal = #$99.00");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "order").contains("V008"));
        }

        @Test void evaluatesParenthesesAndPrecedence() {
            var schema = "{discount}\nsubtotal = #$\npercentage = #\nfixed_amount = #$\ntotal = #$\n"
                    + ":invariant total = subtotal - (subtotal * percentage / 100) - fixed_amount";
            assertTrue(run(schema, "{discount}\nsubtotal = #$100.00\npercentage = #10\nfixed_amount = #$5.00\ntotal = #$85.00").valid());
            assertFalse(run(schema, "{discount}\nsubtotal = #$100.00\npercentage = #10\nfixed_amount = #$5.00\ntotal = #$80.00").valid());
        }

        @Test void evaluatesLogicalOr() {
            var schema = "{discount}\npercentage = #\nfixed_amount = #$\n:invariant percentage == 0 || fixed_amount == 0";
            assertTrue(run(schema, "{discount}\npercentage = #0\nfixed_amount = #$5.00").valid());
            assertFalse(run(schema, "{discount}\npercentage = #10\nfixed_amount = #$5.00").valid());
        }

        @Test void evaluatesLogicalAndAndNegation() {
            var schema = "{f}\na = #\nb = #\n:invariant !(a > 10) && b < 5";
            assertTrue(run(schema, "{f}\na = #3\nb = #2").valid());
            assertFalse(run(schema, "{f}\na = #20\nb = #2").valid());
        }

        @Test void evaluatesModulo() {
            var schema = "{n}\nx = ##\n:invariant x % 2 == 0";
            assertTrue(run(schema, "{n}\nx = ##4").valid());
            assertFalse(run(schema, "{n}\nx = ##5").valid());
        }

        @Test void comparesTemporalOperands() {
            var schema = "{r}\nstart = date\nend = date\n:invariant end >= start";
            assertTrue(run(schema, "{r}\nstart = 2020-01-01\nend = 2020-02-01").valid());
            assertFalse(run(schema, "{r}\nstart = 2020-03-01\nend = 2020-02-01").valid());
        }

        @Test void treatsNullOperandAsFalse() {
            var r = run(
                    "{o}\ntotal = #$\nsubtotal = #$\ntax = ~#$\n:invariant total = subtotal + tax",
                    "{o}\ntotal = #$10.00\nsubtotal = #$10.00\ntax = ~");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "o").contains("V008"));
        }

        @Test void inapplicableWhenOperandAbsent() {
            var r = run(
                    "{o}\ntotal = #$\nsubtotal = #$\ntax = #$\n:invariant total = subtotal + tax",
                    "{o}\ntotal = #$10.00");
            assertTrue(r.valid());
        }

        @Test void reportsMalformedExpressionAsV008() {
            var r = run("{o}\nx = #\n:invariant x + + ", "{o}\nx = #1");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "o").contains("V008"));
        }
    }

    @Nested class CurrencyDecimalPlaces {
        @Test void acceptsDeclaredPlaces() {
            assertTrue(run("{w}\nbtc = #$.8", "{w}\nbtc = #$1.00000000").valid());
        }

        @Test void rejectsTooFewPlaces() {
            var r = run("{w}\nbtc = #$.8", "{w}\nbtc = #$1.00");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "w.btc").contains("V003"));
        }

        @Test void defaultsToTwoPlaces() {
            assertTrue(run("{w}\nprice = #$", "{w}\nprice = #$9.99").valid());
            assertFalse(run("{w}\nprice = #$", "{w}\nprice = #$9.999").valid());
        }
    }

    @Nested class PercentBounds {
        @Test void acceptsInRange() {
            assertTrue(run("{r}\nrate = #%:(0..1)", "{r}\nrate = #%0.5").valid());
        }

        @Test void rejectsOutOfRange() {
            var r = run("{r}\nrate = #%:(0..1)", "{r}\nrate = #%1.5");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "r.rate").contains("V003"));
        }

        @Test void rejectsBelowMinimum() {
            assertFalse(run("{r}\nrate = #%:(0.1..1)", "{r}\nrate = #%0.05").valid());
        }
    }

    @Nested class OverrideRestrictiveness {
        @Test void acceptsNarrowingBounds() {
            assertTrue(run("{@base}\namount = #$:(0..1000)\n\n{@narrow}\n= @base :override\namount = #$:(0..100)", "").valid());
        }

        @Test void rejectsWideningBounds() {
            var r = run("{@base}\namount = #$:(0..100)\n\n{@wide}\n= @base :override\namount = #$:(0..1000)", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@wide.amount").contains("V017"));
        }

        @Test void allowsOptionalToRequiredNotReverse() {
            assertTrue(run("{@base}\nname =\n\n{@d}\n= @base :override\nname = !", "").valid());
            var r = run("{@base}\nname = !\n\n{@d}\n= @base :override\nname =", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@d.name").contains("V017"));
        }

        @Test void allowsRemovingNullabilityNotAdding() {
            assertTrue(run("{@base}\nx = ~#\n\n{@d}\n= @base :override\nx = #", "").valid());
            var r = run("{@base}\nx = #\n\n{@d}\n= @base :override\nx = ~#", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@d.x").contains("V017"));
        }

        @Test void rejectsChangingBaseType() {
            var r = run("{@base}\nx = #\n\n{@d}\n= @base :override\nx =", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@d.x").contains("V017"));
        }

        @Test void enforcesOnPathLevelCompositions() {
            var r = run("{@base}\namount = #$:(0..100)\n\n{order}\n= @base :override\namount = #$:(0..1000)", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "order.amount").contains("V017"));
        }

        @Test void doesNotFlagUntouchedFields() {
            assertTrue(run("{@base}\na = #$:(0..100)\nb = !\n\n{@d}\n= @base :override\na = #$:(0..50)", "").valid());
        }
    }

    @Nested class IntersectionConflicts {
        @Test void rejectsDifferingSameNameFields() {
            var r = run("{@a}\nx = !\n\n{@b}\nx = !##\n\n{cust}\n= @a & @b", "{cust}\nx = ##5");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@cust.x").contains("V017"));
        }

        @Test void acceptsDisjointOrIdenticalFields() {
            assertTrue(run("{@a}\nx = !\nname = !\n\n{@b}\nx = !\nage = !##\n\n{cust}\n= @a & @b",
                    "{cust}\nx = \"hi\"\nname = \"n\"\nage = ##5").valid());
        }

        @Test void reportsConflictForThreeWayIntersection() {
            var r = run("{@a}\nx = !\n\n{@b}\ny = !\n\n{@c}\nx = !##\n\n{cust}\n= @a & @b & @c", "{cust}\nx = \"hi\"\ny = \"z\"");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "@cust.x").contains("V017"));
        }
    }

    @Nested class TabularColumns {
        @Test void acceptsPrimitiveColumns() {
            assertTrue(run("{contacts[] : name, email}\nname = !\nemail = !", "{contacts[0]}\nname = \"a\"\nemail = \"b\"").valid());
        }

        @Test void rejectsTypeRefColumn() {
            var r = run("{@addr}\nline1 = !\n\n{customers[] : name, address}\nname = !\naddress = @addr", "{customers[0]}\nname = \"a\"");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "customers[].address").contains("V017"));
        }

        @Test void acceptsSingleLevelColumns() {
            assertTrue(run("{rows[] : id, label}\nid = !##\nlabel = !", "{rows[0]}\nid = ##1\nlabel = \"x\"").valid());
        }
    }

    @Nested class DefaultValueRules {
        @Test void acceptsDefaultWithinConstraints() {
            assertTrue(run("{root}\npriority = ##:(1..5) ##3", "").valid());
        }

        @Test void rejectsDefaultOnRequired() {
            var r = run("{root}\nstatus = !(\"a\", \"b\") \"a\"", "{root}\nstatus = \"a\"");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "root.status").contains("V017"));
        }

        @Test void rejectsDefaultThatViolatesBounds() {
            var r = run("{root}\npriority = ##:(1..5) ##9", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "root.priority").contains("V017"));
        }

        @Test void rejectsDefaultOutsideEnum() {
            var r = run("{root}\nstatus = (\"a\", \"b\") \"c\"", "");
            assertFalse(r.valid());
            assertTrue(codesAt(r, "root.status").contains("V017"));
        }

        @Test void acceptsDefaultMatchingEnum() {
            assertTrue(run("{root}\nstatus = (\"a\", \"b\") \"a\"", "").valid());
        }
    }
}
