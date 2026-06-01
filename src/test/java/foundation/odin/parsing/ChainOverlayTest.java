package foundation.odin.parsing;

import foundation.odin.Odin;
import foundation.odin.types.OdinDocument;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainOverlayTest {

    // ── Replace / passthrough ──

    @Nested
    class Replace {
        @Test void repeatedPathReplacesAndKeepsUntouched() {
            var doc = Odin.collapseChain(
                    "{person}\nname = \"John\"\nage = ##30\ncity = \"Austin\"\n\n---\n\n{person}\nage = ##31\nstate = \"TX\"");
            assertEquals("John", doc.getString("person.name"));
            assertEquals(31L, doc.getInteger("person.age"));
            assertEquals("Austin", doc.getString("person.city"));
            assertEquals("TX", doc.getString("person.state"));
        }

        @Test void singleDocumentPassthrough() {
            var doc = Odin.collapseChain("{p}\na = \"x\"\nb = ##5");
            assertEquals("x", doc.getString("p.a"));
            assertEquals(5L, doc.getInteger("p.b"));
        }

        @Test void threeDocumentChainResolvesToLast() {
            var doc = Odin.collapseChain(
                    "{p}\nv = \"1\"\nstable = \"keep\"\n\n---\n\n{p}\nv = \"2\"\n\n---\n\n{p}\nv = \"3\"");
            assertEquals("3", doc.getString("p.v"));
            assertEquals("keep", doc.getString("p.stable"));
        }
    }

    // ── Null removal ──

    @Nested
    class NullRemoval {
        @Test void nullRemovesField() {
            var doc = Odin.collapseChain(
                    "{person}\nname = \"John\"\ntemporary = \"gone\"\n\n---\n\n{person}\ntemporary = ~");
            assertEquals("John", doc.getString("person.name"));
            assertNull(doc.get("person.temporary"));
        }

        @Test void nullRemovesSubtree() {
            var doc = Odin.collapseChain(
                    "{p}\na.b = \"x\"\na.c = \"y\"\nkeep = \"z\"\n\n---\n\n{p}\na = ~");
            assertEquals("z", doc.getString("p.keep"));
            assertNull(doc.get("p.a.b"));
            assertNull(doc.get("p.a.c"));
        }

        @Test void reassignAfterRemoval() {
            var doc = Odin.collapseChain(
                    "{p}\nx = \"old\"\n\n---\n\n{p}\nx = ~\n\n---\n\n{p}\nx = \"new\"");
            assertEquals("new", doc.getString("p.x"));
        }
    }

    // ── Array clear ──

    @Nested
    class ArrayClear {
        @Test void clearRemovesAllElements() {
            var doc = Odin.collapseChain(
                    "{p}\ntags[0] = \"x\"\ntags[1] = \"y\"\nkeep = \"z\"\n\n---\n\n{p}\ntags[] = ~");
            assertEquals("z", doc.getString("p.keep"));
            assertNull(doc.get("p.tags[0]"));
            assertNull(doc.get("p.tags[1]"));
        }

        @Test void clearThenRepopulate() {
            var doc = Odin.collapseChain(
                    "{p}\ntags[0] = \"a\"\ntags[1] = \"b\"\n\n---\n\n{p}\ntags[] = ~\n\n---\n\n{p}\ntags[0] = \"c\"");
            assertEquals("c", doc.getString("p.tags[0]"));
            assertNull(doc.get("p.tags[1]"));
        }
    }

    // ── Metadata isolation ──

    @Nested
    class MetadataIsolation {
        @Test void carriesOnlyFinalDocumentMetadata() {
            var doc = Odin.collapseChain(
                    "{$}\nid = \"first\"\nrole = \"base\"\n\n{p}\nn = \"A\"\n\n---\n\n{$}\nid = \"second\"\n\n{p}\nn = \"B\"");
            assertEquals("B", doc.getString("p.n"));
            assertEquals("second", doc.getString("$.id"));
            assertNull(doc.get("$.role"));
        }
    }

    // ── Parsed-list overload ──

    @Nested
    class ParsedListOverload {
        @Test void acceptsPreParsedDocuments() {
            java.util.List<OdinDocument> docs = Odin.parseDocuments(
                    "{p}\nv = \"1\"\n\n---\n\n{p}\nv = \"2\"");
            var doc = Odin.collapseChain(docs);
            assertEquals("2", doc.getString("p.v"));
        }
    }

    // ── Edge: empty / no-op chains ──

    @Nested
    class Edge {
        @Test void emptyChainYieldsEmptyDocument() {
            var doc = Odin.collapseChain("{p}\n");
            assertNull(doc.get("p.anything"));
        }

        @Test void nullOnAbsentPathIsNoOp() {
            var doc = Odin.collapseChain("{p}\na = \"x\"\n\n---\n\n{p}\nmissing = ~");
            assertEquals("x", doc.getString("p.a"));
            assertNull(doc.get("p.missing"));
        }
    }
}
