package de.tum.cit.aet.artemis.plagiarism.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

/**
 * Transfers information about plagiarism checks result and its statistics
 *
 * @param plagiarismResult      the plagiarism result to be transferred
 * @param plagiarismResultStats the statistics regarding the plagiarism result
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismResultDTO(PlagiarismResult plagiarismResult, PlagiarismResultStats plagiarismResultStats) {
}
