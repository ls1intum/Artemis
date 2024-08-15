package de.tum.in.www1.artemis.web.rest.dto.plagiarism;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismVerdictDTO(@NonNull PlagiarismVerdict verdict, String verdictMessage, int verdictPointDeduction) {
}
