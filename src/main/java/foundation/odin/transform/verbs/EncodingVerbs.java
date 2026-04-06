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
import java.util.Base64;
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
