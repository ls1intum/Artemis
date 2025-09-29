package de.tum.cit.aet.artemis.core.util;

public class TimeLogUtil {

    /**
     * calculate the difference to the given start time in nanoseconds and format it in a readable way
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

        /*
         * Minutes and hours need a special treatment to prevent formats like this:
         * '1.58min': Is this supposed to mean 1 min and 58 seconds or 1 min and 35 seconds?
         * '1.94hours': Here it would be obvious that something is off, but how long is that really?
         * This happens because there's not 100 seconds in a minute and also not 100 hours in a day.
         */
        int durationInMinutes = (int) (durationInSeconds / 60.0);
        if (durationInMinutes < 60) {
            return durationInMinutes + ":" + padTo2Digits((int) durationInSeconds % 60) + "min";
        }

        int durationInHours = durationInMinutes / 60;
        return durationInHours + ":" + padTo2Digits(durationInMinutes % 60) + "hours";
    }

    private static String roundOffTo2DecPlaces(double val) {
        return String.format("%.2f", val);
    }

    private static String padTo2Digits(int val) {
        return String.format("%02d", val);
    }
}
