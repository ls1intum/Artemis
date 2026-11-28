package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A reference to a course by its id, as the REST API takes it.
 *
 * @param id the id of the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentCourseRefDTO(Long id) {
}
