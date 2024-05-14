package de.tum.in.www1.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

/**
 * A DTO representing a timestamp associated to a resource.
 *
 * @param id        the id of the resource
 * @param timestamp the timestamp associated to the resource
 */
public record ResourceTimestampDTO(long id, ZonedDateTime timestamp) {
}
