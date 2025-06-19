package de.tum.cit.aet.artemis.atlas.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a timestamp associated to a resource.
 *
 * @param exerciseId      the id of the resource
 * @param timestamp       the timestamp associated to the resource
 * @param participationId the id of the participation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResourceTimestampDTO(long exerciseId, ZonedDateTime timestamp, long participationId) {
}
