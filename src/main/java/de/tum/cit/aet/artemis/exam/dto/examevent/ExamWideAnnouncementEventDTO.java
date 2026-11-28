package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.ExamWideAnnouncementEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamWideAnnouncementEventDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdDate,
        String text) implements ExamLiveEventBaseDTO {

}
