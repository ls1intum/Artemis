package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeSessionReviewDTO(long id, long courseId, ZonedDateTime startTime, ZonedDateTime completedAt, Double score, Double maxPoints,
        List<PracticeQuestionReviewDTO> questions) {

    public static PracticeSessionReviewDTO of(PracticeSession session, List<PracticeQuestionReviewDTO> questions) {
        return new PracticeSessionReviewDTO(session.getId(), session.getCourse().getId(), session.getStartTime(), session.getCompletedAt(), session.getScore(),
                session.getMaxPoints(), questions);
    }
}
