package foundation.odin.transform;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import foundation.odin.types.DynValue;

public final class JsonSourceParser {

    private JsonSourceParser() {}

    public static DynValue parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("JSON input is null or empty.");
        }
        try {
            var element = JsonParser.parseString(input);
            return DynValue.fromJsonElement(element);
        } catch (JsonSyntaxException ex) {
            throw new FormatException("Invalid JSON: " + ex.getMessage(), ex);
        }
    }
}
