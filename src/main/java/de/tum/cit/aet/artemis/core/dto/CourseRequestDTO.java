package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestDTO(Long id, String title, String shortName, String semester, ZonedDateTime startDate, ZonedDateTime endDate, boolean testCourse, String reason,
        CourseRequestStatus status, ZonedDateTime createdDate, ZonedDateTime processedDate, String decisionReason, UserDTO requester, Long createdCourseId,
        Integer instructorCourseCount) {
}
