package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import org.jspecify.annotations.Nullable;

/**
 * One log record as Iris reports it from its in-memory ingestion buffer.
 *
 * @param timestamp  when it was logged, in epoch milliseconds
 * @param level      the level name, e.g. {@code DEBUG}
 * @param logger     the logger that emitted it
 * @param message    the formatted message
 * @param stackTrace the rendered traceback, or null when the record carried none
 */
public record PyrisLogEntryDTO(double timestamp, String level, String logger, String message, @Nullable String stackTrace) {
}
