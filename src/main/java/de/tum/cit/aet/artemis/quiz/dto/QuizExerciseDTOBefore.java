package de.tum.cit.aet.artemis.quiz.dto;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseDTOBefore(String type, Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts, Integer remainingNumberOfAttempts, Boolean isOpenForPractice,
        QuizMode quizMode, Integer duration, List<QuizQuestionDTOBefore> quizQuestions, Set<QuizBatchDTO> quizBatches, boolean quizStarted, boolean quizEnded) {

}
