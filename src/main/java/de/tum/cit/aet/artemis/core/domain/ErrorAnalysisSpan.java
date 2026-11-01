package de.tum.cit.aet.artemis.core.domain;

import java.time.Duration;

/**
 * The time span that the error analysis looks at, counted back from now.
 */
public enum ErrorAnalysisSpan {

    DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30));

    private final Duration duration;

    ErrorAnalysisSpan(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }
}
