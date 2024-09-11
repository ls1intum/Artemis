package de.tum.cit.aet.artemis.web.rest.dto.plagiarism;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;
import de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResultStats;

/**
 * Transfers information about plagiarism checks result and its statistics
 *
 * @param plagiarismResult      the plagiarism result to be transferred
 * @param plagiarismResultStats the statistics regarding the plagiarism result
 * @param <T>                   the type of plagiarism result
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismResultDTO<T extends PlagiarismResult<? extends PlagiarismSubmissionElement>>(T plagiarismResult, PlagiarismResultStats plagiarismResultStats) {
}
