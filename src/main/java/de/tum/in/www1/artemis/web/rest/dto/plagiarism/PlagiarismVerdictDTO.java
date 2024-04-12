package de.tum.in.www1.artemis.web.rest.dto.plagiarism;

import jakarta.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismVerdictDTO(@Nonnull PlagiarismVerdict verdict, String verdictMessage, int verdictPointDeduction) {
}
