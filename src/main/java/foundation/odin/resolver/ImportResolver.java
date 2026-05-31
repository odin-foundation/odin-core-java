package foundation.odin.resolver;

import foundation.odin.parsing.OdinParser;
import foundation.odin.types.*;
import foundation.odin.validation.SchemaParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

public final class ImportResolver {

    // ─── File Reader Interface ───────────────────────────────────────────────

    public interface FileReader {
        String readFile(String path);
        String resolvePath(String basePath, String importPath);
    }

    // ─── Default Filesystem Reader ───────────────────────────────────────────

    static final class DefaultFileReader implements FileReader {
        private final long maxFileSize;

        DefaultFileReader(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        @Override
        public String readFile(String path) {
            try {
                Path filePath = Paths.get(path);
                long size = Files.size(filePath);
                if (size > maxFileSize) {
                    throw new OdinErrors.OdinException("I005", "File too large: " + size + " bytes");
                }
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                throw new OdinErrors.OdinException("I006", "File not found: " + path);
            } catch (IOException e) {
                throw new OdinErrors.OdinException("I008", "Failed to load file: " + path + " - " + e.getMessage());
            }
        }

        @Override
        public String resolvePath(String basePath, String importPath) {
            Path baseDir = basePath != null ? Paths.get(basePath).getParent() : Paths.get(".");
            if (baseDir == null) baseDir = Paths.get(".");
            return baseDir.resolve(importPath).normalize().toString();
        }
    }

    // ─── Options ────────────────────────────────────────────────────────────

    public record ResolverOptions(
            String sandboxRoot,
            int maxImportDepth,
            boolean schemaMode,
            List<String> allowedExtensions,
            long maxFileSize
    ) {
        public ResolverOptions() {
            this(null, 32, true, List.of(".odin"), 10 * 1024 * 1024);
        }

        public static ResolverOptions forSchemas() { return new ResolverOptions(); }
        public static ResolverOptions forDocuments() {
            return new ResolverOptions(null, 32, false, List.of(".odin"), 10 * 1024 * 1024);
        }
    }

    // ─── Type Registry ──────────────────────────────────────────────────────

    public static final class TypeRegistry {
        private final Map<String, OdinSchema.SchemaType> localTypes = new LinkedHashMap<>();
        private final Map<String, Map<String, OdinSchema.SchemaType>> namespaces = new LinkedHashMap<>();

        public TypeRegistry() {}

        public void registerAll(Map<String, OdinSchema.SchemaType> types, String ns) {
            if (ns != null) {
                namespaces.computeIfAbsent(ns, k -> new LinkedHashMap<>()).putAll(types);
            } else {
                localTypes.putAll(types);
            }
        }

        public void registerAll(Map<String, OdinSchema.SchemaType> types) {
            registerAll(types, null);
        }

        public OdinSchema.SchemaType lookup(String name) {
            // Try local first
            var local = localTypes.get(name);
            if (local != null) return local;

            // Try namespaced: "namespace.TypeName"
            int dotPos = name.indexOf('.');
            if (dotPos >= 0) {
                String ns = name.substring(0, dotPos);
                String typeName = name.substring(dotPos + 1);
                var nsMap = namespaces.get(ns);
                if (nsMap != null) {
                    var found = nsMap.get(typeName);
                    if (found != null) return found;
                }
            }

            // Search all namespaces for unqualified name
            for (var nsMap : namespaces.values()) {
                var found = nsMap.get(name);
                if (found != null) return found;
            }

            return null;
        }

        public List<String> allTypeNames() {
            var names = new ArrayList<>(localTypes.keySet());
            for (var entry : namespaces.entrySet()) {
                for (var typeName : entry.getValue().keySet()) {
                    names.add(entry.getKey() + "." + typeName);
                }
            }
            return names;
        }

        public Map<String, OdinSchema.SchemaType> getLocalTypes() { return localTypes; }
        public Map<String, Map<String, OdinSchema.SchemaType>> getNamespaces() { return namespaces; }

        public boolean isEmpty() { return localTypes.isEmpty() && namespaces.isEmpty(); }

        /** Flat view of all types (local + namespaced with qualified keys). */
        public Map<String, OdinSchema.SchemaType> toFlatMap() {
            var flat = new LinkedHashMap<>(localTypes);
            for (var nsEntry : namespaces.entrySet()) {
                for (var typeEntry : nsEntry.getValue().entrySet()) {
                    flat.put(nsEntry.getKey() + "_" + typeEntry.getKey(), typeEntry.getValue());
                }
            }
            return flat;
        }
    }

    // ─── Circular Detector ──────────────────────────────────────────────────

    static final class CircularDetector {
        private final List<String> chain = new ArrayList<>();
        private final Set<String> chainSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        void enter(String path) {
            String normalized = normalizePath(path);
            if (chainSet.contains(normalized)) {
                String cycle = formatCycle(normalized);
                throw new OdinErrors.OdinException("I011", "Circular import detected: " + cycle);
            }
            chainSet.add(normalized);
            chain.add(normalized);
        }

        void exit() {
            if (!chain.isEmpty()) {
                String last = chain.remove(chain.size() - 1);
                chainSet.remove(last);
            }
        }

        boolean isCircular(String path) {
            return chainSet.contains(normalizePath(path));
        }

        private String formatCycle(String path) {
            var cycle = new ArrayList<String>();
            boolean found = false;
            for (String p : chain) {
                if (p.equalsIgnoreCase(path)) found = true;
                if (found) cycle.add(p);
            }
            cycle.add(path);
            return String.join(" -> ", cycle);
        }
    }

    // ─── Result Types ───────────────────────────────────────────────────────

    public record ResolvedImport(
            String alias,
            String path,
            String originalPath,
            OdinSchema.SchemaDefinition schema,
            OdinDocument document,
            int line
    ) {}

    public record ResolvedResult(
            Map<String, ResolvedImport> imports,
            Map<String, OdinSchema.SchemaType> typeRegistry,
            List<String> resolvedPaths,
            TypeRegistry registry
    ) {
        public ResolvedResult(Map<String, ResolvedImport> imports,
                Map<String, OdinSchema.SchemaType> typeRegistry, List<String> resolvedPaths) {
            this(imports, typeRegistry, resolvedPaths, new TypeRegistry());
        }
    }

    public record ResolvedSchema(
            OdinSchema.SchemaDefinition schema,
            ResolvedResult resolution
    ) {}

    public record ResolvedDocument(
            OdinDocument document,
            ResolvedResult resolution
    ) {}

    // ─── State ──────────────────────────────────────────────────────────────

    private final FileReader reader;
    private final ResolverOptions options;
    private final Map<String, CachedEntry> cache = new HashMap<>();
    private int totalFilesLoaded;

    // Parser delegates (injectable for testing, like .NET)
    private Function<String, OdinDocument> documentParser;
    private Function<String, OdinSchema.SchemaDefinition> schemaParserFn;

    public ImportResolver(FileReader reader, ResolverOptions options) {
        this.reader = reader != null ? reader : new DefaultFileReader(
                options != null ? options.maxFileSize() : 10 * 1024 * 1024);
        this.options = options != null ? options : new ResolverOptions();
    }

    public ImportResolver(ResolverOptions options) {
        this(null, options);
    }

    public ImportResolver() {
        this(null, new ResolverOptions());
    }

    public void setDocumentParser(Function<String, OdinDocument> parser) {
        this.documentParser = parser;
    }

    public void setSchemaParser(Function<String, OdinSchema.SchemaDefinition> parser) {
        this.schemaParserFn = parser;
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public ResolvedSchema resolveSchema(OdinSchema.SchemaDefinition schema, String basePath) {
        totalFilesLoaded = 0;
        var imports = new LinkedHashMap<String, ResolvedImport>();
        var typeRegistry = new LinkedHashMap<String, OdinSchema.SchemaType>();
        var registry = new TypeRegistry();
        var resolvedPaths = new ArrayList<String>();
        var detector = new CircularDetector();

        if (schema.types() != null) {
            typeRegistry.putAll(schema.types());
            registry.registerAll(schema.types());
        }

        if (schema.imports() != null) {
            for (var imp : schema.imports()) {
                resolveSchemaImport(imp, basePath, detector, 0, imports, typeRegistry, registry, resolvedPaths);
            }
        }

        var resolution = new ResolvedResult(imports, typeRegistry, resolvedPaths, registry);
        return new ResolvedSchema(schema, resolution);
    }

    public ResolvedDocument resolveDocument(OdinDocument document, String basePath) {
        totalFilesLoaded = 0;
        var imports = new LinkedHashMap<String, ResolvedImport>();
        var typeRegistry = new LinkedHashMap<String, OdinSchema.SchemaType>();
        var registry = new TypeRegistry();
        var resolvedPaths = new ArrayList<String>();
        var detector = new CircularDetector();

        if (document.getImports() != null) {
            for (var imp : document.getImports()) {
                resolveDocumentImport(imp, basePath, detector, 0, imports, typeRegistry, resolvedPaths);
            }
        }

        var resolution = new ResolvedResult(imports, typeRegistry, resolvedPaths, registry);
        return new ResolvedDocument(document, resolution);
    }

    public ResolvedSchema resolveSchemaFile(String filePath) {
        String content = loadFile(filePath);
        var schema = parseSchemaText(content);
        return resolveSchema(schema, filePath);
    }

    public ResolvedDocument resolveDocumentFile(String filePath) {
        String content = loadFile(filePath);
        var doc = parseDocumentText(content);
        return resolveDocument(doc, filePath);
    }

    // ─── Internal Resolution ────────────────────────────────────────────────

    private void resolveSchemaImport(
            OdinSchema.SchemaImport imp,
            String basePath,
            CircularDetector detector,
            int depth,
            Map<String, ResolvedImport> imports,
            Map<String, OdinSchema.SchemaType> typeRegistry,
            TypeRegistry registry,
            List<String> resolvedPaths
    ) {
        if (depth > options.maxImportDepth()) {
            throw new OdinErrors.OdinException("I012", "Maximum import depth exceeded: " + depth);
        }

        String resolvedPath = resolveImportPath(basePath, imp.path());
        String normalizedPath = normalizePath(resolvedPath);

        if (detector.isCircular(normalizedPath)) {
            throw new OdinErrors.OdinException("I011", "Circular import detected: " +
                    normalizedPath);
        }

        if (imports.containsKey(imp.alias())) return;

        totalFilesLoaded++;
        if (totalFilesLoaded > 100) {
            throw new OdinErrors.OdinException("I013", "Maximum total imports exceeded");
        }

        detector.enter(normalizedPath);
        try {
            OdinSchema.SchemaDefinition importedSchema;
            String cacheKey = normalizePath(resolvedPath);
            var cached = cache.get(cacheKey);
            if (cached != null && cached.schema != null) {
                importedSchema = cached.schema;
            } else {
                String content = loadFile(resolvedPath);
                importedSchema = parseSchemaText(content);
                cache.put(cacheKey, new CachedEntry(importedSchema));
            }

            resolvedPaths.add(resolvedPath);

            String namespace = imp.alias();
            if (importedSchema.types() != null) {
                for (var entry : importedSchema.types().entrySet()) {
                    String qualifiedName = namespace + "_" + entry.getKey();
                    typeRegistry.put(qualifiedName, entry.getValue());
                }
                registry.registerAll(importedSchema.types(), namespace);
            }

            if (importedSchema.imports() != null) {
                for (var nestedImp : importedSchema.imports()) {
                    resolveSchemaImport(nestedImp, resolvedPath, detector, depth + 1,
                            imports, typeRegistry, registry, resolvedPaths);
                }
            }

            imports.put(imp.alias(), new ResolvedImport(
                    imp.alias(), resolvedPath, imp.path(), importedSchema, null, 0));

        } finally {
            detector.exit();
        }
    }

    private void resolveDocumentImport(
            OdinImport imp,
            String basePath,
            CircularDetector detector,
            int depth,
            Map<String, ResolvedImport> imports,
            Map<String, OdinSchema.SchemaType> typeRegistry,
            List<String> resolvedPaths
    ) {
        if (depth > options.maxImportDepth()) {
            throw new OdinErrors.OdinException("I012", "Maximum import depth exceeded: " + depth);
        }

        String resolvedPath = resolveImportPath(basePath, imp.path());
        String normalizedPath = normalizePath(resolvedPath);

        if (detector.isCircular(normalizedPath)) {
            throw new OdinErrors.OdinException("I011", "Circular import detected: " +
                    normalizedPath);
        }

        String alias = imp.alias() != null ? imp.alias() : imp.path();
        if (imports.containsKey(alias)) return;

        totalFilesLoaded++;
        if (totalFilesLoaded > 100) {
            throw new OdinErrors.OdinException("I013", "Maximum total imports exceeded");
        }

        detector.enter(normalizedPath);
        try {
            OdinDocument importedDoc;
            String cacheKey = normalizePath(resolvedPath);
            var cached = cache.get(cacheKey);
            if (cached != null && cached.document != null) {
                importedDoc = cached.document;
            } else {
                String content = loadFile(resolvedPath);
                importedDoc = parseDocumentText(content);
                cache.put(cacheKey, new CachedEntry(importedDoc));
            }

            resolvedPaths.add(resolvedPath);

            if (importedDoc.getImports() != null) {
                for (var nestedImp : importedDoc.getImports()) {
                    resolveDocumentImport(nestedImp, resolvedPath, detector, depth + 1,
                            imports, typeRegistry, resolvedPaths);
                }
            }

            imports.put(alias, new ResolvedImport(
                    alias, resolvedPath, imp.path(), null, importedDoc, imp.line()));

        } finally {
            detector.exit();
        }
    }

    // ─── Parsing Helpers ────────────────────────────────────────────────────

    private OdinDocument parseDocumentText(String content) {
        if (documentParser != null) return documentParser.apply(content);
        return OdinParser.parse(content, null);
    }

    private OdinSchema.SchemaDefinition parseSchemaText(String content) {
        if (schemaParserFn != null) return schemaParserFn.apply(content);
        return SchemaParser.parse(content);
    }

    // ─── Path Resolution ────────────────────────────────────────────────────

    private String resolveImportPath(String basePath, String importPath) {
        if (importPath == null || importPath.isEmpty()) {
            throw new OdinErrors.OdinException("I001", "Empty import path");
        }

        if (importPath.indexOf('\0') >= 0) {
            throw new OdinErrors.OdinException("I001", "Null byte in import path");
        }

        if (importPath.startsWith("http://") || importPath.startsWith("https://")) {
            throw new OdinErrors.OdinException("I001", "Remote URLs not supported");
        }

        boolean validExt = false;
        String importPathLower = importPath.toLowerCase();
        for (String ext : options.allowedExtensions()) {
            if (importPathLower.endsWith(ext.toLowerCase())) { validExt = true; break; }
        }
        if (!validExt) {
            throw new OdinErrors.OdinException("I003", "File extension not allowed: " + importPath);
        }

        String resolved = reader.resolvePath(basePath, importPath);

        if (options.sandboxRoot() != null) {
            Path sandbox = Paths.get(options.sandboxRoot()).normalize();
            if (!Paths.get(resolved).normalize().startsWith(sandbox)) {
                throw new OdinErrors.OdinException("I004", "Path escapes sandbox: " + importPath);
            }
        }

        return resolved;
    }

    private String loadFile(String path) {
        return reader.readFile(path);
    }

    static String normalizePath(String path) {
        return path.replace('\\', '/').toLowerCase();
    }

    // ─── Cache Entry ────────────────────────────────────────────────────────

    private static final class CachedEntry {
        final OdinSchema.SchemaDefinition schema;
        final OdinDocument document;

        CachedEntry(OdinSchema.SchemaDefinition schema) {
            this.schema = schema;
            this.document = null;
        }

        CachedEntry(OdinDocument document) {
            this.schema = null;
            this.document = document;
        }
    }

    // ─── Factory ────────────────────────────────────────────────────────────

    public static ImportResolver create(FileReader reader, ResolverOptions options) {
        return new ImportResolver(reader, options);
    }

    public static ImportResolver create(ResolverOptions options) {
        return new ImportResolver(options);
    }

    public static ImportResolver create() {
        return new ImportResolver();
    }
}
