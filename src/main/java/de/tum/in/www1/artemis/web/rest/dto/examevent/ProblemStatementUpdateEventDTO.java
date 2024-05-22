package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ProblemStatementUpdateEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemStatementUpdateEventDTO(Long id, String createdBy, Instant createdDate, String text, String problemStatement, long exerciseId, String exerciseName)
        implements ExamLiveEventDTO {

}
