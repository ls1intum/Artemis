package de.tum.cit.aet.artemis.exercise.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/** Pagination, ordering and team search for the assessment queue. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssessmentQueueSearchDTO(@Min(0) int page, @Min(1) @Max(100) int pageSize, SortingOrder sortingOrder,
        @Pattern(regexp = "participationId|participantName|submissionDate|submissionLanguage|score") String sortedColumn, String searchTerm) {
}
