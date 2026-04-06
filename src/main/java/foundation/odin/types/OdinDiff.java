package foundation.odin.types;

import java.util.List;

public record OdinDiff(
        List<DiffEntry> added,
        List<DiffEntry> removed,
        List<DiffChange> changed,
        List<DiffMove> moved
) {
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty() && moved.isEmpty();
    }

    public record DiffEntry(String path, OdinValue value) {}
    public record DiffChange(String path, OdinValue oldValue, OdinValue newValue) {}
    public record DiffMove(String fromPath, String toPath, OdinValue value) {}
}
