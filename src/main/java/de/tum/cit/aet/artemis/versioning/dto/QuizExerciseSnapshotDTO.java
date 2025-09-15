package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

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
public record QuizExerciseSnapshotDTO(Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts, Boolean isOpenForPractice, QuizMode quizMode, Integer duration,
        List<QuizQuestionSnapshotDTO> quizQuestions) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record QuizQuestionSnapshotDTO(ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO,
            MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO, DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO)
            implements Serializable {

        private static QuizQuestionSnapshotDTO of(QuizQuestion quizQuestion) {
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
        var questions = exercise.getQuizQuestions() != null ? exercise.getQuizQuestions().stream().map(QuizQuestionSnapshotDTO::of).collect(Collectors.toCollection(ArrayList::new))
                : null;
        if (questions != null && questions.isEmpty()) {
            questions = null;
        }
        return new QuizExerciseSnapshotDTO(exercise.isRandomizeQuestionOrder(), exercise.getAllowedNumberOfAttempts(), exercise.isIsOpenForPractice(), exercise.getQuizMode(),
                exercise.getDuration(),
                exercise.getQuizQuestions() != null ? exercise.getQuizQuestions().stream().map(QuizQuestionSnapshotDTO::of).collect(Collectors.toCollection(ArrayList::new))
                        : null);
    }

}
