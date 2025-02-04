package de.tum.cit.aet.artemis.quiz.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.exercise.dto.ExerciseBeforeDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseDTOBefore(@JsonUnwrapped ExerciseBeforeDTO exerciseBeforeDTO, String type, Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts,
        Integer remainingNumberOfAttempts, Boolean isOpenForPractice, QuizMode quizMode, Integer duration, Set<QuizBatchDTO> quizBatches, boolean quizStarted, boolean quizEnded) {

    public static QuizExerciseDTOBefore of(QuizExercise quizExercise) {
        return new QuizExerciseDTOBefore(ExerciseBeforeDTO.of(quizExercise), quizExercise.getType(), quizExercise.isRandomizeQuestionOrder(),
                quizExercise.getAllowedNumberOfAttempts(), quizExercise.getRemainingNumberOfAttempts(), quizExercise.isIsOpenForPractice(), quizExercise.getQuizMode(),
                quizExercise.getDuration(), quizExercise.getQuizBatches().stream().map(QuizBatchDTO::of).collect(Collectors.toSet()), quizExercise.isQuizStarted(),
                quizExercise.isQuizEnded());
    }

}
