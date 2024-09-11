package de.tum.cit.aet.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.domain.exam.event.WorkingTimeUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkingTimeUpdateEventDTO(Long id, String createdBy, Instant createdDate, int newWorkingTime, int oldWorkingTime, boolean courseWide)
        implements ExamLiveEventBaseDTO {

}
