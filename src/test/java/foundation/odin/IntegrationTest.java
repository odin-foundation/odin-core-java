package foundation.odin;

import foundation.odin.types.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static OdinDocument roundtrip(String input) {
        var d = Odin.parse(input);
        var text = Odin.serialize(d);
        return Odin.parse(text);
    }

    private static void assertConsistent(String input) {
        var d1 = Odin.parse(input);
        var t1 = Odin.serialize(d1);
        var d2 = Odin.parse(t1);
        var t2 = Odin.serialize(d2);
        assertEquals(t1, t2, "Parse→Stringify not stable for: " + input);
    }

    private static void assertPatchRoundtrip(String src, String dst) {
        var d1 = Odin.parse(src);
        var d2 = Odin.parse(dst);
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        var diff2 = Odin.diff(patched, d2);
        assertTrue(diff2.isEmpty(), "Patch roundtrip failed for: " + src + " -> " + dst);
    }

    private static OdinDocument builderRoundtrip(String key, OdinValue val) {
        var d = Odin.builder().set(key, val).build();
        var text = Odin.serialize(d);
        return Odin.parse(text);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Roundtrip Tests (Parse -> Stringify -> Parse preserves values)
    // ═══════════════════════════════════════════════════════════════════════

    @Test void roundtrip_SimpleString() { var d = roundtrip("name = \"Alice\"\n"); assertEquals("Alice", d.getString("name")); }
    @Test void roundtrip_Integer() { var d = roundtrip("x = ##42\n"); assertEquals(42L, d.getInteger("x")); }
    @Test void roundtrip_NegativeInteger() { var d = roundtrip("x = ##-5\n"); assertEquals(-5L, d.getInteger("x")); }
    @Test void roundtrip_ZeroInteger() { var d = roundtrip("x = ##0\n"); assertEquals(0L, d.getInteger("x")); }
    @Test void roundtrip_Number() { var d = roundtrip("x = #3.14\n"); assertTrue(Math.abs(d.getNumber("x") - 3.14) < 0.001); }
    @Test void roundtrip_BooleanTrue() { var d = roundtrip("x = true\n"); assertEquals(true, d.getBoolean("x")); }
    @Test void roundtrip_BooleanFalse() { var d = roundtrip("x = false\n"); assertEquals(false, d.getBoolean("x")); }
    @Test void roundtrip_Null() { var d = roundtrip("x = ~\n"); assertTrue(d.get("x").isNull()); }
    @Test void roundtrip_Currency() { var d = roundtrip("x = #$99.99\n"); assertTrue(d.get("x").isCurrency()); }
    @Test void roundtrip_Percent() { var d = roundtrip("x = #%50\n"); assertTrue(d.get("x").isPercent()); }
    @Test void roundtrip_Date() { var d = roundtrip("x = 2024-01-15\n"); assertTrue(d.get("x").isDate()); }
    @Test void roundtrip_Timestamp() { var d = roundtrip("x = 2024-01-15T10:30:00Z\n"); assertTrue(d.get("x").isTimestamp()); }
    @Test void roundtrip_Time() { var d = roundtrip("x = T14:30:00\n"); assertNotNull(d.get("x")); }
    @Test void roundtrip_Duration() { var d = roundtrip("x = P1Y2M3D\n"); assertNotNull(d.get("x")); }
    @Test void roundtrip_Reference() { var d = roundtrip("x = @other\n"); assertTrue(d.get("x").isReference()); }
    @Test void roundtrip_Binary() { var d = roundtrip("x = ^SGVsbG8=\n"); assertTrue(d.get("x").isBinary()); }
    @Test void roundtrip_EmptyString() { var d = roundtrip("x = \"\"\n"); assertEquals("", d.getString("x")); }
    @Test void roundtrip_StringWithSpaces() { var d = roundtrip("x = \"  spaces  \"\n"); assertEquals("  spaces  ", d.getString("x")); }
    @Test void roundtrip_StringWithEscape() { var d = roundtrip("x = \"a\\nb\"\n"); assertEquals("a\nb", d.getString("x")); }
    @Test void roundtrip_LargeInteger() { var d = roundtrip("x = ##2147483647\n"); assertEquals(2147483647L, d.getInteger("x")); }

    @Test void roundtrip_Section() {
        var d = roundtrip("{S}\nname = \"Alice\"\n");
        assertEquals("Alice", d.getString("S.name"));
    }

    @Test void roundtrip_NestedSection() {
        var d = roundtrip("{A}\n{A.B}\nfield = ##1\n");
        assertEquals(1L, d.getInteger("A.B.field"));
    }

    @Test void roundtrip_Array() {
        var d = roundtrip("items[0] = \"a\"\nitems[1] = \"b\"\n");
        assertEquals("a", d.getString("items[0]"));
        assertEquals("b", d.getString("items[1]"));
    }

    @Test void roundtrip_MultipleFields() {
        var d = roundtrip("a = ##1\nb = ##2\nc = ##3\n");
        assertEquals(1L, d.getInteger("a"));
        assertEquals(2L, d.getInteger("b"));
        assertEquals(3L, d.getInteger("c"));
    }

    @Test void roundtrip_RequiredModifier() { var d = roundtrip("x = !\"val\"\n"); assertTrue(d.get("x").isRequired()); }
    @Test void roundtrip_ConfidentialModifier() { var d = roundtrip("x = *\"secret\"\n"); assertTrue(d.get("x").isConfidential()); }
    @Test void roundtrip_DeprecatedModifier() { var d = roundtrip("x = -\"old\"\n"); assertTrue(d.get("x").isDeprecated()); }

    // ═══════════════════════════════════════════════════════════════════════
    // Parse All Types
    // ═══════════════════════════════════════════════════════════════════════

    @Test void parseType_String() { var d = Odin.parse("x = \"hello\"\n"); assertEquals("hello", d.getString("x")); }
    @Test void parseType_Integer() { var d = Odin.parse("x = ##42\n"); assertEquals(42L, d.getInteger("x")); }
    @Test void parseType_Number() { var d = Odin.parse("x = #3.14\n"); assertTrue(Math.abs(d.getNumber("x") - 3.14) < 0.001); }
    @Test void parseType_Boolean() { var d = Odin.parse("x = true\n"); assertEquals(true, d.getBoolean("x")); }
    @Test void parseType_Null() { var d = Odin.parse("x = ~\n"); assertTrue(d.get("x").isNull()); }
    @Test void parseType_Currency() { var d = Odin.parse("x = #$99.99\n"); assertTrue(d.get("x").isCurrency()); }
    @Test void parseType_Percent() { var d = Odin.parse("x = #%50\n"); assertTrue(d.get("x").isPercent()); }
    @Test void parseType_Date() { var d = Odin.parse("x = 2024-01-15\n"); assertTrue(d.get("x").isDate()); }
    @Test void parseType_Timestamp() { var d = Odin.parse("x = 2024-01-15T10:30:00Z\n"); assertTrue(d.get("x").isTimestamp()); }
    @Test void parseType_Reference() { var d = Odin.parse("x = @other\n"); assertTrue(d.get("x").isReference()); }
    @Test void parseType_Binary() { var d = Odin.parse("x = ^SGVsbG8=\n"); assertTrue(d.get("x").isBinary()); }

    // ═══════════════════════════════════════════════════════════════════════
    // Modifier Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void modifier_RequiredString() { var d = Odin.parse("x = !\"val\"\n"); assertTrue(d.get("x").isRequired()); }
    @Test void modifier_RequiredInteger() { var d = Odin.parse("x = !##42\n"); assertTrue(d.get("x").isRequired()); }
    @Test void modifier_RequiredBoolean() { var d = Odin.parse("x = !true\n"); assertTrue(d.get("x").isRequired()); }
    @Test void modifier_ConfidentialString() { var d = Odin.parse("x = *\"secret\"\n"); assertTrue(d.get("x").isConfidential()); }
    @Test void modifier_ConfidentialInteger() { var d = Odin.parse("x = *##42\n"); assertTrue(d.get("x").isConfidential()); }
    @Test void modifier_DeprecatedString() { var d = Odin.parse("x = -\"old\"\n"); assertTrue(d.get("x").isDeprecated()); }
    @Test void modifier_DeprecatedNumber() { var d = Odin.parse("x = -#3.14\n"); assertTrue(d.get("x").isDeprecated()); }

    @Test void modifier_Combined() {
        var d = Odin.parse("x = !-*\"secret\"\n");
        assertTrue(d.get("x").isRequired());
        assertTrue(d.get("x").isDeprecated());
        assertTrue(d.get("x").isConfidential());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error Handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test void error_ParseEmptyInput() {
        var d = Odin.parse("");
        assertEquals(0, d.getAssignments().size());
    }

    @Test void error_ParseOnlyWhitespace() {
        var d = Odin.parse("   \n\n  \n");
        assertEquals(0, d.getAssignments().size());
    }

    @Test void error_ParseOnlyComments() {
        var d = Odin.parse("; comment\n; another\n");
        assertEquals(0, d.getAssignments().size());
    }

    @Test void error_UnterminatedString() {
        assertThrows(Exception.class, () -> Odin.parse("x = \"unterminated\n"));
    }

    @Test void error_NegativeArrayIndex() {
        assertThrows(Exception.class, () -> Odin.parse("items[-1] = \"bad\"\n"));
    }

    @Test void error_NonContiguousArray() {
        assertThrows(Exception.class, () -> Odin.parse("items[0] = \"a\"\nitems[2] = \"c\"\n"));
    }

    @Test void error_MissingEquals() {
        assertThrows(Exception.class, () -> Odin.parse("x \"value\"\n"));
    }

    @Test void error_InvalidNumberPrefix() {
        assertThrows(Exception.class, () -> Odin.parse("x = #abc\n"));
    }

    @Test void error_InvalidIntegerPrefix() {
        assertThrows(Exception.class, () -> Odin.parse("x = ##abc\n"));
    }

    @Test void error_UnterminatedSection() {
        assertThrows(Exception.class, () -> Odin.parse("{Unterminated\nx = ##1\n"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Section Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void section_Simple() { var d = Odin.parse("{Person}\nname = \"Alice\"\n"); assertEquals("Alice", d.getString("Person.name")); }
    @Test void section_Nested() { var d = Odin.parse("{A}\n{A.B}\nfield = ##1\n"); assertEquals(1L, d.getInteger("A.B.field")); }

    @Test void section_Multiple() {
        var d = Odin.parse("{A}\nx = ##1\n{B}\ny = ##2\n");
        assertEquals(1L, d.getInteger("A.x"));
        assertEquals(2L, d.getInteger("B.y"));
    }

    @Test void section_WithArray() {
        var d = Odin.parse("{S}\nitems[0] = \"a\"\nitems[1] = \"b\"\n");
        assertEquals("a", d.getString("S.items[0]"));
    }

    @Test void section_MultipleFields() {
        var d = Odin.parse("{Config}\na = ##1\nb = ##2\nc = ##3\n");
        assertEquals(1L, d.getInteger("Config.a"));
        assertEquals(3L, d.getInteger("Config.c"));
    }

    @Test void section_DeeplyNested() {
        var d = Odin.parse("{A}\n{A.B}\n{A.B.C}\nf = ##1\n");
        assertEquals(1L, d.getInteger("A.B.C.f"));
    }

    @Test void section_WithModifiers() {
        var d = Odin.parse("{Secure}\npassword = *\"secret\"\nid = !##42\n");
        assertTrue(d.get("Secure.password").isConfidential());
        assertTrue(d.get("Secure.id").isRequired());
    }

    @Test void section_WithComments() {
        var d = Odin.parse("; top comment\n{Section}\n; field comment\nf = ##1\n");
        assertEquals(1L, d.getInteger("Section.f"));
    }

    @Test void section_ManySections() {
        var sb = new StringBuilder();
        for (int i = 0; i < 20; i++)
            sb.append("{S").append(i).append("}\nfield = ##").append(i).append('\n');
        var d = Odin.parse(sb.toString());
        assertEquals(0L, d.getInteger("S0.field"));
        assertEquals(19L, d.getInteger("S19.field"));
    }

    @Test void section_WithArrays() {
        var d = Odin.parse("{List}\nitems[0] = \"first\"\nitems[1] = \"second\"\nitems[2] = \"third\"\n");
        assertEquals("first", d.getString("List.items[0]"));
        assertEquals("third", d.getString("List.items[2]"));
    }

    @Test void section_RootFieldBeforeSection() {
        var d = Odin.parse("top = ##1\n{S}\nbottom = ##2\n");
        assertEquals(1L, d.getInteger("top"));
        assertEquals(2L, d.getInteger("S.bottom"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Metadata Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void metadata_ParseSection() {
        var d = Odin.parse("{$}\nodin = \"1.0.0\"\n\nname = \"doc\"\n");
        assertEquals("doc", d.getString("name"));
    }

    @Test void metadata_Version() {
        var d = Odin.parse("{$}\nodin = \"1.0.0\"\n");
        assertNotNull(d.getMetadata());
        assertTrue(d.getMetadata().size() > 0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Diff Integration
    // ═══════════════════════════════════════════════════════════════════════

    @Test void diff_Identical() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("x = ##1\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.isEmpty());
    }

    @Test void diff_Added() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("x = ##1\ny = ##2\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.added().size() > 0);
    }

    @Test void diff_Removed() {
        var d1 = Odin.parse("x = ##1\ny = ##2\n");
        var d2 = Odin.parse("x = ##1\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.removed().size() > 0);
    }

    @Test void diff_Changed() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("x = ##2\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_PatchRoundtrip() {
        var d1 = Odin.parse("name = \"Alice\"\nage = ##25\n");
        var d2 = Odin.parse("name = \"Bob\"\nage = ##30\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        assertEquals("Bob", patched.getString("name"));
        assertEquals(30L, patched.getInteger("age"));
    }

    @Test void diff_PatchThenDiffEmpty() {
        var d1 = Odin.parse("x = ##1\ny = ##2\n");
        var d2 = Odin.parse("x = ##10\ny = ##20\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        var diff2 = Odin.diff(patched, d2);
        assertTrue(diff2.isEmpty());
    }

    @Test void diff_EmptyToPopulated() {
        var d1 = Odin.parse("");
        var d2 = Odin.parse("x = ##1\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.added().size() > 0);
    }

    @Test void diff_PopulatedToEmpty() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.removed().size() > 0);
    }

    @Test void diff_TypeChange() {
        var d1 = Odin.parse("x = \"42\"\n");
        var d2 = Odin.parse("x = ##42\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_StringToString() {
        var d1 = Odin.parse("x = \"hello\"\n");
        var d2 = Odin.parse("x = \"world\"\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_BooleanChange() {
        var d1 = Odin.parse("x = true\n");
        var d2 = Odin.parse("x = false\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_MultipleAdds() {
        var d1 = Odin.parse("");
        var d2 = Odin.parse("a = ##1\nb = ##2\nc = ##3\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.added().size() >= 3);
    }

    @Test void diff_MultipleRemoves() {
        var d1 = Odin.parse("a = ##1\nb = ##2\nc = ##3\n");
        var d2 = Odin.parse("");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.removed().size() >= 3);
    }

    @Test void diff_PatchAddField() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("x = ##1\ny = ##2\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        assertEquals(2L, patched.getInteger("y"));
    }

    @Test void diff_PatchRemoveField() {
        var d1 = Odin.parse("x = ##1\ny = ##2\n");
        var d2 = Odin.parse("x = ##1\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        assertNull(patched.get("y"));
    }

    @Test void diff_PatchChangeValue() {
        var d1 = Odin.parse("x = \"old\"\n");
        var d2 = Odin.parse("x = \"new\"\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        assertEquals("new", patched.getString("x"));
    }

    @Test void diff_SectionFieldAdded() {
        var d1 = Odin.parse("{S}\na = ##1\n");
        var d2 = Odin.parse("{S}\na = ##1\nb = ##2\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.added().size() > 0);
    }

    @Test void diff_SectionFieldRemoved() {
        var d1 = Odin.parse("{S}\na = ##1\nb = ##2\n");
        var d2 = Odin.parse("{S}\na = ##1\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.removed().size() > 0);
    }

    @Test void diff_SectionFieldChanged() {
        var d1 = Odin.parse("{S}\na = ##1\n");
        var d2 = Odin.parse("{S}\na = ##99\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_PatchRoundtripComplex() {
        var d1 = Odin.parse("{A}\nx = ##1\ny = \"hello\"\n{B}\nz = true\n");
        var d2 = Odin.parse("{A}\nx = ##99\ny = \"world\"\n{B}\nz = false\nw = ##42\n");
        var diff = Odin.diff(d1, d2);
        var patched = Odin.patch(d1, diff);
        assertEquals(99L, patched.getInteger("A.x"));
        assertEquals("world", patched.getString("A.y"));
        assertEquals(false, patched.getBoolean("B.z"));
    }

    @Test void diff_NullToValue() {
        var d1 = Odin.parse("x = ~\n");
        var d2 = Odin.parse("x = ##42\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    @Test void diff_ValueToNull() {
        var d1 = Odin.parse("x = ##42\n");
        var d2 = Odin.parse("x = ~\n");
        var diff = Odin.diff(d1, d2);
        assertTrue(diff.changed().size() > 0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Extended Diff-Patch Roundtrip
    // ═══════════════════════════════════════════════════════════════════════

    @Test void patchRt_Add() { assertPatchRoundtrip("x = ##1\n", "x = ##1\ny = ##2\n"); }
    @Test void patchRt_Remove() { assertPatchRoundtrip("x = ##1\ny = ##2\n", "x = ##1\n"); }
    @Test void patchRt_ChangeInt() { assertPatchRoundtrip("x = ##1\n", "x = ##99\n"); }
    @Test void patchRt_ChangeStr() { assertPatchRoundtrip("x = \"old\"\n", "x = \"new\"\n"); }
    @Test void patchRt_ChangeBool() { assertPatchRoundtrip("x = true\n", "x = false\n"); }
    @Test void patchRt_ChangeType() { assertPatchRoundtrip("x = \"str\"\n", "x = ##42\n"); }
    @Test void patchRt_ToNull() { assertPatchRoundtrip("x = ##42\n", "x = ~\n"); }
    @Test void patchRt_FromNull() { assertPatchRoundtrip("x = ~\n", "x = ##42\n"); }

    @Test void patchRt_MultiField() {
        assertPatchRoundtrip("a = ##1\nb = ##2\nc = ##3\n", "a = ##10\nb = ##20\nd = ##4\n");
    }

    @Test void patchRt_SectionChange() {
        assertPatchRoundtrip("{S}\nf = ##1\n", "{S}\nf = ##99\n");
    }

    @Test void patchRt_SectionAddField() {
        assertPatchRoundtrip("{S}\na = ##1\n", "{S}\na = ##1\nb = ##2\n");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Canonicalize Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void canonical_Deterministic() {
        var d = Odin.parse("b = ##2\na = ##1\n");
        var c1 = Odin.canonicalize(d);
        var c2 = Odin.canonicalize(d);
        assertArrayEquals(c1, c2);
    }

    @Test void canonical_SortedFields() {
        var d = Odin.parse("z = \"z\"\na = \"a\"\nm = \"m\"\n");
        var c = new String(Odin.canonicalize(d), java.nio.charset.StandardCharsets.UTF_8);
        var aPos = c.indexOf("a =");
        var mPos = c.indexOf("m =");
        var zPos = c.indexOf("z =");
        assertTrue(aPos < mPos && mPos < zPos);
    }

    @Test void canonical_DifferentDocsDifferent() {
        var d1 = Odin.parse("x = ##1\n");
        var d2 = Odin.parse("x = ##2\n");
        assertFalse(Arrays.equals(Odin.canonicalize(d1), Odin.canonicalize(d2)));
    }

    @Test void canonical_EmptyDoc() {
        var d = Odin.parse("");
        assertNotNull(Odin.canonicalize(d));
    }

    @Test void canonical_WithSection() {
        var d = Odin.parse("{S}\nf = \"v\"\n");
        var c = new String(Odin.canonicalize(d), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(c.contains("f"));
    }

    @Test void canonical_KeyOrderingStable() {
        var d1 = Odin.parse("z = ##1\na = ##2\nm = ##3\n");
        var d2 = Odin.parse("a = ##2\nm = ##3\nz = ##1\n");
        assertArrayEquals(Odin.canonicalize(d1), Odin.canonicalize(d2));
    }

    @Test void canonical_SameValueSameOutput() {
        var d1 = Odin.parse("x = ##42\n");
        var d2 = Odin.parse("x = ##42\n");
        assertArrayEquals(Odin.canonicalize(d1), Odin.canonicalize(d2));
    }

    @Test void canonical_DifferentValuesDifferent() {
        var d1 = Odin.parse("x = \"a\"\n");
        var d2 = Odin.parse("x = \"b\"\n");
        assertFalse(Arrays.equals(Odin.canonicalize(d1), Odin.canonicalize(d2)));
    }

    @Test void canonical_DifferentKeysDifferent() {
        var d1 = Odin.parse("a = ##1\n");
        var d2 = Odin.parse("b = ##1\n");
        assertFalse(Arrays.equals(Odin.canonicalize(d1), Odin.canonicalize(d2)));
    }

    @Test void canonical_SectionOrdering() {
        var d1 = Odin.parse("{B}\nf = ##1\n{A}\nf = ##2\n");
        var d2 = Odin.parse("{A}\nf = ##2\n{B}\nf = ##1\n");
        assertArrayEquals(Odin.canonicalize(d1), Odin.canonicalize(d2));
    }

    @Test void canonical_WithArrays() {
        var d = Odin.parse("items[0] = \"x\"\nitems[1] = \"y\"\n");
        assertNotNull(Odin.canonicalize(d));
    }

    @Test void canonical_ManyFields() {
        var sb = new StringBuilder();
        for (int i = 25; i >= 0; i--) {
            char ch = (char) ('a' + i);
            sb.append(ch).append(" = ##").append(i).append('\n');
        }
        var d = Odin.parse(sb.toString());
        var c = new String(Odin.canonicalize(d), java.nio.charset.StandardCharsets.UTF_8);
        var aPos = c.indexOf("a =");
        var zPos = c.indexOf("z =");
        assertTrue(aPos < zPos);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Document Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void multiDoc_Single() {
        var docs = Odin.parseDocuments("x = ##1\n");
        assertEquals(1, docs.size());
    }

    @Test void multiDoc_Two() {
        var docs = Odin.parseDocuments("x = ##1\n---\ny = ##2\n");
        assertEquals(2, docs.size());
        assertEquals(1L, docs.get(0).getInteger("x"));
        assertEquals(2L, docs.get(1).getInteger("y"));
    }

    @Test void multiDoc_Three() {
        var docs = Odin.parseDocuments("a = ##1\n---\nb = ##2\n---\nc = ##3\n");
        assertEquals(3, docs.size());
    }

    @Test void multiDoc_WithSections() {
        var docs = Odin.parseDocuments("{A}\nx = ##1\n---\n{B}\ny = ##2\n");
        assertEquals(2, docs.size());
    }

    @Test void multiDoc_EmptyYieldsOne() {
        var docs = Odin.parseDocuments("");
        assertEquals(1, docs.size());
    }

    @Test void multiDoc_Five() {
        var docs = Odin.parseDocuments("a = ##1\n---\nb = ##2\n---\nc = ##3\n---\nd = ##4\n---\ne = ##5\n");
        assertEquals(5, docs.size());
    }

    @Test void multiDoc_WithDifferentSections() {
        var docs = Odin.parseDocuments("{A}\nf = ##1\n---\n{B}\nf = ##2\n---\n{C}\nf = ##3\n");
        assertEquals(3, docs.size());
        assertEquals(1L, docs.get(0).getInteger("A.f"));
        assertEquals(2L, docs.get(1).getInteger("B.f"));
        assertEquals(3L, docs.get(2).getInteger("C.f"));
    }

    @Test void multiDoc_ChainFieldsIndependent() {
        var docs = Odin.parseDocuments("x = ##1\ny = ##2\n---\nz = ##3\n");
        assertEquals(1L, docs.get(0).getInteger("x"));
        assertEquals(2L, docs.get(0).getInteger("y"));
        assertNull(docs.get(1).get("x"));
        assertEquals(3L, docs.get(1).getInteger("z"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Builder Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void builder_String() {
        var d = Odin.builder().set("x", OdinValue.ofString("hi")).build();
        assertEquals("hi", d.getString("x"));
    }

    @Test void builder_Integer() {
        var d = Odin.builder().set("x", OdinValue.ofInteger(42)).build();
        assertEquals(42L, d.getInteger("x"));
    }

    @Test void builder_Number() {
        var d = Odin.builder().set("x", OdinValue.ofNumber(3.14)).build();
        assertTrue(Math.abs(d.getNumber("x") - 3.14) < 0.001);
    }

    @Test void builder_Boolean() {
        var d = Odin.builder().set("x", OdinValue.ofBoolean(true)).build();
        assertEquals(true, d.getBoolean("x"));
    }

    @Test void builder_Null() {
        var d = Odin.builder().setNull("x").build();
        assertTrue(d.get("x").isNull());
    }

    @Test void builder_Empty() {
        var d = Odin.builder().build();
        assertEquals(0, d.getAssignments().size());
    }

    @Test void builder_SectionPath() {
        var d = Odin.builder().set("S.f", OdinValue.ofString("v")).build();
        assertEquals("v", d.getString("S.f"));
    }

    @Test void builder_Overwrite() {
        var d = Odin.builder()
                .set("x", OdinValue.ofString("a"))
                .set("x", OdinValue.ofString("b"))
                .build();
        assertEquals("b", d.getString("x"));
    }

    @Test void builder_Multiple() {
        var d = Odin.builder()
                .setString("a", "1")
                .setInteger("b", 2)
                .setBoolean("c", true)
                .build();
        assertEquals("1", d.getString("a"));
        assertEquals(2L, d.getInteger("b"));
        assertEquals(true, d.getBoolean("c"));
    }

    @Test void builder_Currency() {
        var d = Odin.builder().setCurrency("price", 99.99).build();
        assertTrue(d.get("price").isCurrency());
    }

    @Test void builder_Date() {
        var d = Odin.builder().set("born", OdinValue.ofDate(2024, (byte) 1, (byte) 15)).build();
        assertTrue(d.get("born").isDate());
    }

    @Test void builder_Reference() {
        var d = Odin.builder().set("ref", OdinValue.ofReference("other.path")).build();
        assertTrue(d.get("ref").isReference());
    }

    @Test void builder_Binary() {
        var d = Odin.builder().set("data", OdinValue.ofBinary(new byte[]{72, 101, 108, 108, 111})).build();
        assertTrue(d.get("data").isBinary());
    }

    @Test void builder_ManyFields() {
        var b = Odin.builder();
        for (int i = 0; i < 50; i++)
            b = b.setInteger("field_" + i, i);
        var d = b.build();
        assertEquals(0L, d.getInteger("field_0"));
        assertEquals(49L, d.getInteger("field_49"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Builder -> Stringify -> Parse Roundtrip
    // ═══════════════════════════════════════════════════════════════════════

    @Test void builderRoundtrip_String() {
        var d = Odin.builder().setString("name", "Alice").build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals("Alice", d2.getString("name"));
    }

    @Test void builderRoundtrip_Integer() {
        var d = Odin.builder().setInteger("n", -5).build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals(-5L, d2.getInteger("n"));
    }

    @Test void builderRoundtrip_AllTypes() {
        var d = Odin.builder()
                .setString("s", "test")
                .setInteger("i", 42)
                .setNumber("n", 3.14)
                .setBoolean("b", false)
                .setNull("null")
                .build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals("test", d2.getString("s"));
        assertEquals(42L, d2.getInteger("i"));
        assertEquals(false, d2.getBoolean("b"));
        assertTrue(d2.get("null").isNull());
    }

    @Test void builderRoundtrip_SectionPath() {
        var d = Odin.builder()
                .setString("S.name", "test")
                .setInteger("S.value", 42)
                .build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals("test", d2.getString("S.name"));
        assertEquals(42L, d2.getInteger("S.value"));
    }

    @Test void builderRoundtrip_MultipleSections() {
        var d = Odin.builder()
                .setInteger("A.x", 1)
                .setInteger("B.y", 2)
                .build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals(1L, d2.getInteger("A.x"));
        assertEquals(2L, d2.getInteger("B.y"));
    }

    @Test void builderRoundtrip_ManyTypes() {
        var d = Odin.builder()
                .setString("str", "hello")
                .setInteger("int", 42)
                .setNumber("num", 3.14)
                .setBoolean("bool_t", true)
                .setBoolean("bool_f", false)
                .setNull("null")
                .setCurrency("curr", 9.99)
                .set("ref", OdinValue.ofReference("other"))
                .build();
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals("hello", d2.getString("str"));
        assertEquals(42L, d2.getInteger("int"));
        assertEquals(true, d2.getBoolean("bool_t"));
        assertEquals(false, d2.getBoolean("bool_f"));
        assertTrue(d2.get("null").isNull());
        assertTrue(d2.get("curr").isCurrency());
        assertTrue(d2.get("ref").isReference());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Stringify Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void stringify_IntegerPrefix() {
        var d = Odin.builder().setInteger("x", 42).build();
        var t = Odin.serialize(d);
        assertTrue(t.contains("##42"));
    }

    @Test void stringify_Boolean() {
        var d = Odin.builder().setBoolean("x", true).build();
        var t = Odin.serialize(d);
        assertTrue(t.contains("true"));
    }

    @Test void stringify_Null() {
        var d = Odin.builder().setNull("x").build();
        var t = Odin.serialize(d);
        assertTrue(t.contains("~"));
    }

    @Test void stringify_QuotedString() {
        var d = Odin.builder().setString("x", "hello").build();
        var t = Odin.serialize(d);
        assertTrue(t.contains("\"hello\""));
    }

    @Test void stringify_EmptyDoc() {
        var d = Odin.builder().build();
        var t = Odin.serialize(d);
        assertTrue(t == null || t.isBlank());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Schema Parse & Validate
    // ═══════════════════════════════════════════════════════════════════════

    @Test void schema_ParseWithTypes() {
        var s = Odin.parseSchema("{@Person}\nname = \"\"\nage = ##\n");
        assertTrue(s.types().containsKey("Person"));
    }

    @Test void schema_ParseMultipleTypes() {
        var s = Odin.parseSchema("{@A}\nx = \"\"\n{@B}\ny = ##\n");
        assertTrue(s.types().containsKey("A"));
        assertTrue(s.types().containsKey("B"));
    }

    @Test void schema_ParseEmpty() {
        var s = Odin.parseSchema("");
        assertTrue(s.types().isEmpty());
    }

    @Test void schema_ParseComments() {
        var s = Odin.parseSchema("; comment\n");
        assertTrue(s.types().isEmpty());
    }

    @Test void schema_BooleanField() {
        var s = Odin.parseSchema("{@Config}\nenabled = ?\n");
        assertTrue(s.types().containsKey("Config"));
    }

    @Test void schema_ValidateCorrectType() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\n");
        var doc = Odin.parse("Person.name = \"Alice\"\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateWrongType() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\n");
        var doc = Odin.parse("Person.name = ##42\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateEmptyDocPasses() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\n");
        var doc = Odin.parse("");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateIntegerFieldCorrect() {
        var schema = Odin.parseSchema("{Person}\nage = ##\n");
        var doc = Odin.parse("Person.age = ##25\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateIntegerFieldWrongType() {
        var schema = Odin.parseSchema("{Person}\nage = ##\n");
        var doc = Odin.parse("Person.age = \"twenty-five\"\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateBooleanFieldCorrect() {
        var schema = Odin.parseSchema("{Config}\nenabled = ?\n");
        var doc = Odin.parse("Config.enabled = true\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateBooleanFieldWrong() {
        var schema = Odin.parseSchema("{Config}\nenabled = ?\n");
        var doc = Odin.parse("Config.enabled = \"yes\"\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateNumberFieldCorrect() {
        var schema = Odin.parseSchema("{Measure}\nweight = #\n");
        var doc = Odin.parse("Measure.weight = #72.5\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateNumberFieldWrong() {
        var schema = Odin.parseSchema("{Measure}\nweight = #\n");
        var doc = Odin.parse("Measure.weight = \"heavy\"\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateMultipleFieldsAllCorrect() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\nage = ##\nactive = ?\n");
        var doc = Odin.parse("Person.name = \"Alice\"\nPerson.age = ##30\nPerson.active = true\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateMultipleFieldsOneWrong() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\nage = ##\n");
        var doc = Odin.parse("Person.name = \"Alice\"\nPerson.age = \"thirty\"\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateExtraFieldsPass() {
        var schema = Odin.parseSchema("{Person}\nname = \"\"\n");
        var doc = Odin.parse("Person.name = \"Alice\"\nPerson.extra = ##42\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateCurrencyCorrect() {
        var schema = Odin.parseSchema("{Order}\ntotal = #$\n");
        var doc = Odin.parse("Order.total = #$99.99\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateCurrencyWrongType() {
        var schema = Odin.parseSchema("{Order}\ntotal = #$\n");
        var doc = Odin.parse("Order.total = ##99\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_EmptyValidatesAnything() {
        var schema = Odin.parseSchema("");
        var doc = Odin.parse("x = ##42\ny = \"hello\"\n");
        var result = Odin.validate(doc, schema);
        assertTrue(result.valid());
    }

    @Test void schema_ValidateStringWhereIntExpected() {
        var schema = Odin.parseSchema("{Data}\ncount = ##\n");
        var doc = Odin.parse("Data.count = \"not a number\"\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateIntWhereStringExpected() {
        var schema = Odin.parseSchema("{Data}\nname = \"\"\n");
        var doc = Odin.parse("Data.name = ##42\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    @Test void schema_ValidateBoolWhereNumberExpected() {
        var schema = Odin.parseSchema("{Data}\nval = #\n");
        var doc = Odin.parse("Data.val = true\n");
        var result = Odin.validate(doc, schema);
        assertFalse(result.valid());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Comment Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void comment_LineIgnored() { var d = Odin.parse("; this is a comment\nx = ##1\n"); assertEquals(1L, d.getInteger("x")); }
    @Test void comment_Multiple() { var d = Odin.parse("; c1\n; c2\n; c3\nx = ##1\n"); assertEquals(1L, d.getInteger("x")); }

    @Test void comment_BetweenFields() {
        var d = Odin.parse("a = ##1\n; comment\nb = ##2\n");
        assertEquals(1L, d.getInteger("a"));
        assertEquals(2L, d.getInteger("b"));
    }

    @Test void comment_Inline() {
        var d = Odin.parse("x = ##42 ; inline\n");
        assertEquals(42L, d.getInteger("x"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // String Escape Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void escape_Newline() { var d = Odin.parse("x = \"a\\nb\"\n"); assertEquals("a\nb", d.getString("x")); }
    @Test void escape_Tab() { var d = Odin.parse("x = \"a\\tb\"\n"); assertEquals("a\tb", d.getString("x")); }
    @Test void escape_Backslash() { var d = Odin.parse("x = \"a\\\\b\"\n"); assertEquals("a\\b", d.getString("x")); }
    @Test void escape_Quote() { var d = Odin.parse("x = \"a\\\"b\"\n"); assertEquals("a\"b", d.getString("x")); }
    @Test void escape_CarriageReturn() { var d = Odin.parse("x = \"a\\rb\"\n"); assertEquals("a\rb", d.getString("x")); }

    @Test void escape_Multiple() {
        var d = Odin.parse("x = \"a\\n\\tb\\\\c\"\n");
        assertEquals("a\n\tb\\c", d.getString("x"));
    }

    @Test void escape_UnicodeInString() {
        var d = Odin.parse("x = \"hello \uD83C\uDF0D\"\n");
        assertEquals("hello \uD83C\uDF0D", d.getString("x"));
    }

    @Test void escape_CjkInString() {
        var d = Odin.parse("x = \"\u65E5\u672C\u8A9E\"\n");
        assertEquals("\u65E5\u672C\u8A9E", d.getString("x"));
    }

    @Test void escape_EmptyString() { var d = Odin.parse("x = \"\"\n"); assertEquals("", d.getString("x")); }
    @Test void escape_StringWithSpaces() { var d = Odin.parse("x = \"  spaces  \"\n"); assertEquals("  spaces  ", d.getString("x")); }
    @Test void escape_StringWithSemicolon() { var d = Odin.parse("x = \"has ; semicolon\"\n"); assertEquals("has ; semicolon", d.getString("x")); }
    @Test void escape_StringWithEquals() { var d = Odin.parse("x = \"a = b\"\n"); assertEquals("a = b", d.getString("x")); }
    @Test void escape_StringWithBraces() { var d = Odin.parse("x = \"{not a section}\"\n"); assertEquals("{not a section}", d.getString("x")); }
    @Test void escape_StringWithHash() { var d = Odin.parse("x = \"#not a number\"\n"); assertEquals("#not a number", d.getString("x")); }

    // ═══════════════════════════════════════════════════════════════════════
    // Array Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void array_SingleElement() { var d = Odin.parse("items[0] = \"only\"\n"); assertEquals("only", d.getString("items[0]")); }

    @Test void array_ThreeElements() {
        var d = Odin.parse("a[0] = ##1\na[1] = ##2\na[2] = ##3\n");
        assertEquals(1L, d.getInteger("a[0]"));
        assertEquals(3L, d.getInteger("a[2]"));
    }

    @Test void array_StringArray() {
        var d = Odin.parse("tags[0] = \"red\"\ntags[1] = \"blue\"\n");
        assertEquals("red", d.getString("tags[0]"));
        assertEquals("blue", d.getString("tags[1]"));
    }

    @Test void array_MixedTypes() {
        var d = Odin.parse("mix[0] = \"str\"\nmix[1] = ##42\nmix[2] = true\n");
        assertEquals("str", d.getString("mix[0]"));
        assertEquals(42L, d.getInteger("mix[1]"));
        assertEquals(true, d.getBoolean("mix[2]"));
    }

    @Test void array_InSection() {
        var d = Odin.parse("{Data}\nitems[0] = ##10\nitems[1] = ##20\n");
        assertEquals(10L, d.getInteger("Data.items[0]"));
        assertEquals(20L, d.getInteger("Data.items[1]"));
    }

    @Test void array_MultipleArrays() {
        var d = Odin.parse("a[0] = ##1\na[1] = ##2\nb[0] = \"x\"\nb[1] = \"y\"\n");
        assertEquals(1L, d.getInteger("a[0]"));
        assertEquals("x", d.getString("b[0]"));
    }

    @Test void array_LargeArray() {
        var sb = new StringBuilder();
        for (int i = 0; i < 20; i++)
            sb.append("items[").append(i).append("] = ##").append(i).append('\n');
        var d = Odin.parse(sb.toString());
        assertEquals(0L, d.getInteger("items[0]"));
        assertEquals(19L, d.getInteger("items[19]"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Extended Roundtrip Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Test void rtExt_StringWithNewline() { var d = roundtrip("x = \"line1\\nline2\"\n"); assertTrue(d.getString("x").contains("\n")); }
    @Test void rtExt_StringWithTab() { var d = roundtrip("x = \"col1\\tcol2\"\n"); assertTrue(d.getString("x").contains("\t")); }
    @Test void rtExt_StringWithBackslash() { var d = roundtrip("x = \"path\\\\to\\\\file\"\n"); assertTrue(d.getString("x").contains("\\")); }
    @Test void rtExt_StringWithQuotes() { var d = roundtrip("x = \"say \\\"hello\\\"\"\n"); assertTrue(d.getString("x").contains("\"")); }

    @Test void rtExt_NegativeNumber() {
        var d = roundtrip("x = #-99.5\n");
        assertTrue(Math.abs(d.getNumber("x") + 99.5) < 0.01);
    }

    @Test void rtExt_VeryLargeInteger() { var d = roundtrip("x = ##2147483647\n"); assertEquals(2147483647L, d.getInteger("x")); }
    @Test void rtExt_NegativeLargeInteger() { var d = roundtrip("x = ##-2147483648\n"); assertEquals(-2147483648L, d.getInteger("x")); }
    @Test void rtExt_CurrencyCents() { var d = roundtrip("x = #$0.01\n"); assertTrue(d.get("x").isCurrency()); }
    @Test void rtExt_CurrencyLarge() { var d = roundtrip("x = #$999999.99\n"); assertTrue(d.get("x").isCurrency()); }
    @Test void rtExt_PercentZero() { var d = roundtrip("x = #%0\n"); assertTrue(d.get("x").isPercent()); }
    @Test void rtExt_PercentHundred() { var d = roundtrip("x = #%100\n"); assertTrue(d.get("x").isPercent()); }
    @Test void rtExt_DateEndOfYear() { var d = roundtrip("x = 2024-12-31\n"); assertTrue(d.get("x").isDate()); }
    @Test void rtExt_DateStartOfYear() { var d = roundtrip("x = 2024-01-01\n"); assertTrue(d.get("x").isDate()); }
    @Test void rtExt_TimestampWithMillis() { var d = roundtrip("x = 2024-06-15T14:30:00.123Z\n"); assertTrue(d.get("x").isTimestamp()); }
    @Test void rtExt_DurationComplex() { var d = roundtrip("x = P1Y2M3DT4H5M6S\n"); assertNotNull(d.get("x")); }
    @Test void rtExt_ReferenceSimple() { var d = roundtrip("x = @target\n"); assertTrue(d.get("x").isReference()); }
    @Test void rtExt_ReferenceNested() { var d = roundtrip("x = @a.b.c.d\n"); assertTrue(d.get("x").isReference()); }

    @Test void rtExt_SectionWithAllTypes() {
        var d = roundtrip("{Data}\ns = \"text\"\ni = ##10\nn = #2.5\nb = true\nnull = ~\nc = #$50.00\n");
        assertEquals("text", d.getString("Data.s"));
        assertEquals(10L, d.getInteger("Data.i"));
        assertEquals(true, d.getBoolean("Data.b"));
        assertTrue(d.get("Data.null").isNull());
    }

    @Test void rtExt_MultipleSectionsWithData() {
        var d = roundtrip("{A}\na1 = ##1\na2 = ##2\n{B}\nb1 = \"x\"\nb2 = \"y\"\n{C}\nc1 = true\n");
        assertEquals(1L, d.getInteger("A.a1"));
        assertEquals("x", d.getString("B.b1"));
        assertEquals(true, d.getBoolean("C.c1"));
    }

    @Test void rtExt_ArrayOfIntegers() {
        var d = roundtrip("nums[0] = ##1\nnums[1] = ##2\nnums[2] = ##3\n");
        assertEquals(1L, d.getInteger("nums[0]"));
        assertEquals(2L, d.getInteger("nums[1]"));
        assertEquals(3L, d.getInteger("nums[2]"));
    }

    @Test void rtExt_ArrayOfStrings() {
        var d = roundtrip("tags[0] = \"a\"\ntags[1] = \"b\"\ntags[2] = \"c\"\n");
        assertEquals("a", d.getString("tags[0]"));
        assertEquals("c", d.getString("tags[2]"));
    }

    @Test void rtExt_MixedRootAndSections() {
        var d = roundtrip("root_field = \"top\"\n{S}\nsection_field = ##42\n");
        assertEquals("top", d.getString("root_field"));
        assertEquals(42L, d.getInteger("S.section_field"));
    }

    @Test void rtExt_ModifierRequiredNumber() { var d = roundtrip("x = !#3.14\n"); assertTrue(d.get("x").isRequired()); }
    @Test void rtExt_ModifierConfidentialCurrency() { var d = roundtrip("x = *#$100.00\n"); assertTrue(d.get("x").isConfidential()); }
    @Test void rtExt_ModifierDeprecatedBoolean() { var d = roundtrip("x = -true\n"); assertTrue(d.get("x").isDeprecated()); }

    // ═══════════════════════════════════════════════════════════════════════
    // Consistency Tests (Parse -> Stringify -> Parse -> Stringify stable)
    // ═══════════════════════════════════════════════════════════════════════

    @Test void stable_String() { assertConsistent("x = \"hello\"\n"); }
    @Test void stable_Integer() { assertConsistent("x = ##42\n"); }
    @Test void stable_NegInteger() { assertConsistent("x = ##-5\n"); }
    @Test void stable_Number() { assertConsistent("x = #3.14\n"); }
    @Test void stable_BooleanTrue() { assertConsistent("x = true\n"); }
    @Test void stable_BooleanFalse() { assertConsistent("x = false\n"); }
    @Test void stable_Null() { assertConsistent("x = ~\n"); }
    @Test void stable_Currency() { assertConsistent("x = #$99.99\n"); }
    @Test void stable_Percent() { assertConsistent("x = #%50\n"); }
    @Test void stable_Date() { assertConsistent("x = 2024-01-15\n"); }
    @Test void stable_Timestamp() { assertConsistent("x = 2024-01-15T10:30:00Z\n"); }
    @Test void stable_Reference() { assertConsistent("x = @other\n"); }
    @Test void stable_Binary() { assertConsistent("x = ^SGVsbG8=\n"); }
    @Test void stable_Section() { assertConsistent("{S}\nf = ##1\n"); }
    @Test void stable_NestedSection() { assertConsistent("{A}\n{A.B}\nf = ##1\n"); }
    @Test void stable_Array() { assertConsistent("items[0] = \"a\"\nitems[1] = \"b\"\n"); }
    @Test void stable_Required() { assertConsistent("x = !\"val\"\n"); }
    @Test void stable_Confidential() { assertConsistent("x = *\"secret\"\n"); }
    @Test void stable_Deprecated() { assertConsistent("x = -\"old\"\n"); }

    @Test void stable_Complex() {
        assertConsistent("{$}\nodin = \"1.0.0\"\n\nname = \"test\"\nage = ##25\nactive = true\nprice = #$49.99\n{Address}\nstreet = \"123 Main\"\ncity = \"Portland\"\n");
    }

    @Test void stable_MultiSection() { assertConsistent("{A}\na = ##1\n{B}\nb = ##2\n{C}\nc = ##3\n"); }
    @Test void stable_ArrayInSection() { assertConsistent("{S}\nitems[0] = \"x\"\nitems[1] = \"y\"\nitems[2] = \"z\"\n"); }

    // ═══════════════════════════════════════════════════════════════════════
    // Transform Integration
    // ═══════════════════════════════════════════════════════════════════════

    private static String header() {
        return "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"json->json\"\ntarget.format = \"json\"\n\n";
    }

    @Test void transform_SimpleCopy() {
        var tText = header() + "{Output}\nName = \"@.name\"\n";
        var src = DynValue.ofObject(List.of(Map.entry("name", DynValue.ofString("Alice"))));
        var r = Odin.executeTransform(tText, src);
        assertTrue(r.isSuccess());
    }

    @Test void transform_LiteralValue() {
        var tText = header() + "{Output}\nStatus = \"active\"\n";
        var src = DynValue.ofObject(List.of());
        var r = Odin.executeTransform(tText, src);
        assertTrue(r.isSuccess());
    }

    @Test void transform_NestedSource() {
        var tText = header() + "{Output}\nCity = \"@.address.city\"\n";
        var src = DynValue.ofObject(List.of(
                Map.entry("address", DynValue.ofObject(List.of(
                        Map.entry("city", DynValue.ofString("Portland"))
                )))
        ));
        var r = Odin.executeTransform(tText, src);
        assertTrue(r.isSuccess());
    }

    @Test void transform_MultiField() {
        var tText = header() + "{Output}\nA = \"@.a\"\nB = \"@.b\"\n";
        var src = DynValue.ofObject(List.of(
                Map.entry("a", DynValue.ofString("x")),
                Map.entry("b", DynValue.ofInteger(42))
        ));
        var r = Odin.executeTransform(tText, src);
        assertTrue(r.isSuccess());
    }

    @Test void transform_ParseEmpty() {
        var t = Odin.parseTransform(header());
        assertNotNull(t);
    }

    @Test void transform_ParseSingleMapping() {
        var tText = header() + "{Output}\nName = \"@.name\"\n";
        var t = Odin.parseTransform(tText);
        assertTrue(t.getSegments().size() > 0);
    }

    @Test void transform_ParseMultipleMappings() {
        var tText = header() + "{Output}\nA = \"@.a\"\nB = \"@.b\"\nC = \"@.c\"\n";
        var t = Odin.parseTransform(tText);
        assertTrue(t.getSegments().get(0).getMappings().size() >= 3);
    }

    @Test void transform_ParseDirection() {
        var t = Odin.parseTransform(header());
        assertEquals("json->json", t.getMetadata().getDirection());
    }

    @Test void transform_ParseOdinToJsonDirection() {
        var tText = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"odin->json\"\ntarget.format = \"json\"\n\n";
        var t = Odin.parseTransform(tText);
        assertEquals("odin->json", t.getMetadata().getDirection());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Stringify Parse Roundtrip Preserves Values
    // ═══════════════════════════════════════════════════════════════════════

    @Test void stringifyParseRoundtrip_PreservesValues() {
        var input = "name = \"Alice\"\nage = ##30\nactive = true\n";
        var d = Odin.parse(input);
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals("Alice", d2.getString("name"));
        assertEquals(30L, d2.getInteger("age"));
        assertEquals(true, d2.getBoolean("active"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Type Value Roundtrip via Builder
    // ═══════════════════════════════════════════════════════════════════════

    @Test void typeRt_StringEmpty() { var d = builderRoundtrip("x", OdinValue.ofString("")); assertEquals("", d.getString("x")); }
    @Test void typeRt_StringSpaces() { var d = builderRoundtrip("x", OdinValue.ofString("  ")); assertEquals("  ", d.getString("x")); }

    @Test void typeRt_StringLong() {
        var s = "a".repeat(500);
        var d = builderRoundtrip("x", OdinValue.ofString(s));
        assertEquals(500, d.getString("x").length());
    }

    @Test void typeRt_IntegerZero() { var d = builderRoundtrip("x", OdinValue.ofInteger(0)); assertEquals(0L, d.getInteger("x")); }
    @Test void typeRt_IntegerOne() { var d = builderRoundtrip("x", OdinValue.ofInteger(1)); assertEquals(1L, d.getInteger("x")); }
    @Test void typeRt_IntegerNegOne() { var d = builderRoundtrip("x", OdinValue.ofInteger(-1)); assertEquals(-1L, d.getInteger("x")); }
    @Test void typeRt_IntegerLarge() { var d = builderRoundtrip("x", OdinValue.ofInteger(999999)); assertEquals(999999L, d.getInteger("x")); }
    @Test void typeRt_IntegerNegLarge() { var d = builderRoundtrip("x", OdinValue.ofInteger(-999999)); assertEquals(-999999L, d.getInteger("x")); }

    @Test void typeRt_NumberPi() {
        var d = builderRoundtrip("x", OdinValue.ofNumber(3.14159));
        assertTrue(Math.abs(d.getNumber("x") - 3.14159) < 0.001);
    }

    @Test void typeRt_NumberZero() {
        var d = builderRoundtrip("x", OdinValue.ofNumber(0.0));
        assertTrue(Math.abs(d.getNumber("x")) < 0.001);
    }

    @Test void typeRt_NumberNegative() {
        var d = builderRoundtrip("x", OdinValue.ofNumber(-42.5));
        assertTrue(Math.abs(d.getNumber("x") + 42.5) < 0.1);
    }

    @Test void typeRt_BooleanTrue() { var d = builderRoundtrip("x", OdinValue.ofBoolean(true)); assertEquals(true, d.getBoolean("x")); }
    @Test void typeRt_BooleanFalse() { var d = builderRoundtrip("x", OdinValue.ofBoolean(false)); assertEquals(false, d.getBoolean("x")); }
    @Test void typeRt_NullVal() { var d = builderRoundtrip("x", OdinValue.ofNull()); assertTrue(d.get("x").isNull()); }
    @Test void typeRt_CurrencySmall() { var d = builderRoundtrip("x", OdinValue.ofCurrency(0.01, (byte) 2)); assertTrue(d.get("x").isCurrency()); }
    @Test void typeRt_CurrencyLarge() { var d = builderRoundtrip("x", OdinValue.ofCurrency(99999.99, (byte) 2)); assertTrue(d.get("x").isCurrency()); }
    @Test void typeRt_PercentHalf() { var d = builderRoundtrip("x", OdinValue.ofPercent(0.5)); assertTrue(d.get("x").isPercent()); }
    @Test void typeRt_PercentFull() { var d = builderRoundtrip("x", OdinValue.ofPercent(1.0)); assertTrue(d.get("x").isPercent()); }
    @Test void typeRt_DateVal() { var d = builderRoundtrip("x", OdinValue.ofDate(2024, (byte) 6, (byte) 15)); assertTrue(d.get("x").isDate()); }
    @Test void typeRt_ReferenceVal() { var d = builderRoundtrip("x", OdinValue.ofReference("other")); assertTrue(d.get("x").isReference()); }
    @Test void typeRt_BinaryVal() { var d = builderRoundtrip("x", OdinValue.ofBinary(new byte[]{1, 2, 3})); assertTrue(d.get("x").isBinary()); }

    // ═══════════════════════════════════════════════════════════════════════
    // Large Document Handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test void largeDoc_ManyFields() {
        var sb = new StringBuilder();
        for (int i = 0; i < 100; i++)
            sb.append("field_").append(i).append(" = ##").append(i).append('\n');
        var d = Odin.parse(sb.toString());
        assertEquals(0L, d.getInteger("field_0"));
        assertEquals(99L, d.getInteger("field_99"));
    }

    @Test void largeDoc_ManySections() {
        var sb = new StringBuilder();
        for (int i = 0; i < 50; i++)
            sb.append("{Section").append(i).append("}\nvalue = ##").append(i).append('\n');
        var d = Odin.parse(sb.toString());
        assertEquals(0L, d.getInteger("Section0.value"));
        assertEquals(49L, d.getInteger("Section49.value"));
    }

    @Test void largeDoc_LargeArray() {
        var sb = new StringBuilder();
        for (int i = 0; i < 100; i++)
            sb.append("items[").append(i).append("] = ##").append(i).append('\n');
        var d = Odin.parse(sb.toString());
        assertEquals(0L, d.getInteger("items[0]"));
        assertEquals(99L, d.getInteger("items[99]"));
    }

    @Test void largeDoc_LongStringValue() {
        var longStr = "x".repeat(10000);
        var d = Odin.parse("data = \"" + longStr + "\"\n");
        assertEquals(10000, d.getString("data").length());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test void edge_ParseAndStringifyEmpty() {
        var d = Odin.parse("");
        var text = Odin.serialize(d);
        var d2 = Odin.parse(text);
        assertEquals(0, d2.getAssignments().size());
    }

    @Test void edge_ImmutableDocument() {
        var d = Odin.parse("x = ##1\n");
        var d2 = d.with("y", OdinValue.ofInteger(2));
        assertNull(d.get("y"));
        assertEquals(1L, d2.getInteger("x"));
        assertEquals(2L, d2.getInteger("y"));
    }

    @Test void edge_DocumentWithout() {
        var d = Odin.parse("x = ##1\ny = ##2\n");
        var d2 = d.without("y");
        assertNull(d2.get("y"));
        assertEquals(1L, d2.getInteger("x"));
        assertEquals(2L, d.getInteger("y"));
    }

    @Test void edge_DocumentHas() {
        var d = Odin.parse("x = ##1\n");
        assertTrue(d.has("x"));
        assertFalse(d.has("y"));
    }

    @Test void edge_DocumentPaths() {
        var d = Odin.parse("a = ##1\nb = ##2\nc = ##3\n");
        var paths = d.paths();
        assertEquals(3, paths.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Step Workflows
    // ═══════════════════════════════════════════════════════════════════════

    @Test void workflow_ParseValidateDiffPatch() {
        var d1 = Odin.parse("name = \"Alice\"\nage = ##25\n");
        var d2 = Odin.parse("name = \"Bob\"\nage = ##30\nactive = true\n");

        var schema = Odin.parseSchema("{Person}\nname = \"\"\nage = ##\n");
        assertTrue(Odin.validate(d1, schema).valid());
        assertTrue(Odin.validate(d2, schema).valid());

        var diff = Odin.diff(d1, d2);
        assertFalse(diff.isEmpty());

        var patched = Odin.patch(d1, diff);
        assertEquals("Bob", patched.getString("name"));
        assertEquals(30L, patched.getInteger("age"));

        var diff2 = Odin.diff(patched, d2);
        assertTrue(diff2.isEmpty());
    }

    @Test void workflow_BuildStringifyParseCanonicalizeCompare() {
        var d1 = Odin.builder()
                .setString("z_name", "Alice")
                .setInteger("a_age", 25)
                .setBoolean("m_active", true)
                .build();

        var text = Odin.serialize(d1);
        var d2 = Odin.parse(text);

        var c1 = Odin.canonicalize(d1);
        var c2 = Odin.canonicalize(d2);
        assertArrayEquals(c1, c2);
    }

    @Test void workflow_TransformThenValidate() {
        var tText = header() + "{Person}\nName = \"@.name\"\nAge = \"@.age\"\n";
        var src = DynValue.ofObject(List.of(
                Map.entry("name", DynValue.ofString("Alice")),
                Map.entry("age", DynValue.ofInteger(30))
        ));
        var result = Odin.executeTransform(tText, src);
        assertTrue(result.isSuccess());
    }

    @Test void workflow_ParseDocumentChain() {
        var docs = Odin.parseDocuments("x = ##1\n---\nx = ##2\n---\nx = ##3\n");
        assertEquals(3, docs.size());

        var diff01 = Odin.diff(docs.get(0), docs.get(1));
        assertTrue(diff01.changed().size() > 0);

        var diff12 = Odin.diff(docs.get(1), docs.get(2));
        assertTrue(diff12.changed().size() > 0);
    }

    @Test void workflow_ExportToJson() {
        var d = Odin.parse("{Person}\nname = \"Alice\"\nage = ##30\n");
        var json = Odin.toJson(d);
        assertTrue(json.contains("Alice"));
        assertTrue(json.contains("30"));
    }

    @Test void workflow_ExportToXml() {
        var d = Odin.parse("{Person}\nname = \"Alice\"\n");
        var xml = Odin.toXml(d);
        assertTrue(xml.contains("Alice"));
    }
}
