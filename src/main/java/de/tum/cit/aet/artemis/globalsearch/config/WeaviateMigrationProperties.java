package de.tum.cit.aet.artemis.globalsearch.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the one-off Weaviate schema migration that runs in the background on the scheduling node at startup.
 * <p>
 * These previously sat under {@code artemis.weaviate.outbox}, which was a misnomer: schema migration has nothing
 * to do with the outbox. They were never released under the old keys.
 *
 * @param initialDelaySeconds delay before the first attempt, so the migration does not compete with application
 *                                startup. It runs on a background thread either way, so this only affects when
 *                                the work begins.
 * @param maxAttempts         bounded in-process retries. An attempt can fail when the embedding backend is cold
 *                                or briefly unavailable, and retrying in process lets it self-heal rather than
 *                                waiting for the next restart of the scheduling node.
 * @param retryDelaySeconds   delay between attempts.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.weaviate.migration", ignoreUnknownFields = false)
public record WeaviateMigrationProperties(@DefaultValue("30") @Positive long initialDelaySeconds, @DefaultValue("5") @Positive int maxAttempts,
        @DefaultValue("120") @Positive long retryDelaySeconds) {
}
