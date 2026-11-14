package de.tum.cit.aet.artemis.programming.exception;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import de.tum.cit.aet.artemis.programming.domain.BuildTimeWindow;

/**
 * Thrown if a push to a student repository is not built because it was made outside the build time window of the exercise.
 * The push itself is valid and stays in the repository, only the submission and the build are skipped.
 */
public class BuildOutsideTimeWindowException extends VersionControlException {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    private final transient BuildTimeWindow window;

    public BuildOutsideTimeWindowException(long participationId, BuildTimeWindow window) {
        super("Push to participation " + participationId + " will not be built, it was made outside the build time window " + window);
        this.window = window;
    }

    /**
     * A message for the person who pushed, naming the period in which pushes are built.
     *
     * @return the message to show in the output of the push
     */
    public String getUserMessage() {
        String period;
        if (window.start() != null && window.end() != null) {
            period = "builds run from " + format(window.start()) + " until " + format(window.end());
        }
        else if (window.start() != null) {
            period = "builds start on " + format(window.start());
        }
        else {
            period = "builds stop after " + format(window.end());
        }
        return "Your changes were saved, but they were not built or graded because they were pushed outside the build window of this exercise (" + period + ").";
    }

    private static String format(ZonedDateTime date) {
        return FORMATTER.format(date.withZoneSameInstant(ZoneOffset.UTC));
    }
}
