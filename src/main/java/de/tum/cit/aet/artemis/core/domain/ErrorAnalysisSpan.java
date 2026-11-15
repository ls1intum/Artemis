package de.tum.cit.aet.artemis.core.domain;

import java.time.Duration;

/**
 * The time span that the error analysis looks at.
 * <p>
 * {@link #RELEASE} looks at the errors of the release that is running, however long ago it was deployed and without a limit in
 * time, which the other values have: they count back from now. A restart of the same release is still the same release.
 */
public enum ErrorAnalysisSpan {

    RELEASE(null), DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30));

    private final Duration duration;

    ErrorAnalysisSpan(Duration duration) {
        this.duration = duration;
    }

    /**
     * How far back from now the span reaches.
     *
     * @return the length of the span, or {@code null} for {@link #RELEASE}, which is bounded by the release instead
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Whether the span counts back from now.
     *
     * @return {@code false} for {@link #RELEASE}
     */
    public boolean isBoundedByTime() {
        return duration != null;
    }
}
