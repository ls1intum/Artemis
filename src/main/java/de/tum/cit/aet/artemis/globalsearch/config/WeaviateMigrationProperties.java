package de.tum.cit.aet.artemis.globalsearch.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the global search index check that runs in the background on the scheduling node at startup (see
 * {@code SearchableEntityIndexRebuildService}). The outbox dispatcher runs the check before its first write and writes
 * nothing until the check has succeeded.
 * <p>
 * The keys keep the prefix {@code artemis.weaviate.migration} so existing configurations stay valid.
 *
 * @param initialDelaySeconds delay after the start of the scheduling node before the first check, so a rebuild does
 *                                not compete with application startup; 0 checks at the first drain.
 * @param maxAttempts         failed attempts that log a warning. An attempt fails when Weaviate is unavailable; the
 *                                check keeps retrying after this number too, logging an error each time, so the queued
 *                                index writes are applied as soon as Weaviate is back, without a restart.
 * @param retryDelaySeconds   delay between attempts.
 */
@Validated
@ConfigurationProperties(prefix = "artemis.weaviate.migration", ignoreUnknownFields = false)
public record WeaviateMigrationProperties(@DefaultValue("30") @PositiveOrZero long initialDelaySeconds, @DefaultValue("5") @Positive int maxAttempts,
        @DefaultValue("120") @Positive long retryDelaySeconds) {
}
