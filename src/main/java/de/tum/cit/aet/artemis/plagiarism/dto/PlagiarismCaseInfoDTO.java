package de.tum.cit.aet.artemis.plagiarism.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseInfoDTO(Long id, PlagiarismVerdict verdict, boolean createdByContinuousPlagiarismControl) {
}
