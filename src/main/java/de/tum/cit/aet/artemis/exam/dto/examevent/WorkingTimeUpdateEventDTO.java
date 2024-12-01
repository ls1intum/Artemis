package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.WorkingTimeUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkingTimeUpdateEventDTO(Long id, String createdBy, Instant createdDate, int newWorkingTime, int oldWorkingTime, boolean courseWide)
        implements ExamLiveEventBaseDTO {

}
