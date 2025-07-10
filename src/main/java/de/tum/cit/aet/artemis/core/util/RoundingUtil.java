package de.tum.cit.aet.artemis.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import de.tum.cit.aet.artemis.core.domain.Course;

public class RoundingUtil {

    /**
     * Rounds a double to one decimal place after the decimal symbol
     * <p>
     * 1.26 -> 1.3
     * 1.25 -> 1.3
     * 1.24 -> 1.2
     *
     * @param number number to round to one decimal place
     * @return number rounded to one decimal place
     */
    public static double round(double number) {
        return roundToNDecimalPlaces(number, 1);
    }

    /**
     * Rounds a score to the specified amount of decimal places after the decimal symbol
     *
     * @param score  The score to round
     * @param course The course that specifies the amount of decimal places in the attribute {@link Course#getAccuracyOfScores()}
     * @return The rounded number
     */
    public static double roundScoreSpecifiedByCourseSettings(double score, Course course) {
        return roundToNDecimalPlaces(score, course.getAccuracyOfScores());
    }

    /**
     * Rounds a score to the specified amount of decimal places after the decimal symbol
     *
     * @param score            The score to round
     * @param accuracyOfScores The accuracy of scores from the course
     * @return The rounded number
     */
    public static double roundScoreSpecifiedByAccuracy(double score, int accuracyOfScores) {
        return roundToNDecimalPlaces(score, accuracyOfScores);
    }

    /**
     * Rounds a float to one decimal place after the comma
     * <p>
     * 1.26 -> 1.3
     * 1.25 -> 1.3
     * 1.24 -> 1.2
     *
     * @param number number to round to one decimal place
     * @return number rounded to one decimal place
     */
    public static float round(float number) {
        return (float) roundToNDecimalPlaces(number, 1);
    }

    /**
     * Rounds a number to the specified number of decimal places after the decimal symbol
     *
     * @param number                The number to round
     * @param numberOfDecimalPlaces The number of decimal places to round to
     * @return The rounded number
     */
    public static double roundToNDecimalPlaces(double number, int numberOfDecimalPlaces) {
        return new BigDecimal(String.valueOf(number)).setScale(numberOfDecimalPlaces, RoundingMode.HALF_UP).doubleValue();
    }

}
