package foundation.odin.transform;

import foundation.odin.Odin;
import foundation.odin.types.DynValue;
import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinErrors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Wave-3 transform conformance fixes: bare/header-inline directives, validation,
// inline objects, :raw / :array, field :if comparison, counters, XML :cdata,
// computation sinks, and fixed-width line padding.
class TransformWave3Test {

    private static DynValue odinToDyn(String odinText) {
        OdinDocument doc = Odin.parse(odinText);
        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        for (var kv : doc.getAssignments()) {
            if (kv.getKey().startsWith("$")) continue;
            setNested(entries, kv.getKey().split("\\."), 0, TransformEngine.odinValueToDyn(kv.getValue()));
        }
        return DynValue.ofObject(entries);
    }

    private static void setNested(List<Map.Entry<String, DynValue>> entries, String[] segs, int idx, DynValue value) {
        String seg = segs[idx];
        boolean isLast = idx == segs.length - 1;
        int bp = seg.indexOf('[');
        if (bp >= 0 && seg.endsWith("]")) {
            String key = seg.substring(0, bp);
            int arrIdx = Integer.parseInt(seg.substring(bp + 1, seg.length() - 1));
            int pos = find(entries, key);
            if (pos < 0) { entries.add(Map.entry(key, DynValue.ofArray(new ArrayList<>()))); pos = entries.size() - 1; }
            var arr = entries.get(pos).getValue().asArray();
            while (arr.size() <= arrIdx) arr.add(isLast ? DynValue.ofNull() : DynValue.ofObject(new ArrayList<>()));
            if (isLast) { arr.set(arrIdx, value); }
            else {
                var inner = arr.get(arrIdx).asObject();
                if (inner == null) { inner = new ArrayList<>(); arr.set(arrIdx, DynValue.ofObject(inner)); }
                setNested(inner, segs, idx + 1, value);
            }
            return;
        }
        if (isLast) { entries.add(Map.entry(seg, value)); return; }
        int pos = find(entries, seg);
        if (pos >= 0) { setNested(entries.get(pos).getValue().asObject(), segs, idx + 1, value); }
        else {
            var obj = new ArrayList<Map.Entry<String, DynValue>>();
            entries.add(Map.entry(seg, DynValue.ofObject(obj)));
            setNested(obj, segs, idx + 1, value);
        }
    }

    private static int find(List<Map.Entry<String, DynValue>> entries, String key) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).getKey().equals(key)) return i;
        return -1;
    }

    private static String run(String input, String transformText) {
        var transform = TransformParser.parse(transformText);
        var result = TransformEngine.execute(transform, odinToDyn(input));
        return result.getFormatted();
    }

    private static final String HEADER_JSON =
            "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"odin->json\"\ntarget.format = \"json\"\n";

    // ── Fix 1 & 6: bare :loop / :counter directive lines ──

    @Test
    void bareLoopAndCounterDirectives() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{items[] : sku}\n\"A\"\n\"B\"\n\"C\"\n";
        var transform = HEADER_JSON +
                "\n{rows[]}\n:loop items\n:counter rownum\n" +
                "sku = \"@.sku\"\nn = \"@rownum\"\nm = \"@$accumulator.rownum\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"sku\": \"B\""), out);
        assertTrue(out.contains("\"n\": 1"), out);
        assertTrue(out.contains("\"m\": 1"), out);
    }

    // ── Fix 2: header-inline :loop ──

    @Test
    void headerInlineLoopDirective() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{items[] : sku}\n\"X\"\n\"Y\"\n";
        var transform = HEADER_JSON + "\n{rows[] :loop items}\nsku = \"@.sku\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"sku\": \"X\""), out);
        assertTrue(out.contains("\"sku\": \"Y\""), out);
    }

    // ── Fix 3: :validate / :enum / :range ──

    @Test
    void validationWarnEmitsValue() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{record}\nstatus = \"Z\"\n";
        var transform = HEADER_JSON + "target.onValidation = \"warn\"\n\n{Record}\nstatus = \"@record.status :enum A,P,C\"\n";
        var transformObj = TransformParser.parse(transform);
        var result = TransformEngine.execute(transformObj, odinToDyn(input));
        assertTrue(result.getFormatted().contains("\"status\": \"Z\""));
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void validationFailEmitsT013() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{record}\nstatus = \"Z\"\n";
        var transform = HEADER_JSON + "target.onValidation = \"fail\"\n\n{Record}\nstatus = \"@record.status :enum A,P,C\"\n";
        var result = TransformEngine.execute(TransformParser.parse(transform), odinToDyn(input));
        assertFalse(result.getErrors().isEmpty());
        assertEquals(OdinErrors.TransformErrorCodes.T013_VALIDATION_FAILED, result.getErrors().get(0).getCode());
    }

    @Test
    void validationSkipDropsField() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{record}\nyear = ##1850\n";
        var transform = HEADER_JSON + "target.onValidation = \"skip\"\n\n{Record}\nyear = \"@record.year :range 1900..2100\"\n";
        var result = TransformEngine.execute(TransformParser.parse(transform), odinToDyn(input));
        assertFalse(result.getFormatted().contains("year"));
        assertTrue(result.getErrors().isEmpty());
    }

    // ── Fix 4: :object / :raw / :array ──

    @Test
    void inlineObjectModifier() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{insured}\nname = \"John Doe\"\nphone = \"512-555-1234\"\n";
        var transform = HEADER_JSON + "\n{Quote}\ncontact = \":object {name = @insured.name, phone = @insured.phone}\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"name\": \"John Doe\""), out);
        assertTrue(out.contains("\"phone\": \"512-555-1234\""), out);
    }

    @Test
    void rawJsonModifier() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{document}\njsonMetadata = \"{\\\"version\\\":2,\\\"active\\\":true}\"\n";
        var transform = HEADER_JSON + "\n{Document}\nmetadata = \"@document.jsonMetadata :raw\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"version\": 2"), out);
        assertTrue(out.contains("\"active\": true"), out);
    }

    @Test
    void arrayWrapModifier() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{policy}\nprimaryCode = \"COLL\"\n";
        var transform = HEADER_JSON + "\n{Policy}\ncodes = \"@policy.primaryCode :array\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"codes\": [\n"), out);
        assertTrue(out.contains("\"COLL\""), out);
    }

    // ── Fix 5: field :if path = value comparison ──

    @Test
    void fieldIfCompareEmitsAndOmits() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{policy}\ntier = \"gold\"\ndiscount = ##15\nsurcharge = ##40\n";
        var transform = HEADER_JSON +
                "\n{Quote}\ndiscount = \"@policy.discount :if @policy.tier = gold\"\n" +
                "surcharge = \"@policy.surcharge :if @policy.tier = bronze\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("\"discount\": 15"), out);
        assertFalse(out.contains("surcharge"), out);
    }

    // ── Fix 7: XML :cdata ──

    @Test
    void xmlCdataWrapsText() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{policy}\ndescription = \"a < 500 & b > 0\"\n";
        var transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"odin->xml\"\n"
                + "target.format = \"xml\"\nemitTypeHints = ?false\n\n{Policy}\nDescription = \"@policy.description :cdata\"\n";
        var out = run(input, transform);
        assertTrue(out.contains("<![CDATA[a < 500 & b > 0]]>"), out);
    }

    // ── Fix 8: computation-only sink (looping) omitted ──

    @Test
    void loopingSinkOmittedButAccumulates() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{items[] : amount}\n##10\n##20\n##30\n";
        var transform = HEADER_JSON +
                "\n{$accumulator}\ntotal = ##0\n\n{_sumItems[]}\n:loop items\n_ = \"%accumulate total @.amount\"\n" +
                "\n{Summary}\ntotal = \"@$accumulator.total\"\n";
        var out = run(input, transform);
        assertFalse(out.contains("_sumItems"), out);
        assertTrue(out.contains("\"total\": 60"), out);
    }

    // ── Fix 9: fixed-width lineWidth padding ──

    @Test
    void fixedWidthPadsToLineWidth() {
        var input = "{$}\nodin = \"1.0.0\"\n{}\n{record}\ncode = \"AB\"\nname = \"WIDGET\"\n";
        var transform = "{$}\nodin = \"1.0.0\"\ntransform = \"1.0.0\"\ndirection = \"odin->fixed-width\"\n"
                + "target.format = \"fixed-width\"\n\n{$target}\nlineWidth = ##20\npadChar = \".\"\n\n"
                + "{record}\ncode = @record.code :pos 0 :len 5 :rightPad \" \"\n"
                + "name = @record.name :pos 5 :len 8 :rightPad \" \"\n";
        var out = run(input, transform).replace("\r\n", "\n").trim();
        assertEquals("AB   WIDGET  .......", out);
        assertEquals(20, out.length());
    }
}
