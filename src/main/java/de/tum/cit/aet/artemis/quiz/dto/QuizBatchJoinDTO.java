package de.tum.cit.aet.artemis.quiz.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchJoinDTO(@Nullable String password) {
}
