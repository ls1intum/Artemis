package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.exam.event.ExamAttendanceCheckEvent;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamAttendanceCheckEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamAttendanceCheckEventDTO(Long id, String createdBy, Instant createdDate, String text) implements ExamLiveEventBaseDTO {

    public static ExamAttendanceCheckEventDTO of(ExamAttendanceCheckEvent examAttendanceCheckEvent) {
        return new ExamAttendanceCheckEventDTO(examAttendanceCheckEvent.getId(), examAttendanceCheckEvent.getCreatedBy(), examAttendanceCheckEvent.getCreatedDate(),
                examAttendanceCheckEvent.getTextContent());
    }
}
