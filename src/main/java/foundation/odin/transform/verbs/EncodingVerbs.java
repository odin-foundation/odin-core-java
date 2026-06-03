package foundation.odin.transform.verbs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.zip.CRC32;

public final class EncodingVerbs {

    private EncodingVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("base64Encode", EncodingVerbs::base64Encode);
        reg.put("base64Decode", EncodingVerbs::base64Decode);
        reg.put("urlEncode", EncodingVerbs::urlEncode);
        reg.put("urlDecode", EncodingVerbs::urlDecode);
        reg.put("jsonEncode", EncodingVerbs::jsonEncode);
        reg.put("jsonDecode", EncodingVerbs::jsonDecode);
        reg.put("hexEncode", EncodingVerbs::hexEncode);
        reg.put("hexDecode", EncodingVerbs::hexDecode);
        reg.put("sha256", EncodingVerbs::sha256);
        reg.put("sha1", EncodingVerbs::sha1);
        reg.put("sha512", EncodingVerbs::sha512);
        reg.put("md5", EncodingVerbs::md5);
        reg.put("crc32", EncodingVerbs::crc32);
        reg.put("jsonPath", EncodingVerbs::jsonPath);
        reg.put("base64urlEncode", EncodingVerbs::base64urlEncode);
        reg.put("base64urlDecode", EncodingVerbs::base64urlDecode);
        reg.put("hmac", EncodingVerbs::hmac);
        reg.put("parseUrl", EncodingVerbs::parseUrl);
        reg.put("buildUrl", EncodingVerbs::buildUrl);
        reg.put("parseQuery", EncodingVerbs::parseQuery);
        reg.put("buildQuery", EncodingVerbs::buildQuery);
        reg.put("stableStringify", EncodingVerbs::stableStringify);
        reg.put("canonicalHash", EncodingVerbs::canonicalHash);
    }

    // ── Helpers ──

    private static String coerceStr(DynValue v) {
        if (v.isNull()) return null;
        var s = v.asString();
        if (s != null) return s;
        var i = v.asInt64();
        if (i != null) return Long.toString(i);
        var d = v.asDouble();
        if (d != null) return Double.toString(d);
        var b = v.asBool();
        if (b != null) return b ? "true" : "false";
        return v.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) hex = "0" + hex;
        var bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String hashWith(String algorithm, String input) {
        try {
            var md = MessageDigest.getInstance(algorithm);
            var hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available: " + algorithm, e);
        }
    }

    private static String serializeDynValue(DynValue v) {
        return v.toJsonElement().toString();
    }

    // ── Verb Implementations ──

    private static DynValue base64Encode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static DynValue base64Decode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        try {
            return DynValue.ofString(new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue urlEncode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static DynValue urlDecode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(URLDecoder.decode(s, StandardCharsets.UTF_8));
    }

    private static DynValue jsonEncode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofString("null");
        return DynValue.ofString(serializeDynValue(args[0]));
    }

    private static DynValue jsonDecode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        try {
            JsonElement element = JsonParser.parseString(s);
            return DynValue.fromJsonElement(element);
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue hexEncode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(bytesToHex(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static DynValue hexDecode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        try {
            return DynValue.ofString(new String(hexToBytes(s), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue sha256(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(hashWith("SHA-256", s));
    }

    private static DynValue sha1(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(hashWith("SHA-1", s));
    }

    private static DynValue sha512(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(hashWith("SHA-512", s));
    }

    private static DynValue md5(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(hashWith("MD5", s));
    }

    private static DynValue crc32(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        var crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return DynValue.ofString(String.format("%08x", crc.getValue()));
    }

    // ── URL-safe base64, HMAC, URL/query parsing, stable serialization ──

    private static DynValue base64urlEncode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        return DynValue.ofString(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static DynValue base64urlDecode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var s = coerceStr(args[0]);
        if (s == null) return DynValue.ofNull();
        try {
            return DynValue.ofString(new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue hmac(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        var message = coerceStr(args[0]);
        var key = coerceStr(args[1]);
        if (message == null || key == null) return DynValue.ofNull();
        String alg = args.length >= 3 ? coerceStr(args[2]) : "sha256";
        if (alg == null) alg = "sha256";
        String javaAlg = switch (alg.toLowerCase()) {
            case "sha1" -> "HmacSHA1";
            case "sha256" -> "HmacSHA256";
            case "sha512" -> "HmacSHA512";
            case "md5" -> "HmacMD5";
            default -> null;
        };
        if (javaAlg == null) return DynValue.ofNull();
        try {
            var mac = javax.crypto.Mac.getInstance(javaAlg);
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), javaAlg));
            return DynValue.ofString(bytesToHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    // Decode a percent-encoded query component (+ as space).
    private static String decodeComponent(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    // Parse a query string into a sorted map (first value per key wins on display).
    private static List<Map.Entry<String, DynValue>> sortedQuery(String raw) {
        var values = new java.util.TreeMap<String, String>();
        if (raw != null && !raw.isEmpty()) {
            for (String pair : raw.split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String k = eq >= 0 ? pair.substring(0, eq) : pair;
                String v = eq >= 0 ? pair.substring(eq + 1) : "";
                k = decodeComponent(k);
                v = decodeComponent(v);
                values.putIfAbsent(k, v);
            }
        }
        var out = new ArrayList<Map.Entry<String, DynValue>>();
        for (var e : values.entrySet()) out.add(Map.entry(e.getKey(), DynValue.ofString(e.getValue())));
        return out;
    }

    private static DynValue parseUrl(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var raw = coerceStr(args[0]);
        if (raw == null) return DynValue.ofNull();
        try {
            var u = new java.net.URI(raw);
            if (u.getScheme() == null || u.getHost() == null) return DynValue.ofNull();
            var fields = new ArrayList<Map.Entry<String, DynValue>>();
            fields.add(Map.entry("scheme", DynValue.ofString(u.getScheme())));
            fields.add(Map.entry("host", DynValue.ofString(u.getHost())));
            fields.add(Map.entry("port", u.getPort() == -1 ? DynValue.ofNull() : DynValue.ofInteger(u.getPort())));
            fields.add(Map.entry("path", DynValue.ofString(u.getRawPath() != null ? u.getRawPath() : "")));
            fields.add(Map.entry("query", DynValue.ofObject(sortedQuery(u.getRawQuery()))));
            fields.add(Map.entry("fragment", DynValue.ofString(u.getRawFragment() != null ? u.getRawFragment() : "")));
            return DynValue.ofObject(fields);
        } catch (Exception e) {
            return DynValue.ofNull();
        }
    }

    private static DynValue parseQuery(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var raw = coerceStr(args[0]);
        if (raw == null) return DynValue.ofNull();
        if (raw.startsWith("?")) raw = raw.substring(1);
        return DynValue.ofObject(sortedQuery(raw));
    }

    private static String encodeComponent(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static DynValue buildQuery(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = args[0].asObject();
        if (obj == null) obj = args[0].extractObject();
        if (obj == null) return DynValue.ofNull();
        var sorted = new java.util.TreeMap<String, DynValue>();
        for (var e : obj) sorted.put(e.getKey(), e.getValue());
        var sb = new StringBuilder();
        for (var e : sorted.entrySet()) {
            if (e.getValue().isNull()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(encodeComponent(e.getKey())).append('=').append(encodeComponent(coerceStr(e.getValue())));
        }
        return DynValue.ofString(sb.toString());
    }

    private static DynValue buildUrl(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        var obj = args[0].asObject();
        if (obj == null) obj = args[0].extractObject();
        if (obj == null) return DynValue.ofNull();
        var fields = new HashMap<String, DynValue>();
        for (var e : obj) fields.put(e.getKey(), e.getValue());
        java.util.function.Function<String, String> get = k -> {
            var v = fields.get(k);
            return v == null || v.isNull() ? "" : coerceStr(v);
        };
        String scheme = get.apply("scheme");
        String host = get.apply("host");
        if (scheme.isEmpty() || host.isEmpty()) return DynValue.ofNull();
        String port = get.apply("port");
        String path = get.apply("path");
        String fragment = get.apply("fragment");
        var url = new StringBuilder(scheme).append("://").append(host);
        if (!port.isEmpty()) url.append(':').append(port);
        if (path.startsWith("/")) url.append(path);
        else if (!path.isEmpty()) url.append('/').append(path);
        var q = fields.get("query");
        if (q != null && (q.asObject() != null || q.extractObject() != null)) {
            var qStr = buildQuery(new DynValue[]{ q }, ctx);
            var qs = qStr.asString();
            if (qs != null && !qs.isEmpty()) url.append('?').append(qs);
        }
        if (!fragment.isEmpty()) url.append('#').append(fragment);
        return DynValue.ofString(url.toString());
    }

    // Canonical JSON with object keys sorted recursively.
    private static String stableJson(DynValue v) {
        switch (v.getType()) {
            case Null:
                return "null";
            case Array: {
                var arr = v.asArray();
                var sb = new StringBuilder("[");
                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(stableJson(arr.get(i)));
                }
                return sb.append(']').toString();
            }
            case Object: {
                var obj = v.asObject();
                var keys = new ArrayList<String>();
                var map = new HashMap<String, DynValue>();
                for (var e : obj) { keys.add(e.getKey()); map.put(e.getKey(), e.getValue()); }
                Collections.sort(keys);
                var sb = new StringBuilder("{");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(new com.google.gson.JsonPrimitive(keys.get(i)).toString())
                      .append(':').append(stableJson(map.get(keys.get(i))));
                }
                return sb.append('}').toString();
            }
            default:
                return v.toJsonElement().toString();
        }
    }

    private static DynValue stableStringify(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofString(stableJson(args[0]));
    }

    private static DynValue canonicalHash(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        return DynValue.ofString(hashWith("SHA-256", stableJson(args[0])));
    }

    private static DynValue jsonPath(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();

        DynValue root;
        if (args[0].getType() == DynValue.Type.Object || args[0].getType() == DynValue.Type.Array) {
            root = args[0];
        } else {
            var jsonStr = coerceStr(args[0]);
            if (jsonStr == null) return DynValue.ofNull();
            try {
                root = DynValue.fromJsonElement(JsonParser.parseString(jsonStr));
            } catch (Exception e) {
                return DynValue.ofNull();
            }
        }

        var path = coerceStr(args[1]);
        if (path == null) return DynValue.ofNull();

        path = path.trim();
        if (path.startsWith("$")) path = path.substring(1);
        if (path.startsWith(".") && !path.startsWith("..")) path = path.substring(1);
        if (path.isEmpty()) return root;

        var segments = path.split("\\.");
        var current = root;
        for (var seg : segments) {
            if (seg == null || seg.isEmpty()) continue;

            int bracketPos = seg.indexOf('[');
            if (bracketPos >= 0 && seg.length() > bracketPos + 1 && seg.charAt(seg.length() - 1) == ']') {
                var fieldPart = seg.substring(0, bracketPos);
                var indexStr = seg.substring(bracketPos + 1, seg.length() - 1);

                if (!fieldPart.isEmpty()) {
                    var fieldVal = current.get(fieldPart);
                    if (fieldVal == null) return DynValue.ofNull();
                    current = fieldVal;
                }

                try {
                    int idx = Integer.parseInt(indexStr);
                    var elem = current.getIndex(idx);
                    if (elem == null) return DynValue.ofNull();
                    current = elem;
                } catch (NumberFormatException e) {
                    return DynValue.ofNull();
                }
            } else {
                var next = current.get(seg);
                if (next == null) return DynValue.ofNull();
                current = next;
            }
        }
        return current;
    }
}
