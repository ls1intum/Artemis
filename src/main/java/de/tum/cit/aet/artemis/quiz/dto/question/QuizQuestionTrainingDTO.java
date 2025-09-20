package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record QuizQuestionTrainingDTO(@NotNull QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated, @Nullable Set<Long> questionIds) {

    public long getId() {
        return quizQuestionWithSolutionDTO().quizQuestionBaseDTO().id();
    }
}
