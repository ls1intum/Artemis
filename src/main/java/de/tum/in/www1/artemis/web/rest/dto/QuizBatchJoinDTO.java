package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizBatchJoinDTO(@Nullable String password) {
}
