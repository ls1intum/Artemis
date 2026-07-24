package de.tum.cit.aet.artemis.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;

/**
 * DTO representing the input for creating or updating a file upload submission.
 *
 * @param id         the ID of the submission (null for new submissions)
 * @param submitted  whether the submission is being submitted (true) or saved as draft (false)
 * @param exerciseId the ID of the file upload exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadSubmissionInputDTO(Long id, boolean submitted, Long exerciseId) {

    /**
     * Converts this DTO to a {@link FileUploadSubmission} entity.
     *
     * @return the constructed FileUploadSubmission entity
     */
    public FileUploadSubmission toEntity() {
        FileUploadSubmission submission = new FileUploadSubmission();
        submission.setId(id);
        submission.setSubmitted(submitted);
        return submission;
    }
}
