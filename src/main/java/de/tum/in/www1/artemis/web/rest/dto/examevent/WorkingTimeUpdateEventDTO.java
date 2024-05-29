package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.WorkingTimeUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkingTimeUpdateEventDTO(Long id, String createdBy, Instant createdDate, int newWorkingTime, int oldWorkingTime, boolean courseWide)
        implements ExamLiveEventBaseDTO {

}
