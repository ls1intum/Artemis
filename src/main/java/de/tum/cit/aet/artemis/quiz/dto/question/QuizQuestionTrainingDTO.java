package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizQuestionTrainingDTO(@NotNull QuizQuestionWithSolutionDTO quizQuestionWithSolutionDTO, boolean isRated, @Nullable Set<Long> questionIds, boolean isNewSession) {

    public long getId() {
        return quizQuestionWithSolutionDTO().quizQuestionBaseDTO().id();
    }
}
