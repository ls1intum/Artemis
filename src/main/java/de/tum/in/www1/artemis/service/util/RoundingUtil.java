package de.tum.in.www1.artemis.service.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RoundingUtil {

    /**
     * Rounds a double to one decimal place after the comma
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

    public static double roundToNDecimalPlaces(double number, int numberOfDecimalPlaces) {
        return new BigDecimal(String.valueOf(number)).setScale(numberOfDecimalPlaces, RoundingMode.HALF_UP).doubleValue();
    }

}
