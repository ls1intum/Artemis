package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseInfoDTO(Long id, PlagiarismVerdict verdict) {
}
