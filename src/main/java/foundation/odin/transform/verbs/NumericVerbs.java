package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

public final class NumericVerbs {

    private NumericVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("formatNumber", NumericVerbs::formatNumber);
        reg.put("formatInteger", NumericVerbs::formatInteger);
        reg.put("formatCurrency", NumericVerbs::formatCurrency);
        reg.put("floor", NumericVerbs::floor);
        reg.put("ceil", NumericVerbs::ceil);
        reg.put("negate", NumericVerbs::negate);
        reg.put("sign", NumericVerbs::sign);
        reg.put("trunc", NumericVerbs::trunc);
        reg.put("random", NumericVerbs::randomVerb);
        reg.put("minOf", NumericVerbs::minOf);
        reg.put("maxOf", NumericVerbs::maxOf);
        reg.put("formatPercent", NumericVerbs::formatPercent);
        reg.put("parseInt", NumericVerbs::parseInt);
        reg.put("safeDivide", NumericVerbs::safeDivide);
        reg.put("formatLocaleNumber", NumericVerbs::formatLocaleNumber);
        reg.put("add", NumericVerbs::add);
        reg.put("subtract", NumericVerbs::subtract);
        reg.put("multiply", NumericVerbs::multiply);
        reg.put("divide", NumericVerbs::divide);
        reg.put("abs", NumericVerbs::abs);
        reg.put("round", NumericVerbs::round);
        reg.put("mod", NumericVerbs::mod);
        reg.put("convertUnit", NumericVerbs::convertUnit);
    }

    // ── Helpers ──

    private static Double toDouble(DynValue v) {
        if (v.isNull()) return null;
        switch (v.getType()) {
            case Integer: {
                Long l = v.asInt64();
                return l != null ? (double) l : null;
            }
            case Float, Currency, Percent:
                return v.asDouble();
            case FloatRaw, CurrencyRaw: {
                String s = v.asString();
                if (s != null) {
                    try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
                }
                return null;
            }
            case String: {
                String s = v.asString();
                if (s != null) {
                    try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
                }
                return null;
            }
            default:
                return null;
        }
    }

    private static DynValue numericResult(double v) {
        if (v % 1.0 == 0.0 && Math.abs(v) < (double) Long.MAX_VALUE)
            return DynValue.ofInteger((long) v);
        return DynValue.ofFloat(v);
    }

    // ── Verb Implementations (25) ──

    private static DynValue formatNumber(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        Double places = toDouble(args[1]);
        int decimals = places != null ? (int) places.doubleValue() : 2;
        if (decimals < 0) decimals = 0;
        return DynValue.ofString(String.format(Locale.US, "%." + decimals + "f", val));
    }

    private static DynValue formatInteger(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        long intVal = (long) val.doubleValue();
        return DynValue.ofString(Long.toString(intVal));
    }

    private static DynValue formatCurrency(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        int decimals = 2;
        if (args.length >= 2) {
            Double d = toDouble(args[1]);
            if (d != null) decimals = (int) d.doubleValue();
        }
        return DynValue.ofString(String.format(Locale.US, "%." + decimals + "f", val));
    }

    private static DynValue floor(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.floor(val));
    }

    private static DynValue ceil(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.ceil(val));
    }

    private static DynValue negate(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(-val);
    }

    private static DynValue sign(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        if (val > 0) return DynValue.ofInteger(1);
        if (val < 0) return DynValue.ofInteger(-1);
        return DynValue.ofInteger(0);
    }

    private static DynValue trunc(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(val >= 0 ? Math.floor(val) : Math.ceil(val));
    }

    // ── Mulberry32 PRNG (matches TypeScript seededRandom exactly) ──

    static int stringToSeed(String s) {
        int hash = 0;
        for (int i = 0; i < s.length(); i++) {
            hash = ((hash << 5) - hash) + s.charAt(i);
        }
        return hash;
    }

    /**
     * Mulberry32 PRNG. Maintains mutable state via int array wrapper.
     * Produces identical output to TypeScript's seededRandom function.
     */
    static double mulberry32Next(int[] state) {
        state[0] += 0x6D2B79F5;
        int t = state[0];
        t = (t ^ (t >>> 15)) * (t | 1);
        t = (t + ((t ^ (t >>> 7)) * (t | 61))) ^ t;
        // unsigned right shift then convert to unsigned long for division
        long unsigned = Integer.toUnsignedLong(t ^ (t >>> 14));
        return unsigned / 4294967296.0;
    }

    private static DynValue randomVerb(DynValue[] args, VerbContext ctx) {
        // 1 string arg → seeded float in [0, 1)
        if (args.length == 1) {
            String seedStr = args[0].asString();
            if (seedStr != null) {
                int seed = stringToSeed(seedStr);
                int[] state = { seed };
                return DynValue.ofFloat(mulberry32Next(state));
            }
            // single numeric arg → random integer in [0, arg]
            Double maxVal = toDouble(args[0]);
            if (maxVal == null) return DynValue.ofNull();
            int range = (int) Math.floor(maxVal) + 1;
            return DynValue.ofInteger((long) Math.floor(ThreadLocalRandom.current().nextDouble() * range));
        }

        if (args.length < 2) {
            return DynValue.ofFloat(ThreadLocalRandom.current().nextDouble());
        }

        Double min = toDouble(args[0]);
        Double max = toDouble(args[1]);
        if (min == null || max == null) return DynValue.ofNull();
        if (min > max) return DynValue.ofNull();

        // 3rd arg = seed string
        if (args.length >= 3) {
            String seedStr = args[2].asString();
            if (seedStr == null) seedStr = args[2].toString();
            int seed = stringToSeed(seedStr);
            int[] state = { seed };
            double rnd = mulberry32Next(state);
            int iMin = (int) Math.floor(min);
            int iMax = (int) Math.floor(max);
            int range = iMax - iMin + 1;
            int value = iMin + (int) Math.floor(rnd * range);
            return DynValue.ofInteger(value);
        }

        // unseeded with min/max → integer
        int iMin = (int) Math.floor(min);
        int iMax = (int) Math.floor(max);
        int range = iMax - iMin + 1;
        return DynValue.ofInteger((long) (iMin + (int) Math.floor(ThreadLocalRandom.current().nextDouble() * range)));
    }

    private static DynValue minOf(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double result = null;
        for (DynValue arg : args) {
            List<DynValue> arr = arg.asArray();
            if (arr != null) {
                for (DynValue item : arr) {
                    Double val = toDouble(item);
                    if (val != null && (result == null || val < result)) result = val;
                }
            } else {
                Double val = toDouble(arg);
                if (val != null && (result == null || val < result)) result = val;
            }
        }
        if (result == null) return DynValue.ofNull();
        return numericResult(result);
    }

    private static DynValue maxOf(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double result = null;
        for (DynValue arg : args) {
            List<DynValue> arr = arg.asArray();
            if (arr != null) {
                for (DynValue item : arr) {
                    Double val = toDouble(item);
                    if (val != null && (result == null || val > result)) result = val;
                }
            } else {
                Double val = toDouble(arg);
                if (val != null && (result == null || val > result)) result = val;
            }
        }
        if (result == null) return DynValue.ofNull();
        return numericResult(result);
    }

    private static DynValue formatPercent(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        int decimals = 0;
        if (args.length >= 2) {
            Double d = toDouble(args[1]);
            if (d != null) decimals = (int) d.doubleValue();
        }
        double pct = val * 100.0;
        // Use AwayFromZero rounding to match .NET/JS toFixed behavior
        double factor = Math.pow(10, Math.max(0, decimals));
        double rounded = Math.round(pct * factor) / factor;
        return DynValue.ofString(String.format(Locale.US, "%." + decimals + "f%%", rounded));
    }

    private static DynValue parseInt(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        if (args[0].isNull()) return DynValue.ofNull();
        Long intVal = args[0].asInt64();
        if (intVal != null) return DynValue.ofInteger(intVal);
        Double dblVal = toDouble(args[0]);
        if (dblVal != null) return DynValue.ofInteger((long) dblVal.doubleValue());
        String s = args[0].asString();
        if (s != null) {
            s = s.trim();
            try {
                return DynValue.ofInteger(Long.parseLong(s));
            } catch (NumberFormatException e) {
                try {
                    return DynValue.ofInteger((long) Double.parseDouble(s));
                } catch (NumberFormatException e2) {
                    // fall through
                }
            }
        }
        return DynValue.ofNull();
    }

    private static DynValue safeDivide(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double numerator = toDouble(args[0]);
        Double denominator = toDouble(args[1]);
        if (numerator == null) return DynValue.ofNull();
        if (denominator == null || denominator == 0.0) {
            if (args.length >= 3) return args[2];
            return DynValue.ofNull();
        }
        return numericResult(numerator / denominator);
    }

    private static DynValue formatLocaleNumber(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        String locale = "en-US";
        int decimals = -1;
        if (args.length >= 2) {
            String locStr = args[1].asString();
            if (locStr != null) locale = locStr;
        }
        if (args.length >= 3) {
            Double d = toDouble(args[2]);
            if (d != null) decimals = (int) d.doubleValue();
        }
        try {
            Locale loc = Locale.forLanguageTag(locale.replace("_", "-"));
            NumberFormat nf = NumberFormat.getNumberInstance(loc);
            if (decimals >= 0) {
                nf.setMinimumFractionDigits(decimals);
                nf.setMaximumFractionDigits(decimals);
            }
            return DynValue.ofString(nf.format(val));
        } catch (Exception e) {
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
            if (decimals >= 0) {
                nf.setMinimumFractionDigits(decimals);
                nf.setMaximumFractionDigits(decimals);
            }
            return DynValue.ofString(nf.format(val));
        }
    }

    private static DynValue add(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double a = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (a == null || b == null) return DynValue.ofNull();
        return numericResult(a + b);
    }

    private static DynValue subtract(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double a = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (a == null || b == null) return DynValue.ofNull();
        return numericResult(a - b);
    }

    private static DynValue multiply(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double a = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (a == null || b == null) return DynValue.ofNull();
        return numericResult(a * b);
    }

    private static DynValue divide(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double a = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (a == null || b == null) return DynValue.ofNull();
        if (b == 0.0) return DynValue.ofNull();
        return DynValue.ofFloat(a / b);
    }

    private static DynValue abs(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.abs(val));
    }

    private static DynValue round(DynValue[] args, VerbContext ctx) {
        if (args.length < 1) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        int decimals = 0;
        if (args.length >= 2) {
            Double d = toDouble(args[1]);
            if (d != null) decimals = (int) d.doubleValue();
        }
        if (decimals < 0) decimals = 0;
        double factor = Math.pow(10, decimals);
        // AwayFromZero rounding: for positive use floor(x + 0.5), for negative use ceil(x - 0.5)
        double scaled = val * factor;
        double rounded;
        if (scaled >= 0) {
            rounded = Math.floor(scaled + 0.5) / factor;
        } else {
            rounded = Math.ceil(scaled - 0.5) / factor;
        }
        return numericResult(rounded);
    }

    private static DynValue mod(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double a = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (a == null || b == null) return DynValue.ofNull();
        if (b == 0.0) return DynValue.ofNull();
        return numericResult(a % b);
    }

    // ── Unit Conversion ──

    private static final java.util.Set<String> TEMP_UNITS = java.util.Set.of("C", "F", "K");

    private static final Map<String, Map<String, Double>> UNIT_FAMILIES = Map.ofEntries(
            Map.entry("mass", Map.of("kg", 1.0, "g", 0.001, "mg", 0.000001, "lb", 0.453592, "oz", 0.0283495, "ton", 907.185, "tonne", 1000.0)),
            Map.entry("length", Map.of("m", 1.0, "km", 1000.0, "cm", 0.01, "mm", 0.001, "mi", 1609.344, "ft", 0.3048, "in", 0.0254, "yd", 0.9144)),
            Map.entry("volume", Map.of("L", 1.0, "mL", 0.001, "gal", 3.78541, "qt", 0.946353, "pt", 0.473176, "cup", 0.236588, "floz", 0.0295735)),
            Map.entry("speed", Map.of("mps", 1.0, "kph", 0.277778, "mph", 0.44704)),
            Map.entry("area", Map.of("sqm", 1.0, "sqft", 0.092903, "sqkm", 1000000.0, "sqmi", 2589988.11, "acre", 4046.8564, "hectare", 10000.0)),
            Map.entry("data", Map.of("B", 1.0, "KB", 1024.0, "MB", 1048576.0, "GB", 1073741824.0, "TB", 1099511627776.0)),
            Map.entry("time", Map.of("ms", 0.001, "s", 1.0, "min", 60.0, "hr", 3600.0, "day", 86400.0))
    );

    private static double[] findUnitFamily(String unit) {
        for (var family : UNIT_FAMILIES.entrySet()) {
            Double factor = family.getValue().get(unit);
            if (factor != null) return new double[]{family.getKey().hashCode(), factor};
        }
        return null;
    }

    private static String findUnitFamilyName(String unit) {
        for (var family : UNIT_FAMILIES.entrySet()) {
            if (family.getValue().containsKey(unit)) return family.getKey();
        }
        return null;
    }

    private static DynValue convertUnit(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();

        Double value = toDouble(args[0]);
        if (value == null || !Double.isFinite(value)) return DynValue.ofNull();

        String fromUnit = args[1].asString();
        String toUnit = args[2].asString();
        if (fromUnit == null || toUnit == null) return DynValue.ofNull();

        // Handle temperature separately (formula-based)
        if (TEMP_UNITS.contains(fromUnit) && TEMP_UNITS.contains(toUnit)) {
            if (fromUnit.equals(toUnit)) return numericResult(value);

            // Convert to Celsius first
            double celsius = switch (fromUnit) {
                case "C" -> value;
                case "F" -> (value - 32) * 5.0 / 9.0;
                case "K" -> value - 273.15;
                default -> value;
            };

            // Convert from Celsius to target
            double result = switch (toUnit) {
                case "C" -> celsius;
                case "F" -> celsius * 9.0 / 5.0 + 32;
                case "K" -> celsius + 273.15;
                default -> celsius;
            };

            result = Math.round(result * 1000000.0) / 1000000.0;
            return numericResult(result);
        }

        // One is temp, other is not → incompatible
        if (TEMP_UNITS.contains(fromUnit) || TEMP_UNITS.contains(toUnit)) return DynValue.ofNull();

        // Look up families
        String fromFamily = findUnitFamilyName(fromUnit);
        String toFamily = findUnitFamilyName(toUnit);

        if (fromFamily == null || toFamily == null) return DynValue.ofNull();
        if (!fromFamily.equals(toFamily)) return DynValue.ofNull();

        if (fromUnit.equals(toUnit)) return numericResult(value);

        double fromFactor = UNIT_FAMILIES.get(fromFamily).get(fromUnit);
        double toFactor = UNIT_FAMILIES.get(toFamily).get(toUnit);

        double result = value * fromFactor / toFactor;
        double rounded = Math.round(result * 1000000.0) / 1000000.0;
        return numericResult(rounded);
    }
}
