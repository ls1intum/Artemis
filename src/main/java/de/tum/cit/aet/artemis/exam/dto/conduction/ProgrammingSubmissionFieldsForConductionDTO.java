package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Programming-submission-specific content carried in the conduction / summary payload (unwrapped into the submission
 * object): the commit the submission points to and whether its build failed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionFieldsForConductionDTO(String commitHash, boolean buildFailed) {

    /**
     * Extracts the programming-specific submission fields.
     *
     * @param programmingSubmission the programming submission to convert
     * @return the programming-specific fields
     */
    public static ProgrammingSubmissionFieldsForConductionDTO of(ProgrammingSubmission programmingSubmission) {
        return new ProgrammingSubmissionFieldsForConductionDTO(programmingSubmission.getCommitHash(), programmingSubmission.isBuildFailed());
    }
}
