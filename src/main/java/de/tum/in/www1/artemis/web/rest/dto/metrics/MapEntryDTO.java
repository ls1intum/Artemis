package de.tum.in.www1.artemis.web.rest.dto.metrics;

/**
 * DTO for a map entry (key -> value).
 *
 * @param key   the key of the entry
 * @param value the value of the entry
 */
public record MapEntryDTO(long key, long value) {
}
