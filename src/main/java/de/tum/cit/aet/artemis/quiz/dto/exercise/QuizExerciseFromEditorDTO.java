package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseFromEditorDTO(String title, String channelName, Set<String> categories, Set<CompetencyExerciseLink> competencyLinks, DifficultyLevel difficulty,
        Integer duration, Boolean randomizeQuestionOrder, QuizMode quizMode, Set<QuizBatch> quizBatches, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        IncludedInOverallScore includedInOverallScore, List<QuizQuestion> quizQuestions) {

}
