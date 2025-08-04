package de.tum.cit.aet.artemis.quiz.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizTrainingAnswerDTO(@Nullable SubmittedAnswer submittedAnswer) {
}
