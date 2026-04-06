package foundation.odin.forms;

/**
 * ODIN Forms — Unit Conversion
 *
 * Converts between page units (inch, cm, mm, pt) and CSS pixels at 96 DPI.
 * Mirrors the TypeScript units.ts implementation exactly.
 */
public final class Units {

    private static final double DPI = 96.0;

    // Conversion factors: units -> pixels
    private static final double INCH_FACTOR = DPI;           // 1 inch = 96px
    private static final double CM_FACTOR   = DPI / 2.54;    // 1 cm  ≈ 37.795px
    private static final double MM_FACTOR   = DPI / 25.4;    // 1 mm  ≈ 3.7795px
    private static final double PT_FACTOR   = DPI / 72.0;    // 1 pt  ≈ 1.333px

    private Units() {
        throw new UnsupportedOperationException("Units is a static utility class");
    }

    /**
     * Convert a value from the given page unit to CSS pixels.
     *
     * @param value the measurement in {@code unit} units
     * @param unit  one of {@code "inch"}, {@code "cm"}, {@code "mm"}, {@code "pt"}
     * @return the equivalent value in pixels, rounded to 3 decimal places
     * @throws IllegalArgumentException if the unit is not recognised
     */
    public static double toPixels(double value, String unit) {
        double factor = conversionFactor(unit);
        return Math.round(value * factor * 1000.0) / 1000.0;
    }

    /**
     * Convert a pixel value back to the given page unit.
     *
     * @param px   the pixel value
     * @param unit one of {@code "inch"}, {@code "cm"}, {@code "mm"}, {@code "pt"}
     * @return the equivalent value in the requested unit, rounded to 3 decimal places
     * @throws IllegalArgumentException if the unit is not recognised
     */
    public static double fromPixels(double px, String unit) {
        double factor = conversionFactor(unit);
        return Math.round((px / factor) * 1000.0) / 1000.0;
    }

    private static double conversionFactor(String unit) {
        return switch (unit) {
            case "inch" -> INCH_FACTOR;
            case "cm"   -> CM_FACTOR;
            case "mm"   -> MM_FACTOR;
            case "pt"   -> PT_FACTOR;
            default     -> throw new IllegalArgumentException("Unknown unit: " + unit);
        };
    }
}
