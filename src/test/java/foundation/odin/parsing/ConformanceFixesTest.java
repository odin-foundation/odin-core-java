package foundation.odin.parsing;

import foundation.odin.Odin;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinErrors.OdinParseException;
import foundation.odin.types.OdinOptions.ParseOptions;
import foundation.odin.types.OdinValue;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConformanceFixesTest {

    private static OdinDocument parse(String source) {
        return OdinParser.parse(source, ParseOptions.DEFAULT);
    }

    // ── Fix 1: top-level $.path = value (canonical round-trip) ──

    @Nested
    class TopLevelMetadataPath {
        @Test void routesToMetadata() {
            var doc = parse("$.odin = \"1.0.0\"\n$.id = \"a\"\nname = \"x\"");
            assertNotNull(doc.get("$.id"), "$.id should be captured as metadata");
            assertEquals("a", doc.getString("$.id"));
            assertEquals("1.0.0", doc.getString("$.odin"));
            assertEquals("x", doc.getString("name"));
        }

        @Test void canonicalRoundTripIsIdempotent() {
            var doc = parse("{$}\nodin = \"1.0.0\"\nid = \"a\"\n\n{person}\nname = \"x\"\nage = ##5");
            String canon1 = new String(Odin.canonicalize(doc));
            var reparsed = parse(canon1);
            String canon2 = new String(Odin.canonicalize(reparsed));
            assertEquals(canon1, canon2, "canonicalize(parse(canonicalize(doc))) must be stable");
        }

        @Test void nestedMetadataPath() {
            var doc = parse("$.custom.field = \"v\"");
            assertEquals("v", doc.getString("$.custom.field"));
        }
    }

    // ── Fix 2: integer (##) decimal rejection ──

    @Nested
    class IntegerDecimalRejection {
        @Test void rejectsFractionalInteger() {
            assertThrows(OdinParseException.class, () -> parse("x = ##4.2"));
        }

        @Test void rejectsNegativeFractionalInteger() {
            assertThrows(OdinParseException.class, () -> parse("x = ##-3.7"));
        }

        @Test void acceptsExponentInteger() {
            var doc = parse("x = ##1e3");
            assertEquals(1000L, doc.getInteger("x"));
        }

        @Test void acceptsPlainInteger() {
            var doc = parse("x = ##42");
            assertEquals(42L, doc.getInteger("x"));
        }
    }

    // ── Fix 3: @$.path meta reference ──

    @Nested
    class MetaReference {
        @Test void leadingDotMetaReference() {
            var doc = parse("x = @$.id");
            var ref = (OdinValue.OdinReference) doc.get("x");
            assertEquals("$.id", ref.getPath());
        }

        @Test void deepLeadingDotMetaReference() {
            var doc = parse("x = @$.i18n.en.name");
            var ref = (OdinValue.OdinReference) doc.get("x");
            assertEquals("$.i18n.en.name", ref.getPath());
        }

        @Test void constMetaReferenceStillWorks() {
            var doc = parse("x = @$const.NAME");
            var ref = (OdinValue.OdinReference) doc.get("x");
            assertEquals("$const.NAME", ref.getPath());
        }
    }

    // ── Fix 4: document chain API ──

    @Nested
    class DocumentChain {
        @Test void parseDocumentsReturnsFullChain() {
            List<OdinDocument> docs = Odin.parseDocuments("{$}\nid = \"a\"\n\n---\n\n{$}\nid = \"b\"");
            assertEquals(2, docs.size());
            assertEquals("a", docs.get(0).getString("$.id"));
            assertEquals("b", docs.get(1).getString("$.id"));
        }

        @Test void singleDocumentYieldsOneElement() {
            List<OdinDocument> docs = Odin.parseDocuments("{$}\nid = \"a\"\n\n{}\nname = \"x\"");
            assertEquals(1, docs.size());
            assertEquals("x", docs.get(0).getString("name"));
        }

        @Test void perDocumentMetadataIsIsolated() {
            List<OdinDocument> docs = Odin.parseDocuments(
                    "{$}\nid = \"doc1\"\ncustom.a = \"1\"\n\n---\n\n{$}\nid = \"doc2\"\ncustom.b = \"2\"");
            assertEquals(2, docs.size());
            assertEquals("1", docs.get(0).getString("$.custom.a"));
            assertNull(docs.get(0).get("$.custom.b"));
            assertEquals("2", docs.get(1).getString("$.custom.b"));
            assertNull(docs.get(1).get("$.custom.a"));
        }
    }
}
