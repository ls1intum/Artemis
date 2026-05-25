package de.tum.cit.aet.artemis.course.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseRequestStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestDTO(Long id, String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason,
        CourseRequestStatus status, ZonedDateTime createdDate, ZonedDateTime processedDate, String decisionReason, CourseRequestRequesterDTO requester, Long createdCourseId,
        Integer instructorCourseCount) {
}
