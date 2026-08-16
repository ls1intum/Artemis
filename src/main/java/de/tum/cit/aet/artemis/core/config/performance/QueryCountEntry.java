package de.tum.cit.aet.artemis.core.config.performance;

/**
 * One normalized SQL template's contribution to a single HTTP request, as captured in
 * {@link EndpointTimingRecord#queries()}. Unlike {@link SlowQueryRecord}/{@link N1Suspect}, which
 * only ever record outliers (individually slow, or repeated past the N+1 threshold), this
 * captures every query template that ran during the request -- including perfectly ordinary
 * ones -- so a reader can see why an endpoint's total query count or DB-time ratio is high even
 * when no single query trips any global threshold.
 *
 * @param sql             Normalised SQL text (literals stripped, same form as {@link SlowQueryRecord#sql()}).
 * @param count           Number of times this template was executed within the request.
 * @param totalDurationMs Sum of this template's execution times within the request.
 */
public record QueryCountEntry(String sql, int count, long totalDurationMs) {
}
