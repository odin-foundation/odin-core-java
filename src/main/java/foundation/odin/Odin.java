package foundation.odin;

import foundation.odin.diff.Differ;
import foundation.odin.diff.Patcher;
import foundation.odin.forms.FormParser;
import foundation.odin.forms.FormRenderer;
import foundation.odin.forms.FormTypes.OdinForm;
import foundation.odin.forms.FormTypes.RenderFormOptions;
import foundation.odin.parsing.OdinParser;
import foundation.odin.resolver.ImportResolver;
import foundation.odin.resolver.SchemaFlattener;
import foundation.odin.serialization.Canonicalize;
import foundation.odin.serialization.Stringify;
import foundation.odin.validation.SchemaParser;
import foundation.odin.validation.SchemaSerializer;
import foundation.odin.validation.ValidationEngine;
import foundation.odin.export.JsonExport;
import foundation.odin.export.XmlExport;
import foundation.odin.transform.TransformEngine;
import foundation.odin.transform.TransformParser;
import foundation.odin.types.*;
import foundation.odin.types.OdinTransformTypes.*;

import java.util.List;

public final class Odin {

    private static final String VERSION = "1.0.0";

    private Odin() {
        throw new UnsupportedOperationException("Odin is a static class");
    }

    public static String getVersion() {
        return VERSION;
    }

    // ── Parse ──

    public static OdinDocument parse(String text) {
        return parse(text, null);
    }

    public static OdinDocument parse(String text, OdinOptions.ParseOptions options) {
        return OdinParser.parse(text, options != null ? options : OdinOptions.ParseOptions.DEFAULT);
    }

    public static OdinDocument parse(byte[] utf8, OdinOptions.ParseOptions options) {
        return parse(new String(utf8, java.nio.charset.StandardCharsets.UTF_8), options);
    }

    public static List<OdinDocument> parseDocuments(String text) {
        return OdinParser.parseMulti(text, OdinOptions.ParseOptions.DEFAULT);
    }

    public static OdinDocument empty() {
        return OdinDocument.empty();
    }

    public static OdinDocumentBuilder builder() {
        return new OdinDocumentBuilder();
    }

    // ── Serialize ──

    public static String serialize(OdinDocument doc) {
        return serialize(doc, null);
    }

    public static String serialize(OdinDocument doc, OdinOptions.StringifyOptions options) {
        return Stringify.serialize(doc, options);
    }

    // ── Canonicalize ──

    public static byte[] canonicalize(OdinDocument doc) {
        return Canonicalize.serialize(doc);
    }

    // ── Validate ──

    public static OdinSchema.ValidationResult validate(OdinDocument doc, OdinSchema.SchemaDefinition schema) {
        return validate(doc, schema, null);
    }

    public static OdinSchema.ValidationResult validate(OdinDocument doc, OdinSchema.SchemaDefinition schema,
                                                        OdinOptions.ValidateOptions options) {
        return ValidationEngine.validate(doc, schema, options);
    }

    // ── Schema ──

    public static OdinSchema.SchemaDefinition parseSchema(String schemaText) {
        return SchemaParser.parse(schemaText);
    }

    public static String serializeSchema(OdinSchema.SchemaDefinition schema) {
        return SchemaSerializer.serialize(schema);
    }

    // ── Diff & Patch ──

    public static OdinDiff diff(OdinDocument a, OdinDocument b) {
        return Differ.computeDiff(a, b);
    }

    public static OdinDocument patch(OdinDocument doc, OdinDiff diff) {
        return Patcher.apply(doc, diff);
    }

    // ── Resolver & Flattener ──

    public static ImportResolver createImportResolver(ImportResolver.FileReader reader,
                                                       ImportResolver.ResolverOptions options) {
        return new ImportResolver(reader, options);
    }

    public static ImportResolver createImportResolver(ImportResolver.ResolverOptions options) {
        return new ImportResolver(options);
    }

    public static SchemaFlattener createSchemaFlattener(SchemaFlattener.FlattenerOptions options) {
        return new SchemaFlattener(options);
    }

    public static SchemaFlattener createSchemaFlattener() {
        return new SchemaFlattener();
    }

    // ── Transform ──

    public static OdinTransform parseTransform(String transformText) {
        return TransformParser.parse(transformText);
    }

    public static TransformResult executeTransform(String transformText, Object input) {
        var transform = parseTransform(transformText);
        DynValue source;
        if (input instanceof DynValue dv) {
            source = dv;
        } else if (input instanceof String s) {
            source = DynValue.ofString(s);
        } else {
            source = DynValue.ofNull();
        }
        return TransformEngine.execute(transform, source);
    }

    // ── Export ──

    public static String toJson(OdinDocument doc) {
        return JsonExport.toJson(doc, false, false);
    }

    public static String toJson(OdinDocument doc, boolean preserveTypes, boolean preserveModifiers) {
        return JsonExport.toJson(doc, preserveTypes, preserveModifiers);
    }

    public static String toXml(OdinDocument doc) {
        return XmlExport.toXml(doc, false, false);
    }

    public static String toXml(OdinDocument doc, boolean preserveTypes, boolean preserveModifiers) {
        return XmlExport.toXml(doc, preserveTypes, preserveModifiers);
    }

    // ── Forms ──

    /**
     * Parse an ODIN forms document text into a typed {@link OdinForm}.
     *
     * @param text raw ODIN text from a {@code .odin} forms file
     * @return parsed OdinForm with metadata, page defaults, and pages
     */
    public static OdinForm parseForm(String text) {
        return FormParser.parseForm(text);
    }

    /**
     * Render an {@link OdinForm} to a complete accessible HTML string.
     *
     * @param form    parsed OdinForm structure
     * @param data    optional ODIN document for data binding; may be null
     * @param options rendering options (target, scale, className, lang); may be null
     * @return complete HTML string
     */
    public static String renderForm(OdinForm form, OdinDocument data, RenderFormOptions options) {
        return FormRenderer.renderForm(form, data, options);
    }

    // ── Utilities ──

    public static String path(Object... segments) {
        if (segments.length == 0) return "";
        var sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            var segment = segments[i];
            if (segment instanceof Integer) {
                sb.append('[').append(segment).append(']');
            } else if (i == 0) {
                sb.append(segment);
            } else {
                sb.append('.').append(segment);
            }
        }
        return sb.toString();
    }
}
