package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The rows of one origin that are queued in the Weaviate outbox.
 *
 * @param size            the number of queued rows, due or backing off
 * @param oldestCreatedAt the enqueue time of the oldest queued row, or {@code null} if no row is queued
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WeaviateOutboxBacklogDTO(long size, @Nullable ZonedDateTime oldestCreatedAt) {
}
