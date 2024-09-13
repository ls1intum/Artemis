package de.tum.cit.aet.artemis.exam.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.ExamWideAnnouncementEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamWideAnnouncementEventDTO(Long id, String createdBy, Instant createdDate, String text) implements ExamLiveEventBaseDTO {

}
