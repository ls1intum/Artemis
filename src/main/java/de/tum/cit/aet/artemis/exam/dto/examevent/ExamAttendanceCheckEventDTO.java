package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.event.ExamAttendanceCheckEvent;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.ExamAttendanceCheckEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamAttendanceCheckEventDTO(Long id, String createdBy, Instant createdDate, String text) implements ExamLiveEventBaseDTO {

    public static ExamAttendanceCheckEventDTO of(ExamAttendanceCheckEvent examAttendanceCheckEvent) {
        return new ExamAttendanceCheckEventDTO(examAttendanceCheckEvent.getId(), examAttendanceCheckEvent.getCreatedBy(), examAttendanceCheckEvent.getCreatedDate(),
                examAttendanceCheckEvent.getTextContent());
    }
}
