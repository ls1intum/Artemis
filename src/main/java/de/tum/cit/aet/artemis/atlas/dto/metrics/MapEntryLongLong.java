package de.tum.cit.aet.artemis.atlas.dto.metrics;

/**
 * DTO for a map entry (key : long -> value : long).
 *
 * @param key   the key of the entry
 * @param value the value of the entry
 */
public record MapEntryLongLong(long key, long value) {
}
