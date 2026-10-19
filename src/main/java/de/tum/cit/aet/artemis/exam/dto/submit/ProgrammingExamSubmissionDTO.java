package de.tum.cit.aet.artemis.exam.dto.submit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The programming-submission variant of {@link SubmitExamSubmissionDTO}. Programming submissions are persisted
 * exclusively through git pushes / the programming submission page, never through the exam hand-in, so this record
 * carries only the id and is accepted-and-ignored. It exists so a legacy full-entity {@code StudentExam} body that
 * still includes programming submissions deserializes without error.
 *
 * @param id the id of the programming submission (ignored by the submit path)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgrammingExamSubmissionDTO(Long id) implements SubmitExamSubmissionDTO {
}
