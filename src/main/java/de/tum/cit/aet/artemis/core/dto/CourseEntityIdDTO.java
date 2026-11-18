package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A (course id, entity id) pair, used by bulk expected-set queries that resolve, for a set of courses in one query,
 * which entity ids of a type the database expects to be indexed. Returning the course id alongside the entity id lets
 * the caller bucket the flat result by course without issuing a query per course.
 *
 * @param courseId the id of the course the entity belongs to
 * @param entityId the id of the entity (exercise, lecture unit, ...)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseEntityIdDTO(long courseId, long entityId) {
}
