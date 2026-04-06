package foundation.odin.types;

import java.util.List;
import java.util.Map;

public abstract sealed class OdinArrayItem
        permits OdinArrayItem.OdinArrayRecord, OdinArrayItem.OdinArrayValue {

    public static OdinArrayItem record(List<Map.Entry<String, OdinValue>> fields) {
        return new OdinArrayRecord(fields);
    }

    public static OdinArrayItem fromValue(OdinValue value) {
        return new OdinArrayValue(value);
    }

    public List<Map.Entry<String, OdinValue>> asRecord() { return null; }
    public OdinValue asValue() { return null; }

    public static final class OdinArrayRecord extends OdinArrayItem {
        private final List<Map.Entry<String, OdinValue>> fields;

        public OdinArrayRecord(List<Map.Entry<String, OdinValue>> fields) {
            this.fields = fields;
        }

        public List<Map.Entry<String, OdinValue>> getFields() { return fields; }

        @Override
        public List<Map.Entry<String, OdinValue>> asRecord() { return fields; }
    }

    public static final class OdinArrayValue extends OdinArrayItem {
        private final OdinValue value;

        public OdinArrayValue(OdinValue value) { this.value = value; }
        public OdinValue getValue() { return value; }

        @Override
        public OdinValue asValue() { return value; }
    }
}
