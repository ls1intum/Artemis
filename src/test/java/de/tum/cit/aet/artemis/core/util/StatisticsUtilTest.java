package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class StatisticsUtilTest {

    @Test
    void testCalculateMean_withValues() {
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        double mean = StatisticsUtil.calculateMean(values);
        assertThat(mean).isCloseTo(30.0, within(0.001));
    }

    @Test
    void testCalculateMean_withEmptyList() {
        List<Double> values = Collections.emptyList();
        double mean = StatisticsUtil.calculateMean(values);
        assertThat(mean).isEqualTo(0.0);
    }

    @Test
    void testCalculateMean_withNull() {
        double mean = StatisticsUtil.calculateMean(null);
        assertThat(mean).isEqualTo(0.0);
    }

    @Test
    void testCalculateMean_withSingleValue() {
        List<Double> values = List.of(42.0);
        double mean = StatisticsUtil.calculateMean(values);
        assertThat(mean).isCloseTo(42.0, within(0.001));
    }

    @Test
    void testCalculateMedian_withOddNumberOfValues() {
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isCloseTo(30.0, within(0.001));
    }

    @Test
    void testCalculateMedian_withEvenNumberOfValues() {
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0);
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isCloseTo(25.0, within(0.001));
    }

    @Test
    void testCalculateMedian_withEmptyList() {
        List<Double> values = Collections.emptyList();
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isEqualTo(0.0);
    }

    @Test
    void testCalculateMedian_withNull() {
        double median = StatisticsUtil.calculateMedian(null);
        assertThat(median).isEqualTo(0.0);
    }

    @Test
    void testCalculateMedian_withSingleValue() {
        List<Double> values = List.of(42.0);
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isCloseTo(42.0, within(0.001));
    }

    @Test
    void testCalculateMedian_withUnsortedValues() {
        List<Double> values = List.of(50.0, 10.0, 40.0, 20.0, 30.0);
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isCloseTo(30.0, within(0.001));
    }

    @Test
    void testCalculateStandardDeviation_withValues() {
        // Population: 10, 20, 30, 40, 50
        // Mean: 30
        // Variance: ((10-30)^2 + (20-30)^2 + (30-30)^2 + (40-30)^2 + (50-30)^2) / 5 = 200
        // StdDev: sqrt(200) = 14.142...
        List<Double> values = List.of(10.0, 20.0, 30.0, 40.0, 50.0);
        double stdDev = StatisticsUtil.calculateStandardDeviation(values);
        assertThat(stdDev).isCloseTo(14.142, within(0.01));
    }

    @Test
    void testCalculateStandardDeviation_withEmptyList() {
        List<Double> values = Collections.emptyList();
        double stdDev = StatisticsUtil.calculateStandardDeviation(values);
        assertThat(stdDev).isEqualTo(0.0);
    }

    @Test
    void testCalculateStandardDeviation_withNull() {
        double stdDev = StatisticsUtil.calculateStandardDeviation(null);
        assertThat(stdDev).isEqualTo(0.0);
    }

    @Test
    void testCalculateStandardDeviation_withSingleValue() {
        List<Double> values = List.of(42.0);
        double stdDev = StatisticsUtil.calculateStandardDeviation(values);
        assertThat(stdDev).isEqualTo(0.0);
    }

    @Test
    void testCalculateStandardDeviation_withIdenticalValues() {
        List<Double> values = List.of(25.0, 25.0, 25.0, 25.0);
        double stdDev = StatisticsUtil.calculateStandardDeviation(values);
        assertThat(stdDev).isEqualTo(0.0);
    }

    @Test
    void testCalculateMean_withDecimalValues() {
        List<Double> values = List.of(1.5, 2.5, 3.5, 4.5);
        double mean = StatisticsUtil.calculateMean(values);
        assertThat(mean).isCloseTo(3.0, within(0.001));
    }

    @Test
    void testCalculateMedian_withTwoValues() {
        List<Double> values = List.of(10.0, 20.0);
        double median = StatisticsUtil.calculateMedian(values);
        assertThat(median).isCloseTo(15.0, within(0.001));
    }
}
