package foundation.odin.diff;

import foundation.odin.types.*;

import java.util.*;

public final class Differ {

    private Differ() {}

    public static OdinDiff computeDiff(OdinDocument a, OdinDocument b) {
        var added = new ArrayList<OdinDiff.DiffEntry>();
        var removed = new ArrayList<OdinDiff.DiffEntry>();
        var changed = new ArrayList<OdinDiff.DiffChange>();
        var moved = new ArrayList<OdinDiff.DiffMove>();

        var aEntries = a.getAssignments();
        var bEntries = b.getAssignments();

        // Find removed and changed
        for (var entry : aEntries.entries()) {
            String path = entry.getKey();
            OdinValue aVal = entry.getValue();
            OdinValue bVal = bEntries.tryGet(path);

            if (bVal == null) {
                removed.add(new OdinDiff.DiffEntry(path, aVal));
            } else if (!valuesEqual(aVal, bVal)) {
                changed.add(new OdinDiff.DiffChange(path, aVal, bVal));
            }
        }

        // Find added
        for (var entry : bEntries.entries()) {
            String path = entry.getKey();
            if (!aEntries.containsKey(path)) {
                added.add(new OdinDiff.DiffEntry(path, entry.getValue()));
            }
        }

        // Detect moves: find removed values that match added values
        var removedIndicesToRemove = new ArrayList<Integer>();
        var addedIndicesToRemove = new ArrayList<Integer>();

        for (int ri = 0; ri < removed.size(); ri++) {
            OdinValue removedVal = removed.get(ri).value();
            for (int ai = 0; ai < added.size(); ai++) {
                if (addedIndicesToRemove.contains(ai)) continue;
                OdinValue addedVal = added.get(ai).value();
                if (valuesEqual(removedVal, addedVal)) {
                    moved.add(new OdinDiff.DiffMove(
                            removed.get(ri).path(),
                            added.get(ai).path(),
                            removedVal));
                    removedIndicesToRemove.add(ri);
                    addedIndicesToRemove.add(ai);
                    break;
                }
            }
        }

        // Remove matched items (descending order to preserve indices)
        removedIndicesToRemove.sort(Comparator.reverseOrder());
        addedIndicesToRemove.sort(Comparator.reverseOrder());
        for (int idx : removedIndicesToRemove) swapRemove(removed, idx);
        for (int idx : addedIndicesToRemove) swapRemove(added, idx);

        return new OdinDiff(added, removed, changed, moved);
    }

    static boolean valuesEqual(OdinValue a, OdinValue b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;

        // Compare modifiers — different modifiers mean different values
        var modsA = a.getModifiers() != null ? a.getModifiers() : OdinModifiers.EMPTY;
        var modsB = b.getModifiers() != null ? b.getModifiers() : OdinModifiers.EMPTY;
        if (!modsA.equals(modsB)) return false;

        if (a instanceof OdinValue.OdinNull) return true;
        if (a instanceof OdinValue.OdinBoolean ab && b instanceof OdinValue.OdinBoolean bb) return ab.getValue() == bb.getValue();
        if (a instanceof OdinValue.OdinString as && b instanceof OdinValue.OdinString bs) return Objects.equals(as.getValue(), bs.getValue());
        if (a instanceof OdinValue.OdinInteger ai && b instanceof OdinValue.OdinInteger bi) return ai.getValue() == bi.getValue();
        if (a instanceof OdinValue.OdinNumber an && b instanceof OdinValue.OdinNumber bn) return an.getValue() == bn.getValue();
        if (a instanceof OdinValue.OdinCurrency ac && b instanceof OdinValue.OdinCurrency bc)
            return ac.getValue() == bc.getValue() && Objects.equals(ac.getCurrencyCode(), bc.getCurrencyCode());
        if (a instanceof OdinValue.OdinPercent ap && b instanceof OdinValue.OdinPercent bp) return ap.getValue() == bp.getValue();
        if (a instanceof OdinValue.OdinDate ad && b instanceof OdinValue.OdinDate bd) return Objects.equals(ad.getRaw(), bd.getRaw());
        if (a instanceof OdinValue.OdinTimestamp at && b instanceof OdinValue.OdinTimestamp bt) return Objects.equals(at.getRaw(), bt.getRaw());
        if (a instanceof OdinValue.OdinTime atm && b instanceof OdinValue.OdinTime btm) return Objects.equals(atm.getValue(), btm.getValue());
        if (a instanceof OdinValue.OdinDuration ad && b instanceof OdinValue.OdinDuration bd) return Objects.equals(ad.getValue(), bd.getValue());
        if (a instanceof OdinValue.OdinReference ar && b instanceof OdinValue.OdinReference br) return Objects.equals(ar.getPath(), br.getPath());
        if (a instanceof OdinValue.OdinBinary ab && b instanceof OdinValue.OdinBinary bb)
            return Objects.equals(ab.getAlgorithm(), bb.getAlgorithm()) && Arrays.equals(ab.getData(), bb.getData());

        // Complex types: fall back to toString comparison
        return Objects.equals(a.toString(), b.toString());
    }

    private static <T> void swapRemove(List<T> list, int index) {
        int last = list.size() - 1;
        if (index != last) {
            list.set(index, list.get(last));
        }
        list.remove(last);
    }
}
