package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeSessionSummaryDTO(long id, long courseId, ZonedDateTime startTime, ZonedDateTime completedAt, Double score, Double maxPoints, boolean completed) {

    public static PracticeSessionSummaryDTO of(PracticeSession session) {
        return new PracticeSessionSummaryDTO(session.getId(), session.getCourse().getId(), session.getStartTime(), session.getCompletedAt(), session.getScore(),
                session.getMaxPoints(), session.getCompletedAt() != null);
    }
}
