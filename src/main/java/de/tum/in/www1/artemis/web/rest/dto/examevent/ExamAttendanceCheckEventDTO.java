package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamAttendanceCheckEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamAttendanceCheckEventDTO(Long id, String createdBy, Instant createdDate, String text) implements ExamLiveEventDTO {

}
