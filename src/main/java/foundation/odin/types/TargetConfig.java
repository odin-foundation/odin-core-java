package foundation.odin.types;

import java.util.HashMap;
import java.util.Map;

public final class TargetConfig {
    private String format = "";
    private Map<String, String> options = new HashMap<>();

    public TargetConfig() {}

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Map<String, String> getOptions() { return options; }
    public void setOptions(Map<String, String> options) { this.options = options; }
}
