package de.tum.cit.aet.artemis.core.dto.agent;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A lecture to create, in the shape the REST API takes it. The lecture module owns the DTO of the endpoint, which a tool in the core
 * module may not use, so this record carries the same fields.
 *
 * @param title       the title
 * @param description what the lecture is about
 * @param startDate   when it starts
 * @param endDate     when it ends
 * @param course      the course it belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentLectureDTO(String title, String description, ZonedDateTime startDate, ZonedDateTime endDate, AgentCourseRefDTO course) {
}
