package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ProblemStatementUpdateEvent} entity.
 */
public record ProblemStatementUpdateEventDTO(Long id, String createdBy, Instant createdDate, String text, String problemStatement, long exerciseId, String exerciseName)
        implements ExamLiveEventDTO {

}
