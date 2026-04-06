package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;

public final class GenerationVerbs {

    private GenerationVerbs() {}

    private static final String NANOID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_-";
    private static final ThreadLocal<Random> THREAD_RANDOM = ThreadLocal.withInitial(Random::new);

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("uuid", GenerationVerbs::uuid);
        reg.put("sequence", GenerationVerbs::sequence);
        reg.put("resetSequence", GenerationVerbs::resetSequence);
        reg.put("nanoid", GenerationVerbs::nanoid);
    }

    private static DynValue uuid(DynValue[] args, VerbContext ctx) {
        if (args.length > 0) {
            String seed = args[0].asString();
            if (seed != null && !seed.isEmpty()) {
                return DynValue.ofString(generateSeededUUID(seed));
            }
        }
        return DynValue.ofString(UUID.randomUUID().toString());
    }

    /**
     * Generate a deterministic UUID v4 from a seed string.
     * Uses DJB2 hash (starting at 5381/52711) then extracts bytes via bit shifting.
     * Matches the TypeScript implementation exactly.
     */
    private static String generateSeededUUID(String seed) {
        // DJB2 variant - two hashes for 16 bytes
        int hash1 = 5381;
        int hash2 = 52711;

        for (int i = 0; i < seed.length(); i++) {
            char c = seed.charAt(i);
            hash1 = ((hash1 << 5) + hash1) ^ c;
            hash2 = ((hash2 << 5) + hash2) ^ c;
        }

        // Generate 16 bytes from the hashes using signed right shift
        // (matches JavaScript's >> operator, which is signed/arithmetic shift)
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) ((hash1 >> (i * 4)) & 0xFF);
            bytes[i + 8] = (byte) ((hash2 >> (i * 4)) & 0xFF);
        }

        // Set version 5 (name-based) and variant (10xx) bits
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x50); // Version 5
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80); // Variant 10xx

        // Convert to hex string with dashes (matching TypeScript's inline formatting)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static DynValue sequence(DynValue[] args, VerbContext ctx) {
        String name = "default";
        if (args.length > 0) {
            var n = args[0].asString();
            if (n != null) name = n;
        }

        String key = "__seq_" + name;
        long current = 0;

        var existing = ctx.getAccumulators().get(key);
        if (existing != null) {
            var val = existing.asInt64();
            if (val != null) current = val;
            else {
                var dVal = existing.asDouble();
                if (dVal != null) current = dVal.longValue();
            }
        }

        ctx.getAccumulators().put(key, DynValue.ofInteger(current + 1));
        return DynValue.ofInteger(current);
    }

    private static DynValue resetSequence(DynValue[] args, VerbContext ctx) {
        String name = "default";
        if (args.length > 0) {
            var n = args[0].asString();
            if (n != null) name = n;
        }

        long resetTo = 0;
        if (args.length > 1) {
            var d = args[1].asDouble();
            if (d != null) resetTo = d.longValue();
            else {
                var i = args[1].asInt64();
                if (i != null) resetTo = i;
            }
        }

        String key = "__seq_" + name;
        ctx.getAccumulators().put(key, DynValue.ofInteger(resetTo));
        return DynValue.ofInteger(resetTo);
    }

    private static DynValue nanoid(DynValue[] args, VerbContext ctx) {
        int length = 21;
        if (args.length > 0) {
            var d = args[0].asDouble();
            if (d != null && d > 0) length = d.intValue();
            else {
                var i = args[0].asInt64();
                if (i != null && i > 0) length = (int) i.longValue();
            }
        }

        var rng = THREAD_RANDOM.get();
        var chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = NANOID_ALPHABET.charAt(rng.nextInt(NANOID_ALPHABET.length()));
        }
        return DynValue.ofString(new String(chars));
    }
}
