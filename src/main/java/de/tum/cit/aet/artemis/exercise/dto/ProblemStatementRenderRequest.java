package de.tum.cit.aet.artemis.exercise.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProblemStatementRenderRequest(@Size(max = 100_000) String markdown, @Nullable @Size(max = 100) List<TestFeedbackInput> testResults,
        @Nullable ResultSummaryInput resultSummary, @Nullable @Size(max = 10) String locale) {
}
