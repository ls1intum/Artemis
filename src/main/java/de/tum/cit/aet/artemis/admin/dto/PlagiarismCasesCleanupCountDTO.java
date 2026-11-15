package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO previewing the number of plagiarism cases that would be deleted by the age-based plagiarism-case cleanup (cases of
 * courses that ended before the configured retention cutoff).
 *
 * @param plagiarismCases the number of affected plagiarism cases
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(requiredProperties = { "plagiarismCases" })
public record PlagiarismCasesCleanupCountDTO(int plagiarismCases) {
}
