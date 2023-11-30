package de.tum.in.www1.artemis.web.rest.dto;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizBatchJoinDTO(@Nullable String password) {
}
