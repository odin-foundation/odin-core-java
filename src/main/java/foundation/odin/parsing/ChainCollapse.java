package foundation.odin.parsing;

import foundation.odin.types.OdinDocument;
import foundation.odin.types.OdinModifiers;
import foundation.odin.types.OdinValue;
import foundation.odin.types.OrderedMap;

import java.util.ArrayList;
import java.util.List;

// Collapse a chain of documents into a computed current-state document via overlay semantics.
public final class ChainCollapse {
    private ChainCollapse() {}

    // Later documents overlay earlier ones: a repeated path replaces, `field = ~` removes the
    // field and its descendants, `field[] = ~` clears the array. The result carries the final
    // document's metadata.
    public static OdinDocument collapse(List<OdinDocument> docs) {
        var assignments = new OrderedMap<String, OdinValue>();
        var modifiers = new OrderedMap<String, OdinModifiers>();
        var metadata = new OrderedMap<String, OdinValue>();

        for (var doc : docs) {
            metadata = doc.getMetadata().copy();

            for (var path : doc.paths()) {
                if (path.startsWith("$.")) continue;

                var value = doc.get(path);
                if (value == null) continue;

                if (value.isNull()) {
                    if (path.endsWith("[]")) {
                        clearArray(assignments, modifiers, path.substring(0, path.length() - 2));
                    } else {
                        removePath(assignments, modifiers, path);
                    }
                    continue;
                }

                assignments.set(path, value);
                var mods = doc.getPathModifiers().tryGet(path);
                if (mods != null) {
                    modifiers.set(path, mods);
                } else {
                    modifiers.remove(path);
                }
            }
        }

        return new OdinDocument(metadata, assignments, modifiers, null, null, null, null);
    }

    // Remove a path and any nested descendants from the working maps.
    private static void removePath(OrderedMap<String, OdinValue> assignments,
                                   OrderedMap<String, OdinModifiers> modifiers, String path) {
        for (var key : new ArrayList<>(assignments.keys())) {
            if (key.equals(path) || key.startsWith(path + ".") || key.startsWith(path + "[")) {
                assignments.remove(key);
                modifiers.remove(key);
            }
        }
    }

    // Clear all indexed elements of an array path from the working maps.
    private static void clearArray(OrderedMap<String, OdinValue> assignments,
                                   OrderedMap<String, OdinModifiers> modifiers, String arrayPath) {
        String prefix = arrayPath + "[";
        for (var key : new ArrayList<>(assignments.keys())) {
            if (key.startsWith(prefix)) {
                assignments.remove(key);
                modifiers.remove(key);
            }
        }
    }
}
