package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.question.DragAndDropQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.MultipleChoiceQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.ShortAnswerQuestionWithMappingDTO;

public record QuizExerciseSnapshot(Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts, Boolean isOpenForPractice, QuizMode quizMode, Integer duration,
        List<QuizQuestionSnapshot> quizQuestions) implements Serializable {

    public record QuizQuestionSnapshot(ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO,
            MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO, DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO)
            implements Serializable {

        private static QuizQuestionSnapshot of(QuizQuestion quizQuestion) {
            ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO = quizQuestion instanceof ShortAnswerQuestion
                    ? ShortAnswerQuestionWithMappingDTO.of((ShortAnswerQuestion) quizQuestion)
                    : null;
            MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO = quizQuestion instanceof MultipleChoiceQuestion
                    ? MultipleChoiceQuestionWithSolutionDTO.of((MultipleChoiceQuestion) quizQuestion)
                    : null;
            DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO = quizQuestion instanceof DragAndDropQuestion
                    ? DragAndDropQuestionWithSolutionDTO.of((DragAndDropQuestion) quizQuestion)
                    : null;
            return new QuizQuestionSnapshot(shortAnswerQuestionWithMappingDTO, multipleChoiceQuestionWithSolutionDTO, dragAndDropQuestionWithSolutionDTO);
        }
    }

    public static QuizExerciseSnapshot of(QuizExercise exercise) {
        return new QuizExerciseSnapshot(exercise.isRandomizeQuestionOrder(), exercise.getAllowedNumberOfAttempts(), exercise.isIsOpenForPractice(), exercise.getQuizMode(),
                exercise.getDuration(),
                exercise.getQuizQuestions() != null ? exercise.getQuizQuestions().stream().map(QuizQuestionSnapshot::of).collect(Collectors.toCollection(ArrayList::new)) : null);
    }

}
