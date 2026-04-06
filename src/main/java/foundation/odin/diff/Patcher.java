package foundation.odin.diff;

import foundation.odin.types.*;
import foundation.odin.types.OdinErrors.PatchException;

public final class Patcher {

    private Patcher() {}

    public static OdinDocument apply(OdinDocument doc, OdinDiff diff) {
        var assignments = doc.getAssignments().copy();

        // 1. Removals
        for (var entry : diff.removed()) {
            if (!assignments.containsKey(entry.path())) {
                throw new PatchException("Cannot remove non-existent path: " + entry.path(), entry.path());
            }
            assignments.remove(entry.path());
        }

        // 2. Changes
        for (var change : diff.changed()) {
            if (!assignments.containsKey(change.path())) {
                throw new PatchException("Cannot change non-existent path: " + change.path(), change.path());
            }
            assignments.set(change.path(), change.newValue());
        }

        // 3. Additions
        for (var entry : diff.added()) {
            assignments.set(entry.path(), entry.value());
        }

        // 4. Moves
        for (var move : diff.moved()) {
            OdinValue val = assignments.tryGet(move.fromPath());
            if (val == null) {
                throw new PatchException("Cannot move from non-existent path: " + move.fromPath(), move.fromPath());
            }
            assignments.remove(move.fromPath());
            assignments.set(move.toPath(), val);
        }

        return new OdinDocument(
                doc.getMetadata().copy(),
                assignments,
                doc.getPathModifiers().copy(),
                doc.getImports(),
                doc.getSchemas(),
                doc.getConditionals(),
                doc.getComments());
    }
}
