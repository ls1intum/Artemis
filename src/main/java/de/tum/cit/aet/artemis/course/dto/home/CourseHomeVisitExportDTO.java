package de.tum.cit.aet.artemis.course.dto.home;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeVisitExportDTO(long courseId, Instant firstSeenAt, Instant lastSeenAt, long variantCursor) {
}
