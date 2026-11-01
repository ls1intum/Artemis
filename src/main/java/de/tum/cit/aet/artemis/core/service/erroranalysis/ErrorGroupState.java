package de.tum.cit.aet.artemis.core.service.erroranalysis;

import java.io.Serializable;
import java.time.Instant;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;

/**
 * What the cluster knows about a group of errors apart from its counts, which are stored per hour.
 * <p>
 * The record is a value of a distributed map, so it is compared by its encoded bytes on some providers. It holds only a string,
 * two enums and two numbers, which encode the same way every time.
 *
 * @param location        where the errors happened
 * @param severity        how serious they are
 * @param pattern         what they have in common: the message with numbers, identifiers and email addresses replaced by placeholders
 * @param firstSeenMillis when the first of them happened, in milliseconds since the epoch
 * @param lastSeenMillis  when the last of them happened
 */
public record ErrorGroupState(ErrorLocation location, ErrorSeverity severity, String pattern, long firstSeenMillis, long lastSeenMillis) implements Serializable {

    /**
     * Combines what two nodes saw of the same group: the earlier first and the later last time.
     *
     * @param other what another node saw of the same group
     * @return the combined state
     */
    ErrorGroupState mergedWith(ErrorGroupState other) {
        return new ErrorGroupState(location, severity, pattern, Math.min(firstSeenMillis, other.firstSeenMillis), Math.max(lastSeenMillis, other.lastSeenMillis));
    }

    ErrorGroupDTO toDto(long count) {
        return new ErrorGroupDTO(location, severity, pattern, count, Instant.ofEpochMilli(firstSeenMillis), Instant.ofEpochMilli(lastSeenMillis));
    }
}
