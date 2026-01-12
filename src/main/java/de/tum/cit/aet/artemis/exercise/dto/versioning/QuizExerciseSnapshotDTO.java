package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.CollectionUtil;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.question.DragAndDropQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.MultipleChoiceQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.ShortAnswerQuestionWithMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseSnapshotDTO(Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts, QuizMode quizMode, Integer duration,
        List<QuizQuestionSnapshotDTO> quizQuestions) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record QuizQuestionSnapshotDTO(ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO,
            MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO, DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO)
            implements Serializable {

        private static QuizQuestionSnapshotDTO of(QuizQuestion quizQuestion) {
            if (quizQuestion.getId() == null) {
                return null;
            }
            ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO = quizQuestion instanceof ShortAnswerQuestion
                    ? ShortAnswerQuestionWithMappingDTO.of((ShortAnswerQuestion) quizQuestion)
                    : null;
            MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO = quizQuestion instanceof MultipleChoiceQuestion
                    ? MultipleChoiceQuestionWithSolutionDTO.of((MultipleChoiceQuestion) quizQuestion)
                    : null;
            DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO = quizQuestion instanceof DragAndDropQuestion
                    ? DragAndDropQuestionWithSolutionDTO.of((DragAndDropQuestion) quizQuestion)
                    : null;
            return new QuizQuestionSnapshotDTO(shortAnswerQuestionWithMappingDTO, multipleChoiceQuestionWithSolutionDTO, dragAndDropQuestionWithSolutionDTO);
        }
    }

    /**
     * creates a snapshot DTO of the given quiz exercise.
     *
     * @param exercise {@link QuizExercise}}
     */
    public static QuizExerciseSnapshotDTO of(QuizExercise exercise) {
        var quizQuestions = CollectionUtil.nullIfEmpty(exercise.getQuizQuestions());
        ArrayList<QuizQuestionSnapshotDTO> questions = null;
        if (quizQuestions != null) {
            questions = exercise.getQuizQuestions().stream().map(QuizQuestionSnapshotDTO::of).collect(Collectors.toCollection(ArrayList::new));
        }
        return new QuizExerciseSnapshotDTO(exercise.isRandomizeQuestionOrder(), exercise.getAllowedNumberOfAttempts(), exercise.getQuizMode(), exercise.getDuration(), questions);
    }

}
