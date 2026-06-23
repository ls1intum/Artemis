package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestFeedbackInputDTO(@NotNull Long testId, @NotNull @Size(max = 500) String testName, boolean passed, @Nullable @Size(max = 5000) String message,
        @Nullable Double credits) {
}
