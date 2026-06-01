package foundation.odin.transform.verbs;

import foundation.odin.types.DynValue;
import foundation.odin.transform.TransformEngine.VerbContext;

import java.util.*;
import java.util.function.BiFunction;

public final class FinancialVerbs {

    private FinancialVerbs() {}

    public static void register(Map<String, BiFunction<DynValue[], VerbContext, DynValue>> reg) {
        // Math
        reg.put("log", FinancialVerbs::log);
        reg.put("ln", FinancialVerbs::ln);
        reg.put("log10", FinancialVerbs::log10);
        reg.put("exp", FinancialVerbs::exp);
        reg.put("pow", FinancialVerbs::pow);
        reg.put("sqrt", FinancialVerbs::sqrt);

        // Time value of money
        reg.put("compound", FinancialVerbs::compound);
        reg.put("discount", FinancialVerbs::discount);
        reg.put("pmt", FinancialVerbs::pmt);
        reg.put("fv", FinancialVerbs::fv);
        reg.put("pv", FinancialVerbs::pv);
        reg.put("rate", FinancialVerbs::rate);
        reg.put("nper", FinancialVerbs::nper);

        // Statistics
        reg.put("std", FinancialVerbs::std);
        reg.put("variance", FinancialVerbs::variance);
        reg.put("stdSample", FinancialVerbs::stdSample);
        reg.put("varianceSample", FinancialVerbs::varianceSample);
        reg.put("median", FinancialVerbs::median);
        reg.put("mode", FinancialVerbs::mode);
        reg.put("percentile", FinancialVerbs::percentile);
        reg.put("quantile", FinancialVerbs::quantile);
        reg.put("covariance", FinancialVerbs::covariance);
        reg.put("correlation", FinancialVerbs::correlation);
        reg.put("zscore", FinancialVerbs::zscore);

        // Other
        reg.put("clamp", FinancialVerbs::clamp);
        reg.put("interpolate", FinancialVerbs::interpolate);
        reg.put("weightedAvg", FinancialVerbs::weightedAvg);
        reg.put("npv", FinancialVerbs::npv);
        reg.put("irr", FinancialVerbs::irr);
        reg.put("depreciation", FinancialVerbs::depreciation);
        reg.put("movingAvg", FinancialVerbs::movingAvg);
    }

    // ── Helpers ──

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
        if (v == Math.floor(v) && Math.abs(v) < (double) Long.MAX_VALUE)
            return DynValue.ofInteger((long) v);
        return DynValue.ofFloat(v);
    }

    private static List<Double> extractDoubles(DynValue arg) {
        var items = arg.asArray();
        if (items == null) items = arg.extractArray();
        if (items == null) return null;
        var result = new ArrayList<Double>(items.size());
        for (DynValue item : items) {
            Double v = toDouble(item);
            if (v != null) result.add(v);
        }
        return result;
    }

    private static double populationVariance(List<Double> vals) {
        if (vals.isEmpty()) return 0;
        double mean = 0;
        for (double v : vals) mean += v;
        mean /= vals.size();
        double sum = 0;
        for (double v : vals) {
            double diff = v - mean;
            sum += diff * diff;
        }
        return sum / vals.size();
    }

    private static double sampleVariance(List<Double> vals) {
        if (vals.size() < 2) return 0;
        double mean = 0;
        for (double v : vals) mean += v;
        mean /= vals.size();
        double sum = 0;
        for (double v : vals) {
            double diff = v - mean;
            sum += diff * diff;
        }
        return sum / (vals.size() - 1);
    }

    // ── Math Verbs ──

    private static DynValue log(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        Double b = toDouble(args[1]);
        if (val == null || b == null) return DynValue.ofNull();
        return numericResult(Math.log(val) / Math.log(b));
    }

    private static DynValue ln(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.log(val));
    }

    private static DynValue log10(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.log10(val));
    }

    private static DynValue exp(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.exp(val));
    }

    private static DynValue pow(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double b = toDouble(args[0]);
        Double e = toDouble(args[1]);
        if (b == null || e == null) return DynValue.ofNull();
        return numericResult(Math.pow(b, e));
    }

    private static DynValue sqrt(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        if (val == null) return DynValue.ofNull();
        return numericResult(Math.sqrt(val));
    }

    // ── Time Value of Money ──

    private static DynValue compound(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double principal = toDouble(args[0]);
        Double rateVal = toDouble(args[1]);
        Double periods = toDouble(args[2]);
        if (principal == null || rateVal == null || periods == null) return DynValue.ofNull();
        return numericResult(principal * Math.pow(1.0 + rateVal, periods));
    }

    private static DynValue discount(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double fvVal = toDouble(args[0]);
        Double rateVal = toDouble(args[1]);
        Double periods = toDouble(args[2]);
        if (fvVal == null || rateVal == null || periods == null) return DynValue.ofNull();
        return numericResult(fvVal / Math.pow(1.0 + rateVal, periods));
    }

    private static DynValue pmt(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double principal = toDouble(args[0]);
        Double rateVal = toDouble(args[1]);
        Double nperVal = toDouble(args[2]);
        if (principal == null || rateVal == null || nperVal == null) return DynValue.ofNull();

        double p = principal, r = rateVal, n = nperVal;
        if (n <= 0) return DynValue.ofNull();
        if (r == 0.0) return numericResult(p / n);

        double factor = Math.pow(1.0 + r, n);
        double result = (p * r * factor) / (factor - 1.0);
        if (Double.isInfinite(result) || Double.isNaN(result)) return DynValue.ofNull();
        return numericResult(result);
    }

    private static DynValue fv(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double payment = toDouble(args[0]);
        Double rateVal = toDouble(args[1]);
        Double nperVal = toDouble(args[2]);
        if (payment == null || rateVal == null || nperVal == null) return DynValue.ofNull();

        double pmtVal = payment, r = rateVal, n = nperVal;
        if (r == 0.0) return numericResult(pmtVal * n);

        double factor = Math.pow(1.0 + r, n);
        double result = pmtVal * ((factor - 1.0) / r);
        if (Double.isInfinite(result) || Double.isNaN(result)) return DynValue.ofNull();
        return numericResult(result);
    }

    private static DynValue pv(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double payment = toDouble(args[0]);
        Double rateVal = toDouble(args[1]);
        Double nperVal = toDouble(args[2]);
        if (payment == null || rateVal == null || nperVal == null) return DynValue.ofNull();

        double pmtVal = payment, r = rateVal, n = nperVal;
        if (r == 0.0) return numericResult(pmtVal * n);

        double factor = Math.pow(1.0 + r, -n);
        double result = pmtVal * ((1.0 - factor) / r);
        if (Double.isInfinite(result) || Double.isNaN(result)) return DynValue.ofNull();
        return numericResult(result);
    }

    private static DynValue rate(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double nperVal = toDouble(args[0]);
        Double pmtVal = toDouble(args[1]);
        Double pvVal = toDouble(args[2]);
        Double fvOpt = args.length > 3 ? toDouble(args[3]) : 0.0;
        if (nperVal == null || pmtVal == null || pvVal == null) return DynValue.ofNull();

        double n = nperVal, m = pmtVal, p = pvVal, f = fvOpt != null ? fvOpt : 0.0;

        double guess = 0.1;
        for (int i = 0; i < 100; i++) {
            double g1 = Math.pow(1.0 + guess, n);
            double fVal = p * g1 + m * (g1 - 1.0) / guess + f;
            double fDeriv = p * n * Math.pow(1.0 + guess, n - 1.0)
                    + m * (n * Math.pow(1.0 + guess, n - 1.0) * guess - (g1 - 1.0)) / (guess * guess);

            if (fDeriv == 0.0) break;
            double next = guess - fVal / fDeriv;
            if (Math.abs(next - guess) < 1e-10) {
                guess = next;
                break;
            }
            guess = next;
        }

        return numericResult(guess);
    }

    private static DynValue nper(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double rateVal = toDouble(args[0]);
        Double pmtVal = toDouble(args[1]);
        Double pvVal = toDouble(args[2]);
        if (rateVal == null || pmtVal == null || pvVal == null) return DynValue.ofNull();

        double r = rateVal, m = pmtVal, p = pvVal;

        if (r == 0.0) {
            if (m == 0.0) return DynValue.ofNull();
            return numericResult(-p / m);
        }

        double numerator = Math.log(m / (m + r * p));
        double denominator = Math.log(1.0 + r);
        if (denominator == 0.0) return DynValue.ofNull();
        return numericResult(numerator / denominator);
    }

    // ── Statistics ──

    private static DynValue std(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.isEmpty()) return DynValue.ofNull();
        return numericResult(Math.sqrt(populationVariance(vals)));
    }

    private static DynValue variance(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.isEmpty()) return DynValue.ofNull();
        return numericResult(populationVariance(vals));
    }

    private static DynValue stdSample(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.size() < 2) return DynValue.ofNull();
        return numericResult(Math.sqrt(sampleVariance(vals)));
    }

    private static DynValue varianceSample(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.size() < 2) return DynValue.ofNull();
        return numericResult(sampleVariance(vals));
    }

    private static DynValue median(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.isEmpty()) return DynValue.ofNull();
        Collections.sort(vals);
        int mid = vals.size() / 2;
        if (vals.size() % 2 == 0)
            return numericResult((vals.get(mid - 1) + vals.get(mid)) / 2.0);
        return numericResult(vals.get(mid));
    }

    private static DynValue mode(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        if (vals == null || vals.isEmpty()) return DynValue.ofNull();

        var counts = new LinkedHashMap<Double, Integer>();
        for (double v : vals) counts.merge(v, 1, Integer::sum);

        double modeVal = vals.get(0);
        int maxCount = 0;
        for (var entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                modeVal = entry.getKey();
            }
        }

        return numericResult(modeVal);
    }

    private static DynValue percentile(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        Double p = toDouble(args[1]);
        if (vals == null || vals.isEmpty() || p == null) return DynValue.ofNull();

        Collections.sort(vals);
        double pct = p;
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;

        double rankVal = (pct / 100.0) * (vals.size() - 1);
        int lower = (int) Math.floor(rankVal);
        int upper = (int) Math.ceil(rankVal);

        if (lower == upper || upper >= vals.size())
            return numericResult(vals.get(lower));

        double frac = rankVal - lower;
        return numericResult(vals.get(lower) + frac * (vals.get(upper) - vals.get(lower)));
    }

    private static DynValue quantile(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double q = toDouble(args[1]);
        if (q == null) return DynValue.ofNull();

        var pctArgs = new DynValue[] { args[0], DynValue.ofFloat(q * 100.0) };
        return percentile(pctArgs, ctx);
    }

    private static DynValue covariance(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        List<Double> xs = extractDoubles(args[0]);
        List<Double> ys = extractDoubles(args[1]);
        if (xs == null || ys == null) return DynValue.ofNull();

        int n = Math.min(xs.size(), ys.size());
        if (n == 0) return DynValue.ofNull();

        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) { meanX += xs.get(i); meanY += ys.get(i); }
        meanX /= n;
        meanY /= n;

        double cov = 0;
        for (int i = 0; i < n; i++)
            cov += (xs.get(i) - meanX) * (ys.get(i) - meanY);
        cov /= n;

        return numericResult(cov);
    }

    private static DynValue correlation(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        List<Double> xs = extractDoubles(args[0]);
        List<Double> ys = extractDoubles(args[1]);
        if (xs == null || ys == null) return DynValue.ofNull();

        int n = Math.min(xs.size(), ys.size());
        if (n == 0) return DynValue.ofNull();

        double meanX = 0, meanY = 0;
        for (int i = 0; i < n; i++) { meanX += xs.get(i); meanY += ys.get(i); }
        meanX /= n;
        meanY /= n;

        double cov = 0, varX = 0, varY = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs.get(i) - meanX;
            double dy = ys.get(i) - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        double denom = Math.sqrt(varX * varY);
        if (denom == 0.0) return DynValue.ofNull();

        return numericResult(cov / denom);
    }

    private static DynValue zscore(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double value = toDouble(args[0]);
        List<Double> arr = extractDoubles(args[1]);
        if (value == null || arr == null || arr.isEmpty()) return DynValue.ofNull();

        double mean = 0;
        for (double v : arr) mean += v;
        mean /= arr.size();
        double sumSq = 0;
        for (double v : arr) sumSq += (v - mean) * (v - mean);
        double stdDev = Math.sqrt(sumSq / arr.size());
        if (stdDev == 0.0) return DynValue.ofNull();

        double z = (value - mean) / stdDev;
        if (Double.isInfinite(z) || Double.isNaN(z)) return DynValue.ofNull();
        return numericResult(z);
    }

    // ── Other ──

    private static DynValue clamp(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double val = toDouble(args[0]);
        Double lo = toDouble(args[1]);
        Double hi = toDouble(args[2]);
        if (val == null || lo == null || hi == null) return DynValue.ofNull();
        double clamped = val;
        if (clamped < lo) clamped = lo;
        if (clamped > hi) clamped = hi;
        return numericResult(clamped);
    }

    private static DynValue interpolate(DynValue[] args, VerbContext ctx) {
        if (args.length < 5) return DynValue.ofNull();
        Double x = toDouble(args[0]);
        Double x1 = toDouble(args[1]);
        Double y1 = toDouble(args[2]);
        Double x2 = toDouble(args[3]);
        Double y2 = toDouble(args[4]);
        if (x == null || x1 == null || y1 == null || x2 == null || y2 == null) return DynValue.ofNull();
        if (x2.doubleValue() == x1.doubleValue()) return numericResult(y1);
        return numericResult(y1 + ((x - x1) * (y2 - y1)) / (x2 - x1));
    }

    private static DynValue weightedAvg(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        List<Double> vals = extractDoubles(args[0]);
        List<Double> weights = extractDoubles(args[1]);
        if (vals == null || weights == null) return DynValue.ofNull();

        int n = Math.min(vals.size(), weights.size());
        if (n == 0) return DynValue.ofNull();

        double sumProduct = 0, sumWeights = 0;
        for (int i = 0; i < n; i++) {
            sumProduct += vals.get(i) * weights.get(i);
            sumWeights += weights.get(i);
        }

        if (sumWeights == 0.0) return DynValue.ofNull();
        return numericResult(sumProduct / sumWeights);
    }

    private static DynValue npv(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();
        Double rateVal = toDouble(args[0]);
        List<Double> cashflows = extractDoubles(args[1]);
        if (rateVal == null || cashflows == null) return DynValue.ofNull();

        double r = rateVal;
        double npvVal = 0;
        for (int t = 0; t < cashflows.size(); t++) {
            npvVal += cashflows.get(t) / Math.pow(1.0 + r, t);
        }

        if (Double.isInfinite(npvVal) || Double.isNaN(npvVal)) return DynValue.ofNull();
        return numericResult(npvVal);
    }

    private static DynValue irr(DynValue[] args, VerbContext ctx) {
        if (args.length == 0) return DynValue.ofNull();
        List<Double> cashflows = extractDoubles(args[0]);
        if (cashflows == null || cashflows.isEmpty()) return DynValue.ofNull();

        double guess = args.length > 1 ? (toDouble(args[1]) != null ? toDouble(args[1]) : 0.1) : 0.1;

        for (int iter = 0; iter < 200; iter++) {
            double npvVal = 0;
            double dnpv = 0;
            for (int i = 0; i < cashflows.size(); i++) {
                double power = Math.pow(1.0 + guess, i);
                if (power == 0.0) continue;
                npvVal += cashflows.get(i) / power;
                if (i > 0)
                    dnpv -= i * cashflows.get(i) / Math.pow(1.0 + guess, i + 1);
            }

            if (dnpv == 0.0) break;
            double next = guess - npvVal / dnpv;
            if (Math.abs(next - guess) < 1e-10) {
                guess = next;
                break;
            }
            guess = next;
        }

        return numericResult(guess);
    }

    private static DynValue depreciation(DynValue[] args, VerbContext ctx) {
        if (args.length < 3) return DynValue.ofNull();
        Double cost = toDouble(args[0]);
        Double salvage = toDouble(args[1]);
        Double life = toDouble(args[2]);
        if (cost == null || salvage == null || life == null) return DynValue.ofNull();
        if (life == 0.0) return DynValue.ofNull();
        return numericResult((cost - salvage) / life);
    }

    // ── movingAvg ──

    private static DynValue movingAvg(DynValue[] args, VerbContext ctx) {
        if (args.length < 2) return DynValue.ofNull();

        var arrVal = args[0].asArray();
        if (arrVal == null) {
            arrVal = args[0].extractArray();
            if (arrVal == null) return DynValue.ofArray(new ArrayList<>());
        }
        if (arrVal.isEmpty()) return DynValue.ofArray(new ArrayList<>());

        Double windowD = toDouble(args[1]);
        if (windowD == null) return DynValue.ofNull();
        int windowSize = (int) Math.floor(windowD);
        if (windowSize < 1) return DynValue.ofNull();

        // Extract numeric values (non-numeric treated as 0)
        double[] values = new double[arrVal.size()];
        for (int i = 0; i < arrVal.size(); i++) {
            Double v = toDouble(arrVal.get(i));
            values[i] = v != null ? v : 0.0;
        }

        var result = new ArrayList<DynValue>();
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - windowSize + 1);
            double sum = 0;
            for (int j = start; j <= i; j++) {
                sum += values[j];
            }
            int count = i - start + 1;
            result.add(numericResult(sum / count));
        }

        return DynValue.ofArray(result);
    }
}
