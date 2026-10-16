package de.tum.cit.aet.artemis.globalsearch.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the outbox dispatcher, which drains queued metadata writes to Weaviate on the scheduling node.
 * <p>
 * Freshness is driven by an after-commit nudge rather than by polling, so {@code drainIntervalSeconds} is only
 * a safety net: it covers enqueues made on other nodes and any nudge that was missed.
 *
 * @param drainIntervalSeconds cadence of the safety-net drain tick. Read directly by the {@code @Scheduled}
 *                                 annotation, which cannot resolve a bound record, so it is declared here only
 *                                 so that strict binding accepts it and it appears in one place.
 * @param batchSize            outbox rows read per drain batch. A drain keeps reading batches until one comes
 *                                 back smaller than this, so a burst larger than one batch still drains fully.
 * @param baseBackoffSeconds   delay before the first retry of a failed write, doubling on each further attempt.
 * @param maxBackoffSeconds    cap on the retry backoff, so a sustained outage settles into a steady retry rate
 *                                 instead of growing without bound.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.weaviate.outbox", ignoreUnknownFields = false)
public record WeaviateOutboxProperties(@DefaultValue("5") @Positive long drainIntervalSeconds, @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("10") @Positive long baseBackoffSeconds, @DefaultValue("300") @Positive long maxBackoffSeconds) {
}
