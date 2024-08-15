package de.tum.in.www1.artemis.web.rest.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizBatchJoinDTO(@Nullable String password) {
}
