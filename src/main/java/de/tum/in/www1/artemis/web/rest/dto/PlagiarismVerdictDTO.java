package de.tum.in.www1.artemis.web.rest.dto;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismVerdictDTO(@NotNull PlagiarismVerdict verdict, String verdictMessage, int verdictPointDeduction) {
}
