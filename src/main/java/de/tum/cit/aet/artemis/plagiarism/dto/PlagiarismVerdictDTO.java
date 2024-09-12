package de.tum.cit.aet.artemis.plagiarism.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismVerdictDTO(@NotNull PlagiarismVerdict verdict, String verdictMessage, int verdictPointDeduction) {
}
