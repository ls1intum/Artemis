package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.event.ExamRescheduledEvent;

/**
 * A DTO for the {@link ExamRescheduledEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamRescheduledEventDTO(Long id, String createdBy, Instant createdDate, ZonedDateTime newStartDate, ZonedDateTime newEndDate) implements ExamLiveEventBaseDTO {

}
