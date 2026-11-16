package de.tum.cit.aet.artemis.atlas.dto.practice;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.practice.PracticeSession;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithoutSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PracticeSessionDTO(long id, long courseId, ZonedDateTime startTime, ZonedDateTime completedAt, Double score, Double maxPoints,
        List<QuizQuestionWithoutSolutionDTO> questions) {

    public static PracticeSessionDTO of(PracticeSession session, List<QuizQuestionWithoutSolutionDTO> questions) {
        return new PracticeSessionDTO(session.getId(), session.getCourse().getId(), session.getStartTime(), session.getCompletedAt(), session.getScore(), session.getMaxPoints(),
                questions);
    }
}
