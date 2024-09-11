package de.tum.cit.aet.artemis.web.rest.dto.plagiarism;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismComparisonStatusDTO(PlagiarismStatus status) {
}
