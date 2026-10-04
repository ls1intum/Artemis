package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * File-upload-submission-specific content carried in the conduction payload (unwrapped into the submission object).
 * During a fresh conduction the {@code filePath} is absent; on resume it carries the path of the previously uploaded
 * file.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadSubmissionFieldsForConductionDTO(String filePath) {
}
