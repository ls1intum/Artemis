package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The modeling-submission variant of {@link SubmitExamSubmissionDTO}: carries the existing submission id and the
 * student's latest UML model and explanation.
 *
 * @param id              the id of the existing modeling submission the answer belongs to
 * @param model           the submitted UML model as JSON string (may be {@code null})
 * @param explanationText the submitted explanation text (may be {@code null})
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelingExamSubmissionDTO(Long id, String model, String explanationText) implements SubmitExamSubmissionDTO {
}
