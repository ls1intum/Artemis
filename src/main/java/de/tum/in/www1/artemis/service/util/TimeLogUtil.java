package de.tum.in.www1.artemis.service.util;

public class TimeLogUtil {

    public static String formatDurationFrom(long timeNanoStart) {
        long durationInMicroSeconds = (System.nanoTime() - timeNanoStart) / 1000;
        if (durationInMicroSeconds > 1000) {
            double durationInMilliSeconds = durationInMicroSeconds / 1000.0;
            if (durationInMilliSeconds > 1000) {
                double durationInSeconds = durationInMilliSeconds / 1000.0;
                return roundOffTo2DecPlaces(durationInSeconds) + "s";
            }
            return roundOffTo2DecPlaces(durationInMilliSeconds) + "ms";
        }
        return durationInMicroSeconds + "µs";
    }

    private static String roundOffTo2DecPlaces(double val) {
        return String.format("%.2f", val);
    }
}
