package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The file-upload-submission variant of {@link SubmitExamSubmissionDTO}. File-upload submissions are persisted
 * exclusively through the file-upload submission page, never through the exam hand-in, so this record carries only
 * the id and is accepted-and-ignored. It exists so a legacy full-entity {@code StudentExam} body that still includes
 * file-upload submissions deserializes without error.
 *
 * @param id the id of the file-upload submission (ignored by the submit path)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileUploadExamSubmissionDTO(Long id) implements SubmitExamSubmissionDTO {
}
