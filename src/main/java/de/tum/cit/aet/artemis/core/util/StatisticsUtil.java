package de.tum.cit.aet.artemis.core.util;

import java.util.Collection;
import java.util.List;

/**
 * Utility class for statistical calculations used in exports and reports.
 */
public final class StatisticsUtil {

    private StatisticsUtil() {
        // Utility class
    }

    /**
     * Calculates the arithmetic mean (average) of a collection of values.
     *
     * @param values the collection of values
     * @return the mean, or 0.0 if the collection is empty
     */
    public static double calculateMean(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * Calculates the median of a collection of values.
     * For an even number of values, returns the average of the two middle values.
     *
     * @param values the collection of values
     * @return the median, or 0.0 if the collection is empty
     */
    public static double calculateMedian(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
        else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Calculates the population standard deviation of a collection of values.
     *
     * @param values the collection of values
     * @return the standard deviation, or 0.0 if the collection is empty
     */
    public static double calculateStandardDeviation(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double mean = calculateMean(values);
        double sumSquaredDifferences = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum();
        return Math.sqrt(sumSquaredDifferences / values.size());
    }
}
