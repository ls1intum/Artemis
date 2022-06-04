package de.tum.in.www1.artemis.service.util;

public class TimeLogUtil {

    /**
     * calculate the difference to the given start time in nano seconds and format it in a readable way
     *
     * @param timeNanoStart the time of the first measurement in nanoseconds
     * @return formatted string of the duration between now and timeNanoStart
     */
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
