package de.tum.cit.aet.artemis.plagiarism.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonStatusDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PlagiarismStatus status) {
}
