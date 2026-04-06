package foundation.odin.types;

import java.util.HashMap;
import java.util.Map;

public final class SourceConfig {
    private String format = "";
    private Map<String, String> options = new HashMap<>();
    private Map<String, String> namespaces = new HashMap<>();

    public SourceConfig() {}

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }

    public Map<String, String> getNamespaces() { return namespaces; }
    public void setNamespaces(Map<String, String> namespaces) { this.namespaces = namespaces; }
}
