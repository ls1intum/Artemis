package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonStatusDTO(PlagiarismStatus status) {
}
