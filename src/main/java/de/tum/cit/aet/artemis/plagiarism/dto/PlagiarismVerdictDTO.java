package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * DTO carrying the non-null verdict, optional verdict message (at most 1000 characters), and optional point deduction for a plagiarism case.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismVerdictDTO(@NotNull PlagiarismVerdict verdict, @Size(max = 1000) String verdictMessage, int verdictPointDeduction) {
}
