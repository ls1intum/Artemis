package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The text-submission variant of {@link SubmitExamSubmissionDTO}: carries the existing submission id and the
 * student's latest answer text.
 *
 * @param id   the id of the existing text submission the answer belongs to
 * @param text the submitted answer text (may be {@code null} if the student left it empty)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextExamSubmissionDTO(Long id, String text) implements SubmitExamSubmissionDTO {
}
