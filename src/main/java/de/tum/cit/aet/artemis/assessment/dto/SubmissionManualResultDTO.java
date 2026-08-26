package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One manual result of a submission, reduced to what the exam score statistics need.
 *
 * @param submissionId the submission the result belongs to
 * @param resultId     the result's id, which orders the correction rounds: the lowest belongs to the first round
 * @param score        the score of the result in percent, null while the result is still being worked on
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionManualResultDTO(long submissionId, long resultId, @Nullable Double score) {
}
