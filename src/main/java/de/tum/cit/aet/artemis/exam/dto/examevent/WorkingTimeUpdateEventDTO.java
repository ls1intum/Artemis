package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.WorkingTimeUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record WorkingTimeUpdateEventDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdDate,
        int newWorkingTime, int oldWorkingTime, boolean courseWide, Instant newStartDate, Instant newEndDate) implements ExamLiveEventBaseDTO {

}
