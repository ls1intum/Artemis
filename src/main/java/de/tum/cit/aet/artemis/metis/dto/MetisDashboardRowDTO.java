package de.tum.cit.aet.artemis.metis.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of the results dashboard of an exercise: a student, the student's tutorial group, the comprehension check and its questions in thread order. The
 * question list is always sent, also when it is empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisDashboardRowDTO(long participationId, @NotNull String studentName, @NotNull String studentLogin, @Nullable String tutorialGroupTitle,
        @NotNull MetisCheckDTO check, @JsonInclude @NotNull List<MetisQuestionDTO> questions) {
}
