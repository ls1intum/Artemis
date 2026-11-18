package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Minimal information about a (pending) programming submission needed by the client to display the build state of a
 * participation. Deliberately excludes results, participation, and all other entity data: a pending submission has no
 * result yet, and the client only reads the id, commit hash, and submission date from it.
 *
 * @param id             the id of the submission
 * @param commitHash     the commit hash of the submission
 * @param submissionDate the date the submission was created
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionInfoDTO(long id, String commitHash, ZonedDateTime submissionDate) implements Serializable {

    public static ProgrammingSubmissionInfoDTO of(ProgrammingSubmission submission) {
        return new ProgrammingSubmissionInfoDTO(submission.getId(), submission.getCommitHash(), submission.getSubmissionDate());
    }
}
