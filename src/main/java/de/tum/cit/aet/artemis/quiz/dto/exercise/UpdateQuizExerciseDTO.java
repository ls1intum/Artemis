package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.fromEditor.QuizQuestionFromEditorDTO;

/**
 * DTO for updating quiz exercises from the editor.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateQuizExerciseDTO(String title, String channelName, Set<String> categories, Set<CompetencyLinkDTO> competencyLinks, DifficultyLevel difficulty, Integer duration,
        Boolean randomizeQuestionOrder, QuizMode quizMode, Set<@Valid QuizBatchFromEditorDTO> quizBatches, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, IncludedInOverallScore includedInOverallScore, List<@Valid ? extends QuizQuestionFromEditorDTO> quizQuestions) implements CompetencyLinksHolderDTO {

    /**
     * Creates a QuizExerciseFromEditorDTO from the given QuizExercise domain object.
     *
     * @param quizExercise the quiz exercise to convert
     * @return the corresponding DTO
     */
    public static UpdateQuizExerciseDTO of(QuizExercise quizExercise) {
        List<QuizQuestionFromEditorDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionFromEditorDTO::of).toList();
        Set<QuizBatchFromEditorDTO> batchDTOs = Optional.ofNullable(quizExercise.getQuizBatches()).orElse(Set.of()).stream().map(QuizBatchFromEditorDTO::of)
                .collect(Collectors.toSet());
        // Only convert competency links if they are initialized (to avoid LazyInitializationException)
        Set<CompetencyLinkDTO> competencyLinkDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getCompetencyLinks()) && quizExercise.getCompetencyLinks() != null) {
            competencyLinkDTOs = quizExercise.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        return new UpdateQuizExerciseDTO(quizExercise.getTitle(), quizExercise.getChannelName(), quizExercise.getCategories(), competencyLinkDTOs, quizExercise.getDifficulty(),
                quizExercise.getDuration(), quizExercise.isRandomizeQuestionOrder(), quizExercise.getQuizMode(), batchDTOs, quizExercise.getReleaseDate(),
                quizExercise.getStartDate(), quizExercise.getDueDate(), quizExercise.getIncludedInOverallScore(), questionDTOs);
    }
}
