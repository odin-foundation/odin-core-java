package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;

public final class GeoVerbs {

    private GeoVerbs() {}

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double EARTH_RADIUS_MILES = 3958.8;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double RAD_TO_DEG = 180.0 / Math.PI;

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        reg.put("distance", GeoVerbs::distance);
        reg.put("inBoundingBox", GeoVerbs::inBoundingBox);
        reg.put("toRadians", GeoVerbs::toRadians);
        reg.put("toDegrees", GeoVerbs::toDegrees);
        reg.put("bearing", GeoVerbs::bearing);
        reg.put("midpoint", GeoVerbs::midpoint);
    }

    private static Double toDouble(DynValue v) {
        if (v.isNull()) return null;
        Double d = v.asDouble();
        if (d != null) return d;
        String s = v.asString();
        if (s != null) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static DynValue numericResult(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return DynValue.ofNull();
        return DynValue.ofFloat(v);
    }

    private static DynValue distance(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofNull();
        Double lat1 = toDouble(args[0]), lon1 = toDouble(args[1]);
        Double lat2 = toDouble(args[2]), lon2 = toDouble(args[3]);
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return DynValue.ofNull();

        // Determine unit (default: km)
        String unit = "km";
        if (args.length >= 5) {
            String u = args[4].asString();
            if (u != null) unit = u.toLowerCase();
        }

        double radius;
        switch (unit) {
            case "km":
                radius = EARTH_RADIUS_KM;
                break;
            case "mi":
            case "miles":
                radius = EARTH_RADIUS_MILES;
                break;
            default:
                if (ctx != null) ctx.addError(foundation.odin.types.OdinErrors.incompatibleConversionError(
                        "distance", "unknown unit '" + unit + "' (expected 'km', 'mi', or 'miles')"));
                return DynValue.ofNull();
        }

        double lat1Rad = lat1 * DEG_TO_RAD;
        double lat2Rad = lat2 * DEG_TO_RAD;
        double dLat = (lat2 - lat1) * DEG_TO_RAD;
        double dLon = (lon2 - lon1) * DEG_TO_RAD;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return numericResult(radius * c);
    }

    private static DynValue inBoundingBox(DynValue[] args, VerbContext ctx) {
        if (args.length < 6) return DynValue.ofNull();
        Double lat = toDouble(args[0]), lon = toDouble(args[1]);
        Double minLat = toDouble(args[2]), minLon = toDouble(args[3]);
        Double maxLat = toDouble(args[4]), maxLon = toDouble(args[5]);
        if (lat == null || lon == null || minLat == null || minLon == null || maxLat == null || maxLon == null)
            return DynValue.ofNull();
        boolean inside = lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        return DynValue.ofBool(inside);
    }

    private static DynValue toRadians(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double deg = toDouble(args[0]);
        if (deg == null) return DynValue.ofNull();
        return numericResult(deg * DEG_TO_RAD);
    }

    private static DynValue toDegrees(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double rad = toDouble(args[0]);
        if (rad == null) return DynValue.ofNull();
        return numericResult(rad * RAD_TO_DEG);
    }

    private static DynValue bearing(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofNull();
        Double lat1 = toDouble(args[0]), lon1 = toDouble(args[1]);
        Double lat2 = toDouble(args[2]), lon2 = toDouble(args[3]);
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return DynValue.ofNull();

        double lat1Rad = lat1 * DEG_TO_RAD;
        double lat2Rad = lat2 * DEG_TO_RAD;
        double dLon = (lon2 - lon1) * DEG_TO_RAD;

        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        double b = Math.atan2(y, x) * RAD_TO_DEG;
        b = (b + 360.0) % 360.0;
        return numericResult(b);
    }

    private static DynValue midpoint(DynValue[] args, VerbContext ctx) {
        if (args.length < 4) return DynValue.ofNull();
        Double lat1 = toDouble(args[0]), lon1 = toDouble(args[1]);
        Double lat2 = toDouble(args[2]), lon2 = toDouble(args[3]);
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return DynValue.ofNull();

        double lat1Rad = lat1 * DEG_TO_RAD;
        double lon1Rad = lon1 * DEG_TO_RAD;
        double lat2Rad = lat2 * DEG_TO_RAD;
        double dLon = (lon2 - lon1) * DEG_TO_RAD;

        double bx = Math.cos(lat2Rad) * Math.cos(dLon);
        double by = Math.cos(lat2Rad) * Math.sin(dLon);

        double midLat = Math.atan2(
                Math.sin(lat1Rad) + Math.sin(lat2Rad),
                Math.sqrt((Math.cos(lat1Rad) + bx) * (Math.cos(lat1Rad) + bx) + by * by)
        );
        double midLon = lon1Rad + Math.atan2(by, Math.cos(lat1Rad) + bx);

        var entries = new ArrayList<Map.Entry<String, DynValue>>();
        entries.add(Map.entry("lat", DynValue.ofFloat(midLat * RAD_TO_DEG)));
        entries.add(Map.entry("lon", DynValue.ofFloat(midLon * RAD_TO_DEG)));
        return DynValue.ofObject(entries);
    }
}
