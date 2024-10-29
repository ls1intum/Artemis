package de.tum.cit.aet.artemis.core.util;

import org.apache.commons.lang.time.DurationFormatUtils;

public class TimeLogUtil {

    /**
     * calculate the difference to the given start time in nano seconds and format it in a readable way
     *
     * @param timeNanoStart the time of the first measurement in nanoseconds
     * @return formatted string of the duration between now and timeNanoStart
     */
    public static String formatDurationFrom(long timeNanoStart) {
        long durationInMicroSeconds = (System.nanoTime() - timeNanoStart) / 1000;
        if (durationInMicroSeconds < 1000) {
            return durationInMicroSeconds + "µs";
        }
        double durationInMilliSeconds = durationInMicroSeconds / 1000.0;
        if (durationInMilliSeconds < 1000) {
            return roundOffTo2DecPlaces(durationInMilliSeconds) + "ms";
        }
        double durationInSeconds = durationInMilliSeconds / 1000.0;
        if (durationInSeconds < 60) {
            return roundOffTo2DecPlaces(durationInSeconds) + "sec";
        }
        double durationInMinutes = durationInSeconds / 60.0;
        if (durationInMinutes < 60) {
            return roundOffTo2DecPlaces(durationInMinutes) + "min";
        }
        double durationInHours = durationInMinutes / 60.0;
        return roundOffTo2DecPlaces(durationInHours) + "hours";
    }

    public static String formatDuration(long durationInSeconds) {
        if (durationInSeconds < 60) {
            return durationInSeconds + "s";
        }
        return DurationFormatUtils.formatDuration(durationInSeconds * 1000, "HH:mm:ss") + " (HH:mm:ss)";
    }

    private static String roundOffTo2DecPlaces(double val) {
        return String.format("%.2f", val);
    }
}
