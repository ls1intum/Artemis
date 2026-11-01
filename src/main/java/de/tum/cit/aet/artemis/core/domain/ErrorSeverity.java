package de.tum.cit.aet.artemis.core.domain;

/**
 * How serious an error is, from the least to the most serious. The order of the values is the order of the severity.
 * <p>
 * A client reports its errors with one of them. {@link #INFO} is a hint that is logged but not part of the error analysis,
 * {@link #WARN} is a problem the application recovers from and {@link #ERROR} is an error. {@link #CRITICAL} is an error
 * that stops the application from working at all, such as a client that cannot start. An error that the server logs is a
 * {@link #WARN} or an {@link #ERROR}. {@code INFO} is logged as an info entry, {@code WARN} as a warning, and {@code ERROR}
 * and {@code CRITICAL} as an error.
 */
public enum ErrorSeverity {

    INFO, WARN, ERROR, CRITICAL;

    /**
     * Whether this severity is at least as serious as another one.
     *
     * @param other the severity to compare with
     * @return {@code true} if this severity is {@code other} or more serious
     */
    public boolean isAtLeast(ErrorSeverity other) {
        return compareTo(other) >= 0;
    }
}
