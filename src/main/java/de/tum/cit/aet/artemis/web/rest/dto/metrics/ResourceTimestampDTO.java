package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing a timestamp associated to a resource.
 *
 * @param id        the id of the resource
 * @param timestamp the timestamp associated to the resource
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResourceTimestampDTO(long id, ZonedDateTime timestamp) {
}
