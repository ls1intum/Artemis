package de.tum.cit.aet.artemis.core.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One captured ingestion log record, from either service, for the admin ingestion dashboard.
 * <p>
 * Artemis and Iris each keep their own bounded in-memory buffer; this is the shape both are read as, so the
 * dashboard can merge them into one time-ordered story rather than asking a reader to correlate two lists.
 *
 * @param source     which service produced the record, {@code ARTEMIS} or {@code IRIS}
 * @param occurredAt when it was logged
 * @param level      the level name, e.g. {@code DEBUG}
 * @param logger     the logger that emitted it
 * @param message    the formatted message
 * @param stackTrace the rendered throwable, or null when the record carried none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionLogEntryDTO(String source, Instant occurredAt, String level, String logger, String message, @Nullable String stackTrace) {

    public static final String SOURCE_ARTEMIS = "ARTEMIS";

    public static final String SOURCE_IRIS = "IRIS";
}
